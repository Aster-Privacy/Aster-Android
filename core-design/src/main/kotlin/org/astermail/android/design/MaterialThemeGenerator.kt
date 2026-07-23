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

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object MaterialThemeGenerator {

    val CUSTOM_THEME_ROLE_KEYS = listOf(
        "accent_color",
        "accent_color_hover",
        "bg_primary",
        "bg_secondary",
        "text_primary",
        "text_secondary",
        "border_primary",
    )

    private val HEX_COLOR_PATTERN = Regex("^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$")

    fun is_valid_hex_color(value: String): Boolean = HEX_COLOR_PATTERN.matches(value)

    private fun srgb_to_linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)

    private fun linear_to_srgb(channel: Double): Double =
        if (channel <= 0.0031308) channel * 12.92 else 1.055 * channel.pow(1.0 / 2.4) - 0.055

    private fun hex_to_rgb(hex: String): Triple<Int, Int, Int> {
        val normalized = hex.removePrefix("#")
        val expanded = if (normalized.length == 3) normalized.map { "$it$it" }.joinToString("") else normalized
        val int_value = expanded.toLong(16)
        return Triple(
            ((int_value shr 16) and 0xFF).toInt(),
            ((int_value shr 8) and 0xFF).toInt(),
            (int_value and 0xFF).toInt(),
        )
    }

    private fun rgb_to_hex(r: Double, g: Double, b: Double): String {
        fun clamp(v: Double) = max(0.0, min(255.0, v.roundToInt().toDouble())).toInt()
        return "#" + listOf(r, g, b).joinToString("") { clamp(it).toString(16).padStart(2, '0') }
    }

    private data class Lab(val L: Double, val a: Double, val b: Double)
    private data class Lch(val L: Double, val C: Double, val H: Double)
    private data class Rgb(val r: Double, val g: Double, val b: Double)

    private fun linear_rgb_to_oklab(r: Double, g: Double, b: Double): Lab {
        val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
        val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
        val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
        val l_ = cbrt(l)
        val m_ = cbrt(m)
        val s_ = cbrt(s)
        return Lab(
            L = 0.2104542553 * l_ + 0.793617785 * m_ - 0.0040720468 * s_,
            a = 1.9779984951 * l_ - 2.428592205 * m_ + 0.4505937099 * s_,
            b = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.808675766 * s_,
        )
    }

    private fun oklab_to_linear_rgb(L: Double, a: Double, b: Double): Rgb {
        val l_ = L + 0.3963377774 * a + 0.2158037573 * b
        val m_ = L - 0.1055613458 * a - 0.0638541728 * b
        val s_ = L - 0.0894841775 * a - 1.291485548 * b
        val l = l_ * l_ * l_
        val m = m_ * m_ * m_
        val s = s_ * s_ * s_
        return Rgb(
            r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            b = -0.0041960863 * l - 0.7034186147 * m + 1.707614701 * s,
        )
    }

    private fun oklab_to_oklch(L: Double, a: Double, b: Double): Lch {
        val C = sqrt(a * a + b * b)
        var H = atan2(b, a) * 180 / PI
        if (H < 0) H += 360
        return Lch(L, C, H)
    }

    private fun oklch_to_oklab(L: Double, C: Double, H: Double): Lab {
        val hue_radians = H * PI / 180
        return Lab(L, C * cos(hue_radians), C * sin(hue_radians))
    }

    private data class SeedHueChroma(val hue: Double, val chroma: Double)

    private fun get_seed_hue_chroma(hex: String): SeedHueChroma {
        val (r, g, b) = hex_to_rgb(hex)
        val linear_r = srgb_to_linear(r / 255.0)
        val linear_g = srgb_to_linear(g / 255.0)
        val linear_b = srgb_to_linear(b / 255.0)
        val lab = linear_rgb_to_oklab(linear_r, linear_g, linear_b)
        val lch = oklab_to_oklch(lab.L, lab.a, lab.b)
        return SeedHueChroma(hue = lch.H, chroma = lch.C)
    }

    private fun tone_to_hex(hue: Double, chroma: Double, tone_percent: Double): String {
        val target_l = max(0.0, min(100.0, tone_percent)) / 100.0
        var working_chroma = chroma

        repeat(24) {
            val lab = oklch_to_oklab(target_l, working_chroma, hue)
            val rgb = oklab_to_linear_rgb(target_l, lab.a, lab.b)
            val sr = linear_to_srgb(rgb.r)
            val sg = linear_to_srgb(rgb.g)
            val sb = linear_to_srgb(rgb.b)
            val in_gamut = sr in -0.001..1.001 && sg in -0.001..1.001 && sb in -0.001..1.001

            if (in_gamut) {
                return rgb_to_hex(sr * 255, sg * 255, sb * 255)
            }

            working_chroma *= 0.9
        }

        val rgb = oklab_to_linear_rgb(target_l, 0.0, 0.0)
        return rgb_to_hex(
            linear_to_srgb(rgb.r) * 255,
            linear_to_srgb(rgb.g) * 255,
            linear_to_srgb(rgb.b) * 255,
        )
    }

    fun mix_hex_srgb(hex: String, other: String, ratio: Double): String {
        val (r1, g1, b1) = hex_to_rgb(hex)
        val (r2, g2, b2) = hex_to_rgb(other)
        return rgb_to_hex(
            r1 * ratio + r2 * (1 - ratio),
            g1 * ratio + g2 * (1 - ratio),
            b1 * ratio + b2 * (1 - ratio),
        )
    }

    data class MaterialThemeVars(
        val bg_primary: String,
        val bg_secondary: String,
        val bg_tertiary: String,
        val bg_hover: String,
        val bg_selected: String,
        val bg_card: String,
        val border_primary: String,
        val border_secondary: String,
        val border_thread_divider: String,
        val text_primary: String,
        val text_secondary: String,
        val text_tertiary: String,
        val text_muted: String,
        val accent_color: String,
        val accent_color_hover: String,
        val accent_blue: String,
        val accent_blue_hover: String,
        val avatar_bg: String,
        val avatar_text: String,
        val indicator_bg: String,
        val sidebar_bg: String,
        val sidebar_hover: String,
    )

    private val DARK_BASE_VARS = MaterialThemeVars(
        bg_primary = "#121212", bg_secondary = "#0a0a0a", bg_tertiary = "#121212", bg_hover = "#1a1a1a",
        bg_selected = "#142744", bg_card = "#121212",
        border_primary = "#333333", border_secondary = "#2a2a2a", border_thread_divider = "#333333",
        text_primary = "#f5f5f5", text_secondary = "#d4d4d4", text_tertiary = "#a1a1aa", text_muted = "#8a8a8a",
        accent_color = "#3b82f6", accent_color_hover = "#60a5fa",
        accent_blue = "#3b82f6", accent_blue_hover = "#60a5fa",
        avatar_bg = "#2a2a2a", avatar_text = "#9ca3af", indicator_bg = "#121212",
        sidebar_bg = "#0a0a0a", sidebar_hover = "#0f0f0f",
    )

    private val LIGHT_BASE_VARS = MaterialThemeVars(
        bg_primary = "#ffffff", bg_secondary = "#f5f5f5", bg_tertiary = "#f3f4f6", bg_hover = "#ececec",
        bg_selected = "#eff6ff", bg_card = "#ffffff",
        border_primary = "#e8e8e8", border_secondary = "#e5e7eb", border_thread_divider = "#e5e5e5",
        text_primary = "#111827", text_secondary = "#374151", text_tertiary = "#4b5563", text_muted = "#5f6470",
        accent_color = "#3b82f6", accent_color_hover = "#2563eb",
        accent_blue = "#3b82f6", accent_blue_hover = "#2563eb",
        avatar_bg = "#e5e7eb", avatar_text = "#6b7280", indicator_bg = "#ffffff",
        sidebar_bg = "#f5f5f5", sidebar_hover = "#e0e0e0",
    )

    fun generate_material_theme(seed_hex: String, is_dark: Boolean): MaterialThemeVars {
        val (hue, seed_chroma) = get_seed_hue_chroma(seed_hex)
        val accent_chroma = min(seed_chroma, 0.19)
        val base = if (is_dark) DARK_BASE_VARS else LIGHT_BASE_VARS
        val accent_color = tone_to_hex(hue, accent_chroma, if (is_dark) 64.0 else 40.0)
        val accent_color_hover = tone_to_hex(hue, accent_chroma, if (is_dark) 73.0 else 34.0)

        return base.copy(
            accent_color = accent_color,
            accent_color_hover = accent_color_hover,
            accent_blue = accent_color,
            accent_blue_hover = accent_color_hover,
        )
    }

    private fun apply_override(current: String, overrides: Map<String, String>, key: String): String {
        val value = overrides[key]
        return if (value != null && is_valid_hex_color(value)) value else current
    }

    fun compute_custom_theme_vars(
        seed_hex: String,
        is_dark: Boolean,
        overrides: Map<String, String>,
    ): MaterialThemeVars {
        val base = generate_material_theme(seed_hex, is_dark)
        if (overrides.isEmpty()) return base

        return base.copy(
            accent_color = apply_override(base.accent_color, overrides, "accent_color"),
            accent_color_hover = apply_override(base.accent_color_hover, overrides, "accent_color_hover"),
            bg_primary = apply_override(base.bg_primary, overrides, "bg_primary"),
            bg_secondary = apply_override(base.bg_secondary, overrides, "bg_secondary"),
            text_primary = apply_override(base.text_primary, overrides, "text_primary"),
            text_secondary = apply_override(base.text_secondary, overrides, "text_secondary"),
            border_primary = apply_override(base.border_primary, overrides, "border_primary"),
        )
    }

    fun to_palette(vars: MaterialThemeVars): ColorThemePalette {
        fun c(hex: String) = androidx.compose.ui.graphics.Color(("FF" + hex.removePrefix("#")).toLong(16))

        return ColorThemePalette(
            bg_primary = c(vars.bg_primary),
            bg_secondary = c(vars.bg_secondary),
            bg_tertiary = c(vars.bg_tertiary),
            bg_hover = c(vars.bg_hover),
            bg_selected = c(vars.bg_selected),
            bg_card = c(vars.bg_card),
            border_primary = c(vars.border_primary),
            border_secondary = c(vars.border_secondary),
            text_primary = c(vars.text_primary),
            text_secondary = c(vars.text_secondary),
            text_tertiary = c(vars.text_tertiary),
            text_muted = c(vars.text_muted),
            accent_color = c(vars.accent_color),
            accent_color_hover = c(vars.accent_color_hover),
            avatar_bg = c(vars.avatar_bg),
            avatar_text = c(vars.avatar_text),
            indicator_bg = c(vars.indicator_bg),
            sidebar_bg = c(vars.sidebar_bg),
            sidebar_hover = c(vars.sidebar_hover),
            modal_bg = c(vars.bg_primary),
            dropdown_bg = c(vars.bg_primary),
            dropdown_hover = c(vars.bg_hover),
            input_bg = c(vars.bg_tertiary),
            input_border = c(vars.border_primary),
            thread_card_bg = c(vars.bg_tertiary),
            thread_card_bg_hover = c(vars.bg_hover),
            thread_card_border = c(vars.border_primary),
            thread_header_bg = c(vars.bg_tertiary),
            thread_content_bg = c(vars.bg_primary),
        )
    }
}
