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
)

object AsterColorThemes {
    val purple = palette(
        bg_primary = "#180a31", bg_secondary = "#100721", bg_tertiary = "#1d0d34", bg_hover = "#210f41",
        bg_selected = "#2b1256", avatar_bg = "#33175a", avatar_text = "#cbb0e8",
        border_primary = "#371d5d", border_secondary = "#2b1644",
        text_primary = "#f4effa", text_secondary = "#dcc8ef", text_tertiary = "#b18fde", text_muted = "#8e73ae",
        accent_color = "#ad5ef8", accent_color_hover = "#c590fa",
        sidebar_bg = "#100721", sidebar_hover = "#1a0a31",
        input_bg = "#1d0d34", input_border = "#371d5d",
        thread_card_bg = "#1d0d34", thread_card_bg_hover = "#210f41", thread_card_border = "#371d5d",
        thread_header_bg = "#1d0d34", thread_content_bg = "#180a31",
    )

    val green = palette(
        bg_primary = "#08281c", bg_secondary = "#061b13", bg_tertiary = "#0b3323", bg_hover = "#0d3e28",
        bg_selected = "#0f4830", avatar_bg = "#115236", avatar_text = "#aee8c8",
        border_primary = "#1c593b", border_secondary = "#15432e",
        text_primary = "#effaf4", text_secondary = "#caefdb", text_tertiary = "#8fdeb4", text_muted = "#73ae90",
        accent_color = "#1dd560", accent_color_hover = "#4de585",
        sidebar_bg = "#061b13", sidebar_hover = "#092e1c",
        input_bg = "#0b3323", input_border = "#1c593b",
        thread_card_bg = "#0b3323", thread_card_bg_hover = "#0d3e28", thread_card_border = "#1c593b",
        thread_header_bg = "#0b3323", thread_content_bg = "#08281c",
    )

    val rose = palette(
        bg_primary = "#2e0914", bg_secondary = "#20070d", bg_tertiary = "#380b1e", bg_hover = "#430e23",
        bg_selected = "#53112b", avatar_bg = "#571229", avatar_text = "#edbfcc",
        border_primary = "#5a1d30", border_secondary = "#441525",
        text_primary = "#faf0f3", text_secondary = "#f0cbd7", text_tertiary = "#de8fa9", text_muted = "#ae7386",
        accent_color = "#f74664", accent_color_hover = "#fa7d8f",
        sidebar_bg = "#20070d", sidebar_hover = "#300a17",
        input_bg = "#380b1e", input_border = "#5a1d30",
        thread_card_bg = "#380b1e", thread_card_bg_hover = "#430e23", thread_card_border = "#5a1d30",
        thread_header_bg = "#380b1e", thread_content_bg = "#2e0914",
    )

    val orange = palette(
        bg_primary = "#2b1a09", bg_secondary = "#1e1206", bg_tertiary = "#35200b", bg_hover = "#40270d",
        bg_selected = "#4f3010", avatar_bg = "#513111", avatar_text = "#ead0b5",
        border_primary = "#53381a", border_secondary = "#3e2a13",
        text_primary = "#faf3ed", text_secondary = "#eedcc6", text_tertiary = "#d8ac78", text_muted = "#b98c54",
        accent_color = "#f67a23", accent_color_hover = "#f7984a",
        sidebar_bg = "#1e1206", sidebar_hover = "#2d1b09",
        input_bg = "#35200b", input_border = "#53381a",
        thread_card_bg = "#35200b", thread_card_bg_hover = "#40270d", thread_card_border = "#53381a",
        thread_header_bg = "#35200b", thread_content_bg = "#2b1a09",
    )

    val teal = palette(
        bg_primary = "#082727", bg_secondary = "#061b1b", bg_tertiary = "#0b3333", bg_hover = "#0d3d3d",
        bg_selected = "#0f4747", avatar_bg = "#115151", avatar_text = "#a4e5de",
        border_primary = "#1c5858", border_secondary = "#154242",
        text_primary = "#ecf9f8", text_secondary = "#c7efea", text_tertiary = "#8dddd3", text_muted = "#73ada5",
        accent_color = "#0ec894", accent_color_hover = "#2fdcab",
        sidebar_bg = "#061b1b", sidebar_hover = "#092e2e",
        input_bg = "#0b3333", input_border = "#1c5858",
        thread_card_bg = "#0b3333", thread_card_bg_hover = "#0d3d3d", thread_card_border = "#1c5858",
        thread_header_bg = "#0b3333", thread_content_bg = "#082727",
    )

