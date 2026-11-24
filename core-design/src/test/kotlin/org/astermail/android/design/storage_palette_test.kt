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
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min

class storage_palette_test {

    private data class palette_metrics(val normal: Double, val cvd: Double, val contrast: Double)

    private fun hex(color: Color): String =
        "#" + Integer.toHexString(color.toArgb()).padStart(8, '0').takeLast(6)

    private fun metrics(palette: List<String>, surface: String): palette_metrics {
        var normal = Double.MAX_VALUE
        var cvd = Double.MAX_VALUE
        var contrast = Double.MAX_VALUE
        for (i in palette.indices) {
            contrast = min(contrast, storage_contrast(palette[i], surface))
            for (j in i + 1 until palette.size) {
                normal = min(normal, storage_color_delta(palette[i], palette[j]))
                cvd = min(cvd, storage_cvd_delta(palette[i], palette[j]))
            }
        }
        return palette_metrics(normal, cvd, contrast)
    }

    private fun themes(): List<Pair<String, AsterSemanticColors>> {
        val named = ColorThemeId.entries.mapNotNull { id ->
            val palette = AsterColorThemes.palette_for(id) ?: return@mapNotNull null
            id.name to AsterColorThemes.semantic_colors_for(true, palette)
        }
        return listOf(
            "default_light" to AsterColorThemes.semantic_colors_for(false, null),
            "default_dark" to AsterColorThemes.semantic_colors_for(true, null),
        ) + named
    }

    @Test
    fun every_theme_gets_six_distinct_readable_colors() {
        val failures = mutableListOf<String>()
        for ((name, base) in themes()) {
            for (high_contrast in listOf(false, true)) {
                val colors = if (high_contrast) apply_high_contrast(base) else base
                val label = if (high_contrast) "${name}_high_contrast" else name
                val palette = storage_palette_for(colors, high_contrast).slots.map(::hex)
                val surface = hex(colors.bg_primary)
                val result = metrics(palette, surface)
                val minimum = if (high_contrast) storage_min_segment_contrast_high else storage_min_segment_contrast
                if (palette.size != storage_palette_size || palette.toSet().size != storage_palette_size) {
                    failures += "$label size ${palette.size}"
                }
                if (result.contrast < minimum) failures += "$label contrast ${result.contrast}"
                if (result.normal < storage_min_normal_delta) failures += "$label delta ${result.normal}"
                if (result.cvd < storage_min_cvd_delta * 0.95) failures += "$label cvd ${result.cvd}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun first_color_is_the_theme_accent_when_readable() {
        for ((name, colors) in themes()) {
            val accent = hex(colors.accent_blue)
            val surface = hex(colors.bg_primary)
            val first = hex(storage_palette_for(colors, false).slots[0])
            if (storage_contrast(accent, surface) >= storage_min_segment_contrast) {
                assertEquals(name, accent, first)
            } else {
                assertTrue(name, storage_contrast(first, surface) >= storage_min_segment_contrast)
            }
        }
    }

    @Test
    fun each_named_theme_gets_its_own_palette() {
        val named = themes().filter { it.first != "default_light" && it.first != "default_dark" }
        val keys = named.map { (_, colors) -> storage_palette_for(colors, false).slots.joinToString { hex(it) } }
        assertEquals(named.size, keys.toSet().size)
    }

    @Test
    fun matches_the_web_palette_for_shared_inputs() {
        assertEquals(
            listOf("#3b82f6", "#cec0fe", "#11b0d4", "#ffc763", "#f2798f", "#52db9c"),
            build_storage_palette("#3b82f6", "#121212", is_dark = true, high_contrast = false),
        )
    }

    @Test
    fun custom_accents_stay_distinct_across_the_hue_wheel() {
        val failures = mutableListOf<String>()
        for (is_dark in listOf(true, false)) {
            val surface = if (is_dark) "#121212" else "#ffffff"
            val samples = if (is_dark) {
                listOf(0.6 to 0.15, 0.75 to 0.18, 0.9 to 0.1, 0.5 to 0.02)
            } else {
                listOf(0.5 to 0.15, 0.6 to 0.2, 0.85 to 0.15, 0.3 to 0.1)
            }
            for (hue in 0 until 360 step 15) {
                for ((lightness, chroma) in samples) {
                    val accent = storage_oklch_to_hex(lightness, chroma, hue.toDouble())
                    val result = metrics(build_storage_palette(accent, surface, is_dark, false), surface)
                    if (result.contrast < storage_min_segment_contrast || result.normal < 0.09 || result.cvd < 0.035) {
                        failures += "$accent dark=$is_dark $result"
                    }
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun custom_theme_seeds_stay_distinct() {
        val failures = mutableListOf<String>()
        for (seed in listOf("#3b82f6", "#ffff00", "#00ff00", "#ff0000", "#7c3aed", "#000000", "#ffffff", "#808080")) {
            for (dark in listOf(true, false)) {
                val palette = MaterialThemeGenerator.to_palette(MaterialThemeGenerator.generate_material_theme(seed, dark))
                val colors = AsterColorThemes.semantic_colors_for(dark, palette)
                val slots = storage_palette_for(colors, false).slots.map(::hex)
                val result = metrics(slots, hex(colors.bg_primary))
                if (result.contrast < storage_min_segment_contrast || result.normal < 0.09 || result.cvd < 0.035) {
                    failures += "$seed dark=$dark $result"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun invalid_input_falls_back_to_defaults() {
        val palette = build_storage_palette("blue", "", is_dark = true, high_contrast = false)
        assertEquals("#3b82f6", palette[0])
        assertEquals(storage_palette_size, palette.size)
        assertTrue(abs(storage_hex_to_oklch(palette[0])[2] - storage_hex_to_oklch("#3b82f6")[2]) < 0.5)
    }
}
