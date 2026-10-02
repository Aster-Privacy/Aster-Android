//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.mail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.astermail.android.R
import org.astermail.android.api.preferences.CustomCategoryRule
import org.astermail.android.mail.CATEGORY_TABS
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.active_category_tabs
import org.astermail.android.mail.category_entries
import org.astermail.android.mail.category_for_tab
import org.astermail.android.mail.sanitize_custom_categories
import org.astermail.android.ui.common.category_icon_for
import org.astermail.android.ui.settings.mail_rules.pickers.base_sheet
import org.astermail.android.ui.settings.mail_rules.pickers.row_select

private val category_move_item_types = setOf(null, "received")

fun can_move_to_category(item: InboxItem?, categories_enabled: Boolean): Boolean {
    if (!categories_enabled || item == null) return false
    if (item.is_trashed || item.is_spam || item.is_archived) return false
    return item.raw_item.item_type in category_move_item_types
}

fun category_move_tabs(
    enabled_categories: List<String>?,
    custom_categories: List<CustomCategoryRule>,
    custom_category_limit: Int,
): List<String> = if (enabled_categories == null) {
    CATEGORY_TABS
} else {
    active_category_tabs(enabled_categories, sanitize_custom_categories(custom_categories), custom_category_limit)
}

@Composable
fun move_to_category_sheet(
    current_category: String,
    active_tabs: List<String>,
    custom_categories: List<CustomCategoryRule>,
    on_pick: (String) -> Unit,
    on_close: () -> Unit,
) {
    val selected = category_for_tab(current_category, active_tabs)
    val entries = category_entries(active_tabs, sanitize_custom_categories(custom_categories))
    base_sheet(on_dismiss = on_close, title = stringResource(R.string.move_to_category)) {
        Column(modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            entries.forEach { entry ->
                row_select(
                    label = entry.label,
                    selected = entry.id == selected,
                    on_click = {
                        on_close()
                        if (entry.id != selected) on_pick(entry.id)
                    },
                    test_tag = "category_${entry.id}",
                    icon = category_icon_for(entry.icon),
                )
            }
        }
    }
}
