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

package org.astermail.android.design

import org.astermail.android.design.SquircleShape

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val aster_shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

enum class AsterThemeMode { system, light, dark }

private fun apply_high_contrast(base: AsterSemanticColors): AsterSemanticColors {
    return if (base.is_dark) {
        base.copy(
            text_primary = Color.White,
            text_secondary = Color(0xFFE0E0E0),
            text_tertiary = Color(0xFFBBBBBB),
            text_muted = Color(0xFF999999),
            border_primary = Color(0xFF666666),
            border_secondary = Color(0xFF555555),
            bg_card = Color(0xFF1A1A1A),
            bg_primary = Color.Black,
        )
    } else {
        base.copy(
            text_primary = Color.Black,
            text_secondary = Color(0xFF1A1A1A),
            text_tertiary = Color(0xFF333333),
            text_muted = Color(0xFF555555),
            border_primary = Color(0xFF888888),
            border_secondary = Color(0xFF999999),
            bg_card = Color.White,
            bg_primary = Color(0xFFF5F5F5),
        )
    }
}

private fun apply_reduce_transparency(base: AsterSemanticColors): AsterSemanticColors {
    return base.copy(
        modal_overlay = base.modal_overlay.copy(alpha = 1f),
        bg_hover = base.bg_hover.copy(alpha = 1f),
        bg_selected = base.bg_selected.copy(alpha = 1f),
        indicator_bg = base.indicator_bg.copy(alpha = 1f),
    )
}

@Composable
fun AsterTheme(
    use_dark_theme: Boolean = isSystemInDarkTheme(),
    theme_mode: AsterThemeMode? = null,
    high_contrast: Boolean = false,
    reduce_transparency: Boolean = false,
    dyslexia_font: FontFamily? = null,
    text_spacing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val resolved_dark = when (theme_mode) {
        AsterThemeMode.light -> false
        AsterThemeMode.dark -> true
        AsterThemeMode.system -> isSystemInDarkTheme()
        null -> use_dark_theme
    }
    val color_scheme = if (resolved_dark) dark_color_scheme else light_color_scheme
    var semantic = if (resolved_dark) dark_semantic_colors else light_semantic_colors
    if (high_contrast) semantic = apply_high_contrast(semantic)
    if (reduce_transparency) semantic = apply_reduce_transparency(semantic)

    val active_font = dyslexia_font ?: inter_family
    val typography = if (dyslexia_font != null || text_spacing) {
        build_typography(active_font, text_spacing)
    } else {
        aster_typography
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !resolved_dark
            controller.isAppearanceLightNavigationBars = !resolved_dark
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(
        local_aster_colors provides semantic,
        local_dyslexia_font provides dyslexia_font,
    ) {
        MaterialTheme(
            colorScheme = color_scheme,
            typography = typography,
            shapes = aster_shapes,
            content = content,
        )
    }
}

fun apply_system_bars(activity: Activity, dark_icons: Boolean) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.statusBarColor = Color.Transparent.toArgb()
    window.navigationBarColor = Color.Transparent.toArgb()
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = dark_icons
    controller.isAppearanceLightNavigationBars = dark_icons
}
