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
    dropdown_bg = c(bg_primary),
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
        bg_primary = "#1b1526", bg_secondary = "#120e1a", bg_tertiary = "#1f1829", bg_hover = "#251d33",
        bg_selected = "#2f2147", avatar_bg = "#362a47", avatar_text = "#cbb8e0",
        border_primary = "#3a2d4d", border_secondary = "#2c2238",
        text_primary = "#f4eefb", text_secondary = "#dccbec", text_tertiary = "#b39ecf", text_muted = "#8f7ea3",
        accent_color = "#af78e3", accent_color_hover = "#c8a2ee",
        sidebar_bg = "#120e1a", sidebar_hover = "#1c1526",
        input_bg = "#1f1829", input_border = "#3a2d4d",
        thread_card_bg = "#1f1829", thread_card_bg_hover = "#251d33", thread_card_border = "#3a2d4d",
        thread_header_bg = "#1f1829", thread_content_bg = "#1b1526",
    )

    val green = palette(
        bg_primary = "#10201a", bg_secondary = "#0a1712", bg_tertiary = "#142a21", bg_hover = "#183327",
        bg_selected = "#16412f", avatar_bg = "#204334", avatar_text = "#b6e0c9",
        border_primary = "#2b4a3b", border_secondary = "#1e3a2d",
        text_primary = "#eefbf4", text_secondary = "#cdecdb", text_tertiary = "#9ecfb5", text_muted = "#7ea390",
        accent_color = "#3dba6b", accent_color_hover = "#6acd8e",
        sidebar_bg = "#0a1712", sidebar_hover = "#12251c",
        input_bg = "#142a21", input_border = "#2b4a3b",
        thread_card_bg = "#142a21", thread_card_bg_hover = "#183327", thread_card_border = "#2b4a3b",
        thread_header_bg = "#142a21", thread_content_bg = "#10201a",
    )

    val rose = palette(
        bg_primary = "#241318", bg_secondary = "#1a0d10", bg_tertiary = "#2c1720", bg_hover = "#351c26",
        bg_selected = "#451f2e", avatar_bg = "#45242f", avatar_text = "#f0bccb",
        border_primary = "#4d2a35", border_secondary = "#3a1f28",
        text_primary = "#fceef2", text_secondary = "#eecdd8", text_tertiary = "#cf9eae", text_muted = "#a37e8a",
        accent_color = "#dd657a", accent_color_hover = "#ea919e",
        sidebar_bg = "#1a0d10", sidebar_hover = "#26141a",
        input_bg = "#2c1720", input_border = "#4d2a35",
        thread_card_bg = "#2c1720", thread_card_bg_hover = "#351c26", thread_card_border = "#4d2a35",
        thread_header_bg = "#2c1720", thread_content_bg = "#241318",
    )

    val orange = palette(
        bg_primary = "#241a10", bg_secondary = "#1a120a", bg_tertiary = "#2c2014", bg_hover = "#352718",
        bg_selected = "#45301a", avatar_bg = "#45311d", avatar_text = "#f3d0ac",
        border_primary = "#4d3820", border_secondary = "#3a2a17",
        text_primary = "#fdf3ea", text_secondary = "#f0dcc4", text_tertiary = "#d1ab7f", text_muted = "#a68a67",
        accent_color = "#dc8143", accent_color_hover = "#e39d64",
        sidebar_bg = "#1a120a", sidebar_hover = "#261b10",
        input_bg = "#2c2014", input_border = "#4d3820",
        thread_card_bg = "#2c2014", thread_card_bg_hover = "#352718", thread_card_border = "#4d3820",
        thread_header_bg = "#2c2014", thread_content_bg = "#241a10",
    )

    val teal = palette(
        bg_primary = "#0f2020", bg_secondary = "#0a1717", bg_tertiary = "#142a2a", bg_hover = "#183232",
        bg_selected = "#164040", avatar_bg = "#204242", avatar_text = "#a9e0da",
        border_primary = "#2b4949", border_secondary = "#1e3939",
        text_primary = "#eafbf9", text_secondary = "#cbebe7", text_tertiary = "#9ccec8", text_muted = "#7ea29d",
        accent_color = "#2ead8a", accent_color_hover = "#50c1a1",
        sidebar_bg = "#0a1717", sidebar_hover = "#122525",
        input_bg = "#142a2a", input_border = "#2b4949",
        thread_card_bg = "#142a2a", thread_card_bg_hover = "#183232", thread_card_border = "#2b4949",
        thread_header_bg = "#142a2a", thread_content_bg = "#0f2020",
    )

    val indigo = palette(
        bg_primary = "#191a2e", bg_secondary = "#131325", bg_tertiary = "#21223c", bg_hover = "#262848",
        bg_selected = "#2a2d56", avatar_bg = "#2d2f52", avatar_text = "#c7c9fa",
        border_primary = "#34365c", border_secondary = "#26283f",
        text_primary = "#eef0fd", text_secondary = "#d3d5f2", text_tertiary = "#a5a8d9", text_muted = "#7d80ab",
        accent_color = "#8385e0", accent_color_hover = "#9ea5ea",
        sidebar_bg = "#131325", sidebar_hover = "#1c1d34",
        input_bg = "#21223c", input_border = "#34365c",
        thread_card_bg = "#21223c", thread_card_bg_hover = "#262848", thread_card_border = "#34365c",
        thread_header_bg = "#21223c", thread_content_bg = "#191a2e",
    )

    val amber = palette(
        bg_primary = "#201a0a", bg_secondary = "#171207", bg_tertiary = "#2b2210", bg_hover = "#332813",
        bg_selected = "#3d2f12", avatar_bg = "#3a2d10", avatar_text = "#f5dfa0",
        border_primary = "#4d3d18", border_secondary = "#362a10",
        text_primary = "#fdf6e3", text_secondary = "#f0e2bd", text_tertiary = "#d4bd82", text_muted = "#a4905e",
        accent_color = "#d7b238", accent_color_hover = "#dfcc4f",
        sidebar_bg = "#171207", sidebar_hover = "#211a0c",
        input_bg = "#2b2210", input_border = "#4d3d18",
        thread_card_bg = "#2b2210", thread_card_bg_hover = "#332813", thread_card_border = "#4d3d18",
        thread_header_bg = "#2b2210", thread_content_bg = "#201a0a",
    )

    val cyan = palette(
        bg_primary = "#0a1e24", bg_secondary = "#07161a", bg_tertiary = "#0f2830", bg_hover = "#123138",
        bg_selected = "#123d47", avatar_bg = "#163842", avatar_text = "#a0e6f2",
        border_primary = "#1e3d45", border_secondary = "#163038",
        text_primary = "#e8fbfd", text_secondary = "#c3eef4", text_tertiary = "#8fd0dd", text_muted = "#6b9ba5",
        accent_color = "#258fc4", accent_color_hover = "#4ba7d4",
        sidebar_bg = "#07161a", sidebar_hover = "#0d1f24",
        input_bg = "#0f2830", input_border = "#1e3d45",
        thread_card_bg = "#0f2830", thread_card_bg_hover = "#123138", thread_card_border = "#1e3d45",
        thread_header_bg = "#0f2830", thread_content_bg = "#0a1e24",
    )

    val slate = palette(
        bg_primary = "#16181c", bg_secondary = "#101114", bg_tertiary = "#202329", bg_hover = "#262a31",
        bg_selected = "#2b2f38", avatar_bg = "#2c2f36", avatar_text = "#cbd5e1",
        border_primary = "#33373f", border_secondary = "#24272c",
        text_primary = "#f1f5f9", text_secondary = "#d6dce3", text_tertiary = "#a8b0bb", text_muted = "#7c8591",
        accent_color = "#707c8e", accent_color_hover = "#a2acb9",
        sidebar_bg = "#101114", sidebar_hover = "#191b1f",
        input_bg = "#202329", input_border = "#33373f",
        thread_card_bg = "#202329", thread_card_bg_hover = "#262a31", thread_card_border = "#33373f",
        thread_header_bg = "#202329", thread_content_bg = "#16181c",
    )

    val aster_blue = palette(
        bg_primary = "#0f1b2e", bg_secondary = "#0a1420", bg_tertiary = "#16273f", bg_hover = "#1b3050",
        bg_selected = "#1e3a66", avatar_bg = "#1f3a63", avatar_text = "#bcdcff",
        border_primary = "#2c4568", border_secondary = "#1f3350",
        text_primary = "#eaf2ff", text_secondary = "#cfe0fb", text_tertiary = "#9fc0e8", text_muted = "#6f8bb3",
        accent_color = "#6291de", accent_color_hover = "#82afe7",
        sidebar_bg = "#0a1420", sidebar_hover = "#13233a",
        input_bg = "#16273f", input_border = "#2c4568",
        thread_card_bg = "#16273f", thread_card_bg_hover = "#1b3050", thread_card_border = "#2c4568",
        thread_header_bg = "#16273f", thread_content_bg = "#0f1b2e",
    )

    val lime = palette(
        bg_primary = "#191f0a", bg_secondary = "#121607", bg_tertiary = "#212910", bg_hover = "#283213",
        bg_selected = "#2e3d12", avatar_bg = "#2c3a10", avatar_text = "#d4f5a0",
        border_primary = "#3d4d18", border_secondary = "#2a3610",
        text_primary = "#f6fde3", text_secondary = "#e5f0bd", text_tertiary = "#bdd482", text_muted = "#93a45e",
        accent_color = "#87bf33", accent_color_hover = "#a4d15a",
        sidebar_bg = "#121607", sidebar_hover = "#1a200c",
        input_bg = "#212910", input_border = "#3d4d18",
        thread_card_bg = "#212910", thread_card_bg_hover = "#283213", thread_card_border = "#3d4d18",
        thread_header_bg = "#212910", thread_content_bg = "#191f0a",
    )

    val fuchsia = palette(
        bg_primary = "#241123", bg_secondary = "#1a0c1a", bg_tertiary = "#2c1529", bg_hover = "#351a32",
        bg_selected = "#451e3f", avatar_bg = "#45213e", avatar_text = "#f5b3e6",
        border_primary = "#4d2647", border_secondary = "#3a1c34",
        text_primary = "#fceafb", text_secondary = "#eec7e8", text_tertiary = "#cf94c1", text_muted = "#a3768f",
        accent_color = "#bf3fc5", accent_color_hover = "#d16fd7",
        sidebar_bg = "#1a0c1a", sidebar_hover = "#261329",
        input_bg = "#2c1529", input_border = "#4d2647",
        thread_card_bg = "#2c1529", thread_card_bg_hover = "#351a32", thread_card_border = "#4d2647",
        thread_header_bg = "#2c1529", thread_content_bg = "#241123",
    )

    val emerald = palette(
        bg_primary = "#0a1f13", bg_secondary = "#071510", bg_tertiary = "#10291a", bg_hover = "#143322",
        bg_selected = "#173e29", avatar_bg = "#163a24", avatar_text = "#aef5c4",
        border_primary = "#1e4d31", border_secondary = "#123618",
        text_primary = "#e8fdef", text_secondary = "#c0f0cd", text_tertiary = "#8dd4a8", text_muted = "#63a47c",
        accent_color = "#52c44b", accent_color_hover = "#79d070",
        sidebar_bg = "#071510", sidebar_hover = "#0d2015",
        input_bg = "#10291a", input_border = "#1e4d31",
        thread_card_bg = "#10291a", thread_card_bg_hover = "#143322", thread_card_border = "#1e4d31",
        thread_header_bg = "#10291a", thread_content_bg = "#0a1f13",
    )

    val pink = palette(
        bg_primary = "#24101c", bg_secondary = "#1a0a14", bg_tertiary = "#2e1524", bg_hover = "#38192c",
        bg_selected = "#451d36", avatar_bg = "#451d3c", avatar_text = "#f5b0d5",
        border_primary = "#4d2140", border_secondary = "#38192c",
        text_primary = "#fde9f2", text_secondary = "#f0c2dd", text_tertiary = "#d491ba", text_muted = "#a4698f",
        accent_color = "#cc5c9f", accent_color_hover = "#dd89ba",
        sidebar_bg = "#1a0a14", sidebar_hover = "#241020",
        input_bg = "#2e1524", input_border = "#4d2140",
        thread_card_bg = "#2e1524", thread_card_bg_hover = "#38192c", thread_card_border = "#4d2140",
        thread_header_bg = "#2e1524", thread_content_bg = "#24101c",
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

    fun is_dark_only(id: ColorThemeId): Boolean = id != ColorThemeId.default && id != ColorThemeId.custom

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
            text_primary = palette.text_primary,
            text_secondary = palette.text_secondary,
            text_tertiary = palette.text_tertiary,
            text_muted = palette.text_muted,
            accent_blue = palette.accent_color,
            accent_blue_hover = palette.accent_color_hover,
            avatar_bg = palette.avatar_bg,
            avatar_text = palette.avatar_text,
            indicator_bg = palette.indicator_bg,
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
        )
    }
}
