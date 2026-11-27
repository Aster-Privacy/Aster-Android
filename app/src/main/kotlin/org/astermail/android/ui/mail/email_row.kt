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

import org.astermail.android.ui.search.search_row_text
import androidx.compose.animation.core.snap
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import compose.icons.TablerIcons
import compose.icons.tablericons.*

import org.astermail.android.design.acrylic
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterIconSwap
import org.astermail.android.design.AsterMotionSpec
import org.astermail.android.design.aster_animate_color
import org.astermail.android.design.aster_collapse_horizontal_out
import org.astermail.android.design.aster_expand_horizontal_in
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.astermail.android.ui.theme.local_accessibility
import androidx.compose.ui.layout.layout
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.design.AsterSemanticColors
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.mail.MailViewModel
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.security.safe_sender_name
import org.astermail.android.security.trusted_sender_name
import org.astermail.android.ui.icons.alias_at_icon

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun inbox_leading_slot(
    show_pictures: Boolean,
    is_selected: Boolean,
    avatar_size: androidx.compose.ui.unit.Dp,
    on_avatar_click: (() -> Unit)? = null,
    on_avatar_long_click: (() -> Unit)? = null,
    haptic_enabled: Boolean = true,
    avatar: @Composable () -> Unit,
) {
    val colors = AsterMaterial.colors
    val reduce = aster_reduce_motion()
    val toggle_label = stringResource(if (is_selected) R.string.deselect else R.string.select)
    val tap_interaction = remember { MutableInteractionSource() }
    val tap_modifier = if (on_avatar_click != null) {
        Modifier.combinedClickable(
            interactionSource = tap_interaction,
            indication = androidx.compose.material3.ripple(bounded = false, radius = avatar_size / 2),
            onClickLabel = toggle_label,
            onClick = on_avatar_click,
            onLongClick = on_avatar_long_click,
            hapticFeedbackEnabled = haptic_enabled,
        )
    } else {
        Modifier
    }
    if (!show_pictures) {
        AnimatedVisibility(
            visible = is_selected,
            enter = aster_expand_horizontal_in(reduce),
            exit = aster_collapse_horizontal_out(reduce),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = tap_modifier) {
                    inbox_selected_check(24.dp, 16.dp, 1f)
                }
                Spacer(Modifier.width(AsterSpacing.md))
            }
        }
        return
    }
    val flip = animateFloatAsState(
        targetValue = if (is_selected) 180f else 0f,
        animationSpec = tween(
            durationMillis = if (reduce) 0 else AsterMotionSpec.icon_ms,
            easing = AsterEasing.emphasized,
        ),
        label = "avatar_flip",
    )
    val check_reveal = animateFloatAsState(
        targetValue = if (is_selected) 1f else 0f,
        animationSpec = if (is_selected) {
            tween(
                durationMillis = if (reduce) AsterMotionSpec.reduced_fade_ms else AsterMotionSpec.icon_ms / 2,
                delayMillis = if (reduce) 0 else AsterMotionSpec.icon_ms / 2,
                easing = AsterEasing.emphasized_decelerate,
            )
        } else {
            tween(AsterMotionSpec.fade_out_ms, easing = AsterEasing.emphasized_accelerate)
        },
        label = "avatar_check_reveal",
    )
    val back_visible by remember { derivedStateOf { flip.value > 90f } }
    Box(
        modifier = tap_modifier.graphicsLayer {
            rotationY = flip.value
            cameraDistance = 12f * density
        },
        contentAlignment = Alignment.Center,
    ) {
        if (back_visible) {
            Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                inbox_selected_check(avatar_size, 20.dp, check_reveal.value)
            }
        } else {
            avatar()
        }
    }
    Spacer(Modifier.width(AsterSpacing.md))
}

@Composable
private fun inbox_selected_check(
    circle_size: androidx.compose.ui.unit.Dp,
    icon_size: androidx.compose.ui.unit.Dp,
    reveal: Float,
) {
    val colors = AsterMaterial.colors
    Box(
        modifier = Modifier
            .size(circle_size)
            .clip(CircleShape)
            .background(colors.accent_blue),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = TablerIcons.Check,
            contentDescription = stringResource(R.string.selected),
            tint = colors.on_accent,
            modifier = Modifier
                .size(icon_size)
                .graphicsLayer {
                    alpha = reveal
                    val check_scale = AsterMotionSpec.icon_from + (1f - AsterMotionSpec.icon_from) * reveal
                    scaleX = check_scale
                    scaleY = check_scale
                },
        )
    }
}

