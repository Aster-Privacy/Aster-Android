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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Copy
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.api.settings.DnsRecord
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.DialogConfirmStyle

internal val dkim_notice_fill = Color(0xFFFACC15)
internal val dkim_notice_field_fill = Color(0xFFFDE047)
internal val dkim_notice_heading_ink = Color(0xFF1C1917)
internal val dkim_notice_body_ink = Color(0xFF292524)

@Composable
internal fun dkim_rotate_confirm_dialog(
    domain_name: String,
    is_aster_managed: Boolean,
    on_dismiss: () -> Unit,
    on_confirm: () -> Unit,
) {
    AsterAlertDialog(
        on_dismiss = on_dismiss,
        title = stringResource(R.string.domain_dkim_rotate_confirm_title),
        message = stringResource(
            if (is_aster_managed) {
                R.string.domain_dkim_rotate_confirm_purchased
            } else {
                R.string.domain_dkim_rotate_confirm_manual
            },
            domain_name,
        ),
        confirm_label = stringResource(R.string.domain_dkim_rotate_action),
        cancel_label = stringResource(R.string.cancel),
        confirm_style = if (is_aster_managed) DialogConfirmStyle.primary else DialogConfirmStyle.destructive,
        on_confirm = on_confirm,
    )
}

@Composable
internal fun dkim_rotation_notice(
    record: DnsRecord,
    on_copy: (String, String) -> Unit,
    on_dismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(dkim_notice_fill, AsterShapes.island)
            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = AsterSpacing.xs, bottom = AsterSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = TablerIcons.AlertTriangle,
                contentDescription = null,
                tint = dkim_notice_heading_ink,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(AsterSpacing.sm))
            Text(
                text = stringResource(R.string.domain_dkim_notice_title),
                color = dkim_notice_heading_ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            AsterIconButton(
                icon = TablerIcons.X,
                content_description = stringResource(R.string.close),
                onClick = on_dismiss,
                tint = dkim_notice_heading_ink,
                icon_size = 18,
            )
        }
        Column(modifier = Modifier.padding(end = AsterSpacing.md)) {
            Text(
                text = stringResource(R.string.domain_dkim_notice_body),
                color = dkim_notice_body_ink,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(AsterSpacing.sm))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(dkim_notice_field_fill, AsterShapes.control)
                    .padding(start = AsterSpacing.md, top = AsterSpacing.xs, bottom = AsterSpacing.xs),
            ) {
                Text(
                    text = record.type.uppercase(),
                    color = dkim_notice_heading_ink,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = AsterSpacing.xs),
                )
                dkim_notice_field(
                    label = stringResource(R.string.domain_record_host),
                    value = record.name,
                    on_copy = { on_copy(record.type, record.name) },
                )
                dkim_notice_field(
                    label = stringResource(R.string.domain_record_value),
                    value = record.value,
                    on_copy = { on_copy(record.type, record.value) },
                )
            }
        }
    }
}

@Composable
private fun dkim_notice_field(label: String, value: String, on_copy: () -> Unit) {
    var value_expanded by remember(value) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            color = dkim_notice_body_ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(48.dp).padding(top = 2.dp),
        )
        Text(
            text = value,
            color = dkim_notice_heading_ink,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = if (value_expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp)
                .clickable { value_expanded = !value_expanded },
        )
        AsterIconButton(
            icon = TablerIcons.Copy,
            content_description = stringResource(R.string.copy_to_clipboard),
            onClick = on_copy,
            tint = dkim_notice_heading_ink,
        )
    }
}
