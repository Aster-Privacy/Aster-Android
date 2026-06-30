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

import kotlinx.serialization.json.Json
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.preferences.merge_decrypted_preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferencesMergeTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun server_blob_without(key: String): String {
        val full = json.encodeToString(UserPreferences.serializer(), UserPreferences())
        val obj = json.parseToJsonElement(full)
        val filtered = obj.toString()
        return filtered.replace(Regex(",?\"$key\":(true|false)"), "")
    }

    @Test
    fun keeps_last_known_when_stale_client_dropped_the_key() {
        val previous = UserPreferences(show_aster_branding = false)
        val stripped = server_blob_without("show_aster_branding")

        val merged = merge_decrypted_preferences(json, stripped, previous)

        assertFalse(merged.show_aster_branding)
    }

    @Test
    fun defaults_a_missing_key_when_there_is_no_last_known() {
        val stripped = server_blob_without("show_aster_branding")

        val merged = merge_decrypted_preferences(json, stripped, null)

        assertTrue(merged.show_aster_branding)
    }

    @Test
    fun server_value_wins_over_last_known_when_present() {
        val previous = UserPreferences(show_aster_branding = true)
        val present = json.encodeToString(
            UserPreferences.serializer(),
            UserPreferences(show_aster_branding = false),
        )

        val merged = merge_decrypted_preferences(json, present, previous)

        assertFalse(merged.show_aster_branding)
    }

    @Test
    fun dropped_key_does_not_revert_unrelated_present_server_values() {
        val previous = UserPreferences(show_aster_branding = false, undo_send_seconds = 5)
        val present = json.encodeToString(
            UserPreferences.serializer(),
            UserPreferences(undo_send_seconds = 30),
        )
        val stripped = present.replace(Regex(",?\"show_aster_branding\":(true|false)"), "")

        val merged = merge_decrypted_preferences(json, stripped, previous)

        assertFalse(merged.show_aster_branding)
        assertEquals(30, merged.undo_send_seconds)
    }
}
