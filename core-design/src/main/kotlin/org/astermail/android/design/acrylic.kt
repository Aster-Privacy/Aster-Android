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
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager

fun interface AcrylicSource {
    fun DrawScope.paint(origin: Offset)
}

val local_acrylic = staticCompositionLocalOf<AcrylicSource?> { null }

private const val acrylic_tint_strength = 0.82f

@Composable
fun in_overlay_window(): Boolean {
    val view = LocalView.current
    return remember(view) {
        val params = view.rootView.layoutParams as? WindowManager.LayoutParams
        params != null && params.type != WindowManager.LayoutParams.TYPE_BASE_APPLICATION
    }
}

fun Modifier.window_anchored_behind(on_draw: DrawScope.(origin: Offset) -> Unit): Modifier =
    this then WindowAnchoredBehindElement(on_draw)

private data class WindowAnchoredBehindElement(
    val on_draw: DrawScope.(origin: Offset) -> Unit,
) : ModifierNodeElement<WindowAnchoredBehindNode>() {
    override fun create(): WindowAnchoredBehindNode = WindowAnchoredBehindNode(on_draw)

    override fun update(node: WindowAnchoredBehindNode) {
        node.on_draw = on_draw
        node.invalidateDraw()
    }
}

private class WindowAnchoredBehindNode(
    var on_draw: DrawScope.(origin: Offset) -> Unit,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var origin = Offset.Zero

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        val next = coordinates.positionInWindow()
        if (next == origin) return
        origin = next
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        on_draw(origin)
        drawContent()
    }
}

@Composable
fun Modifier.acrylic(
    colors: AsterSemanticColors,
    shape: Shape,
    tint: Color = colors.bg_card,
): Modifier {
    if (in_overlay_window()) return this.clip(shape).background(tint.copy(alpha = 1f))
    if (!colors.is_translucent) return this.clip(shape).background(colors.glass_surface(tint))
    val source = local_acrylic.current ?: return this.clip(shape).background(colors.glass_surface(tint))
    val veil = colors.glass_surface(tint).copy(alpha = colors.glass_opacity * acrylic_tint_strength)
    val on_draw: DrawScope.(Offset) -> Unit = remember(source, veil) {
        { origin ->
            with(source) { paint(origin) }
            drawRect(color = veil)
        }
    }
    return this
        .clip(shape)
        .window_anchored_behind(on_draw)
        .graphicsLayer()
}

@Composable
fun Modifier.acrylic_backdrop(colors: AsterSemanticColors): Modifier {
    if (in_overlay_window()) return this
    if (!colors.is_translucent) return this
    val source = local_acrylic.current ?: return this
    val on_draw: DrawScope.(Offset) -> Unit = remember(source) {
        { origin -> with(source) { paint(origin) } }
    }
    return this
        .window_anchored_behind(on_draw)
        .graphicsLayer()
}
