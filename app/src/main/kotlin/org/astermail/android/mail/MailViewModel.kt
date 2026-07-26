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

package org.astermail.android.mail

import org.astermail.android.BuildConfig
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.notifications.MailPollingWorker
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.ui.mail.MessageAttachment

private const val INBOX_FETCH_BACKSTOP_MS = 18_000L
private const val BULK_ACTION_CONCURRENCY = 6
private const val CARRIED_ITEM_STALE_MS = 20 * 60 * 1000L
private const val STATS_TTL_MS = 30_000L

data class BatchActionState(
    val action_key: String,
    val count: Int,
    val message: String,
    val undo_label: String,
    val on_undo: () -> Unit,
    val started_at_ms: Long,
)

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val is_loading: Boolean = false,
    val initial: Boolean = true,
    val is_loading_more: Boolean = false,
    val error: String? = null,
    val has_more: Boolean = false,
    val next_cursor: String? = null,
    val total: Int = 0,
    val current_folder: String = "inbox",
    val stats: MailUserStatsResponse? = null,
    val is_refreshing: Boolean = false,
)

data class ThreadUiState(
    val messages: List<ThreadMessageDecrypted> = emptyList(),
    val is_loading: Boolean = false,
    val error: String? = null,
    val item: InboxItem? = null,
    val attachments: Map<String, List<MessageAttachment>> = emptyMap(),
)

