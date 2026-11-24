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

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth

@Stable
class ime_settle internal constructor(
    private val ime: WindowInsets,
    private val ime_target: WindowInsets,
    private val base: WindowInsets,
) {
    private fun bottom_now(density: Density): Int =
        maxOf(base.getBottom(density), ime.getBottom(density))

    private fun bottom_settled(density: Density): Int =
        maxOf(base.getBottom(density), ime_target.getBottom(density))

    fun pending_shift(density: Density): Int =
        bottom_now(density) - bottom_settled(density)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun remember_ime_settle(): ime_settle {
    val ime = WindowInsets.ime
    val ime_target = WindowInsets.imeAnimationTarget
    val bars = WindowInsets.systemBars
    val cutout = WindowInsets.displayCutout
    return remember(ime, ime_target, bars, cutout) {
        ime_settle(ime = ime, ime_target = ime_target, base = bars.union(cutout))
    }
}

fun Modifier.ime_settle_anchor(settle: ime_settle): Modifier = layout { measurable, constraints ->
    val shift = settle.pending_shift(this)
    val inner = if (constraints.hasBoundedHeight) {
        constraints.copy(
            maxHeight = (constraints.maxHeight + shift).coerceAtLeast(constraints.minHeight),
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(inner)
    layout(
        constraints.constrainWidth(placeable.width),
        constraints.constrainHeight((placeable.height - shift).coerceAtLeast(0)),
    ) {
        placeable.place(0, 0)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Modifier.settled_safe_drawing_padding(): Modifier = this
    .windowInsetsPadding(
        WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .union(WindowInsets.imeAnimationTarget),
    )
    .consumeWindowInsets(WindowInsets.ime)
