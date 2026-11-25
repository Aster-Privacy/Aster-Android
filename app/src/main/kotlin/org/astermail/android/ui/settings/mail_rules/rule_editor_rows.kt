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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
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
import compose.icons.tablericons.ChevronDown
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
import compose.icons.tablericons.Rss
import compose.icons.tablericons.ShieldLock
import compose.icons.tablericons.Star
import compose.icons.tablericons.Tag
import compose.icons.tablericons.Trash
import compose.icons.tablericons.User
import compose.icons.tablericons.Users
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterShapes
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.aster_menu
import org.astermail.android.design.field_surface_color

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
internal fun rule_dropdown(
    value: String,
    expanded: Boolean,
    on_expand: () -> Unit,
    on_dismiss: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    test_tag: String? = null,
    menu: @Composable ColumnScope.() -> Unit,
) {
    val colors = AsterMaterial.colors
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(AsterShapes.control)
                .background(field_surface_color(colors), AsterShapes.control)
                .clickable(enabled = enabled, role = Role.DropdownList, onClick = on_expand)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .let { if (test_tag != null) it.testTag(test_tag) else it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.text_secondary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
            }
            Text(
                text = value,
                color = when {
                    !enabled -> colors.text_muted
                    placeholder -> colors.text_muted
                    else -> colors.text_primary
                },
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (enabled) {
                Spacer(Modifier.width(AsterSpacing.xs))
                Icon(
                    imageVector = TablerIcons.ChevronDown,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        aster_menu(expanded = expanded, on_dismiss = on_dismiss, content = menu)
    }
}