data class SearchUiState(
    val all_items: List<InboxItem> = emptyList(),
    val is_indexing: Boolean = false,
    val is_indexed: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: MailRepository,
    private val search_index_manager: SearchIndexManager,
) : ViewModel() {

    private val _inbox_state = MutableStateFlow(InboxUiState())
    val inbox_state: StateFlow<InboxUiState> = _inbox_state.asStateFlow()

    private val _thread_state = MutableStateFlow(ThreadUiState())
    val thread_state: StateFlow<ThreadUiState> = _thread_state.asStateFlow()

    val visible_order: StateFlow<List<String>> = repository.visible_order

    fun set_visible_order(ids: List<String>) {
        repository.set_visible_order(ids)
    }

    private val _search_state = MutableStateFlow(SearchUiState())
    val search_state: StateFlow<SearchUiState> = _search_state.asStateFlow()

    data class InboxAttachmentChip(
        val filename: String,
        val content_type: String,
    )

    private val _inbox_attachment_chips = MutableStateFlow<Map<String, List<InboxAttachmentChip>>>(emptyMap())
    val inbox_attachment_chips: StateFlow<Map<String, List<InboxAttachmentChip>>> = _inbox_attachment_chips.asStateFlow()

    private val _thread_participants = MutableStateFlow<Map<String, List<Pair<String, String>>>>(emptyMap())
    val thread_participants: StateFlow<Map<String, List<Pair<String, String>>>> = _thread_participants.asStateFlow()

    private fun cache_thread_participants(thread_token: String?, messages: List<ThreadMessageDecrypted>) {
        if (thread_token.isNullOrBlank() || messages.size < 2) return
        val ordered = messages.sortedByDescending { it.timestamp }
        val seen = mutableSetOf<String>()
        val participants = mutableListOf<Pair<String, String>>()
        for (m in ordered) {
            val shown_name = m.display_sender_name ?: m.sender_name
            val shown_email = m.display_sender_email ?: m.sender_email
            val key = shown_email.lowercase().ifBlank { shown_name.lowercase() }
            if (key.isBlank()) continue
            if (seen.add(key)) participants.add(shown_name to shown_email)
        }
        if (participants.size < 2) return
        _thread_participants.update { it + (thread_token to participants) }
    }

    private val folder_cache = java.util.concurrent.ConcurrentHashMap<String, InboxUiState>()
    private val folder_cache_time = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val item_last_confirmed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val pending_removed_ids = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private var inbox_load_job: Job? = null
    private var last_stats_load_ms = 0L
    private var silent_revalidate_job: Job? = null
    private var refresh_job: Job? = null
    private var account_generation = 0
    private val star_overrides = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val pin_overrides = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val read_overrides = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val mark_read_jobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    data class ToastEvent(
        val message: String,
        val undo_label: String? = null,
        val on_undo: (() -> Unit)? = null,
        val duration_ms: Long? = null,
        val on_timeout: (() -> Unit)? = null,
    )

    private val _toast_events = MutableSharedFlow<ToastEvent>(extraBufferCapacity = 32)
    val toast_events: SharedFlow<ToastEvent> = _toast_events.asSharedFlow()

    private val _batch_action_state = kotlinx.coroutines.flow.MutableStateFlow<BatchActionState?>(null)
    val batch_action_state: kotlinx.coroutines.flow.StateFlow<BatchActionState?> = _batch_action_state.asStateFlow()

    fun clear_batch_action(key: String) {
        if (_batch_action_state.value?.action_key == key) _batch_action_state.value = null
    }

    private fun emit_toast(msg: String) {
        _toast_events.tryEmit(ToastEvent(msg))
    }

    private fun emit_toast_undo(msg: String, undo_label: String, on_undo: () -> Unit) {
        _toast_events.tryEmit(ToastEvent(msg, undo_label, on_undo))
    }

    private fun accumulate_batch_action(
        action_key: String,
        thread_count: Int,
        message_fn: (Int) -> String,
        undo_label: String,
        build_undo: (prev_undo: (() -> Unit)?) -> () -> Unit,
    ) {
        val now = System.currentTimeMillis()
        val existing = _batch_action_state.value
        val (new_count, combined_undo, started_ms) = if (
            existing != null && existing.action_key == action_key && (now - existing.started_at_ms) < 4500L
        ) {
            Triple(existing.count + thread_count, build_undo(existing.on_undo), existing.started_at_ms)
        } else {
            Triple(thread_count, build_undo(null), now)
        }
        _batch_action_state.value = BatchActionState(
            action_key = action_key,
            count = new_count,
            message = message_fn(new_count),
            undo_label = undo_label,
            on_undo = combined_undo,
            started_at_ms = started_ms,
        )
    }

    fun reset_for_account_switch() {
        account_generation++
        inbox_load_job?.cancel()
        silent_revalidate_job?.cancel()
        refresh_job?.cancel()
        folder_cache.clear()
        folder_cache_time.clear()
        item_last_confirmed.clear()
        pending_removed_ids.clear()
        last_stats_load_ms = 0L
        star_overrides.clear()
        pin_overrides.clear()
        read_overrides.clear()
        _inbox_state.value = InboxUiState()
        _thread_state.value = ThreadUiState()
        _search_state.value = SearchUiState()
        _inbox_attachment_chips.value = emptyMap()
        _thread_participants.value = emptyMap()
        repository.clear_caches()
        runCatching { AsterProfileResolverHolder.shared?.clear() }
        kotlinx.coroutines.runBlocking {
            runCatching { search_index_manager.clear() }
        }
    }

    private fun apply_star_overrides(items: List<InboxItem>): List<InboxItem> {
        if (star_overrides.isEmpty()) return items
        return items.map { item ->
            val override = star_overrides[item.id]
            if (override != null && override == item.is_starred) {
                star_overrides.remove(item.id)
                item
            } else if (override != null) {
                item.copy(is_starred = override)
            } else {
                item
            }
        }
    }

    private fun apply_read_overrides(items: List<InboxItem>): List<InboxItem> {
        if (read_overrides.isEmpty()) return items
        return items.map { item ->
            val override = read_overrides[item.id]
            if (override != null && override == item.is_read) {
                read_overrides.remove(item.id)
                item
            } else if (override != null) {
                item.copy(is_read = override)
            } else {
                item
            }
        }
    }

    private fun apply_pin_overrides(items: List<InboxItem>): List<InboxItem> {
        if (pin_overrides.isEmpty()) return items
        return items.map { item ->
            val override = pin_overrides[item.id] ?: return@map item
            val current_pin = item.raw_item.metadata?.is_pinned ?: false
            if (override == current_pin) {
                pin_overrides.remove(item.id)
                item
            } else {
                val meta = (item.raw_item.metadata
                    ?: org.astermail.android.api.mail.MailItemMetadata()).copy(is_pinned = override)
                item.copy(raw_item = item.raw_item.copy(metadata = meta))
            }
        }
    }

    private fun merge_with_previous(
        page_items: List<InboxItem>,
        previous_items: List<InboxItem>,
        folder: String,
        total: Int?,
    ): List<InboxItem> {
        val now = System.currentTimeMillis()
        page_items.forEach { item_last_confirmed[it.id] = now }
        val live_items = if (pending_removed_ids.isEmpty()) {
            page_items
        } else {
            page_items.filter { it.id !in pending_removed_ids }
        }
        if (previous_items.isEmpty()) return live_items
        if (total != null && total <= live_items.size) return live_items
        val page_ids = live_items.map { it.id }.toHashSet()
        val cap = (total ?: previous_items.size).coerceAtLeast(live_items.size)
        val min_page_timestamp = live_items.minOfOrNull { it.timestamp }
        val carried = previous_items.asSequence()
            .filter { it.id !in page_ids }
            .filter { it.id !in pending_removed_ids }
            .filter { min_page_timestamp == null || it.timestamp < min_page_timestamp }
            .filter { folder_matches(folder, it) }
            .filter { now - (item_last_confirmed[it.id] ?: 0L) <= CARRIED_ITEM_STALE_MS }
            .toList()
        val combined = live_items + carried
        return if (combined.size > cap) combined.take(cap) else combined
    }

    private val demo_dismissed_prefs by lazy {
        context.getSharedPreferences("aster_demo_phish", Context.MODE_PRIVATE)
    }

    private fun demo_dismissed(): Boolean =
        demo_dismissed_prefs.getBoolean("dismissed", false)

    private fun dismiss_demo() {
        demo_dismissed_prefs.edit().putBoolean("dismissed", true).apply()
    }

    private fun apply_demo_overlay(items: List<InboxItem>, folder: String): List<InboxItem> {
        return items.filter { it.id != DEMO_PHISH_ITEM_ID }
    }

    private fun handle_demo_in(item_ids: List<String>): List<String> {
        if (DEMO_PHISH_ITEM_ID !in item_ids) return item_ids
        dismiss_demo()
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.filter { it.id != DEMO_PHISH_ITEM_ID },
        )
        folder_cache.keys.toList().forEach { k ->
            val s = folder_cache[k] ?: return@forEach
            folder_cache[k] = s.copy(items = s.items.filter { it.id != DEMO_PHISH_ITEM_ID })
        }
        return item_ids.filter { it != DEMO_PHISH_ITEM_ID }
    }

    fun load_inbox(folder: String = "inbox", force: Boolean = false) {
        val current = _inbox_state.value
        if (current.current_folder != folder && folder_cache_time.containsKey(current.current_folder)) {
            folder_cache[current.current_folder] = current
        }
        if (!force && current.items.isNotEmpty() && current.current_folder == folder) return
        if (force && current.items.isNotEmpty() && current.current_folder == folder && !folder_cache.containsKey(folder)) {
            folder_cache[folder] = current
        }
        val cached = folder_cache[folder]
        if (cached != null && cached.items.isNotEmpty()) {
            inbox_load_job?.cancel()
            silent_revalidate_job?.cancel()
            val warm = cached.copy(
                items = apply_demo_overlay(
                    apply_pin_overrides(apply_star_overrides(apply_read_overrides(cached.items))),
                    folder,
                ),
                is_loading = false,
                initial = false,
                error = null,
                current_folder = folder,
                stats = current.stats ?: cached.stats,
            )
            _inbox_state.value = warm
            val age = System.currentTimeMillis() - (folder_cache_time[folder] ?: 0L)
            if (force || age > 30_000L) {
                silent_revalidate(folder)
            }
            return
        }
        inbox_load_job?.cancel()
        silent_revalidate_job?.cancel()
        _inbox_state.value = InboxUiState(
            is_loading = true,
            initial = true,
            current_folder = folder,
            stats = current.stats,
        )
        inbox_load_job = viewModelScope.launch {
            if (_inbox_state.value.items.isEmpty()) {
                val persisted = runCatching { search_index_manager.get_cached_items() }.getOrNull().orEmpty()
                if (persisted.isNotEmpty() && _inbox_state.value.current_folder == folder && folder == "inbox") {
                    val safe = persisted.filter { !it.is_trashed && !it.is_archived && !it.is_spam }.take(20)
                    if (safe.isNotEmpty()) {
                        val items = safe.map { it.to_inbox_item() }
                            .filter { folder_matches(folder, it) }
                        if (items.isNotEmpty()) {
                            _inbox_state.value = _inbox_state.value.copy(
                                items = apply_demo_overlay(apply_pin_overrides(apply_star_overrides(apply_read_overrides(items))), folder),
                                initial = false,
                            )
                        }
                    }
                }
            }
            var result = runCatching {
                kotlinx.coroutines.withTimeout(INBOX_FETCH_BACKSTOP_MS) {
                    fetch_for_folder(folder).getOrThrow()
                }
            }
            if (result.isFailure &&
                _inbox_state.value.current_folder == folder &&
                !is_timeout_failure(result.exceptionOrNull())
            ) {
                kotlinx.coroutines.delay(500L)
                result = runCatching {
                    kotlinx.coroutines.withTimeout(INBOX_FETCH_BACKSTOP_MS) {
                        fetch_for_folder(folder).getOrThrow()
                    }
                }
            }
            if (_inbox_state.value.current_folder != folder) {
                return@launch
            }
            result.fold(
                onSuccess = { page ->
                    if (BuildConfig.DEBUG && (folder.startsWith("label:") || folder.startsWith("tag:"))) {
                        android.util.Log.d(
                            "MailVM",
                            "label_load folder=$folder api_items=${page.items.size} archived_in_api=${page.items.count { it.is_archived }} total=${page.total}",
                        )
                    }
                    val previous = _inbox_state.value.items
                    val combined = merge_with_previous(page.items, previous, folder, page.total)
                    val merged_items = apply_demo_overlay(
                        apply_pin_overrides(apply_star_overrides(apply_read_overrides(combined))),
                        folder,
                    )
                    _inbox_state.value = _inbox_state.value.copy(
                        items = merged_items,
                        is_loading = false,
                        initial = false,
                        has_more = page.has_more,
                        next_cursor = page.next_cursor,
                        total = page.total,
                    )
                    folder_cache[folder] = _inbox_state.value
                    folder_cache_time[folder] = System.currentTimeMillis()
                    search_index_manager.on_items_loaded(page.items)
                    reconcile_cache_window(folder, page)
                    search_index_manager.ensure_index_built()
                },
                onFailure = { t ->
                    val keep_items = _inbox_state.value.items.isNotEmpty()
                    _inbox_state.value = _inbox_state.value.copy(
                        is_loading = false,
                        initial = false,
                        error = if (keep_items) null else friendly_load_error(t),
                    )
                },
            )
        }
    }

    internal var foreground_check: () -> Boolean = {
        runCatching {
            androidx.lifecycle.ProcessLifecycleOwner.get()
                .lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }.getOrDefault(true)
    }

    fun foreground_fallback_tick() {
        val has_push = runCatching {
            org.astermail.android.notifications.UnifiedPushState.endpoint(context) != null
        }.getOrDefault(false)
        if (foreground_check() && !has_push) {
            silent_revalidate(_inbox_state.value.current_folder)
        }
    }

    private fun silent_revalidate(folder: String) {
        silent_revalidate_job?.cancel()
        silent_revalidate_job = viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(INBOX_FETCH_BACKSTOP_MS) {
                    fetch_for_folder(folder).getOrThrow()
                }
            }
            if (_inbox_state.value.current_folder != folder) return@launch
            result.onSuccess { page ->
                val previous = _inbox_state.value.items
                val combined = merge_with_previous(page.items, previous, folder, page.total)
                val merged_items = apply_demo_overlay(
                    apply_pin_overrides(apply_star_overrides(apply_read_overrides(combined))),
                    folder,
                )
                _inbox_state.value = _inbox_state.value.copy(
                    items = merged_items,
                    is_loading = false,
                    initial = false,
                    error = null,
                    has_more = page.has_more,
                    next_cursor = page.next_cursor,
                    total = page.total,
                )
                folder_cache[folder] = _inbox_state.value
                folder_cache_time[folder] = System.currentTimeMillis()
                search_index_manager.on_items_loaded(page.items)
                reconcile_cache_window(folder, page)
                search_index_manager.ensure_index_built()
            }
        }
    }

    private suspend fun reconcile_cache_window(folder: String, page: InboxPage) {
        if (folder != "inbox" || page.items.isEmpty()) return
        val min_timestamp = page.items.minOf { it.timestamp }
        val returned_ids = page.items.map { it.id }.toHashSet()
        runCatching { search_index_manager.reconcile_inbox_window(returned_ids, min_timestamp) }
    }

    fun load_more() {
        val state = _inbox_state.value
        if (state.is_loading || state.is_loading_more || !state.has_more) return
        var cursor = state.next_cursor ?: return
        val started_folder = state.current_folder
        _inbox_state.value = state.copy(is_loading_more = true)
        viewModelScope.launch {
            var pages_scanned = 0
            while (true) {
                val page = try {
                    fetch_for_folder(started_folder, cursor).getOrNull()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (_inbox_state.value.current_folder != started_folder) {
                    _inbox_state.value = _inbox_state.value.copy(is_loading_more = false)
                    return@launch
                }
                if (page == null) {
                    if (pages_scanned > 0) {
                        _inbox_state.update { it.copy(is_loading_more = false, next_cursor = cursor) }
                    } else {
                        _inbox_state.update { it.copy(is_loading_more = false) }
                        emit_toast(context.getString(R.string.failed_to_load))
                    }
                    return@launch
                }
                val confirmed_at = System.currentTimeMillis()
                page.items.forEach { item_last_confirmed[it.id] = confirmed_at }
                val existing = _inbox_state.value.items
                val existing_ids = existing.map { it.id }.toHashSet()
                val new_items = page.items.filter { it.id !in existing_ids }
                val cursor_advanced = page.next_cursor != null && page.next_cursor != cursor
                pages_scanned++
                if (new_items.isEmpty() && page.has_more && cursor_advanced && pages_scanned < 20) {
                    cursor = page.next_cursor!!
                    continue
                }
                val combined = apply_pin_overrides(
                    apply_star_overrides(apply_read_overrides(existing + new_items)),
                )
                val effective_has_more = page.has_more && cursor_advanced
                _inbox_state.value = _inbox_state.value.copy(
                    items = combined,
                    is_loading_more = false,
                    has_more = effective_has_more,
                    next_cursor = if (effective_has_more) page.next_cursor else null,
                )
                folder_cache[started_folder] = _inbox_state.value
                folder_cache_time[started_folder] = System.currentTimeMillis()
                search_index_manager.on_items_loaded(page.items)
                return@launch
            }
        }
    }

    fun load_all_remaining(on_complete: (() -> Unit)? = null) {
        viewModelScope.launch {
            val started_folder = _inbox_state.value.current_folder
            var guard = 0
            while (guard < 200) {
                val s = _inbox_state.value
                if (s.current_folder != started_folder) return@launch
                if (!s.has_more) break
                if (s.is_loading || s.is_loading_more) {
                    kotlinx.coroutines.delay(50)
                    continue
                }
                val before_cursor = s.next_cursor
                val before_count = s.items.size
                load_more()
                var waited = 0
                while (_inbox_state.value.is_loading_more && waited < 400) {
                    kotlinx.coroutines.delay(25)
                    waited++
                }
                val after = _inbox_state.value
                if (after.next_cursor == before_cursor && after.items.size == before_count) break
                guard++
            }
            if (_inbox_state.value.current_folder == started_folder) on_complete?.invoke()
        }
    }

    fun get_user_email(): String? = repository.get_user_email()

    fun load_stats(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && _inbox_state.value.stats != null && now - last_stats_load_ms < STATS_TTL_MS) return
        last_stats_load_ms = now
        viewModelScope.launch {
            repository.get_stats().onSuccess { stats ->
                _inbox_state.update { it.copy(stats = stats) }
            }
        }
    }

    fun load_draft(draft_id: String) {
        _thread_state.value = ThreadUiState(is_loading = true)
        viewModelScope.launch(Dispatchers.IO) {
            repository.fetch_draft_for_compose(draft_id).fold(
                onSuccess = { (item, envelope) ->
                    val addresses = envelope?.let {
                        Pair(
                            it.to.map { a -> a.second }.filter { a -> a.isNotBlank() },
                            it.cc.map { a -> a.second }.filter { a -> a.isNotBlank() },
                        )
                    }
                    val msg = ThreadMessageDecrypted(
                        id = item.id,
                        sender_name = envelope?.from_name ?: item.sender_name,
                        sender_email = envelope?.from_email ?: item.sender_email,
                        to_label = "",
                        timestamp = item.timestamp,
                        body_text = envelope?.body_text ?: item.preview,
                        body_html = envelope?.body_html,
                        is_encrypted = true,
                        is_read = true,
                        raw_item = org.astermail.android.api.mail.ThreadMessageItem(
                            id = item.id,
                            item_type = "draft",
                        ),
                        to_addresses = addresses?.first ?: emptyList(),
                        cc_addresses = addresses?.second ?: emptyList(),
                    )
                    _thread_state.value = ThreadUiState(
                        messages = listOf(msg),
                        item = item,
                    )
                },
                onFailure = { t ->
                    _thread_state.value = ThreadUiState(
                        error = t.message ?: context.getString(R.string.something_went_wrong),
                    )
                },
            )
        }
    }

    fun refresh_current_thread() {
        _thread_state.value.item?.id?.let { load_thread(it) }
    }

    fun load_thread(item_id: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) {
            val demo_item = build_demo_phishing_inbox_item()
            val demo_msg = build_demo_phishing_thread_message()
            _thread_state.value = ThreadUiState(
                messages = listOf(demo_msg),
                item = demo_item,
            )
            return
        }
        val cur_thread = _thread_state.value
        _thread_state.value = if (cur_thread.item?.id == item_id && cur_thread.messages.isNotEmpty()) {
            cur_thread.copy(is_loading = true, error = null)
        } else {
            val seed = _inbox_state.value.items.find { it.id == item_id }
                ?: folder_cache.values.firstNotNullOfOrNull { cached ->
                    cached.items.find { it.id == item_id }
                }
            if (seed != null) {
                ThreadUiState(
                    is_loading = true,
                    item = seed,
                    messages = listOf(seed_message_from_inbox_item(seed)),
                )
            } else {
                ThreadUiState(is_loading = true)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val item_result = withTimeoutOrNull(15_000) {
                repository.fetch_single_message(item_id)
            } ?: Result.failure(Exception(context.getString(R.string.something_went_wrong)))
            val item = item_result.getOrNull()
            val thread_token = item?.thread_token
            if (thread_token != null) {
                val fallback = listOf(single_message_from_item(item))
                val result = withTimeoutOrNull(15_000) {
                    repository.fetch_thread(thread_token)
                } ?: Result.failure(Exception(context.getString(R.string.something_went_wrong)))
                result.fold(
                    onSuccess = { messages ->
                        val resolved = if (messages.isEmpty()) fallback else messages
                        _thread_state.value = ThreadUiState(
                            messages = resolved,
                            item = item,
                        )
                        cache_thread_participants(thread_token, resolved)
                        load_attachments_for_thread(resolved)
                    },
                    onFailure = { t ->
                        _thread_state.value = ThreadUiState(
                            messages = fallback,
                            error = t.message ?: context.getString(R.string.something_went_wrong),
                            item = item,
                        )
                        cache_thread_participants(thread_token, fallback)
                        load_attachments_for_thread(fallback)
                    },
                )
            } else if (item != null) {
                val msgs = listOf(single_message_from_item(item))
                _thread_state.value = ThreadUiState(
                    messages = msgs,
                    item = item,
                )
                load_attachments_for_thread(msgs)
            } else {
                val keep = _thread_state.value
                _thread_state.value = if (keep.item?.id == item_id && keep.messages.isNotEmpty()) {
                    keep.copy(is_loading = false, error = null)
                } else {
                    ThreadUiState(
                        error = item_result.exceptionOrNull()?.message ?: context.getString(R.string.something_went_wrong),
                    )
                }
            }
        }
    }

    private suspend fun single_message_from_item(item: InboxItem): ThreadMessageDecrypted {
        val raw = item.raw_item
        val thread_item = org.astermail.android.api.mail.ThreadMessageItem(
            id = raw.id,
            item_type = raw.item_type,
            encrypted_envelope = raw.encrypted_envelope,
            envelope_nonce = raw.envelope_nonce,
            message_ts = raw.message_ts,
            created_at = raw.created_at,
            metadata = raw.metadata,
        )
        return repository.decrypt_single_thread_message(thread_item)
    }

    private fun seed_message_from_inbox_item(item: InboxItem): ThreadMessageDecrypted {
        val raw = item.raw_item
        val thread_item = org.astermail.android.api.mail.ThreadMessageItem(
            id = raw.id,
            item_type = raw.item_type,
            encrypted_envelope = raw.encrypted_envelope,
            envelope_nonce = raw.envelope_nonce,
            message_ts = raw.message_ts,
            created_at = raw.created_at,
            metadata = raw.metadata,
        )
        return ThreadMessageDecrypted(
            id = item.id,
            sender_name = item.sender_name,
            sender_email = item.sender_email,
            to_label = "",
            timestamp = item.timestamp,
            body_text = item.preview,
            body_html = null,
            is_encrypted = item.is_encrypted,
            is_read = item.is_read,
            raw_item = thread_item,
            has_attachments = item.has_attachments,
            subject = item.subject,
            display_sender_name = item.display_sender_name,
            display_sender_email = item.display_sender_email,
        )
    }

    private fun load_attachments_for_thread(messages: List<ThreadMessageDecrypted>) {
        val ids = messages.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val ids_with_attachments = repository.find_messages_with_attachments(ids)
            if (ids_with_attachments.isEmpty()) return@launch
            val results = ids_with_attachments.map { id ->
                async { id to repository.fetch_attachments_for_message(id) }
            }.awaitAll().toMap().filter { it.value.isNotEmpty() }
            _thread_state.update { it.copy(attachments = it.attachments + results) }
        }
    }

    fun download_attachment(
        attachment: MessageAttachment,
        on_result: (Result<Pair<MessageAttachment, ByteArray>>) -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                if (!attachment.encrypted_data.isNullOrBlank() && !attachment.data_nonce.isNullOrBlank()) {
                    val bytes = repository.decrypt_attachment_data(
                        attachment.encrypted_data,
                        attachment.data_nonce,
                        attachment.session_key ?: "",
                    )
                    Pair(attachment, bytes)
                } else {
                    repository.download_attachment(attachment.id)
                        ?: throw IllegalStateException("failed to decrypt attachment")
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                on_result(result)
            }
        }
    }

    fun mark_read_delayed(item_id: String, delay_ms: Long) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        mark_read_jobs.remove(item_id)?.cancel()
        mark_read_jobs[item_id] = viewModelScope.launch {
            if (delay_ms > 0) kotlinx.coroutines.delay(delay_ms)
            mark_read(item_id)
            mark_read_jobs.remove(item_id)
        }
    }

    fun mark_read(item_id: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        MailPollingWorker.cancel_message_notification(context, item_id)
        val item = _inbox_state.value.items.find { it.id == item_id }
            ?: folder_cache.values.firstNotNullOfOrNull { c -> c.items.find { it.id == item_id } }
        read_overrides[item_id] = true
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) it.copy(is_read = true) else it
            },
        )
        folder_cache.replaceAll { _, cached ->
            cached.copy(items = cached.items.map {
                if (it.id == item_id) it.copy(is_read = true) else it
            })
        }
        invalidate_caches(listOf("starred"))
        viewModelScope.launch {
            runCatching { search_index_manager.update_read(item_id, true) }
            var result = repository.mark_read(item_id, true, item?.raw_item)
            if (result.isFailure) {
                kotlinx.coroutines.delay(1500L)
                result = repository.mark_read(item_id, true, item?.raw_item)
            }
            if (result.isFailure) {
                revert_read_state(item_id, item?.is_read ?: false)
            }
        }
    }

    fun mark_unread(item_id: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val item = _inbox_state.value.items.find { it.id == item_id }
            ?: folder_cache.values.firstNotNullOfOrNull { c -> c.items.find { it.id == item_id } }
        read_overrides[item_id] = false
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) it.copy(is_read = false) else it
            },
        )
        folder_cache.replaceAll { _, cached ->
            cached.copy(items = cached.items.map {
                if (it.id == item_id) it.copy(is_read = false) else it
            })
        }
        invalidate_caches(listOf("starred"))
        viewModelScope.launch {
            runCatching { search_index_manager.update_read(item_id, false) }
            var result = repository.mark_read(item_id, false, item?.raw_item)
            if (result.isFailure) {
                kotlinx.coroutines.delay(1500L)
                result = repository.mark_read(item_id, false, item?.raw_item)
            }
            if (result.isFailure) {
                revert_read_state(item_id, item?.is_read ?: true)
            }
        }
    }

    private suspend fun revert_read_state(item_id: String, previous_is_read: Boolean) {
        read_overrides.remove(item_id)
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) it.copy(is_read = previous_is_read) else it
            },
        )
        folder_cache.replaceAll { _, cached ->
            cached.copy(items = cached.items.map {
                if (it.id == item_id) it.copy(is_read = previous_is_read) else it
            })
        }
        val thread = _thread_state.value
        if (thread.item != null && thread.item.id == item_id) {
            _thread_state.value = thread.copy(item = thread.item.copy(is_read = previous_is_read))
        }
        runCatching { search_index_manager.update_read(item_id, previous_is_read) }
        emit_toast(context.getString(R.string.failed_mark_read))
    }

    fun toggle_star(item_id: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val current = _inbox_state.value.items.find { it.id == item_id }
            ?: _thread_state.value.item?.takeIf { it.id == item_id }
            ?: folder_cache.values.firstNotNullOfOrNull { cached ->
                cached.items.find { it.id == item_id }
            }
            ?: return
        val new_starred = !current.is_starred
        star_overrides[item_id] = new_starred
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) it.copy(is_starred = new_starred) else it
            },
        )
        val current_folder = _inbox_state.value.current_folder
        val updated_cache = folder_cache.mapValues { (folder, cached) ->
            if (folder == current_folder) {
                cached
            } else if (folder == "starred") {
                if (new_starred) {
                    if (cached.items.any { it.id == item_id }) {
                        cached.copy(items = cached.items.map {
                            if (it.id == item_id) it.copy(is_starred = true) else it
                        })
                    } else {
                        cached
                    }
                } else {
                    cached.copy(items = cached.items.filter { it.id != item_id })
                }
            } else {
                cached.copy(items = cached.items.map {
                    if (it.id == item_id) it.copy(is_starred = new_starred) else it
                })
            }
        }
        folder_cache.putAll(updated_cache)
        val thread = _thread_state.value
        if (thread.item?.id == item_id) {
            _thread_state.value = thread.copy(item = thread.item.copy(is_starred = new_starred))
        }
        viewModelScope.launch {
            repository.toggle_star(item_id, new_starred, current.raw_item)
        }
    }

    fun snooze_until(item_id: String, snoozed_until_iso: String, label: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) {
            handle_demo_in(listOf(item_id))
            emit_toast(context.getString(R.string.snoozed_until, label))
            return
        }
        viewModelScope.launch {
            repository.snooze(item_id, snoozed_until_iso).fold(
                onSuccess = {
                    _inbox_state.value = _inbox_state.value.copy(
                        items = _inbox_state.value.items.filter { it.id != item_id },
                    )
                    invalidate_caches(listOf("inbox", "snoozed"))
                    emit_toast(context.getString(R.string.snoozed_until, label))
                },
                onFailure = { t ->
                    emit_toast(t.message ?: context.getString(R.string.couldnt_snooze))
                },
            )
        }
    }

    fun toggle_pin(item_id: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val current = _inbox_state.value.items.find { it.id == item_id }
            ?: _thread_state.value.item?.takeIf { it.id == item_id }
            ?: folder_cache.values.firstNotNullOfOrNull { cached ->
                cached.items.find { it.id == item_id }
            }
            ?: return
        val raw_meta = current.raw_item.metadata
        val new_pinned = !(raw_meta?.is_pinned ?: false)
        pin_overrides[item_id] = new_pinned
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) {
                    val updated_meta = (it.raw_item.metadata
                        ?: org.astermail.android.api.mail.MailItemMetadata()).copy(is_pinned = new_pinned)
                    it.copy(raw_item = it.raw_item.copy(metadata = updated_meta))
                } else it
            },
        )
        val thread = _thread_state.value
        if (thread.item?.id == item_id) {
            val updated_meta = (thread.item.raw_item.metadata
                ?: org.astermail.android.api.mail.MailItemMetadata()).copy(is_pinned = new_pinned)
            _thread_state.value = thread.copy(
                item = thread.item.copy(raw_item = thread.item.raw_item.copy(metadata = updated_meta)),
            )
        }
        viewModelScope.launch {
            repository.toggle_pin(item_id, new_pinned, current.raw_item).fold(
                onSuccess = {
                    emit_toast(context.getString(if (new_pinned) R.string.pinned else R.string.unpinned))
                },
                onFailure = {
                    emit_toast(context.getString(if (new_pinned) R.string.pin_failed else R.string.unpin_failed))
                },
            )
        }
    }

    fun apply_label(item_id: String, label_token: String, display_name: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val prev_inbox_labels = _inbox_state.value.items.find { it.id == item_id }?.labels
        val prev_thread_labels = _thread_state.value.item?.takeIf { it.id == item_id }?.labels
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) it.copy(labels = (it.labels + label_token).distinct()) else it
            },
        )
        val thread = _thread_state.value
        if (thread.item?.id == item_id) {
            _thread_state.value = thread.copy(
                item = thread.item.copy(labels = (thread.item.labels + label_token).distinct()),
            )
        }
        viewModelScope.launch {
            repository.add_label_to_item(item_id, label_token).fold(
                onSuccess = {
                    invalidate_caches(listOf("label:$label_token"))
                    emit_toast(context.getString(R.string.added_to_label, display_name))
                },
                onFailure = {
                    if (prev_inbox_labels != null) {
                        _inbox_state.value = _inbox_state.value.copy(
                            items = _inbox_state.value.items.map {
                                if (it.id == item_id) it.copy(labels = prev_inbox_labels) else it
                            },
                        )
                    }
                    val th = _thread_state.value
                    if (prev_thread_labels != null && th.item?.id == item_id) {
                        _thread_state.value = th.copy(item = th.item.copy(labels = prev_thread_labels))
                    }
                    emit_toast(it.message ?: context.getString(R.string.couldnt_apply_label))
                },
            )
        }
    }

    fun apply_tag(item_id: String, tag_token: String, display_name: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val prev_inbox_item = _inbox_state.value.items.find { it.id == item_id }
        val prev_thread_item = _thread_state.value.item?.takeIf { it.id == item_id }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) {
                    val new_tokens = (it.tag_tokens + tag_token).distinct()
                    it.copy(
                        tag_tokens = new_tokens,
                        raw_item = it.raw_item.copy(tag_tokens = new_tokens),
                    )
                } else it
            },
        )
        val thread = _thread_state.value
        if (thread.item?.id == item_id) {
            val new_tokens = (thread.item.tag_tokens + tag_token).distinct()
            _thread_state.value = thread.copy(
                item = thread.item.copy(
                    tag_tokens = new_tokens,
                    raw_item = thread.item.raw_item.copy(tag_tokens = new_tokens),
                ),
            )
        }
        viewModelScope.launch {
            repository.add_tag_to_item(item_id, tag_token).fold(
                onSuccess = { emit_toast(context.getString(R.string.added_to_label, display_name)) },
                onFailure = {
                    if (prev_inbox_item != null) {
                        _inbox_state.value = _inbox_state.value.copy(
                            items = _inbox_state.value.items.map {
                                if (it.id == item_id) prev_inbox_item else it
                            },
                        )
                    }
                    val th = _thread_state.value
                    if (prev_thread_item != null && th.item?.id == item_id) {
                        _thread_state.value = th.copy(item = prev_thread_item)
                    }
                    emit_toast(it.message ?: context.getString(R.string.couldnt_apply_label))
                },
            )
        }
    }

    fun apply_label_bulk(item_ids: List<String>, label_token: String, display_name: String) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        val id_set = ids.toSet()
        val prev_items = _inbox_state.value.items.filter { it.id in id_set }.associateBy { it.id }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in id_set) it.copy(labels = (it.labels + label_token).distinct()) else it
            },
        )
        viewModelScope.launch {
            val failed_ids = run_bulk_action(ids) { id ->
                repository.add_label_to_item(id, label_token).isSuccess
            }
            invalidate_caches(listOf("label:$label_token"))
            if (failed_ids.isEmpty()) {
                emit_toast(context.getString(R.string.added_to_label, display_name))
            } else {
                _inbox_state.value = _inbox_state.value.copy(
                    items = _inbox_state.value.items.map {
                        val prev = prev_items[it.id]
                        if (it.id in failed_ids && prev != null) it.copy(labels = prev.labels) else it
                    },
                )
                emit_toast(context.getString(R.string.couldnt_apply_label))
            }
        }
    }

    fun apply_tag_bulk(item_ids: List<String>, tag_token: String, display_name: String) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        val id_set = ids.toSet()
        val prev_items = _inbox_state.value.items.filter { it.id in id_set }.associateBy { it.id }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in id_set) {
                    val new_tokens = (it.tag_tokens + tag_token).distinct()
                    it.copy(
                        tag_tokens = new_tokens,
                        raw_item = it.raw_item.copy(tag_tokens = new_tokens),
                    )
                } else it
            },
        )
        viewModelScope.launch {
            val failed_ids = run_bulk_action(ids) { id ->
                repository.add_tag_to_item(id, tag_token).isSuccess
            }
            invalidate_caches(listOf("tag:$tag_token"))
            if (failed_ids.isEmpty()) {
                emit_toast(context.getString(R.string.added_to_label, display_name))
            } else {
                _inbox_state.value = _inbox_state.value.copy(
                    items = _inbox_state.value.items.map {
                        val prev = prev_items[it.id]
                        if (it.id in failed_ids && prev != null) prev else it
                    },
                )
                emit_toast(context.getString(R.string.couldnt_apply_label))
            }
        }
    }

    fun star_bulk(item_ids: List<String>) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        val id_set = ids.toSet()
        val targets = _inbox_state.value.items.filter { it.id in id_set }
        if (targets.isEmpty()) return
        val new_starred = targets.any { !it.is_starred }
        ids.forEach { star_overrides[it] = new_starred }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in id_set) it.copy(is_starred = new_starred) else it
            },
        )
        val current_folder = _inbox_state.value.current_folder
        val updated_cache = folder_cache.mapValues { (folder, cached) ->
            when {
                folder == current_folder -> cached
                folder == "starred" && !new_starred ->
                    cached.copy(items = cached.items.filter { it.id !in id_set })
                else -> cached.copy(items = cached.items.map {
                    if (it.id in id_set) it.copy(is_starred = new_starred) else it
                })
            }
        }
        folder_cache.putAll(updated_cache)
        if (new_starred) invalidate_caches(listOf("starred"))
        val raw_by_id = targets.associate { it.id to it.raw_item }
        viewModelScope.launch {
            val failed_ids = run_bulk_action(ids) { id ->
                repository.toggle_star(id, new_starred, raw_by_id[id]).isSuccess
            }
            if (failed_ids.isNotEmpty()) {
                emit_toast(context.getString(R.string.failed_to_update_selection))
            } else {
                emit_toast(
                    if (new_starred) context.getString(R.string.starred_count, ids.size)
                    else context.getString(R.string.unstarred_count, ids.size),
                )
            }
        }
    }

    fun snooze_bulk(item_ids: List<String>, snoozed_until_iso: String, label: String) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val failed_ids = run_bulk_action(ids) { id ->
                repository.snooze(id, snoozed_until_iso).isSuccess
            }
            val ok_ids = ids.filter { it !in failed_ids }.toSet()
            if (ok_ids.isNotEmpty()) {
                _inbox_state.value = _inbox_state.value.copy(
                    items = _inbox_state.value.items.filter { it.id !in ok_ids },
                )
                invalidate_caches(listOf("inbox", "snoozed"))
            }
            if (failed_ids.isNotEmpty()) {
                emit_toast(context.getString(R.string.couldnt_snooze))
            } else {
                emit_toast(context.getString(R.string.snoozed_until, label))
            }
        }
    }

    fun mark_unread_bulk(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        item_ids.forEach { read_overrides[it] = false }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in item_ids) it.copy(is_read = false) else it
            },
        )
        folder_cache.replaceAll { _, cached ->
            cached.copy(items = cached.items.map {
                if (it.id in item_ids) it.copy(is_read = false) else it
            })
        }
        _search_state.value = _search_state.value.copy(
            all_items = _search_state.value.all_items.map {
                if (it.id in item_ids) it.copy(is_read = false) else it
            },
        )
        val thread = _thread_state.value
        if (thread.item != null && thread.item.id in item_ids) {
            _thread_state.value = thread.copy(item = thread.item.copy(is_read = false))
        }
        invalidate_caches(listOf("starred"))
        viewModelScope.launch {
            repository.mark_unread_bulk(item_ids).fold(
                onSuccess = { emit_toast(context.getString(R.string.marked_unread_count, item_ids.size)) },
                onFailure = { emit_toast(context.getString(R.string.failed_mark_read)) },
            )
        }
    }

    private suspend fun run_bulk_action(
        ids: List<String>,
        action: suspend (String) -> Boolean,
    ): Set<String> = kotlinx.coroutines.coroutineScope {
        val failed = mutableSetOf<String>()
        ids.chunked(BULK_ACTION_CONCURRENCY).forEach { chunk ->
            val results = chunk.map { id -> async { id to runCatching { action(id) }.getOrDefault(false) } }.awaitAll()
            results.forEach { (id, ok) -> if (!ok) failed.add(id) }
        }
        failed
    }

    private fun count_label(n: Int, singular: String, plural: String): String {
        return if (n == 1) singular else "$n $plural"
    }

    private fun invalidate_caches(folders: List<String>) {
        folders.forEach {
            folder_cache.remove(it)
            folder_cache_time.remove(it)
        }
    }

    private fun lookup_raw_items(item_ids: List<String>): List<org.astermail.android.api.mail.MailItem?> {
        val all_items = _inbox_state.value.items +
            folder_cache.values.flatMap { it.items }
        val thread_item = _thread_state.value.item
        return item_ids.map { id ->
            all_items.find { it.id == id }?.raw_item
                ?: thread_item?.takeIf { it.id == id }?.raw_item
        }
    }

    fun archive(item_ids: List<String>, thread_count: Int = 1) {
        val had_demo = DEMO_PHISH_ITEM_ID in item_ids
        @Suppress("NAME_SHADOWING") val item_ids = handle_demo_in(item_ids)
        if (item_ids.isEmpty()) {
            if (had_demo) emit_toast(context.getString(R.string.archived_conversations, 1))
            return
        }
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        val raw_items = lookup_raw_items(item_ids)
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        val search_removed = remove_search_items(item_ids)
        pending_removed_ids.addAll(item_ids)
        val affected_label_caches = removed_items.flatMap { it.labels }.map { "label:$it" }
        val affected_tag_caches = removed_items.flatMap { it.tag_tokens }.map { "tag:$it" }
        invalidate_caches(listOf("archive", "inbox", "all") + affected_label_caches + affected_tag_caches)
        viewModelScope.launch {
            try {
                repository.archive(item_ids, raw_items).fold(
                    onSuccess = {
                        runCatching { search_index_manager.mark_archived(item_ids) }
                        accumulate_batch_action(
                            action_key = "archive",
                            thread_count = thread_count,
                            message_fn = { n -> context.getString(R.string.archived_conversations, n) },
                            undo_label = context.getString(R.string.undo),
                        ) { prev_undo -> { prev_undo?.invoke(); undo_local_restore(removed_items); undo_search_restore(search_removed); unarchive_backend_only(item_ids) } }
                        load_stats()
                    },
                    onFailure = { t ->
                        if (BuildConfig.DEBUG) android.util.Log.w("MailVM", "archive failed", t)
                        undo_local_restore(removed_items)
                        undo_search_restore(search_removed)
                        emit_toast(context.getString(R.string.failed_to_archive))
                    },
                )
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("MailVM", "archive threw", t)
                undo_local_restore(removed_items)
                undo_search_restore(search_removed)
                emit_toast(context.getString(R.string.failed_to_archive))
            } finally {
                pending_removed_ids.removeAll(item_ids.toSet())
            }
        }
    }

    fun trash(item_ids: List<String>, thread_count: Int = 1) {
        if (_inbox_state.value.current_folder == "drafts") {
            delete_draft_items(item_ids)
            return
        }
        val had_demo = DEMO_PHISH_ITEM_ID in item_ids
        @Suppress("NAME_SHADOWING") val item_ids = handle_demo_in(item_ids)
        if (item_ids.isEmpty()) {
            if (had_demo) emit_toast(context.getString(R.string.moved_to_trash, 1))
            return
        }
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        val raw_items = lookup_raw_items(item_ids)
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        val search_removed = remove_search_items(item_ids)
        pending_removed_ids.addAll(item_ids)
        invalidate_caches(listOf("trash", "inbox"))
        viewModelScope.launch {
            try {
                repository.trash(item_ids, raw_items).fold(
                    onSuccess = {
                        runCatching { search_index_manager.mark_trashed(item_ids) }
                        accumulate_batch_action(
                            action_key = "trash",
                            thread_count = thread_count,
                            message_fn = { n -> context.getString(R.string.moved_to_trash, n) },
                            undo_label = context.getString(R.string.undo),
                        ) { prev_undo -> { prev_undo?.invoke(); undo_local_restore(removed_items); undo_search_restore(search_removed); restore_trash_backend_only(item_ids) } }
                        load_stats()
                    },
                    onFailure = {
                        undo_local_restore(removed_items)
                        undo_search_restore(search_removed)
                        emit_toast(context.getString(R.string.failed_to_trash))
                    },
                )
            } catch (_: Throwable) {
                undo_local_restore(removed_items)
                undo_search_restore(search_removed)
                emit_toast(context.getString(R.string.failed_to_trash))
            } finally {
                pending_removed_ids.removeAll(item_ids.toSet())
            }
        }
    }

    private fun delete_draft_items(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val id_set = item_ids.toHashSet()
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in id_set }
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in id_set },
        )
        pending_removed_ids.addAll(id_set)
        viewModelScope.launch {
            try {
                val all_succeeded = item_ids.map { id ->
                    repository.delete_draft(id).isSuccess
                }.all { it }
                if (all_succeeded) {
                    load_stats()
                    emit_toast(context.getString(R.string.draft_deleted, item_ids.size))
                } else {
                    undo_local_restore(removed_items)
                    emit_toast(context.getString(R.string.failed_to_delete_draft))
                }
            } finally {
                pending_removed_ids.removeAll(id_set)
            }
        }
    }

    fun mark_spam(item_ids: List<String>, thread_count: Int = 1, sender_emails_hint: List<String> = emptyList()) {
        val had_demo = DEMO_PHISH_ITEM_ID in item_ids
        @Suppress("NAME_SHADOWING") val item_ids = handle_demo_in(item_ids)
        if (item_ids.isEmpty()) {
            if (had_demo) emit_toast(context.getString(R.string.reported_as_spam))
            return
        }
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        val raw_items = lookup_raw_items(item_ids)
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        pending_removed_ids.addAll(item_ids)
        invalidate_caches(listOf("spam", "inbox"))
        viewModelScope.launch {
            try {
                val sender_emails =
                    removed_items.map { it.sender_email }.ifEmpty { sender_emails_hint }
                repository.mark_spam(item_ids, raw_items).fold(
                    onSuccess = {
                        runCatching { search_index_manager.mark_spam(item_ids) }
                        val report_job =
                            viewModelScope.launch { repository.report_spam_senders(sender_emails) }
                        emit_toast_undo(
                            context.getString(R.string.reported_as_spam),
                            context.getString(R.string.undo),
                        ) {
                            undo_local_restore(removed_items)
                            unmark_spam_backend_only(item_ids)
                            viewModelScope.launch {
                                report_job.join()
                                repository.remove_spam_senders(sender_emails)
                            }
                        }
                        load_stats()
                    },
                    onFailure = {
                        undo_local_restore(removed_items)
                        emit_toast(context.getString(R.string.failed_report_spam))
                    },
                )
            } finally {
                pending_removed_ids.removeAll(item_ids.toSet())
            }
        }
    }

    fun unmark_spam(item_ids: List<String>, sender_emails_hint: List<String> = emptyList()) {
        if (item_ids.isEmpty()) return
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        pending_removed_ids.addAll(item_ids)
        invalidate_caches(listOf("spam", "inbox"))
        viewModelScope.launch {
            try {
                val sender_emails =
                    removed_items.map { it.sender_email }.ifEmpty { sender_emails_hint }
                repository.unmark_spam(item_ids).fold(
                    onSuccess = {
                        val remove_job =
                            viewModelScope.launch { repository.remove_spam_senders(sender_emails) }
                        emit_toast_undo(
                            context.getString(R.string.moved_to_inbox),
                            context.getString(R.string.undo),
                        ) {
                            undo_local_restore(removed_items)
                            mark_spam_backend_only(item_ids)
                            viewModelScope.launch {
                                remove_job.join()
                                repository.report_spam_senders(sender_emails)
                            }
                        }
                        load_stats()
                    },
                    onFailure = {
                        undo_local_restore(removed_items)
                        emit_toast(context.getString(R.string.failed_remove_spam))
                    },
                )
            } finally {
                pending_removed_ids.removeAll(item_ids.toSet())
            }
        }
    }

    private fun undo_local_restore(removed: List<InboxItem>) {
        if (removed.isEmpty()) return
        val current = _inbox_state.value.items
        val current_ids = current.map { it.id }.toHashSet()
        val to_add = removed.filter { it.id !in current_ids }
        if (to_add.isEmpty()) {
            invalidate_caches(listOf("inbox", "archive", "trash", "spam"))
            return
        }
        val merged = (current + to_add).sortedByDescending { it.timestamp }
        _inbox_state.value = _inbox_state.value.copy(items = merged)
        invalidate_caches(listOf("inbox", "archive", "trash", "spam"))
    }

    private fun remove_search_items(item_ids: List<String>): List<InboxItem> {
        val current = _search_state.value.all_items
        val removed = current.filter { it.id in item_ids }
        if (removed.isNotEmpty()) {
            _search_state.value = _search_state.value.copy(
                all_items = current.filter { it.id !in item_ids },
            )
        }
        return removed
    }

    private fun undo_search_restore(removed: List<InboxItem>) {
        if (removed.isEmpty()) return
        val current = _search_state.value.all_items
        val current_ids = current.map { it.id }.toHashSet()
        val to_add = removed.filter { it.id !in current_ids }
        if (to_add.isEmpty()) return
        _search_state.value = _search_state.value.copy(all_items = current + to_add)
    }

    fun unarchive_backend_only(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        viewModelScope.launch { repository.unarchive(item_ids) }
    }

    fun archive_backend_only(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val raw_items = lookup_raw_items(item_ids)
        viewModelScope.launch { repository.archive(item_ids, raw_items) }
    }

    fun trash_backend_only(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val raw_items = lookup_raw_items(item_ids)
        viewModelScope.launch { repository.trash(item_ids, raw_items) }
    }

    fun mark_spam_backend_only(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val raw_items = lookup_raw_items(item_ids)
        viewModelScope.launch { repository.mark_spam(item_ids, raw_items) }
    }

    fun restore_trash_backend_only(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        viewModelScope.launch { repository.restore_trash(item_ids) }
    }

    fun unmark_spam_backend_only(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        viewModelScope.launch { repository.unmark_spam(item_ids) }
    }

    fun unarchive(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        val raw_items = lookup_raw_items(item_ids)
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        pending_removed_ids.addAll(item_ids)
        invalidate_caches(listOf("inbox", "archive"))
        viewModelScope.launch {
            try {
                repository.unarchive(item_ids, raw_items).fold(
                    onSuccess = {
                        runCatching { search_index_manager.mark_unarchived(item_ids) }
                        emit_toast_undo(
                            context.getString(R.string.moved_to_inbox),
                            context.getString(R.string.undo),
                        ) { undo_local_restore(removed_items); archive_backend_only(item_ids) }
                        load_stats()
                    },
                    onFailure = {
                        undo_local_restore(removed_items)
                        emit_toast(context.getString(R.string.failed_to_unarchive))
                    },
                )
            } finally {
                pending_removed_ids.removeAll(item_ids.toSet())
            }
        }
    }

    fun restore_trash(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        pending_removed_ids.addAll(item_ids)
        invalidate_caches(listOf("inbox", "trash"))
        viewModelScope.launch {
            try {
                repository.restore_trash(item_ids).fold(
                    onSuccess = {
                        emit_toast_undo(
                            context.getString(R.string.restored_to_inbox),
                            context.getString(R.string.undo),
                        ) { undo_local_restore(removed_items); trash_backend_only(item_ids) }
                        load_stats()
                    },
                    onFailure = {
                        undo_local_restore(removed_items)
                        emit_toast(context.getString(R.string.failed_to_restore))
                    },
                )
            } finally {
                pending_removed_ids.removeAll(item_ids.toSet())
            }
        }
    }

    fun delete_permanent(item_id: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) {
            handle_demo_in(listOf(item_id))
            emit_toast(context.getString(R.string.deleted_permanently))
            return
        }
        val previous = _inbox_state.value.items
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id != item_id },
        )
        pending_removed_ids.add(item_id)
        viewModelScope.launch {
            try {
                repository.delete_permanent(item_id).fold(
                    onSuccess = {
                        runCatching { search_index_manager.remove_items(listOf(item_id)) }
                        emit_toast(context.getString(R.string.deleted_permanently))
                        load_stats()
                    },
                    onFailure = {
                        _inbox_state.value = _inbox_state.value.copy(items = previous)
                        emit_toast(context.getString(R.string.failed_to_delete))
                    },
                )
            } finally {
                pending_removed_ids.remove(item_id)
            }
        }
    }

    fun mark_read_bulk(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        MailPollingWorker.cancel_message_notifications(context, item_ids)
        item_ids.forEach { read_overrides[it] = true }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in item_ids) it.copy(is_read = true) else it
            },
        )
        folder_cache.replaceAll { _, cached ->
            cached.copy(items = cached.items.map {
                if (it.id in item_ids) it.copy(is_read = true) else it
            })
        }
        _search_state.value = _search_state.value.copy(
            all_items = _search_state.value.all_items.map {
                if (it.id in item_ids) it.copy(is_read = true) else it
            },
        )
        val thread = _thread_state.value
        if (thread.item != null && thread.item.id in item_ids) {
            _thread_state.value = thread.copy(item = thread.item.copy(is_read = true))
        }
        invalidate_caches(listOf("starred"))
        viewModelScope.launch {
            repository.mark_read_bulk(item_ids).fold(
                onSuccess = { emit_toast(context.getString(R.string.marked_read_count, item_ids.size)) },
                onFailure = { emit_toast(context.getString(R.string.failed_mark_read)) },
            )
        }
    }

    fun mark_all_read_scope(folder: String) {
        MailPollingWorker.clear_all_mail_notifications(context)
        val prior_reads = collect_read_states(folder)
        prior_reads.keys.forEach { read_overrides[it] = true }
        apply_bulk_read(folder, true)
        viewModelScope.launch {
            val result = if (repository.folder_supports_bulk_scope(folder)) {
                repository.mark_all_read_scope(folder)
            } else {
                val ids = prior_reads.keys.toList()
                if (ids.isEmpty()) return@launch else repository.mark_read_bulk(ids)
            }
            result.fold(
                onSuccess = {
                    invalidate_caches(listOf(folder))
                    emit_toast(context.getString(R.string.all_marked_read))
                },
                onFailure = {
                    revert_bulk_read(folder, prior_reads)
                    emit_toast(context.getString(R.string.failed_mark_all_read))
                },
            )
        }
    }

    fun mark_all_unread_scope(folder: String) {
        val prior_reads = collect_read_states(folder)
        prior_reads.keys.forEach { read_overrides[it] = false }
        apply_bulk_read(folder, false)
        viewModelScope.launch {
            val result = if (repository.folder_supports_bulk_scope(folder)) {
                repository.mark_all_unread_scope(folder)
            } else {
                val ids = prior_reads.keys.toList()
                if (ids.isEmpty()) return@launch else repository.mark_unread_bulk(ids)
            }
            result.fold(
                onSuccess = {
                    invalidate_caches(listOf(folder))
                    emit_toast(context.getString(R.string.all_marked_unread))
                },
                onFailure = {
                    revert_bulk_read(folder, prior_reads)
                    emit_toast(context.getString(R.string.failed_mark_all_unread))
                },
            )
        }
    }

    private fun collect_read_states(folder: String): Map<String, Boolean> {
        val states = LinkedHashMap<String, Boolean>()
        _inbox_state.value.items.forEach { states[it.id] = it.is_read }
        folder_cache[folder]?.items?.forEach { states.putIfAbsent(it.id, it.is_read) }
        return states
    }

    private fun apply_bulk_read(folder: String, target_read: Boolean) {
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map { it.copy(is_read = target_read) },
        )
        folder_cache[folder]?.let { cached ->
            folder_cache[folder] = cached.copy(items = cached.items.map { it.copy(is_read = target_read) })
        }
    }

    private fun revert_bulk_read(folder: String, prior: Map<String, Boolean>) {
        prior.keys.forEach { read_overrides.remove(it) }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map { item -> prior[item.id]?.let { item.copy(is_read = it) } ?: item },
        )
        folder_cache[folder]?.let { cached ->
            folder_cache[folder] = cached.copy(
                items = cached.items.map { item -> prior[item.id]?.let { item.copy(is_read = it) } ?: item },
            )
        }
    }

    fun empty_trash() {
        val previous = _inbox_state.value.items
        _inbox_state.value = _inbox_state.value.copy(items = emptyList())
        folder_cache.remove("trash")
        viewModelScope.launch {
            repository.empty_trash().fold(
                onSuccess = {
                    emit_toast(context.getString(R.string.trash_emptied))
                    load_stats()
                    if (_inbox_state.value.current_folder == "trash") {
                        load_inbox("trash", force = true)
                    }
                },
                onFailure = {
                    if (_inbox_state.value.current_folder == "trash") {
                        _inbox_state.value = _inbox_state.value.copy(items = previous)
                    }
                    emit_toast(context.getString(R.string.failed_empty_trash))
                },
            )
        }
    }

    fun build_search_index(force: Boolean = false) {
        val current = _search_state.value
        if (current.is_indexing) return
        if (current.is_indexed && !force) return
        _search_state.value = current.copy(is_indexing = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = search_index_manager.get_cached_items()
                if (cached.isNotEmpty() && !force) {
                    _search_state.value = SearchUiState(
                        all_items = cached.map { it.to_inbox_item() },
                        is_indexed = true,
                    )
                    search_index_manager.refresh_index()
                } else {
                    search_index_manager.ensure_index_built()
                    repository.fetch_all_for_search().fold(
                        onSuccess = { items ->
                            search_index_manager.on_items_loaded(items)
                            _search_state.value = SearchUiState(
                                all_items = items,
                                is_indexed = true,
                            )
                        },
                        onFailure = { t ->
                            val keep = _search_state.value.all_items
                            _search_state.value = _search_state.value.copy(
                                is_indexing = false,
                                error = if (keep.isNotEmpty()) null else (t.message ?: context.getString(R.string.something_went_wrong)),
                            )
                        },
                    )
                }
            } catch (t: Throwable) {
                val keep = _search_state.value.all_items
                _search_state.value = _search_state.value.copy(
                    is_indexing = false,
                    error = if (keep.isNotEmpty()) null else (t.message ?: context.getString(R.string.something_went_wrong)),
                )
            }
        }
    }

    fun load_inbox_attachment_chips(item_ids: List<String>) {
        val already_loaded = _inbox_attachment_chips.value
        val to_load = item_ids.filter { it !in already_loaded }
        if (to_load.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val with_attachments = repository.find_messages_with_attachments(to_load)
            if (with_attachments.isEmpty()) return@launch
            val chips = with_attachments.map { id ->
                async {
                    val atts = repository.fetch_attachments_for_message(id)
                    id to atts.take(3).map { att ->
                        InboxAttachmentChip(
                            filename = att.filename,
                            content_type = att.content_type,
                        )
                    }
                }
            }.awaitAll().toMap().filter { it.value.isNotEmpty() }
            _inbox_attachment_chips.update { it + chips }
        }
    }

    fun refresh() {
        val folder = _inbox_state.value.current_folder
        if (_inbox_state.value.is_refreshing) return
        val gen = account_generation
        folder_cache.remove(folder)
        folder_cache_time.remove(folder)
        _inbox_state.update { it.copy(is_refreshing = true) }
        load_stats()
        inbox_load_job?.cancel()
        silent_revalidate_job?.cancel()
        refresh_job?.cancel()
        refresh_job = viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(INBOX_FETCH_BACKSTOP_MS) {
                    fetch_for_folder(folder).getOrThrow()
                }
            }
            if (account_generation != gen) return@launch
            if (_inbox_state.value.current_folder != folder) {
                _inbox_state.update { it.copy(is_refreshing = false) }
                return@launch
            }
            result.fold(
                onSuccess = { page ->
                    val previous = _inbox_state.value.items
                    val combined = merge_with_previous(page.items, previous, folder, page.total)
                    val merged_items = apply_demo_overlay(
                        apply_pin_overrides(apply_star_overrides(apply_read_overrides(combined))),
                        folder,
                    )
                    _inbox_state.value = _inbox_state.value.copy(
                        items = merged_items,
                        is_loading = false,
                        is_refreshing = false,
                        initial = false,
                        error = null,
                        has_more = page.has_more,
                        next_cursor = page.next_cursor,
                        total = page.total,
                    )
                    folder_cache[folder] = _inbox_state.value
                    folder_cache_time[folder] = System.currentTimeMillis()
                    search_index_manager.on_items_loaded(page.items)
                    search_index_manager.ensure_index_built()
                },
                onFailure = {
                    _inbox_state.update { it.copy(is_refreshing = false) }
                },
            )
        }
    }

    suspend fun get_or_create_thread_token(original_email_id: String, existing_thread_token: String?): String? =
        repository.get_or_create_thread_token(original_email_id, existing_thread_token)

    suspend fun send_email(
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        body_html: String,
        sender_email: String? = null,
        sender_display_name: String? = null,
        thread_token: String? = null,
        expires_at: String? = null,
        expiry_password: String? = null,
        attachments: List<ExternalAttachmentPayload> = emptyList(),
        sender_alias_hash: String? = null,
        suppress_branding: Boolean? = null,
    ): Result<org.astermail.android.api.send.SimpleSendResponse> {
        val result = repository.send_email(
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body_html = body_html,
            sender_email = sender_email,
            sender_display_name = sender_display_name,
            thread_token = thread_token,
            expires_at = expires_at,
            expiry_password = expiry_password,
            attachments = attachments,
            sender_alias_hash = sender_alias_hash,
            suppress_branding = suppress_branding,
        )
        if (result.isSuccess) {
            invalidate_caches(listOf("sent", "drafts"))
        }
        return result
    }

    val pending_undo_send: StateFlow<MailRepository.PendingUndoSend?> = repository.pending_undo_send

    val send_problem: StateFlow<Boolean> = repository.send_problem

    fun dismiss_send_problem() {
        repository.clear_send_problem()
    }

    init {
        viewModelScope.launch {
            repository.new_mail_events.collect {
                if (foreground_check()) {
                    silent_revalidate(_inbox_state.value.current_folder)
                }
            }
        }
        viewModelScope.launch {
            repository.send_result_events.collect { result ->
                if (result.isSuccess) {
                    invalidate_caches(listOf("sent", "drafts"))
                    viewModelScope.launch {
                        repeat(2) { attempt ->
                            kotlinx.coroutines.delay(if (attempt == 0) 1_200L else 5_000L)
                            invalidate_caches(listOf("sent", "drafts"))
                            val current = _inbox_state.value.current_folder
                            if (current == "sent" || current == "drafts") {
                                silent_revalidate(current)
                            }
                        }
                    }
                    refresh_current_thread()
                } else {
                    emit_toast(
                        result.exceptionOrNull()?.message
                            ?: context.getString(R.string.save_failed),
                    )
                }
            }
        }
    }

    fun schedule_send_with_undo(
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body_html: String,
        sender_email: String?,
        sender_display_name: String?,
        thread_token: String? = null,
        expires_at: String? = null,
        expiry_password: String? = null,
        attachments: List<ExternalAttachmentPayload> = emptyList(),
        sender_alias_hash: String? = null,
        suppress_branding: Boolean? = null,
        undo_seconds: Int,
        draft_id: String? = null,
    ) {
        repository.schedule_send_with_undo(
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body_html = body_html,
            sender_email = sender_email,
            sender_display_name = sender_display_name,
            thread_token = thread_token,
            expires_at = expires_at,
            expiry_password = expiry_password,
            attachments = attachments,
            sender_alias_hash = sender_alias_hash,
            suppress_branding = suppress_branding,
            undo_seconds = undo_seconds,
            draft_id = draft_id,
        )
    }

    suspend fun save_draft(
        subject: String,
        body_html: String,
        sender_email: String? = null,
        to: List<String> = emptyList(),
        cc: List<String> = emptyList(),
        existing_draft_id: String? = null,
    ): Result<String> {
        val result = repository.save_draft(
            subject = subject,
            body_html = body_html,
            sender_email = sender_email,
            to = to,
            cc = cc,
            existing_draft_id = existing_draft_id,
        )
        if (result.isSuccess) invalidate_caches(listOf("drafts"))
        return result
    }

    fun save_draft_and_finish(
        subject: String,
        body_html: String,
        sender_email: String? = null,
        to: List<String> = emptyList(),
        cc: List<String> = emptyList(),
        existing_draft_id: String? = null,
        on_complete: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.save_draft(
                    subject = subject,
                    body_html = body_html,
                    sender_email = sender_email,
                    to = to,
                    cc = cc,
                    existing_draft_id = existing_draft_id,
                )
            }
            if (result.isSuccess) {
                runCatching { invalidate_caches(listOf("drafts")) }
            } else {
                runCatching {
                    emit_toast(context.getString(R.string.failed_to_save_draft))
                }
            }
            on_complete(result.isSuccess)
        }
    }

    suspend fun schedule_email(
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        body_html: String,
        sender_email: String? = null,
        sender_display_name: String? = null,
        scheduled_at: String,
        sender_alias_hash: String? = null,
    ): Result<String> {
        return repository.schedule_email(
            subject = subject,
            body_html = body_html,
            sender_email = sender_email,
            sender_display_name = sender_display_name,
            to = to,
            cc = cc,
            bcc = bcc,
            scheduled_at = scheduled_at,
            sender_alias_hash = sender_alias_hash,
        )
    }

    private suspend fun item_to_single_message(item: InboxItem): ThreadMessageDecrypted {
        val raw = item.raw_item
        val thread_item = org.astermail.android.api.mail.ThreadMessageItem(
            id = raw.id,
            item_type = raw.item_type,
            encrypted_envelope = raw.encrypted_envelope,
            envelope_nonce = raw.envelope_nonce,
            message_ts = raw.message_ts,
            created_at = raw.created_at,
            metadata = raw.metadata,
        )
        val decrypted = repository.decrypt_single_thread_message(thread_item)
        return if (decrypted.sender_name.isNotBlank() || decrypted.body_text.isNotBlank() || decrypted.body_html != null) {
            decrypted
        } else {
            decrypted.copy(
                sender_name = item.sender_name,
                sender_email = item.sender_email,
                body_text = item.preview,
                display_sender_name = item.display_sender_name,
                display_sender_email = item.display_sender_email,
            )
        }
    }

    private fun folder_matches(folder: String, item: InboxItem): Boolean = when (folder) {
        "inbox" -> !item.is_trashed && !item.is_archived && !item.is_spam
        "starred" -> item.is_starred && !item.is_trashed
        "trash" -> item.is_trashed
        "spam" -> item.is_spam
        "archive" -> item.is_archived
        "all" -> !item.is_trashed
        else -> when {
            folder.startsWith("label:") -> {
                val token = folder.removePrefix("label:")
                item.labels.contains(token) && !item.is_trashed
            }
            folder.startsWith("tag:") -> {
                val token = folder.removePrefix("tag:")
                item.tag_tokens.contains(token) && !item.is_trashed
            }
            else -> item.labels.contains(folder) && !item.is_trashed
        }
    }

    private fun is_timeout_failure(t: Throwable?): Boolean = when (t) {
        null -> false
        is kotlinx.coroutines.TimeoutCancellationException -> true
        is io.ktor.client.plugins.HttpRequestTimeoutException -> true
        is io.ktor.client.network.sockets.ConnectTimeoutException -> true
        is java.net.SocketTimeoutException -> true
        else -> false
    }

    private fun friendly_load_error(t: Throwable): String {
        val res = when {
            is_timeout_failure(t) -> R.string.error_timeout
            t is org.astermail.android.api.ApiError.NetworkError -> R.string.error_no_connection
            t is org.astermail.android.api.ApiError.ServerError -> R.string.error_server
            t is java.net.UnknownHostException ||
                t is java.net.ConnectException ||
                t is java.io.IOException -> R.string.error_no_connection
            else -> R.string.something_went_wrong
        }
        return context.getString(res)
    }

    private suspend fun fetch_for_folder(
        folder: String,
        cursor: String? = null,
        limit: Int = 50,
    ): Result<InboxPage> = when (folder) {
        "inbox" -> repository.fetch_inbox(limit = limit, cursor = cursor)
        "sent" -> repository.fetch_sent(limit = limit, cursor = cursor)
        "drafts" -> repository.fetch_drafts(limit = limit, cursor = cursor)
        "starred" -> repository.fetch_starred(limit = limit, cursor = cursor)
        "trash" -> repository.fetch_trash(limit = limit, cursor = cursor)
        "spam" -> repository.fetch_spam(limit = limit, cursor = cursor)
        "archive" -> repository.fetch_archive(limit = limit, cursor = cursor)
        "scheduled" -> repository.fetch_scheduled(limit = limit, cursor = cursor)
        "snoozed" -> repository.fetch_snoozed(limit = limit, cursor = cursor)
        "all" -> repository.fetch_inbox(limit = limit, cursor = cursor, item_type = "all")
        else -> when {
            folder.startsWith("label:") -> {
                val label_token = folder.removePrefix("label:")
                repository.fetch_inbox(limit = limit, item_type = null, label_token = label_token, offset = cursor?.toIntOrNull())
            }
            folder.startsWith("tag:") -> {
                val tag_token = folder.removePrefix("tag:")
                repository.fetch_inbox(limit = limit, item_type = null, tag_token = tag_token, offset = cursor?.toIntOrNull())
            }
            folder.startsWith("routing:") -> {
                val routing_token = folder.removePrefix("routing:")
                repository.fetch_inbox(limit = limit, item_type = null, routing_token = routing_token, offset = cursor?.toIntOrNull())
            }
            else -> repository.fetch_inbox(limit = limit, item_type = null, label_token = folder, offset = cursor?.toIntOrNull())
        }
    }
}

fun org.astermail.android.storage.search.DecryptedMailEntity.to_inbox_item(): InboxItem = InboxItem(
    id = id,
    thread_token = thread_token,
    thread_message_count = thread_message_count,
    sender_name = sender_name,
    sender_email = sender_email,
    subject = subject,
    preview = preview,
    timestamp = timestamp,
    is_read = is_read,
    is_starred = is_starred,
    is_encrypted = is_encrypted,
    has_attachments = has_attachments,
    is_trashed = is_trashed,
    is_archived = is_archived,
    is_spam = is_spam,
    labels = if (labels.isBlank()) emptyList() else labels.split(","),
    category = category,
    received_on = received_on,
    display_sender_name = display_sender_name,
    display_sender_email = display_sender_email,
    raw_item = org.astermail.android.api.mail.MailItem(id = id),
)
