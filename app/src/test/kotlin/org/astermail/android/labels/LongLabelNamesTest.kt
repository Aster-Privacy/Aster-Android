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


package org.astermail.android.labels

import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.tags.TagItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LongLabelNamesTest {

    private val long_names = listOf(
        "Newsletters and promotions",
        "Rachunkiiumowy2026Firma",
        "ZamowieniaInternetowe2026Sklep",
        "Faktury i rachunki firmowe",
        "Zamówienia internetowe2026",
        "Żółte kartki i ważne sprawy",
    )

    private fun label(token: String, name: String?) = LabelItem(
        id = token,
        label_token = token,
        encrypted_name = name,
        name_nonce = "n",
        folder_type = LABEL_FOLDER_TYPE,
    )

    private fun tag(token: String, name: String) = TagItem(
        id = token,
        tag_token = token,
        encrypted_name = name,
        name_nonce = "n",
    )

    @Test
    fun long_label_names_are_listed() {
        val labels = long_names.mapIndexed { i, name -> label("l$i", name) }
        assertEquals(long_names, label_rows(labels).map { it.encrypted_name })
    }

    @Test
    fun long_tag_names_are_readable() {
        long_names.forEachIndexed { i, name ->
            val item = tag("t$i", name)
            assertTrue(name, tag_name_readable(item))
            assertEquals(name, tag_display_name(item, "Unknown"))
        }
    }

    @Test
    fun labels_without_a_name_stay_hidden() {
        val labels = listOf(label("a", null), label("b", ""), label("c", "Praca"))
        assertEquals(listOf("c"), label_rows(labels).map { it.label_token })
    }

    @Test
    fun tags_without_a_name_show_the_unknown_label() {
        val item = tag("t", "")
        assertFalse(tag_name_readable(item))
        assertEquals("Unknown", tag_display_name(item, "Unknown"))
    }
}
