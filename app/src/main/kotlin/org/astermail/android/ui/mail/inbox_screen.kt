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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.animation.core.tween
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Surface
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.foundation.gestures.awaitFirstDown
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.astermail.android.design.components.aster_dropdown_divider
import org.astermail.android.design.components.aster_dropdown_item
import org.astermail.android.design.components.aster_dropdown_menu
import org.astermail.android.design.components.aster_dropdown_section_label
import org.astermail.android.R
import org.astermail.android.debugtools.debug_build_pill_inline
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterIconButton
import org.astermail.android.mail.DEFAULT_SWIPE_LEFT_ACTION
import org.astermail.android.mail.DEFAULT_SWIPE_RIGHT_ACTION
import org.astermail.android.mail.MailViewModel
import org.astermail.android.mail.all_mail_folder
import org.astermail.android.mail.is_all_mail_folder
import org.astermail.android.mail.normalize_swipe_action
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.icons.all_mail_icon
import org.astermail.android.settings.shared_settings_view_model

enum class InboxSortMode { newest, oldest, unread_first, starred_first }

data class ThreadRowResult(
    val rows: List<ThreadRow>,
    val participants: Map<String, List<Pair<String, String>>>,
)

fun build_thread_rows(
    emails: List<Email>,
    categories_enabled: Boolean,
    active_category: String,
    active_tabs: List<String>,
    sort_mode: InboxSortMode,
    cached_participants: Map<String, List<Pair<String, String>>>,
    sticky_participants: Map<String, List<Pair<String, String>>>,
    grouping_enabled: Boolean = true,
): ThreadRowResult {
    val source = if (categories_enabled) {
        emails.filter {
            org.astermail.android.mail.category_for_tab(it.category, active_tabs) == active_category
        }
    } else {
        emails
    }
    val grouped_raw = if (grouping_enabled) group_by_thread(source) else flat_thread_rows(source)
    val resolved = HashMap<String, List<Pair<String, String>>>(grouped_raw.size)
    val grouped = grouped_raw.map { row ->
        val candidates = listOfNotNull(
            cached_participants[row.thread_id],
            sticky_participants[row.thread_id],
            row.participants,
        )
        val merged = candidates.maxByOrNull { it.size } ?: row.participants
        resolved[row.thread_id] = merged
        if (merged === row.participants) row else row.copy(participants = merged)
    }
    val sorted = when (sort_mode) {
        InboxSortMode.newest -> grouped.sortedWith(
            compareByDescending<ThreadRow> { it.is_pinned }.thenByDescending { it.newest.received_at }.thenByDescending { it.thread_id },
        )
        InboxSortMode.oldest -> grouped.sortedWith(
            compareByDescending<ThreadRow> { it.is_pinned }.thenBy { it.newest.received_at }.thenBy { it.thread_id },
        )
        InboxSortMode.unread_first -> grouped.sortedWith(
            compareByDescending<ThreadRow> { it.is_pinned }.thenByDescending { it.has_unread }.thenByDescending { it.newest.received_at }.thenByDescending { it.thread_id },
        )
        InboxSortMode.starred_first -> grouped.sortedWith(
            compareByDescending<ThreadRow> { it.is_pinned }.thenByDescending { it.is_starred }.thenByDescending { it.newest.received_at }.thenByDescending { it.thread_id },
        )
    }
    return ThreadRowResult(sorted, resolved)
}

private const val UNREAD_MISMATCH_GRACE_MS = 3000L

private const val MIN_FILLED_ROWS = 15

private const val CATEGORY_DRAIN_MAX_ITEMS = 200

private const val LOCAL_READ_MUTATION_TTL_MS = 15_000L

private const val MIN_SKELETON_MS = 350L

internal const val SELECT_ALL_DRAIN_LIMIT = 5000

private val pull_refresh_travel = 56.dp

