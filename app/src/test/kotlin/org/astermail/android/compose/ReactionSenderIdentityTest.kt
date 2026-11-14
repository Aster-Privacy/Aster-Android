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

import org.astermail.android.api.ghost.GhostAlias
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.astermail.android.ui.compose.ReactionSenderIdentity
import org.astermail.android.ui.compose.domain_address_routing_token
import org.astermail.android.ui.compose.reaction_routing_token_map
import org.astermail.android.ui.compose.resolve_reaction_sender_identity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class ReactionSenderIdentityTest {
    private val primary = "me@astermail.org"
    private val alias = "shopdeals@aster.cx"
    private val other_alias = "newsletters@aster.cx"
    private val ghost = "quietfox4821@aster.cx"
    private val options = listOf(primary, alias, other_alias, ghost)
    private val hashes = mapOf(
        alias to "hash_alias",
        other_alias to "hash_other",
        ghost to "hash_ghost",
    )
    private val tokens = mapOf(
        "hash_alias" to alias,
        "hash_other" to other_alias,
        "hash_ghost" to ghost,
    )

    private fun resolve(
        visible: List<String> = emptyList(),
        sender: String = "someone@example.com",
        is_own_message: Boolean = false,
        alias_options: List<String> = options,
        alias_hash_map: Map<String, String> = hashes,
        user_email: String = primary,
        delivered_to: String? = null,
        routing_token: String? = null,
        routing_token_map: Map<String, String> = tokens,
    ): ReactionSenderIdentity? = resolve_reaction_sender_identity(
        own_recipient_addresses = visible,
        message_sender_email = sender,
        is_own_message = is_own_message,
        alias_options = alias_options,
        alias_hash_map = alias_hash_map,
        user_email = user_email,
        delivered_to = delivered_to,
        routing_token = routing_token,
        routing_token_map = routing_token_map,
    )

    @Test
    fun uses_alias_the_message_was_received_on() {
        val identity = resolve(visible = listOf(alias))
        assertEquals(alias, identity?.email)
        assertEquals("hash_alias", identity?.alias_hash)
    }

    @Test
    fun uses_primary_when_the_message_is_addressed_to_primary() {
        val identity = resolve(visible = listOf(primary))
        assertEquals(primary, identity?.email)
        assertNull(identity?.alias_hash)
    }

    @Test
    fun refuses_when_the_alias_hash_is_missing() {
        assertNull(resolve(visible = listOf(alias), alias_hash_map = emptyMap()))
        assertNull(resolve(visible = listOf(alias), alias_hash_map = mapOf(alias to " ")))
    }

    @Test
    fun matches_alias_case_insensitively() {
        val identity = resolve(visible = listOf("  ShopDeals@ASTER.cx "))
        assertEquals(alias, identity?.email)
        assertEquals("hash_alias", identity?.alias_hash)
    }

    @Test
    fun own_message_reacts_from_the_address_it_was_sent_from() {
        val identity = resolve(
            visible = listOf("someone@example.com"),
            sender = alias,
            is_own_message = true,
        )
        assertEquals(alias, identity?.email)
        assertEquals("hash_alias", identity?.alias_hash)
    }

    @Test
    fun own_message_from_primary_reacts_from_primary() {
        val identity = resolve(
            visible = listOf("someone@example.com"),
            sender = primary,
            is_own_message = true,
        )
        assertEquals(primary, identity?.email)
        assertNull(identity?.alias_hash)
    }

    @Test
    fun own_message_from_an_unknown_address_is_refused() {
        assertNull(
            resolve(
                visible = listOf("someone@example.com"),
                sender = "stranger@example.com",
                is_own_message = true,
            ),
        )
    }

    @Test
    fun refuses_when_no_visible_recipient_is_an_own_address() {
        assertNull(resolve(visible = listOf("stranger@example.com")))
    }

    @Test
    fun refuses_when_nothing_shows_where_the_message_was_delivered() {
        assertNull(resolve())
        assertNull(resolve(delivered_to = " ", routing_token = " "))
    }

    @Test
    fun routing_token_selects_the_alias_without_visible_recipients() {
        val identity = resolve(routing_token = "hash_alias")
        assertEquals(alias, identity?.email)
        assertEquals("hash_alias", identity?.alias_hash)
    }

    @Test
    fun routing_token_wins_over_visible_primary() {
        val identity = resolve(visible = listOf(primary), routing_token = "hash_alias")
        assertEquals(alias, identity?.email)
    }

    @Test
    fun routing_token_wins_over_delivered_to_and_visible_aliases() {
        val identity = resolve(
            visible = listOf(other_alias),
            delivered_to = other_alias,
            routing_token = "hash_alias",
        )
        assertEquals(alias, identity?.email)
    }

    @Test
    fun routing_token_selects_a_ghost_alias() {
        val identity = resolve(routing_token = "hash_ghost")
        assertEquals(ghost, identity?.email)
        assertEquals("hash_ghost", identity?.alias_hash)
    }

    @Test
    fun unknown_routing_token_does_not_select_primary() {
        assertNull(resolve(routing_token = "hash_of_something_else"))
    }

    @Test
    fun unknown_routing_token_falls_through_to_visible_recipients() {
        val identity = resolve(visible = listOf(primary), routing_token = "hash_of_something_else")
        assertEquals(primary, identity?.email)
        assertNull(identity?.alias_hash)
    }

    @Test
    fun delivered_to_selects_the_alias_without_visible_recipients() {
        val identity = resolve(delivered_to = "ShopDeals@aster.cx")
        assertEquals(alias, identity?.email)
        assertEquals("hash_alias", identity?.alias_hash)
    }

    @Test
    fun delivered_to_alias_wins_over_visible_primary() {
        val identity = resolve(visible = listOf(primary), delivered_to = alias)
        assertEquals(alias, identity?.email)
    }

    @Test
    fun delivered_to_primary_selects_primary_without_visible_recipients() {
        val identity = resolve(delivered_to = primary)
        assertEquals(primary, identity?.email)
        assertNull(identity?.alias_hash)
    }

    @Test
    fun visible_alias_wins_over_delivered_to_primary() {
        val identity = resolve(visible = listOf(alias), delivered_to = primary)
        assertEquals(alias, identity?.email)
    }

    @Test
    fun delivered_to_alias_without_a_hash_does_not_select_primary() {
        assertNull(resolve(delivered_to = alias, alias_hash_map = emptyMap()))
    }

    @Test
    fun delivered_to_an_unknown_address_is_refused() {
        assertNull(resolve(delivered_to = "stranger@example.com"))
    }

    @Test
    fun a_blank_primary_address_is_refused() {
        assertNull(resolve(visible = listOf(alias), user_email = ""))
    }

    @Test
    fun domain_routing_token_ignores_case_and_dots_in_the_local_part() {
        val expected = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest("janedoe@example.org".toByteArray(Charsets.UTF_8)),
        )
        assertEquals(expected, domain_address_routing_token("Jane.Doe", "Example.ORG"))
        assertEquals(expected, domain_address_routing_token("janedoe", "example.org"))
    }

    @Test
    fun routing_token_map_covers_aliases_ghosts_and_domain_addresses() {
        val map = reaction_routing_token_map(
            aliases = listOf(
                AliasInfo(
                    id = "alias_1",
                    encrypted_local_part = "shopdeals",
                    domain = "aster.cx",
                    alias_address_hash = "hash_alias",
                ),
            ),
            custom_domain_addresses = listOf(
                CustomDomainAddressInfo(
                    id = "domain_1",
                    domain_name = "example.org",
                    encrypted_local_part = "jane.doe",
                    local_part_hash = "hash_domain",
                ),
            ),
            ghost_aliases = listOf(
                GhostAlias(
                    id = "ghost_1",
                    domain = "aster.cx",
                    alias_address_hash = "hash_ghost",
                    decrypted_address = ghost,
                ),
            ),
        )
        assertEquals(alias, map["hash_alias"])
        assertEquals(ghost, map["hash_ghost"])
        assertEquals(
            "jane.doe@example.org",
            map[domain_address_routing_token("jane.doe", "example.org")],
        )
        assertEquals(3, map.size)
    }

    @Test
    fun routing_token_map_leaves_out_addresses_that_cannot_send() {
        val map = reaction_routing_token_map(
            aliases = listOf(
                AliasInfo(
                    id = "alias_off",
                    encrypted_local_part = "shopdeals",
                    domain = "aster.cx",
                    alias_address_hash = "hash_off",
                    is_enabled = false,
                ),
                AliasInfo(
                    id = "alias_unreadable",
                    domain = "aster.cx",
                    alias_address_hash = "hash_unreadable",
                    decryption_failed = true,
                ),
                AliasInfo(
                    id = "alias_no_hash",
                    encrypted_local_part = "newsletters",
                    domain = "aster.cx",
                ),
            ),
            custom_domain_addresses = listOf(
                CustomDomainAddressInfo(
                    id = "domain_off",
                    domain_name = "example.org",
                    encrypted_local_part = "jane",
                    is_enabled = false,
                ),
            ),
            ghost_aliases = listOf(
                GhostAlias(
                    id = "ghost_unreadable",
                    domain = "aster.cx",
                    alias_address_hash = "hash_ghost_unreadable",
                    decryption_failed = true,
                ),
                GhostAlias(
                    id = "ghost_no_hash",
                    domain = "aster.cx",
                    decrypted_address = ghost,
                ),
                GhostAlias(
                    id = "ghost_off",
                    domain = "aster.cx",
                    alias_address_hash = "hash_ghost_off",
                    decrypted_address = "sleepyowl1234@aster.cx",
                    is_enabled = false,
                ),
            ),
        )
        assertTrue(map.isEmpty())
        assertFalse(map.containsValue("ghost@aster.cx"))
    }
}
