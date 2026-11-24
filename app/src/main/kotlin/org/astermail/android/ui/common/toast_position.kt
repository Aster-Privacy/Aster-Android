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

enum class ToastEdge { top, bottom }

const val toast_position_top = "top"
const val toast_position_bottom = "bottom-center"
const val default_toast_position = toast_position_top

val toast_position_choices: List<String> = listOf(toast_position_top, toast_position_bottom)

internal const val toast_gravity_top = 0x30
internal const val toast_gravity_bottom = 0x50
internal const val toast_gravity_start = 0x00800003

fun toast_edge_for(value: String?): ToastEdge {
    val trimmed = value?.trim().orEmpty()
    return if (trimmed.startsWith("bottom-")) ToastEdge.bottom else ToastEdge.top
}

fun toast_position_choice_for(value: String?): String =
    if (toast_edge_for(value) == ToastEdge.top) toast_position_top else toast_position_bottom

fun toast_position_after_pick(stored: String?, picked: String): String? {
    if (picked !in toast_position_choices) return stored
    if (toast_position_choice_for(stored) == picked) return stored
    return picked
}

internal fun toast_window_gravity(edge: ToastEdge): Int =
    (if (edge == ToastEdge.top) toast_gravity_top else toast_gravity_bottom) or toast_gravity_start

internal fun toast_enter_sign(edge: ToastEdge): Float = if (edge == ToastEdge.top) -1f else 1f

internal data class toast_window_spec(
    val edge: ToastEdge,
    val height_px: Int,
    val offset_px: Int,
    val secure: Boolean,
)

internal fun toast_hidden_offset(edge: ToastEdge, card_height_px: Float, margin_px: Float): Float =
    toast_enter_sign(edge) * (card_height_px + margin_px)

internal fun toast_swipe_dismisses(edge: ToastEdge, offset_px: Float, velocity_px: Float, distance_px: Float, speed_px: Float): Boolean {
    val sign = toast_enter_sign(edge)
    return offset_px * sign > distance_px || velocity_px * sign > speed_px
}

internal fun toast_drag_translation(edge: ToastEdge, offset_px: Float, resistance: Float): Float {
    val toward_edge = offset_px * toast_enter_sign(edge) > 0f
    return if (toward_edge) offset_px else offset_px * resistance
}
