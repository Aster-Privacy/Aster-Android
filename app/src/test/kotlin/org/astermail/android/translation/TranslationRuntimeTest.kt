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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationRuntimeTest {

    @Test
    fun reads_the_major_version_from_a_webview_version_name() {
        assertEquals(69, TranslationRuntime.parse_major("69.0.3497.100"))
        assertEquals(129, TranslationRuntime.parse_major(" 129.0.6668.100 "))
        assertNull(TranslationRuntime.parse_major("unknown"))
        assertNull(TranslationRuntime.parse_major(null))
    }

    @Test
    fun rejects_webviews_older_than_the_translator_needs() {
        assertFalse(TranslationRuntime.major_supported(69))
        assertFalse(TranslationRuntime.major_supported(90))
        assertTrue(TranslationRuntime.major_supported(91))
        assertTrue(TranslationRuntime.major_supported(129))
    }

    @Test
    fun allows_translation_when_the_version_is_unknown() {
        assertTrue(TranslationRuntime.major_supported(null))
    }

    @Test
    fun names_the_app_that_provides_the_webview() {
        assertEquals("Chrome", TranslationRuntime.display_name(" Chrome "))
    }

    @Test
    fun falls_back_to_the_standard_webview_name_when_the_label_is_missing() {
        assertEquals("Android System WebView", TranslationRuntime.display_name(null))
        assertEquals("Android System WebView", TranslationRuntime.display_name("  "))
    }
}
