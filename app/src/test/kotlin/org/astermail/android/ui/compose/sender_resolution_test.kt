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

package org.astermail.android.ui.compose

import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class sender_resolution_test {
    private val primary = "me@astermail.org"

    private fun inbound(vararg to: String, delivered_to: String? = null, cc: List<String> = emptyList()) =
        compose_thread_message(
            id = "m1",
            sender_email = "friend@example.com",
            to_addresses = to.toList(),
            cc_addresses = cc,
            delivered_to = delivered_to,
        )

    private fun pending_state(
        is_thread: Boolean = true,
        is_draft: Boolean = false,
        manual: Boolean = false,
        from: String = primary,
        thread_settled: Boolean = true,
        identities_settled: Boolean = true,
        draft_settled: Boolean = true,
        has_change: Boolean = false,
    ) = sender_resolution_state(is_thread, is_draft, manual, from, thread_settled, identities_settled, draft_settled, has_change)

    @Test
    fun reply_expects_the_alias_the_mail_was_sent_to() {
        val options = listOf(primary, "shop@astermail.org")
        assertEquals("shop@astermail.org", expected_sender_address(inbound("shop@astermail.org"), options, primary))
    }

    @Test
    fun matching_ignores_case() {
        val options = listOf(primary, "Shop@Example.com")
        assertEquals("Shop@Example.com", expected_sender_address(inbound("shop@example.COM"), options, primary))
    }

    @Test
    fun plus_tag_matches_the_base_alias() {
        val options = listOf(primary, "shop@d.com")
        assertEquals("shop@d.com", expected_sender_address(inbound("shop+x@d.com"), options, primary))
    }

    @Test
    fun exact_plus_address_wins_over_its_base() {
        val options = listOf(primary, "shop@d.com", "shop+x@d.com")
        assertEquals("shop+x@d.com", expected_sender_address(inbound("shop+x@d.com"), options, primary))
    }

    @Test
    fun dotted_and_dotless_aster_addresses_match() {
        val options = listOf(primary, "johnsmith@astermail.org")
        assertEquals(
            "johnsmith@astermail.org",
            expected_sender_address(inbound("john.smith@astermail.org"), options, primary),
        )
        assertTrue(same_sender_address("j.s@aster.cx", "js@aster.cx"))
    }

    @Test
    fun dots_still_matter_on_other_domains() {
        val options = listOf(primary, "johnsmith@d.com")
        assertNull(expected_sender_address(inbound("john.smith@d.com"), options, primary))
    }

    @Test
    fun delivered_to_and_cc_are_considered() {
        val options = listOf(primary, "a@d.com", "b@d.com")
        assertEquals("b@d.com", expected_sender_address(inbound("other@x.com", delivered_to = "b@d.com"), options, primary))
        assertEquals("a@d.com", expected_sender_address(inbound("other@x.com", cc = listOf("A@d.com")), options, primary))
    }

    @Test
    fun several_matches_take_the_first_non_primary_one() {
        val options = listOf(primary, "a@d.com", "b@d.com")
        assertEquals("a@d.com", expected_sender_address(inbound(primary, "a@d.com", "b@d.com"), options, primary))
        assertEquals(primary, expected_sender_address(inbound(primary), options, primary))
    }

    @Test
    fun deleted_alias_gives_no_expected_address() {
        assertNull(expected_sender_address(inbound("gone@d.com"), listOf(primary), primary))
    }

    @Test
    fun follow_up_from_sent_expects_the_sent_from() {
        val sent = compose_thread_message(
            id = "s1",
            sender_email = "shop@d.com",
            to_addresses = listOf("vendor@example.com"),
            is_sent = true,
        )
        assertEquals("shop@d.com", expected_sender_address(sent, listOf(primary, "shop@d.com"), primary))
    }

    @Test
    fun display_name_wrappers_are_unwrapped() {
        assertEquals("shop@d.com", match_sender_option("Shop <shop@d.com>", listOf(primary, "shop@d.com")))
    }

    @Test
    fun live_resolution_uses_follow_up_sender() {
        val thread = compose_thread_snapshot(
            item_id = "t1",
            messages = listOf(
                compose_thread_message(
                    id = "s1",
                    sender_email = "shop@d.com",
                    to_addresses = listOf("v@x.com"),
                    is_sent = true,
                ),
            ),
        )
        assertEquals(
            "shop@d.com",
            resolve_live_received_on_alias("s1", "reply", thread, listOf(primary, "shop@d.com"), primary),
        )
    }

    @Test
    fun manual_pick_never_waits() {
        assertFalse(
            sender_resolution_pending(pending_state(manual = true, thread_settled = false, identities_settled = false)),
        )
    }

    @Test
    fun thread_compose_waits_for_thread_and_identities() {
        assertTrue(sender_resolution_pending(pending_state(thread_settled = false)))
        assertTrue(sender_resolution_pending(pending_state(identities_settled = false)))
        assertTrue(sender_resolution_pending(pending_state(has_change = true)))
        assertFalse(sender_resolution_pending(pending_state()))
    }

    @Test
    fun blank_from_is_always_pending() {
        assertTrue(sender_resolution_pending(pending_state(is_thread = false, from = "")))
        assertTrue(sender_resolution_pending(pending_state(manual = true, from = "")))
    }

    @Test
    fun new_compose_does_not_wait_on_thread() {
        assertFalse(
            sender_resolution_pending(pending_state(is_thread = false, thread_settled = false, identities_settled = false)),
        )
    }

    @Test
    fun draft_waits_for_its_saved_from() {
        assertTrue(sender_resolution_pending(pending_state(is_thread = false, is_draft = true, draft_settled = false)))
        assertFalse(sender_resolution_pending(pending_state(is_thread = false, is_draft = true, identities_settled = false)))
    }

    @Test
    fun queued_send_fires_once_resolved_and_drops_after_timeout() {
        assertEquals(queued_send_action.idle, queued_send_step(null, 0L, pending = true))
        assertEquals(queued_send_action.wait, queued_send_step(1_000L, 5_000L, pending = true))
        assertEquals(queued_send_action.fire, queued_send_step(1_000L, 5_000L, pending = false))
        assertEquals(
            queued_send_action.drop,
            queued_send_step(1_000L, 1_000L + queued_send_timeout_ms, pending = true),
        )
        assertEquals(queued_send_action.fire, queued_send_step(1_000L, 99_000L, pending = false))
    }

    @Test
    fun mismatch_only_when_resolved_and_different() {
        assertTrue(sender_mismatch(true, true, "shop@d.com", primary))
        assertFalse(sender_mismatch(true, false, "shop@d.com", primary))
        assertFalse(sender_mismatch(true, true, "shop@d.com", "SHOP@d.com"))
        assertFalse(sender_mismatch(true, true, "js@astermail.org", "j.s@astermail.org"))
        assertFalse(sender_mismatch(true, true, null, primary))
        assertFalse(sender_mismatch(false, true, "shop@d.com", primary))
    }

    private val ghost = org.astermail.android.api.ghost.GhostAlias(
        id = "g1",
        domain = "realiased.me",
        decrypted_address = "quiet.fox@realiased.me",
    )

    @Test
    fun sending_rows_list_every_sendable_address_with_primary_first() {
        val aliases = listOf(
            AliasInfo(id = "a1", encrypted_local_part = "shop", domain = "astermail.org"),
            AliasInfo(id = "a2", encrypted_local_part = "news", domain = "aster.cx"),
            AliasInfo(id = "a3", domain = "astermail.org", decryption_failed = true),
        )
        val domain = listOf(
            CustomDomainAddressInfo(id = "d1", domain_name = "d.com", encrypted_local_part = "hi"),
            CustomDomainAddressInfo(id = "d2", domain_name = "d.com", decryption_failed = true),
        )
        val undecrypted_ghost = org.astermail.android.api.ghost.GhostAlias(id = "g2", domain = "realiased.me")
        val rows = sending_block_rows(
            primary_address = primary,
            primary_blocked = true,
            aliases = aliases,
            ghost_aliases = listOf(ghost, undecrypted_ghost),
            domain_addresses = domain,
            blocked_alias_ids = setOf("a2", "g1"),
            blocked_domain_address_ids = emptySet(),
        )
        assertEquals(
            listOf(primary, "hi@d.com", "news@aster.cx", "quiet.fox@realiased.me", "shop@astermail.org"),
            rows.map { it.address },
        )
        assertEquals("primary", rows.first().kind)
        assertTrue(rows.first().sending_disabled)
        assertTrue(rows.first { it.id == "a2" }.sending_disabled)
        assertTrue(rows.first { it.id == "g1" }.sending_disabled)
        assertEquals("alias", rows.first { it.id == "g1" }.kind)
        assertEquals("domain_address", rows.first { it.id == "d1" }.kind)
        assertFalse(rows.first { it.id == "a1" }.sending_disabled)
    }

    @Test
    fun sending_rows_search_matches_primary_and_ghost_aliases() {
        val rows = sending_block_rows(
            primary_address = primary,
            primary_blocked = false,
            aliases = listOf(AliasInfo(id = "a1", encrypted_local_part = "shop", domain = "astermail.org")),
            ghost_aliases = listOf(ghost),
            domain_addresses = emptyList(),
            blocked_alias_ids = emptySet(),
            blocked_domain_address_ids = emptySet(),
        )
        assertEquals(listOf(primary), filter_sending_block_rows(rows, " ME@ ").map { it.address })
        assertEquals(listOf("quiet.fox@realiased.me"), filter_sending_block_rows(rows, "fox").map { it.address })
        assertEquals(3, filter_sending_block_rows(rows, "").size)
    }

    @Test
    fun ghost_alias_already_listed_as_alias_is_not_repeated() {
        val rows = sending_block_rows(
            primary_address = null,
            primary_blocked = false,
            aliases = listOf(AliasInfo(id = "g1", encrypted_local_part = "quiet.fox", domain = "realiased.me")),
            ghost_aliases = listOf(ghost),
            domain_addresses = emptyList(),
            blocked_alias_ids = emptySet(),
            blocked_domain_address_ids = emptySet(),
        )
        assertEquals(listOf("g1"), rows.map { it.id })
    }

    @Test
    fun sending_rows_scale_to_hundreds_of_addresses() {
        val aliases = (0 until 600).map { AliasInfo(id = "a$it", encrypted_local_part = "box$it", domain = "astermail.org") }
        val rows = sending_block_rows(primary, false, aliases, emptyList(), emptyList(), setOf("a599"), emptySet())
        assertEquals(601, rows.size)
        assertEquals(primary, rows.first().address)
        assertTrue(rows.first { it.id == "a599" }.sending_disabled)
        assertEquals(11, filter_sending_block_rows(rows, "box59").size)
    }

    @Test
    fun all_off_notice_only_when_every_address_is_disabled() {
        val aliases = listOf(AliasInfo(id = "a1", encrypted_local_part = "shop", domain = "astermail.org"))
        val all_off = sending_block_rows(primary, true, aliases, emptyList(), emptyList(), setOf("a1"), emptySet())
        val one_on = sending_block_rows(primary, false, aliases, emptyList(), emptyList(), setOf("a1"), emptySet())
        assertTrue(every_sending_address_disabled(all_off))
        assertFalse(every_sending_address_disabled(one_on))
        assertFalse(every_sending_address_disabled(emptyList()))
    }

    @Test
    fun blocked_primary_and_ghost_alias_cannot_be_sent_from() {
        val keys = sending_disabled_addresses(
            primary_address = primary,
            primary_blocked = true,
            aliases = emptyList(),
            ghost_aliases = listOf(ghost),
            domain_addresses = emptyList(),
            blocked_alias_ids = setOf("g1"),
            blocked_domain_address_ids = emptySet(),
        )
        assertTrue(is_sending_disabled_sender("Me@AsterMail.org", keys))
        assertTrue(is_sending_disabled_sender("quietfox@realiased.me", keys))
        assertFalse(is_sending_disabled_sender("other@astermail.org", keys))
    }

    @Test
    fun disabled_sender_keys_match_the_server_normalization() {
        val aliases = listOf(AliasInfo(id = "a1", encrypted_local_part = "john.smith", domain = "astermail.org"))
        val domain = listOf(CustomDomainAddressInfo(id = "d1", domain_name = "d.com", encrypted_local_part = "sales.team"))
        val keys = sending_disabled_addresses(null, false, aliases, emptyList(), domain, setOf("a1"), setOf("d1"))
        assertTrue(is_sending_disabled_sender("JohnSmith@astermail.org", keys))
        assertTrue(is_sending_disabled_sender("john.smith+news@astermail.org", keys))
        assertTrue(is_sending_disabled_sender("Sales Team <salesteam@D.com>", keys))
        assertTrue(is_sending_disabled_sender("sales.team+q4@d.com", keys))
        assertFalse(is_sending_disabled_sender(primary, keys))
        assertFalse(is_sending_disabled_sender("", keys))
    }

    @Test
    fun primary_is_blocked_only_when_the_server_says_so() {
        val keys = sending_disabled_addresses(primary, false, emptyList(), emptyList(), emptyList(), emptySet(), emptySet())
        assertFalse(is_sending_disabled_sender(primary, keys))
    }

    @Test
    fun send_gate_queues_blocks_then_requires_mismatch_confirmation() {
        assertEquals(send_gate_action.queue, send_gate(true, false, false, false))
        assertEquals(send_gate_action.blocked, send_gate(true, true, true, true))
        assertEquals(send_gate_action.blocked, send_gate(false, false, true, false))
        assertEquals(send_gate_action.confirm_mismatch, send_gate(false, false, false, true))
        assertEquals(send_gate_action.confirm_mismatch, send_gate(true, true, false, true))
        assertEquals(send_gate_action.proceed, send_gate(false, false, false, false))
    }

    @Test
    fun a_queued_fast_send_still_stops_at_the_mismatch() {
        assertEquals(send_gate_action.queue, send_gate(true, false, false, false))
        assertEquals(queued_send_action.fire, queued_send_step(1_000L, 1_200L, pending = false))
        val mismatch = sender_mismatch(true, true, "shop@d.com", primary)
        assertEquals(send_gate_action.confirm_mismatch, send_gate(false, false, false, mismatch))
    }
}
