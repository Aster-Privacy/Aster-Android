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

import org.astermail.android.api.preferences.CustomCategoryRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTabLimitTest {

    private val rules = listOf(
        CustomCategoryRule(id = "receipts", name = "Receipts", match_keywords = listOf("receipt")),
        CustomCategoryRule(id = "school", name = "School", match_keywords = listOf("school")),
        CustomCategoryRule(id = "clubs", name = "Clubs", match_keywords = listOf("club")),
    )

    @Test
    fun plan_limit_truncates_custom_tabs() {
        val limited = active_category_tabs(listOf("promotions"), rules, 1)
        assertTrue(limited.contains("receipts"))
        assertFalse(limited.contains("school"))
        assertFalse(limited.contains("clubs"))
    }

    @Test
    fun unlimited_keeps_every_custom_tab() {
        val unlimited = active_category_tabs(listOf("promotions"), rules, -1)
        assertTrue(unlimited.contains("receipts"))
        assertTrue(unlimited.contains("school"))
        assertTrue(unlimited.contains("clubs"))
    }

    @Test
    fun a_limit_mismatch_would_strand_mail_in_an_unreachable_tab() {
        val drawer_tabs = active_category_tabs(listOf("promotions"), rules, 1)
        val list_tabs = active_category_tabs(listOf("promotions"), rules, -1)
        val stranded = list_tabs.filterNot { drawer_tabs.contains(it) }
        assertEquals(listOf("school", "clubs"), stranded)
        assertEquals(drawer_tabs, active_category_tabs(listOf("promotions"), rules, 1))
    }

    @Test
    fun primary_survives_every_limit() {
        assertEquals("primary", active_category_tabs(emptyList(), rules, 0).first())
        assertEquals("primary", active_category_tabs(emptyList(), emptyList(), 0).single())
    }
}
