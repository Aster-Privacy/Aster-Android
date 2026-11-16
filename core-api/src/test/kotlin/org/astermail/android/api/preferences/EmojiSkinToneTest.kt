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

class EmojiSkinToneTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun cp(vararg points: Int): String {
        val builder = StringBuilder()
        points.forEach { builder.appendCodePoint(it) }
        return builder.toString()
    }

    @Test
    fun the_default_tone_leaves_every_emoji_unchanged() {
        assertEquals(cp(0x1F44D), apply_emoji_skin_tone(cp(0x1F44D), "default"))
        assertEquals(cp(0x1F469, 0x200D, 0x1F4BB), apply_emoji_skin_tone(cp(0x1F469, 0x200D, 0x1F4BB), "default"))
    }

    @Test
    fun a_single_hand_emoji_gets_the_modifier() {
        assertEquals(cp(0x1F44D, 0x1F3FD), apply_emoji_skin_tone(cp(0x1F44D), "medium"))
        assertEquals(cp(0x1F44B, 0x1F3FF), apply_emoji_skin_tone(cp(0x1F44B), "dark"))
    }

    @Test
    fun the_modifier_replaces_the_variation_selector() {
        assertEquals(cp(0x270C, 0x1F3FB), apply_emoji_skin_tone(cp(0x270C, 0xFE0F), "light"))
        assertEquals(cp(0x261D, 0x1F3FE), apply_emoji_skin_tone(cp(0x261D, 0xFE0F), "medium_dark"))
    }

    @Test
    fun the_modifier_follows_the_person_in_a_profession_sequence() {
        assertEquals(
            cp(0x1F469, 0x1F3FC, 0x200D, 0x1F4BB),
            apply_emoji_skin_tone(cp(0x1F469, 0x200D, 0x1F4BB), "medium_light"),
        )
        assertEquals(
            cp(0x1F9D1, 0x1F3FF, 0x200D, 0x1F692),
            apply_emoji_skin_tone(cp(0x1F9D1, 0x200D, 0x1F692), "dark"),
        )
    }

    @Test
    fun a_gendered_sequence_keeps_its_gender_sign() {
        assertEquals(
            cp(0x1F575, 0x1F3FD, 0x200D, 0x2640, 0xFE0F),
            apply_emoji_skin_tone(cp(0x1F575, 0xFE0F, 0x200D, 0x2640, 0xFE0F), "medium"),
        )
        assertEquals(
            cp(0x1F937, 0x1F3FB, 0x200D, 0x2642, 0xFE0F),
            apply_emoji_skin_tone(cp(0x1F937, 0x200D, 0x2642, 0xFE0F), "light"),
        )
    }

    @Test
    fun both_people_are_toned_in_holding_hands_and_couple_sequences() {
        assertEquals(
            cp(0x1F9D1, 0x1F3FF, 0x200D, 0x1F91D, 0x200D, 0x1F9D1, 0x1F3FF),
            apply_emoji_skin_tone(cp(0x1F9D1, 0x200D, 0x1F91D, 0x200D, 0x1F9D1), "dark"),
        )
        assertEquals(
            cp(0x1F469, 0x1F3FD, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F468, 0x1F3FD),
            apply_emoji_skin_tone(cp(0x1F469, 0x200D, 0x2764, 0xFE0F, 0x200D, 0x1F468), "medium"),
        )
    }

    @Test
    fun neutral_emoji_and_families_stay_the_same() {
        val unchanged = listOf(
            cp(0x1F602),
            cp(0x2764, 0xFE0F),
            cp(0x1F389),
            cp(0x1F525),
            cp(0x1F3F3, 0xFE0F, 0x200D, 0x1F308),
            cp(0x1F441, 0xFE0F, 0x200D, 0x1F5E8, 0xFE0F),
            cp(0x1F43B, 0x200D, 0x2744, 0xFE0F),
            cp(0x1F468, 0x200D, 0x1F469, 0x200D, 0x1F467),
            cp(0x1F46A),
            cp(0x1F93C),
        )
        for (emoji in unchanged) assertEquals(emoji, apply_emoji_skin_tone(emoji, "dark"))
    }

    @Test
    fun an_already_toned_emoji_never_gets_a_second_modifier() {
        val toned = cp(0x1F44D, 0x1F3FB)

        assertFalse(is_tone_capable_emoji(toned))
        assertEquals(toned, apply_emoji_skin_tone(toned, "dark"))
    }

    @Test
    fun every_tone_gives_a_distinct_emoji() {
        val results = emoji_skin_tones.map { apply_emoji_skin_tone(cp(0x1F44D), it) }.toSet()

        assertEquals(emoji_skin_tones.size, results.size)
    }

    @Test
    fun people_and_hands_are_tone_capable_and_objects_are_not() {
        assertTrue(is_tone_capable_emoji(cp(0x1F44D)))
        assertTrue(is_tone_capable_emoji(cp(0x1F91D)))
        assertTrue(is_tone_capable_emoji(cp(0x1F469, 0x200D, 0x1F4BB)))
        assertFalse(is_tone_capable_emoji(cp(0x1F602)))
        assertFalse(is_tone_capable_emoji(""))
    }

    @Test
    fun the_web_and_this_client_agree_on_the_tone_names() {
        assertEquals(listOf("default", "light", "medium_light", "medium", "medium_dark", "dark"), emoji_skin_tones)
        for (tone in emoji_skin_tones) assertEquals(tone, normalize_emoji_skin_tone(tone))
        assertEquals("default", normalize_emoji_skin_tone("purple"))
        assertEquals("default", normalize_emoji_skin_tone(null))
    }

    @Test
    fun the_setting_is_default_when_the_blob_lacks_it() {
        assertEquals("default", UserPreferences().emoji_skin_tone)
        assertEquals("default", merge_decrypted_preferences(json, """{"theme":"dark"}""", null).emoji_skin_tone)
    }

    @Test
    fun an_unknown_or_malformed_tone_reads_as_default() {
        for (raw in listOf("\"purple\"", "3", "null", "true", "\"MEDIUM\"", "{}", "\"\"")) {
            val prefs = merge_decrypted_preferences(
                json,
                """{"emoji_skin_tone":$raw,"relative_dates":false}""",
                null,
            )

            assertEquals("value $raw", "default", prefs.emoji_skin_tone)
            assertFalse("value $raw", prefs.relative_dates)
        }
    }

    @Test
    fun a_tone_saved_by_the_web_client_loads_here() {
        val prefs = merge_decrypted_preferences(json, """{"emoji_skin_tone":"medium_dark"}""", null)

        assertEquals("medium_dark", prefs.emoji_skin_tone)
    }

    @Test
    fun the_tone_survives_a_save_and_keeps_unknown_keys() {
        val saved = encode_preferences_preserving_unknown(
            json,
            UserPreferences(emoji_skin_tone = "light"),
            """{"setting_from_a_future_client":"kept"}""",
        )
        val stored = json.parseToJsonElement(saved).jsonObject

        assertEquals(JsonPrimitive("light"), stored["emoji_skin_tone"])
        assertEquals(JsonPrimitive("kept"), stored["setting_from_a_future_client"])
        assertEquals("light", merge_decrypted_preferences(json, saved, null).emoji_skin_tone)
    }

    @Test
    fun changing_the_tone_on_one_device_reaches_the_other() {
        val loaded = """{"emoji_skin_tone":"dark","relative_dates":false}"""
        val baseline = merge_decrypted_preferences(json, loaded, null)
        val updated = baseline.copy(emoji_skin_tone = "medium")
        val to_save = rebase_preferences_changes(json, baseline, baseline, updated)
        val payload = encode_preferences_preserving_unknown(json, to_save, loaded)

        val other_device = merge_decrypted_preferences(json, payload, baseline)

        assertEquals("medium", other_device.emoji_skin_tone)
        assertFalse(other_device.relative_dates)
    }
}
