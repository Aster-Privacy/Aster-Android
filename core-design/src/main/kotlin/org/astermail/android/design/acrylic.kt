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

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

fun interface AcrylicSource {
    fun DrawScope.paint(origin: Offset)
}

val local_acrylic = staticCompositionLocalOf<AcrylicSource?> { null }

private const val acrylic_tint_strength = 0.82f

@Composable
fun Modifier.acrylic(
    colors: AsterSemanticColors,
    shape: Shape,
    tint: Color = colors.bg_card,
): Modifier {
    if (!colors.is_glass) return this.clip(shape).background(tint)
    val source = local_acrylic.current ?: return this.clip(shape).background(colors.glass_surface(tint))
    var origin by remember { mutableStateOf(Offset.Zero) }
    val veil = colors.glass_surface(tint).copy(alpha = colors.glass_opacity * acrylic_tint_strength)
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .clip(shape)
        .drawBehind {
            with(source) { paint(origin) }
            drawRect(color = veil)
        }
}

@Composable
fun Modifier.acrylic_backdrop(colors: AsterSemanticColors): Modifier {
    if (!colors.is_glass) return this
    val source = local_acrylic.current ?: return this
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawBehind { with(source) { paint(origin) } }
}
