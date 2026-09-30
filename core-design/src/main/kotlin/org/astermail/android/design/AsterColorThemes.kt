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

import androidx.compose.ui.graphics.Color

enum class ColorThemeId {
    default,
    custom,
    dynamic,
    purple,
    green,
    rose,
    orange,
    teal,
    indigo,
    amber,
    cyan,
    slate,
    aster_blue,
    lime,
    fuchsia,
    emerald,
    pink,
    black,
    ;

    companion object {
        fun from_key(key: String?): ColorThemeId = entries.firstOrNull { it.name == key } ?: default
    }
}

data class ColorThemePalette(
    val bg_primary: Color,
    val bg_secondary: Color,
    val bg_tertiary: Color,
    val bg_hover: Color,
    val bg_selected: Color,
    val bg_card: Color,
    val border_primary: Color,
    val border_secondary: Color,
    val text_primary: Color,
    val text_secondary: Color,
    val text_tertiary: Color,
    val text_muted: Color,
    val accent_color: Color,
    val accent_color_hover: Color,
    val avatar_bg: Color,
    val avatar_text: Color,
    val indicator_bg: Color,
    val sidebar_bg: Color,
    val sidebar_hover: Color,
    val modal_bg: Color,
    val dropdown_bg: Color,
    val dropdown_hover: Color,
    val input_bg: Color,
    val input_border: Color,
    val thread_card_bg: Color,
    val thread_card_bg_hover: Color,
    val thread_card_border: Color,
    val thread_header_bg: Color,
    val thread_content_bg: Color,
    val on_accent: Color = Color.White,
    val secondary_control_bg: Color = bg_secondary,
    val secondary_control_border: Color = Color.Transparent,
)

private fun c(hex: String): Color = Color(("FF" + hex.removePrefix("#")).toLong(16))

private fun palette(
    bg_primary: String,
    bg_secondary: String,
    bg_tertiary: String,
    bg_hover: String,
    bg_selected: String,
    avatar_bg: String,
    avatar_text: String,
    border_primary: String,
    border_secondary: String,
    text_primary: String,
    text_secondary: String,
    text_tertiary: String,
    text_muted: String,
    accent_color: String,
    accent_color_hover: String,
    sidebar_bg: String,
    sidebar_hover: String,
    input_bg: String,
    input_border: String,
    thread_card_bg: String,
    thread_card_bg_hover: String,
    thread_card_border: String,
    thread_header_bg: String,
    thread_content_bg: String,
    on_accent: String = "#ffffff",
    secondary_control_bg: String = bg_secondary,
    secondary_control_border: String? = null,
): ColorThemePalette = ColorThemePalette(
    bg_primary = c(bg_primary),
    bg_secondary = c(bg_secondary),
    bg_tertiary = c(bg_tertiary),
    bg_hover = c(bg_hover),
    bg_selected = c(bg_selected),
    bg_card = c(bg_primary),
    border_primary = c(border_primary),
    border_secondary = c(border_secondary),
    text_primary = c(text_primary),
    text_secondary = c(text_secondary),
    text_tertiary = c(text_tertiary),
    text_muted = c(text_muted),
    accent_color = c(accent_color),
    accent_color_hover = c(accent_color_hover),
    avatar_bg = c(avatar_bg),
    avatar_text = c(avatar_text),
    indicator_bg = c(bg_primary),
    sidebar_bg = c(sidebar_bg),
    sidebar_hover = c(sidebar_hover),
    modal_bg = c(bg_primary),
    dropdown_bg = c(bg_tertiary),
    dropdown_hover = c(bg_hover),
    input_bg = c(input_bg),
    input_border = c(input_border),
    thread_card_bg = c(thread_card_bg),
    thread_card_bg_hover = c(thread_card_bg_hover),
    thread_card_border = c(thread_card_border),
    thread_header_bg = c(thread_header_bg),
    thread_content_bg = c(thread_content_bg),
    on_accent = c(on_accent),
    secondary_control_bg = c(secondary_control_bg),
    secondary_control_border = secondary_control_border?.let { c(it) } ?: Color.Transparent,
)

