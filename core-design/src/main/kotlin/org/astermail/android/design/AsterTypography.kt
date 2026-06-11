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

import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GoogleFontFontCompat
import androidx.compose.ui.unit.sp

private val google_fonts_provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val inter_google_font = GoogleFont("Roboto Flex")

val inter_family = FontFamily(
    GoogleFontFontCompat(inter_google_font, google_fonts_provider, FontWeight.Normal),
    GoogleFontFontCompat(inter_google_font, google_fonts_provider, FontWeight.Medium),
    GoogleFontFontCompat(inter_google_font, google_fonts_provider, FontWeight.SemiBold),
    GoogleFontFontCompat(inter_google_font, google_fonts_provider, FontWeight.Bold),
    GoogleFontFontCompat(inter_google_font, google_fonts_provider, FontWeight.Normal, FontStyle.Italic),
)

val local_dyslexia_font = compositionLocalOf<FontFamily?> { null }

val aster_typography = Typography(
    displayLarge = TextStyle(fontFamily = inter_family, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = inter_family, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    displaySmall = TextStyle(fontFamily = inter_family, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineLarge = TextStyle(fontFamily = inter_family, fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontFamily = inter_family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontFamily = inter_family, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = inter_family, fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = inter_family, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = inter_family, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = inter_family, fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = inter_family, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = inter_family, fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = inter_family, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = inter_family, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = inter_family, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp, lineHeight = 16.sp),
)

fun build_typography(font: FontFamily, extra_spacing: Boolean): Typography {
    val s = if (extra_spacing) 0.5f else 0f
    val le = if (extra_spacing) 4f else 0f
    return Typography(
        displayLarge = TextStyle(fontFamily = font, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5f + s).sp, lineHeight = (40f + le).sp),
        displayMedium = TextStyle(fontFamily = font, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3f + s).sp, lineHeight = (34f + le).sp),
        displaySmall = TextStyle(fontFamily = font, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.sp, lineHeight = (30f + le).sp),
        headlineLarge = TextStyle(fontFamily = font, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.sp, lineHeight = (32f + le).sp),
        headlineMedium = TextStyle(fontFamily = font, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.sp, lineHeight = (28f + le).sp),
        headlineSmall = TextStyle(fontFamily = font, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.sp, lineHeight = (24f + le).sp),
        titleLarge = TextStyle(fontFamily = font, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.sp, lineHeight = (28f + le).sp),
        titleMedium = TextStyle(fontFamily = font, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, letterSpacing = s.sp, lineHeight = (22f + le).sp),
        titleSmall = TextStyle(fontFamily = font, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = s.sp, lineHeight = (20f + le).sp),
        bodyLarge = TextStyle(fontFamily = font, fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = s.sp, lineHeight = (22f + le).sp),
        bodyMedium = TextStyle(fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = s.sp, lineHeight = (20f + le).sp),
        bodySmall = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = s.sp, lineHeight = (18f + le).sp),
        labelLarge = TextStyle(fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = s.sp, lineHeight = (20f + le).sp),
        labelMedium = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = s.sp, lineHeight = (18f + le).sp),
        labelSmall = TextStyle(fontFamily = font, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = s.sp, lineHeight = (16f + le).sp),
    )
}
