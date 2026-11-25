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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryPatternWarmTest {
    @Test
    fun warm_compiles_every_subject_pattern_list() {
        warm_category_patterns()

        assertTrue(category_patterns_compiled())
    }

    @Test
    fun warm_is_idempotent_and_keeps_the_same_lists() {
        warm_category_patterns()
        val first = PROMOTIONS_SUBJECT_PATTERNS
        warm_category_patterns()

        assertTrue(first === PROMOTIONS_SUBJECT_PATTERNS)
    }

    @Test
    fun tab_helpers_need_no_subject_patterns() {
        assertEquals("primary", category_for_tab(null))
        assertEquals("primary", category_for_tab("not_a_category"))
        assertEquals(CATEGORY_TABS.first(), "primary")
    }

    @Test
    fun subject_patterns_still_match_after_lazy_init() {
        assertTrue(PROMOTIONS_SUBJECT_PATTERNS.any { it.containsMatchIn("Save 20% off today") })
        assertTrue(EMOJI_SUBJECT_PATTERN.containsMatchIn("New arrivals \uD83C\uDF89"))
        assertTrue(ACCOUNT_ACCESS_SUBJECT_PATTERNS.isNotEmpty())
    }
}
