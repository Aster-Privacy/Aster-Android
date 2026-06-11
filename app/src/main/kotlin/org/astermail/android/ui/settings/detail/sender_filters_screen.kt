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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Unsubscribe
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider

@Composable
fun SenderFiltersScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    detail_scaffold(title = stringResource(R.string.mail_management), on_back = on_back) {
        section_label(stringResource(R.string.filters_rules))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(title = stringResource(R.string.filters), subtitle = stringResource(R.string.rules_sort_mail), icon = Icons.Outlined.FilterAlt, on_click = { on_open("filters") })
            AsterDivider(modifier = Modifier)
            detail_row(title = stringResource(R.string.auto_forward), subtitle = stringResource(R.string.forward_matching), icon = Icons.Outlined.MarkEmailRead, on_click = { on_open("auto_forward") })
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.block_allow))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(title = stringResource(R.string.blocked_senders), subtitle = stringResource(R.string.senders_never_hear), icon = Icons.Outlined.Block, on_click = { on_open("blocked") })
            AsterDivider(modifier = Modifier)
            detail_row(title = stringResource(R.string.allowlist), subtitle = stringResource(R.string.always_allow), icon = Icons.Outlined.CheckCircle, on_click = { on_open("allowlist") })
            AsterDivider(modifier = Modifier)
            detail_row(title = stringResource(R.string.subscriptions_label), subtitle = stringResource(R.string.mailing_lists_on), icon = Icons.Outlined.Unsubscribe, on_click = { on_open("subscriptions") })
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.storage_data))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(title = stringResource(R.string.storage_title), subtitle = stringResource(R.string.see_using_space), icon = Icons.Outlined.Storage, on_click = { on_open("storage") })
            AsterDivider(modifier = Modifier)
            detail_row(title = stringResource(R.string.import_label), subtitle = stringResource(R.string.import_from_providers), icon = Icons.Outlined.CloudDownload, on_click = { on_open("import") })
            AsterDivider(modifier = Modifier)
            detail_row(title = stringResource(R.string.export_label), subtitle = stringResource(R.string.export_your_mail), icon = Icons.Outlined.CloudDownload, on_click = { on_open("export") })
        }
        v_gap(AsterSpacing.xxl)
    }
}
