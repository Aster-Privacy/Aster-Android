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

package org.astermail.android.api.keys

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundWriteSwitchesTest {

    @After
    fun teardown() {
        BoundWriteSwitches.clear()
        BoundWriteSwitches.bind_refresher(null)
    }

    @Test
    fun a_vault_write_reads_the_switch_fresh_from_the_server() = kotlinx.coroutines.runBlocking {
        val later = 1_000L + BoundWriteSwitches.FRESH_MS * 3
        BoundWriteSwitches.record("""{"bound_vault_writes":false}""", 1_000L)
        var fetches = 0
        BoundWriteSwitches.bind_refresher {
            fetches += 1
            BoundWriteSwitches.record("""{"bound_vault_writes":true}""", later)
        }
        assertTrue(BoundWriteSwitches.refresh_for_vault_write(now = { later }))
        assertTrue(fetches == 1)
    }

    @Test
    fun a_failed_refresh_leaves_a_stale_switch_off() = kotlinx.coroutines.runBlocking {
        BoundWriteSwitches.record("""{"bound_vault_writes":true}""", 1_000L)
        BoundWriteSwitches.bind_refresher { throw IllegalStateException("offline") }
        assertFalse(BoundWriteSwitches.refresh_for_vault_write(now = { 1_000L + BoundWriteSwitches.FRESH_MS }))
        assertTrue(BoundWriteSwitches.refresh_for_vault_write(now = { 1_000L + 1 }))
    }

    @Test
    fun a_refresh_that_never_answers_gives_up_and_uses_the_recorded_switch() = kotlinx.coroutines.runBlocking {
        BoundWriteSwitches.record("""{"bound_vault_writes":true}""", 1_000L)
        BoundWriteSwitches.bind_refresher { kotlinx.coroutines.awaitCancellation() }
        val started = System.nanoTime()
        assertTrue(BoundWriteSwitches.refresh_for_vault_write(now = { 1_000L + 1 }, timeout_ms = 50L))
        assertTrue(System.nanoTime() - started < 5_000_000_000L)
    }

    @Test
    fun a_refresh_without_a_fetcher_uses_the_recorded_switch() = kotlinx.coroutines.runBlocking {
        BoundWriteSwitches.record("""{"bound_vault_writes":true}""", 1_000L)
        assertTrue(BoundWriteSwitches.refresh_for_vault_write(now = { 1_000L }))
    }

    @Test
    fun the_switch_is_off_before_the_server_answers() {
        BoundWriteSwitches.clear()
        assertFalse(BoundWriteSwitches.vault())
    }

    @Test
    fun a_server_that_omits_the_switch_leaves_it_off() {
        BoundWriteSwitches.record("""{"format_writes":true}""", 1_000L)
        assertFalse(BoundWriteSwitches.vault(1_000L))
    }

    @Test
    fun the_switch_follows_its_field() {
        BoundWriteSwitches.record("""{"bound_vault_writes":true,"bound_attachment_writes":false}""", 1_000L)
        assertTrue(BoundWriteSwitches.vault(1_000L))
        BoundWriteSwitches.record("""{"bound_vault_writes":false,"bound_attachment_writes":true}""", 2_000L)
        assertFalse(BoundWriteSwitches.vault(2_000L))
    }

    @Test
    fun only_a_json_true_turns_the_switch_on() {
        BoundWriteSwitches.record("""{"bound_vault_writes":"true","bound_attachment_writes":1}""", 1_000L)
        assertFalse(BoundWriteSwitches.vault(1_000L))
        BoundWriteSwitches.record("not json", 1_000L)
        assertFalse(BoundWriteSwitches.vault(1_000L))
    }

    @Test
    fun a_stale_answer_counts_as_off() {
        BoundWriteSwitches.record("""{"bound_vault_writes":true,"bound_attachment_writes":true}""", 1_000L)
        assertTrue(BoundWriteSwitches.vault(1_000L + BoundWriteSwitches.FRESH_MS - 1))
        assertFalse(BoundWriteSwitches.vault(1_000L + BoundWriteSwitches.FRESH_MS))
    }

    @Test
    fun the_field_switch_follows_its_own_field() {
        BoundWriteSwitches.record("""{"bound_field_writes":true}""", 1_000L)
        assertTrue(BoundWriteSwitches.field(1_000L))
        assertFalse(BoundWriteSwitches.vault(1_000L))
        assertFalse(BoundWriteSwitches.field(1_000L + BoundWriteSwitches.FRESH_MS))
        BoundWriteSwitches.record("""{"bound_vault_writes":true}""", 2_000L)
        assertFalse(BoundWriteSwitches.field(2_000L))
    }
}
