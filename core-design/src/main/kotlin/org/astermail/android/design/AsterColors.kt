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

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object AsterColors {
    val accent_blue = Color(0xFF3B82F6)
    val accent_blue_hover_light = Color(0xFF2563EB)
    val accent_blue_hover_dark = Color(0xFF60A5FA)

    val danger = Color(0xFFEF4444)
    val warning = Color(0xFFF59E0B)
    val success = Color(0xFF22C55E)
    val info = Color(0xFF3B82F6)
    val destructive = Color(0xFFDC2626)

    val light_bg_primary = Color(0xFFFFFFFF)
    val light_bg_secondary = Color(0xFFF5F5F5)
    val light_bg_tertiary = Color(0xFFF3F4F6)
    val light_bg_hover = Color(0xFFF9FAFB)
    val light_bg_selected = Color(0xFFEFF6FF)
    val light_bg_card = Color(0xFFFFFFFF)
    val light_border_primary = Color(0xFFE8E8E8)
    val light_border_secondary = Color(0xFFE5E7EB)
    val light_border_thread_divider = Color(0xFFE5E5E5)
    val light_text_primary = Color(0xFF111827)
    val light_text_secondary = Color(0xFF374151)
    val light_text_tertiary = Color(0xFF4B5563)
    val light_text_muted = Color(0xFF6B7280)
    val light_avatar_bg = Color(0xFFE5E7EB)
    val light_avatar_text = Color(0xFF6B7280)
    val light_indicator_bg = Color(0xFFFFFFFF)
    val light_indicator_border = Color(0xFFE8E8E8)
    val light_sidebar_bg = Color(0xFFF5F5F5)
    val light_sidebar_hover = Color(0xFFE0E0E0)
    val light_sidebar = Color(0xFFFAFAFA)
    val light_sidebar_foreground = Color(0xFF09090B)
    val light_sidebar_accent = Color(0xFFF4F4F5)
    val light_sidebar_border = Color(0xFFE8E8E8)
    val light_modal_bg = Color(0xFFFFFFFF)
    val light_modal_overlay = Color(0x80000000)
    val light_dropdown_bg = Color(0xFFFFFFFF)
    val light_dropdown_hover = Color(0xFFF3F4F6)
    val light_input_bg = Color(0xFFFFFFFF)
    val light_input_border = Color(0xFFD1D5DB)
    val light_card = Color(0xFFFFFFFF)
    val light_card_foreground = Color(0xFF09090B)
    val light_popover = Color(0xFFFFFFFF)
    val light_primary = Color(0xFF18181B)
    val light_primary_foreground = Color(0xFFFAFAFA)
    val light_secondary = Color(0xFFF4F4F5)
    val light_muted = Color(0xFFF4F4F5)
    val light_muted_foreground = Color(0xFF71717A)
    val light_ring = Color(0xFFA1A1AA)
    val light_thread_card_bg = Color(0xFFF8F8F8)
    val light_thread_card_bg_hover = Color(0xFFF0F0F0)
    val light_thread_card_border = Color(0xFFE0E0E0)
    val light_thread_header_bg = Color(0xFFF3F3F3)
    val light_thread_content_bg = Color(0xFFFFFFFF)

    val dark_bg_primary = Color(0xFF141414)
    val dark_bg_secondary = Color(0xFF1C1C1C)
    val dark_bg_tertiary = Color(0xFF1C1C1C)
    val dark_bg_hover = Color(0xFF242424)
    val dark_bg_selected = Color(0xFF1A2744)
    val dark_bg_card = Color(0xFF1E1E1E)
    val dark_border_primary = Color(0xFF333333)
    val dark_border_secondary = Color(0xFF2E2E2E)
    val dark_border_thread_divider = Color(0xFF333333)
    val dark_text_primary = Color(0xFFF5F5F5)
    val dark_text_secondary = Color(0xFFD4D4D4)
    val dark_text_tertiary = Color(0xFFA1A1AA)
    val dark_text_muted = Color(0xFF8A8A8A)
    val dark_avatar_bg = Color(0xFF2E2E2E)
    val dark_avatar_text = Color(0xFF9CA3AF)
    val dark_indicator_bg = Color(0xFF252525)
    val dark_indicator_border = Color(0xFF3A3A3A)
    val dark_sidebar_bg = Color(0xFF181818)
    val dark_sidebar_hover = Color(0xFF252525)
    val dark_sidebar = Color(0xFF1A1A1A)
    val dark_sidebar_foreground = Color(0xFFFAFAFA)
    val dark_sidebar_accent = Color(0xFF2A2A2E)
    val dark_sidebar_border = Color(0xFF333333)
    val dark_modal_bg = Color(0xFF1E1E1E)
    val dark_modal_overlay = Color(0x52000000)
    val dark_dropdown_bg = Color(0xFF1E1E1E)
    val dark_dropdown_hover = Color(0xFF2A2A2A)
    val dark_input_bg = Color(0xFF1E1E1E)
    val dark_input_border = Color(0xFF333333)
    val dark_card = Color(0xFF1E1E1E)
    val dark_card_foreground = Color(0xFFFAFAFA)
    val dark_popover = Color(0xFF1E1E1E)
    val dark_primary = Color(0xFFE4E4E7)
    val dark_primary_foreground = Color(0xFF18181B)
    val dark_secondary = Color(0xFF27272A)
    val dark_muted = Color(0xFF27272A)
    val dark_muted_foreground = Color(0xFFA1A1AA)
    val dark_ring = Color(0xFF71717A)
    val dark_thread_card_bg = Color(0xFF1C1C1C)
    val dark_thread_card_bg_hover = Color(0xFF252525)
    val dark_thread_card_border = Color(0xFF333333)
    val dark_thread_header_bg = Color(0xFF1E1E1E)
    val dark_thread_content_bg = Color(0xFF141414)
}

