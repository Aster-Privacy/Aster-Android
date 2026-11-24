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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val local_high_contrast = staticCompositionLocalOf { false }

data class storage_palette(
    val blue: Color,
    val violet: Color,
    val teal: Color,
    val amber: Color,
    val rose: Color,
    val fuchsia: Color,
    val track: Color,
)

val storage_palette_light = storage_palette(
    blue = Color(0xFF3B82F6),
    violet = Color(0xFF8B5CF6),
    teal = Color(0xFF0D9488),
    amber = Color(0xFFB86F06),
    rose = Color(0xFFE11D48),
    fuchsia = Color(0xFFC026D3),
    track = Color(0x1464748B),
)

val storage_palette_dark = storage_palette(
    blue = Color(0xFF4F8FF7),
    violet = Color(0xFFA78BFA),
    teal = Color(0xFF2DD4BF),
    amber = Color(0xFFFBBF24),
    rose = Color(0xFFFB7185),
    fuchsia = Color(0xFFE879F9),
    track = Color(0x2994A3B8),
)

val storage_palette_light_high_contrast = storage_palette(
    blue = Color(0xFF2563EB),
    violet = Color(0xFF7C3AED),
    teal = Color(0xFF0F766E),
    amber = Color(0xFFB45309),
    rose = Color(0xFFBE123C),
    fuchsia = Color(0xFFA21CAF),
    track = Color(0x38475569),
)

val storage_palette_dark_high_contrast = storage_palette(
    blue = Color(0xFF60A5FA),
    violet = Color(0xFFA78BFA),
    teal = Color(0xFF2DD4BF),
    amber = Color(0xFFFBBF24),
    rose = Color(0xFFFB7185),
    fuchsia = Color(0xFFE879F9),
    track = Color(0x38CBD5E1),
)

fun storage_palette_for(is_dark: Boolean, high_contrast: Boolean): storage_palette = when {
    is_dark && high_contrast -> storage_palette_dark_high_contrast
    is_dark -> storage_palette_dark
    high_contrast -> storage_palette_light_high_contrast
    else -> storage_palette_light
}

@Composable
fun current_storage_palette(): storage_palette =
    storage_palette_for(AsterMaterial.colors.is_dark, local_high_contrast.current)
