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

package org.astermail.android.ui.common

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.astermail.android.design.AsterColorThemes
import org.astermail.android.design.ColorThemeId
import org.astermail.android.design.MaterialThemeGenerator
import org.astermail.android.storage.ThemeMode
import org.astermail.android.R
import org.astermail.android.storage.ThemeStore

fun theme_boot_splash_style(context: Context): Int {
    return try {
        val snapshot = ThemeStore.boot_snapshot(context)
        val theme_id = ColorThemeId.from_key(snapshot.color_theme)
        when (theme_id) {
            ColorThemeId.purple -> R.style.Theme_Aster_Splash_Purple
            ColorThemeId.green -> R.style.Theme_Aster_Splash_Green
            ColorThemeId.rose -> R.style.Theme_Aster_Splash_Rose
            ColorThemeId.orange -> R.style.Theme_Aster_Splash_Orange
            ColorThemeId.teal -> R.style.Theme_Aster_Splash_Teal
            ColorThemeId.indigo -> R.style.Theme_Aster_Splash_Indigo
            ColorThemeId.amber -> R.style.Theme_Aster_Splash_Amber
            ColorThemeId.cyan -> R.style.Theme_Aster_Splash_Cyan
            ColorThemeId.slate -> R.style.Theme_Aster_Splash_Slate
            ColorThemeId.aster_blue -> R.style.Theme_Aster_Splash_AsterBlue
            ColorThemeId.lime -> R.style.Theme_Aster_Splash_Lime
            ColorThemeId.fuchsia -> R.style.Theme_Aster_Splash_Fuchsia
            ColorThemeId.emerald -> R.style.Theme_Aster_Splash_Emerald
            ColorThemeId.pink -> R.style.Theme_Aster_Splash_Pink
            ColorThemeId.black -> R.style.Theme_Aster_Splash_Black
            else -> {
                val system_dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
                val dark = when (snapshot.theme_mode) {
                    ThemeMode.light -> false
                    ThemeMode.dark -> true
                    ThemeMode.system -> system_dark
                }
                if (dark) R.style.Theme_Aster_Splash_Dark else R.style.Theme_Aster_Splash_Light
            }
        }
    } catch (_: Throwable) {
        R.style.Theme_Aster_Splash_Dark
    }
}

fun theme_boot_background_argb(context: Context): Int {
    return try {
        resolve_boot_background(context).toArgb()
    } catch (_: Throwable) {
        val night = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        if (night) 0xFF121212.toInt() else 0xFFFFFFFF.toInt()
    }
}

private fun resolve_boot_background(context: Context): Color {
    val snapshot = ThemeStore.boot_snapshot(context)
    val theme_id = ColorThemeId.from_key(snapshot.color_theme)
    val system_dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val is_dark = when {
        AsterColorThemes.is_dark_only(theme_id) -> true
        snapshot.theme_mode == ThemeMode.light -> false
        snapshot.theme_mode == ThemeMode.dark -> true
        else -> system_dark
    }

    if (theme_id == ColorThemeId.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (is_dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        return scheme.surfaceContainer
    }

    val palette = if (theme_id == ColorThemeId.custom) {
        val seed = snapshot.custom_theme_seed
            .takeIf { MaterialThemeGenerator.is_valid_hex_color(it) } ?: "#3b82f6"
        MaterialThemeGenerator.to_palette(
            MaterialThemeGenerator.compute_custom_theme_vars(
                seed_hex = seed,
                is_dark = is_dark,
                overrides = snapshot.custom_theme_overrides,
            ),
        )
    } else {
        AsterColorThemes.palette_for(theme_id)
    }

    return AsterColorThemes.semantic_colors_for(is_dark, palette).bg_primary
}
