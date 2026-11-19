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

package org.astermail.android.settings

import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SidebarAliasItemsTest {

    private fun alias(
        id: String,
        local_part: String,
        domain: String = "astermail.org",
        is_enabled: Boolean = true,
        decryption_failed: Boolean = false,
    ) = AliasInfo(
        id = id,
        encrypted_local_part = local_part,
        domain = domain,
        is_enabled = is_enabled,
        decryption_failed = decryption_failed,
    )

    private fun domain_address(
        id: String,
        local_part: String,
        domain: String = "example.com",
        decryption_failed: Boolean = false,
    ) = CustomDomainAddressInfo(
        id = id,
        domain_name = domain,
        encrypted_local_part = local_part,
        decryption_failed = decryption_failed,
    )

    @Test
    fun generated_alias_with_a_long_local_part_is_listed() {
        val result = sidebar_alias_items(
            listOf(alias("a1", "curious.penguin7k2mzq4x"), alias("a2", "shop")),
            emptyList(),
        )

        assertEquals(
            listOf("curious.penguin7k2mzq4x@astermail.org", "shop@astermail.org"),
            result.map { it.address },
        )
    }

    @Test
    fun long_letters_only_local_part_is_listed() {
        val result = sidebar_alias_items(
            listOf(alias("a1", "newsletterssubscriptions")),
            emptyList(),
        )

        assertEquals(listOf("newsletterssubscriptions@astermail.org"), result.map { it.address })
    }

    @Test
    fun long_custom_domain_address_is_listed() {
        val result = sidebar_alias_items(
            emptyList(),
            listOf(domain_address("d1", "billing.department2026")),
        )

        assertEquals(listOf("billing.department2026@example.com"), result.map { it.address })
    }

    @Test
    fun disabled_aliases_stay_out_of_the_side_menu() {
        val result = sidebar_alias_items(
            listOf(alias("a1", "shop", is_enabled = false), alias("a2", "news")),
            emptyList(),
        )

        assertEquals(listOf("news@astermail.org"), result.map { it.address })
    }

    @Test
    fun unreadable_addresses_stay_out_of_the_side_menu() {
        val result = sidebar_alias_items(
            listOf(alias("a1", "", decryption_failed = true), alias("a2", "news")),
            listOf(domain_address("d1", "", decryption_failed = true)),
        )

        assertEquals(listOf("news@astermail.org"), result.map { it.address })
    }

    @Test
    fun every_domain_is_listed_under_the_polish_locale() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("pl-PL"))
        try {
            val result = sidebar_alias_items(
                listOf(
                    alias("a1", "curious.penguin7k2mzq4x", domain = "astermail.org"),
                    alias("a2", "quiet.harbor4bq7xz2m", domain = "aster.cx"),
                    alias("a3", "golden.meadow2n6vk3pq", domain = "astermail.org"),
                    alias("a4", "silent.river5h7tm2wq", domain = "aster.cx"),
                    alias("a5", "zakupy", domain = "astermail.net"),
                ),
                emptyList(),
            )

            assertEquals(
                listOf(
                    "curious.penguin7k2mzq4x@astermail.org",
                    "quiet.harbor4bq7xz2m@aster.cx",
                    "golden.meadow2n6vk3pq@astermail.org",
                    "silent.river5h7tm2wq@aster.cx",
                    "zakupy@astermail.net",
                ),
                result.map { it.address },
            )
        } finally {
            Locale.setDefault(previous)
        }
    }
}
