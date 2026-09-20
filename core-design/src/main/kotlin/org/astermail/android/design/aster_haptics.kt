// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.design

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch

enum class aster_haptic {
    tap,
    tick,
    toggle_on,
    toggle_off,
    confirm,
    reject,
    gesture_threshold,
    long_press,
}

internal fun aster_haptic_constant(kind: aster_haptic, sdk: Int = Build.VERSION.SDK_INT): Int = when (kind) {
    aster_haptic.tap -> HapticFeedbackConstants.VIRTUAL_KEY
    aster_haptic.tick -> HapticFeedbackConstants.CLOCK_TICK
    aster_haptic.toggle_on -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_ON else HapticFeedbackConstants.VIRTUAL_KEY
    aster_haptic.toggle_off -> if (sdk >= 34) HapticFeedbackConstants.TOGGLE_OFF else HapticFeedbackConstants.CLOCK_TICK
    aster_haptic.confirm -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
    aster_haptic.reject -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    aster_haptic.gesture_threshold ->
        if (sdk >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE else HapticFeedbackConstants.CLOCK_TICK
    aster_haptic.long_press -> HapticFeedbackConstants.LONG_PRESS
}

fun View.aster_perform_haptic(kind: aster_haptic) {
    performHapticFeedback(aster_haptic_constant(kind))
}

fun View.aster_click_sound() {
    playSoundEffect(SoundEffectConstants.CLICK)
}

fun View.aster_tap_feedback(kind: aster_haptic = aster_haptic.tap) {
    aster_click_sound()
    aster_perform_haptic(kind)
}

@Stable
private class aster_press_feedback_indication(
    private val inner: IndicationNodeFactory,
    private val kind: aster_haptic?,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        aster_press_feedback_node(interactionSource, kind, inner.create(interactionSource))

    override fun equals(other: Any?): Boolean =
        other is aster_press_feedback_indication && other.inner == inner && other.kind == kind

    override fun hashCode(): Int = 31 * inner.hashCode() + kind.hashCode()
}

private class aster_press_feedback_node(
    private val interaction_source: InteractionSource,
    private val kind: aster_haptic?,
    inner: DelegatableNode,
) : DelegatingNode(), CompositionLocalConsumerModifierNode {
    init {
        delegate(inner)
    }

    override fun onAttach() {
        coroutineScope.launch {
            interaction_source.interactions.collect { interaction ->
                if (interaction is PressInteraction.Release) {
                    val view = currentValueOf(LocalView)
                    if (kind == null) view.aster_click_sound() else view.aster_tap_feedback(kind)
                }
            }
        }
    }
}

fun aster_with_tap_haptic(inner: IndicationNodeFactory, kind: aster_haptic = aster_haptic.tap): IndicationNodeFactory =
    aster_press_feedback_indication(inner, kind)

@Composable
fun aster_ripple(
    bounded: Boolean = true,
    radius: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified,
): IndicationNodeFactory =
    aster_press_feedback_indication(ripple(bounded = bounded, radius = radius, color = color), null)

@Composable
fun aster_press_feedback(interaction_source: InteractionSource, kind: aster_haptic?) {
    val view = LocalView.current
    LaunchedEffect(interaction_source, kind) {
        interaction_source.interactions.collect { interaction ->
            if (interaction is PressInteraction.Release) {
                if (kind == null) view.aster_click_sound() else view.aster_tap_feedback(kind)
            }
        }
    }
}

@Composable
fun aster_tap_haptics(interaction_source: InteractionSource, kind: aster_haptic = aster_haptic.tap) {
    aster_press_feedback(interaction_source, kind)
}

@Composable
fun remember_click_interaction(): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    aster_press_feedback(source, null)
    return source
}

@Composable
fun remember_haptic_interaction(kind: aster_haptic): MutableInteractionSource {
    val source = remember { MutableInteractionSource() }
    aster_press_feedback(source, kind)
    return source
}

@Composable
fun remember_haptic(): (aster_haptic) -> Unit {
    val view = LocalView.current
    return remember(view) { { kind -> view.aster_perform_haptic(kind) } }
}
