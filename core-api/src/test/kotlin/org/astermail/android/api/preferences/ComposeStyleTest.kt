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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeStyleTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun the_shared_labels_map_to_the_shared_pixel_sizes() {
        assertEquals(listOf("small", "normal", "large", "huge"), compose_font_size_labels)
        assertEquals(12, compose_font_size_px("small"))
        assertEquals(14, compose_font_size_px("normal"))
        assertEquals(18, compose_font_size_px("large"))
        assertEquals(24, compose_font_size_px("huge"))
    }

    @Test
    fun an_unknown_size_label_falls_back_to_normal() {
        assertEquals("normal", normalize_compose_font_size("gigantic"))
        assertEquals("normal", normalize_compose_font_size(""))
        assertEquals("normal", normalize_compose_font_size(null))
        assertEquals(14, compose_font_size_px("gigantic"))
        assertEquals(14, compose_font_size_px(null))
    }

    @Test
    fun a_size_label_is_trimmed_and_case_insensitive() {
        assertEquals("large", normalize_compose_font_size("  LARGE "))
        assertEquals(18, compose_font_size_px("Large"))
    }

    @Test
    fun a_valid_six_digit_hex_color_is_kept_and_lowercased() {
        assertEquals("#1a73e8", normalize_compose_font_color("#1a73e8"))
        assertEquals("#1a73e8", normalize_compose_font_color("#1A73E8"))
        assertEquals("#1a73e8", normalize_compose_font_color("  #1A73E8  "))
    }

    @Test
    fun a_hostile_or_malformed_color_normalizes_to_the_theme_default() {
        val rejected = listOf(
            "red;background:url(x)",
            "#1a73e8;background:url(x)",
            "red",
            "#abc",
            "#1a73e",
            "#1a73e88",
            "1a73e8",
            "#1a73eg",
            "#1a73e8;",
            "rgb(1,2,3)",
            "expression(alert(1))",
            "\"",
            "",
        )
        for (value in rejected) {
            assertEquals(value, "", normalize_compose_font_color(value))
            assertNull(value, compose_font_color_argb(value))
        }
        assertEquals("", normalize_compose_font_color(null))
        assertNull(compose_font_color_argb(null))
    }

    @Test
    fun a_valid_color_converts_to_an_opaque_argb_value() {
        assertEquals(0xFF1A73E8.toInt(), compose_font_color_argb("#1a73e8"))
        assertEquals(0xFF000000.toInt(), compose_font_color_argb("#000000"))
        assertEquals(0xFFFFFFFF.toInt(), compose_font_color_argb("#ffffff"))
    }

    @Test
    fun color_conversion_never_throws_on_arbitrary_input() {
        val inputs = listOf("#zzzzzz", "#-00000", "#00000 ", "\u0000", "#00000\n", "##00000")
        for (value in inputs) assertNull(value, compose_font_color_argb(value))
    }

    @Test
    fun the_defaults_match_the_shared_contract() {
        val prefs = UserPreferences()
        assertEquals("normal", prefs.compose_font_size)
        assertEquals("", prefs.compose_font_color)
        assertEquals("normal", effective_compose_font_size(prefs))
        assertEquals("", effective_compose_font_color(prefs))
        assertEquals("normal", effective_compose_font_size(null))
        assertEquals("", effective_compose_font_color(null))
    }

    @Test
    fun corrupt_stored_values_are_repaired_at_the_point_of_use() {
        val prefs = UserPreferences(compose_font_size = "enormous", compose_font_color = "red;x:y")
        assertEquals("normal", effective_compose_font_size(prefs))
        assertEquals("", effective_compose_font_color(prefs))
    }

    @Test
    fun the_keys_survive_a_decrypted_blob_merge() {
        val incoming = """{"compose_font_size":"large","compose_font_color":"#1A73E8"}"""
        val merged = merge_decrypted_preferences(json, incoming, null)

        assertEquals("large", merged.compose_font_size)
        assertEquals("#1A73E8", merged.compose_font_color)
        assertEquals("#1a73e8", effective_compose_font_color(merged))
    }

    @Test
    fun a_wrongly_typed_blob_value_falls_back_to_the_stored_default() {
        val incoming = """{"compose_font_size":42,"compose_font_color":true}"""
        val merged = merge_decrypted_preferences(json, incoming, null)

        assertEquals("normal", merged.compose_font_size)
        assertEquals("", merged.compose_font_color)
    }

    @Test
    fun the_keys_survive_the_encode_path_alongside_unknown_keys() {
        val prefs = UserPreferences(compose_font_size = "huge", compose_font_color = "#1a73e8")
        val original = """{"compose_font_size":"small","some_future_key":"keep_me"}"""
        val encoded = encode_preferences_preserving_unknown(json, prefs, original)

        assertTrue(encoded.contains("\"compose_font_size\":\"huge\""))
        assertTrue(encoded.contains("\"compose_font_color\":\"#1a73e8\""))
        assertTrue(encoded.contains("\"some_future_key\":\"keep_me\""))
    }

    @Test
    fun a_round_trip_through_encode_and_merge_preserves_both_keys() {
        val prefs = UserPreferences(compose_font_size = "small", compose_font_color = "#0a0b0c")
        val encoded = encode_preferences_preserving_unknown(json, prefs, null)
        val merged = merge_decrypted_preferences(json, encoded, null)

        assertEquals("small", merged.compose_font_size)
        assertEquals("#0a0b0c", merged.compose_font_color)
    }

    @Test
    fun the_keys_are_not_web_aliased() {
        assertNull(web_preference_key_aliases["compose_font_size"])
        assertNull(web_preference_key_aliases["compose_font_color"])
    }

    @Test
    fun a_local_edit_rebases_onto_a_newer_remote_blob() {
        val remote = UserPreferences(compose_font_size = "large", language = "fr")
        val baseline = UserPreferences()
        val updated = baseline.copy(compose_font_color = "#1a73e8")
        val rebased = rebase_preferences_changes(json, remote, baseline, updated)

        assertEquals("large", rebased.compose_font_size)
        assertEquals("#1a73e8", rebased.compose_font_color)
        assertEquals("fr", rebased.language)
    }
}
