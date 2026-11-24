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

package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class AliasListLayoutTest {

    @Test
    fun split_keeps_domain_with_at_sign() {
        assertEquals(AliasAddressParts("shop.a1b2", "@astermail.org"), split_alias_address("shop.a1b2@astermail.org"))
    }

    @Test
    fun split_uses_last_at_sign_and_trims() {
        assertEquals(AliasAddressParts("\"a@b\"", "@aster.cx"), split_alias_address("  \"a@b\"@aster.cx "))
    }

    @Test
    fun split_without_usable_domain_returns_whole_string() {
        assertEquals(AliasAddressParts("plain", ""), split_alias_address("plain"))
        assertEquals(AliasAddressParts("@aster.cx", ""), split_alias_address("@aster.cx"))
        assertEquals(AliasAddressParts("name@", ""), split_alias_address("name@"))
    }

    @Test
    fun wrappable_address_breaks_before_at_and_after_separators() {
        val z = alias_break_opportunity
        assertEquals(
            "shop.${z}a1b2$z@astermail.org",
            alias_wrappable_address(split_alias_address("shop.a1b2@astermail.org")),
        )
        assertEquals("plain", alias_wrappable_address(split_alias_address("plain")))
        assertEquals("a-${z}b_${z}c+${z}d", alias_wrappable_local("a-b_c+d"))
        assertEquals("trailing.", alias_wrappable_local("trailing."))
    }

    @Test
    fun wrappable_address_keeps_every_visible_character() {
        val address = "first.last-name+tag@sub.example.org"
        val shown = alias_wrappable_address(split_alias_address(address))
        assertEquals(address, shown.replace(alias_break_opportunity.toString(), ""))
    }

    @Test
    fun group_puts_pinned_first_and_keeps_order() {
        val groups = group_aliases(listOf("a" to false, "b" to true, "c" to false, "d" to true)) { it.second }
        assertEquals(listOf("b", "d"), groups.pinned.map { it.first })
        assertEquals(listOf("a", "c"), groups.others.map { it.first })
    }

    @Test
    fun group_handles_empty_list() {
        val groups = group_aliases(emptyList<String>()) { true }
        assertEquals(emptyList<String>(), groups.pinned)
        assertEquals(emptyList<String>(), groups.others)
    }

    @Test
    fun usage_level_thresholds() {
        assertEquals(AliasUsageLevel.Unlimited, alias_usage_level(5, null))
        assertEquals(AliasUsageLevel.Unlimited, alias_usage_level(5, 0))
        assertEquals(AliasUsageLevel.Normal, alias_usage_level(7, 10))
        assertEquals(AliasUsageLevel.Near, alias_usage_level(8, 10))
        assertEquals(AliasUsageLevel.Full, alias_usage_level(10, 10))
        assertEquals(AliasUsageLevel.Full, alias_usage_level(12, 10))
    }

    @Test
    fun usage_fraction_is_clamped() {
        assertEquals(0f, alias_usage_fraction(3, null), 0f)
        assertEquals(0.5f, alias_usage_fraction(5, 10), 0.0001f)
        assertEquals(1f, alias_usage_fraction(15, 10), 0f)
        assertEquals(0f, alias_usage_fraction(-1, 10), 0f)
    }
}