    val indigo = palette(
        bg_primary = "#0c0e3b", bg_secondary = "#0a0a2e", bg_tertiary = "#10124d", bg_hover = "#13175b",
        bg_selected = "#161c6a", avatar_bg = "#161a69", avatar_text = "#d0d1f1",
        border_primary = "#23266d", border_secondary = "#181c4d",
        text_primary = "#f0f2fb", text_secondary = "#d3d5f2", text_tertiary = "#9ca0e2", text_muted = "#6f74b9",
        accent_color = "#676af7", accent_color_hover = "#8994fa",
        sidebar_bg = "#0a0a2e", sidebar_hover = "#0e1042",
        input_bg = "#10124d", input_border = "#23266d",
        thread_card_bg = "#10124d", thread_card_bg_hover = "#13175b", thread_card_border = "#23266d",
        thread_header_bg = "#10124d", thread_content_bg = "#0c0e3b",
    )

    val amber = palette(
        bg_primary = "#231b07", bg_secondary = "#191305", bg_tertiary = "#31240a", bg_hover = "#3a2a0c",
        bg_selected = "#42310d", avatar_bg = "#3d2e0d", avatar_text = "#e7d8ae",
        border_primary = "#4d3d18", border_secondary = "#352a11",
        text_primary = "#f8f4e8", text_secondary = "#ede1c0", text_tertiary = "#d9bf7d", text_muted = "#b99949",
        accent_color = "#f5c115", accent_color_hover = "#f6dd33",
        sidebar_bg = "#191305", sidebar_hover = "#251b08",
        input_bg = "#31240a", input_border = "#4d3d18",
        thread_card_bg = "#31240a", thread_card_bg_hover = "#3a2a0c", thread_card_border = "#4d3d18",
        thread_header_bg = "#31240a", thread_content_bg = "#231b07",
    )

    val cyan = palette(
        bg_primary = "#081f26", bg_secondary = "#06171b", bg_tertiary = "#0b2a34", bg_hover = "#0d343d",
        bg_selected = "#0f3f4a", avatar_bg = "#0f3c49", avatar_text = "#abdee7",
        border_primary = "#18414b", border_secondary = "#13323b",
        text_primary = "#ecf8f9", text_secondary = "#c8eaef", text_tertiary = "#8ed1de", text_muted = "#5aa6b6",
        accent_color = "#0995db", accent_color_hover = "#24b0f6",
        sidebar_bg = "#06171b", sidebar_hover = "#082229",
        input_bg = "#0b2a34", input_border = "#18414b",
        thread_card_bg = "#0b2a34", thread_card_bg_hover = "#0d343d", thread_card_border = "#18414b",
        thread_header_bg = "#0b2a34", thread_content_bg = "#081f26",
    )

    val slate = palette(
        bg_primary = "#16181c", bg_secondary = "#101114", bg_tertiary = "#202329", bg_hover = "#262a31",
        bg_selected = "#2b2f38", avatar_bg = "#2c2f36", avatar_text = "#cbd5e1",
        border_primary = "#33373f", border_secondary = "#24272c",
        text_primary = "#f1f5f9", text_secondary = "#d6dce3", text_tertiary = "#a8b0bb", text_muted = "#7c8591",
        accent_color = "#64748b", accent_color_hover = "#94a3b8",
        sidebar_bg = "#101114", sidebar_hover = "#191b1f",
        input_bg = "#202329", input_border = "#33373f",
        thread_card_bg = "#202329", thread_card_bg_hover = "#262a31", thread_card_border = "#33373f",
        thread_header_bg = "#202329", thread_content_bg = "#16181c",
    )

    val aster_blue = palette(
        bg_primary = "#0a1a33", bg_secondary = "#071423", bg_tertiary = "#0e2647", bg_hover = "#122e59",
        bg_selected = "#16386e", avatar_bg = "#16386c", avatar_text = "#cbddf0",
        border_primary = "#244470", border_secondary = "#1b3254",
        text_primary = "#eff3fa", text_secondary = "#d7e2f3", text_tertiary = "#a3c0e4", text_muted = "#5b87c7",
        accent_color = "#4488f7", accent_color_hover = "#6babf9",
        sidebar_bg = "#071423", sidebar_hover = "#0d2240",
        input_bg = "#0e2647", input_border = "#244470",
        thread_card_bg = "#0e2647", thread_card_bg_hover = "#122e59", thread_card_border = "#244470",
        thread_header_bg = "#0e2647", thread_content_bg = "#0a1a33",
    )

