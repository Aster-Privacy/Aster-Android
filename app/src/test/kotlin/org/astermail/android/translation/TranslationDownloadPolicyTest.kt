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

package org.astermail.android.translation

import org.junit.Assert.assertEquals
import org.junit.Test

class TranslationDownloadPolicyTest {

    @Test
    fun names_one_pack_for_a_route_that_touches_english() {
        assertEquals(listOf("deen"), TranslationDownloadPolicy.route_packs("de", "en"))
        assertEquals(listOf("enja"), TranslationDownloadPolicy.route_packs("en", "ja"))
    }

    @Test
    fun names_both_packs_for_a_route_that_pivots_through_english() {
        assertEquals(listOf("deen", "enfr"), TranslationDownloadPolicy.route_packs("de", "fr"))
    }

    @Test
    fun names_no_pack_when_the_languages_match() {
        assertEquals(emptyList<String>(), TranslationDownloadPolicy.route_packs("fr", "fr"))
    }

    @Test
    fun covers_every_supported_language_from_english() {
        for (code in translation_language_codes) {
            if (code == PIVOT_LANGUAGE) continue
            assertEquals(
                listOf("$PIVOT_LANGUAGE$code"),
                TranslationDownloadPolicy.route_packs(PIVOT_LANGUAGE, code),
            )
        }
    }
}
