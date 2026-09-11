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

import org.astermail.android.ui.common.category_icon_catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogTest {

    @Test
    fun builtin_order_matches_the_shared_contract() {
        assertEquals(
            listOf(
                "primary",
                "promotions",
                "newsletters",
                "social",
                "updates",
                "transactions",
                "forums",
                "finance",
                "travel",
                "shopping",
            ),
            BUILTIN_CATEGORIES.map { it.id },
        )
    }

    @Test
    fun new_categories_are_off_by_default_and_fold_back() {
        assertEquals(listOf("promotions", "social", "updates"), DEFAULT_ENABLED_CATEGORIES)
        assertFalse(builtin_category("newsletters")!!.default_enabled)
        assertFalse(builtin_category("transactions")!!.default_enabled)
        assertEquals("promotions", fold_builtin("newsletters"))
        assertEquals("updates", fold_builtin("transactions"))
        assertEquals(listOf("primary", "promotions", "social", "updates"), CATEGORY_TABS)
    }

    @Test
    fun saved_preferences_without_newsletters_keep_their_tabs() {
        val tabs = active_category_tabs(listOf("promotions", "social", "updates", "forums"), emptyList(), -1)
        assertEquals(listOf("primary", "promotions", "social", "updates", "forums"), tabs)
        assertEquals("promotions", category_for_tab("newsletters", tabs))
        assertEquals("updates", category_for_tab("transactions", tabs))
    }

    @Test
    fun enabled_tabs_follow_catalog_order_not_saved_order() {
        val tabs = active_category_tabs(
            listOf("shopping", "transactions", "updates", "newsletters", "promotions"),
            emptyList(),
            -1,
        )
        assertEquals(
            listOf("primary", "promotions", "newsletters", "updates", "transactions", "shopping"),
            tabs,
        )
    }

    @Test
    fun rule_targets_list_every_category_in_order() {
        assertFalse(RULE_CATEGORY_TARGETS.contains("important"))
        assertEquals(
            listOf(
                "primary",
                "promotions",
                "newsletters",
                "social",
                "updates",
                "transactions",
                "forums",
                "finance",
                "travel",
                "shopping",
            ),
            RULE_CATEGORY_TARGETS,
        )
    }

    @Test
    fun every_builtin_icon_resolves() {
        for (category in BUILTIN_CATEGORIES) {
            assertTrue(category.icon, category_icon_catalog.containsKey(category.icon))
        }
        assertTrue(CUSTOM_CATEGORY_ICON_CHOICES.contains("newspaper"))
        assertTrue(CUSTOM_CATEGORY_ICON_CHOICES.contains("receipt"))
    }
}
