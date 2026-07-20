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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import org.astermail.android.R
import org.astermail.android.debugtools.debug_build_pill_inline
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.mail.MailViewModel
import org.astermail.android.settings.SettingsViewModel

enum class InboxSortMode { newest, oldest, unread_first, starred_first }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    on_open_drawer: () -> Unit,
    on_open_search: () -> Unit,
    on_compose: () -> Unit,
    on_compose_draft: (String) -> Unit = {},
    on_view_pending_send: () -> Unit = {},
    on_open_email: (String) -> Unit,
    on_open_settings: () -> Unit = {},
    on_open_upgrade: () -> Unit = {},
    current_folder: String = "inbox",
    display_title: String? = null,
    inbox_category: String = "primary",
    on_folder_change: (String) -> Unit = {},
    custom_folders: List<Pair<String, String>> = emptyList(),
    on_custom_folder_change: (String, String) -> Unit = { _, _ -> },
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val list_state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val mail_vm: MailViewModel = hiltViewModel()
    val settings_vm: SettingsViewModel = hiltViewModel()
    val inbox_state by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val attachment_chips by mail_vm.inbox_attachment_chips.collectAsStateWithLifecycle()
    val settings_state by settings_vm.state.collectAsStateWithLifecycle()
    val haptic_enabled = settings_state.preferences?.haptic_enabled ?: true
    val context_for_prefs = LocalContext.current
    val plan_prefs = remember { context_for_prefs.getSharedPreferences("aster_plan", android.content.Context.MODE_PRIVATE) }
    val initial_paid = remember { plan_prefs.getBoolean("has_paid", false) }
    val initial_plan_known = remember { plan_prefs.getBoolean("plan_known", false) }
    var cached_paid by rememberSaveable { mutableStateOf(initial_paid) }
    var plan_known by rememberSaveable { mutableStateOf(initial_plan_known) }
    var fresh_check_complete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settings_vm.load_subscription(force = false) }
    LaunchedEffect(settings_state.subscription, settings_state.is_loading) {
        val sub = settings_state.subscription
        if (sub != null) {
            val paid = (sub.effective_price_cents) > 0 &&
                sub.status !in setOf("canceled", "cancelled", "incomplete_expired", "unpaid")
            if (paid != cached_paid || !plan_known) {
                cached_paid = paid
                plan_known = true
                plan_prefs.edit().putBoolean("has_paid", paid).putBoolean("plan_known", true).apply()
            }
            fresh_check_complete = true
        } else if (!settings_state.is_loading && plan_known) {
            fresh_check_complete = true
        }
    }
    val has_paid_plan = cached_paid ||
        ((settings_state.subscription?.effective_price_cents ?: 0) > 0 &&
            settings_state.subscription?.status !in setOf("canceled", "cancelled", "incomplete_expired", "unpaid"))
    val show_upgrade_button = plan_known && fresh_check_complete && !has_paid_plan
    val prefetch_context = LocalContext.current
    val toast_context = LocalContext.current

    val send_problem by mail_vm.send_problem.collectAsStateWithLifecycle()
    if (send_problem) {
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { mail_vm.dismiss_send_problem() },
            title = stringResource(R.string.send_problem_title),
            message = stringResource(R.string.send_problem_message),
            footer = {
                org.astermail.android.design.components.AsterDialogOutlineButton(
                    label = stringResource(R.string.ok),
                    onClick = { mail_vm.dismiss_send_problem() },
                )
            },
        )
    }

    var top_toast_state by remember { mutableStateOf<org.astermail.android.ui.common.TopToastState?>(null) }
    LaunchedEffect(mail_vm) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            mail_vm.foreground_fallback_tick()
        }
    }
    LaunchedEffect(mail_vm) {
        mail_vm.toast_events.collect { evt ->
            top_toast_state = org.astermail.android.ui.common.TopToastState(
                message = evt.message,
                undo_label = evt.undo_label,
                on_undo = evt.on_undo,
                duration_ms = evt.duration_ms,
                on_timeout = evt.on_timeout,
            )
        }
    }
    val batch_action by mail_vm.batch_action_state.collectAsStateWithLifecycle()
    LaunchedEffect(batch_action) {
        val ba = batch_action
        if (ba == null) {
            if (top_toast_state?.accumulation_key != null) top_toast_state = null
            return@LaunchedEffect
        }
        val current = top_toast_state
        if (current != null && current.accumulation_key == ba.action_key) {
            top_toast_state = current.copy(
                message = ba.message,
                on_undo = { ba.on_undo(); mail_vm.clear_batch_action(ba.action_key) },
                key = System.currentTimeMillis(),
            )
        } else {
            top_toast_state = org.astermail.android.ui.common.TopToastState(
                message = ba.message,
                undo_label = ba.undo_label,
                on_undo = { ba.on_undo(); mail_vm.clear_batch_action(ba.action_key) },
                on_timeout = { mail_vm.clear_batch_action(ba.action_key) },
                on_close = { mail_vm.clear_batch_action(ba.action_key) },
                accumulation_key = ba.action_key,
            )
        }
    }
    val pending_undo_send by mail_vm.pending_undo_send.collectAsStateWithLifecycle()
    var dismissed_send_id by remember { mutableStateOf<Long?>(null) }
    var send_toast_shown by remember { mutableStateOf(false) }
    LaunchedEffect(pending_undo_send?.started_at_ms) {
        val p = pending_undo_send
        if (p == null) {
            if (send_toast_shown) {
                top_toast_state = null
                send_toast_shown = false
            }
            return@LaunchedEffect
        }
        val end_ms = p.started_at_ms + p.duration_ms
        while (true) {
            if (dismissed_send_id == p.started_at_ms) {
                top_toast_state = null
                send_toast_shown = false
                return@LaunchedEffect
            }
            val now = System.currentTimeMillis()
            val remaining_ms = end_ms - now
            if (remaining_ms <= 0) break
            val seconds_left = ((remaining_ms + 999) / 1000).toInt().coerceAtLeast(1)
            val view_action: () -> Unit = { on_view_pending_send() }
            top_toast_state = org.astermail.android.ui.common.TopToastState(
                message = toast_context.getString(R.string.sending_in_countdown, seconds_left),
                undo_label = toast_context.getString(R.string.undo),
                on_undo = { p.undo() },
                secondary_label = toast_context.getString(R.string.view_message),
                on_secondary = view_action,
                on_tap = view_action,
                show_close = true,
                on_close = { dismissed_send_id = p.started_at_ms },
                duration_ms = remaining_ms,
                key = p.started_at_ms,
            )
            send_toast_shown = true
            kotlinx.coroutines.delay(1000L - (remaining_ms % 1000L))
        }
        top_toast_state = null
        send_toast_shown = false
    }

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        var initial_resume_replayed = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!initial_resume_replayed) {
                    initial_resume_replayed = true
                    return@LifecycleEventObserver
                }
                settings_vm.load_preferences()
                settings_vm.load_tags()
                mail_vm.load_inbox(current_folder, force = true)
            }
        }
        lifecycle_owner.lifecycle.addObserver(observer)
        onDispose { lifecycle_owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(current_folder) {
        mail_vm.load_inbox(current_folder)
        mail_vm.load_stats(force = false)
        settings_vm.load_tags(force = false)
    }

    val attachment_ids_key = remember(inbox_state.items) {
        inbox_state.items.filter { it.has_attachments }.map { it.id }
    }
    LaunchedEffect(attachment_ids_key) {
        if (attachment_ids_key.isNotEmpty()) {
            mail_vm.load_inbox_attachment_chips(attachment_ids_key)
        }
    }

    val api_emails = remember(inbox_state.items, settings_state.tags) {
        inbox_state.items.map { inbox_item_to_email(it, settings_state.tags) }
    }
    val emails = remember { mutableStateListOf<Email>() }
    LaunchedEffect(api_emails) {
        val by_id = emails.associateBy { it.id }
        val merged = api_emails.map { server ->
            val local = by_id[server.id] ?: return@map server
            server.copy(
                is_read = if (local.is_read != server.is_read) local.is_read else server.is_read,
                is_starred = if (local.is_starred != server.is_starred) local.is_starred else server.is_starred,
                is_pinned = if (local.is_pinned != server.is_pinned) local.is_pinned else server.is_pinned,
            )
        }
        if (merged != emails.toList()) {
            emails.clear()
            emails.addAll(merged)
        }
    }
    val is_refreshing = inbox_state.is_refreshing
    var sort_mode_user_set by remember { mutableStateOf(false) }
    var sort_mode by remember { mutableStateOf(InboxSortMode.newest) }
    var select_mode by remember { mutableStateOf(false) }
    var select_all_active by remember { mutableStateOf(false) }
    val selected_ids = remember { mutableStateListOf<String>() }
    var show_empty_trash_dialog by remember { mutableStateOf(false) }
    var confirm_action_pending by remember { mutableStateOf<String?>(null) }
    var confirm_item_ids_pending by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm_thread_id_pending by remember { mutableStateOf<String?>(null) }

    val scrolled_elevation = list_state.firstVisibleItemScrollOffset > 0 ||
        list_state.firstVisibleItemIndex > 0

    val sticky_participants = remember(current_folder) { mutableMapOf<String, List<Pair<String, String>>>() }
    val cached_participants by mail_vm.thread_participants.collectAsStateWithLifecycle()

    val categories_enabled = current_folder == "inbox" &&
        (settings_state.preferences?.inbox_categories_enabled ?: true)
    val active_category = inbox_category
    val emails_fingerprint = run {
        var hash = emails.size
        emails.forEach { e ->
            hash = 31 * hash + e.id.hashCode()
            hash = 31 * hash + (if (e.is_starred) 1 else 0)
            hash = 31 * hash + (if (e.is_read) 2 else 0)
            hash = 31 * hash + (if (e.is_pinned) 4 else 0)
        }
        hash
    }
    val category_source = remember(emails_fingerprint, categories_enabled, active_category) {
        if (categories_enabled) {
            emails.filter { org.astermail.android.mail.category_for_tab(it.category) == active_category }
        } else {
            emails.toList()
        }
    }
    val threads = androidx.compose.runtime.remember(emails_fingerprint, categories_enabled, active_category, sort_mode, cached_participants) {
        val grouped_raw = group_by_thread(category_source)
        val live_ids = grouped_raw.map { it.thread_id }.toHashSet()
        sticky_participants.keys.retainAll(live_ids)
        val grouped = grouped_raw.map { row ->
            val from_cache = cached_participants[row.thread_id]
            val existing = sticky_participants[row.thread_id]
            val candidates = listOfNotNull(from_cache, existing, row.participants)
            val merged = candidates.maxByOrNull { it.size } ?: row.participants
            sticky_participants[row.thread_id] = merged
            if (merged === row.participants) row else row.copy(participants = merged)
        }
        when (sort_mode) {
            InboxSortMode.newest -> grouped.sortedWith(
                compareByDescending<ThreadRow> { it.is_pinned }.thenByDescending { it.newest.received_at },
            )
            InboxSortMode.oldest -> grouped.sortedWith(
                compareByDescending<ThreadRow> { it.is_pinned }.thenBy { it.newest.received_at },
            )
            InboxSortMode.unread_first -> grouped.sortedWith(
                compareByDescending<ThreadRow> { it.is_pinned }.thenByDescending { it.has_unread }.thenByDescending { it.newest.received_at },
            )
            InboxSortMode.starred_first -> grouped.sortedWith(
                compareByDescending<ThreadRow> { it.is_pinned }.thenByDescending { it.is_starred }.thenByDescending { it.newest.received_at },
            )
        }
    }

    var last_scroll_reset_key by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(sort_mode, current_folder) {
        val reset_key = "$sort_mode|$current_folder"
        if (last_scroll_reset_key.isNotEmpty() && last_scroll_reset_key != reset_key) {
            list_state.scrollToItem(0)
        }
        last_scroll_reset_key = reset_key
    }

    LaunchedEffect(settings_state.preferences?.conversation_order) {
        if (!sort_mode_user_set) {
            sort_mode = when (settings_state.preferences?.conversation_order) {
                "oldest" -> InboxSortMode.oldest
                else -> InboxSortMode.newest
            }
        }
    }

    val folder_count = when (current_folder) {
        "inbox" -> inbox_state.stats?.unread ?: 0
        "sent" -> inbox_state.stats?.sent ?: 0
        "drafts" -> inbox_state.stats?.drafts ?: 0
        "starred" -> inbox_state.stats?.starred ?: 0
        "archive" -> inbox_state.stats?.archived ?: 0
        "scheduled" -> inbox_state.stats?.scheduled ?: 0
        "spam" -> inbox_state.stats?.spam ?: 0
        "trash" -> inbox_state.stats?.trash ?: 0
        else -> 0
    }
    val visible_threads = threads

    val visible_order_ids = remember(visible_threads) { visible_threads.map { it.newest.id } }
    LaunchedEffect(visible_order_ids) {
        mail_vm.set_visible_order(visible_order_ids)
    }

    LaunchedEffect(select_all_active, visible_order_ids) {
        if (select_all_active) {
            selected_ids.clear()
            selected_ids.addAll(visible_threads.map { it.thread_id })
        }
    }

    LaunchedEffect(list_state, current_folder) {
        snapshotFlow {
            val layout_info = list_state.layoutInfo
            val total = layout_info.totalItemsCount
            val last_visible = layout_info.visibleItemsInfo.lastOrNull()?.index ?: 0
            total > 0 && (total - last_visible) <= 3
        }.distinctUntilChanged().collect { near_end ->
            val s = inbox_state
            if (near_end &&
                s.has_more &&
                !s.is_loading &&
                !s.is_loading_more &&
                !s.initial &&
                s.items.isNotEmpty() &&
                s.next_cursor != null &&
                s.current_folder == current_folder
            ) {
                mail_vm.load_more()
            }
        }
    }

    LaunchedEffect(categories_enabled, active_category, emails_fingerprint, inbox_state.has_more, inbox_state.is_loading, inbox_state.is_loading_more) {
        val s = inbox_state
        if (categories_enabled &&
            s.has_more &&
            !s.is_loading &&
            !s.is_loading_more &&
            !s.initial &&
            s.next_cursor != null &&
            s.current_folder == current_folder
        ) {
            val in_tab = emails.count {
                org.astermail.android.mail.category_for_tab(it.category) == active_category
            }
            if (in_tab < 15) mail_vm.load_more()
        }
    }

    fun do_refresh() {
        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        mail_vm.refresh()
    }

    fun mark_all_read(target_read: Boolean) {
        if (target_read) {
            mail_vm.mark_all_read_scope(current_folder)
            for (i in emails.indices) {
                if (!emails[i].is_read) emails[i] = emails[i].copy(is_read = true)
            }
        } else {
            mail_vm.mark_all_unread_scope(current_folder)
            for (i in emails.indices) {
                emails[i] = emails[i].copy(is_read = false)
            }
        }
    }

    fun select_all() {
        select_mode = true
        selected_ids.clear()
        selected_ids.addAll(visible_threads.map { it.thread_id })
        if (inbox_state.has_more) {
            select_all_active = true
            mail_vm.load_all_remaining()
        }
    }

    fun exit_select_mode() {
        select_mode = false
        select_all_active = false
        selected_ids.clear()
    }

    fun toggle_selection(id: String) {
        select_all_active = false
        if (selected_ids.contains(id)) selected_ids.remove(id) else selected_ids.add(id)
        if (selected_ids.isEmpty()) select_mode = false
    }

    fun thread_id_at_offset(y: Float): String? {
        val info = list_state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { item ->
            y >= item.offset && y < item.offset + item.size
        } ?: return null
        val key = item.key as? String ?: return null
        if (key.startsWith("_")) return null
        return key
    }

    fun selected_email_ids(): List<String> {
        val thread_ids = selected_ids.toSet()
        return emails.filter { it.thread_id in thread_ids }.map { it.id }
    }

    fun archive_selected() {
        val ids = selected_email_ids()
        val thread_count = selected_ids.size
        val to_remove = selected_ids.toSet()
        mail_vm.archive(ids, thread_count)
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    fun delete_selected() {
        val ids = selected_email_ids()
        val thread_count = selected_ids.size
        val to_remove = selected_ids.toSet()
        mail_vm.trash(ids, thread_count)
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    fun restore_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        mail_vm.restore_trash(ids)
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    fun unarchive_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        mail_vm.unarchive(ids)
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    fun unmark_spam_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        mail_vm.unmark_spam(ids)
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    fun mark_spam_selected() {
        val ids = selected_email_ids()
        val thread_count = selected_ids.size
        val to_remove = selected_ids.toSet()
        mail_vm.mark_spam(ids, thread_count)
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    fun delete_permanent_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        ids.forEach { mail_vm.delete_permanent(it) }
        emails.removeAll { it.thread_id in to_remove }
        exit_select_mode()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            androidx.compose.animation.Crossfade(targetState = select_mode, label = "topbar_mode") { mode ->
                if (mode) {
                    select_mode_top_bar(
                        selected_count = selected_ids.size,
                        on_close = ::exit_select_mode,
                        on_select_all = ::select_all,
                        on_archive = ::archive_selected,
                        on_delete = ::delete_selected,
                        on_restore = ::restore_selected,
                        on_unarchive = ::unarchive_selected,
                        on_unmark_spam = ::unmark_spam_selected,
                        on_delete_permanent = ::delete_permanent_selected,
                        on_mark_read = {
                            val thread_ids = selected_ids.toSet()
                            val email_ids = emails
                                .filter { it.thread_id in thread_ids && !it.is_read }
                                .map { it.id }
                            if (email_ids.isNotEmpty()) {
                                mail_vm.mark_read_bulk(email_ids)
                            }
                            for (i in emails.indices) {
                                if (emails[i].thread_id in thread_ids && !emails[i].is_read) {
                                    emails[i] = emails[i].copy(is_read = true)
                                }
                            }
                            exit_select_mode()
                        },
                        show_divider = scrolled_elevation,
                        current_folder = current_folder,
                    )
                } else {
                    inbox_top_bar(
                        folder_title = display_title ?: folder_display_name(current_folder),
                        unread_count = folder_count,
                        on_open_drawer = on_open_drawer,
                        on_open_search = on_open_search,
                        on_enter_select_mode = {
                            select_mode = true
                            selected_ids.clear()
                        },
                        on_refresh = ::do_refresh,
                        on_mark_all_read = { target_read -> mark_all_read(target_read) },
                        has_unread = (inbox_state.stats?.unread ?: 0) > 0,
                        on_select_all = ::select_all,
                        on_open_settings = on_open_settings,
                        on_open_upgrade = on_open_upgrade,
                        show_upgrade = show_upgrade_button,
                        on_empty_trash = { show_empty_trash_dialog = true },
                        sort_mode = sort_mode,
                        on_sort_change = { sort_mode = it; sort_mode_user_set = true },
                        show_divider = scrolled_elevation,
                        current_folder = current_folder,
                        on_folder_change = on_folder_change,
                        custom_folders = custom_folders,
                        on_custom_folder_change = on_custom_folder_change,
                    )
                }
            }

            val pull_state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = is_refreshing && !select_mode,
                onRefresh = { if (!select_mode) do_refresh() },
                modifier = Modifier.fillMaxSize(),
                state = pull_state,
                indicator = {
                    androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator(
                        state = pull_state,
                        isRefreshing = is_refreshing && !select_mode,
                        containerColor = colors.bg_card,
                        color = colors.accent_blue,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                if (inbox_state.initial || (inbox_state.is_loading && threads.isEmpty())) {
                    inbox_skeleton()
                } else if (threads.isEmpty() && inbox_state.error != null) {
                    inbox_error_state(inbox_state.error.orEmpty()) {
                        mail_vm.load_inbox(current_folder, force = true)
                    }
                } else if (threads.isEmpty()) {
                    empty_inbox_state(current_folder)
                } else {
                    val user_prefs_outer = settings_state.preferences
                    val right_action_outer = user_prefs_outer?.swipe_right_action ?: "archive"
                    val left_action_outer = user_prefs_outer?.swipe_left_action ?: "trash"
                    val right_label_outer = swipe_action_label(right_action_outer)
                    val left_label_outer = swipe_action_label(left_action_outer)
                    val restore_label_outer = stringResource(R.string.swipe_restore)
                    val delete_label_outer = stringResource(R.string.swipe_delete)
                    val delete_forever_label_outer = stringResource(R.string.swipe_delete_forever)
                    val not_spam_label_outer = stringResource(R.string.swipe_not_spam)
                    val hoisted_swipe_config = remember(
                        current_folder, right_action_outer, left_action_outer,
                        right_label_outer, left_label_outer, restore_label_outer,
                        delete_label_outer, delete_forever_label_outer, not_spam_label_outer,
                    ) {
                        when (current_folder) {
                            "archive" -> SwipeConfig(
                                start_label = restore_label_outer, end_label = delete_label_outer,
                                start_icon = Icons.Filled.Inbox, end_icon = Icons.Filled.Delete,
                                start_action = "unarchive", end_action = "trash",
                            )
                            "trash" -> SwipeConfig(
                                start_label = restore_label_outer, end_label = delete_forever_label_outer,
                                start_icon = Icons.Filled.Inbox, end_icon = Icons.Filled.Delete,
                                start_action = "restore_trash", end_action = "delete_permanent",
                            )
                            "spam" -> SwipeConfig(
                                start_label = not_spam_label_outer, end_label = delete_label_outer,
                                start_icon = Icons.Filled.Inbox, end_icon = Icons.Filled.Delete,
                                start_action = "unmark_spam", end_action = "trash",
                            )
                            else -> SwipeConfig(
                                start_label = right_label_outer,
                                end_label = left_label_outer,
                                start_icon = swipe_action_icon(right_action_outer),
                                end_icon = swipe_action_icon(left_action_outer),
                                start_action = right_action_outer,
                                end_action = left_action_outer,
                            )
                        }
                    }
                    LazyColumn(
                        state = list_state,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(visible_threads) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val id = thread_id_at_offset(offset.y)
                                        if (id != null) {
                                            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            if (!select_mode) {
                                                select_mode = true
                                                selected_ids.clear()
                                            }
                                            if (!selected_ids.contains(id)) selected_ids.add(id)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val id = thread_id_at_offset(change.position.y)
                                        if (id != null && !selected_ids.contains(id)) {
                                            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selected_ids.add(id)
                                        }
                                    },
                                    onDragEnd = {},
                                    onDragCancel = {},
                                )
                            },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
                    ) {
                        itemsIndexed(items = visible_threads, key = { _, item -> item.thread_id }, contentType = { _, _ -> "thread_row" }) { row_index, thread ->
                            val is_selected = select_mode && selected_ids.contains(thread.thread_id)
                            if (select_mode) {
                                val toggle_label = stringResource(if (is_selected) R.string.inbox_a11y_deselect_thread else R.string.inbox_a11y_select_thread)
                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth()
                                        .background(
                                            if (is_selected) colors.bg_selected else colors.bg_primary,
                                        )
                                        .clickable(onClickLabel = toggle_label) { toggle_selection(thread.thread_id) },
                                ) {
                                    ThreadInboxRow(
                                        thread = thread,
                                        on_click = { toggle_selection(thread.thread_id) },
                                        on_long_click = { toggle_selection(thread.thread_id) },
                                        on_toggle_star = {
                                            val idx = emails.indexOfFirst { it.thread_id == thread.thread_id }
                                            if (idx >= 0) {
                                                emails[idx] = emails[idx].copy(is_starred = !emails[idx].is_starred)
                                                mail_vm.toggle_star(thread.newest.id)
                                            }
                                        },
                                        is_selected = is_selected,
                                        attachment_chips = attachment_chips[thread.newest.id].orEmpty(),
                                        haptic_enabled = haptic_enabled,
                                        row_index = row_index,
                                        user_prefs = settings_state.preferences,
                                    )
                                }
                            } else {
                                val swipe_config = hoisted_swipe_config
                                swipeable_thread_row(
                                    modifier = Modifier.animateItem(),
                                    thread = thread,
                                    row_index = row_index,
                                    is_pinned = thread.is_pinned,
                                    on_click = { on_open_email(thread.newest.id) },
                                    on_long_click = {
                                        select_mode = true
                                        selected_ids.clear()
                                        selected_ids.add(thread.thread_id)
                                    },
                                    on_toggle_star = {
                                        val idx = emails.indexOfFirst { it.thread_id == thread.thread_id }
                                        if (idx >= 0) {
                                            emails[idx] = emails[idx].copy(is_starred = !emails[idx].is_starred)
                                            mail_vm.toggle_star(thread.newest.id)
                                        }
                                    },
                                    attachment_chips = attachment_chips[thread.newest.id].orEmpty(),
                                    swipe_start_action = swipe_config.start_action,
                                    swipe_end_action = swipe_config.end_action,
                                    swipe_start_label = swipe_config.start_label,
                                    swipe_end_label = swipe_config.end_label,
                                    swipe_start_icon = swipe_config.start_icon,
                                    swipe_end_icon = swipe_config.end_icon,
                                    swipe_start_color = swipe_action_color(swipe_config.start_action, colors),
                                    swipe_end_color = swipe_action_color(swipe_config.end_action, colors),
                                    on_swipe_start = {
                                        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val ids = emails.filter { it.thread_id == thread.thread_id }.map { it.id }
                                        val prefs = settings_state.preferences
                                        val needs_confirm = (swipe_config.start_action == "archive" && prefs?.confirm_archive == true) ||
                                            (swipe_config.start_action == "trash" && prefs?.confirm_delete == true) ||
                                            (swipe_config.start_action == "spam" && prefs?.confirm_spam == true) ||
                                            (swipe_config.start_action == "delete_permanent" && prefs?.confirm_delete == true)
                                        if (needs_confirm) {
                                            confirm_action_pending = swipe_config.start_action
                                            confirm_item_ids_pending = ids
                                            confirm_thread_id_pending = thread.thread_id
                                        } else {
                                            execute_swipe_action(swipe_config.start_action, ids, mail_vm, emails, thread.thread_id, current_folder)
                                        }
                                    },
                                    on_swipe_end = {
                                        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val ids = emails.filter { it.thread_id == thread.thread_id }.map { it.id }
                                        val prefs = settings_state.preferences
                                        val needs_confirm = (swipe_config.end_action == "archive" && prefs?.confirm_archive == true) ||
                                            (swipe_config.end_action == "trash" && prefs?.confirm_delete == true) ||
                                            (swipe_config.end_action == "spam" && prefs?.confirm_spam == true) ||
                                            (swipe_config.end_action == "delete_permanent" && prefs?.confirm_delete == true)
                                        if (needs_confirm) {
                                            confirm_action_pending = swipe_config.end_action
                                            confirm_item_ids_pending = ids
                                            confirm_thread_id_pending = thread.thread_id
                                        } else {
                                            execute_swipe_action(swipe_config.end_action, ids, mail_vm, emails, thread.thread_id, current_folder)
                                        }
                                    },
                                    haptic_enabled = haptic_enabled,
                                    user_prefs = settings_state.preferences,
                                )
                            }
                        }

                        if (inbox_state.is_loading_more) {
                            item(key = "_loading_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(vertical = AsterSpacing.lg),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = colors.accent_blue,
                                        strokeWidth = 2.dp,
                                    )
                                }
                            }
                        } else if (!inbox_state.has_more && visible_threads.isNotEmpty()) {
                            item(key = "_no_more") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().animateItem(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    AsterDivider(modifier = Modifier.fillMaxWidth())
                                    Spacer(Modifier.height(AsterSpacing.xl))
                                    Text(
                                        text = stringResource(R.string.all_caught_up),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.text_muted,
                                    )
                                    Spacer(Modifier.height(AsterSpacing.xl))
                                }
                            }
                        }
                    }
                }
            }
        }

        org.astermail.android.ui.common.top_toast_overlay(
            state = top_toast_state,
            on_dismiss = { top_toast_state = null },
        )

        androidx.compose.animation.AnimatedVisibility(
            visible = select_mode,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
            ),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutLinearInEasing),
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 140),
            ),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            select_mode_bottom_bar(
                selected_count = selected_ids.size,
                on_archive = ::archive_selected,
                on_delete = ::delete_selected,
                on_restore = ::restore_selected,
                on_unarchive = ::unarchive_selected,
                on_unmark_spam = ::unmark_spam_selected,
                on_mark_spam = ::mark_spam_selected,
                on_delete_permanent = ::delete_permanent_selected,
                on_mark_read = {
                    val thread_ids = selected_ids.toSet()
                    val email_ids = emails
                        .filter { it.thread_id in thread_ids && !it.is_read }
                        .map { it.id }
                    if (email_ids.isNotEmpty()) {
                        mail_vm.mark_read_bulk(email_ids)
                    }
                    for (i in emails.indices) {
                        if (emails[i].thread_id in thread_ids && !emails[i].is_read) {
                            emails[i] = emails[i].copy(is_read = true)
                        }
                    }
                    exit_select_mode()
                },
                current_folder = current_folder,
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !select_mode,
            enter = androidx.compose.animation.scaleIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                initialScale = 0.6f,
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 160),
            ),
            exit = androidx.compose.animation.scaleOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 140, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                targetScale = 0.6f,
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 120),
            ),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            FloatingActionButton(
                onClick = {
                    if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    on_compose()
                },
                containerColor = colors.accent_blue,
                contentColor = Color.White,
                shape = SquircleShape(18.dp),
                modifier = Modifier
                    .padding(AsterSpacing.lg)
                    .size(56.dp)
                    .testTag("compose"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.compose),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (show_empty_trash_dialog) {
            org.astermail.android.design.components.AsterAlertDialog(
                on_dismiss = { show_empty_trash_dialog = false },
                title = stringResource(R.string.empty_trash),
                message = stringResource(R.string.empty_trash_confirm),
                confirm_label = stringResource(R.string.delete_all),
                cancel_label = stringResource(R.string.cancel),
                confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
                on_confirm = {
                    show_empty_trash_dialog = false
                    mail_vm.empty_trash()
                },
            )
        }

        if (confirm_action_pending != null) {
            val pending_action = confirm_action_pending!!
            val pending_ids = confirm_item_ids_pending
            val pending_thread = confirm_thread_id_pending
            fun dismiss_confirm() {
                confirm_action_pending = null
                confirm_item_ids_pending = emptyList()
                confirm_thread_id_pending = null
            }
            org.astermail.android.design.components.AsterAlertDialog(
                on_dismiss = ::dismiss_confirm,
                title = stringResource(when (pending_action) {
                    "archive" -> R.string.confirm_archive_title
                    "trash" -> R.string.confirm_trash_title
                    "spam" -> R.string.confirm_spam_title
                    "delete_permanent" -> R.string.confirm_delete_permanent_title
                    else -> R.string.confirm
                }),
                message = stringResource(when (pending_action) {
                    "archive" -> R.string.confirm_archive_message
                    "trash" -> R.string.confirm_trash_message
                    "spam" -> R.string.confirm_spam_message
                    "delete_permanent" -> R.string.confirm_delete_permanent_message
                    else -> R.string.confirm
                }),
                confirm_label = stringResource(R.string.confirm),
                cancel_label = stringResource(R.string.cancel),
                confirm_style = org.astermail.android.design.components.DialogConfirmStyle.destructive,
                on_confirm = {
                    if (pending_thread != null) {
                        execute_swipe_action(pending_action, pending_ids, mail_vm, emails, pending_thread, current_folder)
                    }
                    dismiss_confirm()
                },
            )
        }
    }
}

