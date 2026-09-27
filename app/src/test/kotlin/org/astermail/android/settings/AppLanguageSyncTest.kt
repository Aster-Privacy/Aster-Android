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

    @Test
    fun web_labels_map_to_codes() {
        assertEquals("pt", app_language.normalize_code("Português (Portugal)"))
        assertEquals("pt-BR", app_language.normalize_code("Português (Brazil)"))
        assertEquals("zh-CN", app_language.normalize_code("简体中文 (Simplified)"))
        assertEquals("de", app_language.normalize_code("Deutsch"))
        assertEquals("ar", app_language.normalize_code("العربية"))
    }

    @Test
    fun codes_and_tags_map_to_codes() {
        assertEquals("zh-CN", app_language.normalize_code("zh"))
        assertEquals("zh-CN", app_language.normalize_code("zh-Hans-CN"))
        assertEquals("pt-BR", app_language.normalize_code("pt_BR"))
        assertEquals("pt-BR", app_language.normalize_code("pt-br"))
        assertEquals("pt", app_language.normalize_code("pt-PT"))
        assertEquals("fr", app_language.normalize_code("fr-CA"))
    }

    @Test
    fun legacy_stored_codes_are_not_rewritten() {
        assertNull(app_language.synced_code_to_store("简体中文 (Simplified)", true, "zh"))
    }

    @Test
    fun server_value_round_trips_through_normalize() {
        for (option in app_language.supported) {
            assertEquals(option.code, app_language.normalize_code(app_language.server_value(option.code)))
        }
        assertEquals("", app_language.server_value(null))
    }

    @Test
    fun supported_languages_match_web() {
        assertEquals(
            listOf("en", "es", "fr", "de", "it", "pt", "pt-BR", "nl", "pl", "tr", "ru", "zh-CN", "ja", "ko", "ar", "hi"),
            app_language.supported.map { it.code },
        )
    }

    @Test
    fun locale_config_lists_every_supported_language() {
        var file = java.io.File("src/main/res/xml/locales_config.xml")
        if (!file.isFile) file = java.io.File("app/src/main/res/xml/locales_config.xml")
        val declared = Regex("android:name=\"([^\"]+)\"").findAll(file.readText()).map { it.groupValues[1] }.toList()

        assertEquals(app_language.supported.map { it.code }, declared)
    }

    @Test
    fun every_supported_language_has_its_own_resources() {
        var root = java.io.File("src/main/res")
        if (!root.isDirectory) root = java.io.File("app/src/main/res")
        val missing = app_language.supported
            .map { it.code }
            .filter { it != "en" }
            .map { code ->
                val parts = code.split("-")
                if (parts.size == 1) "values-${parts[0]}" else "values-${parts[0]}-r${parts[1]}"
            }
            .filter { !java.io.File(root, "$it/strings.xml").isFile }

        assertEquals(emptyList<String>(), missing)
    }
}
