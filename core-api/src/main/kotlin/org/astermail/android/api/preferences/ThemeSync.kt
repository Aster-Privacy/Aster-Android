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

private val theme_values = setOf("light", "dark", "system")

data class ThemeValues(
    val theme: String,
    val color_theme: String,
    val custom_theme_seed: String,
)

private fun read_theme(value: String): String? = if (value in theme_values) value else null

private fun read_non_blank(value: String): String? = value.ifBlank { null }

fun theme_sync_enabled(preferences: UserPreferences): Boolean =
    preferences.theme_sync_enabled_android

fun effective_theme_values(preferences: UserPreferences): ThemeValues {
    val shared = ThemeValues(
        theme = read_theme(preferences.theme) ?: "system",
        color_theme = read_non_blank(preferences.color_theme) ?: "default",
        custom_theme_seed = preferences.custom_theme_seed,
    )

    if (theme_sync_enabled(preferences)) return shared

    return ThemeValues(
        theme = read_theme(preferences.theme_android) ?: shared.theme,
        color_theme = read_non_blank(preferences.color_theme_android) ?: shared.color_theme,
        custom_theme_seed = read_non_blank(preferences.custom_theme_seed_android)
            ?: shared.custom_theme_seed,
    )
}

fun with_theme_values(
    preferences: UserPreferences,
    theme: String? = null,
    color_theme: String? = null,
    custom_theme_seed: String? = null,
): UserPreferences {
    if (theme_sync_enabled(preferences)) {
        return preferences.copy(
            theme = theme ?: preferences.theme,
            color_theme = color_theme ?: preferences.color_theme,
            custom_theme_seed = custom_theme_seed ?: preferences.custom_theme_seed,
        )
    }

    return preferences.copy(
        theme_android = theme ?: preferences.theme_android,
        color_theme_android = color_theme ?: preferences.color_theme_android,
        custom_theme_seed_android = custom_theme_seed ?: preferences.custom_theme_seed_android,
    )
}

fun with_theme_sync_enabled(
    preferences: UserPreferences,
    enabled: Boolean,
): UserPreferences {
    if (enabled) return preferences.copy(theme_sync_enabled_android = true)

    val effective = effective_theme_values(preferences)

    return preferences.copy(
        theme_android = effective.theme,
        color_theme_android = effective.color_theme,
        custom_theme_seed_android = effective.custom_theme_seed,
        theme_sync_enabled_android = false,
    )
}
