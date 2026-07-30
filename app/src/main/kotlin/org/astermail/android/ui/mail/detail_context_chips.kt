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

package org.astermail.android.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Archive
import compose.icons.tablericons.Clock
import compose.icons.tablericons.FileText
import compose.icons.tablericons.Folder
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.Lock
import compose.icons.tablericons.Send
import compose.icons.tablericons.Trash
import org.astermail.android.R
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.tags.TagItem
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.SquircleShape
import org.astermail.android.folders.is_folder_protected
import org.astermail.android.looks_encrypted
import org.astermail.android.mail.InboxItem

internal data class detail_folder_chip_data(
    val name: String,
    val icon: ImageVector,
    val tint_kind: String,
)

internal fun detail_custom_folder(item: InboxItem, folders: List<LabelItem>): LabelItem? =
    folders.firstOrNull { label ->
        label.folder_type == "folder" &&
            label.label_token in item.labels &&
            !label.encrypted_name.isNullOrBlank() &&
            !looks_encrypted(label.encrypted_name)
    }

internal fun detail_system_folder_id(item: InboxItem): String = when {
    item.is_archived -> "archive"
    item.raw_item.item_type == "draft" -> "drafts"
    item.raw_item.item_type == "scheduled" -> "scheduled"
    item.raw_item.item_type == "outbox" -> "scheduled"
    item.raw_item.item_type == "sent" -> "sent"
    else -> "inbox"
}

internal fun detail_system_folder_icon(folder_id: String): ImageVector = when (folder_id) {
    "trash" -> TablerIcons.Trash
    "spam" -> TablerIcons.AlertTriangle
    "archive" -> TablerIcons.Archive
    "drafts" -> TablerIcons.FileText
    "scheduled" -> TablerIcons.Clock
    "sent" -> TablerIcons.Send
    else -> TablerIcons.Inbox
}

@Composable
internal fun detail_folder_chip_for(
    item: InboxItem?,
    folders: List<LabelItem>,
    is_spam: Boolean,
    is_trashed: Boolean,
): detail_folder_chip_data? {
    if (item == null) return null
    if (is_trashed) {
        return detail_folder_chip_data(
            name = stringResource(R.string.folder_trash),
            icon = detail_system_folder_icon("trash"),
            tint_kind = "danger",
        )
    }
    if (is_spam) {
        return detail_folder_chip_data(
            name = stringResource(R.string.folder_spam),
            icon = detail_system_folder_icon("spam"),
            tint_kind = "warning",
        )
    }
    val custom = detail_custom_folder(item, folders)
    if (custom != null) {
        return detail_folder_chip_data(
            name = custom.encrypted_name.orEmpty(),
            icon = if (is_folder_protected(custom)) TablerIcons.Lock else TablerIcons.Folder,
            tint_kind = "accent",
        )
    }
    val folder_id = detail_system_folder_id(item)
    return detail_folder_chip_data(
        name = folder_display_name(folder_id),
        icon = detail_system_folder_icon(folder_id),
        tint_kind = "accent",
    )
}

@Composable
internal fun detail_folder_chip(data: detail_folder_chip_data, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val tint = when (data.tint_kind) {
        "danger" -> colors.danger
        "warning" -> colors.warning
        else -> colors.accent_blue
    }
    val shape = RoundedCornerShape(6.dp)
    val background = chip_background(tint, colors.bg_primary, colors.is_dark)
    val content = chip_content(tint, background, colors.is_dark)
    Row(
        modifier = modifier
            .testTag("detail_folder_chip")
            .clip(shape)
            .background(background, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = data.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = data.name,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

@Composable
internal fun detail_label_chip(tag: TagItem, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val tag_color = parse_tag_color(tag.encrypted_color) ?: colors.accent_blue
    val icon_name = tag.encrypted_icon.orEmpty()
    val icon_vector = if (icon_name.isNotBlank() && !looks_encrypted(icon_name)) {
        material_icon_from_name(icon_name)
    } else {
        null
    }
    val shape = RoundedCornerShape(6.dp)
    val background = chip_background(tag_color, colors.bg_primary, colors.is_dark)
    val content = chip_content(tag_color, background, colors.is_dark)
    Row(
        modifier = modifier
            .testTag("detail_label_chip")
            .clip(shape)
            .background(background, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon_vector != null) {
            Icon(
                imageVector = icon_vector,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp),
            )
        }
        Text(
            text = tag.encrypted_name,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 160.dp),
        )
    }
}

internal fun parse_tag_color(value: String?): Color? = try {
    value?.takeIf { it.isNotBlank() }?.let { Color(android.graphics.Color.parseColor(it)) }
} catch (_: Throwable) {
    null
}