private val pull_refresh_threshold = 56.dp

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
    custom_folders: List<quick_folder_node> = emptyList(),
    on_custom_folder_change: (String, String) -> Unit = { _, _ -> },
    folder_unread_counts: Map<String, Int> = emptyMap(),
    on_customize_toolbar: () -> Unit = {},
    scroll_top_token: Int = 0,
    all_mail_include_spam: Boolean = false,
    all_mail_include_trash: Boolean = false,
    on_all_mail_scope_change: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    val colors = AsterMaterial.colors
    val haptics = LocalHapticFeedback.current
    val list_state = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val mail_vm: MailViewModel = hiltViewModel()
    val settings_vm: SettingsViewModel = shared_settings_view_model()
    val inbox_state by mail_vm.inbox_state.collectAsStateWithLifecycle()
    val attachment_ids by mail_vm.inbox_attachment_ids.collectAsStateWithLifecycle()
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
    LaunchedEffect(Unit) {
        settings_vm.refresh_cached_preferences()
        settings_vm.load_preferences()
    }
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
    val failed_send_count by mail_vm.failed_send_count.collectAsStateWithLifecycle()
    if (send_problem) {
        val has_failed = failed_send_count > 0
        org.astermail.android.design.components.AsterDialog(
            on_dismiss = { mail_vm.dismiss_send_problem() },
            title = stringResource(R.string.send_problem_title),
            message = if (has_failed) {
                stringResource(R.string.send_problem_failed_message)
            } else {
                stringResource(R.string.send_problem_message)
            },
            footer = {
                if (has_failed) {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.discard),
                        onClick = { mail_vm.discard_failed_sends() },
                    )
                    org.astermail.android.design.components.AsterDialogPrimaryButton(
                        label = stringResource(R.string.retry),
                        onClick = { mail_vm.retry_failed_sends() },
                    )
                } else {
                    org.astermail.android.design.components.AsterDialogOutlineButton(
                        label = stringResource(R.string.ok),
                        onClick = { mail_vm.dismiss_send_problem() },
                    )
                }
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
    undo_send_toast(on_view = on_view_pending_send)

    val lifecycle_owner = LocalLifecycleOwner.current
    DisposableEffect(lifecycle_owner) {
        var was_backgrounded = false
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                was_backgrounded = true
                return@LifecycleEventObserver
            }
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!was_backgrounded) return@LifecycleEventObserver
                was_backgrounded = false
                settings_vm.load_preferences()
                settings_vm.load_tags()
                mail_vm.load_inbox(current_folder, force = true)
                mail_vm.load_stats(force = true)
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
        inbox_state.items.filterNot { it.has_attachments }.map { it.id }
    }
    LaunchedEffect(attachment_ids_key) {
        if (attachment_ids_key.isNotEmpty()) {
            mail_vm.resolve_inbox_attachment_flags(attachment_ids_key)
        }
    }

    val is_all_mail_view = is_all_mail_folder(current_folder)
    val system_folder_chip_names = mapOf(
        "inbox" to folder_display_name("inbox"),
        "sent" to folder_display_name("sent"),
        "drafts" to folder_display_name("drafts"),
        "archive" to folder_display_name("archive"),
        "scheduled" to folder_display_name("scheduled"),
        "trash" to stringResource(R.string.folder_trash),
        "spam" to stringResource(R.string.folder_spam),
    )
    val all_mail_folder_chip: ((org.astermail.android.mail.InboxItem) -> list_folder_chip?)? =
        if (!is_all_mail_view) null else { item ->
            val neutral = Color(0xFF64748B)
            when {
                item.is_trashed -> list_folder_chip(
                    name = system_folder_chip_names.getValue("trash"),
                    icon = "trash",
                    color = Color(0xFFEF4444),
                )
                item.is_spam -> list_folder_chip(
                    name = system_folder_chip_names.getValue("spam"),
                    icon = "warning",
                    color = Color(0xFFF59E0B),
                )
                else -> {
                    val custom = settings_state.labels.firstOrNull { label ->
                        label.folder_type == "folder" &&
                            label.label_token in item.labels &&
                            !org.astermail.android.folders.requires_unlock(label) &&
                            !label.encrypted_name.isNullOrBlank() &&
                            !org.astermail.android.looks_encrypted(label.encrypted_name)
                    }
                    if (custom != null) {
                        list_folder_chip(
                            name = custom.encrypted_name.orEmpty(),
                            icon = if (org.astermail.android.folders.is_folder_protected(custom)) "lock" else "folder",
                            color = custom.encrypted_color
                                ?.takeIf { it.startsWith("#") }
                                ?.let { org.astermail.android.design.parse_hex_color_safe(it) }
                                ?: neutral,
                        )
                    } else {
                        val folder_id = detail_system_folder_id(item)
                        val icon = when (folder_id) {
                            "sent" -> "send"
                            "drafts" -> "draft"
                            "archive" -> "archive"
                            "scheduled" -> "clock"
                            else -> "inbox"
                        }
                        list_folder_chip(
                            name = system_folder_chip_names[folder_id] ?: folder_id,
                            icon = icon,
                            color = neutral,
                        )
                    }
                }
            }
        }
    val api_emails = remember(inbox_state.items, settings_state.tags, attachment_ids, settings_state.labels, current_folder) {
        inbox_state.items.map {
            inbox_item_to_email(
                if (!it.has_attachments && it.id in attachment_ids) it.copy(has_attachments = true) else it,
                settings_state.tags,
                folder_chip = all_mail_folder_chip?.invoke(it),
            )
        }
    }
    val emails = remember { mutableStateListOf<Email>() }
    val previous_api_emails = remember { mutableMapOf<String, Email>() }
    val local_read_mutations = remember { mutableMapOf<String, Long>() }
    fun note_read_mutation(id: String) {
        local_read_mutations[id] = android.os.SystemClock.uptimeMillis()
    }
    var emails_folder by remember { mutableStateOf(current_folder) }
    LaunchedEffect(current_folder) {
        if (emails_folder != current_folder) {
            emails.clear()
            previous_api_emails.clear()
            local_read_mutations.clear()
            emails_folder = current_folder
        }
    }
    LaunchedEffect(api_emails) {
        val current = emails.toList()
        val now_ms = android.os.SystemClock.uptimeMillis()
        local_read_mutations.entries.removeAll { now_ms - it.value > LOCAL_READ_MUTATION_TTL_MS }
        val sticky_read_ids = local_read_mutations.keys.toSet()
        val merged = withContext(Dispatchers.Default) {
            val by_id = current.associateBy { it.id }
            api_emails.map { server ->
                val local = by_id[server.id] ?: return@map server
                val previous = previous_api_emails[server.id] ?: return@map server
                server.copy(
                    is_read = if (previous.is_read == server.is_read && server.id in sticky_read_ids) local.is_read else server.is_read,
                    is_starred = if (previous.is_starred == server.is_starred) local.is_starred else server.is_starred,
                    is_pinned = if (previous.is_pinned == server.is_pinned) local.is_pinned else server.is_pinned,
                )
            }
        }
        previous_api_emails.clear()
        api_emails.forEach { previous_api_emails[it.id] = it }
        if (merged != emails.toList()) {
            if (merged.size == emails.size) {
                merged.forEachIndexed { index, item ->
                    if (emails[index] != item) emails[index] = item
                }
            } else {
                val shared = minOf(merged.size, emails.size)
                for (index in 0 until shared) {
                    if (emails[index] != merged[index]) emails[index] = merged[index]
                }
                if (merged.size > emails.size) {
                    emails.addAll(merged.subList(shared, merged.size))
                } else {
                    while (emails.size > merged.size) emails.removeAt(emails.size - 1)
                }
            }
        }
    }
    val is_refreshing = inbox_state.is_refreshing
    var sort_mode_user_set by remember { mutableStateOf(false) }
    var sort_mode by remember { mutableStateOf(InboxSortMode.newest) }
    var select_mode by remember { mutableStateOf(false) }
    var select_all_active by remember { mutableStateOf(false) }
    var select_all_loading by remember { mutableStateOf(false) }
    var scope_selection_confirmed by remember { mutableStateOf(false) }
    val selected_ids = remember { mutableStateListOf<String>() }
    var show_empty_trash_dialog by remember { mutableStateOf(false) }
    var show_selection_overflow by remember { mutableStateOf(false) }
    var show_bulk_folder_sheet by remember { mutableStateOf(false) }
    var show_bulk_label_sheet by remember { mutableStateOf(false) }
    var show_bulk_snooze_sheet by remember { mutableStateOf(false) }
    var swipe_snooze_ids by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(settings_state.preferences?.selection_toolbar_actions) {
        val raw = settings_state.preferences?.selection_toolbar_actions
        if (raw != null) {
            cache_selection_toolbar_actions(context_for_prefs, parse_selection_toolbar_actions(raw))
        }
    }
    val selection_toolbar_slots = remember(select_mode, settings_state.preferences?.selection_toolbar_actions) {
        load_selection_toolbar_actions(context_for_prefs)
    }
    var confirm_action_pending by remember { mutableStateOf<String?>(null) }
    var confirm_item_ids_pending by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirm_thread_id_pending by remember { mutableStateOf<String?>(null) }

    val scrolled_elevation by remember(list_state) {
        derivedStateOf {
            list_state.firstVisibleItemScrollOffset > 0 || list_state.firstVisibleItemIndex > 0
        }
    }

    val sticky_participants = remember(current_folder) { mutableMapOf<String, List<Pair<String, String>>>() }
    val cached_participants by mail_vm.thread_participants.collectAsStateWithLifecycle()

    val categories_enabled = current_folder == "inbox" &&
        (settings_state.preferences?.inbox_categories_enabled ?: true)
    val active_category = inbox_category
    val emails_fingerprint by remember { derivedStateOf { emails_fingerprint_of(emails) } }
    val active_tabs = remember(
        settings_state.preferences?.enabled_categories,
        settings_state.preferences?.custom_categories,
    ) {
        val prefs = settings_state.preferences
        if (prefs == null) {
            org.astermail.android.mail.CATEGORY_TABS
        } else {
            org.astermail.android.mail.active_category_tabs(
                prefs.enabled_categories,
                org.astermail.android.mail.sanitize_custom_categories(prefs.custom_categories),
                -1,
            )
        }
    }
    val active_category_label = if (categories_enabled) {
        val customs = org.astermail.android.mail.sanitize_custom_categories(
            settings_state.preferences?.custom_categories.orEmpty(),
        )
        org.astermail.android.mail.category_entries(active_tabs, customs)
            .firstOrNull { it.id == active_category }
            ?.label
    } else {
        null
    }
    var threads by remember { mutableStateOf<List<ThreadRow>>(emptyList()) }
    var threads_pending by remember { mutableStateOf(true) }
    var threads_folder by remember { mutableStateOf(current_folder) }
    val grouping_enabled = settings_state.preferences?.conversation_grouping != false
    LaunchedEffect(
        current_folder,
        emails_fingerprint,
        categories_enabled,
        active_category,
        active_tabs,
        sort_mode,
        cached_participants,
        grouping_enabled,
    ) {
        threads_pending = true
        if (threads_folder != current_folder) {
            threads = emptyList()
            threads_folder = current_folder
        }
        val snapshot = emails.toList()
        val sticky_snapshot = HashMap(sticky_participants)
        val computed = withContext(Dispatchers.Default) {
            build_thread_rows(
                emails = snapshot,
                categories_enabled = categories_enabled,
                active_category = active_category,
                active_tabs = active_tabs,
                sort_mode = sort_mode,
                cached_participants = cached_participants,
                sticky_participants = sticky_snapshot,
                grouping_enabled = grouping_enabled,
            )
        }
        sticky_participants.keys.retainAll(computed.participants.keys)
        sticky_participants.putAll(computed.participants)
        threads = computed.rows
        threads_pending = false
    }

    LaunchedEffect(sort_mode, sort_mode_user_set, settings_state.preferences?.conversation_order) {
        val prefs_order = settings_state.preferences?.conversation_order
        if (!sort_mode_user_set && prefs_order == null) return@LaunchedEffect
        val oldest = if (sort_mode_user_set) {
            sort_mode == InboxSortMode.oldest
        } else {
            prefs_order == "oldest"
        }
        mail_vm.set_list_order(if (oldest) "asc" else null)
    }

    LaunchedEffect(settings_state.preferences?.inbox_page_size) {
        val prefs_page_size = settings_state.preferences?.inbox_page_size ?: return@LaunchedEffect
        mail_vm.set_page_size(prefs_page_size.coerceIn(10, 100))
    }

    var last_scroll_reset_key by rememberSaveable { mutableStateOf("") }
    var pending_scroll_reset by remember { mutableStateOf(false) }
    LaunchedEffect(sort_mode, current_folder, active_category, categories_enabled) {
        val reset_key = "$sort_mode|$current_folder|${if (categories_enabled) active_category else ""}"
        if (last_scroll_reset_key.isNotEmpty() && last_scroll_reset_key != reset_key) {
            pending_scroll_reset = true
            list_state.scrollToItem(0)
        }
        last_scroll_reset_key = reset_key
    }
    LaunchedEffect(pending_scroll_reset, threads.firstOrNull()?.thread_id, threads.size) {
        if (pending_scroll_reset && threads.isNotEmpty()) {
            list_state.scrollToItem(0)
            pending_scroll_reset = false
        }
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
        "inbox" -> if (categories_enabled) {
            threads.count { it.has_unread }
        } else {
            inbox_state.stats?.unread ?: 0
        }
        "sent" -> inbox_state.stats?.sent ?: 0
        "drafts" -> inbox_state.stats?.drafts ?: 0
        "starred" -> inbox_state.stats?.starred ?: 0
        "archive" -> inbox_state.stats?.archived ?: 0
        "scheduled" -> inbox_state.stats?.scheduled ?: 0
        "spam" -> inbox_state.stats?.spam ?: 0
        "trash" -> inbox_state.stats?.trash ?: 0
        else -> 0
    }
    val folder_total = when (current_folder) {
        "inbox" -> inbox_state.stats?.inbox ?: 0
        "sent" -> inbox_state.stats?.sent ?: 0
        "drafts" -> inbox_state.stats?.drafts ?: 0
        "starred" -> inbox_state.stats?.starred ?: 0
        "archive" -> inbox_state.stats?.archived ?: 0
        "scheduled" -> inbox_state.stats?.scheduled ?: 0
        "spam" -> inbox_state.stats?.spam ?: 0
        "trash" -> inbox_state.stats?.trash ?: 0
        else -> if (current_folder.startsWith("label:") || current_folder.startsWith("tag:")) {
            inbox_state.total
        } else {
            0
        }
    }
    val visible_threads = threads
    val top_thread_key = visible_threads.firstOrNull()?.thread_id
    LaunchedEffect(top_thread_key) {
        if (top_thread_key != null &&
            list_state.firstVisibleItemIndex <= 1 &&
            list_state.firstVisibleItemScrollOffset == 0 &&
            !list_state.isScrollInProgress
        ) {
            list_state.scrollToItem(0)
        }
    }

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

    val scope_selection = select_all_active &&
        scope_selection_confirmed &&
        mail_vm.folder_supports_scope_selection(current_folder)
    val selection_count = scope_selection_count(scope_selection, folder_total, selected_ids.size)
    val can_offer_scope_selection = select_mode &&
        select_all_active &&
        !scope_selection_confirmed &&
        inbox_state.has_more &&
        folder_total > selected_ids.size &&
        mail_vm.folder_supports_scope_selection(current_folder)

    LaunchedEffect(list_state, current_folder) {
        snapshotFlow {
            val layout_info = list_state.layoutInfo
            val total = layout_info.totalItemsCount
            val last_visible = layout_info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val near_end = total > 0 && (total - last_visible) <= 3
            val s = inbox_state
            near_end &&
                s.has_more &&
                !s.is_loading &&
                !s.is_loading_more &&
                !s.initial &&
                s.items.isNotEmpty() &&
                s.next_cursor != null &&
                s.current_folder == current_folder
        }.distinctUntilChanged().collect { should_load_more ->
            if (should_load_more) mail_vm.load_more()
        }
    }

    LaunchedEffect(
        categories_enabled,
        active_category,
        active_tabs,
        emails_fingerprint,
        threads.size,
        inbox_state.has_more,
        inbox_state.is_loading,
        inbox_state.is_loading_more,
        inbox_state.initial,
    ) {
        val s = inbox_state
        if (s.has_more &&
            !s.is_loading &&
            !s.is_loading_more &&
            !s.initial &&
            s.next_cursor != null &&
            s.current_folder == current_folder
        ) {
            val visible_rows = if (categories_enabled) {
                emails.count {
                    org.astermail.android.mail.category_for_tab(it.category, active_tabs) == active_category
                }
            } else if (emails.isNotEmpty() && threads.isEmpty()) {
                MIN_FILLED_ROWS
            } else {
                threads.size
            }
            if (visible_rows < MIN_FILLED_ROWS && s.items.size < CATEGORY_DRAIN_MAX_ITEMS) mail_vm.load_more()
        }
    }

    fun do_refresh() {
        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        mail_vm.refresh()
    }

    fun mark_all_read(target_read: Boolean) {
        if (target_read) {
            mail_vm.mark_all_read_scope(current_folder)
            for (i in emails.indices) {
                if (!emails[i].is_read) {
                    note_read_mutation(emails[i].id)
                    emails[i] = emails[i].copy(is_read = true)
                }
            }
        } else {
            mail_vm.mark_all_unread_scope(current_folder)
            for (i in emails.indices) {
                note_read_mutation(emails[i].id)
                emails[i] = emails[i].copy(is_read = false)
            }
        }
    }

    fun select_all() {
        select_mode = true
        selected_ids.clear()
        selected_ids.addAll(visible_threads.map { it.thread_id })
        select_all_active = true
        scope_selection_confirmed = mail_vm.folder_supports_scope_selection(current_folder)
        if (inbox_state.has_more && folder_total <= SELECT_ALL_DRAIN_LIMIT) {
            select_all_loading = true
            mail_vm.load_all_remaining { select_all_loading = false }
        } else {
            select_all_loading = false
        }
    }

    fun toggle_select_all() {
        val all_selected = visible_threads.isNotEmpty() && selected_ids.size >= visible_threads.size
        if (select_all_active || all_selected) {
            select_mode = false
            select_all_active = false
            select_all_loading = false
            scope_selection_confirmed = false
            selected_ids.clear()
            mail_vm.cancel_load_all_remaining()
        } else {
            select_all()
        }
    }

    fun exit_select_mode() {
        select_mode = false
        select_all_active = false
        select_all_loading = false
        scope_selection_confirmed = false
        selected_ids.clear()
        mail_vm.cancel_load_all_remaining()
    }

    androidx.activity.compose.BackHandler(enabled = select_mode) { exit_select_mode() }

    fun toggle_selection(id: String) {
        select_all_active = false
        scope_selection_confirmed = false
        if (selected_ids.contains(id)) selected_ids.remove(id) else selected_ids.add(id)
        if (selected_ids.isEmpty()) select_mode = false
    }

    fun thread_id_at_offset(y: Float): String? {
        val info = list_state.layoutInfo
        val item_y = y + info.viewportStartOffset
        val item = info.visibleItemsInfo.firstOrNull { item ->
            item_y >= item.offset && item_y < item.offset + item.size
        } ?: return null
        val key = item.key as? String ?: return null
        if (key.startsWith("_")) return null
        return key
    }

    fun thread_index_at_offset(y: Float): Int? {
        val id = thread_id_at_offset(y) ?: return null
        val idx = visible_threads.indexOfFirst { it.thread_id == id }
        return if (idx >= 0) idx else null
    }

    val drag_anchor_index = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val drag_last_index = remember { androidx.compose.runtime.mutableIntStateOf(-1) }
    val drag_pre_selected = remember { mutableStateListOf<String>() }
    val drag_pointer_y = remember { androidx.compose.runtime.mutableFloatStateOf(-1f) }
    val drag_viewport_height = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var drag_selecting by remember { mutableStateOf(false) }

    fun apply_drag_selection(y: Float) {
        val anchor = drag_anchor_index.intValue
        if (anchor < 0) return
        val idx = thread_index_at_offset(y) ?: return
        if (idx == drag_last_index.intValue) return
        drag_last_index.intValue = idx
        val lo = minOf(anchor, idx)
        val hi = maxOf(anchor, idx)
        val target = LinkedHashSet(drag_pre_selected)
        for (i in lo..hi) {
            visible_threads.getOrNull(i)?.thread_id?.let { target.add(it) }
        }
        if (target.size != selected_ids.size || !target.containsAll(selected_ids)) {
            if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selected_ids.clear()
            selected_ids.addAll(target)
            select_all_active = false
            scope_selection_confirmed = false
        }
    }

    val drag_edge_px = with(LocalDensity.current) { 96.dp.toPx() }
    LaunchedEffect(drag_selecting) {
        if (!drag_selecting) return@LaunchedEffect
        while (true) {
            androidx.compose.runtime.withFrameNanos { }
            val y = drag_pointer_y.floatValue
            val viewport = drag_viewport_height.floatValue
            if (y < 0f || viewport <= 0f) continue
            val ratio = when {
                y < drag_edge_px -> -(1f - (y / drag_edge_px))
                y > viewport - drag_edge_px -> 1f - ((viewport - y) / drag_edge_px)
                else -> 0f
            }.coerceIn(-1f, 1f)
            if (ratio != 0f) {
                val step = ratio * 26f
                list_state.scrollBy(step)
                apply_drag_selection(y)
            }
        }
    }

    fun selected_email_ids(): List<String> {
        val thread_ids = selected_ids.toSet()
        return emails.filter { (it.thread_id in thread_ids || it.id in thread_ids) }.map { it.id }
    }

    fun archive_selected() {
        val ids = selected_email_ids()
        val thread_count = selected_ids.size
        val to_remove = selected_ids.toSet()
        mail_vm.archive(ids, thread_count)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun delete_selected() {
        val ids = selected_email_ids()
        val thread_count = selected_ids.size
        val to_remove = selected_ids.toSet()
        mail_vm.trash(ids, thread_count)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun restore_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        mail_vm.restore_trash(ids)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun unarchive_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        mail_vm.unarchive(ids)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun unmark_spam_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        mail_vm.unmark_spam(ids)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun mark_spam_selected() {
        val ids = selected_email_ids()
        val thread_count = selected_ids.size
        val to_remove = selected_ids.toSet()
        mail_vm.mark_spam(ids, thread_count)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun delete_permanent_selected() {
        val ids = selected_email_ids()
        val to_remove = selected_ids.toSet()
        ids.forEach { mail_vm.delete_permanent(it) }
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun mark_read_selected() {
        val thread_ids = selected_ids.toSet()
        val email_ids = emails
            .filter { (it.thread_id in thread_ids || it.id in thread_ids) && !it.is_read }
            .map { it.id }
        if (email_ids.isNotEmpty()) {
            mail_vm.mark_read_bulk(email_ids)
        }
        for (i in emails.indices) {
            if ((emails[i].thread_id in thread_ids || emails[i].id in thread_ids) && !emails[i].is_read) {
                note_read_mutation(emails[i].id)
                emails[i] = emails[i].copy(is_read = true)
            }
        }
        exit_select_mode()
    }

    fun mark_unread_selected() {
        val thread_ids = selected_ids.toSet()
        val email_ids = emails
            .filter { (it.thread_id in thread_ids || it.id in thread_ids) && it.is_read }
            .map { it.id }
        if (email_ids.isNotEmpty()) {
            mail_vm.mark_unread_bulk(email_ids)
        }
        for (i in emails.indices) {
            if ((emails[i].thread_id in thread_ids || emails[i].id in thread_ids) && emails[i].is_read) {
                note_read_mutation(emails[i].id)
                emails[i] = emails[i].copy(is_read = false)
            }
        }
        exit_select_mode()
    }

    fun star_selected() {
        val thread_ids = selected_ids.toSet()
        val new_starred = emails.any { (it.thread_id in thread_ids || it.id in thread_ids) && !it.is_starred }
        mail_vm.star_bulk(selected_email_ids())
        for (i in emails.indices) {
            if ((emails[i].thread_id in thread_ids || emails[i].id in thread_ids) && emails[i].is_starred != new_starred) {
                emails[i] = emails[i].copy(is_starred = new_starred)
            }
        }
        exit_select_mode()
    }

    fun notify_if_scope_incomplete(applied: Int) {
        if (scope_selection && folder_total > applied) {
            mail_vm.notify_partial_scope_selection(applied, folder_total)
        }
    }

    fun snooze_selected(iso: String, label: String) {
        val to_remove = selected_ids.toSet()
        val ids = selected_email_ids()
        notify_if_scope_incomplete(ids.size)
        mail_vm.snooze_bulk(ids, iso, label)
        emails.removeAll { (it.thread_id in to_remove || it.id in to_remove) }
        exit_select_mode()
    }

    fun move_selected_to_folder(label_token: String, display_name: String) {
        val ids = selected_email_ids()
        notify_if_scope_incomplete(ids.size)
        mail_vm.apply_label_bulk(ids, label_token, display_name)
        exit_select_mode()
    }

    fun label_selected(tag_token: String, display_name: String) {
        val ids = selected_email_ids()
        notify_if_scope_incomplete(ids.size)
        mail_vm.apply_tag_bulk(ids, tag_token, display_name)
        exit_select_mode()
    }

    fun unlabel_selected(tag_token: String, display_name: String) {
        val ids = selected_email_ids()
        notify_if_scope_incomplete(ids.size)
        mail_vm.remove_tag_bulk(ids, tag_token, display_name)
        exit_select_mode()
    }

    fun unsnooze_selected() {
        val ids = selected_email_ids()
        notify_if_scope_incomplete(ids.size)
        mail_vm.unsnooze_bulk(ids)
        exit_select_mode()
    }

    fun scope_action_name(action_id: String): String? = when (action_id) {
        "read" -> "mark_read"
        "unread" -> "mark_unread"
        "trash" -> "trash"
        "archive" -> "archive"
        "spam" -> "mark_spam"
        "not_spam" -> "unmark_spam"
        "restore" -> "restore_trash"
        "unarchive" -> "unarchive"
        else -> null
    }

    fun apply_selection_action(action_id: String) {
        when (action_id) {
            "read" -> mark_read_selected()
            "unread" -> mark_unread_selected()
            "trash" -> delete_selected()
            "archive" -> archive_selected()
            "not_spam" -> unmark_spam_selected()
            "restore" -> restore_selected()
            "unarchive" -> unarchive_selected()
            "delete_permanent" -> delete_permanent_selected()
            "folder" -> {
                if (settings_state.labels.isEmpty()) settings_vm.load_labels()
                show_bulk_folder_sheet = true
            }
            "label" -> show_bulk_label_sheet = true
            "star" -> star_selected()
            "snooze" -> show_bulk_snooze_sheet = true
            "unsnooze" -> unsnooze_selected()
            "spam" -> mark_spam_selected()
        }
    }

    fun run_selection_action(action_id: String) {
        if (scope_selection && action_id == "star") {
            val thread_ids = selected_ids.toSet()
            mail_vm.star_scope(current_folder, emails.any { (it.thread_id in thread_ids || it.id in thread_ids) && !it.is_starred })
            exit_select_mode()
            return
        }
        if (scope_selection && action_id == "delete_permanent" && current_folder == "trash") {
            mail_vm.empty_trash()
            exit_select_mode()
            return
        }
        val scope_action = scope_action_name(action_id)
        if (scope_selection && scope_action != null && mail_vm.action_supports_scope_selection(scope_action)) {
            mail_vm.bulk_scope_action(current_folder, scope_action, null)
            exit_select_mode()
            return
        }
        apply_selection_action(action_id)
    }

    LaunchedEffect(current_folder) {
        select_all_loading = false
        scope_selection_confirmed = false
    }

    val density = LocalDensity.current
    val nav_bar_bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val status_bar_top = androidx.compose.foundation.layout.WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    var header_height_px by remember { mutableIntStateOf(0) }
    val header_offset_px = remember { mutableFloatStateOf(0f) }
    var header_hidden by remember { mutableStateOf(false) }
    val header_height_dp = with(density) { header_height_px.toDp() }
    val pull_state = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val header_nested_scroll = remember(header_offset_px, pull_state) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val limit = header_height_px.toFloat()
                if (limit == 0f) return Offset.Zero
                if (pull_state.distanceFraction > 0f) return Offset.Zero
                if (consumed.y == 0f) return Offset.Zero
                val next = (header_offset_px.floatValue + consumed.y).coerceIn(-limit, 0f)
                if (next != header_offset_px.floatValue) header_offset_px.floatValue = next
                val hidden = if (header_hidden) next <= -limit * 0.15f else next <= -limit * 0.6f
                if (hidden != header_hidden) header_hidden = hidden
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(select_mode) {
        if (select_mode) {
            header_offset_px.floatValue = 0f
            header_hidden = false
        }
    }
    LaunchedEffect(scroll_top_token, list_state) {
        if (scroll_top_token > 0) {
            list_state.scrollToItem(0)
            header_offset_px.floatValue = 0f
            header_hidden = false
        }
    }
    LaunchedEffect(list_state) {
        snapshotFlow {
            !list_state.canScrollForward && !list_state.canScrollBackward
        }.distinctUntilChanged().collect { not_scrollable ->
            if (not_scrollable) {
                header_offset_px.floatValue = 0f
                header_hidden = false
            }
        }
    }
    LaunchedEffect(list_state) {
        snapshotFlow {
            list_state.firstVisibleItemIndex == 0 &&
                list_state.firstVisibleItemScrollOffset == 0 &&
                header_offset_px.floatValue != 0f
        }.distinctUntilChanged().collect { at_top ->
            if (at_top) {
                header_offset_px.floatValue = 0f
                header_hidden = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .nestedScroll(header_nested_scroll),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullToRefresh(
                        isRefreshing = is_refreshing && !select_mode,
                        state = pull_state,
                        enabled = !select_mode,
                        threshold = pull_refresh_threshold,
                        onRefresh = { if (!select_mode) do_refresh() },
                    ),
            ) {
                val pull_indicator: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
                    val refreshing_now = is_refreshing && !select_mode
                    val fraction = pull_state.distanceFraction
                    if (fraction > 0.01f || refreshing_now) {
                        val travel_px = with(density) { pull_refresh_travel.toPx() }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = header_height_dp)
                                .graphicsLayer {
                                    translationY = if (refreshing_now) travel_px
                                    else fraction.coerceIn(0f, 1.4f) * travel_px
                                    alpha = if (refreshing_now) 1f
                                    else (fraction * 2f).coerceIn(0f, 1f)
                                }
                                .size(44.dp)
                                .shadow(6.dp, CircleShape)
                                .background(colors.bg_card, CircleShape)
                                .border(1.dp, colors.border_primary, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (refreshing_now) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = colors.accent_blue,
                                    strokeWidth = 2.5.dp,
                                )
                            } else {
                                CircularProgressIndicator(
                                    progress = { fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.size(22.dp),
                                    color = colors.accent_blue,
                                    strokeWidth = 2.5.dp,
                                    trackColor = Color.Transparent,
                                )
                            }
                        }
                    }
                }
                val hidden_by_category = threads.isEmpty() && !threads_pending && inbox_state.items.isNotEmpty()
                val unread_mismatch = threads.isEmpty() &&
                    inbox_state.items.isEmpty() &&
                    !inbox_state.is_loading &&
                    !inbox_state.initial &&
                    current_folder == "inbox" &&
                    (inbox_state.stats?.unread ?: 0) > 0
                var contradicts_unread by remember { mutableStateOf(false) }
                LaunchedEffect(unread_mismatch) {
                    if (!unread_mismatch) {
                        contradicts_unread = false
                    } else {
                        kotlinx.coroutines.delay(UNREAD_MISMATCH_GRACE_MS)
                        contradicts_unread = true
                    }
                }
                val skeleton_target = inbox_state.initial ||
                    ((inbox_state.is_loading || threads_pending) && threads.isEmpty())
                var show_skeleton by remember { mutableStateOf(skeleton_target) }
                var skeleton_shown_at by remember { mutableStateOf(0L) }
                LaunchedEffect(skeleton_target) {
                    if (skeleton_target) {
                        if (!show_skeleton) {
                            show_skeleton = true
                            skeleton_shown_at = android.os.SystemClock.uptimeMillis()
                        }
                    } else if (show_skeleton) {
                        val shown_for = android.os.SystemClock.uptimeMillis() - skeleton_shown_at
                        if (shown_for < MIN_SKELETON_MS) {
                            kotlinx.coroutines.delay(MIN_SKELETON_MS - shown_for)
                        }
                        show_skeleton = false
                    }
                }
                if (skeleton_target || (show_skeleton && threads.isEmpty())) {
                    Box(Modifier.padding(top = header_height_dp)) {
                        inbox_skeleton(list_density = settings_state.preferences?.mail_list_density)
                    }
                } else if (threads.isEmpty() && inbox_state.error != null) {
                    Box(Modifier.padding(top = header_height_dp)) {
                        inbox_error_state(inbox_state.error.orEmpty()) {
                            mail_vm.load_inbox(current_folder, force = true)
                        }
                    }
                } else if (contradicts_unread) {
                    Box(Modifier.padding(top = header_height_dp)) {
                        inbox_error_state(stringResource(R.string.error_generic)) {
                            mail_vm.load_inbox(current_folder, force = true)
                        }
                    }
                } else if (hidden_by_category) {
                    org.astermail.android.ui.common.overscroll_stretch(
                        modifier = Modifier.padding(top = header_height_dp),
                    ) { empty_category_state(active_category_label) }
                } else if (threads.isEmpty()) {
                    org.astermail.android.ui.common.overscroll_stretch(
                        modifier = Modifier.padding(top = header_height_dp),
                    ) { empty_inbox_state(current_folder) }
                } else {
                    val user_prefs_outer = settings_state.preferences
                    val right_action_outer = normalize_swipe_action(
                        user_prefs_outer?.swipe_right_action,
                        DEFAULT_SWIPE_RIGHT_ACTION,
                    )
                    val left_action_outer = normalize_swipe_action(
                        user_prefs_outer?.swipe_left_action,
                        DEFAULT_SWIPE_LEFT_ACTION,
                    )
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
                                start_icon = TablerIcons.Inbox, end_icon = TablerIcons.Trash,
                                start_action = "unarchive", end_action = "delete",
                            )
                            "trash" -> SwipeConfig(
                                start_label = restore_label_outer, end_label = delete_forever_label_outer,
                                start_icon = TablerIcons.Inbox, end_icon = TablerIcons.Trash,
                                start_action = "restore_trash", end_action = "delete_permanent",
                            )
                            "spam" -> SwipeConfig(
                                start_label = not_spam_label_outer, end_label = delete_label_outer,
                                start_icon = TablerIcons.Inbox, end_icon = TablerIcons.Trash,
                                start_action = "unmark_spam", end_action = "delete",
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
                            .pointerInput(Unit) {
                                val press_slop = viewConfiguration.touchSlop
                                val long_press_ms = viewConfiguration.longPressTimeoutMillis
                                val drag_start_slop = 24.dp.toPx()
                                awaitEachGesture {
                                    val down = awaitFirstDown(
                                        requireUnconsumed = false,
                                        pass = PointerEventPass.Initial,
                                    )
                                    val anchor_id = thread_id_at_offset(down.position.y)
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
                                    var drag_travel = 0f
                                    var drag_started = false
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull() ?: break
                                            if (drag_started) change.consume()
                                            if (!change.pressed) break
                                            drag_travel += (change.position - change.previousPosition).getDistance()
                                            if (drag_travel < drag_start_slop) continue
                                            if (!select_mode) continue
                                            if (!drag_started) {
                                                val anchor_index = visible_threads.indexOfFirst { it.thread_id == anchor_id }
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = header_height_dp, bottom = 96.dp + nav_bar_bottom),
                    ) {
                        val spam_retention_days = settings_state.preferences?.auto_delete_spam_days ?: 0
                        if (current_folder == "spam" && !select_mode && spam_retention_days > 0) {
                            item(key = "_spam_retention_notice", contentType = "spam_notice") {
                                spam_retention_banner(days = spam_retention_days)
                            }
                        }
                        itemsIndexed(
                            items = visible_threads,
                            key = { _, item -> item.thread_id },
                            contentType = { _, _ -> if (select_mode) "thread_row_select" else "thread_row" },
                        ) { row_index, thread ->
                            val is_selected by remember(thread.thread_id) {
                                derivedStateOf { select_mode && selected_ids.contains(thread.thread_id) }
                            }
                            if (select_mode) {
                                Box(
                                    modifier = Modifier
                                        .animateItem()
                                        .fillMaxWidth(),
                                ) {
                                    ThreadInboxRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        thread = thread,
                                        on_click = { toggle_selection(thread.thread_id) },
                                        on_long_click = { toggle_selection(thread.thread_id) },
                                        on_toggle_star = {
                                            val idx = emails.indexOfFirst { (it.thread_id == thread.thread_id || it.id == thread.thread_id) }
                                            if (idx >= 0) {
                                                emails[idx] = emails[idx].copy(is_starred = !emails[idx].is_starred)
                                                mail_vm.toggle_star(thread.newest.id)
                                            }
                                        },
                                        is_selected = is_selected,
                                        select_mode = true,
                                        haptic_enabled = haptic_enabled,
                                        is_first = row_index == 0,
                                        is_last = row_index == visible_threads.lastIndex,
                                        user_prefs = settings_state.preferences,
                                    )
                                }
                            } else {
                                val swipe_config = hoisted_swipe_config
                                swipeable_thread_row(
                                    modifier = Modifier.animateItem(),
                                    list_scrolling = { list_state.isScrollInProgress },
                                    thread = thread,
                                    is_first = row_index == 0,
                                    is_last = row_index == visible_threads.lastIndex,
                                    is_pinned = thread.is_pinned,
                                    on_click = { on_open_email(thread_open_target_id(thread)) },
                                    on_long_click = {
                                        select_mode = true
                                        selected_ids.clear()
                                        selected_ids.add(thread.thread_id)
                                    },
                                    on_toggle_star = {
                                        val idx = emails.indexOfFirst { (it.thread_id == thread.thread_id || it.id == thread.thread_id) }
                                        if (idx >= 0) {
                                            emails[idx] = emails[idx].copy(is_starred = !emails[idx].is_starred)
                                            mail_vm.toggle_star(thread.newest.id)
                                        }
                                    },
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
                                        val ids = emails.filter { (it.thread_id == thread.thread_id || it.id == thread.thread_id) }.map { it.id }
                                        val prefs = settings_state.preferences
                                        val needs_confirm = (swipe_config.start_action == "archive" && prefs?.confirm_archive == true) ||
                                            (swipe_config.start_action == "delete" && prefs?.confirm_delete == true) ||
                                            (swipe_config.start_action == "spam" && prefs?.confirm_spam == true) ||
                                            (swipe_config.start_action == "delete_permanent" && prefs?.confirm_delete == true)
                                        if (needs_confirm) {
                                            confirm_action_pending = swipe_config.start_action
                                            confirm_item_ids_pending = ids
                                            confirm_thread_id_pending = thread.thread_id
                                        } else {
                                            execute_swipe_action(
                                                swipe_config.start_action, ids, mail_vm, emails, thread.thread_id, current_folder,
                                                on_read_mutation = { mutated -> mutated.forEach { note_read_mutation(it) } },
                                            ) { snooze_ids ->
                                                swipe_snooze_ids = snooze_ids
                                            }
                                        }
                                    },
                                    on_swipe_end = {
                                        if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val ids = emails.filter { (it.thread_id == thread.thread_id || it.id == thread.thread_id) }.map { it.id }
                                        val prefs = settings_state.preferences
                                        val needs_confirm = (swipe_config.end_action == "archive" && prefs?.confirm_archive == true) ||
                                            (swipe_config.end_action == "delete" && prefs?.confirm_delete == true) ||
                                            (swipe_config.end_action == "spam" && prefs?.confirm_spam == true) ||
                                            (swipe_config.end_action == "delete_permanent" && prefs?.confirm_delete == true)
                                        if (needs_confirm) {
                                            confirm_action_pending = swipe_config.end_action
                                            confirm_item_ids_pending = ids
                                            confirm_thread_id_pending = thread.thread_id
                                        } else {
                                            execute_swipe_action(
                                                swipe_config.end_action, ids, mail_vm, emails, thread.thread_id, current_folder,
                                                on_read_mutation = { mutated -> mutated.forEach { note_read_mutation(it) } },
                                            ) { snooze_ids ->
                                                swipe_snooze_ids = snooze_ids
                                            }
                                        }
                                    },
                                    haptic_enabled = haptic_enabled,
                                    user_prefs = settings_state.preferences,
                                )
                            }
                        }

                        if (inbox_state.is_loading_more) {
                            items(
                                count = 3,
                                key = { "_loading_more_$it" },
                                contentType = { "skeleton_row" },
                            ) { skeleton_index ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateItem()
                                        .padding(top = if (skeleton_index == 0) inbox_group_split else 0.dp),
                                ) {
                                    inbox_skeleton_row(
                                        list_density = settings_state.preferences?.mail_list_density,
                                        is_first = false,
                                        is_last = skeleton_index == 2,
                                    )
                                }
                            }
                        } else if (
                            !inbox_state.has_more &&
                            !inbox_state.is_loading &&
                            !inbox_state.initial &&
                            visible_threads.isNotEmpty()
                        ) {
                            item(key = "_no_more") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().animateItem(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
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
                    org.astermail.android.ui.common.fast_scroll_bar(
                        state = list_state,
                        modifier = Modifier.align(Alignment.TopEnd),
                        top_padding = header_height_dp,
                        bottom_padding = 96.dp + nav_bar_bottom,
                    )
                }
                pull_indicator()
            }
        }

        val header_bg = colors.bg_primary
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .offset { IntOffset(0, header_offset_px.floatValue.roundToInt()) }
                .onSizeChanged { if (it.height > 0) header_height_px = it.height }
                .drawBehind {
                    val limit = header_height_px.toFloat()
                    val fraction = if (limit == 0f) 0f else (-header_offset_px.floatValue / limit).coerceIn(0f, 1f)
                    drawRect(color = header_bg, alpha = 1f - fraction)
                }
                ,
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(status_bar_top))
            androidx.compose.animation.Crossfade(targetState = select_mode, label = "topbar_mode") { mode ->
                if (mode) {
                    select_mode_top_bar(
                        selected_count = selection_count,
                        on_close = ::exit_select_mode,
                        on_select_all = ::toggle_select_all,
                        show_divider = scrolled_elevation,
                        current_folder = current_folder,
                        counting = select_all_loading,
                        all_selected = select_all_active ||
                            (visible_threads.isNotEmpty() && selection_count >= visible_threads.size),
                    )
                } else {
                    inbox_top_bar(
                        folder_title = display_title ?: folder_display_name(current_folder),
                        search_scope_title = active_category_label,
                        unread_count = folder_count,
                        on_open_drawer = on_open_drawer,
                        on_open_search = on_open_search,
                        on_enter_select_mode = {
                            select_mode = true
                            selected_ids.clear()
                        },
                        on_refresh = ::do_refresh,
                        on_mark_all_read = { target_read -> mark_all_read(target_read) },
                        has_unread = threads.any { it.has_unread },
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
                        folder_unread_counts = folder_unread_counts,
                        all_mail_include_spam = all_mail_include_spam,
                        all_mail_include_trash = all_mail_include_trash,
                        on_all_mail_scope_change = on_all_mail_scope_change,
                    )
                }
            }
            scope_selection_banner(
                offered = can_offer_scope_selection,
                confirmed = scope_selection,
                folder_total = folder_total,
                folder_name = display_title ?: folder_display_name(current_folder),
                crosses_categories = categories_enabled,
                on_confirm = { scope_selection_confirmed = true },
            )
          }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(status_bar_top)
                .background(colors.bg_primary),
        )

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
                selected_count = selection_count,
                custom_actions = selection_toolbar_slots,
                on_action = ::run_selection_action,
                on_more = { show_selection_overflow = true },
                current_folder = current_folder,
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = !select_mode,
            enter = androidx.compose.animation.slideInHorizontally(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 150, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                initialOffsetX = { it },
            ) + androidx.compose.animation.fadeIn(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 110),
            ),
            exit = androidx.compose.animation.slideOutHorizontally(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 130, easing = androidx.compose.animation.core.FastOutLinearInEasing),
                targetOffsetX = { it },
            ) + androidx.compose.animation.fadeOut(
                animationSpec = androidx.compose.animation.core.tween(durationMillis = 100),
            ),
            modifier = Modifier.align(Alignment.BottomEnd),
        ) {
            compose_fab(
                expanded = !header_hidden,
                on_click = {
                    if (haptic_enabled) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    on_compose()
                },
            )
        }

        if (show_selection_overflow) {
            selection_overflow_sheet(
                on_close = { show_selection_overflow = false },
                on_action = { id ->
                    show_selection_overflow = false
                    run_selection_action(id)
                },
                show_unsnooze = current_folder == "snoozed",
                on_customize = {
                    show_selection_overflow = false
                    on_customize_toolbar()
                },
            )
        }

        if (show_bulk_folder_sheet) {
            val unnamed_folder_label = stringResource(R.string.unnamed_folder)
            val folder_decrypt_failed_label = stringResource(R.string.folder_decrypt_failed)
            val folder_items = org.astermail.android.folders.flatten_folder_tree(settings_state.labels)
                .map { node ->
                    val label = node.label
                    val readable = label.encrypted_name?.takeIf {
                        it.isNotBlank() && !org.astermail.android.looks_encrypted(it)
                    }
                    label.copy(encrypted_name = readable ?: folder_decrypt_failed_label)
                }
            label_picker_sheet(
                title = stringResource(R.string.move_to_folder),
                empty_message = stringResource(R.string.no_folders_yet_create),
                items = folder_items,
                on_close = { show_bulk_folder_sheet = false },
                on_pick = { picked ->
                    val display = picked.encrypted_name?.takeIf { it.isNotBlank() }
                        ?: unnamed_folder_label
                    show_bulk_folder_sheet = false
                    move_selected_to_folder(picked.label_token, display)
                },
            )
        }

        if (show_bulk_label_sheet) {
            val tag_items = org.astermail.android.labels.tag_rows(settings_state.tags)
            val selected = selected_email_ids().toSet()
            val selected_items = inbox_state.items.filter { it.id in selected }
            val applied_tags = if (selected_items.isEmpty()) {
                emptySet()
            } else {
                selected_items.map { it.tag_tokens.toSet() }.reduce { acc, tokens -> acc intersect tokens }
            }
            tag_picker_sheet(
                title = stringResource(R.string.edit_labels),
                empty_message = stringResource(R.string.no_labels_yet_create),
                items = tag_items,
                on_close = { show_bulk_label_sheet = false },
                on_pick = { picked ->
                    val display = picked.encrypted_name.takeIf { it.isNotBlank() } ?: picked.tag_token
                    show_bulk_label_sheet = false
                    if (picked.tag_token in applied_tags) {
                        unlabel_selected(picked.tag_token, display)
                    } else {
                        label_selected(picked.tag_token, display)
                    }
                },
                applied_tokens = applied_tags,
            )
        }

        if (show_bulk_snooze_sheet) {
            snooze_sheet(
                on_close = { show_bulk_snooze_sheet = false },
                on_pick = { iso, label ->
                    show_bulk_snooze_sheet = false
                    snooze_selected(iso, label)
                },
            )
        }

        if (swipe_snooze_ids.isNotEmpty()) {
            val pending_snooze_ids = swipe_snooze_ids
            snooze_sheet(
                on_close = { swipe_snooze_ids = emptyList() },
                on_pick = { iso, label ->
                    swipe_snooze_ids = emptyList()
                    mail_vm.snooze_bulk(pending_snooze_ids, iso, label)
                    emails.removeAll { pending_snooze_ids.contains(it.id) }
                },
            )
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
                    "delete", "trash" -> R.string.confirm_trash_title
                    "spam" -> R.string.confirm_spam_title
                    "delete_permanent" -> R.string.confirm_delete_permanent_title
                    else -> R.string.confirm
                }),
                message = stringResource(when (pending_action) {
                    "archive" -> R.string.confirm_archive_message
                    "delete", "trash" -> R.string.confirm_trash_message
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

internal data class quick_switch_folder(
    val id: String,
    val label_res: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

internal val quick_switch_folders = listOf(
    quick_switch_folder("inbox", R.string.folder_inbox, TablerIcons.Inbox),
    quick_switch_folder("sent", R.string.folder_sent, TablerIcons.Send),
    quick_switch_folder("drafts", R.string.folder_drafts, TablerIcons.FileText),
    quick_switch_folder("archive", R.string.folder_archive, TablerIcons.Archive),
    quick_switch_folder("starred", R.string.folder_starred, TablerIcons.Star),
    quick_switch_folder("scheduled", R.string.folder_scheduled, TablerIcons.Clock),
    quick_switch_folder("snoozed", R.string.folder_snoozed, TablerIcons.BellMinus),
    quick_switch_folder("spam", R.string.folder_spam, TablerIcons.AlertTriangle),
    quick_switch_folder("trash", R.string.folder_trash, TablerIcons.Trash),
    quick_switch_folder("all", R.string.folder_all_mail, all_mail_icon),
)

data class quick_folder_node(
    val id: String,
    val name: String,
    val depth: Int,
    val has_children: Boolean,
    val parent_id: String?,
)

private fun folder_ancestor_ids(nodes: List<quick_folder_node>, id: String?): Set<String> {
    if (id == null) return emptySet()
    val by_id = nodes.associateBy { it.id }
    val result = mutableSetOf<String>()
    var current = by_id[id]?.parent_id
    while (current != null && result.add(current)) {
        current = by_id[current]?.parent_id
    }
    return result
}

@Composable
private fun folder_tree_dropdown_items(
    nodes: List<quick_folder_node>,
    current_folder: String,
    folder_unread_counts: Map<String, Int>,
    on_select: (String, String) -> Unit,
) {
    val colors = AsterMaterial.colors
    val expanded = remember(nodes, current_folder) {
        mutableStateListOf<String>().apply { addAll(folder_ancestor_ids(nodes, current_folder)) }
    }
    val by_id = remember(nodes) { nodes.associateBy { it.id } }
    val visible = nodes.filter { node ->
        var parent = node.parent_id
        var shown = true
        while (parent != null) {
            if (parent !in expanded) {
                shown = false
                break
            }
            parent = by_id[parent]?.parent_id
        }
        shown
    }
    visible.forEach { node ->
        val is_expanded = node.id in expanded
        aster_dropdown_item(
            label = node.name,
            icon = TablerIcons.Folder,
            selected = node.id == current_folder,
            count = folder_unread_counts[node.id] ?: 0,
            indent = (node.depth * 14).dp,
            leading = {
                if (node.has_children) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(SquircleShape(8.dp))
                            .clickable {
                                if (is_expanded) expanded.remove(node.id) else expanded.add(node.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (is_expanded) TablerIcons.ChevronDown else TablerIcons.ChevronRight,
                            contentDescription = stringResource(
                                if (is_expanded) R.string.collapse_folder else R.string.expand_folder,
                                node.name,
                            ),
                            tint = colors.text_muted,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(22.dp))
                }
            },
            on_click = { on_select(node.id, node.name) },
        )
    }
}

@Composable
private fun all_mail_scope_chip(
    label: String,
    active: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .clip(SquircleShape(14.dp))
            .background(if (active) colors.accent_blue.copy(alpha = 0.16f) else Color.Transparent)
            .border(
                1.dp,
                if (active) colors.accent_blue.copy(alpha = 0.5f) else colors.border_primary,
                SquircleShape(14.dp),
            )
            .clickable(onClick = on_click)
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .testTag("all_mail_chip_$label"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (active) {
            Icon(
                imageVector = TablerIcons.Check,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            color = if (active) colors.accent_blue else colors.text_secondary,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
internal fun inbox_top_bar(
    folder_title: String,
    search_scope_title: String? = null,
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
    custom_folders: List<quick_folder_node> = emptyList(),
    on_custom_folder_change: (String, String) -> Unit = { _, _ -> },
    folder_unread_counts: Map<String, Int> = emptyMap(),
    all_mail_include_spam: Boolean = false,
    all_mail_include_trash: Boolean = false,
    on_all_mail_scope_change: (Boolean, Boolean) -> Unit = { _, _ -> },
) {
    val colors = AsterMaterial.colors
    val divider_alpha by animateFloatAsState(
        targetValue = if (show_divider) 1f else 0f,
        label = "divider_alpha",
    )
    var folder_menu_open by remember { mutableStateOf(false) }
    var overflow_menu_open by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.xs)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs)
                .height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsterIconButton(
                icon = TablerIcons.Menu2,
                content_description = stringResource(R.string.open_drawer),
                onClick = on_open_drawer,
                modifier = Modifier.testTag("account_avatar"),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .padding(horizontal = AsterSpacing.sm)
                    .clip(SquircleShape(26.dp))
                    .background(search_field_bg_color(colors))
                    .clickable { on_open_search() }
                    .padding(horizontal = AsterSpacing.lg)
                    .testTag("search"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (search_scope_title != null) {
                        stringResource(R.string.inbox_search_in_category, search_scope_title)
                    } else {
                        stringResource(R.string.inbox_search_in_folder, folder_title.lowercase(java.util.Locale.getDefault()))
                    },
                    color = colors.text_secondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            AsterIconButton(
                icon = TablerIcons.Settings,
                content_description = stringResource(R.string.settings),
                onClick = on_open_settings,
                modifier = Modifier.testTag("settings"),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.lg, end = AsterSpacing.sm)
                .padding(top = AsterSpacing.sm, bottom = AsterSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(SquircleShape(12.dp))
                        .clickable { folder_menu_open = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = folder_title,
                        color = colors.text_secondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                    )
                    if (unread_count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = unread_count.toString(),
                            color = colors.accent_blue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        imageVector = TablerIcons.ChevronDown,
                        contentDescription = stringResource(R.string.switch_folder),
                        tint = colors.text_muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                aster_dropdown_menu(
                    expanded = folder_menu_open,
                    on_dismiss = { folder_menu_open = false },
                ) {
                    quick_switch_folders.forEach { entry ->
                        val entry_selected = if (entry.id == all_mail_folder) {
                            is_all_mail_folder(current_folder)
                        } else {
                            entry.id == current_folder
                        }
                        aster_dropdown_item(
                            label = stringResource(entry.label_res),
                            icon = entry.icon,
                            selected = entry_selected,
                            count = folder_unread_counts[entry.id] ?: 0,
                            on_click = {
                                folder_menu_open = false
                                if (!entry_selected) on_folder_change(entry.id)
                            },
                        )
                    }
                    if (custom_folders.isNotEmpty()) {
                        aster_dropdown_divider()
                        folder_tree_dropdown_items(
                            nodes = custom_folders,
                            current_folder = current_folder,
                            folder_unread_counts = folder_unread_counts,
                            on_select = { id, name ->
                                folder_menu_open = false
                                if (id != current_folder) on_custom_folder_change(id, name)
                            },
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (is_all_mail_folder(current_folder)) {
                    Spacer(Modifier.width(AsterSpacing.sm))
                    all_mail_scope_chip(
                        label = stringResource(R.string.include_spam),
                        active = all_mail_include_spam,
                        on_click = { on_all_mail_scope_change(!all_mail_include_spam, all_mail_include_trash) },
                    )
                    Spacer(Modifier.width(6.dp))
                    all_mail_scope_chip(
                        label = stringResource(R.string.include_trash),
                        active = all_mail_include_trash,
                        on_click = { on_all_mail_scope_change(all_mail_include_spam, !all_mail_include_trash) },
                    )
                    Spacer(Modifier.width(AsterSpacing.sm))
                }
            }
            debug_build_pill_inline()
            Box {
                AsterIconButton(
                    icon = TablerIcons.DotsVertical,
                    content_description = stringResource(R.string.more_options),
                    onClick = { overflow_menu_open = true },
                    modifier = Modifier.testTag("inbox_overflow"),
                )
                aster_dropdown_menu(
                    expanded = overflow_menu_open,
                    on_dismiss = { overflow_menu_open = false },
                ) {
                    overflow_menu_item(
                        label = stringResource(if (has_unread) R.string.mark_all_read else R.string.mark_all_unread),
                        icon = if (has_unread) TablerIcons.MailOpened else TablerIcons.Mail,
                    ) {
                        overflow_menu_open = false
                        on_mark_all_read(has_unread)
                    }
                    overflow_menu_item(
                        label = stringResource(R.string.select),
                        icon = TablerIcons.SquareCheck,
                    ) {
                        overflow_menu_open = false
                        on_enter_select_mode()
                    }
                    overflow_menu_item(
                        label = stringResource(R.string.refresh),
                        icon = TablerIcons.Refresh,
                    ) {
                        overflow_menu_open = false
                        on_refresh()
                    }
                    if (current_folder == "trash") {
                        overflow_menu_item(
                            label = stringResource(R.string.empty_trash),
                            icon = TablerIcons.Trash,
                        ) {
                            overflow_menu_open = false
                            on_empty_trash()
                        }
                    }
                    aster_dropdown_divider()
                    aster_dropdown_section_label(stringResource(R.string.sort_by))
                    sort_menu_item(stringResource(R.string.sort_newest), sort_mode == InboxSortMode.newest) {
                        overflow_menu_open = false
                        on_sort_change(InboxSortMode.newest)
                    }
                    sort_menu_item(stringResource(R.string.sort_oldest), sort_mode == InboxSortMode.oldest) {
                        overflow_menu_open = false
                        on_sort_change(InboxSortMode.oldest)
                    }
                    sort_menu_item(stringResource(R.string.sort_unread), sort_mode == InboxSortMode.unread_first) {
                        overflow_menu_open = false
                        on_sort_change(InboxSortMode.unread_first)
                    }
                    sort_menu_item(stringResource(R.string.sort_starred), sort_mode == InboxSortMode.starred_first) {
                        overflow_menu_open = false
                        on_sort_change(InboxSortMode.starred_first)
                    }
                }
            }
        }
        if (divider_alpha > 0f) {
            AsterDivider(modifier = Modifier.fillMaxWidth())
        }
    }
}

internal fun scope_selection_count(
    scope_selection: Boolean,
    folder_total: Int,
    selected_count: Int,
): Int = if (scope_selection) maxOf(folder_total, selected_count) else selected_count

@Composable
private fun overflow_menu_item(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    on_click: () -> Unit,
) {
    aster_dropdown_item(
        label = label,
        icon = icon,
        on_click = on_click,
    )
}

@Composable
private fun sort_menu_item(label: String, is_selected: Boolean, on_click: () -> Unit) {
    aster_dropdown_item(
        label = label,
        selected = is_selected,
        on_click = on_click,
    )
}

@Composable
private fun select_mode_top_bar(
    selected_count: Int,
    on_close: () -> Unit,
    on_select_all: () -> Unit,
    show_divider: Boolean,
    current_folder: String = "inbox",
    counting: Boolean = false,
    all_selected: Boolean = false,
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
                icon = TablerIcons.X,
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (counting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp).testTag("select_all_progress"),
                    strokeWidth = 2.dp,
                    color = colors.accent_blue,
                )
                Spacer(Modifier.width(AsterSpacing.xs))
            }
            org.astermail.android.ui.common.select_all_button(
                on_click = on_select_all,
                modifier = Modifier.testTag("select_all"),
                all_selected = all_selected,
            )
        }
        if (divider_alpha > 0f) {
            AsterDivider(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun scope_selection_banner(
    offered: Boolean,
    confirmed: Boolean,
    folder_total: Int,
    folder_name: String,
    crosses_categories: Boolean,
    on_confirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!offered && !confirmed) return
    val colors = AsterMaterial.colors
    Column(modifier = modifier.fillMaxWidth().background(colors.bg_secondary)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (offered) Modifier.clickable(onClick = on_confirm) else Modifier)
                .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.sm)
                .testTag(if (confirmed) "scope_selection_confirmed" else "scope_selection_offer"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when {
                    confirmed -> stringResource(R.string.selection_scope_confirmed, folder_total, folder_name)
                    crosses_categories -> stringResource(R.string.selection_scope_offer_categories, folder_total, folder_name)
                    else -> stringResource(R.string.selection_scope_offer, folder_total, folder_name)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (confirmed) colors.text_secondary else colors.accent_blue,
                fontWeight = if (confirmed) FontWeight.Normal else FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AsterDivider(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun select_mode_bottom_bar(
    selected_count: Int,
    custom_actions: List<String>,
    on_action: (String) -> Unit,
    on_more: () -> Unit,
    current_folder: String,
    modifier: Modifier = Modifier,
) {
    val colors = AsterMaterial.colors
    val enabled = selected_count > 0
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.bg_primary,
        shadowElevation = 0.dp,
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
                when (current_folder) {
                    "trash" -> {
                        bottom_select_action(
                            icon = TablerIcons.MailOpened,
                            label = stringResource(R.string.mark_read_action),
                            enabled = enabled,
                            onClick = { on_action("read") },
                            test_tag = "mark_read",
                        )
                        bottom_select_action(
                            icon = TablerIcons.Inbox,
                            label = stringResource(R.string.swipe_restore),
                            enabled = enabled,
                            onClick = { on_action("restore") },
                            test_tag = "sel_action_restore",
                        )
                        bottom_select_action(
                            icon = TablerIcons.TrashOff,
                            label = stringResource(R.string.swipe_delete_forever),
                            enabled = enabled,
                            onClick = { on_action("delete_permanent") },
                            tint = colors.danger,
                            test_tag = "sel_action_delete_permanent",
                        )
                    }
                    "archive" -> {
                        bottom_select_action(
                            icon = TablerIcons.MailOpened,
                            label = stringResource(R.string.mark_read_action),
                            enabled = enabled,
                            onClick = { on_action("read") },
                            test_tag = "mark_read",
                        )
                        bottom_select_action(
                            icon = TablerIcons.Inbox,
                            label = stringResource(R.string.swipe_restore),
                            enabled = enabled,
                            onClick = { on_action("unarchive") },
                            test_tag = "sel_action_unarchive",
                        )
                        bottom_select_action(
                            icon = TablerIcons.Ban,
                            label = stringResource(R.string.report_spam),
                            enabled = enabled,
                            onClick = { on_action("spam") },
                            test_tag = "sel_action_spam",
                        )
                        bottom_select_action(
                            icon = TablerIcons.Trash,
                            label = stringResource(R.string.delete_action),
                            enabled = enabled,
                            onClick = { on_action("trash") },
                            tint = colors.danger,
                            test_tag = "sel_action_trash",
                        )
                    }
                    "spam" -> {
                        bottom_select_action(
                            icon = TablerIcons.MailOpened,
                            label = stringResource(R.string.mark_read_action),
                            enabled = enabled,
                            onClick = { on_action("read") },
                            test_tag = "mark_read",
                        )
                        bottom_select_action(
                            icon = TablerIcons.Inbox,
                            label = stringResource(R.string.swipe_not_spam),
                            enabled = enabled,
                            onClick = { on_action("not_spam") },
                            test_tag = "sel_action_not_spam",
                        )
                        bottom_select_action(
                            icon = TablerIcons.Trash,
                            label = stringResource(R.string.delete_action),
                            enabled = enabled,
                            onClick = { on_action("trash") },
                            tint = colors.danger,
                            test_tag = "sel_action_trash",
                        )
                    }
                    else -> {
                        custom_actions.forEach { action_id ->
                            val action = selection_toolbar_action_by_id(action_id) ?: return@forEach
                            bottom_select_action(
                                icon = action.icon,
                                label = stringResource(action.label_res),
                                enabled = enabled,
                                onClick = { on_action(action_id) },
                                tint = if (action_id == "trash" || action_id == "spam") colors.danger else colors.text_primary,
                                test_tag = "sel_action_$action_id",
                            )
                        }
                        bottom_select_action(
                            icon = TablerIcons.Dots,
                            label = stringResource(R.string.more_actions),
                            enabled = enabled,
                            onClick = on_more,
                            test_tag = "sel_action_more",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.bottom_select_action(
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
            .weight(1f)
            .clip(SquircleShape(18.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp)
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
        var label_size by remember(label) { mutableStateOf(11.sp) }
        Text(
            text = label,
            color = resolved_tint,
            fontSize = label_size,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (result.hasVisualOverflow && label_size.value > 8f) {
                    label_size = (label_size.value - 0.5f).sp
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun selection_overflow_sheet(
    on_close: () -> Unit,
    on_action: (String) -> Unit,
    on_customize: (() -> Unit)? = null,
    show_unsnooze: Boolean = false,
) {
    val colors = AsterMaterial.colors
    val state = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = on_close,
        sheetState = state,
        containerColor = colors.bg_card,
        tonalElevation = 0.dp,
        dragHandle = { org.astermail.android.design.components.AsterDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = AsterSpacing.xs),
        ) {
            overflow_sheet_row("star", TablerIcons.Star, stringResource(R.string.star), colors.text_primary) { on_action("star") }
            overflow_sheet_row("read", TablerIcons.MailOpened, stringResource(R.string.mark_as_read), colors.text_primary) { on_action("read") }
            overflow_sheet_row("unread", TablerIcons.Mail, stringResource(R.string.mark_as_unread), colors.text_primary) { on_action("unread") }
            overflow_sheet_row("archive", TablerIcons.Archive, stringResource(R.string.archive_action), colors.text_primary) { on_action("archive") }
            overflow_sheet_row("snooze", TablerIcons.Clock, stringResource(R.string.snooze), colors.text_primary) { on_action("snooze") }
            if (show_unsnooze) {
                overflow_sheet_row("unsnooze", TablerIcons.BellOff, stringResource(R.string.unsnooze), colors.text_primary) { on_action("unsnooze") }
            }
            overflow_sheet_row("folder", TablerIcons.Folder, stringResource(R.string.move_to_folder), colors.text_primary) { on_action("folder") }
            overflow_sheet_row("label", TablerIcons.Tag, stringResource(R.string.add_label), colors.text_primary) { on_action("label") }
            AsterDivider()
            overflow_sheet_row("trash", TablerIcons.Trash, stringResource(R.string.delete_action), colors.danger) { on_action("trash") }
            overflow_sheet_row("spam", TablerIcons.Ban, stringResource(R.string.report_spam), colors.danger) { on_action("spam") }
            if (on_customize != null) {
                AsterDivider()
                overflow_sheet_row("customize", TablerIcons.Settings, stringResource(R.string.customize_toolbar), colors.text_secondary, on_customize)
            }
            Spacer(Modifier.height(AsterSpacing.md))
        }
    }
}

@Composable
private fun overflow_sheet_row(
    action_id: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    on_click: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.xl, vertical = 14.dp)
            .testTag("sel_overflow_$action_id"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Text(
            text = label,
            color = tint,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

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
    swipe_start_icon: androidx.compose.ui.graphics.vector.ImageVector = TablerIcons.Archive,
    swipe_end_icon: androidx.compose.ui.graphics.vector.ImageVector = TablerIcons.Trash,
    swipe_start_color: Color = AsterMaterial.colors.accent_blue,
    swipe_end_color: Color = AsterMaterial.colors.danger,
    modifier: Modifier = Modifier,
    haptic_enabled: Boolean = true,
    is_first: Boolean = true,
    is_last: Boolean = true,
    user_prefs: org.astermail.android.api.preferences.UserPreferences? = null,
    list_scrolling: () -> Boolean = { false },
) {
    swipe_action_row(
        start_action = swipe_start_action,
        end_action = swipe_end_action,
        start_label = swipe_start_label,
        end_label = swipe_end_label,
        start_icon = swipe_start_icon,
        end_icon = swipe_end_icon,
        start_color = swipe_start_color,
        end_color = swipe_end_color,
        on_swipe_start = on_swipe_start,
        on_swipe_end = on_swipe_end,
        modifier = modifier,
        background_shape = inbox_group_shape(is_first, is_last),
        background_padding = PaddingValues(
            start = inbox_card_horizontal_margin,
            end = inbox_card_horizontal_margin,
            bottom = if (is_last) 0.dp else inbox_group_split,
        ),
        haptic_enabled = haptic_enabled,
        list_scrolling = list_scrolling,
    ) {
        ThreadInboxRow(
            thread = thread,
            on_click = on_click,
            on_long_click = on_long_click,
            on_toggle_star = on_toggle_star,
            is_pinned = is_pinned,
            haptic_enabled = haptic_enabled,
            is_first = is_first,
            is_last = is_last,
            user_prefs = user_prefs,
        )
    }
}

private fun swipe_action_color(action: String, colors: org.astermail.android.design.AsterSemanticColors): Color = when (action) {
    "archive", "move_to_inbox", "unarchive", "restore_trash", "unmark_spam" -> colors.accent_blue
    "delete", "trash", "delete_permanent", "spam" -> colors.danger
    "toggle_read" -> colors.success
    "snooze" -> colors.warning
    "star" -> colors.warning
    else -> colors.accent_blue
}

@Composable
private fun swipe_action_label(action: String): String = when (action) {
    "archive" -> stringResource(R.string.swipe_archive)
    "delete", "trash" -> stringResource(R.string.swipe_delete)
    "toggle_read" -> stringResource(R.string.swipe_read)
    "snooze" -> stringResource(R.string.snooze)
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
    "archive" -> TablerIcons.Archive
    "delete", "trash" -> TablerIcons.Trash
    "toggle_read" -> TablerIcons.MailOpened
    "snooze" -> TablerIcons.Clock
    "star" -> TablerIcons.Star
    "spam" -> TablerIcons.Ban
    "move_to_inbox" -> TablerIcons.Inbox
    "unarchive" -> TablerIcons.Inbox
    "restore_trash" -> TablerIcons.Inbox
    "unmark_spam" -> TablerIcons.Inbox
    "delete_permanent" -> TablerIcons.Trash
    else -> TablerIcons.Archive
}

private fun execute_swipe_action(
    action: String,
    ids: List<String>,
    mail_vm: MailViewModel,
    emails: MutableList<Email>,
    thread_id: String,
    current_folder: String,
    on_read_mutation: (List<String>) -> Unit = {},
    on_snooze: (List<String>) -> Unit = {},
) {
    when (action) {
        "archive" -> {
            if (current_folder == "archive") return
            mail_vm.archive(ids, 1)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "delete", "trash" -> {
            if (current_folder == "trash") return
            mail_vm.trash(ids, 1)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "toggle_read" -> {
            val was_read = emails.filter { (it.thread_id == thread_id || it.id == thread_id) }.all { it.is_read }
            if (was_read) {
                ids.forEach { mail_vm.mark_unread(it) }
            } else {
                mail_vm.mark_read_bulk(ids)
            }
            val mutated = ArrayList<String>()
            for (i in emails.indices) {
                if ((emails[i].thread_id == thread_id || emails[i].id == thread_id)) {
                    mutated.add(emails[i].id)
                    emails[i] = emails[i].copy(is_read = !was_read)
                }
            }
            on_read_mutation(mutated)
        }
        "snooze" -> on_snooze(ids)
        "star" -> {
            ids.forEach { mail_vm.toggle_star(it) }
            for (i in emails.indices) {
                if ((emails[i].thread_id == thread_id || emails[i].id == thread_id)) {
                    emails[i] = emails[i].copy(is_starred = !emails[i].is_starred)
                }
            }
        }
        "spam" -> {
            if (current_folder == "spam") return
            mail_vm.mark_spam(ids, 1)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "move_to_inbox" -> {
            if (current_folder == "inbox") return
            mail_vm.unarchive(ids)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "unarchive" -> {
            mail_vm.unarchive(ids)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "restore_trash" -> {
            mail_vm.restore_trash(ids)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "unmark_spam" -> {
            mail_vm.unmark_spam(ids)
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
        }
        "delete_permanent" -> {
            ids.forEach { mail_vm.delete_permanent(it) }
            emails.removeAll { (it.thread_id == thread_id || it.id == thread_id) }
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
private fun spam_retention_banner(days: Int) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AsterSpacing.md, vertical = AsterSpacing.xs)
            .clip(SquircleShape(12.dp))
            .background(colors.bg_card)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = TablerIcons.InfoCircle,
            contentDescription = null,
            tint = colors.text_muted,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.spam_auto_delete_notice, days),
            color = colors.text_muted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun empty_category_state(category_label: String? = null) {
    val colors = AsterMaterial.colors
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
                imageVector = TablerIcons.Inbox,
                contentDescription = null,
                tint = colors.text_muted,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = if (category_label != null) {
                    stringResource(R.string.nothing_in_category, category_label)
                } else {
                    stringResource(R.string.nothing_in_this_tab)
                },
                style = MaterialTheme.typography.titleMedium,
                color = colors.text_primary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (category_label != null) {
                    stringResource(R.string.other_categories_have_mail)
                } else {
                    stringResource(R.string.other_tabs_have_mail)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.text_muted,
            )
        }
    }
}

@Composable
private fun empty_inbox_state(folder: String = "inbox") {
    val colors = AsterMaterial.colors
    val icon = remember(folder) {
        when (folder) {
            "inbox" -> TablerIcons.Inbox
            "sent" -> TablerIcons.Send
            "drafts" -> TablerIcons.Edit
            "starred" -> TablerIcons.Star
            "trash" -> TablerIcons.Trash
            "spam" -> TablerIcons.Ban
            "archive" -> TablerIcons.Archive
            "scheduled" -> TablerIcons.Clock
            "snoozed" -> TablerIcons.BellOff
            else -> TablerIcons.Inbox
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


@Composable
private fun compose_fab(expanded: Boolean, on_click: () -> Unit) {
    val colors = AsterMaterial.colors
    val density = LocalDensity.current
    val progress = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 160,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
    }
    val collapsed_px = with(density) { 56.dp.roundToPx() }
    Surface(
        onClick = on_click,
        shape = SquircleShape(20.dp),
        color = colors.accent_blue,
        contentColor = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .navigationBarsPadding()
            .padding(AsterSpacing.lg)
            .height(56.dp)
            .layout { measurable, constraints ->
                val height = constraints.maxHeight
                val expanded_px = measurable
                    .maxIntrinsicWidth(height)
                    .coerceAtLeast(collapsed_px)
                    .coerceAtMost(constraints.maxWidth)
                val width = (collapsed_px + (expanded_px - collapsed_px) * progress.value).roundToInt()
                val placeable = measurable.measure(
                    androidx.compose.ui.unit.Constraints.fixed(width, height),
                )
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
            .testTag("compose"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .wrapContentSize(align = Alignment.CenterStart, unbounded = true)
                .padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = TablerIcons.Edit,
                contentDescription = stringResource(R.string.compose),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.compose),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.graphicsLayer {
                    alpha = (progress.value * 1.6f - 0.6f).coerceIn(0f, 1f)
                },
            )
        }
    }
}

fun emails_fingerprint_of(emails: List<Email>): Int {
    var hash = emails.size
    emails.forEach { e ->
        hash = 31 * hash + e.id.hashCode()
        hash = 31 * hash + (if (e.is_starred) 1 else 0)
        hash = 31 * hash + (if (e.is_read) 2 else 0)
        hash = 31 * hash + (if (e.is_pinned) 4 else 0)
        hash = 31 * hash + e.label_names.hashCode()
        hash = 31 * hash + e.label_colors.hashCode()
        hash = 31 * hash + e.label_icons.hashCode()
    }
    return hash
}
