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

package org.astermail.android.ui.settings.mail_rules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.Adjustments
import compose.icons.tablericons.Archive
import compose.icons.tablericons.ArrowForward
import compose.icons.tablericons.Ban
import compose.icons.tablericons.Bell
import compose.icons.tablericons.Calendar
import compose.icons.tablericons.CalendarEvent
import compose.icons.tablericons.Clock
import compose.icons.tablericons.Code
import compose.icons.tablericons.CornerUpLeft
import compose.icons.tablericons.Dimensions
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Flag
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Hash
import compose.icons.tablericons.LetterCase
import compose.icons.tablericons.Mail
import compose.icons.tablericons.MailForward
import compose.icons.tablericons.MailOpened
import compose.icons.tablericons.Mailbox
import compose.icons.tablericons.Paperclip
import compose.icons.tablericons.Pin
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Rss
import compose.icons.tablericons.ShieldLock
import compose.icons.tablericons.Star
import compose.icons.tablericons.Tag
import compose.icons.tablericons.Trash
import compose.icons.tablericons.User
import compose.icons.tablericons.Users
import compose.icons.tablericons.X
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing

internal fun field_icon(field: field_id?): ImageVector = when (field) {
    null -> TablerIcons.Adjustments
    field_id.from -> TablerIcons.User
    field_id.reply_to -> TablerIcons.CornerUpLeft
    field_id.to, field_id.cc, field_id.bcc, field_id.any_recipient -> TablerIcons.Users
    field_id.subject -> TablerIcons.LetterCase
    field_id.body -> TablerIcons.FileText
    field_id.header -> TablerIcons.Code
    field_id.list_id, field_id.has_list_id -> TablerIcons.Rss
    field_id.attachment_name, field_id.has_attachment -> TablerIcons.Paperclip
    field_id.attachment_size, field_id.total_size -> TablerIcons.Dimensions
    field_id.recipient_count -> TablerIcons.Hash
    field_id.spam_score -> TablerIcons.Flag
    field_id.date_received -> TablerIcons.Calendar
    field_id.is_reply -> TablerIcons.CornerUpLeft
    field_id.is_forward -> TablerIcons.ArrowForward
    field_id.is_auto_submitted -> TablerIcons.Mail
    field_id.has_calendar_invite -> TablerIcons.CalendarEvent
    field_id.dkim_result, field_id.spf_result, field_id.dmarc_result -> TablerIcons.ShieldLock
}

internal fun action_icon(action: action_id?): ImageVector = when (action) {
    null -> TablerIcons.Ban
    action_id.move_to -> TablerIcons.Folder
    action_id.apply_labels -> TablerIcons.Tag
    action_id.mark_as -> TablerIcons.MailOpened
    action_id.star -> TablerIcons.Star
    action_id.skip_inbox -> TablerIcons.Archive
    action_id.pin -> TablerIcons.Pin
    action_id.snooze -> TablerIcons.Clock
    action_id.categorize -> TablerIcons.Mailbox
    action_id.notify -> TablerIcons.Bell
    action_id.forward -> TablerIcons.MailForward
    action_id.delete -> TablerIcons.Trash
    action_id.auto_reply -> TablerIcons.CornerUpLeft
}

@Composable
internal fun rule_step_row(
    icon: ImageVector,
    title: String,
    detail_prefix: String?,
    detail_value: String?,
    on_click: (() -> Unit)?,
    on_remove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val placeholder = stringResource(R.string.rules_value_placeholder_ellipsis)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (on_click != null) Modifier.clickable(role = Role.Button, onClick = on_click) else Modifier)
            .heightIn(min = 60.dp)
            .padding(start = AsterSpacing.lg, end = AsterSpacing.xs, top = AsterSpacing.sm, bottom = AsterSpacing.sm),
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
            if (detail_prefix != null || detail_value != null) {
                Text(
                    text = buildAnnotatedString {
                        if (detail_prefix != null) {
                            withStyle(SpanStyle(color = colors.text_tertiary)) { append(detail_prefix) }
                            if (detail_value != null) append(" ")
                        }
                        if (detail_value != null) {
                            if (detail_value.isBlank()) {
                                withStyle(SpanStyle(color = colors.text_tertiary)) { append(placeholder) }
                            } else {
                                withStyle(SpanStyle(color = colors.text_secondary, fontWeight = FontWeight.Medium)) {
                                    append(detail_value)
                                }
                            }
                        }
                    },
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (on_remove != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = on_remove),
            ) {
                Icon(
                    imageVector = TablerIcons.X,
                    contentDescription = stringResource(R.string.rules_remove),
                    tint = colors.text_tertiary,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(Modifier.width(AsterSpacing.md))
        }
    }
}

@Composable
internal fun rule_add_row(
    label: String,
    on_click: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = on_click)
            .heightIn(min = 52.dp)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = TablerIcons.Plus,
            contentDescription = null,
            tint = colors.accent_blue,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = colors.accent_blue,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun rule_connector(label: String, on_click: (() -> Unit)?) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg + 22.dp + AsterSpacing.md, end = AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border_secondary),
        )
        Text(
            text = label.uppercase(),
            color = colors.text_tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier
                .clip(CircleShape)
                .then(if (on_click != null) Modifier.clickable(role = Role.Button, onClick = on_click) else Modifier)
                .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.xs),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.border_secondary),
        )
    }
}

@Composable
internal fun rule_group_divider() {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AsterSpacing.lg + 22.dp + AsterSpacing.md)
            .height(1.dp)
            .background(colors.border_secondary),
    )
}
