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

data class sidebar_address_entry<T>(
    val item: T,
    val sidebar_order: Int?,
    val sidebar_hidden: Boolean,
)

fun <T> order_sidebar_addresses(entries: List<sidebar_address_entry<T>>): List<T> =
    entries.withIndex()
        .filter { !it.value.sidebar_hidden }
        .sortedWith(
            compareBy<IndexedValue<sidebar_address_entry<T>>>(
                { it.value.sidebar_order == null },
                { it.value.sidebar_order ?: 0 },
                { it.index },
            ),
        )
        .map { it.value.item }
