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

package org.astermail.android.ui.common

import compose.icons.TablerIcons
import compose.icons.tablericons.World
import org.astermail.android.ui.drawer.resolve_label_icon
import org.astermail.android.ui.mail.material_icon_from_name
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelIconCatalogTest {

    private val web_tag_icon_keys = listOf(
        "clock", "archive", "trash", "send", "draft", "star", "flag", "bolt",
        "shield", "warning", "check", "tag", "folder", "envelope", "lock",
        "bell", "sparkles", "fire", "heart", "bookmark", "chat", "document",
        "currency", "cart", "code", "user", "building", "globe", "info",
        "eye-slash", "at",
    )

    @Test
    fun globe_resolves_to_the_world_glyph_everywhere() {
        assertEquals(TablerIcons.World, label_icon_or_null("globe"))
        assertEquals(TablerIcons.World, material_icon_from_name("globe"))
        assertEquals(TablerIcons.World, resolve_label_icon("globe"))
    }

    @Test
    fun every_catalog_key_resolves_identically_in_the_row_chip_and_the_drawer() {
        label_icon_catalog.forEach { (key, vector) ->
            assertEquals(key, vector, material_icon_from_name(key))
            assertEquals(key, vector, resolve_label_icon(key))
        }
    }

    @Test
    fun catalog_keys_match_the_web_tag_icon_map() {
        assertEquals(web_tag_icon_keys.sorted(), label_icon_catalog.map { it.first }.sorted())
    }

    @Test
    fun catalog_keys_are_unique() {
        val keys = label_icon_catalog.map { it.first }
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun folder_chips_still_resolve_the_inbox_glyph() {
        assertNotNull(material_icon_from_name("inbox"))
    }

    @Test
    fun surrounding_whitespace_is_tolerated() {
        assertEquals(TablerIcons.World, material_icon_from_name("  globe "))
    }

    @Test
    fun unknown_and_blank_names_resolve_to_nothing() {
        assertNull(label_icon_or_null("not-a-real-icon"))
        assertNull(label_icon_or_null(""))
        assertNull(label_icon_or_null("   "))
        assertNull(label_icon_or_null(null))
    }

    @Test
    fun the_drawer_falls_back_to_the_tag_glyph_for_unknown_names() {
        assertEquals(resolve_label_icon("tag"), resolve_label_icon("not-a-real-icon"))
        assertEquals(resolve_label_icon("tag"), resolve_label_icon(null))
    }

    @Test
    fun no_two_catalog_keys_share_a_misleading_glyph() {
        val duplicates = label_icon_catalog
            .groupBy { it.second }
            .filterValues { it.size > 1 }
            .mapValues { entry -> entry.value.map { it.first }.sorted() }
        assertTrue(duplicates.toString(), duplicates.values.all { it == listOf("document", "draft") })
    }
}