@Composable
private fun inbox_row_summary(
    sender: String,
    subject: String,
    preview: String?,
    time: String,
    is_pinned: Boolean,
    has_attachment: Boolean,
    is_starred: Boolean,
): String {
    val pinned_label = stringResource(R.string.pinned)
    val attachment_label = stringResource(R.string.has_attachment)
    val starred_label = stringResource(R.string.starred)
    return remember(sender, subject, preview, time, is_pinned, has_attachment, is_starred, pinned_label, attachment_label, starred_label) {
        buildString {
            append(sender)
            append(", ")
            append(subject)
            if (!preview.isNullOrBlank()) {
                append(", ")
                append(preview)
            }
            append(", ")
            append(time)
            if (is_pinned) {
                append(", ")
                append(pinned_label)
            }
            if (has_attachment) {
                append(", ")
                append(attachment_label)
            }
            if (is_starred) {
                append(", ")
                append(starred_label)
            }
        }
    }
}

@Composable
private fun inbox_row_actions(
    base: List<CustomAccessibilityAction>,
    is_starred: Boolean,
    select_mode: Boolean,
    on_toggle_star: () -> Unit,
    on_avatar_click: (() -> Unit)?,
    is_selected: Boolean,
): List<CustomAccessibilityAction> {
    val star_label = stringResource(if (is_starred) R.string.unstar else R.string.star)
    val select_label = stringResource(if (is_selected) R.string.deselect else R.string.select)
    val current_star = rememberUpdatedState(on_toggle_star)
    val current_avatar = rememberUpdatedState(on_avatar_click)
    val has_avatar_action = on_avatar_click != null
    return remember(base, star_label, select_label, select_mode, has_avatar_action) {
        if (select_mode) {
            base
        } else {
            buildList {
                add(CustomAccessibilityAction(star_label) { current_star.value(); true })
                if (has_avatar_action) {
                    add(
                        CustomAccessibilityAction(select_label) {
                            val action = current_avatar.value
                            if (action != null) {
                                action()
                                true
                            } else {
                                false
                            }
                        },
                    )
                }
                addAll(base)
            }
        }
    }
}

