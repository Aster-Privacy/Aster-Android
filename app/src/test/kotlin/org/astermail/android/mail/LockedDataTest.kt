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

package org.astermail.android.mail

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LockedDataTest {
    private class MemoryCounts(initial: Map<String, Int> = emptyMap()) : LockedSentMailCounts {
        val values = initial.toMutableMap()
        override fun read(account_id: String): Int = values[account_id] ?: 0
        override fun write(account_id: String, count: Int) {
            values[account_id] = count
        }
    }

    private class Calls {
        var restore = 0
        var conversion = 0
        var reseal = 0
        var changed = 0
        var reseal_old: String? = null
    }

    private fun service(
        calls: Calls = Calls(),
        counts: LockedSentMailCounts = MemoryCounts(),
        ids: List<String>? = emptyList(),
        current: String? = "current pass",
        restore: suspend (String) -> Int = { 0 },
        conversion: suspend (String, String) -> AccountDataConversionSummary? = { _, _ -> null },
        reseal: SentMailResealSummary = SentMailResealSummary(),
    ) = LockedDataService(
        list_inactive_key_set_ids = { ids },
        locked_counts = counts,
        current_passphrase = { current?.toByteArray(Charsets.UTF_8) },
        restore_inactive_key_sets = {
            calls.restore++
            restore(it)
        },
        recover_with_conversion = { account_id, password ->
            calls.conversion++
            conversion(account_id, password)
        },
        reseal_sent_mail = { old, _ ->
            calls.reseal++
            calls.reseal_old = String(old, Charsets.UTF_8)
            reseal
        },
        on_changed = { calls.changed++ },
    )

    @Test
    fun signature_sorts_ids_and_drops_empty_ones() {
        assertEquals("a,b,c|", locked_data_signature(listOf("c", "", "a", "b"), 0))
    }

    @Test
    fun signature_marks_locked_sent_mail() {
        assertEquals("a|sent", locked_data_signature(listOf("a"), 3))
        assertEquals("|sent", locked_data_signature(emptyList(), 1))
        assertEquals("|", locked_data_signature(emptyList(), 0))
    }

    @Test
    fun status_counts_sets_and_clamps_negative_sent_mail() {
        val status = locked_data_status(listOf("b", "", "a"), -4)

        assertEquals(2, status.inactive_key_sets)
        assertEquals(0, status.locked_sent_mail)
        assertEquals("a,b|", status.signature)
    }

    @Test
    fun has_locked_data_needs_a_set_or_locked_sent_mail() {
        assertFalse(has_locked_data(null))
        assertFalse(has_locked_data(locked_data_status(emptyList(), 0)))
        assertTrue(has_locked_data(locked_data_status(listOf("a"), 0)))
        assertTrue(has_locked_data(locked_data_status(emptyList(), 2)))
    }

    @Test
    fun banner_shows_only_when_unlocked_loaded_locked_and_not_dismissed() {
        val status = locked_data_status(listOf("a"), 1)

        assertTrue(should_show_locked_data_banner(status, true, true, ""))
        assertFalse(should_show_locked_data_banner(status, false, true, ""))
        assertFalse(should_show_locked_data_banner(status, true, false, ""))
        assertFalse(should_show_locked_data_banner(null, true, true, ""))
        assertFalse(should_show_locked_data_banner(locked_data_status(emptyList(), 0), true, true, ""))
        assertFalse(should_show_locked_data_banner(status, true, true, "a|sent"))
    }

    @Test
    fun banner_returns_when_new_locked_data_changes_the_signature() {
        val status = locked_data_status(listOf("a", "b"), 1)

        assertTrue(should_show_locked_data_banner(status, true, true, "a|sent"))
    }

    @Test
    fun outcome_maps_any_recovery_to_success() {
        assertEquals(
            LockedDataRecoveryOutcome.SUCCESS,
            locked_data_recovery_outcome(LockedDataRecovery(restored_key_sets = 1, failed = true)),
        )
        assertEquals(
            LockedDataRecoveryOutcome.SUCCESS,
            locked_data_recovery_outcome(LockedDataRecovery(recovered_sent_mail = 2)),
        )
        assertEquals(LockedDataRecoveryOutcome.FAILED, locked_data_recovery_outcome(LockedDataRecovery(failed = true)))
        assertEquals(LockedDataRecoveryOutcome.NO_MATCH, locked_data_recovery_outcome(LockedDataRecovery()))
    }

    @Test
    fun status_reads_server_sets_and_the_local_count() = runTest {
        val status = service(ids = listOf("b", "a"), counts = MemoryCounts(mapOf("acct" to 4))).status("acct")

        assertEquals(LockedDataStatus(2, 4, "a,b|sent"), status)
    }

    @Test
    fun status_is_unknown_when_the_server_call_fails() = runTest {
        assertNull(service(ids = null).status("acct"))
        assertNull(service().status(""))
    }

    @Test
    fun the_current_password_recovers_no_sent_mail() = runTest {
        val calls = Calls()

        val result = service(calls = calls).recover_locked_data("acct", "current pass")

        assertEquals(LockedDataRecovery(0, 0, false), result)
        assertEquals(1, calls.restore)
        assertEquals(0, calls.conversion)
        assertEquals(0, calls.reseal)
        assertEquals(1, calls.changed)
    }

    @Test
    fun sent_mail_still_recovers_when_the_key_restore_throws() = runTest {
        val calls = Calls()

        val result = service(
            calls = calls,
            restore = { error("network") },
            conversion = { _, _ -> AccountDataConversionSummary(converted = 5) },
        ).recover_locked_data("acct", "old pass")

        assertEquals(LockedDataRecovery(0, 5, true), result)
        assertEquals(1, calls.conversion)
        assertEquals(LockedDataRecoveryOutcome.SUCCESS, locked_data_recovery_outcome(result))
    }

    @Test
    fun conversion_failures_are_reported() = runTest {
        val result = service(
            restore = { 2 },
            conversion = { _, _ -> AccountDataConversionSummary(converted = 1, failed = 1) },
        ).recover_locked_data("acct", "old pass")

        assertEquals(LockedDataRecovery(2, 1, true), result)
    }

    @Test
    fun falls_back_to_the_reseal_and_stores_what_is_still_locked() = runTest {
        val calls = Calls()
        val counts = MemoryCounts(mapOf("acct" to 9))

        val result = service(
            calls = calls,
            counts = counts,
            reseal = SentMailResealSummary(checked = 9, rewritten = 7, unreadable = 2),
        ).recover_locked_data("acct", "old pass")

        assertEquals(LockedDataRecovery(0, 7, false), result)
        assertEquals(1, calls.reseal)
        assertEquals("old pass", calls.reseal_old)
        assertEquals(2, counts.read("acct"))
    }

    @Test
    fun a_missing_session_passphrase_is_a_failure() = runTest {
        val calls = Calls()

        val result = service(calls = calls, current = null, restore = { 1 }).recover_locked_data("acct", "old pass")

        assertEquals(LockedDataRecovery(1, 0, true), result)
        assertEquals(0, calls.conversion)
        assertEquals(0, calls.reseal)
    }

    @Test
    fun a_wrong_password_is_no_match() = runTest {
        val result = service(
            conversion = { _, _ -> AccountDataConversionSummary(checked = 3, unreadable = 3) },
        ).recover_locked_data("acct", "wrong pass")

        assertEquals(LockedDataRecoveryOutcome.NO_MATCH, locked_data_recovery_outcome(result))
    }

    @Test
    fun an_empty_password_does_nothing() = runTest {
        val calls = Calls()

        assertEquals(LockedDataRecovery(), service(calls = calls).recover_locked_data("acct", ""))
        assertEquals(0, calls.restore)
        assertEquals(0, calls.changed)
    }
}
