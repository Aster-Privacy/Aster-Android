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

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.astermail.android.R
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.subscriptions.MailingListsViewModel

@Composable
fun MailingListsScreen(
    on_back: () -> Unit = {},
    on_open_drawer: (() -> Unit)? = null,
    on_open_search: () -> Unit = {},
    on_search_sender: (String) -> Unit = {},
) {
    val vm: MailingListsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val density = LocalDensity.current

    LaunchedEffect(Unit) {
        vm.load()
        vm.auto_scan_if_empty()
    }

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            vm.clear_message()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            vm.clear_error()
        }
    }

    val haptic_enabled = org.astermail.android.ui.theme.local_accessibility.current.haptic_enabled
    var show_unsubscribed by rememberSaveable { mutableStateOf(false) }
    var selected_ids by remember { mutableStateOf(emptySet<String>()) }
    var confirm_single by remember { mutableStateOf<MailingListSubscription?>(null) }
    var confirm_bulk by remember { mutableStateOf<List<String>>(emptyList()) }

    val active = state.items.filter { it.status == "active" }
    val unsubscribed = state.items.filter { it.status != "active" }
    val visible = if (show_unsubscribed) unsubscribed else active
    val visible_ids = remember(visible) { visible.map { it.id }.toSet() }
    LaunchedEffect(visible_ids) {
        if (selected_ids.any { it !in visible_ids }) selected_ids = selected_ids intersect visible_ids
    }

    val list_state = rememberLazyListState()
    val nav_bar_bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val status_bar_top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var header_height_px by remember { mutableIntStateOf(0) }
    val header_offset_px = remember { mutableFloatStateOf(0f) }
    val header_height_dp = with(density) { header_height_px.toDp() }
    val header_nested_scroll = remember(header_offset_px, list_state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val limit = header_height_px.toFloat()
                if (limit == 0f) return Offset.Zero
                val can_collapse = list_state.canScrollForward ||
                    list_state.canScrollBackward ||
                    header_offset_px.floatValue != 0f
                if (!can_collapse) return Offset.Zero
                val next = (header_offset_px.floatValue + available.y).coerceIn(-limit, 0f)
                if (next != header_offset_px.floatValue) header_offset_px.floatValue = next
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(selected_ids.isNotEmpty()) {
        if (selected_ids.isNotEmpty()) header_offset_px.floatValue = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .nestedScroll(header_nested_scroll),
    ) {
        LazyColumn(
            state = list_state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = header_height_dp + AsterSpacing.sm,
                bottom = 120.dp + nav_bar_bottom,
            ),
        ) {
            item(key = "hero") {
                subscription_hero(
                    active_count = active.size,
                    unsubscribed_count = unsubscribed.size,
                    email_count = state.stats?.total_emails_from_subscriptions
                        ?: state.items.sumOf { it.email_count },
                    is_scanning = state.is_scanning,
                    modifier = Modifier.padding(horizontal = inbox_card_horizontal_margin),
                )
                Spacer(Modifier.height(AsterSpacing.md))
            }
            item(key = "tabs") {
                Row(
                    modifier = Modifier.padding(horizontal = inbox_card_horizontal_margin),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    subscription_chip(
                        text = stringResource(R.string.active_tab) + " (" + active.size + ")",
                        selected = !show_unsubscribed,
                        on_click = { show_unsubscribed = false; selected_ids = emptySet() },
                    )
                    subscription_chip(
                        text = stringResource(R.string.unsubscribed_tab) + " (" + unsubscribed.size + ")",
                        selected = show_unsubscribed,
                        on_click = { show_unsubscribed = true; selected_ids = emptySet() },
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
            }
            when {
                state.is_loading && state.items.isEmpty() -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = colors.accent_blue,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                visible.isEmpty() -> item(key = "empty") {
                    subscription_empty_state(
                        modifier = Modifier.padding(horizontal = inbox_card_horizontal_margin),
                        title = if (show_unsubscribed) {
                            stringResource(R.string.no_unsubscribed_senders)
                        } else {
                            stringResource(R.string.no_mailing_lists)
                        },
                        hint = stringResource(R.string.scan_inbox_hint),
                    )
                }
                else -> itemsIndexed(visible, key = { _, item -> item.id }) { idx, item ->
                    subscription_row(
                        item = item,
                        is_pending = item.id in state.pending_ids,
                        is_selected = item.id in selected_ids,
                        is_first = idx == 0,
                        is_last = idx == visible.lastIndex,
                        selection_active = selected_ids.isNotEmpty(),
                        haptic_enabled = haptic_enabled,
                        on_toggle_select = {
                            selected_ids = if (item.id in selected_ids) {
                                selected_ids - item.id
                            } else {
                                selected_ids + item.id
                            }
                        },
                        on_open = { on_search_sender(item.sender_email) },
                        on_action = {
                            if (show_unsubscribed) vm.reactivate(item.id) else confirm_single = item
                        },
                        action_icon = if (show_unsubscribed) TablerIcons.ArrowBackUp else TablerIcons.Ban,
                        action_label = if (show_unsubscribed) {
                            stringResource(R.string.reactivate)
                        } else {
                            stringResource(R.string.unsubscribe)
                        },
                        is_destructive = !show_unsubscribed,
                    )
                }
            }
        }

        org.astermail.android.ui.common.fast_scroll_bar(
            state = list_state,
            modifier = Modifier.align(Alignment.TopEnd),
            top_padding = header_height_dp,
            bottom_padding = 96.dp + nav_bar_bottom,
        )

        val header_bg = colors.bg_primary
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, header_offset_px.floatValue.roundToInt()) }
                .onSizeChanged { if (it.height > 0) header_height_px = it.height }
                .drawBehind {
                    val limit = header_height_px.toFloat()
                    val fraction = if (limit == 0f) {
                        0f
                    } else {
                        (-header_offset_px.floatValue / limit).coerceIn(0f, 1f)
                    }
                    drawRect(color = header_bg, alpha = 1f - fraction)
                },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(status_bar_top))
                subscription_top_bar(
                    on_back = on_back,
                    on_open_drawer = on_open_drawer,
                    on_open_search = on_open_search,
                    on_scan = { if (!state.is_scanning) vm.scan() },
                    is_scanning = state.is_scanning,
                )
            }
        }

        if (selected_ids.isNotEmpty()) {
            subscription_select_bar(
                count = selected_ids.size,
                is_unsubscribed_tab = show_unsubscribed,
                on_clear = { selected_ids = emptySet() },
                on_select_all = { selected_ids = visible_ids },
                on_action = {
                    val ids = selected_ids.toList()
                    if (show_unsubscribed) {
                        selected_ids = emptySet()
                        ids.forEach { vm.reactivate(it) }
                    } else {
                        confirm_bulk = ids
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    confirm_single?.let { target ->
        AsterAlertDialog(
            on_dismiss = { confirm_single = null },
            title = stringResource(R.string.stop_sender_title),
            message = stringResource(
                R.string.stop_sender_body,
                target.sender_name.ifBlank { target.sender_email },
            ),
            confirm_label = stringResource(R.string.stop_sender_confirm),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                vm.unsubscribe(target.id)
                confirm_single = null
            },
        )
    }

    if (confirm_bulk.isNotEmpty()) {
        val pending = confirm_bulk
        AsterAlertDialog(
            on_dismiss = { confirm_bulk = emptyList() },
            title = stringResource(R.string.stop_senders_title, pending.size),
            message = stringResource(R.string.stop_senders_body),
            confirm_label = stringResource(R.string.stop_sender_confirm),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                vm.bulk_unsubscribe(pending)
                selected_ids = emptySet()
                confirm_bulk = emptyList()
            },
        )
    }
}

@Composable
private fun subscription_top_bar(
    on_back: () -> Unit,
    on_open_drawer: (() -> Unit)?,
    on_open_search: () -> Unit,
    on_scan: () -> Unit,
    is_scanning: Boolean,
) {
    val colors = AsterMaterial.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xs)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (on_open_drawer != null) {
                AsterIconButton(
                    icon = TablerIcons.Menu2,
                    content_description = stringResource(R.string.open_drawer),
                    onClick = on_open_drawer,
                )
            } else {
                AsterIconButton(
                    icon = TablerIcons.ArrowLeft,
                    content_description = stringResource(R.string.back),
                    onClick = on_back,
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .padding(horizontal = AsterSpacing.sm)
                    .clip(SquircleShape(26.dp))
                    .background(search_field_bg_color(colors))
                    .clickable { on_open_search() }
                    .padding(horizontal = AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.search_mail),
                    color = colors.text_secondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (is_scanning) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = colors.accent_blue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                AsterIconButton(
                    icon = TablerIcons.Refresh,
                    content_description = stringResource(R.string.scan_inbox),
                    onClick = on_scan,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.lg, end = AsterSpacing.sm)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.subscriptions),
                color = colors.text_secondary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun subscription_hero(
    active_count: Int,
    unsubscribed_count: Int,
    email_count: Int,
    is_scanning: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val total = active_count + unsubscribed_count
    val target_fraction = if (total == 0) 0f else active_count.toFloat() / total.toFloat()
    val fraction by animateFloatAsState(
        targetValue = target_fraction,
        animationSpec = tween(durationMillis = 450),
        label = "subscription_gauge",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.accent_blue,
                        colors.accent_blue.copy(alpha = 0.78f),
                    ),
                ),
            )
            .padding(AsterSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(92.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 11.dp.toPx()
                val diameter = minOf(size.width, size.height * 2f) - stroke
                if (diameter > 0f) {
                    val arc_size = Size(diameter, diameter)
                    val top_left = Offset(
                        (size.width - diameter) / 2f,
                        size.height - stroke / 2f - diameter / 2f,
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.24f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = top_left,
                        size = arc_size,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (fraction > 0f) {
                        drawArc(
                            color = Color.White,
                            startAngle = 180f,
                            sweepAngle = 180f * fraction,
                            useCenter = false,
                            topLeft = top_left,
                            size = arc_size,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                        )
                    }
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                Text(
                    text = active_count.toString(),
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.active_senders),
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Spacer(Modifier.height(AsterSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            hero_stat_pill(
                modifier = Modifier.weight(1f),
                value = unsubscribed_count.toString(),
                label = stringResource(R.string.stat_unsubscribed),
            )
            hero_stat_pill(
                modifier = Modifier.weight(1f),
                value = email_count.toString(),
                label = stringResource(R.string.stat_emails),
            )
        }
        if (is_scanning) {
            Spacer(Modifier.height(AsterSpacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.scanning),
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun hero_stat_pill(modifier: Modifier = Modifier, value: String, label: String) {
    Column(
        modifier = modifier
            .clip(SquircleShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun subscription_chip(text: String, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (selected) {
        colors.accent_blue
    } else if (colors.is_dark) {
        colors.input_bg
    } else {
        colors.bg_secondary
    }
    val text_color = if (selected) Color.White else colors.text_secondary
    val animated_bg by animateColorAsState(
        targetValue = bg,
        animationSpec = tween(150),
        label = "subscription_chip_bg",
    )
    val animated_text by animateColorAsState(
        targetValue = text_color,
        animationSpec = tween(150),
        label = "subscription_chip_text",
    )
    Box(
        modifier = Modifier
            .clip(SquircleShape(999.dp))
            .background(animated_bg)
            .clickable(onClick = on_click)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = animated_text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun subscription_empty_state(title: String, hint: String?, modifier: Modifier = Modifier) {
    val colors = AsterMaterial.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(SquircleShape(inbox_group_corner))
            .background(inbox_card_read_color(colors)),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = TablerIcons.MailOpened,
                contentDescription = null,
                tint = colors.text_tertiary,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(AsterSpacing.md))
            Text(
                text = title,
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (hint != null) {
                Spacer(Modifier.height(AsterSpacing.xs))
                Text(
                    text = hint,
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun subscription_row(
    item: MailingListSubscription,
    is_pending: Boolean,
    is_selected: Boolean,
    is_first: Boolean,
    is_last: Boolean,
    selection_active: Boolean,
    haptic_enabled: Boolean,
    on_toggle_select: () -> Unit,
    on_open: () -> Unit,
    on_action: () -> Unit,
    action_icon: androidx.compose.ui.graphics.vector.ImageVector,
    action_label: String,
    is_destructive: Boolean,
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val interaction_source = remember { MutableInteractionSource() }
    val row_bg = animateColorAsState(
        targetValue = if (is_selected) {
            inbox_card_selected_color(colors)
        } else {
            inbox_card_read_color(colors)
        },
        animationSpec = tween(durationMillis = 220),
        label = "subscription_row_bg",
    )
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(group_shape)
            .drawBehind { drawRect(row_bg.value) }
            .combinedClickable(
                interactionSource = interaction_source,
                indication = ripple(),
                onClick = { if (selection_active) on_toggle_select() else on_open() },
                onLongClick = {
                    if (haptic_enabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    on_toggle_select()
                },
            )
            .defaultMinSize(minHeight = 88.dp)
            .padding(
                start = inbox_card_content_padding,
                end = AsterSpacing.sm,
                top = AsterSpacing.md,
                bottom = AsterSpacing.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (is_selected) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(colors.accent_blue),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            SenderAvatar(
                email = item.sender_email,
                name = item.sender_name,
                size = 44.dp,
            )
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.sender_name.ifBlank { item.sender_email },
                    color = colors.text_primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.risk_level == "risky") {
                    Spacer(Modifier.width(AsterSpacing.xs))
                    Box(
                        modifier = Modifier
                            .clip(SquircleShape(6.dp))
                            .background(colors.danger.copy(alpha = 0.12f))
                            .padding(horizontal = AsterSpacing.xs, vertical = 1.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.risky),
                            color = colors.danger,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
            Text(
                text = item.sender_email,
                color = colors.text_tertiary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(stringResource(R.string.rules_emails_count, item.email_count))
                    if (item.category.isNotBlank() && item.category != "unknown") {
                        append(" · ")
                        append(item.category.replaceFirstChar { it.uppercase() })
                    }
                },
                color = colors.text_muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(AsterSpacing.xs))
        row_action_button(
            icon = action_icon,
            label = action_label,
            is_destructive = is_destructive,
            is_loading = is_pending,
            on_click = on_action,
        )
    }
}

@Composable
private fun row_action_button(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    is_destructive: Boolean,
    is_loading: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val tint = if (is_destructive) colors.danger else colors.accent_blue
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f))
            .clickable(enabled = !is_loading, onClick = on_click),
        contentAlignment = Alignment.Center,
    ) {
        if (is_loading) {
            CircularProgressIndicator(
                color = tint,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun subscription_select_bar(
    count: Int,
    is_unsubscribed_tab: Boolean,
    on_clear: () -> Unit,
    on_select_all: () -> Unit,
    on_action: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Surface(modifier = modifier.fillMaxWidth(), color = colors.bg_primary) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AsterDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                select_bar_action(
                    icon = TablerIcons.X,
                    label = stringResource(R.string.clear),
                    on_click = on_clear,
                )
                select_bar_action(
                    icon = TablerIcons.Checks,
                    label = stringResource(R.string.select_all),
                    on_click = on_select_all,
                )
                select_bar_action(
                    icon = if (is_unsubscribed_tab) TablerIcons.ArrowBackUp else TablerIcons.Ban,
                    label = if (is_unsubscribed_tab) {
                        stringResource(R.string.reactivate_selected, count)
                    } else {
                        stringResource(R.string.unsubscribe_selected, count)
                    },
                    tint = if (is_unsubscribed_tab) colors.text_primary else colors.danger,
                    on_click = on_action,
                )
            }
        }
    }
}

@Composable
private fun RowScope.select_bar_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on_click: () -> Unit,
    tint: Color = AsterMaterial.colors.text_primary,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(SquircleShape(18.dp))
            .clickable(onClick = on_click)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
