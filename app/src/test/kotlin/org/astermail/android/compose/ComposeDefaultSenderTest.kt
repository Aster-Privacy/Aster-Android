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

package org.astermail.android.compose

import org.astermail.android.api.external_accounts.ExternalAccount
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.imports.ExternalAccountData
import org.astermail.android.imports.ExternalAccountsUiState
import org.astermail.android.imports.external_sender_ids
import org.astermail.android.ui.common.resolve_primary_sender_email
import org.astermail.android.ui.common.sender_id_for_email
import org.astermail.android.ui.compose.compose_thread_message
import org.astermail.android.ui.compose.compose_thread_snapshot
import org.astermail.android.ui.compose.default_sender_retry_attempts
import org.astermail.android.ui.compose.default_sender_retry_delay_ms
import org.astermail.android.ui.compose.from_tier_pinned
import org.astermail.android.ui.compose.from_tier_thread
import org.astermail.android.ui.compose.is_thread_compose
import org.astermail.android.ui.compose.next_from_alias
import org.astermail.android.ui.compose.resolve_from_alias_tiered
import org.astermail.android.ui.compose.resolve_live_received_on_alias
import org.astermail.android.ui.compose.resolved_from_alias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeDefaultSenderTest {

    private val primary = "me@astermail.org"
    private val pinned = "shopping@astermail.org"
    private val support = "support@astermail.org"
    private val options = listOf(primary, pinned, support)

    private val viewed_thread = compose_thread_snapshot(
        item_id = "item-1",
        item_subject = "Hello",
        messages = listOf(
            compose_thread_message(
                id = "item-1",
                sender_email = "friend@example.com",
                to_addresses = listOf(primary),
                timestamp = "2026-09-10T10:00:00Z",
                delivered_to = primary,
            ),
        ),
    )

    private fun live_from(reply_to: String?, mode: String?, thread: compose_thread_snapshot) =
        resolve_from_alias_tiered(
            resolve_live_received_on_alias(reply_to, mode, thread, options, primary),
            null,
            pinned,
            options,
        )

    @Test
    fun new_compose_with_a_viewed_thread_keeps_the_pinned_alias() {
        val resolved = live_from(null, null, viewed_thread)
        assertEquals(pinned, resolved.address)
        assertEquals(from_tier_pinned, resolved.tier)
    }

    @Test
    fun share_compose_with_blank_mode_keeps_the_pinned_alias() {
        val resolved = live_from("", "", viewed_thread)
        assertEquals(pinned, resolved.address)
    }

    @Test
    fun explicit_new_and_draft_modes_ignore_the_thread() {
        assertNull(resolve_live_received_on_alias("item-1", "new", viewed_thread, options, primary))
        assertNull(resolve_live_received_on_alias("item-1", "draft", viewed_thread, options, primary))
    }

    @Test
    fun pinned_alias_is_not_replaced_by_a_primary_from_an_unrelated_thread() {
        val resolved = live_from(null, null, viewed_thread)
        val next = next_from_alias(
            current = pinned,
            current_tier = from_tier_pinned,
            resolved = resolved,
            alias_options = options,
            manually_selected = false,
        )
        assertNull(next)
    }

    @Test
    fun reply_to_a_message_sent_to_an_alias_uses_that_alias() {
        val thread = viewed_thread.copy(
            messages = listOf(
                viewed_thread.messages.first().copy(to_addresses = listOf(support), delivered_to = support),
            ),
        )
        val resolved = live_from("item-1", "reply", thread)
        assertEquals(support, resolved.address)
        assertEquals(from_tier_thread, resolved.tier)
    }

    @Test
    fun reply_waits_for_the_thread_it_targets() {
        assertNull(resolve_live_received_on_alias("item-2", "reply", viewed_thread, options, primary))
        assertEquals(
            primary,
            resolve_live_received_on_alias("item-1", "reply", viewed_thread, options, primary),
        )
    }

    @Test
    fun thread_compose_requires_a_target_and_a_reply_mode() {
        assertTrue(is_thread_compose("item-1", "reply"))
        assertTrue(is_thread_compose("item-1", "forward"))
        assertFalse(is_thread_compose(null, "reply"))
        assertFalse(is_thread_compose("item-1", null))
        assertFalse(is_thread_compose("item-1", ""))
        assertFalse(is_thread_compose("item-1", "new"))
        assertFalse(is_thread_compose("item-1", "draft"))
    }

    @Test
    fun pinned_external_account_resolves_to_its_address() {
        val external = mapOf("acct-7" to "me@example.net")
        assertEquals(
            "me@example.net",
            resolve_primary_sender_email(
                default_sender_id = "external-acct-7",
                user_email = primary,
                aliases = emptyList(),
                ghost_aliases = emptyList(),
                external_senders = external,
            ),
        )
        assertEquals(
            "external-acct-7",
            sender_id_for_email(
                email = "me@example.net",
                user_email = primary,
                aliases = emptyList(),
                ghost_aliases = emptyList(),
                external_senders = external,
            ),
        )
    }

    @Test
    fun unknown_external_id_falls_back_to_the_primary_address() {
        assertEquals(
            primary,
            resolve_primary_sender_email(
                default_sender_id = "external-missing",
                user_email = primary,
                aliases = emptyList(),
                ghost_aliases = emptyList(),
            ),
        )
    }

    @Test
    fun pinned_alias_id_still_resolves_and_round_trips() {
        val aliases = listOf(
            AliasInfo(id = "alias-shopping", encrypted_local_part = "shopping", domain = "astermail.org"),
        )
        val address = resolve_primary_sender_email("alias-shopping", primary, aliases, emptyList())
        assertEquals(pinned, address)
        assertEquals("alias-shopping", sender_id_for_email(address, primary, aliases, emptyList()))
    }

    @Test
    fun external_sender_ids_skip_disabled_oauth_and_import_accounts() {
        val state = ExternalAccountsUiState(
            accounts = listOf(
                ExternalAccount(id = "a1", account_token = "t1"),
                ExternalAccount(id = "a2", account_token = "t2", is_enabled = false),
                ExternalAccount(id = "a3", account_token = "t3", oauth_provider = "google"),
                ExternalAccount(id = "a4", account_token = "t4"),
            ),
            decrypted = mapOf(
                "t1" to ExternalAccountData(email = "one@example.net", created_at = ""),
                "t2" to ExternalAccountData(email = "two@example.net", created_at = ""),
                "t3" to ExternalAccountData(email = "three@example.net", created_at = ""),
                "t4" to ExternalAccountData(email = "archive@import", created_at = ""),
            ),
        )
        assertEquals(mapOf("a1" to "one@example.net"), external_sender_ids(state))
    }

    @Test
    fun default_sender_retry_backs_off() {
        assertTrue(default_sender_retry_attempts > 0)
        val delays = (1..default_sender_retry_attempts).map { default_sender_retry_delay_ms(it) }
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.first() > 0)
    }

    @Test
    fun same_tier_update_still_moves_a_stale_primary_to_the_pinned_alias() {
        val next = next_from_alias(
            current = primary,
            current_tier = from_tier_pinned,
            resolved = resolved_from_alias(pinned, from_tier_pinned),
            alias_options = options,
            manually_selected = false,
        )
        assertEquals(resolved_from_alias(pinned, from_tier_pinned), next)
    }
}
