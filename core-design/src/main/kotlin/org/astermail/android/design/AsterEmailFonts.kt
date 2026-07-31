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

const val EMAIL_FONT_MATCH_APP_ID = "match_app"

data class EmailFontFace(val slot: String, @FontRes val resource: Int, val weight: String)

data class EmailWebFont(
    val id: String,
    val family: String,
    val fallback: String,
    val faces: List<EmailFontFace>,
)

private const val FALLBACK_SANS = "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif"

private const val FALLBACK_SERIF = "Georgia,'Times New Roman',serif"

private const val FALLBACK_MONO = "'Roboto Mono',Menlo,Consolas,monospace"

private fun variable_font(
    id: String,
    family: String,
    @FontRes resource: Int,
    fallback: String = FALLBACK_SANS,
) = EmailWebFont(
    id = id,
    family = family,
    fallback = fallback,
    faces = listOf(EmailFontFace("regular", resource, "100 900")),
)

private fun static_font(
    id: String,
    family: String,
    @FontRes regular: Int,
    @FontRes bold: Int,
    fallback: String = FALLBACK_SANS,
) = EmailWebFont(
    id = id,
    family = family,
    fallback = fallback,
    faces = listOf(
        EmailFontFace("regular", regular, "400"),
        EmailFontFace("bold", bold, "600 900"),
    ),
)

private val email_web_fonts: Map<String, EmailWebFont> = listOf(
    variable_font("inter", "AsterInter", R.font.font_inter),
    variable_font("roboto", "AsterRoboto", R.font.font_roboto),
    variable_font("nunito", "AsterNunito", R.font.font_nunito),
    variable_font("merriweather", "AsterMerriweather", R.font.font_merriweather, FALLBACK_SERIF),
    variable_font("lora", "AsterLora", R.font.font_lora, FALLBACK_SERIF),
    variable_font("jetbrains_mono", "AsterJetBrainsMono", R.font.font_jetbrains_mono, FALLBACK_MONO),
    variable_font("montserrat", "AsterMontserrat", R.font.font_montserrat),
    variable_font("work_sans", "AsterWorkSans", R.font.font_work_sans),
    variable_font("ibm_plex_sans", "AsterIBMPlexSans", R.font.font_ibm_plex_sans),
    variable_font("playfair_display", "AsterPlayfairDisplay", R.font.font_playfair_display, FALLBACK_SERIF),
    variable_font("libre_baskerville", "AsterLibreBaskerville", R.font.font_libre_baskerville, FALLBACK_SERIF),
    variable_font("raleway", "AsterRaleway", R.font.font_raleway),
    static_font("poppins", "AsterPoppins", R.font.font_poppins_regular, R.font.font_poppins_bold),
    static_font(
        "ibm_plex_mono",
        "AsterIBMPlexMono",
        R.font.font_ibm_plex_mono_regular,
        R.font.font_ibm_plex_mono_bold,
        FALLBACK_MONO,
    ),
    static_font("space_mono", "AsterSpaceMono", R.font.font_space_mono_regular, R.font.font_space_mono_bold, FALLBACK_MONO),
    static_font("pt_serif", "AsterPTSerif", R.font.font_pt_serif_regular, R.font.font_pt_serif_bold, FALLBACK_SERIF),
).associateBy { it.id }

fun resolve_email_font_id(email_font_id: String?, app_font_id: String?): String {
    val id = email_font_id?.takeIf { it.isNotBlank() } ?: EMAIL_FONT_MATCH_APP_ID
    if (id != EMAIL_FONT_MATCH_APP_ID) return id
    return app_font_id?.takeIf { it.isNotBlank() } ?: DEFAULT_FONT_ID
}

fun email_web_font_for(id: String?): EmailWebFont? {
    if (id == null) return null
    return email_web_fonts[id]
}

fun email_generic_font_stack(id: String?): String? = when (id) {
    "system_mono" -> FALLBACK_MONO
    else -> null
}

fun email_font_resource_for(id: String?, slot: String?): Int? {
    val font = email_web_fonts[id ?: return null] ?: return null
    return font.faces.firstOrNull { it.slot == slot }?.resource
}
