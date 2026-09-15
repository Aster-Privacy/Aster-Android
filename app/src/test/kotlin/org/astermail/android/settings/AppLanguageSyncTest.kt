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

package org.astermail.android.settings

import org.astermail.android.api.preferences.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppLanguageSyncTest {
    @Test
    fun default_preferences_carry_no_language() {
        val prefs = UserPreferences()

        assertEquals("", prefs.language)
        assertEquals(false, prefs.language_explicit)
    }

    @Test
    fun empty_language_never_pins() {
        assertNull(app_language.synced_code_to_store("", false, null))
        assertNull(app_language.synced_code_to_store(null, false, "ar"))
    }

    @Test
    fun implicit_english_never_pins() {
        assertNull(app_language.synced_code_to_store("en", false, null))
        assertNull(app_language.synced_code_to_store("en", false, "ar"))
    }

    @Test
    fun explicit_english_pins() {
        assertEquals("en", app_language.synced_code_to_store("en", true, null))
        assertEquals("en", app_language.synced_code_to_store("en", true, "ar"))
    }

    @Test
    fun implicit_non_english_still_pins() {
        assertEquals("ar", app_language.synced_code_to_store("ar", false, null))
    }

    @Test
    fun unchanged_code_is_not_rewritten() {
        assertNull(app_language.synced_code_to_store("ar", true, "ar"))
    }

    @Test
    fun unsupported_language_is_ignored() {
        assertNull(app_language.synced_code_to_store("xx", true, null))
    }
}
