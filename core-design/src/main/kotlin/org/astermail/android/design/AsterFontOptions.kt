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

import androidx.annotation.FontRes
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight

data class FontOption(val id: String, val label: String)

const val DEFAULT_FONT_ID = "default"

private val ui_weights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variable_family(@FontRes resource: Int): FontFamily = FontFamily(
    ui_weights.map { weight ->
        Font(
            resId = resource,
            weight = weight,
            style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
        )
    },
)

private fun static_family(
    @FontRes regular: Int,
    @FontRes medium: Int,
    @FontRes semibold: Int,
    @FontRes bold: Int,
): FontFamily = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(medium, FontWeight.Medium),
    Font(semibold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

private fun static_family(@FontRes regular: Int, @FontRes bold: Int): FontFamily = FontFamily(
    Font(regular, FontWeight.Normal),
    Font(bold, FontWeight.Medium),
    Font(bold, FontWeight.SemiBold),
    Font(bold, FontWeight.Bold),
)

val aster_default_family: FontFamily = FontFamily.Default

private val local_families: Map<String, Lazy<FontFamily>> = mapOf(
    "inter" to lazy { variable_family(R.font.font_inter) },
    "roboto" to lazy { variable_family(R.font.font_roboto) },
    "nunito" to lazy { variable_family(R.font.font_nunito) },
    "merriweather" to lazy { variable_family(R.font.font_merriweather) },
    "lora" to lazy { variable_family(R.font.font_lora) },
    "jetbrains_mono" to lazy { variable_family(R.font.font_jetbrains_mono) },
    "montserrat" to lazy { variable_family(R.font.font_montserrat) },
    "work_sans" to lazy { variable_family(R.font.font_work_sans) },
    "ibm_plex_sans" to lazy { variable_family(R.font.font_ibm_plex_sans) },
    "playfair_display" to lazy { variable_family(R.font.font_playfair_display) },
    "libre_baskerville" to lazy { variable_family(R.font.font_libre_baskerville) },
    "raleway" to lazy { variable_family(R.font.font_raleway) },
    "poppins" to lazy {
        static_family(
            R.font.font_poppins_regular,
            R.font.font_poppins_medium,
            R.font.font_poppins_semibold,
            R.font.font_poppins_bold,
        )
    },
    "ibm_plex_mono" to lazy {
        static_family(
            R.font.font_ibm_plex_mono_regular,
            R.font.font_ibm_plex_mono_medium,
            R.font.font_ibm_plex_mono_semibold,
            R.font.font_ibm_plex_mono_bold,
        )
    },
    "space_mono" to lazy { static_family(R.font.font_space_mono_regular, R.font.font_space_mono_bold) },
    "pt_serif" to lazy { static_family(R.font.font_pt_serif_regular, R.font.font_pt_serif_bold) },
)

val FONT_OPTIONS = listOf(
    FontOption("default", "Aster Default"),
    FontOption("system", "System UI"),
    FontOption("inter", "Inter"),
    FontOption("roboto", "Roboto"),
    FontOption("nunito", "Nunito"),
    FontOption("merriweather", "Merriweather"),
    FontOption("lora", "Lora"),
    FontOption("system_mono", "System Mono"),
    FontOption("jetbrains_mono", "JetBrains Mono"),
    FontOption("poppins", "Poppins"),
    FontOption("montserrat", "Montserrat"),
    FontOption("work_sans", "Work Sans"),
    FontOption("ibm_plex_sans", "IBM Plex Sans"),
    FontOption("ibm_plex_mono", "IBM Plex Mono"),
    FontOption("space_mono", "Space Mono"),
    FontOption("playfair_display", "Playfair Display"),
    FontOption("libre_baskerville", "Libre Baskerville"),
    FontOption("pt_serif", "PT Serif"),
    FontOption("raleway", "Raleway"),
)

private val FONT_OPTIONS_BY_ID = FONT_OPTIONS.associateBy { it.id }

fun is_valid_font_id(id: String): Boolean = FONT_OPTIONS_BY_ID.containsKey(id)

fun font_family_for(id: String?): FontFamily? = when (val resolved = id ?: DEFAULT_FONT_ID) {
    "default" -> null
    "system" -> FontFamily.Default
    "system_mono" -> FontFamily.Monospace
    else -> local_families[resolved]?.value
}

fun preview_font_family_for(id: String): FontFamily = font_family_for(id) ?: aster_default_family
