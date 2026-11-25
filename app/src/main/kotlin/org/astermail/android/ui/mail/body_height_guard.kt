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

private const val MIN_MEASURABLE_WIDTH_DP = 120f

internal fun body_overflows_vertically(content_px: Int, view_px: Int, slack_px: Int): Boolean =
    view_px > 0 && content_px > view_px + slack_px.coerceAtLeast(0)

internal fun body_keeps_vertical_drag(
    overflows: Boolean,
    drag_dy: Float,
    can_scroll_down: Boolean,
    can_scroll_up: Boolean,
    bottom_visible: Boolean,
): Boolean {
    if (!overflows || !bottom_visible) return false
    return (drag_dy < 0f && can_scroll_down) || (drag_dy > 0f && can_scroll_up)
}

internal fun body_claims_horizontal_pan(drag_dx: Float, can_scroll_forward: Boolean, can_scroll_back: Boolean): Boolean =
    (drag_dx < 0f && can_scroll_forward) || (drag_dx > 0f && can_scroll_back)

internal fun body_overflows_sideways(scroll_range: Int, scroll_extent: Int): Boolean =
    scroll_extent > 0 && scroll_range > scroll_extent + 1

internal fun body_width_changed(old_width: Int, new_width: Int): Boolean =
    old_width > 0 && new_width > 0 && old_width != new_width

internal fun body_width_measurable(width_px: Int, density: Float): Boolean =
    width_px >= MIN_MEASURABLE_WIDTH_DP * density.coerceAtLeast(1f)

internal fun keeps_inline_images(
    current: Map<String, String>,
    resolved: Map<String, String>,
    same_html: Boolean,
): Boolean =
    same_html && resolved.size < current.size && current.keys.containsAll(resolved.keys)

internal const val pending_send_phase_queued = 0
internal const val pending_send_phase_sending = 1
internal const val pending_send_phase_sent = 2

internal fun pending_send_phase(sent: Boolean, send_at_ms: Long?, now_ms: Long): Pair<Int, Int> = when {
    sent -> pending_send_phase_sent to 0
    send_at_ms != null && now_ms < send_at_ms ->
        pending_send_phase_queued to ((send_at_ms - now_ms + 999L) / 1000L).toInt()
    else -> pending_send_phase_sending to 0
}

internal fun thread_item_keys(ids: List<String>, aliases: Map<String, String>): List<String> {
    if (aliases.isEmpty()) return ids
    val taken = HashSet<String>(ids)
    return ids.map { id ->
        val alias = aliases[id]
        if (alias != null && taken.add(alias)) alias else id
    }
}
