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


package org.astermail.android.ui.settings

import org.astermail.android.ui.upgrade.UpgradeLimitKey
import org.astermail.android.ui.upgrade.UpgradeStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchKeywordTest {

    private val mail_rules_keywords = settings_search_fixture.candidates
        .first { it.screen_id == "mail_rules" && it.is_screen_title }
        .keywords

    @Test
    fun mail_rules_entry_carries_filter_keywords() {
        listOf("filter", "filters", "email filter", "rule").forEach { keyword ->
            assertTrue(keyword, keyword in mail_rules_keywords)
        }
    }

    @Test
    fun filter_query_ranks_mail_rules_near_the_top() {
        listOf("filter", "filters").forEach { query ->
            val top = settings_search_fixture.search(query).take(3).map { it.candidate.screen_id }
            assertTrue(query, "mail_rules" in top)
        }
        listOf("email filter", "mail filters").forEach { query ->
            assertEquals(query, "mail_rules", settings_search_fixture.search(query).first().candidate.screen_id)
        }
    }

    @Test
    fun every_keyword_resource_is_reachable_from_an_entry() {
        val used = settings_search_index.flatMap { settings_keyword_resources(it) }.toSet()
        val declared = settings_screen_keyword_res.values.toSet() + settings_label_keyword_res.values.toSet()
        assertEquals(declared, used)
        val defined = settings_search_fixture.english.keys.filter { it.startsWith("search_keywords_") }.toSet()
        val referenced = declared.map { settings_search_fixture.string_names.getValue(it) }.toSet()
        assertEquals(defined, referenced)
    }

    @Test
    fun keyword_screen_ids_exist_in_the_index() {
        val ids = settings_search_index.filter { it.is_screen_title }.map { it.screen_id }.toSet()
        settings_screen_keyword_res.keys.forEach { assertTrue(it, it in ids) }
        val labels = settings_search_index.map { it.label_res }.toSet()
        settings_label_keyword_res.keys.forEach {
            assertTrue(settings_search_fixture.string_names[it] ?: "$it", it in labels)
        }
    }

    @Test
    fun mail_rules_plan_limit_maps_to_custom_filters() {
        UpgradeStore.show_plan_limit("mail rules", null)
        assertEquals(UpgradeLimitKey.MaxCustomFilters, UpgradeStore.state.value.limit_key)
        UpgradeStore.close()
    }

    @Test
    fun contacts_plan_limit_opens_no_upsell() {
        UpgradeStore.close()
        UpgradeStore.show_plan_limit("contacts", "Contact limit reached")
        assertFalse(UpgradeStore.state.value.is_open)
        assertTrue(UpgradeStore.is_uncapped(" Contacts "))
        assertFalse(UpgradeStore.is_uncapped("aliases"))
        assertFalse(UpgradeStore.is_uncapped(null))
    }
}
