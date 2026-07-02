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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.preferences.encode_preferences_preserving_unknown
import org.astermail.android.api.preferences.merge_decrypted_preferences
import org.astermail.android.api.preferences.rebase_preferences_changes
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

    @Test
    fun encode_preserves_a_web_only_key_the_app_does_not_model() {
        val server_blob = """{"theme":"dark","undo_send_period":"30 seconds","show_aster_branding":false}"""
        val prefs = merge_decrypted_preferences(json, server_blob, null)

        val encoded = encode_preferences_preserving_unknown(json, prefs, server_blob)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertEquals("30 seconds", obj["undo_send_period"]?.jsonPrimitive?.content)
    }

    @Test
    fun plain_serialization_would_drop_the_web_only_key() {
        val server_blob = """{"undo_send_period":"30 seconds"}"""
        val prefs = merge_decrypted_preferences(json, server_blob, null)

        val plain = json.encodeToString(UserPreferences.serializer(), prefs)

        assertFalse(plain.contains("undo_send_period"))
    }

    @Test
    fun encode_lets_the_app_value_win_for_a_shared_key() {
        val server_blob = """{"show_aster_branding":true,"undo_send_period":"30 seconds"}"""
        val prefs = merge_decrypted_preferences(json, server_blob, null).copy(show_aster_branding = false)

        val encoded = encode_preferences_preserving_unknown(json, prefs, server_blob)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertEquals("false", obj["show_aster_branding"]?.jsonPrimitive?.content)
        assertEquals("30 seconds", obj["undo_send_period"]?.jsonPrimitive?.content)
    }

    @Test
    fun string_load_remote_images_from_web_does_not_reset_other_settings() {
        val previous = UserPreferences(
            haptic_enabled = false,
            conversation_grouping = false,
            inbox_categories_enabled = false,
            theme = "dark",
        )
        val web_blob = """{"load_remote_images":"never","theme":"dark","haptic_enabled":false,"conversation_grouping":false,"inbox_categories_enabled":false,"block_tracking_pixels":true}"""

        val merged = merge_decrypted_preferences(json, web_blob, previous)

        assertEquals("never", merged.load_remote_images)
        assertFalse(merged.haptic_enabled)
        assertFalse(merged.conversation_grouping)
        assertFalse(merged.inbox_categories_enabled)
        assertEquals("dark", merged.theme)
        assertTrue(merged.block_tracking_pixels)
    }

    @Test
    fun a_wrong_typed_key_keeps_the_base_value_instead_of_resetting() {
        val previous = UserPreferences(undo_send_seconds = 25, conversation_grouping = false)
        val bad_blob = """{"undo_send_seconds":"not a number","conversation_grouping":false}"""

        val merged = merge_decrypted_preferences(json, bad_blob, previous)

        assertEquals(25, merged.undo_send_seconds)
        assertFalse(merged.conversation_grouping)
    }

    @Test
    fun encode_without_an_original_blob_emits_only_known_keys() {
        val prefs = UserPreferences(show_aster_branding = false)

        val encoded = encode_preferences_preserving_unknown(json, prefs, null)
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertFalse(obj.containsKey("undo_send_period"))
        assertTrue(obj.containsKey("show_aster_branding"))
    }

    @Test
    fun rebase_keeps_server_values_for_keys_the_user_did_not_change() {
        val server = UserPreferences(theme = "dark", conversation_grouping = false)
        val baseline = UserPreferences()
        val updated = baseline.copy(swipe_right_action = "star")

        val rebased = rebase_preferences_changes(json, server, baseline, updated)

        assertEquals("dark", rebased.theme)
        assertFalse(rebased.conversation_grouping)
        assertEquals("star", rebased.swipe_right_action)
    }

    @Test
    fun rebase_applies_user_change_over_server_value() {
        val server = UserPreferences(theme = "dark")
        val baseline = UserPreferences(theme = "dark")
        val updated = baseline.copy(theme = "light")

        val rebased = rebase_preferences_changes(json, server, baseline, updated)

        assertEquals("light", rebased.theme)
    }

    @Test
    fun rebase_with_defaults_baseline_does_not_clobber_server_with_defaults() {
        val server = UserPreferences(
            theme = "dark",
            haptic_enabled = false,
            load_remote_images = "always",
        )
        val baseline: UserPreferences? = null
        val updated = UserPreferences(show_aster_branding = false)

        val rebased = rebase_preferences_changes(json, server, baseline, updated)

        assertEquals("dark", rebased.theme)
        assertFalse(rebased.haptic_enabled)
        assertEquals("always", rebased.load_remote_images)
        assertFalse(rebased.show_aster_branding)
    }
}
