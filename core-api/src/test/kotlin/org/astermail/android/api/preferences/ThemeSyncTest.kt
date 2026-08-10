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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSyncTest {

    private fun prefs(
        theme: String = "system",
        color_theme: String = "default",
        custom_theme_seed: String = "#3b82f6",
        sync: Boolean = true,
        theme_android: String = "",
        color_theme_android: String = "",
        custom_theme_seed_android: String = "",
    ) = UserPreferences(
        theme = theme,
        color_theme = color_theme,
        custom_theme_seed = custom_theme_seed,
        theme_sync_enabled_android = sync,
        theme_android = theme_android,
        color_theme_android = color_theme_android,
        custom_theme_seed_android = custom_theme_seed_android,
    )

    @Test
    fun sync_defaults_to_enabled() {
        assertTrue(theme_sync_enabled(UserPreferences()))
    }

    @Test
    fun effective_values_use_shared_fields_when_sync_is_on() {
        val values = effective_theme_values(
            prefs(
                theme = "dark",
                color_theme = "teal",
                custom_theme_seed = "#111111",
                theme_android = "light",
                color_theme_android = "amber",
                custom_theme_seed_android = "#f0f0f0",
            ),
        )

        assertEquals("dark", values.theme)
        assertEquals("teal", values.color_theme)
        assertEquals("#111111", values.custom_theme_seed)
    }

    @Test
    fun effective_values_use_android_fields_when_sync_is_off() {
        val values = effective_theme_values(
            prefs(
                theme = "dark",
                color_theme = "teal",
                custom_theme_seed = "#111111",
                sync = false,
                theme_android = "light",
                color_theme_android = "amber",
                custom_theme_seed_android = "#f0f0f0",
            ),
        )

        assertEquals("light", values.theme)
        assertEquals("amber", values.color_theme)
        assertEquals("#f0f0f0", values.custom_theme_seed)
    }

    @Test
    fun unset_android_fields_fall_back_to_shared_values() {
        val values = effective_theme_values(
            prefs(theme = "dark", color_theme = "teal", custom_theme_seed = "#111111", sync = false),
        )

        assertEquals("dark", values.theme)
        assertEquals("teal", values.color_theme)
        assertEquals("#111111", values.custom_theme_seed)
    }

    @Test
    fun an_unrecognized_theme_falls_back_rather_than_propagating() {
        assertEquals("system", effective_theme_values(prefs(theme = "neon")).theme)
        assertEquals(
            "dark",
            effective_theme_values(prefs(theme = "dark", sync = false, theme_android = "neon")).theme,
        )
    }

    @Test
    fun writes_target_shared_fields_when_sync_is_on() {
        val next = with_theme_values(prefs(), theme = "dark", color_theme = "rose")

        assertEquals("dark", next.theme)
        assertEquals("rose", next.color_theme)
        assertEquals("", next.theme_android)
        assertEquals("", next.color_theme_android)
    }

    @Test
    fun writes_target_android_fields_when_sync_is_off() {
        val base = prefs(theme = "system", color_theme = "default", sync = false)
        val next = with_theme_values(base, theme = "dark", color_theme = "rose")

        assertEquals("dark", next.theme_android)
        assertEquals("rose", next.color_theme_android)
        assertEquals("system", next.theme)
        assertEquals("default", next.color_theme)
    }

    @Test
    fun a_null_change_leaves_the_field_untouched() {
        val base = prefs(theme = "dark", color_theme = "teal", custom_theme_seed = "#111111")
        val next = with_theme_values(base, color_theme = "rose")

        assertEquals("dark", next.theme)
        assertEquals("rose", next.color_theme)
        assertEquals("#111111", next.custom_theme_seed)
    }

    @Test
    fun disabling_sync_captures_the_current_look_on_the_device() {
        val base = prefs(theme = "dark", color_theme = "teal", custom_theme_seed = "#111111")
        val next = with_theme_sync_enabled(base, false)

        assertFalse(theme_sync_enabled(next))
        assertEquals("dark", next.theme_android)
        assertEquals("teal", next.color_theme_android)
        assertEquals("#111111", next.custom_theme_seed_android)
        assertEquals(effective_theme_values(base), effective_theme_values(next))
    }

    @Test
    fun re_enabling_sync_keeps_the_captured_android_values_for_later() {
        val off = with_theme_sync_enabled(
            prefs(theme = "dark", color_theme = "teal"),
            false,
        )
        val on = with_theme_sync_enabled(off, true)

        assertTrue(theme_sync_enabled(on))
        assertEquals("dark", on.theme_android)
        assertEquals("teal", on.color_theme_android)
    }

    @Test
    fun shared_fields_survive_a_device_only_edit() {
        val base = prefs(theme = "dark", color_theme = "teal", custom_theme_seed = "#111111")
        val off = with_theme_sync_enabled(base, false)
        val edited = with_theme_values(off, theme = "light", color_theme = "amber")

        assertEquals("dark", edited.theme)
        assertEquals("teal", edited.color_theme)
        assertEquals("#111111", edited.custom_theme_seed)
        assertEquals("light", effective_theme_values(edited).theme)
        assertEquals("amber", effective_theme_values(edited).color_theme)
    }

    @Test
    fun unrelated_preferences_are_never_touched() {
        val base = prefs().copy(
            language = "fr",
            custom_theme_overrides = mapOf("primary" to "#abcdef"),
        )
        val next = with_theme_values(with_theme_sync_enabled(base, false), theme = "light")

        assertEquals("fr", next.language)
        assertEquals(mapOf("primary" to "#abcdef"), next.custom_theme_overrides)
    }
}
