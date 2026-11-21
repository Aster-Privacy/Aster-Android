//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.guard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.guard.GUARD_SKIP_FAILED_STALE
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardCancelResult
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.GuardChangeList
import org.astermail.android.api.guard.GuardPendingChange
import org.astermail.android.api.guard.GuardSkipResult
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.GuardThisDevice
import org.astermail.android.api.guard.GuardVetoResult
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.guard_hold_kind
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.InMemoryPrefs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardChangesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val this_id = "a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6"
    private val forward_id = "c1c2c3c4-d1d2-e1e2-f1f2-a1a2a3a4a5a6"
    private val rescue_id = "d1d2d3d4-e1e2-f1f2-a1a2-b1b2b3b4b5b6"
    private val nonce = ByteArray(32) { (it + 3).toByte() }

    private class recording_signer(private val result: guard_sign_result = guard_sign_result.signed(byteArrayOf(4, 5, 6))) : guard_signer {
        val messages = mutableListOf<ByteArray>()
        override suspend fun sign(message: ByteArray): guard_sign_result {
            messages += message
            return result
        }
    }

    @Before
    fun set_up() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tear_down() {
        Dispatchers.resetMain()
    }

    private fun status(long_trusted: Boolean = true, this_device: Boolean = true) = GuardStatus(
        switches = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES,
        hold_scopes = 7,
        enrolled_at = "2026-11-01T00:00:00Z",
        has_rescue_code = true,
        this_device = if (this_device) GuardThisDevice(id = this_id, long_trusted = long_trusted) else null,
    )

    private val forward = GuardPendingChange(forward_id, "forwarding_change", "2026-11-16T10:00:00Z", "2026-11-15T10:00:00Z")
    private val rescue = GuardPendingChange(rescue_id, "rescue", "2026-11-15T20:00:00Z", "2026-11-14T20:00:00Z")

    private fun api(
        status: GuardStatus = status(),
        changes: List<GuardPendingChange> = listOf(forward, rescue),
    ): GuardApi = mockk(relaxed = true) {
        coEvery { status() } returns status
        coEvery { changes() } returns GuardChangeList(changes)
        coEvery { challenge() } returns GuardChallenge(DeviceEnvelope.base64url_encode(nonce))
        coEvery { veto_signed(any(), any()) } returns GuardVetoResult("vetoed")
        coEvery { cancel_change(any(), any()) } returns GuardCancelResult(cancelled = true)
        coEvery { skip_change(any(), any()) } returns GuardSkipResult()
    }

    private fun vm(api: GuardApi) = GuardChangesViewModel(api, GuardDeviceStore(InMemoryPrefs())) { user }

    private fun expected(code: GuardActionCode, id: String) =
        GuardDigests.action_message(code, user, nonce, GuardDigests.id_subject(UUID.fromString(id)))

    @Test
    fun loads_changes_sorted_by_apply_time() = runTest(dispatcher) {
        val model = vm(api())
        advanceUntilIdle()
        val ui = model.state.value
        assertFalse(ui.loading)
        assertEquals(listOf(rescue_id, forward_id), ui.changes.map { it.id })
        assertEquals(guard_hold_kind.rescue, ui.changes.first().kind)
        assertTrue(ui.long_trusted)
    }

    @Test
    fun skip_only_offered_on_long_trusted_device_for_skippable_kinds() = runTest(dispatcher) {
        val trusted = vm(api())
        advanceUntilIdle()
        val ui = trusted.state.value
        val forward_row = ui.changes.first { it.id == forward_id }
        val rescue_row = ui.changes.first { it.id == rescue_id }
        assertTrue(ui.can_skip(forward_row))
        assertFalse(ui.can_skip(rescue_row))
        assertTrue(ui.can_stop(rescue_row))
        assertFalse(ui.can_cancel(rescue_row))
        assertTrue(ui.can_cancel(forward_row))

        val fresh = vm(api(status = status(long_trusted = false)))
        advanceUntilIdle()
        val fresh_ui = fresh.state.value
        val fresh_forward = fresh_ui.changes.first { it.id == forward_id }
        assertFalse(fresh_ui.can_skip(fresh_forward))
        assertTrue(fresh_ui.skip_needs_trust(fresh_forward))
    }

    @Test
    fun cancel_signs_code_4_and_refreshes() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        coEvery { api.changes() } returns GuardChangeList(listOf(rescue))
        val signer = recording_signer()
        model.run_cancel(signer, forward_id)
        coVerify { api.cancel_change(forward_id, any()) }
        assertArrayEquals(expected(GuardActionCode.CANCEL, forward_id), signer.messages.single())
        assertEquals(listOf(rescue_id), model.state.value.changes.map { it.id })
        assertEquals(guard_changes_notice.change_canceled, model.state.value.notice)
        assertNull(model.state.value.working_id)
    }

    @Test
    fun cancel_of_an_ended_change_is_not_reported_as_canceled() = runTest(dispatcher) {
        val api = api()
        coEvery { api.cancel_change(any(), any()) } returns GuardCancelResult(cancelled = false)
        val model = vm(api)
        advanceUntilIdle()
        model.run_cancel(recording_signer(), forward_id)
        assertEquals(guard_changes_notice.change_already_ended, model.state.value.notice)
        assertNull(model.state.value.working_id)
    }

    @Test
    fun stale_skip_is_not_reported_as_applied() = runTest(dispatcher) {
        val api = api()
        coEvery { api.skip_change(any(), any()) } returns GuardSkipResult(GUARD_SKIP_FAILED_STALE)
        val model = vm(api)
        advanceUntilIdle()
        model.run_skip(recording_signer(), forward_id)
        assertEquals(guard_changes_notice.change_stale, model.state.value.notice)
    }

    @Test
    fun server_flags_hide_skip_and_cancel() = runTest(dispatcher) {
        val locked = forward.copy(skippable = false, cancellable = false)
        val rotation = GuardPendingChange(
            "e1e2e3e4-f1f2-a1a2-b1b2-c1c2c3c4c5c6", "rescue_code_rotation", "2026-11-16T12:00:00Z", "2026-11-15T12:00:00Z",
        )
        val model = vm(api(changes = listOf(locked, rotation)))
        advanceUntilIdle()
        val ui = model.state.value
        val locked_row = ui.changes.first { it.id == forward_id }
        val rotation_row = ui.changes.first { it.kind == guard_hold_kind.rescue_code_rotation }
        assertFalse(ui.can_skip(locked_row))
        assertFalse(ui.skip_needs_trust(locked_row))
        assertFalse(ui.can_cancel(locked_row))
        assertTrue(ui.can_skip(rotation_row))
        assertTrue(ui.can_cancel(rotation_row))
    }

    @Test
    fun skip_signs_code_5() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        val signer = recording_signer()
        model.run_skip(signer, forward_id)
        coVerify { api.skip_change(forward_id, any()) }
        assertArrayEquals(expected(GuardActionCode.SKIP, forward_id), signer.messages.single())
    }

    @Test
    fun stop_signs_code_6_with_veto() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        val signer = recording_signer()
        model.run_stop(signer, rescue_id)
        coVerify { api.veto_signed(rescue_id, any()) }
        assertArrayEquals(expected(GuardActionCode.VETO, rescue_id), signer.messages.single())
        assertEquals(guard_changes_notice.change_stopped, model.state.value.notice)
    }

    @Test
    fun step_up_required_on_skip_shows_notice() = runTest(dispatcher) {
        val api = api()
        coEvery { api.skip_change(any(), any()) } throws ApiError.ForbiddenError("step up", GUARD_STEP_UP_REQUIRED_CODE)
        val model = vm(api)
        advanceUntilIdle()
        model.run_skip(recording_signer(), forward_id)
        assertEquals(guard_changes_notice.step_up_required, model.state.value.notice)
        assertNull(model.state.value.working_id)
    }

    @Test
    fun canceled_prompt_sends_nothing() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        model.run_cancel(recording_signer(guard_sign_result.canceled), forward_id)
        coVerify(exactly = 0) { api.cancel_change(any(), any()) }
        assertEquals(guard_changes_notice.canceled, model.state.value.notice)
    }

    @Test
    fun not_found_hides_the_screen() = runTest(dispatcher) {
        val api: GuardApi = mockk(relaxed = true) { coEvery { status() } throws ApiError.NotFoundError }
        val model = vm(api)
        advanceUntilIdle()
        assertTrue(model.state.value.hidden)
    }

    @Test
    fun not_a_guard_device_offers_no_actions() = runTest(dispatcher) {
        val model = vm(api(status = status(this_device = false)))
        advanceUntilIdle()
        val ui = model.state.value
        ui.changes.forEach {
            assertFalse(ui.can_cancel(it))
            assertFalse(ui.can_skip(it))
            assertFalse(ui.can_stop(it))
        }
    }
}
