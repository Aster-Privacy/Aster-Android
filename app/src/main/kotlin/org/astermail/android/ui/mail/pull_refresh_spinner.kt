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
package org.astermail.android.ui.mail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.acrylic

private val spinner_disc_size = 40.dp

private val spinner_ring_size = 22.dp

private val spinner_stroke = 2.5.dp

private val spinner_rest_drop = 6.dp

private val spinner_overpull_travel = 28.dp

private const val ARC_MIN_DEG = 18f

private const val ARC_RANGE_DEG = 252f

private const val ARC_CAP_DEG = 8f

private const val SPIN_CYCLE_MS = 1332L

private const val SPIN_TURN_MS = 1900f

private fun ease(value: Float): Float = FastOutSlowInEasing.transform(value.coerceIn(0f, 1f))

private fun mix(from: Float, to: Float, amount: Float): Float = from + (to - from) * amount

@Composable
internal fun pull_refresh_spinner(
    pull: () -> Float,
    shown: () -> Float,
    pop: () -> Float,
    spinning: Boolean,
    reduce_motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val travel_px = with(density) { (spinner_disc_size + spinner_rest_drop).toPx() }
    val overpull_px = with(density) { spinner_overpull_travel.toPx() }
    val spin_ms = remember { mutableLongStateOf(0L) }
    val handoff_deg = remember { floatArrayOf(-90f) }
    val shown_active = remember { derivedStateOf { shown() > 0.001f } }
    val active = spinning || shown_active.value
    LaunchedEffect(active, reduce_motion) {
        spin_ms.longValue = 0L
        if (!active || reduce_motion) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) {
            withFrameNanos { now -> spin_ms.longValue = (now - start) / 1_000_000L }
        }
    }
    val accent = colors.accent_blue
    val track = colors.text_tertiary.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .graphicsLayer {
                val spin = shown()
                val raw = pull()
                val reveal = maxOf(spin, raw.coerceIn(0f, 1f))
                val over = (raw - 1f).coerceAtLeast(0f)
                alpha = ease(reveal * 1.4f)
                translationY = -travel_px * (1f - ease(reveal)) + overpull_px * (over / (1f + over))
                val scale = 0.55f + 0.45f * ease(reveal) + 0.12f * pop()
                scaleX = scale
                scaleY = scale
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .size(spinner_disc_size)
            .acrylic(colors, CircleShape, colors.bg_secondary)
            .border(1.dp, colors.border_secondary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(spinner_ring_size)) {
            val stroke = spinner_stroke.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val arc_origin = Offset(center.x - radius, center.y - radius)
            val arc_size = Size(radius * 2f, radius * 2f)
            val spin = shown()
            val raw = pull()
            val progress = raw.coerceIn(0f, 1f)
            val over = (raw - 1f).coerceAtLeast(0f)
            val spinning_now = spinning || spin > 0.001f
            drawCircle(color = track, radius = radius, style = Stroke(width = stroke))
            val start: Float
            var sweep: Float
            val intensity: Float
            if (spinning_now) {
                val elapsed = spin_ms.longValue
                val phased = elapsed + SPIN_CYCLE_MS / 2
                val cycle = phased / SPIN_CYCLE_MS
                val phase = (phased % SPIN_CYCLE_MS).toFloat() / SPIN_CYCLE_MS
                val head = ARC_RANGE_DEG * ease(phase * 2f)
                val tail = ARC_RANGE_DEG * ease(phase * 2f - 1f)
                start = handoff_deg[0] + elapsed * 360f / SPIN_TURN_MS + cycle * ARC_RANGE_DEG + tail
                sweep = head - tail + ARC_MIN_DEG
                if (!spinning) sweep = mix(sweep, 360f - ARC_CAP_DEG * 2f, ease((1f - spin) * 2.2f))
                intensity = 1f
            } else {
                start = -90f + 150f * ease(progress) + 220f * over
                sweep = ARC_MIN_DEG + ARC_RANGE_DEG * ease(progress)
                handoff_deg[0] = start
                intensity = if (progress >= 1f) 1f else mix(0.45f, 0.85f, progress)
            }
            sweep = sweep.coerceIn(ARC_MIN_DEG, 360f - ARC_CAP_DEG * 2f)
            drawArc(
                color = accent.copy(alpha = intensity),
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arc_origin,
                size = arc_size,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
