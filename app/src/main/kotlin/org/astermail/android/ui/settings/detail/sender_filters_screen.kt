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

import compose.icons.TablerIcons
import compose.icons.tablericons.Ban
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.CircleCheck
import compose.icons.tablericons.CloudDownload
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MailForward
import compose.icons.tablericons.MailOpened

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.mirror_in_rtl

@Composable
private fun filters_nav_row(
    title: String,
    subtitle: String,
    icon: ImageVector,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .heightIn(min = 58.dp)
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = colors.text_tertiary,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AsterSpacing.sm))
        Icon(
            imageVector = TablerIcons.ChevronRight,
            contentDescription = null,
            tint = colors.text_tertiary,
            modifier = Modifier.size(20.dp).mirror_in_rtl(),
        )
    }
}

@Composable
fun SenderFiltersScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    detail_scaffold(title = stringResource(R.string.mail_management), on_back = on_back) {
        section_label(stringResource(R.string.block_allow))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            filters_nav_row(
                title = stringResource(R.string.blocked_senders),
                subtitle = stringResource(R.string.senders_never_hear),
                icon = TablerIcons.Ban,
                on_click = { on_open("blocked") },
            )
            AsterDivider(modifier = Modifier)
            filters_nav_row(
                title = stringResource(R.string.allowlist),
                subtitle = stringResource(R.string.always_allow),
                icon = TablerIcons.CircleCheck,
                on_click = { on_open("allowlist") },
            )
            AsterDivider(modifier = Modifier)
            filters_nav_row(
                title = stringResource(R.string.subscriptions_label),
                subtitle = stringResource(R.string.mailing_lists_on),
                icon = TablerIcons.Mail,
                on_click = { on_open("subscriptions") },
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.filters_rules))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            filters_nav_row(
                title = stringResource(R.string.auto_forward),
                subtitle = stringResource(R.string.forward_matching),
                icon = TablerIcons.MailForward,
                on_click = { on_open("auto_forward") },
            )
            AsterDivider(modifier = Modifier)
            filters_nav_row(
                title = stringResource(R.string.settings_vacation_reply),
                subtitle = stringResource(R.string.vacation_reply_short),
                icon = TablerIcons.MailOpened,
                on_click = { on_open("vacation_reply") },
            )
        }
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.storage_data))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            filters_nav_row(
                title = stringResource(R.string.export_label),
                subtitle = stringResource(R.string.export_your_mail),
                icon = TablerIcons.CloudDownload,
                on_click = { on_open("export") },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}
