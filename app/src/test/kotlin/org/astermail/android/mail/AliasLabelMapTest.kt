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

package org.astermail.android.mail

import org.astermail.android.ui.mail.AliasLabelEntry
import org.astermail.android.ui.mail.build_alias_label_map
import org.astermail.android.ui.mail.build_alias_token_label_map
import org.astermail.android.ui.mail.resolve_alias_delivery_label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AliasLabelMapTest {

    @Test
    fun falls_back_to_the_local_part_when_there_is_no_display_name() {
        val map = build_alias_label_map(listOf(AliasLabelEntry("shopping@aster.cx")))
        assertEquals("shopping", map["shopping@aster.cx"])
    }

    @Test
    fun prefers_the_trimmed_display_name() {
        val map = build_alias_label_map(
            listOf(AliasLabelEntry("shopping@aster.cx", "  Shopping  ")),
        )
        assertEquals("Shopping", map["shopping@aster.cx"])
    }

    @Test
    fun normalizes_the_address_key() {
        val map = build_alias_label_map(listOf(AliasLabelEntry("  SHOPPING@Aster.CX ")))
        assertEquals("shopping", map["shopping@aster.cx"])
        assertNull(map["SHOPPING@Aster.CX"])
    }

    @Test
    fun skips_entries_without_a_usable_address() {
        val map = build_alias_label_map(
            listOf(
                AliasLabelEntry(null),
                AliasLabelEntry(""),
                AliasLabelEntry("@aster.cx"),
                AliasLabelEntry("no-at-sign"),
            ),
        )
        assertEquals(0, map.size)
    }

    @Test
    fun keeps_the_first_entry_for_a_duplicate_address() {
        val map = build_alias_label_map(
            listOf(
                AliasLabelEntry("shopping@aster.cx", "First"),
                AliasLabelEntry("shopping@aster.cx", "Second"),
            ),
        )
        assertEquals("First", map["shopping@aster.cx"])
        assertEquals(1, map.size)
    }

    @Test
    fun leaves_unknown_addresses_unmapped() {
        val map = build_alias_label_map(listOf(AliasLabelEntry("shopping@aster.cx")))
        assertNull(map["hello@example.com"])
    }

    private val entries = listOf(
        AliasLabelEntry("shopping@aster.cx", "Shopping", "hash-shopping"),
        AliasLabelEntry("newsletters@aster.cx", null, "hash-newsletters"),
    )
    private val token_labels = build_alias_token_label_map(entries)
    private val labels = build_alias_label_map(entries)

    @Test
    fun token_map_keeps_the_hash_case_and_uses_the_same_label_rules() {
        assertEquals("Shopping", token_labels["hash-shopping"])
        assertEquals("newsletters", token_labels["hash-newsletters"])
        assertEquals(2, token_labels.size)
    }

    @Test
    fun token_map_skips_entries_without_a_hash_or_a_usable_address() {
        val map = build_alias_token_label_map(
            listOf(
                AliasLabelEntry("shopping@aster.cx", null, "  "),
                AliasLabelEntry("shopping@aster.cx", null, null),
                AliasLabelEntry("no-at-sign", null, "hash-broken"),
                AliasLabelEntry(null, "Name", "hash-nameless"),
            ),
        )
        assertEquals(0, map.size)
    }

    @Test
    fun token_map_keeps_the_first_entry_for_a_duplicate_hash() {
        val map = build_alias_token_label_map(
            listOf(
                AliasLabelEntry("first@aster.cx", "First", "hash-dup"),
                AliasLabelEntry("second@aster.cx", "Second", "hash-dup"),
            ),
        )
        assertEquals("First", map["hash-dup"])
        assertEquals(1, map.size)
    }

    @Test
    fun resolves_a_bcc_delivery_from_the_routing_token_alone() {
        assertEquals(
            "Shopping",
            resolve_alias_delivery_label(token_labels, labels, "hash-shopping", null),
        )
    }

    @Test
    fun resolves_from_the_recipient_header_when_there_is_no_token() {
        assertEquals(
            "Shopping",
            resolve_alias_delivery_label(token_labels, labels, null, "SHOPPING@Aster.CX"),
        )
    }

    @Test
    fun the_routing_token_wins_when_both_sources_are_present() {
        assertEquals(
            "newsletters",
            resolve_alias_delivery_label(
                token_labels,
                labels,
                "hash-newsletters",
                "shopping@aster.cx",
            ),
        )
    }

    @Test
    fun an_unknown_token_falls_back_to_the_recipient_header() {
        assertEquals(
            "Shopping",
            resolve_alias_delivery_label(
                token_labels,
                labels,
                "hash-not-mine",
                "shopping@aster.cx",
            ),
        )
    }

    @Test
    fun an_unknown_token_with_no_known_header_resolves_to_nothing() {
        assertNull(
            resolve_alias_delivery_label(
                token_labels,
                labels,
                "hash-not-mine",
                "someone@example.com",
            ),
        )
    }

    @Test
    fun a_legacy_cached_row_without_a_token_still_resolves_by_header() {
        assertEquals(
            "Shopping",
            resolve_alias_delivery_label(token_labels, labels, null, "shopping@aster.cx"),
        )
        assertNull(resolve_alias_delivery_label(token_labels, labels, null, null))
        assertNull(resolve_alias_delivery_label(token_labels, labels, "   ", "  "))
    }
}
