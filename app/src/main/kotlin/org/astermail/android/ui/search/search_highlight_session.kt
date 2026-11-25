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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

internal data class search_hit_location(val message_key: String?, val local_index: Int)

@Stable
internal class search_highlight_session(
    val terms: search_highlight_terms,
    val focus_key: String,
) {
    var dismissed by mutableStateOf(false)
        private set
    var current by mutableIntStateOf(-1)
    var subject_count by mutableIntStateOf(0)
    var order by mutableStateOf<List<String>>(emptyList())
    var initial_done by mutableStateOf(false)
    val counts = mutableStateMapOf<String, Int>()
    private val revealers = HashMap<String, suspend (Int) -> Float?>()
    private val ready_keys = mutableStateMapOf<String, Boolean>()

    val body_total: Int get() = order.sumOf { counts[it] ?: 0 }
    val total: Int get() = if (dismissed) 0 else subject_count + body_total
    val active: Boolean get() = !dismissed && !terms.is_empty

    private val measured_keys = HashSet<String>()

    fun report_count(key: String, count: Int) {
        measured_keys.add(key)
        if (count <= 0) counts.remove(key) else counts[key] = count
    }

    fun seed_count(key: String, count: Int) {
        if (key in measured_keys) return
        if (count <= 0) counts.remove(key) else counts[key] = count
    }

    fun is_measured(key: String): Boolean = key in measured_keys

    fun register(key: String, reveal: suspend (Int) -> Float?) {
        revealers[key] = reveal
        ready_keys[key] = true
    }

    fun unregister(key: String) {
        revealers.remove(key)
        ready_keys.remove(key)
    }

    fun is_ready(key: String): Boolean = ready_keys[key] == true

    fun locate(global: Int): search_hit_location? {
        if (global < 0) return null
        if (global < subject_count) return search_hit_location(null, global)
        var rest = global - subject_count
        for (key in order) {
            val count = counts[key] ?: 0
            if (rest < count) return search_hit_location(key, rest)
            rest -= count
        }
        return null
    }

    fun global_index_of(key: String, local: Int): Int {
        var base = subject_count
        for (other in order) {
            if (other == key) return base + local
            base += counts[other] ?: 0
        }
        return -1
    }

    fun first_index_in(key: String): Int = if ((counts[key] ?: 0) > 0) global_index_of(key, 0) else -1

    fun step(delta: Int): Int {
        val size = total
        if (size <= 0) return -1
        val next = if (current < 0) {
            if (delta >= 0) 0 else size - 1
        } else {
            ((current + delta) % size + size) % size
        }
        current = next
        return next
    }

    suspend fun reveal(key: String, local: Int): Float? = revealers[key]?.invoke(local)

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        current = -1
    }
}

internal val local_search_highlight = staticCompositionLocalOf<search_highlight_session?> { null }

internal val local_search_row_terms = staticCompositionLocalOf<search_highlight_terms?> { null }
