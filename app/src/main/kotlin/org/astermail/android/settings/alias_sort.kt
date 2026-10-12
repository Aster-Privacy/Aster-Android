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

package org.astermail.android.settings

import java.text.Collator
import java.util.Locale

enum class AliasSort(val key: String) {
    Newest("newest"),
    Oldest("oldest"),
    AddressAz("az"),
    AddressZa("za");

    companion object {
        fun from_key(key: String?): AliasSort = entries.firstOrNull { it.key == key } ?: Newest
    }
}

const val alias_sort_prefs_name = "aster_alias_list"
const val alias_sort_pref_key = "sort"

internal fun alias_created_millis(value: String): Long =
    runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching {
            java.time.LocalDateTime.parse(value.take(19)).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }.getOrNull()
        ?: 0L

private val natural_chunk = Regex("\\d+|\\D+")

internal fun alias_address_comparator(locale: Locale): Comparator<String> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return Comparator { a, b ->
        val left = natural_chunk.findAll(a).map { it.value }.toList()
        val right = natural_chunk.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val result = if (x[0].isDigit() && y[0].isDigit()) {
                val nx = x.trimStart('0')
                val ny = y.trimStart('0')
                if (nx.length != ny.length) nx.length.compareTo(ny.length) else nx.compareTo(ny)
            } else {
                collator.compare(x, y)
            }
            if (result != 0) return@Comparator result
        }
        left.size.compareTo(right.size)
    }
}

private class SortKey<T>(val item: T, val pinned: Boolean, val created: Long, val address: String)

fun <T> sort_aliases(
    items: List<T>,
    sort: AliasSort,
    locale: Locale,
    address: (T) -> String,
    created_at: (T) -> String,
    pinned: (T) -> Boolean = { false },
): List<T> {
    if (items.size < 2) return items
    val addresses = alias_address_comparator(locale)
    val by_address = Comparator<SortKey<T>> { a, b -> addresses.compare(a.address, b.address) }
    val newest = compareByDescending<SortKey<T>> { it.created }
    val order = when (sort) {
        AliasSort.Newest -> newest.then(by_address)
        AliasSort.Oldest -> compareBy<SortKey<T>> { it.created }.then(by_address)
        AliasSort.AddressAz -> by_address.then(newest)
        AliasSort.AddressZa -> by_address.reversed().then(newest)
    }
    return items
        .map { SortKey(it, pinned(it), alias_created_millis(created_at(it)), address(it)) }
        .sortedWith(compareByDescending<SortKey<T>> { it.pinned }.then(order))
        .map { it.item }
}
