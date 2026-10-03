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


package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.mail.active_category_tabs
import org.astermail.android.mail.builtin_category
import org.astermail.android.mail.launch_folder_category
import org.astermail.android.mail.launch_folder_for_category
import org.astermail.android.mail.launch_folder_inbox
import org.astermail.android.mail.launch_folder_store
import org.astermail.android.mail.launch_folder_system_ids
import org.astermail.android.ui.settings.mail_rules.pickers.base_sheet

internal data class launch_folder_option(
    val id: String,
    val label: String,
    val subtitle: String? = null,
)

private fun launch_folder_label_res(id: String): Int = when (id) {
    "all" -> R.string.folder_all_mail
    "starred" -> R.string.folder_starred
    "sent" -> R.string.folder_sent
    "drafts" -> R.string.folder_drafts
    "archive" -> R.string.folder_archive
    else -> R.string.folder_inbox
}

internal fun launch_folder_tabs(categories_enabled: Boolean, enabled_categories: List<String>): List<String> =
    if (categories_enabled) {
        active_category_tabs(enabled_categories, emptyList(), 0).filter { it != "primary" }
    } else {
        emptyList()
    }

internal fun effective_launch_folder(stored: String, category_tabs: List<String>): String {
    val category = launch_folder_category(stored) ?: return stored
    return if (category in category_tabs) stored else launch_folder_inbox
}

@Composable
internal fun launch_folder_options(category_tabs: List<String>): List<launch_folder_option> {
    val inbox = stringResource(R.string.folder_inbox)
    val options = mutableListOf(launch_folder_option(launch_folder_inbox, inbox))
    for (tab in category_tabs) {
        val builtin = builtin_category(tab) ?: continue
        options.add(launch_folder_option(launch_folder_for_category(tab), stringResource(builtin.label_res), inbox))
    }
    for (id in launch_folder_system_ids) {
        if (id == launch_folder_inbox) continue
        options.add(launch_folder_option(id, stringResource(launch_folder_label_res(id))))
    }
    return options
}

@Composable
internal fun launch_folder_value_label(value: String): String {
    val category = launch_folder_category(value)
    val inbox = stringResource(R.string.folder_inbox)
    if (category != null) {
        val builtin = builtin_category(category) ?: return inbox
        return inbox + " · " + stringResource(builtin.label_res)
    }
    return stringResource(launch_folder_label_res(value))
}

@Composable
internal fun launch_folder_row(categories_enabled: Boolean, enabled_categories: List<String>) {
    val context = LocalContext.current
    var stored by remember { mutableStateOf(launch_folder_store.load(context)) }
    var show_picker by rememberSaveable { mutableStateOf(false) }
    val category_tabs = launch_folder_tabs(categories_enabled, enabled_categories)
    val selected = effective_launch_folder(stored, category_tabs)
    Column(modifier = Modifier.testTag("launch_folder_row")) {
        detail_row(
            title = stringResource(R.string.launch_folder),
            subtitle = launch_folder_value_label(selected),
            on_click = { show_picker = true },
        )
    }
    if (show_picker) {
        launch_folder_picker(
            options = launch_folder_options(category_tabs),
            selected = selected,
            on_pick = { id ->
                launch_folder_store.save(context, id)
                stored = id
                show_picker = false
            },
            on_dismiss = { show_picker = false },
        )
    }
}

@Composable
internal fun launch_folder_picker(
    options: List<launch_folder_option>,
    selected: String,
    on_pick: (String) -> Unit,
    on_dismiss: () -> Unit,
) {
    val colors = AsterMaterial.colors
    base_sheet(on_dismiss = on_dismiss, title = stringResource(R.string.launch_folder)) {
        Text(
            text = stringResource(R.string.launch_folder_hint),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(
                start = AsterSpacing.lg,
                end = AsterSpacing.lg,
                bottom = AsterSpacing.sm,
            ),
        )
        Column(
            modifier = Modifier
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            options.forEach { option ->
                choice_option_row(
                    label = option.label,
                    selected = option.id == selected,
                    subtitle = option.subtitle,
                    test_tag = "launch_folder_${option.id}",
                    on_click = { on_pick(option.id) },
                )
            }
        }
    }
}
