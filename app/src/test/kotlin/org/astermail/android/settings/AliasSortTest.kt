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

import java.util.Locale
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.api.settings.CustomDomainAddressInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class AliasSortTest {

    private fun alias(local: String, created_at: String, is_pinned: Boolean = false) = AliasInfo(
        id = local,
        encrypted_local_part = local,
        domain = "astermail.org",
        created_at = created_at,
        is_pinned = is_pinned,
    )

    private val aliases = listOf(
        alias("shop", "2026-03-01T10:00:00Z"),
        alias("Bank", "2026-01-15T10:00:00Z"),
        alias("news10", "2026-05-20T10:00:00Z"),
        alias("news2", "2026-02-10T10:00:00Z"),
        alias("ébène", "2026-04-05T10:00:00Z"),
    )

    private fun order(sort: AliasSort, items: List<AliasInfo> = aliases) =
        sort_aliases(items, sort, Locale.ENGLISH, { it.address }, { it.created_at }, { it.is_pinned })
            .map { it.encrypted_local_part }

    @Test
    fun newest_first() {
        assertEquals(listOf("news10", "ébène", "shop", "news2", "Bank"), order(AliasSort.Newest))
    }

    @Test
    fun oldest_first() {
        assertEquals(listOf("Bank", "news2", "shop", "ébène", "news10"), order(AliasSort.Oldest))
    }

    @Test
    fun address_a_to_z_ignores_case_and_accents_and_orders_numbers() {
        assertEquals(listOf("Bank", "ébène", "news2", "news10", "shop"), order(AliasSort.AddressAz))
    }

    @Test
    fun address_z_to_a() {
        assertEquals(listOf("shop", "news10", "news2", "ébène", "Bank"), order(AliasSort.AddressZa))
    }

    @Test
    fun pinned_aliases_stay_on_top_in_every_order() {
        val pinned = aliases.map { if (it.id == "shop") it.copy(is_pinned = true) else it }
        AliasSort.entries.forEach { sort ->
            assertEquals("shop", order(sort, pinned).first())
        }
        assertEquals(listOf("shop", "Bank", "ébène", "news2", "news10"), order(AliasSort.AddressAz, pinned))
    }

    @Test
    fun an_unreadable_date_counts_as_oldest() {
        val items = listOf(alias("a", "not a date"), alias("b", "2026-01-01T00:00:00Z"))
        assertEquals(listOf("b", "a"), order(AliasSort.Newest, items))
        assertEquals(listOf("a", "b"), order(AliasSort.Oldest, items))
    }

    @Test
    fun domain_addresses_sort_by_their_own_dates() {
        val addresses = listOf(
            CustomDomainAddressInfo(id = "1", domain_name = "diogo.example", encrypted_local_part = "old", created_at = "2026-01-01T00:00:00Z"),
            CustomDomainAddressInfo(id = "2", domain_name = "diogo.example", encrypted_local_part = "new", created_at = "2026-06-01T00:00:00Z"),
        )
        val sorted = sort_aliases(addresses, AliasSort.Newest, Locale.ENGLISH, { it.address }, { it.created_at })
        assertEquals(listOf("new", "old"), sorted.map { it.encrypted_local_part })
    }

    @Test
    fun stored_key_round_trips_and_unknown_falls_back_to_newest() {
        AliasSort.entries.forEach { assertEquals(it, AliasSort.from_key(it.key)) }
        assertEquals(AliasSort.Newest, AliasSort.from_key(null))
        assertEquals(AliasSort.Newest, AliasSort.from_key("biggest"))
    }
}
