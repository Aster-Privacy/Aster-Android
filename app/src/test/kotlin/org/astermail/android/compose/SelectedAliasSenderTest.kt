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

import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.ui.compose.from_tier_fallback
import org.astermail.android.ui.compose.from_tier_ghost
import org.astermail.android.ui.compose.from_tier_pinned
import org.astermail.android.ui.compose.from_tier_selected_alias
import org.astermail.android.ui.compose.from_tier_thread
import org.astermail.android.ui.compose.next_from_alias
import org.astermail.android.ui.compose.owned_sender_addresses
import org.astermail.android.ui.compose.resolve_from_alias_tiered
import org.astermail.android.ui.compose.selected_alias_for_new_compose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectedAliasSenderTest {

    private val primary = "me@astermail.org"
    private val pinned = "pinned@astermail.org"
    private val selected = "shopping@astermail.org"
    private val received = "support@astermail.org"
    private val ghost = "ghost@realiased.me"
    private val options = listOf(primary, pinned, selected, received, ghost)

    private fun alias(
        local: String,
        enabled: Boolean = true,
        decryption_failed: Boolean = false,
    ) = AliasInfo(
        id = "alias-$local",
        encrypted_local_part = local,
        domain = "astermail.org",
        is_enabled = enabled,
        decryption_failed = decryption_failed,
    )

    private fun domain_address(local: String, enabled: Boolean = true) = CustomDomainAddressInfo(
        id = "domain-$local",
        domain_name = "example.net",
        encrypted_local_part = local,
        is_enabled = enabled,
    )

    private val owned = listOf(primary, pinned, selected, received)

    @Test
    fun the_selected_alias_beats_the_pinned_sender() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options, selected)

        assertEquals(selected, resolved.address)
        assertEquals(from_tier_selected_alias, resolved.tier)
    }

    @Test
    fun the_selected_alias_beats_the_fallback() {
        val resolved = resolve_from_alias_tiered(null, null, "", options, selected)

        assertEquals(selected, resolved.address)
        assertEquals(from_tier_selected_alias, resolved.tier)
    }

    @Test
    fun the_thread_address_beats_the_selected_alias() {
        val resolved = resolve_from_alias_tiered(received, null, pinned, options, selected)

        assertEquals(received, resolved.address)
        assertEquals(from_tier_thread, resolved.tier)
    }

    @Test
    fun the_ghost_match_beats_the_selected_alias() {
        val resolved = resolve_from_alias_tiered(null, ghost, pinned, options, selected)

        assertEquals(ghost, resolved.address)
        assertEquals(from_tier_ghost, resolved.tier)
    }

    @Test
    fun a_selected_alias_that_is_not_a_sender_option_falls_through() {
        val pinned_wins = resolve_from_alias_tiered(null, null, pinned, options, "gone@astermail.org")
        val fallback_wins = resolve_from_alias_tiered(null, null, "", options, "gone@astermail.org")

        assertEquals(pinned, pinned_wins.address)
        assertEquals(from_tier_pinned, pinned_wins.tier)
        assertEquals(primary, fallback_wins.address)
        assertEquals(from_tier_fallback, fallback_wins.tier)
    }

    @Test
    fun the_selected_alias_matches_the_option_spelling() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options, "  Shopping@AsterMail.org ")

        assertEquals(selected, resolved.address)
        assertEquals(from_tier_selected_alias, resolved.tier)
    }

    @Test
    fun no_selected_alias_keeps_the_old_order() {
        for (blank in listOf(null, "", "   ")) {
            val resolved = resolve_from_alias_tiered(null, null, pinned, options, blank)

            assertEquals(pinned, resolved.address)
            assertEquals(from_tier_pinned, resolved.tier)
        }
    }

    @Test
    fun the_gate_passes_the_alias_for_a_new_message() {
        assertEquals(selected, selected_alias_for_new_compose(true, selected, null, null, null, owned))
        assertEquals(selected, selected_alias_for_new_compose(true, selected, "", "new", "", owned))
    }

    @Test
    fun the_gate_is_closed_when_the_setting_is_off() {
        assertNull(selected_alias_for_new_compose(false, selected, null, null, null, owned))
    }

    @Test
    fun the_gate_leaves_replies_forwards_and_drafts_alone() {
        assertNull(selected_alias_for_new_compose(true, selected, "msg-1", "reply", null, owned))
        assertNull(selected_alias_for_new_compose(true, selected, "msg-1", "reply_all", null, owned))
        assertNull(selected_alias_for_new_compose(true, selected, "msg-1", "forward", null, owned))
        assertNull(selected_alias_for_new_compose(true, selected, null, "forward", null, owned))
        assertNull(selected_alias_for_new_compose(true, selected, null, "draft", "draft-1", owned))
        assertNull(selected_alias_for_new_compose(true, selected, null, null, "draft-1", owned))
        assertNull(selected_alias_for_new_compose(true, selected, null, "draft", null, owned))
    }

    @Test
    fun the_gate_drops_an_address_this_account_cannot_send_from() {
        assertNull(selected_alias_for_new_compose(true, "someone@example.com", null, null, null, owned))
        assertNull(selected_alias_for_new_compose(true, ghost, null, null, null, owned))
        assertNull(selected_alias_for_new_compose(true, "", null, null, null, owned))
        assertNull(selected_alias_for_new_compose(true, null, null, null, null, owned))
        assertNull(selected_alias_for_new_compose(true, selected, null, null, null, emptyList()))
    }

    @Test
    fun the_gate_returns_the_owned_spelling() {
        assertEquals(selected, selected_alias_for_new_compose(true, " SHOPPING@astermail.org", null, null, null, owned))
    }

    @Test
    fun owned_senders_skip_disabled_and_unreadable_addresses() {
        val result = owned_sender_addresses(
            user_email = primary,
            aliases = listOf(
                alias("shopping"),
                alias("paused", enabled = false),
                alias("broken", decryption_failed = true),
            ),
            custom_domain_addresses = listOf(
                domain_address("hello"),
                domain_address("old", enabled = false),
            ),
        )

        assertEquals(listOf(primary, "shopping@astermail.org", "hello@example.net"), result)
    }

    @Test
    fun owned_senders_skip_a_blank_primary_address() {
        val result = owned_sender_addresses("", listOf(alias("shopping")), emptyList())

        assertEquals(listOf("shopping@astermail.org"), result)
    }

    @Test
    fun a_disabled_alias_is_never_used_even_when_it_is_selected() {
        val owned_now = owned_sender_addresses(primary, listOf(alias("shopping", enabled = false)), emptyList())

        assertNull(selected_alias_for_new_compose(true, selected, null, null, null, owned_now))
    }

    @Test
    fun a_custom_domain_address_can_be_the_selected_sender() {
        val owned_now = owned_sender_addresses(primary, emptyList(), listOf(domain_address("hello")))

        assertEquals(
            "hello@example.net",
            selected_alias_for_new_compose(true, "hello@example.net", null, null, null, owned_now),
        )
    }

    @Test
    fun a_late_selected_alias_upgrades_the_pinned_sender() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options, selected)
        val next = next_from_alias(pinned, from_tier_pinned, resolved, options, manually_selected = false)

        assertEquals(selected, next?.address)
        assertEquals(from_tier_selected_alias, next?.tier)
    }

    @Test
    fun a_manual_pick_is_never_replaced_by_the_selected_alias() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options, selected)

        assertNull(next_from_alias(pinned, from_tier_pinned, resolved, options, manually_selected = true))
    }

    @Test
    fun the_selected_alias_never_replaces_a_thread_address() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options, selected)

        assertNull(next_from_alias(received, from_tier_thread, resolved, options, manually_selected = false))
    }

    @Test
    fun the_tiers_keep_their_order() {
        assertTrue(from_tier_thread < from_tier_ghost)
        assertTrue(from_tier_ghost < from_tier_selected_alias)
        assertTrue(from_tier_selected_alias < from_tier_pinned)
        assertTrue(from_tier_pinned < from_tier_fallback)
    }
}
