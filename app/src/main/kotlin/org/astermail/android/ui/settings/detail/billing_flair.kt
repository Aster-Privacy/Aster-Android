/*
 * Aster Mail Android
 * Copyright (C) 2026 Aster Privacy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.astermail.android.ui.settings.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import compose.icons.TablerIcons
import compose.icons.tablericons.Flare
import compose.icons.tablericons.Home
import compose.icons.tablericons.Planet
import compose.icons.tablericons.Rocket
import compose.icons.tablericons.Star
import compose.icons.tablericons.Users
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape

internal fun tier_icon(code: String): ImageVector = when (code) {
    "star" -> TablerIcons.Star
    "nova" -> TablerIcons.Flare
    "supernova" -> TablerIcons.Rocket
    "duo" -> TablerIcons.Users
    "family" -> TablerIcons.Home
    else -> TablerIcons.Planet
}

private val star_seeds = listOf(
    0.06f to 0.18f, 0.13f to 0.62f, 0.21f to 0.09f, 0.29f to 0.41f, 0.36f to 0.78f,
    0.44f to 0.22f, 0.52f to 0.55f, 0.58f to 0.12f, 0.66f to 0.70f, 0.73f to 0.31f,
    0.81f to 0.08f, 0.87f to 0.48f, 0.93f to 0.24f, 0.97f to 0.66f, 0.17f to 0.88f,
    0.48f to 0.92f, 0.76f to 0.90f, 0.09f to 0.40f, 0.63f to 0.38f, 0.90f to 0.84f,
)

internal fun Modifier.starfield(
    accent: Color,
    is_dark: Boolean,
    band_fraction: Float = 0.42f,
    edges_only: Boolean = false,
): Modifier = drawBehind {
    val band = size.height * band_fraction
    val glow_center = Offset(size.width * 0.86f, band * 0.05f)
    drawCircle(
        brush = Brush.radialGradient(
            0.0f to accent.copy(alpha = if (is_dark) 0.30f else 0.16f),
            0.55f to accent.copy(alpha = if (is_dark) 0.08f else 0.04f),
            1.0f to Color.Transparent,
            center = glow_center,
            radius = size.width * 0.42f,
        ),
        radius = size.width * 0.42f,
        center = glow_center,
    )
    val star_color = if (is_dark) Color.White else accent
    star_seeds.forEachIndexed { index, (fx, fy) ->
        if (edges_only && fx > 0.22f && fx < 0.78f) return@forEachIndexed
        val radius = if (index % 4 == 0) 1.9.dp.toPx() else 1.1.dp.toPx()
        val alpha = if (is_dark) (if (index % 3 == 0) 0.42f else 0.20f) else (if (index % 3 == 0) 0.24f else 0.12f)
        drawCircle(
            color = star_color.copy(alpha = alpha),
            radius = radius,
            center = Offset(size.width * fx, band * fy),
        )
    }
}

@Composable
internal fun hero_surface(
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    val accent = colors.accent_blue
    val shape = SquircleShape(corner)
    Column(
        modifier = modifier
            .border(1.dp, galaxy_border_brush(accent, colors.text_primary), shape)
            .clip(shape)
            .background(colors.bg_card)
            .background(
                Brush.verticalGradient(
                    0.00f to accent.copy(alpha = if (colors.is_dark) 0.14f else 0.07f),
                    0.32f to accent.copy(alpha = 0.03f),
                    0.55f to Color.Transparent,
                ),
            )
            .starfield(accent, colors.is_dark, band_fraction = 0.30f, edges_only = true),
        content = content,
    )
}

@Composable
internal fun icon_tile(
    icon: ImageVector,
    size: Dp = 40.dp,
    corner: Dp = 12.dp,
    accent: Color = AsterMaterial.colors.accent_blue,
    muted: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(corner)
    val brush = if (muted) {
        Brush.verticalGradient(listOf(colors.bg_tertiary, colors.bg_tertiary))
    } else {
        Brush.linearGradient(
            0.00f to blend(accent, Color.White, 0.28f),
            0.55f to accent,
            1.00f to blend(accent, Color.Black, 0.18f),
        )
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(brush)
            .then(if (muted) Modifier.border(1.dp, colors.border_primary, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (muted) colors.text_secondary else Color.White,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

@Composable
internal fun gradient_bar(fraction: Float, is_over: Boolean, height: Dp = 8.dp) {
    val colors = AsterMaterial.colors
    val target = fraction.coerceIn(0.02f, 1f)
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700),
        label = "storage_fill",
    )
    val accent = if (is_over) colors.danger else colors.accent_blue
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(colors.bg_tertiary),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        0.0f to blend(accent, Color.White, 0.30f),
                        1.0f to accent,
                    ),
                ),
        )
    }
}
