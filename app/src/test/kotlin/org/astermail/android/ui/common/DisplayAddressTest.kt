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

package org.astermail.android.ui.common

import org.astermail.android.api.settings.AliasInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayAddressTest {

    private val user_email = "nikola@astermail.org"
    private val custom = "info@example.com"

    @Test
    fun shows_default_sender_once_loaded() {
        val shown = resolve_display_address("domain-abc", true, custom, "", user_email)
        assertEquals(custom, shown)
    }

    @Test
    fun shows_cached_default_sender_before_load() {
        val shown = resolve_display_address(null, false, user_email, custom, user_email)
        assertEquals(custom, shown)
    }

    @Test
    fun keeps_cached_default_sender_while_addresses_load() {
        val shown = resolve_display_address("domain-abc", true, user_email, custom, user_email)
        assertEquals(custom, shown)
    }

    @Test
    fun switching_back_to_primary_drops_cached_sender() {
        val shown = resolve_display_address("primary", true, user_email, custom, user_email)
        assertEquals(user_email, shown)
    }

    @Test
    fun falls_back_to_account_address_without_cache() {
        val shown = resolve_display_address(null, false, user_email, "", user_email)
        assertEquals(user_email, shown)
    }

    @Test
    fun resolves_alias_default_sender_end_to_end() {
        val alias = AliasInfo(id = "alias-1", encrypted_local_part = "info", domain = "astermail.org")
        val resolved = resolve_primary_sender_email("alias-1", user_email, listOf(alias), emptyList())
        assertEquals("info@astermail.org", resolve_display_address("alias-1", true, resolved, "", user_email))
    }
}
