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
import org.astermail.android.api.preferences.effective_theme_values
import org.astermail.android.api.preferences.encode_preferences_preserving_unknown
import org.astermail.android.api.preferences.merge_decrypted_preferences
import org.astermail.android.api.preferences.normalize_order_preferences
import org.astermail.android.api.preferences.theme_sync_enabled
import org.astermail.android.api.preferences.with_theme_sync_enabled
import org.astermail.android.api.preferences.with_theme_values
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSyncTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val shared = UserPreferences(
        theme = "dark",
        color_theme = "purple",
        custom_theme_seed = "#a855f7",
    )

    @Test
    fun sync_is_on_by_default() {
        assertTrue(theme_sync_enabled(UserPreferences()))
    }

    @Test
    fun reads_shared_values_when_sync_is_on() {
        val prefs = shared.copy(
            theme_android = "light",
            color_theme_android = "green",
            custom_theme_seed_android = "#22c55e",
        )

        val values = effective_theme_values(prefs)

        assertEquals("dark", values.theme)
        assertEquals("purple", values.color_theme)
        assertEquals("#a855f7", values.custom_theme_seed)
    }

    @Test
    fun reads_platform_values_when_sync_is_off() {
        val prefs = shared.copy(
            theme_sync_enabled_android = false,
            theme_android = "light",
            color_theme_android = "green",
            custom_theme_seed_android = "#22c55e",
        )

        val values = effective_theme_values(prefs)

        assertEquals("light", values.theme)
        assertEquals("green", values.color_theme)
        assertEquals("#22c55e", values.custom_theme_seed)
    }

    @Test
    fun falls_back_to_shared_values_when_platform_fields_are_empty() {
        val prefs = shared.copy(theme_sync_enabled_android = false, color_theme_android = "green")

        val values = effective_theme_values(prefs)

        assertEquals("dark", values.theme)
        assertEquals("green", values.color_theme)
        assertEquals("#a855f7", values.custom_theme_seed)
    }

    @Test
    fun writes_shared_fields_when_sync_is_on() {
        val next = with_theme_values(shared, theme = "light", color_theme = "green")

        assertEquals("light", next.theme)
        assertEquals("green", next.color_theme)
        assertEquals("", next.theme_android)
        assertEquals("", next.color_theme_android)
    }

    @Test
    fun writes_only_platform_fields_when_sync_is_off() {
        val prefs = with_theme_sync_enabled(shared, false)

        val next = with_theme_values(prefs, theme = "light", custom_theme_seed = "#22c55e")

        assertEquals("dark", next.theme)
        assertEquals("purple", next.color_theme)
        assertEquals("#a855f7", next.custom_theme_seed)
        assertEquals("light", next.theme_android)
        assertEquals("purple", next.color_theme_android)
        assertEquals("#22c55e", next.custom_theme_seed_android)
    }

    @Test
    fun turning_sync_off_keeps_the_visible_theme() {
        val before = effective_theme_values(shared)

        val next = with_theme_sync_enabled(shared, false)

        assertFalse(theme_sync_enabled(next))
        assertEquals(before, effective_theme_values(next))
        assertEquals("dark", next.theme_android)
        assertEquals("purple", next.color_theme_android)
        assertEquals("#a855f7", next.custom_theme_seed_android)
        assertEquals("dark", next.theme)
        assertEquals("purple", next.color_theme)
    }

    @Test
    fun turning_sync_on_adopts_the_shared_theme() {
        val prefs = shared.copy(
            theme_sync_enabled_android = false,
            theme_android = "light",
            color_theme_android = "green",
            custom_theme_seed_android = "#22c55e",
        )

        val next = with_theme_sync_enabled(prefs, true)
        val values = effective_theme_values(next)

        assertTrue(theme_sync_enabled(next))
        assertEquals("dark", values.theme)
        assertEquals("purple", values.color_theme)
        assertEquals("#a855f7", values.custom_theme_seed)
    }

    @Test
    fun round_trip_preserves_other_platform_fields() {
        val server_obj = json.encodeToJsonElement(UserPreferences.serializer(), shared).jsonObject
        val server_raw = buildString {
            append("{")
            append(server_obj.entries.joinToString(",") { (k, v) -> "\"$k\":$v" })
            append(",\"theme_sync_enabled_ios\":false")
            append(",\"theme_ios\":\"light\"")
            append(",\"color_theme_ios\":\"green\"")
            append(",\"custom_theme_seed_ios\":\"#22c55e\"")
            append(",\"theme_sync_enabled_web\":true")
            append(",\"theme_web\":\"\"")
            append(",\"color_theme_web\":\"\"")
            append(",\"custom_theme_seed_web\":\"\"")
            append("}")
        }

        val merged = merge_decrypted_preferences(json, server_raw, null)
        val updated = with_theme_values(
            with_theme_sync_enabled(merged, false),
            theme = "light",
        )
        val payload = encode_preferences_preserving_unknown(json, updated, server_raw)
        val payload_obj = json.parseToJsonElement(payload).jsonObject

        assertEquals(false, payload_obj["theme_sync_enabled_ios"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals("light", payload_obj["theme_ios"]?.jsonPrimitive?.content)
        assertEquals("green", payload_obj["color_theme_ios"]?.jsonPrimitive?.content)
        assertEquals("#22c55e", payload_obj["custom_theme_seed_ios"]?.jsonPrimitive?.content)
        assertEquals(true, payload_obj["theme_sync_enabled_web"]?.jsonPrimitive?.content?.toBoolean())
        assertEquals("light", payload_obj["theme_android"]?.jsonPrimitive?.content)
        assertEquals("dark", payload_obj["theme"]?.jsonPrimitive?.content)
        assertEquals(false, payload_obj["theme_sync_enabled_android"]?.jsonPrimitive?.content?.toBoolean())
    }

    @Test
    fun android_fields_survive_a_decrypt_merge() {
        val prefs = shared.copy(
            theme_sync_enabled_android = false,
            theme_android = "light",
            color_theme_android = "green",
            custom_theme_seed_android = "#22c55e",
        )
        val raw = json.encodeToString(UserPreferences.serializer(), prefs)

        val merged = merge_decrypted_preferences(json, raw, null)

        assertEquals(normalize_order_preferences(prefs), merged)
        assertNull(json.parseToJsonElement(raw).jsonObject["theme_ios"])
    }
}
