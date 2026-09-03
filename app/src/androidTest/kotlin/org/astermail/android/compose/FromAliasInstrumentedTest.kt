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

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.ui.common.resolve_primary_sender_email
import org.astermail.android.ui.compose.compose_identity_snapshot
import org.astermail.android.ui.compose.compose_seed_store
import org.astermail.android.ui.compose.compute_received_on_alias
import org.astermail.android.ui.compose.from_tier_fallback
import org.astermail.android.ui.compose.from_tier_ghost
import org.astermail.android.ui.compose.from_tier_pinned
import org.astermail.android.ui.compose.from_tier_thread
import org.astermail.android.ui.compose.next_from_alias
import org.astermail.android.ui.compose.resolve_from_alias_tiered
import org.astermail.android.ui.compose.resolved_from_alias
import org.astermail.android.ui.mail.extract_delivered_to
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FromAliasInstrumentedTest {

    private val user_email = "adam@astermail.org"
    private val pinned = "shopping@astermail.org"
    private val received = "support@astermail.org"
    private val ghost = "ghost@realiased.me"
    private val options = listOf(user_email, pinned, received, ghost)

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun reset_seed_store() {
        compose_seed_store.clear(context)
    }

    @After
    fun clear_seed_store() {
        compose_seed_store.clear(context)
    }

    @Test
    fun a_pinned_alias_id_resolves_to_its_address() {
        assertEquals(
            pinned,
            resolve_primary_sender_email(
                default_sender_id = "alias-shopping",
                user_email = user_email,
                aliases = listOf(
                    AliasInfo(
                        id = "alias-shopping",
                        encrypted_local_part = "shopping",
                        domain = "astermail.org",
                    ),
                ),
                ghost_aliases = emptyList(),
            ),
        )
    }

    @Test
    fun a_pinned_ghost_id_resolves_to_its_address() {
        assertEquals(
            ghost,
            resolve_primary_sender_email(
                default_sender_id = "ghost-g1",
                user_email = user_email,
                aliases = emptyList(),
                ghost_aliases = listOf(
                    GhostAlias(id = "g1", domain = "realiased.me", decrypted_address = ghost),
                ),
            ),
        )
    }

    @Test
    fun a_new_compose_starts_on_the_pinned_alias() {
        val resolved = resolve_from_alias_tiered(null, null, pinned, options)
        assertEquals(pinned, resolved.address)
        assertEquals(from_tier_pinned, resolved.tier)
    }

    @Test
    fun a_pinned_alias_survives_a_round_trip_through_device_storage() {
        compose_seed_store.publish_identity(
            context,
            compose_identity_snapshot(
                user_email = user_email,
                alias_options = options,
                primary_sender_email = pinned,
            ),
        )
        val stored = compose_seed_store.read_identity(context, user_email)
        assertEquals(pinned, stored.primary_sender_email)
        assertEquals(options, stored.alias_options)
        assertEquals(
            pinned,
            resolve_from_alias_tiered(
                null,
                null,
                stored.primary_sender_email,
                stored.alias_options,
            ).address,
        )
    }

    @Test
    fun a_seed_from_another_account_is_never_reused() {
        compose_seed_store.publish_identity(
            context,
            compose_identity_snapshot(
                user_email = user_email,
                alias_options = options,
                primary_sender_email = pinned,
            ),
        )
        val other = compose_seed_store.read_identity(context, "someone@astermail.org")
        assertEquals("", other.primary_sender_email)
    }

    @Test
    fun a_late_pinned_value_replaces_the_fallback_shown_first() {
        val next = next_from_alias(
            current = user_email,
            current_tier = from_tier_fallback,
            resolved = resolved_from_alias(pinned, from_tier_pinned),
            alias_options = options,
            manually_selected = false,
        )
        assertEquals(pinned, next?.address)
        assertEquals(from_tier_pinned, next?.tier)
    }

    @Test
    fun a_reply_uses_the_address_the_message_was_delivered_to() {
        val delivered = extract_delivered_to(
            listOf(
                "Return-Path" to "<sender@example.com>",
                "Delivered-To" to "<Support@AsterMail.org>",
            ),
        )
        assertEquals(received, delivered)
        val alias = compute_received_on_alias(listOf(delivered!!), options, user_email)
        assertEquals(received, alias)
        val resolved = resolve_from_alias_tiered(alias, null, pinned, options)
        assertEquals(received, resolved.address)
        assertEquals(from_tier_thread, resolved.tier)
    }

    @Test
    fun a_reply_matches_an_alias_that_only_appears_in_cc() {
        val alias = compute_received_on_alias(
            listOf("someone@example.com", " ", "SHOPPING@astermail.org"),
            options,
            user_email,
        )
        assertEquals(pinned, alias)
    }

    @Test
    fun a_reply_prefers_an_alias_over_the_primary_address() {
        val alias = compute_received_on_alias(listOf(user_email, received), options, user_email)
        assertEquals(received, alias)
    }

    @Test
    fun the_received_alias_outranks_the_pinned_one_on_a_reply() {
        val next = next_from_alias(
            current = pinned,
            current_tier = from_tier_pinned,
            resolved = resolved_from_alias(received, from_tier_thread),
            alias_options = options,
            manually_selected = false,
        )
        assertEquals(received, next?.address)
        assertEquals(from_tier_thread, next?.tier)
    }

    @Test
    fun a_ghost_thread_keeps_replying_from_the_ghost_address() {
        val resolved = resolve_from_alias_tiered(null, ghost, pinned, options)
        assertEquals(ghost, resolved.address)
        assertEquals(from_tier_ghost, resolved.tier)
    }

    @Test
    fun a_manual_choice_is_never_overridden_by_a_late_resolution() {
        assertNull(
            next_from_alias(
                current = user_email,
                current_tier = from_tier_fallback,
                resolved = resolved_from_alias(received, from_tier_thread),
                alias_options = options,
                manually_selected = true,
            ),
        )
    }
}
