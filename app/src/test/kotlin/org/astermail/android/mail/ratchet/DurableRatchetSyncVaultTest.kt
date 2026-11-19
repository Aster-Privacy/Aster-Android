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

package org.astermail.android.mail.ratchet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.astermail.android.storage.fake_prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DurableRatchetSyncVaultTest {

    private class FlakyStore : RatchetVaultStore {
        var accepts = false
        var vault = JSONObject()
        var writes = 0

        override suspend fun load(): JSONObject = JSONObject(vault.toString())

        override suspend fun write(floors: Map<String, Long>, state_v2: Boolean): JSONObject? {
            writes += 1
            if (!accepts) return null
            RatchetVaultFields.apply(vault, floors, state_v2)
            return JSONObject(vault.toString())
        }
    }

    private val prefs = fake_prefs()
    private val journal = PrefsRatchetFloorJournal { prefs }

    private fun killed_process(): CoroutineScope = CoroutineScope(Job().apply { cancel() })

    private fun TestScope.pass_flush_delay() {
        advanceTimeBy(1_000L)
        runCurrent()
    }

    private fun vault(store: RatchetVaultStore, scope: CoroutineScope) = DurableRatchetSyncVault(
        user_id = { "user-1" },
        store = store,
        journal = journal,
        scope = scope,
        flush_delay_ms = 10L,
    )

    @Test
    fun `floors the vault refused survive a restart and are written on the next start`() = runTest {
        val store = FlakyStore()
        val first = vault(store, killed_process())
        first.record("conv-a", 7L, state_v2 = true)
        assertFalse(first.flush_pending())

        store.accepts = true
        val restarted = vault(store, backgroundScope)
        val read = restarted.read()
        assertEquals(7L, read.floor("conv-a"))
        assertTrue(read.state_v2)

        assertTrue(restarted.flush_pending())
        assertEquals(7L, RatchetVaultFields.read(store.vault).floor("conv-a"))
        assertTrue(RatchetVaultFields.read(store.vault).state_v2)
        assertEquals(RatchetVaultFloors.empty, journal.load("user-1"))
    }

    @Test
    fun `a restart retries the refused flush without another write`() = runTest {
        val store = FlakyStore()
        val first = vault(store, killed_process())
        first.record("conv-a", 7L, state_v2 = false)
        assertFalse(first.flush_pending())

        store.accepts = true
        vault(store, backgroundScope).read()
        pass_flush_delay()

        assertEquals(7L, RatchetVaultFields.read(store.vault).floor("conv-a"))
    }

    @Test
    fun `the next write retries a refused flush`() = runTest {
        val store = FlakyStore()
        val synced = vault(store, backgroundScope)
        synced.record("conv-a", 7L, state_v2 = false)
        advanceTimeBy(10L)
        runCurrent()
        assertEquals(1, store.writes)
        assertEquals(0L, RatchetVaultFields.read(store.vault).floor("conv-a"))

        store.accepts = true
        synced.record("conv-a", 7L, state_v2 = false)
        pass_flush_delay()

        assertEquals(7L, RatchetVaultFields.read(store.vault).floor("conv-a"))
        assertEquals(RatchetVaultFloors.empty, journal.load("user-1"))
    }

    @Test
    fun `a refused flush retries with a growing wait and stops after a bounded number of tries`() = runTest {
        val store = FlakyStore()
        val synced = vault(store, backgroundScope)
        synced.record("conv-a", 7L, state_v2 = false)

        advanceTimeBy(10L)
        runCurrent()
        assertEquals(1, store.writes)
        advanceTimeBy(20L)
        runCurrent()
        assertEquals(2, store.writes)
        advanceTimeBy(39L)
        runCurrent()
        assertEquals(2, store.writes)
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(3, store.writes)

        advanceTimeBy(600_000L)
        runCurrent()
        assertEquals(8, store.writes)
    }

    @Test
    fun `a failed flush on demand schedules another try`() = runTest {
        val store = FlakyStore()
        val synced = vault(store, backgroundScope)
        synced.record("conv-a", 7L, state_v2 = false)
        advanceTimeBy(600_000L)
        runCurrent()
        val exhausted = store.writes

        assertFalse(synced.flush_pending())
        assertEquals(exhausted + 1, store.writes)
        store.accepts = true
        advanceTimeBy(10L)
        runCurrent()

        assertEquals(7L, RatchetVaultFields.read(store.vault).floor("conv-a"))
    }

    @Test
    fun `a cancelled vault read is not turned into an empty vault`() = runTest {
        val store = object : RatchetVaultStore {
            override suspend fun load(): JSONObject? = throw kotlinx.coroutines.CancellationException("stopped")
            override suspend fun write(floors: Map<String, Long>, state_v2: Boolean): JSONObject? = null
        }
        val outcome = runCatching { vault(store, killed_process()).read() }

        assertTrue(outcome.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }

    @Test
    fun `a write floor journaled before the server write survives a restart until it is recorded`() = runTest {
        val store = FlakyStore()
        val first = vault(store, killed_process())
        first.intend("conv-a", 9L)

        val restarted = vault(store, killed_process())
        assertEquals(mapOf("conv-a" to 9L), restarted.unconfirmed())
        assertEquals(0L, restarted.read().floor("conv-a"))

        restarted.record("conv-a", 9L, state_v2 = false)
        assertTrue(restarted.unconfirmed().isEmpty())
        assertTrue(vault(store, killed_process()).unconfirmed().isEmpty())
    }
}
