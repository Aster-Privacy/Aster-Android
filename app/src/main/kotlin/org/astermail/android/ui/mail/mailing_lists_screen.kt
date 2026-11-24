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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.withTimeout
import org.astermail.android.R
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.design.AsterEasing
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterMotionSpec
import org.astermail.android.design.aster_animate_color
import org.astermail.android.design.aster_fade_in
import org.astermail.android.design.aster_fade_out
import org.astermail.android.design.aster_reduce_motion
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.acrylic_backdrop
import org.astermail.android.design.auto_mirrored
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.security.safe_sender_name
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.subscriptions.MailingListsViewModel
import org.astermail.android.subscriptions.build_blocked_sender_index
import org.astermail.android.subscriptions.without_blocked_senders
import org.astermail.android.ui.common.chrome_surface
import org.astermail.android.ui.common.page_surface

@Composable
fun MailingListsScreen(
    on_back: () -> Unit = {},
    on_open_drawer: (() -> Unit)? = null,
    on_open_search: () -> Unit = {},
    on_search_sender: (String) -> Unit = {},
) {
    val vm: MailingListsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val settings_vm = shared_settings_view_model()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    var blocked_requested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        settings_vm.load_blocked_senders()
        blocked_requested = true
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
    val haptics = LocalHapticFeedback.current
    var show_unsubscribed by rememberSaveable { mutableStateOf(false) }
    var selected_ids by remember { mutableStateOf(emptySet<String>()) }
    var confirm_single by remember { mutableStateOf<MailingListSubscription?>(null) }
    var confirm_bulk by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm_delete by remember { mutableStateOf<List<String>>(emptyList()) }

    val blocked_pending = settings_state.blocked_senders.isEmpty() &&
        (!blocked_requested || settings_state.blocked_senders_loading)
    val blocked_index = remember(settings_state.blocked_senders) {
        build_blocked_sender_index(settings_state.blocked_senders.map { it.address to it.is_domain })
    }
    val items = remember(state.items, blocked_index, blocked_pending) {
        if (blocked_pending) {
            emptyList()
        } else {
            without_blocked_senders(state.items, blocked_index) { it.sender_email }
        }
    }
    val (active, unsubscribed) = remember(items) {
        items.partition { it.status == "active" }
    }
    val visible = if (show_unsubscribed) unsubscribed else active
    val visible_ids = remember(visible) { visible.map { it.id }.toSet() }
    val latest_visible = androidx.compose.runtime.rememberUpdatedState(visible)
    val latest_visible_ids = androidx.compose.runtime.rememberUpdatedState(visible_ids)
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
    LaunchedEffect(show_unsubscribed) {
        list_state.scrollToItem(0)
        header_offset_px.floatValue = 0f
    }

    fun subscription_id_at_offset(y: Float): String? {
        val info = list_state.layoutInfo
        val item_y = y + info.viewportStartOffset
        val entry = info.visibleItemsInfo.firstOrNull { entry ->
            item_y >= entry.offset && item_y < entry.offset + entry.size
        } ?: return null
        val key = entry.key as? String ?: return null
        return key.takeIf { it in latest_visible_ids.value }
    }

    fun subscription_index_at_offset(y: Float): Int? {
        val id = subscription_id_at_offset(y) ?: return null
        val idx = latest_visible.value.indexOfFirst { it.id == id }
        return if (idx >= 0) idx else null
    }

    val drag_anchor_index = remember { mutableIntStateOf(-1) }
    val drag_last_index = remember { mutableIntStateOf(-1) }
    val drag_pre_selected = remember { mutableStateListOf<String>() }
    val drag_pointer_y = remember { mutableFloatStateOf(-1f) }
    val drag_viewport_height = remember { mutableFloatStateOf(0f) }
    var drag_selecting by remember { mutableStateOf(false) }

    fun apply_drag_selection(y: Float) {
        val anchor = drag_anchor_index.intValue
        if (anchor < 0) return
        val idx = subscription_index_at_offset(y) ?: return
        if (idx == drag_last_index.intValue) return
        drag_last_index.intValue = idx
        val lo = minOf(anchor, idx)
        val hi = maxOf(anchor, idx)
        val target = LinkedHashSet(drag_pre_selected)
        for (i in lo..hi) {
            latest_visible.value.getOrNull(i)?.id?.let { target.add(it) }
        }
        if (target.size != selected_ids.size || !target.containsAll(selected_ids)) {
            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selected_ids = target
        }
    }

    val drag_edge_px = with(density) { 72.dp.toPx() }
    LaunchedEffect(drag_selecting) {
        if (!drag_selecting) return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val y = drag_pointer_y.floatValue
            val viewport = drag_viewport_height.floatValue
            if (y < 0f || viewport <= 0f) continue
            val info = list_state.layoutInfo
            val content_top = info.beforeContentPadding.toFloat()
            val content_bottom = viewport - info.afterContentPadding.toFloat()
            val usable = content_bottom - content_top
            if (usable <= 0f) continue
            val edge = minOf(drag_edge_px, usable / 3f)
            if (edge <= 0f) continue
            val top_bound = content_top + edge
            val bottom_bound = content_bottom - edge
            val ratio = when {
                y < top_bound -> -((top_bound - y) / edge)
                y > bottom_bound -> (y - bottom_bound) / edge
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (ratio != 0f) {
                list_state.scrollBy(ratio * 26f)
                apply_drag_selection(y.coerceIn(content_top, content_bottom - 1f))
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .page_surface(colors)
            .nestedScroll(header_nested_scroll),
    ) {
        LazyColumn(
            state = list_state,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val press_slop = viewConfiguration.touchSlop
                    val long_press_ms = viewConfiguration.longPressTimeoutMillis
                    val drag_start_slop = 24.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val anchor_id = subscription_id_at_offset(down.position.y)
                        var aborted = anchor_id == null
                        var long_pressed = false
                        if (!aborted) {
                            try {
                                withTimeout(long_press_ms) {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull()
                                        if (change == null || !change.pressed) {
                                            aborted = true
                                            break
                                        }
                                        if ((change.position - down.position).getDistance() > press_slop) {
                                            aborted = true
                                            break
                                        }
                                    }
                                }
                            } catch (_: PointerEventTimeoutCancellationException) {
                                long_pressed = true
                            }
                        }
                        if (!long_pressed || aborted) return@awaitEachGesture
                        var drag_started = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if (drag_started || selected_ids.isNotEmpty()) change.consume()
                                if (!change.pressed) break
                                if (selected_ids.isEmpty()) continue
                                if ((change.position - down.position).getDistance() < drag_start_slop) continue
                                if (!drag_started) {
                                    val anchor_index = latest_visible.value.indexOfFirst { it.id == anchor_id }
                                    if (anchor_index < 0) continue
                                    drag_anchor_index.intValue = anchor_index
                                    drag_last_index.intValue = anchor_index
                                    drag_pre_selected.clear()
                                    drag_pre_selected.addAll(selected_ids)
                                    drag_viewport_height.floatValue = size.height.toFloat()
                                    drag_started = true
                                    drag_selecting = true
                                    change.consume()
                                }
                                drag_pointer_y.floatValue = change.position.y
                                apply_drag_selection(change.position.y)
                            }
                        } finally {
                            drag_selecting = false
                            drag_pointer_y.floatValue = -1f
                            drag_anchor_index.intValue = -1
                            drag_last_index.intValue = -1
                            drag_pre_selected.clear()
                        }
                    }
                },
            contentPadding = PaddingValues(
                top = header_height_dp + AsterSpacing.sm,
                bottom = if (selected_ids.isEmpty()) {
                    AsterSpacing.lg + nav_bar_bottom
                } else {
                    88.dp + nav_bar_bottom
                },
            ),
        ) {
            item(key = "hero") {
                subscription_hero(
                    active_count = active.size,
                    unsubscribed_count = unsubscribed.size,
                    email_count = state.stats?.total_emails_from_subscriptions
                        ?.takeIf { items.size == state.items.size }
                        ?: items.sumOf { it.email_count },
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
                        text = stringResource(R.string.tab_label_with_count, stringResource(R.string.active_tab), active.size),
                        selected = !show_unsubscribed,
                        on_click = { show_unsubscribed = false; selected_ids = emptySet() },
                    )
                    subscription_chip(
                        text = stringResource(R.string.tab_label_with_count, stringResource(R.string.unsubscribed_tab), unsubscribed.size),
                        selected = show_unsubscribed,
                        on_click = { show_unsubscribed = true; selected_ids = emptySet() },
                    )
                }
                Spacer(Modifier.height(AsterSpacing.md))
            }
            when {
                (state.is_loading || state.is_scanning || blocked_pending) && items.isEmpty() -> item(key = "loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(AsterSpacing.xxl),
                        contentAlignment = Alignment.Center,
                    ) {
                        org.astermail.android.design.components.aster_spinner(
                            color = colors.accent_blue,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                state.load_error != null && items.isEmpty() -> item(key = "load_error") {
                    Box(modifier = Modifier.padding(horizontal = inbox_card_horizontal_margin)) {
                        Column {
                            org.astermail.android.ui.settings.detail.load_failed_card(
                                message = state.load_error,
                                on_retry = { vm.load() },
                            )
                        }
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
                        hint = if (show_unsubscribed) null else stringResource(R.string.scan_inbox_hint),
                    )
                }
                else -> itemsIndexed(visible, key = { _, item -> item.id }) { idx, item ->
                    subscription_row(
                        modifier = Modifier.animateItem(fadeInSpec = null),
                        item = item,
                        is_pending = item.id in state.pending_ids,
                        is_selected = item.id in selected_ids,
                        is_first = idx == 0,
                        is_last = idx == visible.lastIndex,
                        selection_active = selected_ids.isNotEmpty(),
                        haptic_enabled = haptic_enabled,
                        on_toggle_select = {
                            selected_ids = toggle_subscription_selection(selected_ids, item.id)
                        },
                        on_open = { on_search_sender(item.sender_email) },
                        on_action = {
                            if (show_unsubscribed) vm.reactivate(item.id) else confirm_single = item
                        },
                        action_icon = if (show_unsubscribed) TablerIcons.ArrowBackUp.auto_mirrored() else TablerIcons.Ban,
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

        org.astermail.android.ui.common.vertical_scroll_bar(
            state = list_state,
            modifier = Modifier.align(Alignment.TopEnd),
            top_inset = header_height_dp,
            bottom_inset = if (selected_ids.isEmpty()) {
                AsterSpacing.lg + nav_bar_bottom
            } else {
                88.dp + nav_bar_bottom
            },
        )

        val has_backdrop = colors.is_translucent
        val header_bg = colors.bg_primary
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, header_offset_px.floatValue.roundToInt()) }
                .onSizeChanged { if (it.height > 0) header_height_px = it.height }
                .drawBehind {
                    if (has_backdrop) return@drawBehind
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
                    on_scan = { vm.scan() },
                    on_cancel_scan = { vm.cancel_scan() },
                    is_scanning = state.is_scanning,
                )
            }
        }

        val select_bar_reduce = aster_reduce_motion()
        val last_select_count = remember { mutableIntStateOf(0) }
        if (selected_ids.isNotEmpty()) last_select_count.intValue = selected_ids.size
        androidx.compose.animation.AnimatedVisibility(
            visible = selected_ids.isNotEmpty(),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = if (select_bar_reduce) {
                aster_fade_in(true)
            } else {
                androidx.compose.animation.slideInVertically(
                    animationSpec = tween(AsterMotionSpec.fade_enter_ms, easing = AsterEasing.emphasized_decelerate),
                    initialOffsetY = { it },
                ) + aster_fade_in()
            },
            exit = if (select_bar_reduce) {
                aster_fade_out(true)
            } else {
                androidx.compose.animation.slideOutVertically(
                    animationSpec = tween(AsterMotionSpec.icon_ms, easing = AsterEasing.emphasized_accelerate),
                    targetOffsetY = { it },
                ) + aster_fade_out()
            },
        ) {
            subscription_select_bar(
                count = last_select_count.intValue,
                is_unsubscribed_tab = show_unsubscribed,
                on_action = { action ->
                    val result = reduce_subscription_selection(selected_ids, visible_ids, action)
                    selected_ids = result.selected
                    result.reactivate_request.forEach { vm.reactivate(it) }
                    if (result.unsubscribe_request.isNotEmpty()) confirm_bulk = result.unsubscribe_request
                    if (result.delete_request.isNotEmpty()) confirm_delete = result.delete_request
                },
            )
        }
    }

    confirm_single?.let { target ->
        AsterAlertDialog(
            on_dismiss = { confirm_single = null },
            title = stringResource(R.string.stop_sender_title),
            message = stringResource(
                R.string.stop_sender_body,
                safe_sender_name(target.sender_name, target.sender_email).ifBlank { target.sender_email },
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
            title = pluralStringResource(R.plurals.stop_senders_title, pending.size, pending.size),
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

    if (confirm_delete.isNotEmpty()) {
        val pending = confirm_delete
        AsterAlertDialog(
            on_dismiss = { confirm_delete = emptyList() },
            title = pluralStringResource(R.plurals.delete_subscriptions_title, pending.size, pending.size),
            message = stringResource(R.string.delete_subscriptions_body),
            confirm_label = stringResource(R.string.delete),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                vm.delete(pending)
                selected_ids = emptySet()
                confirm_delete = emptyList()
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
    on_cancel_scan: () -> Unit,
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
                    auto_mirror = true,
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
                val cancel_label = stringResource(R.string.cancel)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(SquircleShape(24.dp))
                        .clickable { on_cancel_scan() }
                        .semantics { contentDescription = cancel_label },
                    contentAlignment = Alignment.Center,
                ) {
                    org.astermail.android.design.components.aster_spinner(
                        color = colors.accent_blue,
                        stroke_width = 2.dp,
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
        animationSpec = tween(durationMillis = org.astermail.android.design.AsterDuration.long_2),
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
                        color = colors.on_accent.copy(alpha = 0.24f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = top_left,
                        size = arc_size,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    if (fraction > 0f) {
                        drawArc(
                            color = colors.on_accent,
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
                    color = colors.on_accent,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.active_senders),
                    color = colors.on_accent.copy(alpha = 0.86f),
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
            Text(
                text = stringResource(R.string.scanning),
                color = colors.on_accent.copy(alpha = 0.86f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun hero_stat_pill(modifier: Modifier = Modifier, value: String, label: String) {
    val colors = AsterMaterial.colors
    Column(
        modifier = modifier
            .clip(SquircleShape(14.dp))
            .background(colors.on_accent.copy(alpha = 0.18f))
            .padding(horizontal = AsterSpacing.md, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = colors.on_accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            color = colors.on_accent.copy(alpha = 0.8f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun subscription_chip(text: String, selected: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val bg = if (selected) colors.accent_blue else search_field_bg_color(colors)
    val text_color = if (selected) colors.on_accent else colors.text_secondary
    val animated_bg by aster_animate_color(target = bg, label = "subscription_chip_bg")
    val animated_text by aster_animate_color(target = text_color, label = "subscription_chip_text")
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
    modifier: Modifier = Modifier,
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
    val row_bg = aster_animate_color(
        target = if (is_selected) {
            inbox_card_selected_color(colors)
        } else {
            inbox_card_read_color(colors)
        },
        label = "subscription_row_bg",
    )
    val group_shape = remember(is_first, is_last) { inbox_group_shape(is_first, is_last) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = inbox_card_horizontal_margin,
                end = inbox_card_horizontal_margin,
                bottom = if (is_last) 0.dp else inbox_group_split,
            )
            .clip(group_shape)
            .drawBehind { drawRect(row_bg.value) }
            .combinedClickable(
                hapticFeedbackEnabled = false,
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
        val flip_reduce = aster_reduce_motion()
        val flip by animateFloatAsState(
            targetValue = if (is_selected) 180f else 0f,
            animationSpec = tween(
                durationMillis = if (flip_reduce) 0 else AsterMotionSpec.icon_ms,
                easing = AsterEasing.emphasized,
            ),
            label = "subscription_avatar_flip",
        )
        val check_reveal by animateFloatAsState(
            targetValue = if (is_selected) 1f else 0f,
            animationSpec = when {
                !is_selected -> tween(AsterMotionSpec.fade_out_ms, easing = AsterEasing.emphasized_accelerate)
                flip_reduce -> tween(AsterMotionSpec.reduced_fade_ms)
                else -> tween(
                    durationMillis = AsterMotionSpec.icon_ms / 2,
                    delayMillis = AsterMotionSpec.icon_ms / 2,
                    easing = AsterEasing.emphasized_decelerate,
                )
            },
            label = "subscription_check_reveal",
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer {
                    rotationY = flip
                    cameraDistance = 12f * density
                },
            contentAlignment = Alignment.Center,
        ) {
            if (flip > 90f) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .graphicsLayer { rotationY = 180f }
                        .clip(CircleShape)
                        .background(colors.accent_blue),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = colors.on_accent,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                alpha = check_reveal
                                val check_scale = AsterMotionSpec.icon_from + (1f - AsterMotionSpec.icon_from) * check_reveal
                                scaleX = check_scale
                                scaleY = check_scale
                            },
                    )
                }
            } else {
                SenderAvatar(
                    email = item.sender_email,
                    name = item.sender_name,
                    size = 44.dp,
                )
            }
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = safe_sender_name(item.sender_name, item.sender_email).ifBlank { item.sender_email },
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
                            overflow = TextOverflow.Ellipsis,
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
                    append(pluralStringResource(R.plurals.rules_emails_count, item.email_count, item.email_count))
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
    val interaction_source = remember { MutableInteractionSource() }
    val pressed by interaction_source.collectIsPressedAsState()
    val intent = if (is_destructive) colors.danger else colors.accent_blue
    val tint by aster_animate_color(
        target = if (is_loading || pressed) intent else colors.text_secondary,
        label = "subscription_action_tint",
    )
    val container by aster_animate_color(
        target = intent.copy(alpha = if (pressed) 0.2f else 0.1f),
        label = "subscription_action_container",
    )
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(SquircleShape(14.dp))
            .background(container)
            .clickable(
                enabled = !is_loading,
                interactionSource = interaction_source,
                indication = ripple(bounded = true, color = intent),
                onClick = on_click,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (is_loading) {
            org.astermail.android.design.components.aster_spinner(
                color = tint,
                stroke_width = 2.dp,
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
    on_action: (subscription_bar_action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .acrylic_backdrop(colors)
            .chrome_surface(colors),
        color = Color.Transparent,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
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
                subscription_bar_actions(is_unsubscribed_tab).forEach { action ->
                    select_bar_action(
                        icon = when (action) {
                            subscription_bar_action.deselect -> TablerIcons.X
                            subscription_bar_action.select_all -> TablerIcons.Checks
                            subscription_bar_action.unsubscribe -> TablerIcons.Ban
                            subscription_bar_action.reactivate -> TablerIcons.ArrowBackUp.auto_mirrored()
                            subscription_bar_action.delete -> TablerIcons.Trash
                        },
                        label = when (action) {
                            subscription_bar_action.unsubscribe,
                            subscription_bar_action.reactivate,
                            subscription_bar_action.delete,
                            -> stringResource(action.label_res, count)
                            else -> stringResource(action.label_res)
                        },
                        tint = when (action) {
                            subscription_bar_action.unsubscribe,
                            subscription_bar_action.delete,
                            -> colors.danger
                            else -> colors.text_primary
                        },
                        on_click = { on_action(action) },
                    )
                }
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
