// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.api.preferences

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ToastPositionPreferenceTest {

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
    fun a_missing_value_reads_as_unset() {
        assertEquals("", UserPreferences().toast_position)
        assertEquals("", merge_decrypted_preferences(json, """{"theme":"dark"}""", null).toast_position)
    }

    @Test
    fun a_value_from_the_web_is_read_unchanged() {
        assertEquals("top-left", merge_decrypted_preferences(json, """{"toast_position":"top-left"}""", null).toast_position)
        assertEquals("sideways", merge_decrypted_preferences(json, """{"toast_position":"sideways"}""", null).toast_position)
    }

    @Test
    fun a_null_or_wrongly_typed_value_reads_as_unset() {
        assertEquals("", merge_decrypted_preferences(json, """{"toast_position":null}""", null).toast_position)
        assertEquals("", merge_decrypted_preferences(json, """{"toast_position":3}""", null).toast_position)
    }

    @Test
    fun an_unrelated_save_keeps_a_value_android_cannot_pick() {
        val blob = """{"theme":"light","toast_position":"top-right"}"""
        val saved = save(blob, blob) { it.copy(relative_dates = false) }
        assertEquals(JsonPrimitive("top-right"), saved["toast_position"])
    }

    @Test
    fun picking_an_edge_writes_the_shared_key() {
        val blob = """{"theme":"light"}"""
        val saved = save(blob, blob) { it.copy(toast_position = "top") }
        assertEquals(JsonPrimitive("top"), saved["toast_position"])
    }

    @Test
    fun an_unrelated_save_keeps_the_value_another_device_wrote() {
        val loaded = """{"theme":"light"}"""
        val fresh = """{"theme":"light","toast_position":"top-left"}"""
        val saved = save(loaded, fresh) { it.copy(relative_dates = false) }
        assertEquals(JsonPrimitive("top-left"), saved["toast_position"])
    }
}
