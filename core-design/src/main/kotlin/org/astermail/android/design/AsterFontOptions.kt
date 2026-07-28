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

@file:OptIn(ExperimentalTextApi::class)

package org.astermail.android.design

import android.content.Context
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.isAvailableOnDevice

data class FontOption(val id: String, val label: String, val google_font_name: String?)

const val DEFAULT_FONT_ID = "default"

internal val google_font_provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val FONT_OPTIONS = listOf(
    FontOption("default", "Aster Default", null),
    FontOption("system", "System UI", null),
    FontOption("inter", "Inter", "Inter"),
    FontOption("roboto", "Roboto", "Roboto"),
    FontOption("nunito", "Nunito", "Nunito"),
    FontOption("merriweather", "Merriweather", "Merriweather"),
    FontOption("lora", "Lora", "Lora"),
    FontOption("jetbrains_mono", "JetBrains Mono", "JetBrains Mono"),
    FontOption("poppins", "Poppins", "Poppins"),
    FontOption("montserrat", "Montserrat", "Montserrat"),
    FontOption("work_sans", "Work Sans", "Work Sans"),
    FontOption("ibm_plex_sans", "IBM Plex Sans", "IBM Plex Sans"),
    FontOption("ibm_plex_mono", "IBM Plex Mono", "IBM Plex Mono"),
    FontOption("space_mono", "Space Mono", "Space Mono"),
    FontOption("playfair_display", "Playfair Display", "Playfair Display"),
    FontOption("libre_baskerville", "Libre Baskerville", "Libre Baskerville"),
    FontOption("pt_serif", "PT Serif", "PT Serif"),
    FontOption("raleway", "Raleway", "Raleway"),
)

private val FONT_OPTIONS_BY_ID = FONT_OPTIONS.associateBy { it.id }

fun is_valid_font_id(id: String): Boolean = FONT_OPTIONS_BY_ID.containsKey(id)

@Volatile
private var downloadable_fonts_supported: Boolean? = null

fun downloadable_fonts_available(context: Context): Boolean {
    downloadable_fonts_supported?.let { return it }
    val supported = runCatching {
        google_font_provider.isAvailableOnDevice(context.applicationContext)
    }.getOrDefault(false)
    downloadable_fonts_supported = supported
    return supported
}

fun font_family_for(id: String?, downloadable_allowed: Boolean = true): FontFamily? {
    val option = FONT_OPTIONS_BY_ID[id ?: DEFAULT_FONT_ID] ?: return null
    return when {
        option.id == "default" -> null
        option.id == "system" -> FontFamily.Default
        option.google_font_name != null -> {
            if (!downloadable_allowed) {
                FontFamily.Default
            } else {
                FontFamily(Font(GoogleFont(option.google_font_name), google_font_provider))
            }
        }
        else -> null
    }
}
