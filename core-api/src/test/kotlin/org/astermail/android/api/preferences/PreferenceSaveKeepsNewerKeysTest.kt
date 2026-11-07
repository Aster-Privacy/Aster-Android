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
import org.junit.Test

class PreferenceSaveKeepsNewerKeysTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun save_like_the_settings_view_model(
        loaded_raw: String,
        fresh_raw: String,
        edit: (UserPreferences) -> UserPreferences,
    ): JsonObject {
        val baseline = merge_decrypted_preferences(json, loaded_raw, null)
        val updated = edit(baseline)
        val server_prefs = merge_decrypted_preferences(json, fresh_raw, baseline)
        val to_save = rebase_preferences_changes(json, server_prefs, baseline, updated)
        val payload = encode_preferences_preserving_unknown(json, to_save, fresh_raw)
        return json.parseToJsonElement(payload).jsonObject
    }

    @Test
    fun a_save_keeps_the_selected_alias_key_that_another_client_wrote_after_the_load() {
        val loaded = """{"theme":"light","relative_dates":true}"""
        val fresh = """{"theme":"light","relative_dates":true,"compose_from_selected_alias":true,"setting_from_a_future_client":"kept"}"""

        val saved = save_like_the_settings_view_model(loaded, fresh) { it.copy(relative_dates = false) }

        assertEquals(JsonPrimitive(true), saved["compose_from_selected_alias"])
        assertEquals(JsonPrimitive("kept"), saved["setting_from_a_future_client"])
        assertEquals(JsonPrimitive(false), saved["relative_dates"])
    }

    @Test
    fun a_save_keeps_the_selected_alias_key_that_was_already_loaded() {
        val blob = """{"theme":"light","relative_dates":true,"compose_from_selected_alias":true}"""

        val saved = save_like_the_settings_view_model(blob, blob) { it.copy(relative_dates = false) }

        assertEquals(JsonPrimitive(true), saved["compose_from_selected_alias"])
        assertEquals(JsonPrimitive(false), saved["relative_dates"])
    }
}
