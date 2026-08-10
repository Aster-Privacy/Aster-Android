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

package org.astermail.android.ui.common

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.design.AsterMaterial
import org.astermail.android.settings.optional_shared_settings_view_model

val plan_ring_stroke: Dp = 2.dp
val plan_ring_gap: Dp = 3.dp

private val unpaid_subscription_states = setOf(
    "canceled",
    "cancelled",
    "incomplete_expired",
    "unpaid",
)

@Composable
fun plan_ring_brush(): Brush {
    val accent = AsterMaterial.colors.accent_blue
    return remember(accent) {
        val light = lerp(accent, Color.White, 0.32f)
        val deep = lerp(accent, Color.Black, 0.22f)
        val seam = lerp(accent, deep, 0.5f)
        Brush.sweepGradient(
            0.000f to seam,
            0.125f to deep,
            0.375f to accent,
            0.625f to light,
            0.875f to accent,
            1.000f to seam,
        )
    }
}

@Composable
fun plan_ring(
    size: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) { content() }
        return
    }

    val inset = plan_ring_stroke + plan_ring_gap
    Box(
        modifier = modifier
            .size(size + inset * 2)
            .border(plan_ring_stroke, plan_ring_brush(), CircleShape)
            .padding(inset),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun remember_has_paid_plan(): Boolean {
    val settings_vm = optional_shared_settings_view_model()
    val context = LocalContext.current
    val plan_prefs = remember(context) {
        context.getSharedPreferences("aster_plan", Context.MODE_PRIVATE)
    }
    var cached_paid by rememberSaveable {
        mutableStateOf(plan_prefs.getBoolean("has_paid", false))
    }
    if (settings_vm == null) return cached_paid

    val settings_state by settings_vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { settings_vm.load_subscription(force = false) }

    LaunchedEffect(settings_state.subscription) {
        val subscription = settings_state.subscription ?: return@LaunchedEffect
        val paid = subscription.effective_price_cents > 0 &&
            subscription.status !in unpaid_subscription_states
        if (paid != cached_paid) {
            cached_paid = paid
        }
        plan_prefs.edit()
            .putBoolean("has_paid", paid)
            .putBoolean("plan_known", true)
            .apply()
    }

    return cached_paid
}
