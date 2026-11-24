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

internal const val BODY_SKELETON_DELAY_MS = 150L
internal const val BODY_PAINT_FALLBACK_MS = 2500L
internal const val BODY_READ_FALLBACK_ATTEMPTS = 10
internal const val BODY_READ_FALLBACK_STEP_MS = 120L

internal const val BODY_HEIGHT_SLACK_CSS = 1

internal const val LIVE_BODY_VIEW_CAPACITY = 6

internal class body_generation_gate {
    private var current = 0
    private var released = false

    val generation: Int get() = current

    val is_released: Boolean get() = released

    fun next(): Int {
        if (!released) current++
        return current
    }

    fun accepts(generation: Int): Boolean = !released && generation == current

    fun release() {
        released = true
        current++
    }
}

internal enum class body_reading_verdict { stale, grow, settle, probe }

internal fun judge_body_reading(
    accepted: Boolean,
    content_css: Int,
    view_css: Int,
    probe_css: Int,
    view_is_known_height: Boolean,
): body_reading_verdict = when {
    !accepted || content_css <= 0 || view_css <= 0 -> body_reading_verdict.stale
    content_css > view_css + BODY_HEIGHT_SLACK_CSS -> body_reading_verdict.grow
    view_css <= probe_css + BODY_HEIGHT_SLACK_CSS -> body_reading_verdict.settle
    view_is_known_height -> body_reading_verdict.settle
    else -> body_reading_verdict.probe
}

internal fun body_reading_height_dp(content_css: Int, max_dp: Int): Float =
    content_css.toFloat().coerceIn(1f, max_dp.coerceAtLeast(1).toFloat())

internal fun body_height_cacheable(
    accepted: Boolean,
    painted: Boolean,
    verdict: body_reading_verdict,
    height_dp: Float,
): Boolean =
    accepted && painted && height_dp >= 1f &&
        (verdict == body_reading_verdict.grow || verdict == body_reading_verdict.settle)

internal data class body_height_seed(val view_dp: Float?, val reserve_dp: Float, val measured: Boolean)

internal fun seed_body_height(cached_dp: Float?, estimate_dp: Float): body_height_seed =
    if (cached_dp != null && cached_dp >= 1f) {
        body_height_seed(view_dp = cached_dp, reserve_dp = cached_dp, measured = false)
    } else {
        body_height_seed(view_dp = null, reserve_dp = estimate_dp.coerceAtLeast(1f), measured = false)
    }

internal fun show_body_skeleton(measured: Boolean, elapsed_ms: Long): Boolean =
    !measured && elapsed_ms >= BODY_SKELETON_DELAY_MS

internal class pending_collapse(
    val generation: Int,
    val collapsed_dp: Float?,
    val shift_dp: Float,
)

internal class quote_toggle_state {
    private var base_dp = 0f
    private var current_dp = 0f

    val collapsed_height_dp: Float? get() = if (base_dp >= 1f) base_dp else null

    val is_expanded: Boolean get() = base_dp >= 1f && current_dp > base_dp + BODY_HEIGHT_SLACK_CSS

    fun on_exact_height(height_dp: Float) {
        if (height_dp < 1f) return
        if (base_dp < 1f) base_dp = height_dp
        current_dp = height_dp
    }

    fun on_collapse_requested(): Float {
        val target = base_dp
        if (target < 1f) return 0f
        val shift = (current_dp - target).coerceAtLeast(0f)
        current_dp = target
        return shift
    }

    fun reset() {
        base_dp = 0f
        current_dp = 0f
    }
}

internal class live_view_lru<K : Any, V : Any>(
    private val capacity: Int,
    private val release: (V) -> Unit,
) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)
    private val attached = HashSet<K>()

    val size: Int get() = entries.size

    fun contains(key: K): Boolean = entries.containsKey(key)

    fun is_attached(key: K): Boolean = attached.contains(key)

    fun get(key: K): V? = entries[key]

    fun put(key: K, value: V, attach: Boolean = false) {
        val previous = entries.put(key, value)
        if (attach) attached.add(key)
        if (previous != null && previous !== value) safe_release(previous)
        trim()
    }

    fun attach(key: K) {
        if (entries.containsKey(key)) attached.add(key)
    }

    fun detach(key: K) {
        attached.remove(key)
        trim()
    }

    fun remove(key: K) {
        attached.remove(key)
        entries.remove(key)?.let { safe_release(it) }
    }

    fun evict_detached() {
        val victims = entries.keys.filter { it !in attached }
        for (key in victims) entries.remove(key)?.let { safe_release(it) }
    }

    fun release_all() {
        val values = entries.values.toList()
        entries.clear()
        attached.clear()
        for (value in values) safe_release(value)
    }

    private fun trim() {
        while (entries.size > capacity.coerceAtLeast(1)) {
            val victim = entries.keys.firstOrNull { it !in attached } ?: return
            entries.remove(victim)?.let { safe_release(it) }
        }
    }

    private fun safe_release(value: V) {
        runCatching { release(value) }
    }
}
