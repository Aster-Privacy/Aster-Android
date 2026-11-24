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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal class body_measure {
    val gate = body_generation_gate()
    val quote = quote_toggle_state()
    var height_key: Long = Long.MIN_VALUE
        private set
    var view_dp by mutableFloatStateOf(0f)
    var reserve_dp by mutableFloatStateOf(1f)
    var measured by mutableStateOf(false)
    var probing by mutableStateOf(false)
    var painted by mutableStateOf(false)
    var view_is_known_height = false
    var load_generation = 0
        private set
    var loaded_built: String? = null
    var loaded_external = false
    var discard_view = false
    var started_ms = 0L
    var width_px = 0

    fun begin_key(key: Long, seed: body_height_seed, now_ms: Long) {
        height_key = key
        view_dp = seed.view_dp ?: 0f
        reserve_dp = seed.reserve_dp
        view_is_known_height = seed.view_dp != null
        measured = seed.measured
        probing = false
        painted = false
        started_ms = now_ms
        quote.reset()
    }

    fun begin_load(now_ms: Long, keep_height: Boolean): Int {
        load_generation = gate.next()
        painted = false
        probing = false
        if (!keep_height) {
            measured = false
            started_ms = now_ms
        }
        return load_generation
    }

    fun accepts(generation: Int): Boolean = gate.accepts(generation) && generation == load_generation
}

internal class held_body(val measure: body_measure) {
    var view: mail_body_web_view? = null

    fun release(close_gate: Boolean) {
        if (close_gate) measure.gate.release()
        val target = view ?: return
        view = null
        release_body_view_later(target)
    }
}

internal fun release_body_view_later(view: mail_body_web_view) {
    android.os.Handler(android.os.Looper.getMainLooper()).post {
        runCatching {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view.release_body()
        }
    }
}

internal class body_view_holder(capacity: Int = LIVE_BODY_VIEW_CAPACITY) {
    private var released = false
    private val entries = live_view_lru<String, held_body>(capacity) { it.release(released) }

    val is_released: Boolean get() = released

    var on_quote_collapse: ((String, Float) -> Unit)? = null

    fun measure_for(key: String): body_measure {
        entries.get(key)?.let { return it.measure }
        val fresh = held_body(body_measure())
        if (!released) entries.put(key, fresh)
        return fresh.measure
    }

    fun take_view(key: String, measure: body_measure): mail_body_web_view? {
        if (released) return null
        val entry = entries.get(key) ?: return null
        if (entry.measure !== measure) return null
        val view = entry.view ?: return null
        if (view.destroyed || measure.discard_view) {
            entry.view = null
            release_body_view_later(view)
            return null
        }
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        entries.attach(key)
        return view
    }

    fun keep_view(key: String, measure: body_measure, view: mail_body_web_view): Boolean {
        if (released || view.destroyed) return false
        var entry = entries.get(key)
        if (entry == null || entry.measure !== measure) {
            entry = held_body(measure)
            entries.put(key, entry, attach = true)
        } else {
            entries.attach(key)
        }
        val previous = entry.view
        if (previous != null && previous !== view) release_body_view_later(previous)
        entry.view = view
        return entries.get(key) === entry
    }

    fun park(key: String, view: mail_body_web_view): Boolean {
        if (released) return false
        val entry = entries.get(key) ?: return false
        if (entry.view !== view) return false
        if (view.destroyed || entry.measure.discard_view) {
            entry.view = null
            return false
        }
        entries.detach(key)
        return true
    }

    fun evict_detached() {
        entries.evict_detached()
    }

    fun release_all() {
        released = true
        on_quote_collapse = null
        entries.release_all()
    }
}

internal val local_body_view_holder = staticCompositionLocalOf<body_view_holder?> { null }
