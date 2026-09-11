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

package org.astermail.android.design.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.HorizontalAlignmentLine
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measured
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

private data class action_row_weight(val weight: Float, val fill: Boolean) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@action_row_weight
}

private object action_row_scope : RowScope {
    override fun Modifier.weight(weight: Float, fill: Boolean): Modifier =
        this.then(action_row_weight(weight, fill))

    override fun Modifier.align(alignment: Alignment.Vertical): Modifier = this

    override fun Modifier.alignBy(alignmentLine: HorizontalAlignmentLine): Modifier = this

    override fun Modifier.alignByBaseline(): Modifier = this

    override fun Modifier.alignBy(alignmentLineBlock: (Measured) -> Int): Modifier = this
}

@Composable
fun AsterActionRow(
    modifier: Modifier = Modifier,
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Layout(
        content = { action_row_scope.content() },
        modifier = modifier,
    ) { measurables, constraints ->
        val count = measurables.size
        if (count == 0) return@Layout layout(constraints.minWidth, constraints.minHeight) {}
        val space = spacing.roundToPx()
        val gaps = space * (count - 1)
        val weights = measurables.map { (it.parentData as? action_row_weight)?.weight ?: 0f }
        val total_weight = weights.sum()
        val intrinsic = measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) }
        val fixed_sum = intrinsic.filterIndexed { index, _ -> weights[index] <= 0f }.sum()
        val bounded = constraints.hasBoundedWidth
        val available = if (bounded) constraints.maxWidth - gaps - fixed_sum else Int.MAX_VALUE
        val target = IntArray(count) { index ->
            if (weights[index] > 0f && bounded) {
                (available * (weights[index] / total_weight)).roundToInt()
            } else {
                intrinsic[index]
            }
        }
        val fits = !bounded || (
            available >= 0 &&
                (0 until count).all { index -> weights[index] <= 0f || target[index] >= intrinsic[index] }
            )
        if (fits) {
            val placeables = measurables.mapIndexed { index, measurable ->
                val width = target[index]
                val min_width = if (weights[index] > 0f && bounded) width else 0
                measurable.measure(
                    constraints.copy(minWidth = min_width, maxWidth = max(width, min_width), minHeight = 0),
                )
            }
            val used = placeables.sumOf { it.width } + gaps
            val width = if (bounded) constraints.maxWidth else max(used, constraints.minWidth)
            val height = max(placeables.maxOf { it.height }, constraints.minHeight)
            layout(width, height) {
                var x = if (total_weight > 0f) 0 else max(width - used, 0)
                placeables.forEach { placeable ->
                    placeable.placeRelative(x, (height - placeable.height) / 2)
                    x += placeable.width + space
                }
            }
        } else {
            val placeables = measurables.map { measurable ->
                measurable.measure(
                    constraints.copy(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth, minHeight = 0),
                )
            }
            val height = max(placeables.sumOf { it.height } + gaps, constraints.minHeight)
            layout(constraints.maxWidth, height) {
                var y = 0
                placeables.asReversed().forEach { placeable ->
                    placeable.placeRelative(0, y)
                    y += placeable.height + space
                }
            }
        }
    }
}
