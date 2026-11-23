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

package org.astermail.android.api.preferences

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideScrollbarsPreferenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun save(loaded_raw: String, fresh_raw: String, edit: (UserPreferences) -> UserPreferences): JsonObject {
        val baseline = merge_decrypted_preferences(json, loaded_raw, null)
        val updated = edit(baseline)
        val server_prefs = merge_decrypted_preferences(json, fresh_raw, baseline)
        val to_save = rebase_preferences_changes(json, server_prefs, baseline, updated)
        val payload = encode_preferences_preserving_unknown(json, to_save, fresh_raw)
        return json.parseToJsonElement(payload).jsonObject
    }

    @Test
    fun scroll_bars_are_shown_by_default() {
        assertFalse(UserPreferences().hide_scrollbars)
        assertFalse(merge_decrypted_preferences(json, """{"theme":"dark"}""", null).hide_scrollbars)
    }

    @Test
    fun the_synced_key_is_read_from_another_device() {
        assertTrue(merge_decrypted_preferences(json, """{"hide_scrollbars":true}""", null).hide_scrollbars)
    }

    @Test
    fun turning_it_on_writes_the_shared_key() {
        val blob = """{"theme":"light"}"""
        val saved = save(blob, blob) { it.copy(hide_scrollbars = true) }
        assertEquals(JsonPrimitive(true), saved["hide_scrollbars"])
    }

    @Test
    fun an_unrelated_save_keeps_the_value_another_device_wrote() {
        val loaded = """{"theme":"light"}"""
        val fresh = """{"theme":"light","hide_scrollbars":true}"""
        val saved = save(loaded, fresh) { it.copy(relative_dates = false) }
        assertEquals(JsonPrimitive(true), saved["hide_scrollbars"])
    }
}
