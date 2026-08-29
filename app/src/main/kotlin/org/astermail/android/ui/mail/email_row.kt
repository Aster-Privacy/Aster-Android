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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import org.astermail.android.ui.icons.pin_icon_filled

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.mail.MailViewModel
import org.astermail.android.api.preferences.UserPreferences

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun inbox_leading_slot(
    show_pictures: Boolean,
    is_selected: Boolean,
    avatar_size: androidx.compose.ui.unit.Dp,
    avatar: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    if (is_selected) {
        Box(
            modifier = Modifier
                .size(if (show_pictures) avatar_size else 24.dp)
                .clip(CircleShape)
                .background(colors.accent_blue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = stringResource(R.string.selected),
                tint = Color.White,
                modifier = Modifier.size(if (show_pictures) 20.dp else 16.dp),
            )
        }
    } else if (show_pictures) {
        avatar()
    } else {
        return
    }
    Spacer(Modifier.width(AsterSpacing.md))
}

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
    is_first: Boolean = true,
    is_last: Boolean = true,
    is_selected: Boolean = false,
    select_mode: Boolean = false,
    list_density: String? = null,
    show_sender_pictures: Boolean = true,
    show_email_preview: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val metrics = remember(list_density) { inbox_row_metrics(list_density) }
    val is_unread = !email.is_read
    val sender_color = if (is_unread) colors.text_primary else colors.text_secondary
    val subject_color = if (is_unread) colors.text_primary else colors.text_secondary
    val preview_color = if (is_unread) colors.text_secondary else colors.text_muted
    val row_bg = animateColorAsState(
        targetValue = when {
            is_selected -> inbox_card_selected_color(colors)
            is_unread -> inbox_card_unread_color(colors)
            else -> inbox_card_read_color(colors)
        },
        animationSpec = tween(durationMillis = 220),
        label = "row_bg",
    )
    val interaction_source = remember { MutableInteractionSource() }
    val yesterday_label = stringResource(R.string.yesterday)
    val relative_time = remember(email.received_at, yesterday_label) {
        email.received_at.format_relative_time(yesterday_label)
    }
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val select_tap_label = stringResource(
        if (is_selected) R.string.inbox_a11y_deselect_thread else R.string.inbox_a11y_select_thread,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (select_mode) {
                    Modifier.select_tap(click_label = select_tap_label, on_tap = on_click)
                } else {
                    Modifier
                },
            )
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(group_shape)
            .drawBehind { drawRect(row_bg.value) }
            .then(
                if (select_mode) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction_source,
                        indication = androidx.compose.material3.ripple(),
                        onClick = on_click,
                        onLongClick = on_long_click,
                        hapticFeedbackEnabled = haptic_enabled,
                    )
                },
            )
            .defaultMinSize(minHeight = metrics.min_height)
            .padding(
                start = inbox_card_content_padding,
                end = inbox_card_content_padding,
                top = metrics.vertical_padding,
                bottom = metrics.vertical_padding,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        inbox_leading_slot(show_sender_pictures, is_selected, metrics.avatar_size) {
            SenderAvatar(
                email = displayed_sender_email(email.display_sender_email, email.sender_email),
                name = displayed_sender_name(email.display_sender_name, email.sender_name),
                size = metrics.avatar_size,
                sender_authenticated = system_avatar_authenticated(email),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayed_sender_name(email.display_sender_name, email.sender_name),
                    style = MaterialTheme.typography.bodyLarge,
                    color = sender_color,
                    fontSize = 16.sp,
                    fontWeight = if (is_unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (email.has_attachment) {
                    Icon(
                        imageVector = TablerIcons.Paperclip,
                        contentDescription = stringResource(R.string.has_attachment),
                        tint = colors.text_tertiary,
                        modifier = Modifier
                            .padding(start = AsterSpacing.sm)
                            .size(14.dp),
                    )
                }
                Text(
                    text = relative_time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (is_unread) colors.text_primary else colors.text_muted,
                    fontSize = 13.sp,
                    fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = if (email.has_attachment) 4.dp else AsterSpacing.sm),
                )
                if (is_unread) {
                    Spacer(Modifier.width(6.dp))
                    unread_dot()
                }
            }
            Spacer(Modifier.height(metrics.line_gap))
            val has_preview = show_email_preview && email.preview.isNotBlank()
            val trailing_controls: @Composable () -> Unit = {
                Spacer(Modifier.width(AsterSpacing.sm))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (is_pinned) {
                        Icon(
                            imageVector = pin_icon_filled,
                            contentDescription = stringResource(R.string.pinned),
                            tint = colors.accent_blue,
                            modifier = Modifier
                                .size(14.dp)
                                .rotate(-38f),
                        )
                    }
                    star_button(
                        is_starred = email.is_starred,
                        interactive = !select_mode,
                        on_toggle = {
                            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            on_toggle_star()
                        },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = email.subject.ifBlank { stringResource(R.string.inbox_no_subject) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = subject_color,
                    fontSize = 15.sp,
                    fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (!has_preview) trailing_controls()
            }
            if (has_preview) {
                Spacer(Modifier.height(metrics.line_gap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = low_network_preview(email.preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = preview_color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    trailing_controls()
                }
            }
            alias_indicator_store.label_for_delivery(email.routing_token, email.received_on)?.let {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    alias_chip(it, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }
    }
}

@Composable
private fun unread_dot(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val label = stringResource(R.string.filter_unread)
    Box(
        modifier = modifier
            .size(7.dp)
            .background(colors.accent_blue, CircleShape)
            .semantics { contentDescription = label }
            .testTag("unread_dot"),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun star_button(
    is_starred: Boolean,
    on_toggle: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
) {
    val colors = AsterMaterial.colors
    val tint by animateColorAsState(
        targetValue = if (is_starred) colors.star else colors.text_tertiary,
        animationSpec = tween(durationMillis = 180),
        label = "star_tint",
    )
    val star_scale = animateFloatAsState(
        targetValue = if (is_starred) 1.12f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "star_scale",
    )
    var tooltip_visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val star_label = if (is_starred) stringResource(R.string.starred) else stringResource(R.string.not_starred)
    org.astermail.android.ui.common.icon_tooltip_host(
        text = star_label,
        visible = tooltip_visible,
        on_dismiss = { tooltip_visible = false },
    ) {
        Box(
            modifier = modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = star_scale.value
                    scaleY = star_scale.value
                }
                .then(
                    if (interactive) {
                        Modifier.combinedClickable(
                            onClick = on_toggle,
                            onLongClick = { tooltip_visible = true },
                        )
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (is_starred) Icons.Filled.Star else TablerIcons.Star,
                contentDescription = star_label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
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
    select_mode: Boolean = false,
    is_pinned: Boolean = false,
    haptic_enabled: Boolean = true,
    is_first: Boolean = true,
    is_last: Boolean = true,
    user_prefs: UserPreferences? = null,
) {
    val email = thread.newest
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val metrics = remember(user_prefs?.mail_list_density) { inbox_row_metrics(user_prefs?.mail_list_density) }
    val is_unread = thread.has_unread
    val sender_color = if (is_unread) colors.text_primary else colors.text_secondary
    val subject_color = if (is_unread) colors.text_primary else colors.text_secondary
    val preview_color = if (is_unread) colors.text_secondary else colors.text_muted
    val row_bg = animateColorAsState(
        targetValue = when {
            is_selected -> inbox_card_selected_color(colors)
            is_unread -> inbox_card_unread_color(colors)
            else -> inbox_card_read_color(colors)
        },
        animationSpec = tween(durationMillis = 220),
        label = "row_bg",
    )
    val interaction_source = remember { MutableInteractionSource() }
    val yesterday_label = stringResource(R.string.yesterday)
    val relative_time = remember(email.received_at, yesterday_label) {
        email.received_at.format_relative_time(yesterday_label)
    }
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val select_tap_label = stringResource(
        if (is_selected) R.string.inbox_a11y_deselect_thread else R.string.inbox_a11y_select_thread,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (select_mode) {
                    Modifier.select_tap(click_label = select_tap_label, on_tap = on_click)
                } else {
                    Modifier
                },
            )
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(group_shape)
            .drawBehind { drawRect(row_bg.value) },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (select_mode) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interaction_source,
                        indication = androidx.compose.material3.ripple(),
                        onClick = on_click,
                        onLongClick = on_long_click,
                        hapticFeedbackEnabled = haptic_enabled,
                    )
                },
            )
            .defaultMinSize(minHeight = metrics.min_height)
            .padding(
                start = inbox_card_content_padding,
                end = inbox_card_content_padding,
                top = metrics.vertical_padding,
                bottom = metrics.vertical_padding,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        inbox_leading_slot(
            user_prefs?.show_profile_pictures != false,
            is_selected,
            metrics.avatar_size,
        ) {
            val participants = androidx.compose.runtime.remember(
                thread.thread_id, thread.participants, email.sender_name, email.sender_email,
                email.display_sender_name, email.display_sender_email,
            ) {
                thread.participants.ifEmpty {
                    listOf(
                        displayed_sender_name(email.display_sender_name, email.sender_name) to
                            displayed_sender_email(email.display_sender_email, email.sender_email),
                    )
                }
            }
            StackedAvatars(participants = participants, size = metrics.avatar_size)
        }
        Column(modifier = Modifier.weight(1f)) {
            val others_count = (thread.participants.size - 1).coerceAtLeast(1)
            val others_template = pluralStringResource(R.plurals.participants_and_others, others_count)
            val participants_text = remember(
                thread.thread_id,
                thread.participants,
                email.display_sender_name,
                email.sender_name,
                others_template,
            ) {
                format_participants(
                    thread.participants,
                    displayed_sender_name(email.display_sender_name, email.sender_name),
                    others_template = others_template,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = participants_text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = sender_color,
                    fontSize = 16.sp,
                    fontWeight = if (is_unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (thread.has_attachment) {
                    Icon(
                        imageVector = TablerIcons.Paperclip,
                        contentDescription = stringResource(R.string.has_attachment),
                        tint = colors.text_tertiary,
                        modifier = Modifier
                            .padding(start = AsterSpacing.sm)
                            .size(14.dp),
                    )
                }
                Text(
                    text = relative_time,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (is_unread) colors.text_primary else colors.text_muted,
                    fontSize = 13.sp,
                    fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = if (thread.has_attachment) 4.dp else AsterSpacing.sm),
                )
                if (is_unread) {
                    Spacer(Modifier.width(6.dp))
                    unread_dot()
                }
            }
            Spacer(Modifier.height(metrics.line_gap))
            val no_subject_label = stringResource(R.string.inbox_no_subject)
            val row_context = LocalContext.current
            val subject_text = remember(email.subject, thread.message_count, no_subject_label, row_context) {
                val base = email.subject.ifBlank { no_subject_label }
                if (thread.message_count > 1) {
                    row_context.getString(R.string.inbox_subject_with_count, base, thread.message_count)
                } else {
                    base
                }
            }
            val has_preview = user_prefs?.show_email_preview != false && email.preview.isNotBlank()
            val trailing_controls: @Composable () -> Unit = {
                if (user_prefs?.show_message_size == true && email.size_bytes > 0) {
                    Spacer(Modifier.width(4.dp))
                    val size_str = remember(email.size_bytes) {
                        when {
                            email.size_bytes < 1024L -> "${email.size_bytes}B"
                            email.size_bytes < 1024L * 1024L -> "${email.size_bytes / 1024L}KB"
                            else -> "${"%.1f".format(email.size_bytes / (1024.0 * 1024.0))}MB"
                        }
                    }
                    Text(size_str, color = colors.text_muted, fontSize = 11.sp)
                }
                if (is_pinned) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    Icon(
                        imageVector = pin_icon_filled,
                        contentDescription = stringResource(R.string.pinned),
                        tint = colors.accent_blue,
                        modifier = Modifier
                            .size(14.dp)
                            .rotate(-38f),
                    )
                }
                Spacer(Modifier.width(AsterSpacing.sm))
                star_button(
                    is_starred = thread.is_starred,
                    interactive = !select_mode,
                    on_toggle = {
                        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        on_toggle_star()
                    },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = subject_text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = subject_color,
                        fontSize = 15.sp,
                        fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    alias_indicator_store.label_for_delivery(email.routing_token, email.received_on)?.let {
                        Spacer(Modifier.width(6.dp))
                        alias_chip(it, modifier = Modifier.widthIn(max = 148.dp))
                    }
                }
                if (!has_preview) trailing_controls()
            }
            val folder_chip = thread.folder_chip
            val chip_count = thread.label_colors.size + (if (folder_chip != null) 1 else 0)
            val labels_inline = has_preview && chip_count in 1..2
            if (has_preview) {
                Spacer(Modifier.height(metrics.line_gap))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (labels_inline) {
                        if (folder_chip != null) {
                            label_chip(
                                color = folder_chip.color,
                                name = folder_chip.name,
                                icon = folder_chip.icon,
                                modifier = Modifier.widthIn(max = 120.dp).testTag("list_folder_chip"),
                            )
                        }
                        thread.label_colors.indices.forEach { i ->
                            label_chip(
                                color = thread.label_colors[i],
                                name = thread.label_names.getOrElse(i) { "" },
                                icon = thread.label_icons.getOrElse(i) { "" },
                                modifier = Modifier.widthIn(max = 120.dp),
                            )
                        }
                    }
                    Text(
                        text = low_network_preview(email.preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = preview_color,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    trailing_controls()
                }
            }
            if (chip_count > 0 && !labels_inline) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (folder_chip != null) {
                        label_chip(
                            color = folder_chip.color,
                            name = folder_chip.name,
                            icon = folder_chip.icon,
                            modifier = Modifier.weight(1f, fill = false).testTag("list_folder_chip"),
                        )
                    }
                    thread.label_colors.indices.forEach { i ->
                        label_chip(
                            color = thread.label_colors[i],
                            name = thread.label_names.getOrElse(i) { "" },
                            icon = thread.label_icons.getOrElse(i) { "" },
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }
    }
}

internal data class InboxRowMetrics(
    val min_height: androidx.compose.ui.unit.Dp,
    val vertical_padding: androidx.compose.ui.unit.Dp,
    val avatar_size: androidx.compose.ui.unit.Dp,
    val line_gap: androidx.compose.ui.unit.Dp,
)

internal fun is_comfortable_density(density: String?): Boolean =
    density == "comfortable" || density == "spacious"

internal fun inbox_row_metrics(density: String?): InboxRowMetrics =
    if (is_comfortable_density(density)) {
        InboxRowMetrics(
            min_height = 88.dp,
            vertical_padding = AsterSpacing.md,
            avatar_size = 44.dp,
            line_gap = 3.dp,
        )
    } else {
        InboxRowMetrics(
            min_height = 72.dp,
            vertical_padding = AsterSpacing.sm,
            avatar_size = 40.dp,
            line_gap = 2.dp,
        )
    }

internal val inbox_card_horizontal_margin = 10.dp
internal val inbox_card_vertical_gap = 0.dp
internal val inbox_card_content_padding = 16.dp
internal val inbox_card_shape = SquircleShape(16.dp)
internal val inbox_group_corner = 18.dp
internal val inbox_group_inner_corner = 6.dp
internal val inbox_group_split = 3.dp

internal fun inbox_group_shape(is_first: Boolean, is_last: Boolean): Shape = RoundedCornerShape(
    topStart = if (is_first) inbox_group_corner else inbox_group_inner_corner,
    topEnd = if (is_first) inbox_group_corner else inbox_group_inner_corner,
    bottomStart = if (is_last) inbox_group_corner else inbox_group_inner_corner,
    bottomEnd = if (is_last) inbox_group_corner else inbox_group_inner_corner,
)

private fun shift_lightness(base: Color, delta: Float, saturation_boost: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[1] = (hsl[1] * saturation_boost).coerceIn(0f, 1f)
    hsl[2] = (hsl[2] + delta).coerceIn(0f, 1f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

private fun lightness_of(color: Color): Float {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    return hsl[2]
}

internal fun inbox_card_read_color(colors: AsterSemanticColors): Color =
    if (colors.is_dark) {
        shift_lightness(colors.bg_primary, 0.045f, 1f)
    } else {
        shift_lightness(colors.bg_primary, -0.04f, 1f)
    }

internal fun inbox_card_unread_color(colors: AsterSemanticColors): Color =
    inbox_card_read_color(colors)

internal fun search_field_bg_color(colors: AsterSemanticColors): Color =
    if (colors.is_dark) {
        shift_lightness(colors.bg_primary, 0.09f, 1.15f)
    } else {
        shift_lightness(colors.bg_primary, -0.10f, 1.15f)
    }

internal fun inbox_card_selected_color(colors: AsterSemanticColors): Color =
    colors.bg_selected

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
internal fun alias_chip(label: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(4.dp)
    val surface = inbox_card_read_color(colors)
    val background = chip_background(colors.text_tertiary, surface, colors.is_dark)
    val border = chip_border(colors.text_tertiary, surface, colors.is_dark)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = TablerIcons.At,
            contentDescription = stringResource(R.string.received_on_label),
            tint = colors.text_tertiary,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.text_secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun label_chip(color: Color, name: String, icon: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val icon_vector = if (icon.isNotBlank()) material_icon_from_name(icon) else null
    val has_name = name.isNotBlank()
    val shape = RoundedCornerShape(4.dp)
    val surface = inbox_card_read_color(colors)
    val background = chip_background(color, surface, colors.is_dark)
    val border = chip_border(color, surface, colors.is_dark)
    val content = chip_content(color, background, colors.is_dark)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon_vector != null) {
            Icon(
                imageVector = icon_vector,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(11.dp),
            )
        }
        if (has_name) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else if (icon_vector == null) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(content, shape = CircleShape),
            )
        }
    }
}

internal fun material_icon_from_name(name: String) =
    org.astermail.android.ui.common.label_icon_or_null(name)

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

@Composable
private fun low_network_preview(preview: String): String {
    val limit = org.astermail.android.api.network.preview_char_limit(
        configured_limit = null,
        low_network = org.astermail.android.network.low_network_active(),
    ) ?: return preview
    if (preview.length <= limit) return preview
    return preview.take(limit).trimEnd() + "…"
}
