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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
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

val dynamic_color_supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private fun dynamic_palette(scheme: ColorScheme): ColorThemePalette = ColorThemePalette(
    bg_primary = scheme.surfaceContainer,
    bg_secondary = scheme.surfaceContainerLow,
    bg_tertiary = scheme.surfaceContainerHigh,
    bg_hover = scheme.surfaceContainerHighest,
    bg_selected = scheme.secondaryContainer,
    bg_card = scheme.surfaceContainerLowest,
    border_primary = scheme.outlineVariant,
    border_secondary = scheme.outlineVariant,
    text_primary = scheme.onSurface,
    text_secondary = scheme.onSurfaceVariant,
    text_tertiary = scheme.onSurfaceVariant,
    text_muted = scheme.outline,
    accent_color = scheme.primary,
    accent_color_hover = scheme.primary,
    avatar_bg = scheme.primaryContainer,
    avatar_text = scheme.onPrimaryContainer,
    indicator_bg = scheme.secondaryContainer,
    sidebar_bg = scheme.surfaceContainerLow,
    sidebar_hover = scheme.surfaceContainerHigh,
    modal_bg = scheme.surfaceContainerHigh,
    dropdown_bg = scheme.surfaceContainerHigh,
    dropdown_hover = scheme.surfaceContainerHighest,
    input_bg = scheme.surfaceContainerHighest,
    input_border = scheme.outlineVariant,
    thread_card_bg = scheme.surfaceContainerLowest,
    thread_card_bg_hover = scheme.surfaceContainerLow,
    thread_card_border = scheme.outlineVariant,
    thread_header_bg = scheme.surfaceContainer,
    thread_content_bg = scheme.surfaceContainerLowest,
)

private fun apply_high_contrast(base: AsterSemanticColors): AsterSemanticColors {
    return if (base.is_dark) {
        base.copy(
            text_primary = Color(0xFFFFFFFF),
            text_secondary = Color(0xFFF0F0F0),
            text_tertiary = Color(0xFFCCCCCC),
            text_muted = Color(0xFFAAAAAA),
            border_primary = Color(0xFF999999),
            border_secondary = Color(0xFF777777),
            border_thread_divider = Color(0xFF888888),
        )
    } else {
        base.copy(
            text_primary = Color(0xFF000000),
            text_secondary = Color(0xFF1A1A1A),
            text_tertiary = Color(0xFF333333),
            text_muted = Color(0xFF555555),
            border_primary = Color(0xFF666666),
            border_secondary = Color(0xFF888888),
            border_thread_divider = Color(0xFF555555),
        )
    }
}

private fun apply_reduce_transparency(base: AsterSemanticColors): AsterSemanticColors {
    return base.copy(
        modal_overlay = if (base.is_dark) Color(0xF2000000) else Color(0xBF000000),
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
    color_theme_id: ColorThemeId = ColorThemeId.default,
    custom_theme_seed: String? = null,
    custom_theme_overrides: Map<String, String> = emptyMap(),
    font_choice: String = DEFAULT_FONT_ID,
    content: @Composable () -> Unit,
) {
    val forced_dark = AsterColorThemes.is_dark_only(color_theme_id)
    val resolved_dark = if (forced_dark) {
        true
    } else {
        when (theme_mode) {
            AsterThemeMode.light -> false
            AsterThemeMode.dark -> true
            AsterThemeMode.system -> isSystemInDarkTheme()
            null -> use_dark_theme
        }
    }

    val context = LocalContext.current
    val palette = remember(color_theme_id, resolved_dark, custom_theme_seed, custom_theme_overrides, context) {
        when {
            color_theme_id == ColorThemeId.dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                dynamic_palette(
                    if (resolved_dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
                )
            }
            color_theme_id == ColorThemeId.custom -> MaterialThemeGenerator.to_palette(
                MaterialThemeGenerator.compute_custom_theme_vars(
                    seed_hex = custom_theme_seed?.takeIf { MaterialThemeGenerator.is_valid_hex_color(it) } ?: "#3b82f6",
                    is_dark = resolved_dark,
                    overrides = custom_theme_overrides,
                ),
            )
            else -> AsterColorThemes.palette_for(color_theme_id)
        }
    }

    val color_scheme = remember(resolved_dark, palette) { color_scheme_for(resolved_dark, palette) }
    val semantic = remember(resolved_dark, palette, high_contrast, reduce_transparency) {
        var built = AsterColorThemes.semantic_colors_for(resolved_dark, palette)
        if (high_contrast) built = apply_high_contrast(built)
        if (reduce_transparency) built = apply_reduce_transparency(built)
        built
    }

    val downloadable_fonts = remember(context) { downloadable_fonts_available(context) }
    val chosen_font = remember(font_choice, downloadable_fonts) {
        font_family_for(font_choice, downloadable_fonts)
    }
    val fallback_font = if (downloadable_fonts) inter_family else FontFamily.Default
    val active_font = dyslexia_font ?: chosen_font ?: fallback_font
    val typography = remember(active_font, text_spacing, dyslexia_font, chosen_font, downloadable_fonts) {
        if (dyslexia_font != null || chosen_font != null || text_spacing || !downloadable_fonts) {
            build_typography(active_font, text_spacing)
        } else {
            aster_typography
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                @Suppress("DEPRECATION")
                window.statusBarColor = Color.Transparent.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
            }
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
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.Transparent.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.Transparent.toArgb()
    }
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    controller.isAppearanceLightStatusBars = dark_icons
    controller.isAppearanceLightNavigationBars = dark_icons
}
