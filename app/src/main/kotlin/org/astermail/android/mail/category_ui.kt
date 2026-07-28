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

package org.astermail.android.mail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.astermail.android.api.preferences.CustomCategoryRule

data class CategoryEntry(
    val id: String,
    val label: String,
    val icon: String,
)

@Composable
fun category_entries(
    active_tabs: List<String>,
    custom_categories: List<CustomCategoryRule>,
): List<CategoryEntry> {
    val entries = mutableListOf<CategoryEntry>()
    for (id in active_tabs) {
        val builtin = builtin_category(id)
        if (builtin != null) {
            entries.add(CategoryEntry(id, stringResource(builtin.label_res), builtin.icon))
            continue
        }
        val custom = custom_categories.firstOrNull { it.id == id } ?: continue
        val name = custom.name.trim().take(MAX_CUSTOM_CATEGORY_NAME)
        if (name.isEmpty()) continue
        entries.add(CategoryEntry(id, name, custom.icon))
    }
    return entries
}

@Composable
fun category_labels(
    active_tabs: List<String>,
    custom_categories: List<CustomCategoryRule>,
): Map<String, String> =
    category_entries(active_tabs, custom_categories).associate { it.id to it.label }