object AsterColorThemes {
    val purple = palette(
        bg_primary = "#180a31", bg_secondary = "#100721", bg_tertiary = "#26163c", bg_hover = "#2c1a4a",
        bg_selected = "#341c5d", avatar_bg = "#33175a", avatar_text = "#cbb0e8",
        border_primary = "#402865", border_secondary = "#35214d",
        text_primary = "#f4effa", text_secondary = "#dcc8ef", text_tertiary = "#c4abe6", text_muted = "#b4a2c9",
        accent_color = "#ad5ef8", accent_color_hover = "#c590fa",
        sidebar_bg = "#100721", sidebar_hover = "#1a0a31",
        input_bg = "#26163c", input_border = "#48306b",
        thread_card_bg = "#26163c", thread_card_bg_hover = "#2c1a4a", thread_card_border = "#48306b",
        thread_header_bg = "#26163c", thread_content_bg = "#180a31",
        on_accent = "#100721",
    )

    val green = palette(
        bg_primary = "#08281c", bg_secondary = "#061b13", bg_tertiary = "#0b3323", bg_hover = "#0d3e28",
        bg_selected = "#0f4830", avatar_bg = "#115236", avatar_text = "#aee8c8",
        border_primary = "#1c593b", border_secondary = "#15432e",
        text_primary = "#effaf4", text_secondary = "#caefdb", text_tertiary = "#8fdeb4", text_muted = "#99c4ae",
        accent_color = "#1dd560", accent_color_hover = "#4de585",
        sidebar_bg = "#061b13", sidebar_hover = "#092e1c",
        input_bg = "#0b3323", input_border = "#1c593b",
        thread_card_bg = "#0b3323", thread_card_bg_hover = "#0d3e28", thread_card_border = "#1c593b",
        thread_header_bg = "#0b3323", thread_content_bg = "#08281c",
        on_accent = "#061b13",
    )

    val rose = palette(
        bg_primary = "#2e0914", bg_secondary = "#20070d", bg_tertiary = "#3e1224", bg_hover = "#481428",
        bg_selected = "#55132d", avatar_bg = "#571229", avatar_text = "#edbfcc",
        border_primary = "#5e2235", border_secondary = "#4b1e2d",
        text_primary = "#faf0f3", text_secondary = "#f0cbd7", text_tertiary = "#e5a7bb", text_muted = "#c9a1ae",
        accent_color = "#f74664", accent_color_hover = "#fa7d8f",
        sidebar_bg = "#20070d", sidebar_hover = "#300a17",
        input_bg = "#3e1224", input_border = "#652c3e",
        thread_card_bg = "#3e1224", thread_card_bg_hover = "#481428", thread_card_border = "#652c3e",
        thread_header_bg = "#3e1224", thread_content_bg = "#2e0914",
        on_accent = "#20070d",
    )

    val orange = palette(
        bg_primary = "#2b1a09", bg_secondary = "#1e1206", bg_tertiary = "#38230e", bg_hover = "#40270d",
        bg_selected = "#4f3010", avatar_bg = "#513111", avatar_text = "#ead0b5",
        border_primary = "#53381a", border_secondary = "#422e17",
        text_primary = "#faf3ed", text_secondary = "#eedcc6", text_tertiary = "#e0bc92", text_muted = "#cfb089",
        accent_color = "#f67a23", accent_color_hover = "#f7984a",
        sidebar_bg = "#1e1206", sidebar_hover = "#2d1b09",
        input_bg = "#38230e", input_border = "#583e20",
        thread_card_bg = "#38230e", thread_card_bg_hover = "#40270d", thread_card_border = "#583e20",
        thread_header_bg = "#38230e", thread_content_bg = "#2b1a09",
        on_accent = "#1e1206",
    )

    val teal = palette(
        bg_primary = "#082727", bg_secondary = "#061b1b", bg_tertiary = "#0b3333", bg_hover = "#0d3d3d",
        bg_selected = "#0f4747", avatar_bg = "#115151", avatar_text = "#a4e5de",
        border_primary = "#1c5858", border_secondary = "#154242",
        text_primary = "#ecf9f8", text_secondary = "#c7efea", text_tertiary = "#8dddd3", text_muted = "#9ac4be",
        accent_color = "#0ec894", accent_color_hover = "#2fdcab",
        sidebar_bg = "#061b1b", sidebar_hover = "#092e2e",
        input_bg = "#0b3333", input_border = "#1c5858",
        thread_card_bg = "#0b3333", thread_card_bg_hover = "#0d3d3d", thread_card_border = "#1c5858",
        thread_header_bg = "#0b3333", thread_content_bg = "#082727",
        on_accent = "#061b1b",
    )

