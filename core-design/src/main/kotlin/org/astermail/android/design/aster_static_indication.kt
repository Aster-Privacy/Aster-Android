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

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch

private const val static_press_alpha = 0.1f

fun aster_static_press_indication(color: Color): IndicationNodeFactory = AsterStaticPressIndication(color)

private data class AsterStaticPressIndication(private val color: Color) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        AsterStaticPressNode(interactionSource, color)
}

private class AsterStaticPressNode(
    private val interaction_source: InteractionSource,
    private val color: Color,
) : Modifier.Node(), DrawModifierNode {
    private var pressed = false

    override fun onAttach() {
        coroutineScope.launch {
            val active = mutableSetOf<PressInteraction.Press>()
            interaction_source.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> active.add(interaction)
                    is PressInteraction.Release -> active.remove(interaction.press)
                    is PressInteraction.Cancel -> active.remove(interaction.press)
                }
                val now_pressed = active.isNotEmpty()
                if (now_pressed != pressed) {
                    pressed = now_pressed
                    invalidateDraw()
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) {
            drawRect(color = color.copy(alpha = static_press_alpha))
        }
    }
}