private fun Modifier.inbox_row_semantics(
    summary: String,
    state: String,
    click_label: String?,
    on_click: () -> Unit,
    on_long_click: () -> Unit,
    actions: List<CustomAccessibilityAction>,
): Modifier = clearAndSetSemantics {
    contentDescription = summary
    stateDescription = state
    onClick(label = click_label) {
        on_click()
        true
    }
    onLongClick {
        on_long_click()
        true
    }
    if (actions.isNotEmpty()) customActions = actions
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
    refresh_engaged: () -> Boolean = { false },
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val tap_guard = remember { row_tap_guard() }
    val compact_rows = local_accessibility.current.compact_mode
    val metrics = remember(list_density, compact_rows) { inbox_row_metrics(list_density, compact_rows) }
    val is_unread = !email.is_read
    val trust = remember_sender_trust(email.trust_message)
    val shown_sender_name = trusted_sender_name(
        trust,
        displayed_sender_name(email.display_sender_name, email.sender_name),
        stringResource(R.string.unknown),
    )
    val marks_hidden = trust?.show_identity_marks == false
    val shows_official_badge = remember(email) { is_official_sender(email) }
    val read_fade_scale = local_read_fade_scale.current
    val sender_color = inbox_sender_color(colors, is_unread, read_fade_scale)
    val subject_color = inbox_subject_color(colors, is_unread, read_fade_scale)
    val preview_color = inbox_preview_color(colors, is_unread, read_fade_scale)
    val row_bg = aster_animate_color(
        target = when {
            is_selected -> inbox_card_selected_color(colors)
            is_unread -> inbox_card_unread_color(colors)
            else -> inbox_card_read_color(colors)
        },
        label = "row_bg",
    )
    val interaction_source = remember { MutableInteractionSource() }
    val yesterday_label = stringResource(R.string.yesterday)
    val relative_time = remember(email.received_at, yesterday_label, AsterTimePreferences.generation) {
        email.received_at.format_relative_time(yesterday_label)
    }
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val select_tap_label = stringResource(
        if (is_selected) R.string.inbox_a11y_deselect_thread else R.string.inbox_a11y_select_thread,
    )
    val read_state_label = stringResource(
        if (is_unread) R.string.inbox_a11y_state_unread else R.string.inbox_a11y_state_read,
    )
    val row_subject = email.subject.ifBlank { stringResource(R.string.inbox_no_subject) }
    val row_summary = inbox_row_summary(
        sender = shown_sender_name,
        subject = row_subject,
        preview = if (show_email_preview) email.preview else null,
        time = relative_time,
        is_pinned = is_pinned,
        has_attachment = email.has_attachment,
        is_starred = email.is_starred,
    )
    val row_actions = inbox_row_actions(
        base = emptyList(),
        is_starred = email.is_starred,
        select_mode = select_mode,
        on_toggle_star = on_toggle_star,
        on_avatar_click = null,
        is_selected = is_selected,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .inbox_row_semantics(
                summary = row_summary,
                state = read_state_label,
                click_label = if (select_mode) select_tap_label else null,
                on_click = on_click,
                on_long_click = on_long_click,
                actions = row_actions,
            )
            .press_preload(email.id, enabled = !select_mode)
            .then(
                if (select_mode) {
                    Modifier.select_tap(click_label = select_tap_label, on_tap = on_click, interaction_source = interaction_source)
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
            .acrylic_backdrop(colors)
            .drawBehind { drawRect(row_bg.value) }
            .then(
                if (select_mode) {
                    Modifier.indication(interaction_source, androidx.compose.material3.ripple())
                } else {
                    Modifier
                        .row_tap_guard(tap_guard, refresh_engaged)
                        .combinedClickable(
                            interactionSource = interaction_source,
                            indication = androidx.compose.material3.ripple(),
                            onClick = { if (!tap_guard.blocked) on_click() },
                            onLongClick = on_long_click,
                            hapticFeedbackEnabled = haptic_enabled,
                        )
                },
            )
            .defaultMinSize(minHeight = metrics.min_height_for(show_email_preview && email.preview.isNotBlank()))
            .padding(
                start = inbox_card_content_padding,
                end = inbox_card_content_padding,
                top = metrics.vertical_padding,
                bottom = metrics.vertical_padding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inbox_leading_slot(show_sender_pictures, is_selected, metrics.avatar_size) {
            SenderAvatar(
                email = displayed_sender_email(email.display_sender_email, email.sender_email),
                name = shown_sender_name,
                size = metrics.avatar_size,
                sender_authenticated = !marks_hidden &&
                    trust?.warning == null &&
                    system_avatar_authenticated(email),
                plain = marks_hidden,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            val via_label = custom_forward_via_label(email.display_sender_email, email.sender_email)
            val via_color = colors.text_tertiary
            val sender_text = remember(shown_sender_name, via_label, via_color) {
                with_via_suffix(shown_sender_name, via_label, via_color)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sender_text,
                        style = MaterialTheme.typography.bodyLarge.merge(inbox_bidi_line_style()),
                        color = sender_color,
                        fontSize = 16.sp,
                        fontWeight = if (is_unread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (shows_official_badge) {
                        official_badge(
                            modifier = Modifier.padding(start = 4.dp),
                            size = 22.dp,
                            interactive = false,
                        )
                    }
                }
                if (is_pinned) {
                    Icon(
                        imageVector = pin_icon_filled,
                        contentDescription = stringResource(R.string.pinned),
                        tint = colors.accent_blue,
                        modifier = Modifier
                            .padding(start = AsterSpacing.sm)
                            .size(14.dp)
                            .testTag("row_pin_indicator"),
                    )
                }
                if (email.has_unseen_reaction) {
                    reaction_badge()
                }
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
                    color = inbox_time_color(colors, is_unread, read_fade_scale),
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
                    star_button(
                        is_starred = email.is_starred,
                        modifier = Modifier.inbox_line_neutral_height(),
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
                    style = MaterialTheme.typography.bodyMedium.merge(inbox_bidi_line_style()),
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
                        style = MaterialTheme.typography.bodySmall.merge(inbox_bidi_line_style()),
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
            val alias_label = remember(
                email.routing_token,
                email.received_on,
                alias_indicator_store.enabled,
                alias_indicator_store.token_labels,
                alias_indicator_store.labels,
            ) {
                alias_indicator_store.label_for_delivery(email.routing_token, email.received_on)
            }
            alias_label?.let {
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
            if (is_send_failure(email.send_status)) {
                Spacer(Modifier.height(4.dp))
                send_failed_chip()
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

@Composable
private fun reaction_badge(modifier: Modifier = Modifier) {
    Icon(
        imageVector = TablerIcons.MoodSmile,
        contentDescription = stringResource(R.string.reaction_badge_label),
        tint = AsterMaterial.colors.accent_blue,
        modifier = modifier
            .padding(start = AsterSpacing.sm)
            .size(14.dp)
            .testTag("row_reaction_badge"),
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
    val tint by aster_animate_color(
        target = if (is_starred) colors.star else colors.text_tertiary,
        label = "star_tint",
    )
    var tooltip_visible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val star_label = if (is_starred) stringResource(R.string.starred) else stringResource(R.string.not_starred)
    org.astermail.android.ui.common.icon_tooltip_host(
        text = star_label,
        visible = tooltip_visible,
        on_dismiss = { tooltip_visible = false },
    ) {
        val star_interaction = remember { MutableInteractionSource() }
        Box(
            modifier = modifier
                .size(inbox_star_slot_size)
                .wrapContentSize(unbounded = true),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (interactive) {
                            Modifier
                                .minimumInteractiveComponentSize()
                                .combinedClickable(
                                    interactionSource = star_interaction,
                                    indication = null,
                                    onClick = on_toggle,
                                    onLongClick = { tooltip_visible = true },
                                )
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .indication(star_interaction, LocalIndication.current),
                    contentAlignment = Alignment.Center,
                ) {
                    AsterIconSwap(target_state = is_starred, label = "star_icon") { starred ->
                        Icon(
                            imageVector = if (starred) Icons.Filled.Star else TablerIcons.Star,
                            contentDescription = star_label,
                            tint = tint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
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
    cached_geometry: SkeletonGeometry? = null,
    refresh_engaged: () -> Boolean = { false },
    accessibility_actions: List<CustomAccessibilityAction> = emptyList(),
    on_avatar_click: (() -> Unit)? = null,
) {
    val email = thread.newest
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val tap_guard = remember { row_tap_guard() }
    val row_density = user_prefs?.mail_list_density ?: cached_geometry?.list_density
    val show_avatar = if (user_prefs != null) {
        user_prefs.show_profile_pictures != false
    } else {
        cached_geometry?.show_avatar != false
    }
    val show_preview = if (user_prefs != null) {
        user_prefs.show_email_preview != false
    } else {
        cached_geometry?.show_preview != false
    }
    val compact_rows = local_accessibility.current.compact_mode
    val metrics = remember(row_density, compact_rows) { inbox_row_metrics(row_density, compact_rows) }
    val is_unread = thread.has_unread
    val trust = remember_sender_trust(email.trust_message)
    val newest_sender_name = trusted_sender_name(
        trust,
        displayed_sender_name(email.display_sender_name, email.sender_name),
        stringResource(R.string.unknown),
    )
    val newest_sender_email = displayed_sender_email(email.display_sender_email, email.sender_email)
    val single_plain_sender = trust != null && (!trust.show_identity_marks || trust.warning != null)
    val shows_official_badge = remember(email) { is_official_sender(email) }
    val shown_participants = remember(
        thread.thread_id,
        thread.participants,
        newest_sender_name,
        newest_sender_email,
        single_plain_sender,
        trust != null,
    ) {
        vetted_participants(
            participants = thread.participants,
            newest_name = newest_sender_name,
            newest_email = newest_sender_email,
            single_sender = single_plain_sender,
            newest_vetted = trust != null,
        )
    }
    val read_fade_scale = local_read_fade_scale.current
    val sender_color = inbox_sender_color(colors, is_unread, read_fade_scale)
    val subject_color = inbox_subject_color(colors, is_unread, read_fade_scale)
    val preview_color = inbox_preview_color(colors, is_unread, read_fade_scale)
    val row_bg = aster_animate_color(
        target = when {
            is_selected -> inbox_card_selected_color(colors)
            is_unread -> inbox_card_unread_color(colors)
            else -> inbox_card_read_color(colors)
        },
        label = "row_bg",
    )
    val interaction_source = remember { MutableInteractionSource() }
    val yesterday_label = stringResource(R.string.yesterday)
    val relative_time = remember(email.received_at, yesterday_label, AsterTimePreferences.generation) {
        email.received_at.format_relative_time(yesterday_label)
    }
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    val select_tap_label = stringResource(
        if (is_selected) R.string.inbox_a11y_deselect_thread else R.string.inbox_a11y_select_thread,
    )
    val read_state_label = stringResource(
        if (is_unread) R.string.inbox_a11y_state_unread else R.string.inbox_a11y_state_read,
    )
    val others_count = (thread.participants.size - 1).coerceAtLeast(1)
    val others_template = pluralStringResource(R.plurals.participants_and_others, others_count)
    val participants_text = remember(
        thread.thread_id,
        shown_participants,
        newest_sender_name,
        others_template,
    ) {
        format_participants(
            shown_participants,
            newest_sender_name,
            others_template = others_template,
        )
    }
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
    val row_summary = inbox_row_summary(
        sender = participants_text,
        subject = subject_text,
        preview = if (show_preview) email.preview else null,
        time = relative_time,
        is_pinned = is_pinned,
        has_attachment = thread.has_attachment,
        is_starred = thread.is_starred,
    )
    val row_actions = inbox_row_actions(
        base = accessibility_actions,
        is_starred = thread.is_starred,
        select_mode = select_mode,
        on_toggle_star = on_toggle_star,
        on_avatar_click = on_avatar_click,
        is_selected = is_selected,
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .inbox_row_semantics(
                summary = row_summary,
                state = read_state_label,
                click_label = if (select_mode) select_tap_label else null,
                on_click = on_click,
                on_long_click = on_long_click,
                actions = row_actions,
            )
            .press_preload(email.id, enabled = !select_mode)
            .then(
                if (select_mode) {
                    Modifier.select_tap(click_label = select_tap_label, on_tap = on_click, interaction_source = interaction_source)
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
            .acrylic_backdrop(colors)
            .drawBehind { drawRect(row_bg.value) },
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (select_mode) {
                    Modifier.indication(interaction_source, androidx.compose.material3.ripple())
                } else {
                    Modifier
                        .row_tap_guard(tap_guard, refresh_engaged)
                        .combinedClickable(
                            interactionSource = interaction_source,
                            indication = androidx.compose.material3.ripple(),
                            onClick = { if (!tap_guard.blocked) on_click() },
                            onLongClick = on_long_click,
                            hapticFeedbackEnabled = haptic_enabled,
                        )
                },
            )
            .defaultMinSize(minHeight = metrics.min_height_for(show_preview && email.preview.isNotBlank()))
            .padding(
                start = inbox_card_content_padding,
                end = inbox_card_content_padding,
                top = metrics.vertical_padding,
                bottom = metrics.vertical_padding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        inbox_leading_slot(
            show_avatar,
            is_selected,
            metrics.avatar_size,
            if (select_mode) null else on_avatar_click,
            on_avatar_long_click = on_long_click,
            haptic_enabled = haptic_enabled,
        ) {
            val authenticated_system_email = androidx.compose.runtime.remember(
                email.sender_email, email.display_sender_email, email.is_external, email.system_origin,
                single_plain_sender,
            ) {
                if (!single_plain_sender && system_avatar_authenticated(email)) {
                    displayed_sender_email(email.display_sender_email, email.sender_email)
                } else {
                    null
                }
            }
            StackedAvatars(
                participants = shown_participants,
                size = metrics.avatar_size,
                authenticated_system_email = authenticated_system_email,
                plain = single_plain_sender,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            val via_label = if (shown_participants.size <= 1) {
                custom_forward_via_label(email.display_sender_email, email.sender_email)
            } else {
                null
            }
            val via_color = colors.text_tertiary
            val sender_text = remember(participants_text, via_label, via_color) {
                with_via_suffix(participants_text, via_label, via_color)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = search_row_text(sender_text) { it.sender },
                        style = inbox_sender_text_style(),
                        color = sender_color,
                        fontWeight = if (is_unread) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (shows_official_badge) {
                        official_badge(
                            modifier = Modifier.padding(start = 4.dp),
                            size = 22.dp,
                            interactive = false,
                        )
                    }
                }
                if (is_pinned) {
                    Icon(
                        imageVector = pin_icon_filled,
                        contentDescription = stringResource(R.string.pinned),
                        tint = colors.accent_blue,
                        modifier = Modifier
                            .padding(start = AsterSpacing.sm)
                            .size(14.dp)
                            .testTag("row_pin_indicator"),
                    )
                }
                if (thread.has_unseen_reaction) {
                    reaction_badge()
                }
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
                    style = inbox_time_text_style(),
                    color = inbox_time_color(colors, is_unread, read_fade_scale),
                    fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.padding(start = if (thread.has_attachment) 4.dp else AsterSpacing.sm),
                )
                if (is_unread) {
                    Spacer(Modifier.width(6.dp))
                    unread_dot()
                }
            }
            Spacer(Modifier.height(metrics.line_gap))
            val has_preview = show_preview && email.preview.isNotBlank()
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
                Spacer(Modifier.width(AsterSpacing.sm))
                star_button(
                    is_starred = thread.is_starred,
                    modifier = Modifier.inbox_line_neutral_height(),
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
                        text = search_row_text(subject_text) { it.subject },
                        style = inbox_subject_text_style(),
                        color = subject_color,
                        fontWeight = if (is_unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val alias_label = remember(
                        email.routing_token,
                        email.received_on,
                        alias_indicator_store.enabled,
                        alias_indicator_store.token_labels,
                        alias_indicator_store.labels,
                    ) {
                        alias_indicator_store.label_for_delivery(email.routing_token, email.received_on)
                    }
                    alias_label?.let {
                        Spacer(Modifier.width(6.dp))
                        alias_chip(it, modifier = Modifier.widthIn(max = 148.dp))
                    }
                    if (is_send_failure(email.send_status)) {
                        Spacer(Modifier.width(6.dp))
                        send_failed_chip()
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
                    inbox_preview_or_order_line(
                        email = email,
                        preview_text = low_network_preview(email.preview),
                        preview_color = preview_color,
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

private val inbox_bidi_ltr_style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Left)

private val inbox_bidi_rtl_style = TextStyle(textDirection = TextDirection.Content, textAlign = TextAlign.Right)

@Composable
internal fun inbox_bidi_line_style(): TextStyle =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) inbox_bidi_rtl_style else inbox_bidi_ltr_style

@Composable
private fun inbox_line_style(base: TextStyle, font_size: androidx.compose.ui.unit.TextUnit, bidi: Boolean): TextStyle {
    val line = if (bidi) inbox_bidi_line_style() else null
    return remember(base, font_size, line) {
        val sized = base.merge(TextStyle(fontSize = font_size))
        if (line != null) sized.merge(line) else sized
    }
}

@Composable
internal fun inbox_sender_text_style(): TextStyle =
    inbox_line_style(MaterialTheme.typography.bodyLarge, 16.sp, bidi = true)

@Composable
internal fun inbox_time_text_style(): TextStyle =
    inbox_line_style(MaterialTheme.typography.labelSmall, 13.sp, bidi = false)

@Composable
internal fun inbox_subject_text_style(): TextStyle =
    inbox_line_style(MaterialTheme.typography.bodyMedium, 15.sp, bidi = true)

@Composable
internal fun inbox_preview_text_style(): TextStyle =
    inbox_line_style(MaterialTheme.typography.bodySmall, 14.sp, bidi = true)

internal val inbox_star_slot_size = 32.dp

internal data class InboxRowMetrics(
    val min_height: androidx.compose.ui.unit.Dp,
    val vertical_padding: androidx.compose.ui.unit.Dp,
    val avatar_size: androidx.compose.ui.unit.Dp,
    val line_gap: androidx.compose.ui.unit.Dp,
    val three_line_min_height: androidx.compose.ui.unit.Dp,
)

internal fun InboxRowMetrics.min_height_for(has_preview_line: Boolean): androidx.compose.ui.unit.Dp =
    if (has_preview_line) three_line_min_height else min_height

internal fun Modifier.inbox_line_neutral_height(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        constraints.copy(minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity),
    )
    layout(placeable.width, 0) {
        placeable.place(0, -placeable.height / 2)
    }
}

internal fun is_comfortable_density(density: String?): Boolean =
    density == "comfortable" || density == "spacious"

internal fun inbox_row_metrics(density: String?, compact_rows: Boolean = false): InboxRowMetrics =
    if (compact_rows) {
        InboxRowMetrics(
            min_height = 60.dp,
            vertical_padding = AsterSpacing.xs,
            avatar_size = 36.dp,
            line_gap = 1.dp,
            three_line_min_height = 72.dp,
        )
    } else if (is_comfortable_density(density)) {
        InboxRowMetrics(
            min_height = 88.dp,
            vertical_padding = AsterSpacing.md,
            avatar_size = 44.dp,
            line_gap = 3.dp,
            three_line_min_height = 96.dp,
        )
    } else {
        InboxRowMetrics(
            min_height = 72.dp,
            vertical_padding = AsterSpacing.sm,
            avatar_size = 40.dp,
            line_gap = 2.dp,
            three_line_min_height = 84.dp,
        )
    }

internal val inbox_card_horizontal_margin = 10.dp
internal val inbox_card_vertical_gap = 0.dp
internal val inbox_card_content_padding = 16.dp
internal val inbox_card_shape = SquircleShape(16.dp)
internal val inbox_group_corner = 18.dp
internal val inbox_group_inner_corner = 6.dp
internal val inbox_group_split = 3.dp

private fun build_inbox_group_shape(is_first: Boolean, is_last: Boolean): Shape = RoundedCornerShape(
    topStart = if (is_first) inbox_group_corner else inbox_group_inner_corner,
    topEnd = if (is_first) inbox_group_corner else inbox_group_inner_corner,
    bottomStart = if (is_last) inbox_group_corner else inbox_group_inner_corner,
    bottomEnd = if (is_last) inbox_group_corner else inbox_group_inner_corner,
)

private val inbox_group_shape_single = build_inbox_group_shape(is_first = true, is_last = true)
private val inbox_group_shape_top = build_inbox_group_shape(is_first = true, is_last = false)
private val inbox_group_shape_bottom = build_inbox_group_shape(is_first = false, is_last = true)
private val inbox_group_shape_middle = build_inbox_group_shape(is_first = false, is_last = false)

internal fun inbox_group_shape(is_first: Boolean, is_last: Boolean): Shape = when {
    is_first && is_last -> inbox_group_shape_single
    is_first -> inbox_group_shape_top
    is_last -> inbox_group_shape_bottom
    else -> inbox_group_shape_middle
}

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

private class CardReadColorEntry(
    val is_glass: Boolean,
    val is_dark: Boolean,
    val bg_primary: Color,
    val bg_card: Color,
    val value: Color,
)

@Volatile
private var card_read_color_entry: CardReadColorEntry? = null

private fun compute_inbox_card_read_color(colors: AsterSemanticColors): Color =
    if (colors.is_glass) {
        colors.bg_card.copy(alpha = 1f)
    } else if (colors.is_dark) {
        shift_lightness(colors.bg_primary, 0.045f, 1f)
    } else {
        shift_lightness(colors.bg_primary, -0.04f, 1f)
    }

internal fun inbox_card_read_color(colors: AsterSemanticColors): Color {
    val cached = card_read_color_entry
    if (
        cached != null &&
        cached.is_glass == colors.is_glass &&
        cached.is_dark == colors.is_dark &&
        cached.bg_primary == colors.bg_primary &&
        cached.bg_card == colors.bg_card
    ) {
        return cached.value
    }
    val value = compute_inbox_card_read_color(colors)
    card_read_color_entry = CardReadColorEntry(colors.is_glass, colors.is_dark, colors.bg_primary, colors.bg_card, value)
    return value
}

internal fun inbox_card_unread_color(colors: AsterSemanticColors): Color =
    inbox_card_read_color(colors)

private const val read_sender_fade = 0.34f
private const val read_subject_fade = 0.46f
private const val read_preview_fade = 0.56f
private const val unread_preview_fade = 0.30f

val local_read_fade_scale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

private fun fade_toward_surface(colors: AsterSemanticColors, amount: Float): Color {
    val surface = if (colors.is_glass) colors.solid_bg else colors.bg_primary
    return androidx.compose.ui.graphics.lerp(colors.text_primary, surface.copy(alpha = 1f), amount)
}

internal fun inbox_sender_color(colors: AsterSemanticColors, unread: Boolean, fade_scale: Float = 1f): Color =
    if (unread) colors.text_primary else fade_toward_surface(colors, read_sender_fade * fade_scale)

internal fun inbox_subject_color(colors: AsterSemanticColors, unread: Boolean, fade_scale: Float = 1f): Color =
    if (unread) colors.text_primary else fade_toward_surface(colors, read_subject_fade * fade_scale)

internal fun inbox_preview_color(colors: AsterSemanticColors, unread: Boolean, fade_scale: Float = 1f): Color =
    fade_toward_surface(colors, if (unread) unread_preview_fade else read_preview_fade * fade_scale)

internal fun inbox_time_color(colors: AsterSemanticColors, unread: Boolean, fade_scale: Float = 1f): Color =
    if (unread) colors.accent_blue else fade_toward_surface(colors, read_preview_fade * fade_scale)

internal fun search_field_bg_color(colors: AsterSemanticColors): Color =
    if (colors.is_glass) {
        colors.glass_surface(colors.input_bg)
    } else if (colors.is_dark) {
        shift_lightness(colors.bg_primary, 0.09f, 1.15f)
    } else {
        shift_lightness(colors.bg_primary, -0.10f, 1.15f)
    }

internal fun inbox_card_selected_color(colors: AsterSemanticColors): Color =
    colors.bg_selected

internal fun vetted_participants(
    participants: List<Pair<String, String>>,
    newest_name: String,
    newest_email: String,
    single_sender: Boolean,
    newest_vetted: Boolean,
): List<Pair<String, String>> {
    if (single_sender || participants.isEmpty()) return listOf(newest_name to newest_email)
    val newest_key = newest_email.trim().lowercase()
    return participants.map { (name, address) ->
        if (newest_vetted && newest_key.isNotEmpty() && address.trim().lowercase() == newest_key) {
            newest_name to address
        } else {
            safe_sender_name(name, address) to address
        }
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

@Composable
internal fun custom_forward_via_label(display_sender_email: String?, sender_email: String): String? {
    val domain = remember(display_sender_email, sender_email) {
        custom_forward_via_domain(display_sender_email, sender_email)
    } ?: return null
    return stringResource(R.string.forwarded_via_domain, domain)
}

internal fun with_via_suffix(name: String, via_label: String?, via_color: Color): AnnotatedString =
    buildAnnotatedString {
        append(name)
        if (via_label != null) {
            append(" ")
            withStyle(SpanStyle(color = via_color, fontWeight = FontWeight.Normal, fontSize = 13.sp)) {
                append(via_label)
            }
        }
    }

private fun first_name(full: String): String {
    val trimmed = full.trim()
    val space = trimmed.indexOf(' ')
    return if (space > 0) trimmed.substring(0, space) else trimmed
}

internal fun is_send_failure(send_status: String?): Boolean =
    send_status == "failed" || send_status == "bounced"

@Composable
internal fun send_failed_chip(modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(4.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(colors.danger.copy(alpha = 0.12f), shape)
            .border(1.dp, colors.danger.copy(alpha = 0.35f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = TablerIcons.AlertTriangle,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = stringResource(R.string.send_failed_chip),
            style = MaterialTheme.typography.labelSmall,
            color = colors.danger,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun alias_chip(label: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val shape = RoundedCornerShape(4.dp)
    val surface = inbox_card_read_color(colors)
    val background = chip_subtle_background(colors.text_tertiary, surface, colors.is_dark)
    val border = chip_subtle_border(colors.text_tertiary, surface, colors.is_dark)
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
            imageVector = alias_at_icon,
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

private val label_chip_shape = RoundedCornerShape(4.dp)

@Composable
private fun label_chip(color: Color, name: String, icon: String, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    val icon_vector = remember(icon) { if (icon.isNotBlank()) material_icon_from_name(icon) else null }
    val has_name = name.isNotBlank()
    val shape = label_chip_shape
    val surface = inbox_card_read_color(colors)
    val background = remember(color, surface, colors.is_dark) { chip_background(color, surface, colors.is_dark) }
    val content = remember(color, background, colors.is_dark) { chip_content(color, background, colors.is_dark) }
    Row(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
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
            .acrylic(colors, androidx.compose.foundation.shape.RoundedCornerShape(999.dp), colors.bg_secondary)
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