private val quick_switch_folders = listOf(
    "inbox" to R.string.folder_inbox,
    "sent" to R.string.folder_sent,
    "drafts" to R.string.folder_drafts,
    "archive" to R.string.folder_archive,
    "starred" to R.string.folder_starred,
    "scheduled" to R.string.folder_scheduled,
    "snoozed" to R.string.folder_snoozed,
    "spam" to R.string.folder_spam,
    "trash" to R.string.folder_trash,
)

@Composable
private fun inbox_top_bar(
    folder_title: String,
    unread_count: Int,
    on_open_drawer: () -> Unit,
    on_open_search: () -> Unit,
    on_enter_select_mode: () -> Unit,
    on_refresh: () -> Unit,
    on_mark_all_read: (Boolean) -> Unit,
    has_unread: Boolean,
    on_select_all: () -> Unit,
    on_open_settings: () -> Unit,
    on_open_upgrade: () -> Unit = {},
    show_upgrade: Boolean = true,
    on_empty_trash: () -> Unit = {},
    sort_mode: InboxSortMode,
    on_sort_change: (InboxSortMode) -> Unit,
    show_divider: Boolean,
    current_folder: String = "inbox",
    on_folder_change: (String) -> Unit = {},
    custom_folders: List<Pair<String, String>> = emptyList(),
    on_custom_folder_change: (String, String) -> Unit = { _, _ -> },
) {
    val colors = AsterMaterial.colors
    val divider_alpha by animateFloatAsState(
        targetValue = if (show_divider) 1f else 0f,
        label = "divider_alpha",
    )
    var folder_menu_open by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().background(colors.bg_primary)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = Icons.Filled.Menu,
                content_description = stringResource(R.string.open_drawer),
                onClick = on_open_drawer,
                modifier = Modifier.testTag("account_avatar"),
            )
            Spacer(Modifier.width(AsterSpacing.xs))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.clickable { folder_menu_open = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = folder_title,
                        color = colors.text_primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                    )
                    if (unread_count > 0) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = unread_count.toString(),
                            color = colors.accent_blue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.switch_folder),
                        tint = colors.text_muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                DropdownMenu(
                    expanded = folder_menu_open,
                    onDismissRequest = { folder_menu_open = false },
                    shape = SquircleShape(18.dp),
                    containerColor = colors.dropdown_bg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.border_secondary),
                    shadowElevation = 8.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier.padding(vertical = 6.dp),
                ) {
                    quick_switch_folders.forEach { (id, label_res) ->
                        val label = stringResource(label_res)
                        DropdownMenuItem(
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            text = {
                                Text(
                                    text = label,
                                    fontSize = 15.sp,
                                    fontWeight = if (id == current_folder) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (id == current_folder) colors.accent_blue else colors.text_primary,
                                )
                            },
                            onClick = {
                                folder_menu_open = false
                                if (id != current_folder) on_folder_change(id)
                            },
                        )
                    }
                    if (custom_folders.isNotEmpty()) {
                        AsterDivider(modifier = Modifier.padding(vertical = 4.dp))
                        custom_folders.forEach { (id, name) ->
                            DropdownMenuItem(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                                text = {
                                    Text(
                                        text = name,
                                        fontSize = 15.sp,
                                        fontWeight = if (id == current_folder) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (id == current_folder) colors.accent_blue else colors.text_primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    folder_menu_open = false
                                    if (id != current_folder) on_custom_folder_change(id, name)
                                },
                            )
                        }
                    }
                }
            }
            debug_build_pill_inline()
            Spacer(Modifier.width(AsterSpacing.xs))
            AsterIconButton(
                icon = Icons.Outlined.Settings,
                content_description = stringResource(R.string.settings),
                onClick = on_open_settings,
                modifier = Modifier.testTag("settings"),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg)
                .padding(bottom = AsterSpacing.sm)
                .clip(SquircleShape(18.dp))
                .background(colors.bg_card)
                .clickable { on_open_search() }
                .padding(horizontal = AsterSpacing.md, vertical = 10.dp)
                .testTag("search"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.inbox_search_in_folder, folder_title.lowercase(java.util.Locale.getDefault())),
                color = colors.text_muted,
                fontSize = 14.sp,
            )
        }
        if (divider_alpha > 0f) {
            AsterDivider(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun sort_menu_item(label: String, is_selected: Boolean, on_click: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            if (is_selected) {
                Icon(Icons.Filled.Check, contentDescription = null)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        onClick = on_click,
    )
}

@Composable
private fun select_mode_top_bar(
    selected_count: Int,
    on_close: () -> Unit,
    on_select_all: () -> Unit,
    on_archive: () -> Unit,
    on_delete: () -> Unit,
    on_restore: () -> Unit,
    on_unarchive: () -> Unit,
    on_unmark_spam: () -> Unit,
    on_delete_permanent: () -> Unit,
    on_mark_read: () -> Unit,
    show_divider: Boolean,
    current_folder: String = "inbox",
) {
    val colors = AsterMaterial.colors
    val divider_alpha by animateFloatAsState(
        targetValue = if (show_divider) 1f else 0f,
        label = "select_divider_alpha",
    )
    Column(modifier = Modifier.fillMaxWidth().background(colors.bg_primary)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = Icons.Filled.Close,
                content_description = stringResource(R.string.exit_selection),
                onClick = on_close,
                modifier = Modifier.testTag("exit_select"),
            )
            Spacer(Modifier.width(AsterSpacing.xs))
            Text(
                text = if (selected_count == 0) stringResource(R.string.select) else stringResource(R.string.inbox_selected_count, selected_count),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            AsterIconButton(
                icon = Icons.Filled.SelectAll,
                content_description = stringResource(R.string.select_all),
                onClick = on_select_all,
            )
        }
        if (divider_alpha > 0f) {
            AsterDivider(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun select_mode_bottom_bar(
    selected_count: Int,
    on_archive: () -> Unit,
    on_delete: () -> Unit,
    on_restore: () -> Unit,
    on_unarchive: () -> Unit,
    on_unmark_spam: () -> Unit,
    on_mark_spam: () -> Unit,
    on_delete_permanent: () -> Unit,
    on_mark_read: () -> Unit,
    current_folder: String,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val enabled = selected_count > 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.bg_card,
        shadowElevation = 16.dp,
        tonalElevation = 0.dp,
    ) {
        Column {
            AsterDivider(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = AsterSpacing.sm, vertical = AsterSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                bottom_select_action(
                    icon = Icons.Outlined.MarkEmailRead,
                    label = stringResource(R.string.mark_read_action),
                    enabled = enabled,
                    onClick = on_mark_read,
                    test_tag = "mark_read",
                )
                when (current_folder) {
                    "trash" -> {
                        bottom_select_action(
                            icon = Icons.Outlined.Inbox,
                            label = stringResource(R.string.swipe_restore),
                            enabled = enabled,
                            onClick = on_restore,
                        )
                        bottom_select_action(
                            icon = Icons.Outlined.DeleteForever,
                            label = stringResource(R.string.swipe_delete_forever),
                            enabled = enabled,
                            onClick = on_delete_permanent,
                            tint = colors.danger,
                        )
                    }
                    "archive" -> {
                        bottom_select_action(
                            icon = Icons.Outlined.Inbox,
                            label = stringResource(R.string.swipe_restore),
                            enabled = enabled,
                            onClick = on_unarchive,
                        )
                        bottom_select_action(
                            icon = Icons.Filled.Block,
                            label = stringResource(R.string.report_spam),
                            enabled = enabled,
                            onClick = on_mark_spam,
                        )
                        bottom_select_action(
                            icon = Icons.Outlined.Delete,
                            label = stringResource(R.string.delete_action),
                            enabled = enabled,
                            onClick = on_delete,
                            tint = colors.danger,
                        )
                    }
                    "spam" -> {
                        bottom_select_action(
                            icon = Icons.Outlined.Inbox,
                            label = stringResource(R.string.swipe_not_spam),
                            enabled = enabled,
                            onClick = on_unmark_spam,
                        )
                        bottom_select_action(
                            icon = Icons.Outlined.Delete,
                            label = stringResource(R.string.delete_action),
                            enabled = enabled,
                            onClick = on_delete,
                            tint = colors.danger,
                        )
                    }
                    else -> {
                        bottom_select_action(
                            icon = Icons.Outlined.Archive,
                            label = stringResource(R.string.archive_action),
                            enabled = enabled,
                            onClick = on_archive,
                            test_tag = "archive_selected",
                        )
                        bottom_select_action(
                            icon = Icons.Filled.Block,
                            label = stringResource(R.string.report_spam),
                            enabled = enabled,
                            onClick = on_mark_spam,
                        )
                        bottom_select_action(
                            icon = Icons.Outlined.Delete,
                            label = stringResource(R.string.delete_action),
                            enabled = enabled,
                            onClick = on_delete,
                            tint = colors.danger,
                            test_tag = "delete",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun bottom_select_action(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tint: Color = AsterMaterial.colors.text_primary,
    test_tag: String? = null,
) {
    val colors = AsterMaterial.colors
    val resolved_tint = if (enabled) tint else colors.text_muted
    Column(
        modifier = Modifier
            .clip(SquircleShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .then(if (test_tag != null) Modifier.testTag(test_tag) else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = resolved_tint,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = resolved_tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun is_removing_swipe_action(action: String): Boolean = action in setOf(
    "archive", "trash", "spam", "move_to_inbox", "unarchive",
    "restore_trash", "unmark_spam", "delete_permanent",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun swipeable_thread_row(
    thread: ThreadRow,
    on_click: () -> Unit,
    on_long_click: () -> Unit,
    on_toggle_star: () -> Unit,
    on_swipe_start: () -> Unit,
    on_swipe_end: () -> Unit,
    swipe_start_action: String = "archive",
    swipe_end_action: String = "trash",
    is_pinned: Boolean = false,
    swipe_start_label: String = stringResource(R.string.swipe_archive),
    swipe_end_label: String = stringResource(R.string.swipe_delete),
    swipe_start_icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Archive,
    swipe_end_icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Delete,
    swipe_start_color: Color = AsterMaterial.colors.accent_blue,
    swipe_end_color: Color = AsterMaterial.colors.danger,
    modifier: Modifier = Modifier,
    attachment_chips: List<MailViewModel.InboxAttachmentChip> = emptyList(),
    haptic_enabled: Boolean = true,
    row_index: Int = 0,
    user_prefs: org.astermail.android.api.preferences.UserPreferences? = null,
) {
    val colors = AsterMaterial.colors
    var gesture_is_horizontal by remember { mutableStateOf(true) }
    var is_dismissed by remember { mutableStateOf(false) }
    val dismiss_state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    val removing = is_removing_swipe_action(swipe_start_action)
                    if (removing) is_dismissed = true
                    on_swipe_start()
                    removing
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    val removing = is_removing_swipe_action(swipe_end_action)
                    if (removing) is_dismissed = true
                    on_swipe_end()
                    removing
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
        positionalThreshold = { total_distance -> total_distance * 0.4f },
    )

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                gesture_is_horizontal = true
                var dx = 0f
                var dy = 0f
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull() ?: break
                    if (!change.pressed) { gesture_is_horizontal = true; break }
                    val delta = change.position - change.previousPosition
                    dx += kotlin.math.abs(delta.x)
                    dy += kotlin.math.abs(delta.y)
                    if (dx + dy > viewConfiguration.touchSlop) {
                        gesture_is_horizontal = dx > dy
                        break
                    }
                }
            }
        },
    ) {
        SwipeToDismissBox(
            state = dismiss_state,
            modifier = Modifier,
            gesturesEnabled = gesture_is_horizontal && !is_dismissed,
            enableDismissFromStartToEnd = swipe_start_action != "none",
            enableDismissFromEndToStart = swipe_end_action != "none",
            backgroundContent = {
                val direction = dismiss_state.dismissDirection
                val (bg, align, icon, label) = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Quad(
                        swipe_start_color, Alignment.CenterStart, swipe_start_icon, swipe_start_label,
                    )
                    SwipeToDismissBoxValue.EndToStart -> Quad(
                        swipe_end_color, Alignment.CenterEnd, swipe_end_icon, swipe_end_label,
                    )
                    SwipeToDismissBoxValue.Settled -> Quad(
                        Color.Transparent, Alignment.CenterStart, swipe_start_icon, "",
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg)
                        .padding(horizontal = AsterSpacing.xl),
                    contentAlignment = align,
                ) {
                    if (direction != SwipeToDismissBoxValue.Settled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(AsterSpacing.sm))
                            Text(
                                text = label,
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            },
        ) {
            Box(modifier = Modifier.background(colors.bg_primary)) {
                ThreadInboxRow(
                    thread = thread,
                    on_click = on_click,
                    on_long_click = on_long_click,
                    on_toggle_star = on_toggle_star,
                    is_pinned = is_pinned,
                    attachment_chips = attachment_chips,
                    haptic_enabled = haptic_enabled,
                    row_index = row_index,
                    user_prefs = user_prefs,
                )
            }
        }
    }
}

private fun swipe_action_color(action: String, colors: org.astermail.android.design.AsterSemanticColors): Color = when (action) {
    "archive", "move_to_inbox", "unarchive", "restore_trash", "unmark_spam" -> colors.accent_blue
    "trash", "delete_permanent", "spam" -> colors.danger
    "mark_read" -> colors.success
    "mark_unread" -> colors.warning
    "star" -> colors.warning
    else -> colors.accent_blue
}

@Composable
private fun swipe_action_label(action: String): String = when (action) {
    "archive" -> stringResource(R.string.swipe_archive)
    "trash" -> stringResource(R.string.swipe_delete)
    "mark_read" -> stringResource(R.string.swipe_read)
    "mark_unread" -> stringResource(R.string.swipe_unread)
    "star" -> stringResource(R.string.swipe_star)
    "spam" -> stringResource(R.string.swipe_spam)
    "move_to_inbox" -> stringResource(R.string.swipe_inbox)
    "unarchive" -> stringResource(R.string.swipe_restore)
    "restore_trash" -> stringResource(R.string.swipe_restore)
    "unmark_spam" -> stringResource(R.string.swipe_not_spam)
    "delete_permanent" -> stringResource(R.string.swipe_delete_forever)
    "none" -> ""
    else -> stringResource(R.string.swipe_archive)
}

private fun swipe_action_icon(action: String): androidx.compose.ui.graphics.vector.ImageVector = when (action) {
    "archive" -> Icons.Filled.Archive
    "trash" -> Icons.Filled.Delete
    "mark_read" -> Icons.Filled.MarkEmailRead
    "mark_unread" -> Icons.Filled.MarkEmailUnread
    "star" -> Icons.Filled.Star
    "spam" -> Icons.Filled.Block
    "move_to_inbox" -> Icons.Filled.Inbox
    "unarchive" -> Icons.Filled.Inbox
    "restore_trash" -> Icons.Filled.Inbox
    "unmark_spam" -> Icons.Filled.Inbox
    "delete_permanent" -> Icons.Filled.Delete
    else -> Icons.Filled.Archive
}

private fun execute_swipe_action(
    action: String,
    ids: List<String>,
    mail_vm: MailViewModel,
    emails: MutableList<Email>,
    thread_id: String,
    current_folder: String,
) {
    when (action) {
        "archive" -> {
            if (current_folder == "archive") return
            mail_vm.archive(ids, 1)
            emails.removeAll { it.thread_id == thread_id }
        }
        "trash" -> {
            if (current_folder == "trash") return
            mail_vm.trash(ids, 1)
            emails.removeAll { it.thread_id == thread_id }
        }
        "mark_read" -> {
            mail_vm.mark_read_bulk(ids)
            for (i in emails.indices) {
                if (emails[i].thread_id == thread_id) {
                    emails[i] = emails[i].copy(is_read = true)
                }
            }
        }
        "mark_unread" -> {
            ids.forEach { mail_vm.mark_unread(it) }
            for (i in emails.indices) {
                if (emails[i].thread_id == thread_id) {
                    emails[i] = emails[i].copy(is_read = false)
                }
            }
        }
        "star" -> {
            ids.forEach { mail_vm.toggle_star(it) }
            for (i in emails.indices) {
                if (emails[i].thread_id == thread_id) {
                    emails[i] = emails[i].copy(is_starred = !emails[i].is_starred)
                }
            }
        }
        "spam" -> {
            if (current_folder == "spam") return
            mail_vm.mark_spam(ids, 1)
            emails.removeAll { it.thread_id == thread_id }
        }
        "move_to_inbox" -> {
            if (current_folder == "inbox") return
            mail_vm.unarchive(ids)
            emails.removeAll { it.thread_id == thread_id }
        }
        "unarchive" -> {
            mail_vm.unarchive(ids)
            emails.removeAll { it.thread_id == thread_id }
        }
        "restore_trash" -> {
            mail_vm.restore_trash(ids)
            emails.removeAll { it.thread_id == thread_id }
        }
        "unmark_spam" -> {
            mail_vm.unmark_spam(ids)
            emails.removeAll { it.thread_id == thread_id }
        }
        "delete_permanent" -> {
            ids.forEach { mail_vm.delete_permanent(it) }
            emails.removeAll { it.thread_id == thread_id }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

private data class SwipeConfig(
    val start_label: String,
    val end_label: String,
    val start_icon: androidx.compose.ui.graphics.vector.ImageVector,
    val end_icon: androidx.compose.ui.graphics.vector.ImageVector,
    val start_action: String,
    val end_action: String,
)

@Composable
private fun inbox_error_state(message: String, on_retry: () -> Unit) {
    val colors = AsterMaterial.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(AsterSpacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.something_went_wrong),
            color = colors.text_primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = message,
            color = colors.text_muted,
            fontSize = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.lg))
        Box(
            modifier = Modifier
                .clip(SquircleShape(18.dp))
                .background(colors.accent_blue)
                .clickable(onClick = on_retry)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Text(text = stringResource(R.string.retry), color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun empty_inbox_state(folder: String = "inbox") {
    val colors = AsterMaterial.colors
    val icon = remember(folder) {
        when (folder) {
            "inbox" -> Icons.Filled.Inbox
            "sent" -> Icons.AutoMirrored.Filled.Send
            "drafts" -> Icons.Filled.Edit
            "starred" -> Icons.Filled.Star
            "trash" -> Icons.Filled.Delete
            "spam" -> Icons.Filled.Block
            "archive" -> Icons.Filled.Archive
            "scheduled" -> Icons.Filled.Schedule
            "snoozed" -> Icons.Filled.NotificationsOff
            else -> Icons.Filled.Inbox
        }
    }
    val title = when (folder) {
        "inbox" -> stringResource(R.string.all_caught_up)
        "sent" -> stringResource(R.string.nothing_sent_yet)
        "drafts" -> stringResource(R.string.no_drafts)
        "starred" -> stringResource(R.string.nothing_starred)
        "trash" -> stringResource(R.string.nothing_here_clear)
        "spam" -> stringResource(R.string.no_spam)
        "archive" -> stringResource(R.string.nothing_archived)
        "scheduled" -> stringResource(R.string.no_scheduled)
        "snoozed" -> stringResource(R.string.nothing_snoozed)
        else -> stringResource(R.string.no_messages)
    }
    val subtitle = when (folder) {
        "inbox" -> stringResource(R.string.new_messages_here)
        "sent" -> stringResource(R.string.sent_messages_here)
        "drafts" -> stringResource(R.string.drafts_working_here)
        "starred" -> stringResource(R.string.star_important)
        "trash" -> stringResource(R.string.deleted_emails_here)
        "spam" -> stringResource(R.string.suspicious_caught_here)
        "archive" -> stringResource(R.string.archive_to_clean)
        "scheduled" -> stringResource(R.string.scheduled_messages_here)
        "snoozed" -> stringResource(R.string.snoozed_wake_here)
        else -> stringResource(R.string.nothing_here_yet)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text_muted,
            )
        }
    }
}