    val indigo = palette(
        bg_primary = "#0c0e3b", bg_secondary = "#0a0a2e", bg_tertiary = "#161851", bg_hover = "#191c5f",
        bg_selected = "#1b216e", avatar_bg = "#161a69", avatar_text = "#d0d1f1",
        border_primary = "#2a2d72", border_secondary = "#232756",
        text_primary = "#f0f2fb", text_secondary = "#d3d5f2", text_tertiary = "#aeb2e7", text_muted = "#a3a6d2",
        accent_color = "#777af7", accent_color_hover = "#8994fa",
        sidebar_bg = "#0a0a2e", sidebar_hover = "#0e1042",
        input_bg = "#161851", input_border = "#333678",
        thread_card_bg = "#161851", thread_card_bg_hover = "#191c5f", thread_card_border = "#333678",
        thread_header_bg = "#161851", thread_content_bg = "#0c0e3b",
        on_accent = "#0a0a2e",
    )

    val amber = palette(
        bg_primary = "#231b07", bg_secondary = "#191305", bg_tertiary = "#31240a", bg_hover = "#3a2a0c",
        bg_selected = "#42310d", avatar_bg = "#3d2e0d", avatar_text = "#e7d8ae",
        border_primary = "#4d3d18", border_secondary = "#3b3017",
        text_primary = "#f8f4e8", text_secondary = "#ede1c0", text_tertiary = "#d9bf7d", text_muted = "#c9b174",
        accent_color = "#f5c115", accent_color_hover = "#f6dd33",
        sidebar_bg = "#191305", sidebar_hover = "#251b08",
        input_bg = "#31240a", input_border = "#50401b",
        thread_card_bg = "#31240a", thread_card_bg_hover = "#3a2a0c", thread_card_border = "#50401b",
        thread_header_bg = "#31240a", thread_content_bg = "#231b07",
        on_accent = "#191305",
    )

    val cyan = palette(
        bg_primary = "#081f26", bg_secondary = "#06171b", bg_tertiary = "#0b2a34", bg_hover = "#0d343d",
        bg_selected = "#0f3f4a", avatar_bg = "#0f3c49", avatar_text = "#abdee7",
        border_primary = "#18414b", border_secondary = "#16353e",
        text_primary = "#ecf8f9", text_secondary = "#c8eaef", text_tertiary = "#8ed1de", text_muted = "#82bcc8",
        accent_color = "#0996dd", accent_color_hover = "#24b0f6",
        sidebar_bg = "#06171b", sidebar_hover = "#082229",
        input_bg = "#0b2a34", input_border = "#204852",
        thread_card_bg = "#0b2a34", thread_card_bg_hover = "#0d343d", thread_card_border = "#204852",
        thread_header_bg = "#0b2a34", thread_content_bg = "#081f26",
        on_accent = "#06171b",
    )

    val slate = palette(
        bg_primary = "#16181c", bg_secondary = "#101114", bg_tertiary = "#202329", bg_hover = "#262a31",
        bg_selected = "#2b2f38", avatar_bg = "#2c2f36", avatar_text = "#cbd5e1",
        border_primary = "#343840", border_secondary = "#2b2e33",
        text_primary = "#f1f5f9", text_secondary = "#d6dce3", text_tertiary = "#b6bdc6", text_muted = "#aab0b8",
        accent_color = "#7d8da4", accent_color_hover = "#94a3b8",
        sidebar_bg = "#101114", sidebar_hover = "#191b1f",
        input_bg = "#202329", input_border = "#3c4047",
        thread_card_bg = "#202329", thread_card_bg_hover = "#262a31", thread_card_border = "#3c4047",
        thread_header_bg = "#202329", thread_content_bg = "#16181c",
        on_accent = "#101114",
    )

