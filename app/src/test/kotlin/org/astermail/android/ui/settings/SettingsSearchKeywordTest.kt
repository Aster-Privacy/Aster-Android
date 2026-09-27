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
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchKeywordTest {

    private val mail_rules_keywords = settings_search_index
        .first { it.screen_id == "mail_rules" && it.is_screen_title }
        .keywords

    @Test
    fun mail_rules_entry_carries_filter_keywords() {
        listOf("filter", "filters", "email filter", "rule").forEach { keyword ->
            assertTrue(keyword, keyword in mail_rules_keywords)
        }
    }

    @Test
    fun exact_filter_query_outscores_filters_and_rules_label() {
        val mail_rules_score = settings_keyword_score(mail_rules_keywords, "filter", "filter") + 20
        val filters_rules_label_score = 60 + 30 + 25 - 1
        assertTrue(mail_rules_score > filters_rules_label_score)
    }

    @Test
    fun partial_filter_query_gets_prefix_score() {
        assertTrue(settings_keyword_score(mail_rules_keywords, "filt", "filt") > 0)
        assertTrue(settings_keyword_score(mail_rules_keywords, "email fil", "fil") > 0)
    }

    @Test
    fun unrelated_prefix_gets_no_keyword_score() {
        assertEquals(0, settings_keyword_score(mail_rules_keywords, "email", "email"))
        assertEquals(0, settings_keyword_score(emptyList(), "filter", "filter"))
    }

    @Test
    fun mail_rules_plan_limit_maps_to_custom_filters() {
        UpgradeStore.show_plan_limit("mail rules", null)
        assertEquals(UpgradeLimitKey.MaxCustomFilters, UpgradeStore.state.value.limit_key)
        UpgradeStore.close()
    }
}
