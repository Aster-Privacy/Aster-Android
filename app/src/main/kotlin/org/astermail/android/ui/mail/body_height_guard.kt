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

internal fun zoomed_body_height_dp(base_dp: Float, ratio: Float, max_dp: Int): Float =
    (base_dp * ratio).coerceIn(1f, max_dp.coerceAtLeast(1).toFloat())

internal class body_height_guard(private val max_capped_remeasures: Int = 2) {
    private var capped_remeasures = 0

    fun should_remeasure_capped(visual_height: Int, max_height: Int): Boolean {
        if (max_height <= 0 || visual_height < max_height) return false
        if (capped_remeasures >= max_capped_remeasures) return false
        capped_remeasures++
        return true
    }
}

internal class body_growth_guard(
    private val ratio_tolerance: Float = 0.02f,
    private val max_repeats: Int = 2,
) {
    private var last_ratio = 0f
    private var repeats = 0
    private var anchor = 0
    private var locked = false

    fun reset() {
        last_ratio = 0f
        repeats = 0
        anchor = 0
        locked = false
    }

    fun settle(current: Int, reported: Int): Int {
        if (current <= 0 || reported <= current) return reported
        val ratio = reported.toFloat() / current.toFloat()
        val same_ratio = last_ratio > 0f && kotlin.math.abs(ratio - last_ratio) <= ratio_tolerance
        if (locked) {
            if (same_ratio) return anchor
            locked = false
            repeats = 0
            anchor = current
            last_ratio = ratio
            return reported
        }
        if (same_ratio) {
            repeats++
        } else {
            repeats = 0
            anchor = current
        }
        last_ratio = ratio
        if (repeats >= max_repeats) {
            locked = true
            return anchor
        }
        return reported
    }
}

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

internal fun body_needs_regrowth(content_dp: Float, shown_dp: Float, max_dp: Float, slack_dp: Float): Boolean {
    if (content_dp <= 0f || shown_dp <= 0f) return false
    if (shown_dp >= max_dp - slack_dp) return false
    return content_dp.coerceAtMost(max_dp) > shown_dp + slack_dp
}

internal fun body_overflows_sideways(scroll_range: Int, scroll_extent: Int): Boolean =
    scroll_extent > 0 && scroll_range > scroll_extent + 1

internal const val OVERFLOW_FIT_SLACK_CSS_PX = 4

internal const val OVERFLOW_FIT_MARGIN_CSS_PX = 2

internal fun overflow_fit_width(
    scroll_range: Int,
    scroll_extent: Int,
    scale: Float,
    current_fit: Int?,
    max_width: Int = MAX_FIT_CONTENT_WIDTH,
): Int? {
    if (scale <= 0f || !body_overflows_sideways(scroll_range, scroll_extent)) return null
    val overflow_css = (scroll_range - scroll_extent) / scale
    if (overflow_css < OVERFLOW_FIT_SLACK_CSS_PX) return null
    val needed = kotlin.math.ceil(scroll_range / scale).toInt() + OVERFLOW_FIT_MARGIN_CSS_PX
    val fit = minOf(needed, max_width)
    if (current_fit != null && fit <= current_fit) return null
    return fit
}

internal fun body_height_viewport_filled(
    content_css: Int,
    viewport_css: Int,
    scroll_range: Int,
    scroll_extent: Int,
): Boolean {
    if (content_css <= 0 || viewport_css <= 0) return false
    if (!body_overflows_sideways(scroll_range, scroll_extent)) return false
    val filled = viewport_css.toLong() * scroll_range / scroll_extent
    return content_css <= filled + maxOf(2L, filled / 50L)
}

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
