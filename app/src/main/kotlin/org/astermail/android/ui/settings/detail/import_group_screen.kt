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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.AsterSpacing

internal const val import_tab_import = "import"
internal const val import_tab_external_accounts = "external_accounts"
internal const val import_tab_export = "export"

internal val import_group_tabs = listOf(import_tab_import, import_tab_external_accounts, import_tab_export)

internal fun import_group_tab_for_route(route_id: String): String = when (route_id) {
    "external_accounts", "external_accounts_gmail" -> import_tab_external_accounts
    "export" -> import_tab_export
    else -> import_tab_import
}

@Composable
fun ImportGroupScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
    initial_tab: String = import_tab_import,
    start_gmail_wizard: Boolean = false,
) {
    var active_tab by rememberSaveable {
        mutableStateOf(initial_tab.takeIf { it in import_group_tabs } ?: import_tab_import)
    }
    var open_gmail_wizard by rememberSaveable { mutableStateOf(start_gmail_wizard) }

    val open_from_import: (String) -> Unit = { id ->
        when (id) {
            "external_accounts" -> {
                open_gmail_wizard = false
                active_tab = import_tab_external_accounts
            }
            "external_accounts_gmail" -> {
                open_gmail_wizard = true
                active_tab = import_tab_external_accounts
            }
            "export" -> active_tab = import_tab_export
            else -> on_open(id)
        }
    }

    detail_scaffold(
        title = stringResource(R.string.import_title),
        on_back = on_back,
        scrollable = false,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AsterSpacing.lg)
                .padding(top = AsterSpacing.sm),
        ) {
            aster_segmented(
                value = active_tab,
                options = listOf(
                    switcher_option(import_tab_import, stringResource(R.string.import_title)),
                    switcher_option(import_tab_external_accounts, stringResource(R.string.external_accounts)),
                    switcher_option(import_tab_export, stringResource(R.string.export_label)),
                ),
                on_change = { tab ->
                    open_gmail_wizard = false
                    active_tab = tab
                },
                modifier = Modifier.testTag("import_group_tabs"),
            )
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (active_tab) {
                import_tab_external_accounts -> ExternalAccountsScreen(
                    on_back = on_back,
                    on_open = open_from_import,
                    start_gmail_wizard = open_gmail_wizard,
                    embedded = true,
                )
                import_tab_export -> ExportScreen(
                    on_back = on_back,
                    on_open = on_open,
                    embedded = true,
                )
                else -> ImportScreen(
                    on_back = on_back,
                    on_open = open_from_import,
                    embedded = true,
                )
            }
        }
    }
}
