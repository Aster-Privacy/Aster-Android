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

import org.astermail.android.ui.compose.resolve_reaction_sender_identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReactionSenderIdentityTest {
    private val primary = "me@astermail.org"
    private val alias = "shopdeals@aster.cx"
    private val options = listOf(primary, alias)
    private val hashes = mapOf(alias to "hash_alias")

    @Test
    fun uses_alias_the_message_was_received_on() {
        val identity = resolve_reaction_sender_identity(
            own_recipient_addresses = listOf(alias),
            message_sender_email = "someone@example.com",
            is_own_message = false,
            alias_options = options,
            alias_hash_map = hashes,
            user_email = primary,
        )
        assertEquals(alias, identity.email)
        assertEquals("hash_alias", identity.alias_hash)
    }

    @Test
    fun falls_back_to_primary_when_received_on_primary() {
        val identity = resolve_reaction_sender_identity(
            own_recipient_addresses = listOf(primary),
            message_sender_email = "someone@example.com",
            is_own_message = false,
            alias_options = options,
            alias_hash_map = hashes,
            user_email = primary,
        )
        assertEquals(primary, identity.email)
        assertNull(identity.alias_hash)
    }

    @Test
    fun falls_back_to_primary_when_alias_hash_missing() {
        val identity = resolve_reaction_sender_identity(
            own_recipient_addresses = listOf(alias),
            message_sender_email = "someone@example.com",
            is_own_message = false,
            alias_options = options,
            alias_hash_map = emptyMap(),
            user_email = primary,
        )
        assertEquals(primary, identity.email)
        assertNull(identity.alias_hash)
    }

    @Test
    fun matches_alias_case_insensitively() {
        val identity = resolve_reaction_sender_identity(
            own_recipient_addresses = listOf("  ShopDeals@ASTER.cx "),
            message_sender_email = "someone@example.com",
            is_own_message = false,
            alias_options = options,
            alias_hash_map = hashes,
            user_email = primary,
        )
        assertEquals(alias, identity.email)
        assertEquals("hash_alias", identity.alias_hash)
    }

    @Test
    fun own_message_reacts_from_the_address_it_was_sent_from() {
        val identity = resolve_reaction_sender_identity(
            own_recipient_addresses = listOf("someone@example.com"),
            message_sender_email = alias,
            is_own_message = true,
            alias_options = options,
            alias_hash_map = hashes,
            user_email = primary,
        )
        assertEquals(alias, identity.email)
        assertEquals("hash_alias", identity.alias_hash)
    }

    @Test
    fun unknown_recipient_falls_back_to_primary() {
        val identity = resolve_reaction_sender_identity(
            own_recipient_addresses = listOf("stranger@example.com"),
            message_sender_email = "someone@example.com",
            is_own_message = false,
            alias_options = options,
            alias_hash_map = hashes,
            user_email = primary,
        )
        assertEquals(primary, identity.email)
        assertNull(identity.alias_hash)
    }
}
