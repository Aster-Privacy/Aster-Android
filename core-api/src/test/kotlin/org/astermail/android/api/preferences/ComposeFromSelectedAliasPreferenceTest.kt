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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeFromSelectedAliasPreferenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun the_setting_is_off_by_default() {
        assertFalse(UserPreferences().compose_from_selected_alias)
    }

    @Test
    fun a_blob_without_the_key_reads_as_off() {
        val prefs = merge_decrypted_preferences(json, """{"theme":"dark","relative_dates":false}""", null)

        assertFalse(prefs.compose_from_selected_alias)
        assertFalse(prefs.relative_dates)
    }

    @Test
    fun a_malformed_value_reads_as_off() {
        for (raw in listOf("\"true\"", "1", "0", "null", "\"yes\"", "{}", "[]", "\"\"")) {
            val prefs = merge_decrypted_preferences(
                json,
                """{"relative_dates":false,"compose_from_selected_alias":$raw}""",
                null,
            )

            assertFalse("value $raw", prefs.compose_from_selected_alias)
            assertFalse("value $raw", prefs.relative_dates)
        }
    }

    @Test
    fun a_true_value_reads_as_on() {
        val prefs = merge_decrypted_preferences(json, """{"compose_from_selected_alias":true}""", null)

        assertTrue(prefs.compose_from_selected_alias)
    }

    @Test
    fun the_setting_survives_a_save_and_a_load() {
        val saved = encode_preferences_preserving_unknown(
            json,
            UserPreferences(compose_from_selected_alias = true),
            """{"setting_from_a_future_client":"kept"}""",
        )
        val stored = json.parseToJsonElement(saved).jsonObject

        assertEquals(JsonPrimitive(true), stored["compose_from_selected_alias"])
        assertEquals(JsonPrimitive("kept"), stored["setting_from_a_future_client"])
        assertTrue(merge_decrypted_preferences(json, saved, null).compose_from_selected_alias)
    }

    @Test
    fun turning_the_setting_off_on_one_device_reaches_the_other() {
        val loaded = """{"compose_from_selected_alias":true,"relative_dates":true}"""
        val baseline = merge_decrypted_preferences(json, loaded, null)
        val updated = baseline.copy(compose_from_selected_alias = false)
        val to_save = rebase_preferences_changes(json, baseline, baseline, updated)
        val payload = encode_preferences_preserving_unknown(json, to_save, loaded)

        val other_device = merge_decrypted_preferences(json, payload, baseline)

        assertFalse(other_device.compose_from_selected_alias)
        assertTrue(other_device.relative_dates)
    }
}
