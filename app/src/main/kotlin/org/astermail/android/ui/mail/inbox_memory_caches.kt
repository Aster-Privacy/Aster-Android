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

private const val inbox_memory_folder_limit = 6

private class inbox_lru<V>(private val limit: Int) : LinkedHashMap<String, V>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > limit
}

internal fun inbox_memory_key(account: String, folder: String): String = "$account|$folder"

internal object inbox_email_cache_store {
    private val caches = inbox_lru<inbox_email_cache>(inbox_memory_folder_limit)

    @Synchronized
    fun for_key(key: String): inbox_email_cache = caches.getOrPut(key) { inbox_email_cache() }

    @Synchronized
    fun clear() {
        caches.clear()
    }
}

internal class inbox_tab_rows_snapshot(
    val source: List<Email>,
    val categories_enabled: Boolean,
    val active_tabs: List<String>,
    val sort_mode: InboxSortMode,
    val grouping_enabled: Boolean,
    val cached_participants: Map<String, List<Pair<String, String>>>,
    val count_corrections: Map<String, ThreadCountCorrection>,
    val rows: TabThreadRows,
)

internal object inbox_tab_rows_store {
    private val snapshots = inbox_lru<inbox_tab_rows_snapshot>(inbox_memory_folder_limit)

    @Synchronized
    fun put(key: String, snapshot: inbox_tab_rows_snapshot) {
        snapshots[key] = snapshot
    }

    @Synchronized
    fun matching(
        key: String,
        source: List<Email>,
        categories_enabled: Boolean,
        active_tabs: List<String>,
        sort_mode: InboxSortMode,
        grouping_enabled: Boolean,
        cached_participants: Map<String, List<Pair<String, String>>>,
        count_corrections: Map<String, ThreadCountCorrection>,
    ): TabThreadRows? {
        val snapshot = snapshots[key] ?: return null
        if (snapshot.categories_enabled != categories_enabled) return null
        if (snapshot.sort_mode != sort_mode) return null
        if (snapshot.grouping_enabled != grouping_enabled) return null
        if (snapshot.active_tabs != active_tabs) return null
        if (!same_email_rows(snapshot.source, source)) return null
        if (snapshot.cached_participants !== cached_participants && snapshot.cached_participants != cached_participants) return null
        if (snapshot.count_corrections !== count_corrections && snapshot.count_corrections != count_corrections) return null
        return snapshot.rows
    }

    @Synchronized
    fun clear() {
        snapshots.clear()
    }
}

internal class email_rows_snapshot(val rows: List<Email>) {
    override fun equals(other: Any?): Boolean = other is email_rows_snapshot && same_email_rows(rows, other.rows)

    override fun hashCode(): Int = rows.size
}

fun clear_inbox_memory_caches() {
    inbox_email_cache_store.clear()
    inbox_tab_rows_store.clear()
    clear_inbox_order_hints()
}
