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

package org.astermail.android.ui.common

internal enum class ToastWindowAction { none, attach, raise, relayout }

internal enum class ToastTouchRoute { toast, host, drop }

internal const val toast_flag_not_touchable = 0x00000010

internal fun toast_window_flags(flags: Int, touchable: Boolean): Int =
    if (touchable) flags and toast_flag_not_touchable.inv() else flags or toast_flag_not_touchable

internal data class toast_card_box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
}

internal fun toast_touch_route(
    cards: Collection<toast_card_box>,
    x: Float,
    y: Float,
    popup_layers_open: Int,
): ToastTouchRoute = when {
    cards.any { it.contains(x, y) } -> ToastTouchRoute.toast
    popup_layers_open > 0 -> ToastTouchRoute.drop
    else -> ToastTouchRoute.host
}


internal fun toast_window_action(
    attached: Boolean,
    attached_generation: Int,
    generation: Int,
    wants_window: Boolean,
    idle: Boolean = true,
    spec_matches: Boolean = true,
): ToastWindowAction = when {
    !attached -> if (wants_window) ToastWindowAction.attach else ToastWindowAction.none
    !idle -> ToastWindowAction.none
    attached_generation != generation -> ToastWindowAction.raise
    !spec_matches -> ToastWindowAction.relayout
    else -> ToastWindowAction.none
}

internal data class toast_window_ledger(
    val attached: Boolean = false,
    val generation: Int = 0,
    val spec: toast_window_spec? = null,
    val adds: Int = 0,
    val removes: Int = 0,
    val relayouts: Int = 0,
)

internal fun toast_window_step(
    ledger: toast_window_ledger,
    wants_window: Boolean,
    generation: Int,
    idle: Boolean,
    spec: toast_window_spec,
): toast_window_ledger = when (
    toast_window_action(ledger.attached, ledger.generation, generation, wants_window, idle, ledger.spec == spec)
) {
    ToastWindowAction.none -> ledger
    ToastWindowAction.attach -> ledger.copy(attached = true, generation = generation, spec = spec, adds = ledger.adds + 1)
    ToastWindowAction.raise -> ledger.copy(
        generation = generation,
        spec = spec,
        adds = ledger.adds + 1,
        removes = ledger.removes + 1,
    )
    ToastWindowAction.relayout -> ledger.copy(spec = spec, relayouts = ledger.relayouts + 1)
}