    val lime = palette(
        bg_primary = "#1a2207", bg_secondary = "#131805", bg_tertiary = "#232f0a", bg_hover = "#2b390c",
        bg_selected = "#2f420d", avatar_bg = "#2d3d0d", avatar_text = "#d1e7ae",
        border_primary = "#3d4d18", border_secondary = "#2a3511",
        text_primary = "#f4f8e8", text_secondary = "#e3edc0", text_tertiary = "#bfd97d", text_muted = "#9eb949",
        accent_color = "#8cdd0f", accent_color_hover = "#a9ee37",
        sidebar_bg = "#131805", sidebar_hover = "#1c2507",
        input_bg = "#232f0a", input_border = "#3d4d18",
        thread_card_bg = "#232f0a", thread_card_bg_hover = "#2b390c", thread_card_border = "#3d4d18",
        thread_header_bg = "#232f0a", thread_content_bg = "#1a2207",
    )

    val fuchsia = palette(
        bg_primary = "#2c092a", bg_secondary = "#200620", bg_tertiary = "#360b30", bg_hover = "#420d3c",
        bg_selected = "#521148", avatar_bg = "#551148", avatar_text = "#ecbce1",
        border_primary = "#571c4e", border_secondary = "#411538",
        text_primary = "#faecf9", text_secondary = "#efc6e8", text_tertiary = "#dc87c8", text_muted = "#b06990",
        accent_color = "#dc19e6", accent_color_hover = "#e850f0",
        sidebar_bg = "#200620", sidebar_hover = "#2c0a32",
        input_bg = "#360b30", input_border = "#571c4e",
        thread_card_bg = "#360b30", thread_card_bg_hover = "#420d3c", thread_card_border = "#571c4e",
        thread_header_bg = "#360b30", thread_content_bg = "#2c092a",
    )

    val emerald = palette(
        bg_primary = "#072213", bg_secondary = "#051711", bg_tertiary = "#0a2f19", bg_hover = "#0c3b21",
        bg_selected = "#0e4728", avatar_bg = "#0e4222", avatar_text = "#b8ebc8",
        border_primary = "#1a5130", border_secondary = "#113718",
        text_primary = "#ecf9f0", text_secondary = "#c3edce", text_tertiary = "#85dca6", text_muted = "#50b878",
        accent_color = "#33e128", accent_color_hover = "#62e753",
        sidebar_bg = "#051711", sidebar_hover = "#082514",
        input_bg = "#0a2f19", input_border = "#1a5130",
        thread_card_bg = "#0a2f19", thread_card_bg_hover = "#0c3b21", thread_card_border = "#1a5130",
        thread_header_bg = "#0a2f19", thread_content_bg = "#072213",
    )

    val pink = palette(
        bg_primary = "#2b091d", bg_secondary = "#1e0615", bg_tertiary = "#380b26", bg_hover = "#430e2f",
        bg_selected = "#511139", avatar_bg = "#511143", avatar_text = "#ebbad4",
        border_primary = "#541a43", border_secondary = "#3e132d",
        text_primary = "#faecf2", text_secondary = "#eec4dd", text_tertiary = "#dd88bc", text_muted = "#b65794",
        accent_color = "#e83ba3", accent_color_hover = "#f26fbb",
        sidebar_bg = "#1e0615", sidebar_hover = "#2b0924",
        input_bg = "#380b26", input_border = "#541a43",
        thread_card_bg = "#380b26", thread_card_bg_hover = "#430e2f", thread_card_border = "#541a43",
        thread_header_bg = "#380b26", thread_content_bg = "#2b091d",
    )

    val black = palette(
        bg_primary = "#0a0a0a", bg_secondary = "#000000", bg_tertiary = "#141414", bg_hover = "#1f1f1f",
        bg_selected = "#262626", avatar_bg = "#262626", avatar_text = "#e5e5e5",
        border_primary = "#2e2e2e", border_secondary = "#1a1a1a",
        text_primary = "#ffffff", text_secondary = "#e5e5e5", text_tertiary = "#a3a3a3", text_muted = "#737373",
        accent_color = "#d4d4d8", accent_color_hover = "#e4e4e7",
        sidebar_bg = "#000000", sidebar_hover = "#0a0a0a",
        input_bg = "#141414", input_border = "#2e2e2e",
        thread_card_bg = "#141414", thread_card_bg_hover = "#1f1f1f", thread_card_border = "#2e2e2e",
        thread_header_bg = "#141414", thread_content_bg = "#0a0a0a",
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
        )
    }
}