val light_color_scheme = lightColorScheme(
    primary = AsterColors.accent_blue,
    onPrimary = Color.White,
    primaryContainer = AsterColors.light_bg_selected,
    onPrimaryContainer = AsterColors.light_text_primary,
    secondary = AsterColors.light_text_secondary,
    onSecondary = Color.White,
    secondaryContainer = AsterColors.light_secondary,
    onSecondaryContainer = AsterColors.light_text_primary,
    tertiary = AsterColors.light_text_tertiary,
    onTertiary = Color.White,
    background = AsterColors.light_bg_primary,
    onBackground = AsterColors.light_text_primary,
    surface = AsterColors.light_bg_card,
    onSurface = AsterColors.light_text_primary,
    surfaceVariant = AsterColors.light_bg_secondary,
    onSurfaceVariant = AsterColors.light_text_tertiary,
    surfaceTint = AsterColors.accent_blue,
    inverseSurface = AsterColors.dark_bg_card,
    inverseOnSurface = AsterColors.dark_text_primary,
    outline = AsterColors.light_border_primary,
    outlineVariant = AsterColors.light_border_secondary,
    error = AsterColors.danger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = AsterColors.destructive,
    scrim = AsterColors.light_modal_overlay,
)

val dark_color_scheme = darkColorScheme(
    primary = AsterColors.accent_blue,
    onPrimary = Color.White,
    primaryContainer = AsterColors.dark_bg_selected,
    onPrimaryContainer = AsterColors.dark_text_primary,
    secondary = AsterColors.dark_text_secondary,
    onSecondary = AsterColors.dark_bg_primary,
    secondaryContainer = AsterColors.dark_secondary,
    onSecondaryContainer = AsterColors.dark_text_primary,
    tertiary = AsterColors.dark_text_tertiary,
    onTertiary = AsterColors.dark_bg_primary,
    background = AsterColors.dark_bg_primary,
    onBackground = AsterColors.dark_text_primary,
    surface = AsterColors.dark_bg_card,
    onSurface = AsterColors.dark_text_primary,
    surfaceVariant = AsterColors.dark_bg_secondary,
    onSurfaceVariant = AsterColors.dark_text_tertiary,
    surfaceTint = Color.Transparent,
    inverseSurface = AsterColors.light_bg_card,
    inverseOnSurface = AsterColors.light_text_primary,
    outline = AsterColors.dark_border_primary,
    outlineVariant = AsterColors.dark_border_secondary,
    error = AsterColors.danger,
    onError = Color.White,
    errorContainer = Color(0xFF3F1212),
    onErrorContainer = Color(0xFFFCA5A5),
    scrim = Color(0x66000000),
)
