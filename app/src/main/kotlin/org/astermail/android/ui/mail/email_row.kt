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
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.inter_family
import org.astermail.android.mail.MailViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmailRow(
    email: Email,
    on_click: () -> Unit,
    on_long_click: () -> Unit,
    on_toggle_star: () -> Unit,
    modifier: Modifier = Modifier,
    is_pinned: Boolean = false,
    haptic_enabled: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val is_unread = !email.is_read
    val sender_color = if (is_unread) colors.text_primary else colors.text_secondary
    val subject_color = if (is_unread) colors.text_primary else colors.text_secondary
    val preview_color = if (is_unread) colors.text_secondary else colors.text_muted
    val row_bg = colors.bg_primary.copy(alpha = 0.6f)
    val yesterday_label = stringResource(R.string.yesterday)
    val relative_time = remember(email.received_at, yesterday_label) {
        email.received_at.format_relative_time(yesterday_label)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(row_bg)
            .combinedClickable(
                onClick = on_click,
                onLongClick = {
                    if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    on_long_click()
                },
            )
            .defaultMinSize(minHeight = 80.dp)
            .padding(
                start = AsterSpacing.lg,
                end = AsterSpacing.lg,
                top = AsterSpacing.lg,
                bottom = AsterSpacing.lg,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        SenderAvatar(email = email.sender_email, name = email.sender_name)

        Spacer(Modifier.width(AsterSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = email.sender_name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = sender_color,
                    fontWeight = if (is_unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = relative_time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (is_unread) colors.text_primary else colors.text_muted,
                    fontSize = 12.sp,
                    fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = AsterSpacing.sm),
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = email.subject,
                style = MaterialTheme.typography.bodyMedium,
                color = subject_color,
                fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = email.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = preview_color,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (is_pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.pinned),
                            tint = colors.accent_blue,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (email.has_attachment) {
                        Icon(
                            imageVector = Icons.Filled.AttachFile,
                            contentDescription = stringResource(R.string.has_attachment),
                            tint = colors.text_tertiary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    star_button(
                        is_starred = email.is_starred,
                        on_toggle = {
                            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            on_toggle_star()
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun star_button(is_starred: Boolean, on_toggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val tint: Color = if (is_starred) Color(0xFFFBBF24) else colors.text_muted
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .combinedClickable(onClick = on_toggle, onLongClick = on_toggle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (is_starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (is_starred) stringResource(R.string.starred) else stringResource(R.string.not_starred),
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThreadInboxRow(
    thread: ThreadRow,
    on_click: () -> Unit,
    on_long_click: () -> Unit,
    on_toggle_star: () -> Unit,
    modifier: Modifier = Modifier,
    is_selected: Boolean = false,
    is_pinned: Boolean = false,
    attachment_chips: List<MailViewModel.InboxAttachmentChip> = emptyList(),
    haptic_enabled: Boolean = true,
    row_index: Int = 0,
) {
    val email = thread.newest
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val is_unread = thread.has_unread
    val sender_color = if (is_unread) colors.text_primary else colors.text_secondary
    val subject_color = if (is_unread) colors.text_primary else colors.text_secondary
    val preview_color = if (is_unread) colors.text_secondary else colors.text_muted
    val row_bg = when {
        is_selected -> colors.accent_blue.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val yesterday_label = stringResource(R.string.yesterday)
    val relative_time = remember(email.received_at, yesterday_label) {
        email.received_at.format_relative_time(yesterday_label)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(row_bg),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = on_click,
                onLongClick = {
                    if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    on_long_click()
                },
            )
            .defaultMinSize(minHeight = 80.dp)
            .padding(
                start = AsterSpacing.lg,
                end = AsterSpacing.lg,
                top = AsterSpacing.lg,
                bottom = AsterSpacing.lg,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (is_selected) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.selected),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            val participants = androidx.compose.runtime.remember(
                thread.thread_id, thread.participants, email.sender_name, email.sender_email,
            ) {
                thread.participants.ifEmpty {
                    listOf(email.sender_name to email.sender_email)
                }
            }
            StackedAvatars(participants = participants)
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = format_participants(
                        thread.participants,
                        email.sender_name,
                        others_template = stringResource(R.string.participants_and_others),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = sender_color,
                    fontWeight = if (is_unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (thread.has_attachment) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = stringResource(R.string.has_attachment),
                        tint = colors.text_tertiary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = relative_time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (is_unread) colors.text_primary else colors.text_muted,
                    fontSize = 12.sp,
                    fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = AsterSpacing.sm),
                )
            }
            Spacer(Modifier.height(2.dp))
            val subject_text = if (thread.message_count > 1) {
                stringResource(R.string.inbox_subject_with_count, email.subject.ifBlank { stringResource(R.string.inbox_no_subject) }, thread.message_count)
            } else {
                email.subject.ifBlank { stringResource(R.string.inbox_no_subject) }
            }
            Text(
                text = subject_text,
                style = MaterialTheme.typography.bodyMedium,
                color = subject_color,
                fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = email.preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = preview_color,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (is_pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.pinned),
                            tint = colors.accent_blue,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    star_button_public(
                        is_starred = thread.is_starred,
                        on_toggle = {
                            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            on_toggle_star()
                        },
                        modifier = Modifier.testTag("star_$row_index"),
                    )
                }
            }
            if (thread.label_colors.isNotEmpty() || attachment_chips.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Spacer(Modifier.weight(1f))
                    attachment_chips.forEach { chip ->
                        inbox_attachment_chip(chip)
                    }
                    thread.label_colors.indices.forEach { i ->
                        label_chip(
                            color = thread.label_colors[i],
                            name = thread.label_names.getOrElse(i) { "" },
                            icon = thread.label_icons.getOrElse(i) { "" },
                        )
                    }
                }
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 64.dp)
            .height(0.5.dp)
            .background(colors.border_secondary.copy(alpha = 0.4f))
            .align(Alignment.BottomStart),
    )
    }
}

private fun format_participants(
    participants: List<Pair<String, String>>,
    fallback: String,
    others_template: String,
): String {
    if (participants.isEmpty()) return fallback
    val names = participants.map { it.first.ifBlank { it.second.substringBefore('@').ifBlank { it.second } } }
    return when (names.size) {
        1 -> names[0]
        2 -> "${first_name(names[0])}, ${first_name(names[1])}"
        else -> try {
            String.format(others_template, first_name(names[0]), names.size - 1)
        } catch (_: Throwable) {
            "${first_name(names[0])} +${names.size - 1}"
        }
    }
}

private fun first_name(full: String): String {
    val trimmed = full.trim()
    val space = trimmed.indexOf(' ')
    return if (space > 0) trimmed.substring(0, space) else trimmed
}

@Composable
private fun label_chip(color: Color, name: String, icon: String) {
    val colors = AsterMaterial.colors
    val icon_vector = if (icon.isNotBlank()) material_icon_from_name(icon) else null
    val has_name = name.isNotBlank()
    val shape = SquircleShape(8.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colors.border_primary, shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon_vector != null) {
            Icon(
                imageVector = icon_vector,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color, shape = CircleShape),
            )
        }
        if (has_name) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = colors.text_secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = inter_family,
            )
        }
    }
}

private fun material_icon_from_name(name: String) = when (name.trim()) {
    "clock" -> Icons.Filled.Schedule
    "archive" -> Icons.Filled.Archive
    "trash" -> Icons.Filled.Delete
    "send" -> Icons.AutoMirrored.Filled.Send
    "draft" -> Icons.Filled.Drafts
    "star" -> Icons.Filled.Star
    "flag" -> Icons.Filled.Flag
    "bolt" -> Icons.Filled.Bolt
    "shield" -> Icons.Filled.Security
    "warning" -> Icons.Filled.Warning
    "check" -> Icons.Filled.CheckCircle
    "tag" -> Icons.AutoMirrored.Filled.Label
    "folder" -> Icons.Filled.Folder
    "envelope" -> Icons.Filled.Email
    "lock" -> Icons.Filled.Lock
    "bell" -> Icons.Filled.Notifications
    "sparkles" -> Icons.Filled.AutoAwesome
    "fire" -> Icons.Filled.Whatshot
    "heart" -> Icons.Filled.Favorite
    "bookmark" -> Icons.Filled.Bookmark
    "chat" -> Icons.AutoMirrored.Filled.Chat
    "document" -> Icons.Filled.Description
    "currency" -> Icons.Filled.AttachMoney
    "cart" -> Icons.Filled.ShoppingCart
    "code" -> Icons.Filled.Code
    "user" -> Icons.Filled.Person
    "building" -> Icons.Filled.Business
    "globe" -> Icons.Filled.Language
    "info" -> Icons.Filled.Info
    "eye-slash" -> Icons.Filled.VisibilityOff
    else -> null
}

@Composable
private fun inbox_attachment_chip(chip: MailViewModel.InboxAttachmentChip) {
    val colors = AsterMaterial.colors
    val type_color = chip_type_color(chip.content_type)
    Row(
        modifier = Modifier
            .clip(SquircleShape(8.dp))
            .background(type_color.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.AttachFile,
            contentDescription = null,
            tint = type_color,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = chip.filename,
            style = MaterialTheme.typography.labelSmall,
            color = colors.text_secondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = inter_family,
        )
    }
}

private fun chip_type_color(content_type: String): Color {
    return when {
        content_type == "application/pdf" -> Color(0xFFEA4335)
        content_type.startsWith("image/") -> Color(0xFFA855F7)
        content_type.startsWith("video/") -> Color(0xFFEC4899)
        content_type.startsWith("audio/") -> Color(0xFF0EA5E9)
        content_type.contains("spreadsheet") || content_type.contains("excel") ||
            content_type == "text/csv" -> Color(0xFF34A853)
        content_type.contains("presentation") || content_type.contains("powerpoint") -> Color(0xFFF97316)
        content_type.contains("word") || content_type.contains("document") -> Color(0xFF4285F4)
        content_type.contains("zip") || content_type.contains("gzip") ||
            content_type.contains("tar") -> Color(0xFF8B5CF6)
        content_type.startsWith("text/") -> Color(0xFF3B82F6)
        else -> Color(0xFF6B7280)
    }
}

@Composable
private fun thread_count_pill(count: Int) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(999.dp))
            .background(colors.bg_secondary)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = count.toString(),
            color = colors.text_secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = inter_family,
        )
    }
}

@Composable
internal fun avatar_bubble_public(name: String, email_address: String) {
    SenderAvatar(email = email_address, name = name)
}

@Composable
internal fun star_button_public(is_starred: Boolean, on_toggle: () -> Unit, modifier: Modifier = Modifier) {
    star_button(is_starred = is_starred, on_toggle = on_toggle, modifier = modifier)
}

@Suppress("unused")
@Composable
fun spacer_for_row_height() {
    Spacer(modifier = Modifier.height(76.dp))
}

@Suppress("unused")
fun local_content_color_placeholder(): Color = Color.Unspecified.also { LocalContentColor }
