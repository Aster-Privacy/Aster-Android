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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AsterSemanticColors(
    val bg_primary: Color,
    val bg_secondary: Color,
    val bg_tertiary: Color,
    val bg_hover: Color,
    val bg_selected: Color,
    val bg_card: Color,
    val border_primary: Color,
    val border_secondary: Color,
    val border_thread_divider: Color,
    val text_primary: Color,
    val text_secondary: Color,
    val text_tertiary: Color,
    val text_muted: Color,
    val accent_blue: Color,
    val accent_blue_hover: Color,
    val avatar_bg: Color,
    val avatar_text: Color,
    val indicator_bg: Color,
    val indicator_border: Color,
    val sidebar_bg: Color,
    val sidebar_hover: Color,
    val modal_bg: Color,
    val modal_overlay: Color,
    val dropdown_bg: Color,
    val dropdown_hover: Color,
    val input_bg: Color,
    val input_border: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val info: Color,
    val thread_card_bg: Color,
    val thread_card_bg_hover: Color,
    val thread_card_border: Color,
    val thread_header_bg: Color,
    val thread_content_bg: Color,
    val is_dark: Boolean,
)

val light_semantic_colors = AsterSemanticColors(
    bg_primary = AsterColors.light_bg_primary,
    bg_secondary = AsterColors.light_bg_secondary,
    bg_tertiary = AsterColors.light_bg_tertiary,
    bg_hover = AsterColors.light_bg_hover,
    bg_selected = AsterColors.light_bg_selected,
    bg_card = AsterColors.light_bg_card,
    border_primary = AsterColors.light_border_primary,
    border_secondary = AsterColors.light_border_secondary,
    border_thread_divider = AsterColors.light_border_thread_divider,
    text_primary = AsterColors.light_text_primary,
    text_secondary = AsterColors.light_text_secondary,
    text_tertiary = AsterColors.light_text_tertiary,
    text_muted = AsterColors.light_text_muted,
    accent_blue = AsterColors.accent_blue,
    accent_blue_hover = AsterColors.accent_blue_hover_light,
    avatar_bg = AsterColors.light_avatar_bg,
    avatar_text = AsterColors.light_avatar_text,
    indicator_bg = AsterColors.light_indicator_bg,
    indicator_border = AsterColors.light_indicator_border,
    sidebar_bg = AsterColors.light_sidebar_bg,
    sidebar_hover = AsterColors.light_sidebar_hover,
    modal_bg = AsterColors.light_modal_bg,
    modal_overlay = AsterColors.light_modal_overlay,
    dropdown_bg = AsterColors.light_dropdown_bg,
    dropdown_hover = AsterColors.light_dropdown_hover,
    input_bg = AsterColors.light_input_bg,
    input_border = AsterColors.light_input_border,
    danger = AsterColors.danger,
    warning = AsterColors.warning,
    success = AsterColors.success,
    info = AsterColors.info,
    thread_card_bg = AsterColors.light_thread_card_bg,
    thread_card_bg_hover = AsterColors.light_thread_card_bg_hover,
    thread_card_border = AsterColors.light_thread_card_border,
    thread_header_bg = AsterColors.light_thread_header_bg,
    thread_content_bg = AsterColors.light_thread_content_bg,
    is_dark = false,
)

val dark_semantic_colors = AsterSemanticColors(
    bg_primary = AsterColors.dark_bg_primary,
    bg_secondary = AsterColors.dark_bg_secondary,
    bg_tertiary = AsterColors.dark_bg_tertiary,
    bg_hover = AsterColors.dark_bg_hover,
    bg_selected = AsterColors.dark_bg_selected,
    bg_card = AsterColors.dark_bg_card,
    border_primary = AsterColors.dark_border_primary,
    border_secondary = AsterColors.dark_border_secondary,
    border_thread_divider = AsterColors.dark_border_thread_divider,
    text_primary = AsterColors.dark_text_primary,
    text_secondary = AsterColors.dark_text_secondary,
    text_tertiary = AsterColors.dark_text_tertiary,
    text_muted = AsterColors.dark_text_muted,
    accent_blue = AsterColors.accent_blue,
    accent_blue_hover = AsterColors.accent_blue_hover_dark,
    avatar_bg = AsterColors.dark_avatar_bg,
    avatar_text = AsterColors.dark_avatar_text,
    indicator_bg = AsterColors.dark_indicator_bg,
    indicator_border = AsterColors.dark_indicator_border,
    sidebar_bg = AsterColors.dark_sidebar_bg,
    sidebar_hover = AsterColors.dark_sidebar_hover,
    modal_bg = AsterColors.dark_modal_bg,
    modal_overlay = AsterColors.dark_modal_overlay,
    dropdown_bg = AsterColors.dark_dropdown_bg,
    dropdown_hover = AsterColors.dark_dropdown_hover,
    input_bg = AsterColors.dark_input_bg,
    input_border = AsterColors.dark_input_border,
    danger = AsterColors.danger,
    warning = AsterColors.warning,
    success = AsterColors.success,
    info = AsterColors.info,
    thread_card_bg = AsterColors.dark_thread_card_bg,
    thread_card_bg_hover = AsterColors.dark_thread_card_bg_hover,
    thread_card_border = AsterColors.dark_thread_card_border,
    thread_header_bg = AsterColors.dark_thread_header_bg,
    thread_content_bg = AsterColors.dark_thread_content_bg,
    is_dark = true,
)

val local_aster_colors = staticCompositionLocalOf { light_semantic_colors }

object AsterMaterial {
    val colors: AsterSemanticColors
        @Composable
        @ReadOnlyComposable
        get() = local_aster_colors.current
}