    val aster_blue = palette(
        bg_primary = "#0a1a33", bg_secondary = "#071423", bg_tertiary = "#0e2647", bg_hover = "#122e59",
        bg_selected = "#16386e", avatar_bg = "#16386c", avatar_text = "#cbddf0",
        border_primary = "#244470", border_secondary = "#1b3254",
        text_primary = "#eff3fa", text_secondary = "#d7e2f3", text_tertiary = "#a4c1e4", text_muted = "#97b3db",
        accent_color = "#4a8bf7", accent_color_hover = "#6babf9",
        sidebar_bg = "#071423", sidebar_hover = "#0d2240",
        input_bg = "#0e2647", input_border = "#244470",
        thread_card_bg = "#0e2647", thread_card_bg_hover = "#122e59", thread_card_border = "#244470",
        thread_header_bg = "#0e2647", thread_content_bg = "#0a1a33",
        on_accent = "#071423",
    )

    val lime = palette(
        bg_primary = "#1a2207", bg_secondary = "#131805", bg_tertiary = "#232f0a", bg_hover = "#2b390c",
        bg_selected = "#2f420d", avatar_bg = "#2d3d0d", avatar_text = "#d1e7ae",
        border_primary = "#3d4d18", border_secondary = "#2c3713",
        text_primary = "#f4f8e8", text_secondary = "#e3edc0", text_tertiary = "#bfd97d", text_muted = "#aac260",
        accent_color = "#8cdd0f", accent_color_hover = "#a9ee37",
        sidebar_bg = "#131805", sidebar_hover = "#1c2507",
        input_bg = "#232f0a", input_border = "#3d4d18",
        thread_card_bg = "#232f0a", thread_card_bg_hover = "#2b390c", thread_card_border = "#3d4d18",
        thread_header_bg = "#232f0a", thread_content_bg = "#1a2207",
        on_accent = "#131805",
    )

    val fuchsia = palette(
        bg_primary = "#2c092a", bg_secondary = "#200620", bg_tertiary = "#3c1236", bg_hover = "#471341",
        bg_selected = "#531249", avatar_bg = "#551148", avatar_text = "#ecbce1",
        border_primary = "#5b2152", border_secondary = "#481e40",
        text_primary = "#faecf9", text_secondary = "#efc7e8", text_tertiary = "#e5a4d5", text_muted = "#cc9fb8",
        accent_color = "#dc47e6", accent_color_hover = "#e850f0",
        sidebar_bg = "#200620", sidebar_hover = "#2c0a32",
        input_bg = "#3c1236", input_border = "#622b5a",
        thread_card_bg = "#3c1236", thread_card_bg_hover = "#471341", thread_card_border = "#622b5a",
        thread_header_bg = "#3c1236", thread_content_bg = "#2c092a",
        on_accent = "#200620",
    )

    val emerald = palette(
        bg_primary = "#072213", bg_secondary = "#051711", bg_tertiary = "#0a2f19", bg_hover = "#0c3b21",
        bg_selected = "#0e4728", avatar_bg = "#0e4222", avatar_text = "#b8ebc8",
        border_primary = "#1a5130", border_secondary = "#13391a",
        text_primary = "#ecf9f0", text_secondary = "#c3edce", text_tertiary = "#85dca6", text_muted = "#72c692",
        accent_color = "#33e128", accent_color_hover = "#62e753",
        sidebar_bg = "#051711", sidebar_hover = "#082514",
        input_bg = "#0a2f19", input_border = "#1a5130",
        thread_card_bg = "#0a2f19", thread_card_bg_hover = "#0c3b21", thread_card_border = "#1a5130",
        thread_header_bg = "#0a2f19", thread_content_bg = "#072213",
        on_accent = "#051711",
    )

    val pink = palette(
        bg_primary = "#2b091d", bg_secondary = "#1e0615", bg_tertiary = "#3c102a", bg_hover = "#471233",
        bg_selected = "#53133b", avatar_bg = "#511143", avatar_text = "#ebbad4",
        border_primary = "#5a2149", border_secondary = "#471e37",
        text_primary = "#faecf2", text_secondary = "#eec4dd", text_tertiary = "#e4a2ca", text_muted = "#d298bd",
        accent_color = "#e849aa", accent_color_hover = "#f26fbb",
        sidebar_bg = "#1e0615", sidebar_hover = "#2b0924",
        input_bg = "#3c102a", input_border = "#612b51",
        thread_card_bg = "#3c102a", thread_card_bg_hover = "#471233", thread_card_border = "#612b51",
        thread_header_bg = "#3c102a", thread_content_bg = "#2b091d",
        on_accent = "#1e0615",
    )

