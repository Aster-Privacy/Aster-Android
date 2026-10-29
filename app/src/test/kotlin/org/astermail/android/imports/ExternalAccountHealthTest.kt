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

package org.astermail.android.imports

import org.astermail.android.api.external_accounts.ExternalAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalAccountHealthTest {

    private val now = java.time.Instant.parse("2026-10-29T12:00:00Z").toEpochMilli()
    private val hour = 60L * 60 * 1000

    private fun iso(ms: Long) = java.time.Instant.ofEpochMilli(ms).toString()

    private fun account(
        id: String,
        status: String? = "success",
        last_sync_at: String? = iso(now - hour),
        needs_reauth: Boolean = false,
        is_enabled: Boolean = true,
        created_at: String = iso(now - 30 * 24 * hour),
    ) = ExternalAccount(
        id = id,
        account_token = "tok_$id",
        protocol = "imap",
        is_enabled = is_enabled,
        last_sync_at = last_sync_at,
        last_sync_status = status,
        needs_reauth = needs_reauth,
        created_at = created_at,
    )

    private fun alerts(accounts: List<ExternalAccount>, problem_since: Map<String, Long> = emptyMap()) =
        external_account_alerts(
            external_account_health_records(accounts, accounts.associate { it.account_token to "${it.id}@example.com" }, problem_since),
            now,
        )

    @Test
    fun classifies_states_in_priority_order() {
        assertEquals(ExternalAccountHealthState.NEEDS_REAUTH, external_account_state(account("a", needs_reauth = true, is_enabled = false)))
        assertEquals(ExternalAccountHealthState.PAUSED, external_account_state(account("a", status = "error", is_enabled = false)))
        assertEquals(ExternalAccountHealthState.SYNCING, external_account_state(account("a", status = "purging")))
        assertEquals(ExternalAccountHealthState.FAILING, external_account_state(account("a", status = "quota_exceeded")))
        assertEquals(ExternalAccountHealthState.NEVER_SYNCED, external_account_state(account("a", status = null, last_sync_at = null)))
        assertEquals(ExternalAccountHealthState.HEALTHY, external_account_state(account("a")))
    }

    @Test
    fun reauth_alerts_at_once_only_when_enabled() {
        assertEquals(1, alerts(listOf(account("a", needs_reauth = true))).size)
        assertTrue(alerts(listOf(account("a", needs_reauth = true, is_enabled = false))).isEmpty())
    }

    @Test
    fun failing_alerts_after_a_day_using_the_earliest_known_time() {
        assertTrue(alerts(listOf(account("a", status = "error", last_sync_at = iso(now - hour)))).isEmpty())
        val tracked = alerts(listOf(account("a", status = "error", last_sync_at = iso(now - hour))), mapOf("a" to now - 25 * hour))
        assertEquals(ExternalAccountHealthState.FAILING, tracked.single().state)
        assertEquals(1, alerts(listOf(account("a", status = "error", last_sync_at = null, created_at = iso(now - 48 * hour)))).size)
    }

    @Test
    fun signature_is_stable_regardless_of_order() {
        val list = listOf(account("b", needs_reauth = true), account("a", needs_reauth = true))
        assertEquals("a:needs_reauth|b:needs_reauth", external_account_alert_signature(alerts(list)))
        assertEquals(external_account_alert_signature(alerts(list)), external_account_alert_signature(alerts(list.reversed())))
    }

    @Test
    fun banner_hides_only_for_the_dismissed_signature() {
        val current = alerts(listOf(account("a", needs_reauth = true)))
        assertTrue(should_show_external_account_banner(current, ""))
        assertFalse(should_show_external_account_banner(current, "a:needs_reauth"))
        val grown = alerts(listOf(account("a", needs_reauth = true), account("b", needs_reauth = true)))
        assertTrue(should_show_external_account_banner(grown, "a:needs_reauth"))
        assertFalse(should_show_external_account_banner(emptyList(), ""))
    }

    @Test
    fun problem_since_starts_holds_through_syncing_and_clears_on_recovery() {
        val started = next_problem_since(emptyMap(), listOf(account("a", status = "error")), now)
        assertEquals(now, started["a"])
        val held = next_problem_since(started, listOf(account("a", status = "syncing")), now + hour)
        assertEquals(now, held["a"])
        val kept = next_problem_since(held, listOf(account("a", status = "error")), now + 2 * hour)
        assertEquals(now, kept["a"])
        assertNull(next_problem_since(kept, listOf(account("a")), now + 3 * hour)["a"])
    }

    @Test
    fun problem_since_keeps_entries_for_accounts_not_in_the_list() {
        val next = next_problem_since(mapOf("other" to now - hour), listOf(account("a")), now)
        assertEquals(now - hour, next["other"])
    }

    @Test
    fun parses_offset_timestamps() {
        assertEquals(now, parse_sync_timestamp("2026-10-29T12:00:00+00:00"))
        assertNull(parse_sync_timestamp("not a date"))
        assertNull(parse_sync_timestamp(null))
    }
}
