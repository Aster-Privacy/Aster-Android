//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.crypto.storage_rotation

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import org.astermail.android.auth.STORAGE_REKEY_DONE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyKeySweepTest {

    private class QuietApi : ResealApi {
        val calls = ArrayList<String>()
        override suspend fun get(path: String): ApiReply = ApiReply(200, json_object("ok" to true)).also { calls.add(path) }
        override suspend fun post(path: String, body: JsonElement): ApiReply = get(path)
        override suspend fun put(path: String, body: JsonElement): ApiReply = get(path)
    }

    private class RecordingStep(private val result: StepResult) : ResealStep {
        override val name = "recording"
        var runs = 0
        var saw_legacy_key = false

        override suspend fun run(ctx: ResealContext): StepResult {
            runs += 1
            saw_legacy_key = ctx.keys.old_aes.any { it.contentEquals(legacy_key) }
            return result
        }
    }

    private class Retirement {
        var count = 0
        fun retire() {
            count += 1
        }
    }

    private fun done_view(random: Boolean = true): VaultKeyView = VaultKeyView(
        data_kek = encode_b64(ByteArray(32) { 7 }),
        data_kek_random = random,
        storage_rekey_state = STORAGE_REKEY_DONE,
        prior_keys = emptyList(),
        legacy_keks = emptyList(),
    )

    private suspend fun sweep(
        step: RecordingStep,
        retirement: Retirement,
        view: VaultKeyView = done_view(),
        legacy_keys: List<ByteArray> = listOf(legacy_key.copyOf()),
        gate: (suspend () -> RekeyGateDecision)? = null,
        api: ResealApi = QuietApi(),
    ): ResealRunResult = run_legacy_key_sweep(
        view = view,
        api = api,
        store = MemoryProgressStore(),
        legacy_keys = legacy_keys,
        retire = retirement::retire,
        sleep = {},
        steps = listOf(step),
        gate = gate,
    )

    @Test
    fun a_finished_sweep_reseals_under_the_legacy_keys_and_retires_them() = runBlocking {
        val step = RecordingStep(StepResult.DONE)
        val retirement = Retirement()
        val result = sweep(step, retirement)
        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(1, step.runs)
        assertTrue(step.saw_legacy_key)
        assertEquals(1, retirement.count)
    }

    @Test
    fun an_incomplete_sweep_keeps_the_legacy_keys() = runBlocking {
        val retirement = Retirement()
        val result = sweep(RecordingStep(StepResult.INCOMPLETE), retirement)
        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals(0, retirement.count)
    }

    @Test
    fun an_interrupted_sweep_keeps_the_legacy_keys() = runBlocking {
        val retirement = Retirement()
        val result = sweep(RecordingStep(StepResult.INTERRUPTED), retirement)
        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals(0, retirement.count)
    }

    @Test
    fun a_closed_gate_defers_the_sweep_and_keeps_the_legacy_keys() = runBlocking {
        val step = RecordingStep(StepResult.DONE)
        val retirement = Retirement()
        val api = QuietApi()
        val result = sweep(step, retirement, gate = { RekeyGateDecision.Wait("old_client", 60) }, api = api)
        assertEquals(ResealRunStatus.DEFERRED, result.status)
        assertEquals(0, step.runs)
        assertEquals(0, retirement.count)
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun no_legacy_keys_means_nothing_to_sweep_or_retire() = runBlocking {
        val step = RecordingStep(StepResult.DONE)
        val retirement = Retirement()
        val result = sweep(step, retirement, legacy_keys = emptyList())
        assertEquals(ResealRunStatus.NOT_NEEDED, result.status)
        assertEquals(0, step.runs)
        assertEquals(0, retirement.count)
    }

    @Test
    fun an_account_without_a_random_data_key_never_retires_its_legacy_keys() = runBlocking {
        val step = RecordingStep(StepResult.DONE)
        val retirement = Retirement()
        val result = sweep(step, retirement, view = done_view(random = false))
        assertEquals(ResealRunStatus.NOT_NEEDED, result.status)
        assertFalse(step.saw_legacy_key)
        assertEquals(0, retirement.count)
    }

    @Test
    fun a_legacy_key_equal_to_the_data_key_is_not_offered_as_old() = runBlocking {
        val step = RecordingStep(StepResult.DONE)
        val retirement = Retirement()
        sweep(step, retirement, legacy_keys = listOf(ByteArray(32) { 7 }, legacy_key.copyOf()))
        assertTrue(step.saw_legacy_key)
        assertEquals(1, retirement.count)
    }

    private companion object {
        val legacy_key = ByteArray(32) { (it + 40).toByte() }
    }
}