    val black = palette(
        bg_primary = "#0a0a0a", bg_secondary = "#000000", bg_tertiary = "#191919", bg_hover = "#1f1f1f",
        bg_selected = "#262626", avatar_bg = "#262626", avatar_text = "#e5e5e5",
        border_primary = "#303030", border_secondary = "#262626",
        text_primary = "#ffffff", text_secondary = "#e3e3e3", text_tertiary = "#bdbdbd", text_muted = "#a4a4a4",
        accent_color = "#f4f4f5", accent_color_hover = "#ffffff",
        sidebar_bg = "#000000", sidebar_hover = "#0a0a0a",
        input_bg = "#191919", input_border = "#393939",
        thread_card_bg = "#191919", thread_card_bg_hover = "#1f1f1f", thread_card_border = "#393939",
        thread_header_bg = "#191919", thread_content_bg = "#0a0a0a",
        on_accent = "#0a0a0a",
        secondary_control_bg = "#1a1a1a", secondary_control_border = "#2e2e2e",
    )

    fun palette_for(id: ColorThemeId): ColorThemePalette? = when (id) {
        ColorThemeId.default -> null
        ColorThemeId.custom -> null
        ColorThemeId.dynamic -> null
        ColorThemeId.purple -> purple
        ColorThemeId.green -> green
        ColorThemeId.rose -> rose
        ColorThemeId.orange -> orange
        ColorThemeId.teal -> teal
        ColorThemeId.indigo -> indigo
        ColorThemeId.amber -> amber
        ColorThemeId.cyan -> cyan
        ColorThemeId.slate -> slate
        ColorThemeId.aster_blue -> aster_blue
        ColorThemeId.lime -> lime
        ColorThemeId.fuchsia -> fuchsia
        ColorThemeId.emerald -> emerald
        ColorThemeId.pink -> pink
        ColorThemeId.black -> black
    }

    fun is_dark_only(id: ColorThemeId): Boolean =
        id != ColorThemeId.default && id != ColorThemeId.custom && id != ColorThemeId.dynamic

    fun semantic_colors_for(is_dark: Boolean, palette: ColorThemePalette?): AsterSemanticColors {
        val base = if (is_dark) dark_semantic_colors else light_semantic_colors
        if (palette == null) return base

        return base.copy(
            bg_primary = palette.bg_primary,
            bg_secondary = palette.bg_secondary,
            bg_tertiary = palette.bg_tertiary,
            bg_hover = palette.bg_hover,
            bg_selected = palette.bg_selected,
            bg_card = palette.bg_card,
            border_primary = palette.border_primary,
            border_secondary = palette.border_secondary,
            border_thread_divider = palette.border_primary,
            text_primary = palette.text_primary,
            text_secondary = palette.text_secondary,
            text_tertiary = palette.text_tertiary,
            text_muted = palette.text_muted,
            accent_blue = palette.accent_color,
            accent_blue_hover = palette.accent_color_hover,
            avatar_bg = palette.avatar_bg,
            avatar_text = palette.avatar_text,
            indicator_bg = palette.indicator_bg,
            indicator_border = palette.border_secondary,
            sidebar_bg = palette.sidebar_bg,
            sidebar_hover = palette.sidebar_hover,
            modal_bg = palette.modal_bg,
            dropdown_bg = palette.dropdown_bg,
            dropdown_hover = palette.dropdown_hover,
            input_bg = palette.input_bg,
            input_border = palette.input_border,
            thread_card_bg = palette.thread_card_bg,
            thread_card_bg_hover = palette.thread_card_bg_hover,
            thread_card_border = palette.thread_card_border,
            thread_header_bg = palette.thread_header_bg,
            thread_content_bg = palette.thread_content_bg,
            star = palette.accent_color,
            on_accent = palette.on_accent,
            secondary_control_bg = palette.secondary_control_bg,
            secondary_control_border = palette.secondary_control_border,
        )
    }
}
