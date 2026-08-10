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
import kotlinx.coroutines.delay
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
import org.astermail.android.crypto.same_address_ignoring_dots
import org.astermail.android.notifications.MailPollingWorker
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.ui.mail.MessageAttachment

private const val INBOX_FETCH_BACKSTOP_MS = 50_000L
private const val PULL_REFRESH_BACKSTOP_MS = 20_000L
private const val WARM_CACHE_MIN_ITEMS = 8
private const val WARM_CACHE_WINDOW = 200
private const val BULK_ACTION_CONCURRENCY = 6
private const val CARRIED_ITEM_STALE_MS = 20 * 60 * 1000L
private const val RESTORE_PROTECTION_MS = 15_000L
private const val STATS_TTL_MS = 30_000L
private const val STATS_DEBOUNCE_MS = 1_200L
private const val DECRYPT_RETRY_TIMEOUT_MS = 20_000L
private const val SEND_GUARD_WINDOW_MS = 30_000L
private const val LOAD_MORE_FAILURE_LIMIT = 3
private const val LOAD_ALL_MAX_PAGES = 5_000
private const val LOAD_ALL_MAX_ITEMS = 50_000
private const val LOAD_ALL_PAGE_WAIT_TICKS = 4_800
private const val LOAD_ALL_MAX_STALLS = 3

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

    fun set_custom_categories(
        rules: List<org.astermail.android.api.preferences.CustomCategoryRule>,
    ) {
        repository.set_custom_categories(rules)
    }

    fun set_conversation_grouping(enabled: Boolean) {
        if (!repository.set_conversation_grouping(enabled)) return
        folder_cache.clear()
        folder_cache_time.clear()
        refresh()
    }

    private val _search_state = MutableStateFlow(SearchUiState())
    val search_state: StateFlow<SearchUiState> = _search_state.asStateFlow()

    private val _inbox_attachment_ids = MutableStateFlow<Set<String>>(emptySet())
    val inbox_attachment_ids: StateFlow<Set<String>> = _inbox_attachment_ids.asStateFlow()

    private val inbox_attachment_probed = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )
    private val inbox_attachment_seeded = java.util.concurrent.atomic.AtomicBoolean(false)
    private val inbox_attachment_probe_retries = 4

    private val _message_reactions = MutableStateFlow<Map<String, List<DecryptedReaction>>>(emptyMap())
    val message_reactions: StateFlow<Map<String, List<DecryptedReaction>>> = _message_reactions.asStateFlow()

    private var reactions_enabled = true

    fun set_reactions_enabled(enabled: Boolean) {
        if (reactions_enabled == enabled) return
        reactions_enabled = enabled
        if (!enabled) _message_reactions.value = emptyMap()
    }

    private fun load_reactions(messages: List<ThreadMessageDecrypted>) {
        if (!reactions_enabled) {
            _message_reactions.value = emptyMap()
            return
        }
        val direct = LinkedHashMap<String, MutableList<DecryptedReaction>>()
        val unresolved = ArrayList<Triple<String, String, Boolean>>()
        for (msg in messages) {
            for (summary in msg.raw_item.reactions.orEmpty()) {
                val emoji = summary.emoji
                if (!emoji.isNullOrBlank()) {
                    direct.getOrPut(msg.id) { ArrayList() }.add(
                        DecryptedReaction(
                            reaction_mail_item_id = summary.reaction_mail_item_id,
                            emoji = emoji,
                            reactor_email = summary.reactor_email.orEmpty(),
                            is_own = summary.is_own,
                        ),
                    )
                } else {
                    unresolved.add(
                        Triple(msg.id, summary.reaction_mail_item_id, summary.is_own),
                    )
                }
            }
        }
        _message_reactions.value = direct.mapValues { it.value.toList() }
        if (unresolved.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolved = unresolved.map { (message_id, reaction_id, is_own) ->
                async {
                    message_id to repository.resolve_reaction(reaction_id)
                        ?.copy(is_own = is_own)
                }
            }.awaitAll()
            if (resolved.none { it.second != null }) return@launch
            val merged = LinkedHashMap<String, MutableList<DecryptedReaction>>()
            _message_reactions.value.forEach { (k, v) -> merged[k] = v.toMutableList() }
            for ((message_id, reaction) in resolved) {
                if (reaction == null) continue
                val bucket = merged.getOrPut(message_id) { ArrayList() }
                if (bucket.none { it.reaction_mail_item_id == reaction.reaction_mail_item_id }) {
                    bucket.add(reaction)
                }
            }
            _message_reactions.value = merged.mapValues { it.value.toList() }
        }
    }

    fun send_reaction(
        message_id: String,
        emoji: String,
        sender_email: String? = null,
        sender_alias_hash: String? = null,
        own_addresses: Set<String> = emptySet(),
        on_result: (String?) -> Unit,
    ) {
        if (!reactions_enabled) return
        val state = _thread_state.value
        val message = state.messages.find { it.id == message_id } ?: return
        val our_email = repository.get_user_email().orEmpty()
        val restriction = reaction_restriction(
            item_type = message.raw_item.item_type ?: "received",
            sender_email = message.sender_email,
            to_addresses = message.to_addresses,
            cc_addresses = message.cc_addresses,
            raw_headers = message.raw_headers,
            reactions = _message_reactions.value[message_id].orEmpty(),
            user_email = our_email,
            is_spam = state.item?.is_spam == true,
            is_trashed = state.item?.is_trashed == true,
            reactions_enabled = true,
            is_own_address = { address -> address in own_addresses },
        )
        if (restriction != null) {
            on_result(context.getString(reaction_restriction_string(restriction)))
            return
        }
        val from_email = sender_email?.takeIf { it.isNotBlank() } ?: our_email
        val sender_is_self = same_address_ignoring_dots(message.sender_email, our_email) ||
            same_address_ignoring_dots(message.sender_email, from_email)
        val recipient = if (!sender_is_self) {
            message.sender_email
        } else {
            message.to_addresses.firstOrNull { it.isNotBlank() }
        }
        if (recipient.isNullOrBlank()) {
            on_result(context.getString(R.string.reaction_failed))
            return
        }
        val optimistic = DecryptedReaction(
            reaction_mail_item_id = "pending_${message_id}_$emoji",
            emoji = emoji,
            reactor_email = from_email,
            is_own = true,
        )
        _message_reactions.update { current ->
            val bucket = current[message_id].orEmpty()
            if (bucket.any { it.emoji == emoji && (it.is_own || it.reactor_email.equals(our_email, ignoreCase = true)) }) {
                current
            } else {
                current + (message_id to (bucket + optimistic))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.send_reaction(
                target_message_id = message_id,
                message_group_id = message.raw_item.message_group_id,
                thread_token = state.item?.thread_token,
                recipient = recipient,
                emoji = emoji,
                sender_email = from_email.ifBlank { null },
                sender_alias_hash = sender_alias_hash,
                reply_subject = message.subject,
                in_reply_to = message.raw_headers
                    .firstOrNull { it.first.equals("message-id", ignoreCase = true) }
                    ?.second,
            )
            val error = result.exceptionOrNull()
            if (error != null) {
                _message_reactions.update { current ->
                    val bucket = current[message_id].orEmpty()
                        .filter { it.reaction_mail_item_id != optimistic.reaction_mail_item_id }
                    current + (message_id to bucket)
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                on_result(
                    when {
                        error == null -> null
                        !error.message.isNullOrBlank() -> error.message
                        else -> context.getString(R.string.reaction_failed)
                    },
                )
            }
        }
    }

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
    private val restore_protected_until = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var list_order: String? = null
    private var page_size: Int = 50
    private var inbox_load_job: Job? = null
    private var last_stats_load_ms = 0L
    private var stats_job: Job? = null
    private val _emptying_spam = MutableStateFlow(false)
    val emptying_spam_state: StateFlow<Boolean> = _emptying_spam.asStateFlow()
    private var silent_revalidate_job: Job? = null
    private var load_more_job: Job? = null
    private var load_more_generation = 0
    private var load_more_failures = 0
    private var refresh_job: Job? = null
    private var refresh_generation = 0
    private var load_all_remaining_job: Job? = null
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
        restore_protected_until.clear()
        last_stats_load_ms = 0L
        star_overrides.clear()
        pin_overrides.clear()
        read_overrides.clear()
        _inbox_state.value = InboxUiState()
        _thread_state.value = ThreadUiState()
        _search_state.value = SearchUiState()
        _inbox_attachment_ids.value = emptySet()
        inbox_attachment_probed.clear()
        inbox_attachment_seeded.set(false)
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
        if (list_order != null) return live_items
        if (total != null && total <= live_items.size) return live_items
        val page_ids = live_items.map { it.id }.toHashSet()
        val cap = (total ?: previous_items.size).coerceAtLeast(live_items.size)
        val min_page_timestamp = live_items.minOfOrNull { it.timestamp }
        val carried = previous_items.asSequence()
            .filter { it.id !in page_ids }
            .filter { it.id !in pending_removed_ids }
            .filter {
                restore_protected(it.id, now) ||
                    min_page_timestamp == null ||
                    it.timestamp < min_page_timestamp
            }
            .filter { folder_matches(folder, it) }
            .filter {
                restore_protected(it.id, now) ||
                    now - (item_last_confirmed[it.id] ?: 0L) <= CARRIED_ITEM_STALE_MS
            }
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

    fun set_list_order(order: String?) {
        if (list_order == order) return
        list_order = order
        val folder = _inbox_state.value.current_folder
        inbox_load_job?.cancel()
        silent_revalidate_job?.cancel()
        folder_cache.clear()
        folder_cache_time.clear()
        _inbox_state.value = _inbox_state.value.copy(
            items = emptyList(),
            is_loading = true,
            initial = true,
            error = null,
            has_more = false,
            next_cursor = null,
        )
        load_inbox(folder, force = true)
    }

    fun set_page_size(size: Int) {
        val clamped = size.coerceIn(10, 100)
        if (page_size == clamped) return
        page_size = clamped
        val folder = _inbox_state.value.current_folder
        inbox_load_job?.cancel()
        silent_revalidate_job?.cancel()
        folder_cache.clear()
        folder_cache_time.clear()
        _inbox_state.value = _inbox_state.value.copy(
            items = emptyList(),
            is_loading = true,
            initial = true,
            error = null,
            has_more = false,
            next_cursor = null,
        )
        load_inbox(folder, force = true)
    }

    fun load_inbox(folder: String = "inbox", force: Boolean = false) {
        load_more_failures = 0
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
                if (persisted.isNotEmpty() && list_order == null && _inbox_state.value.current_folder == folder && folder == "inbox") {
                    val safe = persisted.filter { !it.is_trashed && !it.is_archived && !it.is_spam }
                        .take(WARM_CACHE_WINDOW)
                    if (safe.size >= WARM_CACHE_MIN_ITEMS) {
                        val items = safe.map { it.to_inbox_item() }
                            .filter { folder_matches(folder, it) }
                        if (items.size >= WARM_CACHE_MIN_ITEMS) {
                            val warmed_at = System.currentTimeMillis()
                            items.forEach { item_last_confirmed.putIfAbsent(it.id, warmed_at) }
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
                !is_offline_failure(result.exceptionOrNull())
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
                        total = page.total ?: _inbox_state.value.total,
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
        if (!foreground_check()) return
        val s = _inbox_state.value
        if (s.is_loading || s.is_loading_more) return
        silent_revalidate(s.current_folder)
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
                    total = page.total ?: _inbox_state.value.total,
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
        if (state.is_loading || !state.has_more) return
        if (state.is_loading_more && load_more_job?.isActive == true) return
        if (load_more_failures >= LOAD_MORE_FAILURE_LIMIT) return
        var cursor = state.next_cursor ?: return
        val started_folder = state.current_folder
        _inbox_state.update { it.copy(is_loading_more = true) }
        val load_more_gen = ++load_more_generation
        load_more_job = viewModelScope.launch {
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
                    load_more_failures++
                    if (pages_scanned > 0) {
                        _inbox_state.update { it.copy(is_loading_more = false, next_cursor = cursor) }
                    } else {
                        _inbox_state.update { it.copy(is_loading_more = false) }
                        emit_toast(context.getString(R.string.failed_to_load))
                    }
                    return@launch
                }
                load_more_failures = 0
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
        load_more_job?.invokeOnCompletion {
            if (load_more_gen == load_more_generation && _inbox_state.value.is_loading_more) {
                _inbox_state.update { it.copy(is_loading_more = false) }
            }
        }
    }

    fun cancel_load_all_remaining() {
        load_all_remaining_job?.cancel()
        load_all_remaining_job = null
    }

    fun load_all_remaining(on_complete: (() -> Unit)? = null) {
        load_all_remaining_job?.cancel()
        load_all_remaining_job = viewModelScope.launch {
            val started_folder = _inbox_state.value.current_folder
            var pages = 0
            var stalls = 0
            while (pages < LOAD_ALL_MAX_PAGES) {
                val s = _inbox_state.value
                if (s.current_folder != started_folder) return@launch
                if (!s.has_more) break
                if (s.items.size >= LOAD_ALL_MAX_ITEMS) break
                if (s.is_loading || s.is_loading_more) {
                    kotlinx.coroutines.delay(50)
                    continue
                }
                val before_cursor = s.next_cursor
                val before_count = s.items.size
                load_more()
                var waited = 0
                while (_inbox_state.value.is_loading_more && waited < LOAD_ALL_PAGE_WAIT_TICKS) {
                    if (_inbox_state.value.current_folder != started_folder) return@launch
                    kotlinx.coroutines.delay(25)
                    waited++
                }
                val after = _inbox_state.value
                if (after.current_folder != started_folder) return@launch
                if (after.next_cursor == before_cursor && after.items.size == before_count) {
                    stalls++
                    if (stalls >= LOAD_ALL_MAX_STALLS) break
                    kotlinx.coroutines.delay(500)
                    continue
                }
                stalls = 0
                pages++
            }
            if (_inbox_state.value.current_folder == started_folder) on_complete?.invoke()
        }
    }

    fun get_user_email(): String? = repository.get_user_email()

    fun load_stats(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && _inbox_state.value.stats != null && now - last_stats_load_ms < STATS_TTL_MS) return
        stats_job?.cancel()
        stats_job = viewModelScope.launch {
            delay(STATS_DEBOUNCE_MS)
            last_stats_load_ms = System.currentTimeMillis()
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
                        error = org.astermail.android.api.user_facing_error(t, context.getString(R.string.something_went_wrong)),
                    )
                },
            )
        }
    }

    private val _decrypt_retry_active = MutableStateFlow(false)
    val decrypt_retry_active: StateFlow<Boolean> = _decrypt_retry_active

    fun retry_decrypt_thread() {
        val item_id = _thread_state.value.item?.id ?: return
        if (_decrypt_retry_active.value) return
        _decrypt_retry_active.value = true
        repository.begin_decrypt_retry()
        load_thread(item_id)
        viewModelScope.launch {
            val deadline = System.currentTimeMillis() + DECRYPT_RETRY_TIMEOUT_MS
            while (System.currentTimeMillis() < deadline && _thread_state.value.is_loading) {
                kotlinx.coroutines.delay(120)
            }
            _decrypt_retry_active.value = false
            if (_thread_state.value.messages.any { it.is_undecryptable }) {
                emit_toast(context.getString(R.string.decrypt_retry_failed))
            }
        }
    }

    @Volatile
    private var send_guard_until = 0L

    fun refresh_current_thread() {
        _thread_state.value.item?.id?.let { load_thread(it) }
    }

    private fun refresh_thread_after_send() {
        send_guard_until = System.currentTimeMillis() + SEND_GUARD_WINDOW_MS
        if (_thread_state.value.item == null) return
        viewModelScope.launch {
            val before_ids = _thread_state.value.messages.map { it.id }.toSet()
            refresh_current_thread()
            repeat(3) { attempt ->
                kotlinx.coroutines.delay(if (attempt == 0) 1_500L else 4_000L)
                val cur = _thread_state.value
                if (cur.item == null) return@launch
                if (cur.messages.any { it.id !in before_ids }) return@launch
                refresh_current_thread()
            }
        }
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
                val previous = if (cur_thread.item?.id == item_id) cur_thread.messages else emptyList()
                result.fold(
                    onSuccess = { messages ->
                        val base = if (messages.isEmpty()) previous.ifEmpty { fallback } else messages
                        val resolved = if (
                            previous.isNotEmpty() &&
                            System.currentTimeMillis() < send_guard_until
                        ) {
                            val server_ids = base.map { it.id }.toSet()
                            base + previous.filter { it.id !in server_ids }
                        } else {
                            base
                        }
                        _thread_state.value = ThreadUiState(
                            messages = resolved,
                            item = item,
                            attachments = if (cur_thread.item?.id == item_id) cur_thread.attachments else emptyMap(),
                        )
                        cache_thread_participants(thread_token, resolved)
                        load_attachments_for_thread(resolved)
                        load_reactions(resolved)
                    },
                    onFailure = { t ->
                        val kept = previous.ifEmpty { fallback }
                        _thread_state.value = ThreadUiState(
                            messages = kept,
                            error = org.astermail.android.api.user_facing_error(t, context.getString(R.string.something_went_wrong)),
                            item = item,
                            attachments = if (cur_thread.item?.id == item_id) cur_thread.attachments else emptyMap(),
                        )
                        cache_thread_participants(thread_token, kept)
                        load_attachments_for_thread(kept)
                    },
                )
            } else if (item != null) {
                val msgs = listOf(single_message_from_item(item))
                _thread_state.value = ThreadUiState(
                    messages = msgs,
                    item = item,
                    attachments = if (cur_thread.item?.id == item_id) cur_thread.attachments else emptyMap(),
                )
                load_attachments_for_thread(msgs)
                load_reactions(msgs)
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
        val thread_item = thread_item_from_mail_item(item.raw_item)
        return repository.decrypt_single_thread_message(thread_item)
    }

    private fun seed_message_from_inbox_item(item: InboxItem): ThreadMessageDecrypted {
        val thread_item = thread_item_from_mail_item(item.raw_item)
        return ThreadMessageDecrypted(
            id = item.id,
            sender_name = item.sender_name,
            sender_email = item.sender_email,
            to_label = "",
            timestamp = item.timestamp,
            body_text = "",
            body_html = null,
            is_encrypted = item.is_encrypted,
            is_read = item.is_read,
            raw_item = thread_item,
            has_attachments = item.has_attachments,
            subject = item.subject,
            display_sender_name = item.display_sender_name,
            display_sender_email = item.display_sender_email,
            is_body_pending = true,
        )
    }

    private fun load_attachments_for_thread(messages: List<ThreadMessageDecrypted>) {
        val ids = messages.map { it.id }
        if (ids.isEmpty()) return
        val expected_ids = ids.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            val metas = repository.fetch_attachment_metas_for_messages(ids)
            if (metas.isEmpty()) return@launch
            _thread_state.update { state ->
                if (state.messages.none { it.id in expected_ids }) return@update state
                val fresh = metas.filterKeys { k ->
                    state.attachments[k].orEmpty().none { a -> a.encrypted_data != null }
                }
                state.copy(attachments = state.attachments + fresh)
            }
            val results = metas.keys.map { id ->
                async { id to repository.fetch_attachments_for_message(id) }
            }.awaitAll().toMap().filter { it.value.isNotEmpty() }
            if (results.isEmpty()) return@launch
            _thread_state.update { state ->
                if (state.messages.none { it.id in expected_ids }) return@update state
                state.copy(attachments = state.attachments + results)
            }
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
                        attachment.mail_item_id,
                        attachment.seq_num,
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

    fun mark_thread_read(item_id: String, message_ids: List<String>) {
        val thread_token = _inbox_state.value.items.find { it.id == item_id }?.thread_token
            ?: folder_cache.values.firstNotNullOfOrNull { c -> c.items.find { it.id == item_id } }?.thread_token
        mark_read(item_id)
        val siblings = if (thread_token.isNullOrBlank()) {
            emptyList()
        } else {
            (_inbox_state.value.items + folder_cache.values.flatMap { it.items })
                .filter { it.thread_token == thread_token && !it.is_read }
                .map { it.id }
        }
        val extra = (message_ids + siblings)
            .filter { it != item_id && it != DEMO_PHISH_ITEM_ID }
            .distinct()
        val thread = _thread_state.value
        val metadata_unread = thread.messages
            .filter { !it.is_read && it.id != item_id && it.id != DEMO_PHISH_ITEM_ID }
            .map { it.raw_item }
        if (thread.messages.any { !it.is_read }) {
            _thread_state.value = thread.copy(
                messages = thread.messages.map { if (it.is_read) it else it.copy(is_read = true) },
            )
        }
        if (metadata_unread.isNotEmpty()) {
            viewModelScope.launch {
                metadata_unread.forEach { repository.mark_thread_message_read(it, true) }
            }
        }
        if (!thread_token.isNullOrBlank()) {
            viewModelScope.launch {
                if (repository.mark_thread_read_all(thread_token).isFailure) {
                    kotlinx.coroutines.delay(1500L)
                    repository.mark_thread_read_all(thread_token)
                }
            }
        }
        if (extra.isEmpty()) return
        MailPollingWorker.cancel_message_notifications(context, extra)
        extra.forEach { read_overrides[it] = true }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in extra) it.copy(is_read = true) else it
            },
        )
        folder_cache.replaceAll { _, cached ->
            cached.copy(items = cached.items.map {
                if (it.id in extra) it.copy(is_read = true) else it
            })
        }
        viewModelScope.launch {
            runCatching { extra.forEach { search_index_manager.update_read(it, true) } }
            var result = repository.mark_read_bulk(extra)
            if (result.isFailure) {
                kotlinx.coroutines.delay(1500L)
                result = repository.mark_read_bulk(extra)
            }
            if (result.isFailure) {
                extra.forEach { repository.mark_read(it, true) }
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
            ?: _search_state.value.all_items.find { it.id == item_id }
            ?: return
        val new_starred = !current.is_starred
        star_overrides[item_id] = new_starred
        _search_state.value = _search_state.value.copy(
            all_items = _search_state.value.all_items.map {
                if (it.id == item_id) it.copy(is_starred = new_starred) else it
            },
        )
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
                    emit_toast(org.astermail.android.api.user_facing_error(t, context.getString(R.string.couldnt_snooze)))
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
                    emit_toast(org.astermail.android.api.user_facing_error(it, context.getString(R.string.couldnt_apply_label)))
                },
            )
        }
    }

    fun remove_label(item_id: String, label_token: String, display_name: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val prev_inbox_labels = _inbox_state.value.items.find { it.id == item_id }?.labels
        val prev_thread_labels = _thread_state.value.item?.takeIf { it.id == item_id }?.labels
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) it.copy(labels = it.labels - label_token) else it
            },
        )
        val thread = _thread_state.value
        if (thread.item?.id == item_id) {
            _thread_state.value = thread.copy(
                item = thread.item.copy(labels = thread.item.labels - label_token),
            )
        }
        viewModelScope.launch {
            repository.remove_label_from_item(item_id, label_token).fold(
                onSuccess = {
                    invalidate_caches(listOf("label:$label_token"))
                    emit_toast(context.getString(R.string.removed_from_label, display_name))
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
                    emit_toast(org.astermail.android.api.user_facing_error(it, context.getString(R.string.couldnt_remove_label)))
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
        patch_cached_tag_tokens(setOf(item_id), tag_token)
        viewModelScope.launch {
            repository.add_tag_to_item(item_id, tag_token).fold(
                onSuccess = {
                    invalidate_caches(listOf("tag:$tag_token"))
                    emit_toast(context.getString(R.string.added_to_label, display_name))
                },
                onFailure = {
                    patch_cached_tag_tokens(setOf(item_id), tag_token, add = false)
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
                    emit_toast(org.astermail.android.api.user_facing_error(it, context.getString(R.string.couldnt_apply_label)))
                },
            )
        }
    }

    fun remove_tag(item_id: String, tag_token: String, display_name: String) {
        if (item_id == DEMO_PHISH_ITEM_ID) return
        val prev_inbox_item = _inbox_state.value.items.find { it.id == item_id }
        val prev_thread_item = _thread_state.value.item?.takeIf { it.id == item_id }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id == item_id) {
                    val new_tokens = it.tag_tokens - tag_token
                    it.copy(
                        tag_tokens = new_tokens,
                        raw_item = it.raw_item.copy(tag_tokens = new_tokens),
                    )
                } else it
            },
        )
        val thread = _thread_state.value
        if (thread.item?.id == item_id) {
            val new_tokens = thread.item.tag_tokens - tag_token
            _thread_state.value = thread.copy(
                item = thread.item.copy(
                    tag_tokens = new_tokens,
                    raw_item = thread.item.raw_item.copy(tag_tokens = new_tokens),
                ),
            )
        }
        patch_cached_tag_tokens(setOf(item_id), tag_token, add = false)
        viewModelScope.launch {
            repository.remove_tag_from_item(item_id, tag_token).fold(
                onSuccess = {
                    invalidate_caches(listOf("tag:$tag_token"))
                    emit_toast(context.getString(R.string.removed_from_label, display_name))
                },
                onFailure = {
                    patch_cached_tag_tokens(setOf(item_id), tag_token)
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
                    emit_toast(org.astermail.android.api.user_facing_error(it, context.getString(R.string.couldnt_remove_label)))
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
            val failed_ids = repository.add_label_bulk(ids, label_token)
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

    fun remove_label_bulk(item_ids: List<String>, label_token: String, display_name: String) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        val id_set = ids.toSet()
        val prev_items = _inbox_state.value.items.filter { it.id in id_set }.associateBy { it.id }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in id_set) it.copy(labels = it.labels - label_token) else it
            },
        )
        viewModelScope.launch {
            val failed_ids = repository.remove_label_bulk(ids, label_token)
            invalidate_caches(listOf("label:$label_token"))
            if (failed_ids.isEmpty()) {
                emit_toast(context.getString(R.string.removed_from_label, display_name))
            } else {
                _inbox_state.value = _inbox_state.value.copy(
                    items = _inbox_state.value.items.map {
                        val prev = prev_items[it.id]
                        if (it.id in failed_ids && prev != null) it.copy(labels = prev.labels) else it
                    },
                )
                emit_toast(context.getString(R.string.couldnt_remove_label))
            }
        }
    }

    fun unsnooze_bulk(item_ids: List<String>) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val failed_ids = run_bulk_action(ids) { id ->
                repository.unsnooze(id).isSuccess
            }
            val ok_ids = ids.filter { it !in failed_ids }.toSet()
            if (ok_ids.isNotEmpty()) {
                _inbox_state.value = _inbox_state.value.copy(
                    items = _inbox_state.value.items.filter { it.id !in ok_ids },
                )
                invalidate_caches(listOf("inbox", "snoozed"))
                load_stats(force = true)
            }
            if (failed_ids.isNotEmpty()) {
                emit_toast(context.getString(R.string.couldnt_unsnooze))
            } else {
                emit_toast(context.getString(R.string.unsnoozed_count, ok_ids.size))
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
        patch_cached_tag_tokens(id_set, tag_token)
        viewModelScope.launch {
            val failed_ids = repository.add_tag_bulk(ids, tag_token)
            invalidate_caches(listOf("tag:$tag_token"))
            if (failed_ids.isEmpty()) {
                emit_toast(context.getString(R.string.added_to_label, display_name))
            } else {
                patch_cached_tag_tokens(failed_ids.toSet(), tag_token, add = false)
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

    fun remove_tag_bulk(item_ids: List<String>, tag_token: String, display_name: String) {
        val ids = item_ids.filter { it != DEMO_PHISH_ITEM_ID }
        if (ids.isEmpty()) return
        val id_set = ids.toSet()
        val prev_items = _inbox_state.value.items.filter { it.id in id_set }.associateBy { it.id }
        _inbox_state.value = _inbox_state.value.copy(
            items = _inbox_state.value.items.map {
                if (it.id in id_set) {
                    val new_tokens = it.tag_tokens - tag_token
                    it.copy(
                        tag_tokens = new_tokens,
                        raw_item = it.raw_item.copy(tag_tokens = new_tokens),
                    )
                } else it
            },
        )
        patch_cached_tag_tokens(id_set, tag_token, add = false)
        viewModelScope.launch {
            val failed_ids = repository.remove_tag_bulk(ids, tag_token)
            invalidate_caches(listOf("tag:$tag_token"))
            if (failed_ids.isEmpty()) {
                emit_toast(context.getString(R.string.removed_from_label, display_name))
            } else {
                patch_cached_tag_tokens(failed_ids.toSet(), tag_token)
                _inbox_state.value = _inbox_state.value.copy(
                    items = _inbox_state.value.items.map {
                        val prev = prev_items[it.id]
                        if (it.id in failed_ids && prev != null) prev else it
                    },
                )
                emit_toast(context.getString(R.string.couldnt_remove_label))
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
            val failed = repository.star_bulk(ids, new_starred, ids.map { raw_by_id[it] }).isFailure
            if (failed) {
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

    private fun adjust_stats_for_removed(removed_items: List<InboxItem>) {
        val unread_removed = removed_items.count { !it.is_read }
        if (unread_removed == 0) return
        _inbox_state.update { s ->
            val stats = s.stats ?: return@update s
            s.copy(stats = stats.copy(unread = (stats.unread - unread_removed).coerceAtLeast(0)))
        }
    }

    private fun patch_cached_tag_tokens(ids: Set<String>, tag_token: String, add: Boolean = true) {
        val current_folder = _inbox_state.value.current_folder
        val updated = folder_cache.mapValues { (folder, cached) ->
            if (folder == current_folder) cached
            else cached.copy(
                items = cached.items.map {
                    if (it.id !in ids) it
                    else {
                        val new_tokens =
                            if (add) (it.tag_tokens + tag_token).distinct()
                            else it.tag_tokens.filter { token -> token != tag_token }
                        it.copy(
                            tag_tokens = new_tokens,
                            raw_item = it.raw_item.copy(tag_tokens = new_tokens),
                        )
                    }
                },
            )
        }
        folder_cache.putAll(updated)
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

    private fun archived_message(count: Int, message_scope: Boolean): String =
        archived_action_message(context, count, message_scope)

    private fun trashed_message(count: Int, message_scope: Boolean): String =
        trashed_action_message(context, count, message_scope)

    fun archive(item_ids: List<String>, thread_count: Int = 1, message_scope: Boolean = false) {
        val had_demo = DEMO_PHISH_ITEM_ID in item_ids
        @Suppress("NAME_SHADOWING") val item_ids = handle_demo_in(item_ids)
        if (item_ids.isEmpty()) {
            if (had_demo) emit_toast(archived_message(1, message_scope))
            return
        }
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        val raw_items = lookup_raw_items(item_ids)
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        adjust_stats_for_removed(removed_items)
        val search_removed = remove_search_items(item_ids)
        pending_removed_ids.addAll(item_ids)
        val affected_label_caches = removed_items.flatMap { it.labels }.map { "label:$it" }
        val affected_tag_caches = removed_items.flatMap { it.tag_tokens }.map { "tag:$it" }
        invalidate_caches(listOf("archive", "inbox") + all_mail_folder_ids + affected_label_caches + affected_tag_caches)
        viewModelScope.launch {
            try {
                repository.archive(item_ids, raw_items).fold(
                    onSuccess = {
                        runCatching { search_index_manager.mark_archived(item_ids) }
                        accumulate_batch_action(
                            action_key = batch_action_key("archive", message_scope),
                            thread_count = thread_count,
                            message_fn = { n -> archived_message(n, message_scope) },
                            undo_label = context.getString(R.string.undo),
                        ) { prev_undo ->
                            {
                                prev_undo?.invoke()
                                undo_restore(
                                    removed_items = removed_items,
                                    search_removed = search_removed,
                                    item_ids = item_ids,
                                    reindex = { search_index_manager.mark_unarchived(it) },
                                    restore = { repository.unarchive(it, lookup_raw_items(it)) },
                                )
                            }
                        }
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

    fun trash(item_ids: List<String>, thread_count: Int = 1, message_scope: Boolean = false) {
        if (_inbox_state.value.current_folder == "drafts") {
            delete_draft_items(item_ids)
            return
        }
        val had_demo = DEMO_PHISH_ITEM_ID in item_ids
        @Suppress("NAME_SHADOWING") val item_ids = handle_demo_in(item_ids)
        if (item_ids.isEmpty()) {
            if (had_demo) emit_toast(trashed_message(1, message_scope))
            return
        }
        val previous = _inbox_state.value.items
        val removed_items = previous.filter { it.id in item_ids }
        val raw_items = lookup_raw_items(item_ids)
        _inbox_state.value = _inbox_state.value.copy(
            items = previous.filter { it.id !in item_ids },
        )
        adjust_stats_for_removed(removed_items)
        val search_removed = remove_search_items(item_ids)
        pending_removed_ids.addAll(item_ids)
        invalidate_caches(listOf("trash", "inbox"))
        viewModelScope.launch {
            try {
                repository.trash(item_ids, raw_items).fold(
                    onSuccess = {
                        runCatching { search_index_manager.mark_trashed(item_ids) }
                        accumulate_batch_action(
                            action_key = batch_action_key("trash", message_scope),
                            thread_count = thread_count,
                            message_fn = { n -> trashed_message(n, message_scope) },
                            undo_label = context.getString(R.string.undo),
                        ) { prev_undo ->
                            {
                                prev_undo?.invoke()
                                undo_restore(
                                    removed_items = removed_items,
                                    search_removed = search_removed,
                                    item_ids = item_ids,
                                    reindex = { search_index_manager.mark_restored(it) },
                                    restore = { repository.restore_trash(it) },
                                )
                            }
                        }
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

    suspend fun load_thread_draft(thread_token: String): InboxItem? =
        repository.fetch_thread_draft(thread_token)

    fun delete_thread_draft(draft_id: String, on_done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.delete_draft(draft_id).isSuccess
            if (ok) {
                load_stats()
                emit_toast(context.getString(R.string.draft_deleted, 1))
            } else {
                emit_toast(context.getString(R.string.failed_to_delete_draft))
            }
            on_done(ok)
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
                            undo_restore(
                                removed_items = removed_items,
                                search_removed = emptyList(),
                                item_ids = item_ids,
                                reindex = { search_index_manager.mark_unspam(it) },
                                restore = { repository.unmark_spam(it) },
                            )
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
                        runCatching { search_index_manager.mark_unspam(item_ids) }
                        val remove_job =
                            viewModelScope.launch { repository.remove_spam_senders(sender_emails) }
                        emit_toast_undo(
                            context.getString(R.string.moved_to_inbox),
                            context.getString(R.string.undo),
                        ) {
                            undo_restore(
                                removed_items = removed_items,
                                search_removed = emptyList(),
                                item_ids = item_ids,
                                reindex = { search_index_manager.mark_spam(it) },
                                restore = { repository.mark_spam(it, lookup_raw_items(it)) },
                            )
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
        load_stats(force = true)
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

    private fun protect_restored(item_ids: List<String>) {
        val until = System.currentTimeMillis() + RESTORE_PROTECTION_MS
        item_ids.forEach { restore_protected_until[it] = until }
    }

    private fun restore_protected(item_id: String, now: Long): Boolean {
        val until = restore_protected_until[item_id] ?: return false
        if (now > until) {
            restore_protected_until.remove(item_id)
            return false
        }
        return true
    }

    private fun undo_restore(
        removed_items: List<InboxItem>,
        search_removed: List<InboxItem>,
        item_ids: List<String>,
        reindex: suspend (List<String>) -> Unit,
        restore: suspend (List<String>) -> Unit,
    ) {
        undo_local_restore(removed_items)
        undo_search_restore(search_removed)
        if (item_ids.isEmpty()) return
        protect_restored(item_ids)
        viewModelScope.launch {
            runCatching { restore(item_ids) }
            runCatching { reindex(item_ids) }
            load_inbox(_inbox_state.value.current_folder, force = true)
            load_stats(force = true)
        }
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
                        ) {
                            undo_restore(
                                removed_items = removed_items,
                                search_removed = emptyList(),
                                item_ids = item_ids,
                                reindex = { search_index_manager.mark_archived(it) },
                                restore = { repository.archive(it, lookup_raw_items(it)) },
                            )
                        }
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
                        runCatching { search_index_manager.mark_restored(item_ids) }
                        emit_toast_undo(
                            context.getString(R.string.restored_to_inbox),
                            context.getString(R.string.undo),
                        ) {
                            undo_restore(
                                removed_items = removed_items,
                                search_removed = emptyList(),
                                item_ids = item_ids,
                                reindex = { search_index_manager.mark_trashed(it) },
                                restore = { repository.trash(it, lookup_raw_items(it)) },
                            )
                        }
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

    fun folder_supports_scope_selection(folder: String): Boolean =
        repository.folder_supports_bulk_scope(folder)

    fun action_supports_scope_selection(action: String): Boolean =
        repository.action_supports_bulk_scope(action)

    fun bulk_scope_action(folder: String, action: String, on_failure: (() -> Unit)? = null) {
        val prior = _inbox_state.value
        val snapshot = prior.items
        val removes = action != "mark_read" && action != "mark_unread"
        if (removes) {
            _inbox_state.update { it.copy(items = emptyList(), has_more = false, next_cursor = null) }
            adjust_stats_for_removed(snapshot)
        } else {
            val read = action == "mark_read"
            _inbox_state.update { s -> s.copy(items = s.items.map { it.copy(is_read = read) }) }
            snapshot.forEach { read_overrides[it.id] = read }
        }
        viewModelScope.launch {
            repository.bulk_scope_action(folder, action).fold(
                onSuccess = {
                    invalidate_caches(listOf(folder))
                    load_stats(force = true)
                    refresh()
                },
                onFailure = {
                    _inbox_state.update {
                        it.copy(
                            items = snapshot,
                            has_more = prior.has_more,
                            next_cursor = prior.next_cursor,
                        )
                    }
                    load_stats(force = true)
                    if (on_failure != null) {
                        on_failure()
                    } else {
                        emit_toast(context.getString(R.string.action_failed))
                    }
                },
            )
        }
    }

    fun notify_partial_scope_selection(applied: Int, total: Int) {
        emit_toast(context.getString(R.string.applied_to_loaded_only, applied, total))
    }

    fun star_scope(folder: String, is_starred: Boolean) {
        val prior = _inbox_state.value
        val snapshot = prior.items
        val removes = folder == "starred" && !is_starred
        if (removes) {
            _inbox_state.update { it.copy(items = emptyList(), has_more = false, next_cursor = null) }
        } else {
            _inbox_state.update { s -> s.copy(items = s.items.map { it.copy(is_starred = is_starred) }) }
            snapshot.forEach { star_overrides[it.id] = is_starred }
        }
        viewModelScope.launch {
            repository.star_scope(folder, is_starred).fold(
                onSuccess = { response ->
                    invalidate_caches(listOf(folder, "starred"))
                    load_stats(force = true)
                    refresh()
                    emit_toast(
                        if (is_starred) context.getString(R.string.starred_count, response.affected_count)
                        else context.getString(R.string.unstarred_count, response.affected_count),
                    )
                },
                onFailure = {
                    snapshot.forEach { star_overrides.remove(it.id) }
                    _inbox_state.update {
                        it.copy(
                            items = snapshot,
                            has_more = prior.has_more,
                            next_cursor = prior.next_cursor,
                        )
                    }
                    emit_toast(context.getString(R.string.failed_to_update_selection))
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
        val previous_state = _inbox_state.value
        val previous_cache = folder_cache["trash"]
        val previous_cache_time = folder_cache_time["trash"]
        val viewing_trash = previous_state.current_folder == "trash"
        val trash_count = previous_state.stats?.trash
        val known_empty = trash_count != null && trash_count <= 0
        val viewed_empty = viewing_trash &&
            previous_state.items.isEmpty() &&
            !previous_state.has_more &&
            !previous_state.is_loading
        if (known_empty || viewed_empty) {
            emit_toast(context.getString(R.string.trash_already_empty))
            return
        }
        if (viewing_trash) {
            _inbox_state.value = previous_state.copy(
                items = emptyList(),
                is_loading = false,
                is_loading_more = false,
                is_refreshing = false,
                initial = false,
                error = null,
                has_more = false,
                next_cursor = null,
                total = 0,
            )
        }
        folder_cache["trash"] = (previous_cache ?: InboxUiState(current_folder = "trash")).copy(
            items = emptyList(),
            is_loading = false,
            is_loading_more = false,
            is_refreshing = false,
            initial = false,
            error = null,
            has_more = false,
            next_cursor = null,
            total = 0,
        )
        folder_cache_time["trash"] = System.currentTimeMillis()
        emit_toast(context.getString(R.string.trash_emptied))
        viewModelScope.launch {
            repository.empty_trash().fold(
                onSuccess = { load_stats() },
                onFailure = {
                    if (previous_cache != null) {
                        folder_cache["trash"] = previous_cache
                    } else {
                        folder_cache.remove("trash")
                    }
                    if (previous_cache_time != null) {
                        folder_cache_time["trash"] = previous_cache_time
                    } else {
                        folder_cache_time.remove("trash")
                    }
                    if (_inbox_state.value.current_folder == "trash") {
                        _inbox_state.value = previous_state
                    }
                    emit_toast(context.getString(R.string.failed_empty_trash))
                },
            )
        }
    }

    fun empty_spam() {
        if (_emptying_spam.value) return
        val previous_state = _inbox_state.value
        val spam_count = previous_state.stats?.spam
        if (spam_count != null && spam_count <= 0) {
            emit_toast(context.getString(R.string.spam_already_empty))
            return
        }
        _emptying_spam.value = true
        viewModelScope.launch {
            repository.empty_spam().fold(
                onSuccess = { deleted ->
                    folder_cache.remove("spam")
                    folder_cache_time.remove("spam")
                    if (_inbox_state.value.current_folder == "spam") {
                        _inbox_state.value = _inbox_state.value.copy(
                            items = emptyList(),
                            is_loading = false,
                            is_loading_more = false,
                            is_refreshing = false,
                            initial = false,
                            error = null,
                            has_more = false,
                            next_cursor = null,
                            total = 0,
                        )
                    }
                    _emptying_spam.value = false
                    emit_toast(
                        if (deleted == 0) context.getString(R.string.spam_already_empty)
                        else context.getString(R.string.spam_emptied),
                    )
                    load_stats()
                },
                onFailure = {
                    _emptying_spam.value = false
                    emit_toast(context.getString(R.string.failed_empty_spam))
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
                    search_index_manager.refresh_index_and_wait()
                    val refreshed = search_index_manager.get_cached_items()
                    if (refreshed.isNotEmpty()) {
                        _search_state.value = _search_state.value.copy(
                            all_items = refreshed.map { it.to_inbox_item() },
                        )
                    }
                } else {
                    search_index_manager.ensure_index_built()
                    repository.fetch_all_for_search().fold(
                        onSuccess = { items ->
                            search_index_manager.on_items_loaded(items)
                            _search_state.value = SearchUiState(
                                all_items = items,
                                is_indexed = true,
                            )
                            val with_attachments = search_index_manager.resolve_attachment_ids(
                                items.filterNot { it.has_attachments }.map { it.id },
                            )
                            if (with_attachments.isNotEmpty()) {
                                _search_state.value = _search_state.value.copy(
                                    all_items = _search_state.value.all_items.map {
                                        if (it.id in with_attachments) it.copy(has_attachments = true) else it
                                    },
                                )
                            }
                        },
                        onFailure = { t ->
                            val keep = _search_state.value.all_items
                            _search_state.value = _search_state.value.copy(
                                is_indexing = false,
                                error = if (keep.isNotEmpty()) null else (org.astermail.android.api.user_facing_error(t, context.getString(R.string.something_went_wrong))),
                            )
                        },
                    )
                }
            } catch (t: Throwable) {
                val keep = _search_state.value.all_items
                _search_state.value = _search_state.value.copy(
                    is_indexing = false,
                    error = if (keep.isNotEmpty()) null else (org.astermail.android.api.user_facing_error(t, context.getString(R.string.something_went_wrong))),
                )
            }
        }
    }

    fun seed_inbox_attachment_flags() {
        if (inbox_attachment_seeded.getAndSet(true)) return
        val gen = account_generation
        viewModelScope.launch(Dispatchers.IO) {
            val known = search_index_manager.known_attachment_ids()
            if (gen == account_generation && known.isNotEmpty()) {
                _inbox_attachment_ids.update { it + known }
            }
        }
    }

    fun resolve_inbox_attachment_flags(item_ids: List<String>) {
        if (item_ids.isEmpty()) return
        val gen = account_generation
        seed_inbox_attachment_flags()
        val to_probe = item_ids.filter { inbox_attachment_probed.add(it) }
        if (to_probe.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var pending = to_probe
            var backoff_ms = 1_500L
            var attempt = 0
            while (pending.isNotEmpty() && attempt < inbox_attachment_probe_retries) {
                val result = try {
                    search_index_manager.probe_attachment_ids(pending)
                } catch (t: kotlin.coroutines.cancellation.CancellationException) {
                    throw t
                } catch (_: Throwable) {
                    SearchIndexManager.AttachmentProbeResult(emptySet(), pending)
                }
                if (gen != account_generation) return@launch
                if (result.found.isNotEmpty()) _inbox_attachment_ids.update { it + result.found }
                pending = result.failed
                if (pending.isEmpty()) return@launch
                attempt++
                delay(backoff_ms)
                backoff_ms *= 2
            }
            inbox_attachment_probed.removeAll(pending.toSet())
        }
    }

    fun refresh() {
        val folder = _inbox_state.value.current_folder
        if (_inbox_state.value.is_refreshing && refresh_job?.isActive == true) return
        val gen = account_generation
        load_more_failures = 0
        folder_cache.remove(folder)
        folder_cache_time.remove(folder)
        _inbox_state.update { it.copy(is_refreshing = true) }
        load_stats()
        val refresh_gen = ++refresh_generation
        inbox_load_job?.cancel()
        silent_revalidate_job?.cancel()
        refresh_job?.cancel()
        refresh_job = viewModelScope.launch {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(PULL_REFRESH_BACKSTOP_MS) {
                    fetch_for_folder(folder).getOrThrow()
                }
            }
            if (account_generation != gen) return@launch
            if (refresh_gen != refresh_generation) return@launch
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
                        total = page.total ?: _inbox_state.value.total,
                    )
                    folder_cache[folder] = _inbox_state.value
                    folder_cache_time[folder] = System.currentTimeMillis()
                    search_index_manager.on_items_loaded(page.items)
                    search_index_manager.ensure_index_built()
                },
                onFailure = { t ->
                    _inbox_state.update {
                        it.copy(
                            is_refreshing = false,
                            is_loading = false,
                            initial = false,
                            error = if (it.items.isEmpty()) friendly_load_error(t) else null,
                        )
                    }
                },
            )
        }
        refresh_job?.invokeOnCompletion {
            if (refresh_gen == refresh_generation && _inbox_state.value.is_refreshing) {
                _inbox_state.update { it.copy(is_refreshing = false) }
            }
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
        if (result.isSuccess && result.getOrNull()?.success == true) {
            invalidate_caches(listOf("sent", "drafts"))
            repository.notify_send_success()
        }
        return result
    }

    val search_index_progress: StateFlow<IndexProgress?> = search_index_manager.index_progress

    val search_index_paused: StateFlow<Boolean> = search_index_manager.index_paused

    fun pause_search_indexing() {
        search_index_manager.pause_indexing()
    }

    fun resume_search_indexing() {
        search_index_manager.resume_indexing()
    }

    val pending_undo_send: StateFlow<MailRepository.PendingUndoSend?> = repository.pending_undo_send

    val send_problem: StateFlow<Boolean> = repository.send_problem

    val failed_send_count: StateFlow<Int> = repository.failed_send_count

    fun dismiss_send_problem() {
        repository.clear_send_problem()
    }

    fun retry_failed_sends() {
        viewModelScope.launch { runCatching { repository.retry_failed_sends() } }
    }

    fun discard_failed_sends() {
        viewModelScope.launch { runCatching { repository.discard_failed_sends() } }
    }

    init {
        seed_inbox_attachment_flags()
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
                    refresh_thread_after_send()
                } else {
                    emit_toast(
                        if (result.exceptionOrNull() is TransientSendException) {
                            context.getString(R.string.send_still_trying)
                        } else {
                            context.getString(R.string.send_problem_failed_message)
                        },
                    )
                }
            }
        }
    }

    suspend fun schedule_send_with_undo(
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
    ): Result<String> {
        return repository.schedule_send_with_undo(
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
        draft_type: String = "new",
        reply_to_id: String? = null,
        thread_token: String? = null,
    ): Result<String> {
        val result = repository.save_draft(
            subject = subject,
            body_html = body_html,
            sender_email = sender_email,
            to = to,
            cc = cc,
            existing_draft_id = existing_draft_id,
            draft_type = draft_type,
            reply_to_id = reply_to_id,
            thread_token = thread_token,
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
        draft_type: String = "new",
        reply_to_id: String? = null,
        thread_token: String? = null,
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
                    draft_type = draft_type,
                    reply_to_id = reply_to_id,
                    thread_token = thread_token,
                )
            }
            if (result.isSuccess) {
                runCatching { invalidate_caches(listOf("drafts")) }
                runCatching {
                    emit_toast(context.getString(R.string.email_saved_as_draft))
                }
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

    private fun thread_item_from_mail_item(
        raw: org.astermail.android.api.mail.MailItem,
    ): org.astermail.android.api.mail.ThreadMessageItem =
        org.astermail.android.api.mail.ThreadMessageItem(
            id = raw.id,
            item_type = raw.item_type,
            encrypted_envelope = raw.encrypted_envelope,
            envelope_nonce = raw.envelope_nonce,
            encrypted_metadata = raw.encrypted_metadata,
            metadata_nonce = raw.metadata_nonce,
            metadata_version = raw.metadata_version,
            is_external = raw.is_external,
            has_recipient_key = raw.has_recipient_key,
            ephemeral_key = raw.ephemeral_key,
            ephemeral_pq_key = raw.ephemeral_pq_key,
            send_status = raw.send_status,
            message_ts = raw.message_ts,
            created_at = raw.created_at,
            metadata = raw.metadata,
            spf_result = raw.spf_result,
            dkim_result = raw.dkim_result,
            dmarc_result = raw.dmarc_result,
            is_reaction = raw.is_reaction,
            message_group_id = raw.message_group_id,
            reactions = raw.reactions,
        )

    private suspend fun item_to_single_message(item: InboxItem): ThreadMessageDecrypted {
        val thread_item = thread_item_from_mail_item(item.raw_item)
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

    private fun folder_matches(folder: String, item: InboxItem): Boolean =
        folder_matches_item(folder, item)

    private fun is_timeout_failure(t: Throwable?): Boolean = when (t) {
        null -> false
        is kotlinx.coroutines.TimeoutCancellationException -> true
        is io.ktor.client.plugins.HttpRequestTimeoutException -> true
        is io.ktor.client.network.sockets.ConnectTimeoutException -> true
        is java.net.SocketTimeoutException -> true
        else -> false
    }

    private fun is_offline_failure(t: Throwable?): Boolean = when (t) {
        null -> false
        is io.ktor.client.network.sockets.ConnectTimeoutException -> true
        is java.net.UnknownHostException -> true
        is java.net.ConnectException -> true
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
        limit: Int = page_size,
    ): Result<InboxPage> = when (folder) {
        "inbox" -> repository.fetch_inbox(limit = limit, cursor = cursor, order = list_order)
        "sent" -> repository.fetch_sent(limit = limit, cursor = cursor, order = list_order)
        "drafts" -> repository.fetch_drafts(limit = limit, cursor = cursor)
        "starred" -> repository.fetch_starred(limit = limit, cursor = cursor, order = list_order)
        "trash" -> repository.fetch_trash(limit = limit, cursor = cursor, order = list_order)
        "spam" -> repository.fetch_spam(limit = limit, cursor = cursor, order = list_order)
        "archive" -> repository.fetch_archive(limit = limit, cursor = cursor, order = list_order)
        "scheduled" -> repository.fetch_scheduled(limit = limit, cursor = cursor, order = list_order)
        "snoozed" -> repository.fetch_snoozed(limit = limit, cursor = cursor, order = list_order)
        else -> when {
            is_all_mail_folder(folder) -> repository.fetch_inbox(
                limit = limit,
                cursor = cursor,
                item_type = "all",
                order = list_order,
                include_spam = all_mail_includes_spam(folder),
                include_trash = all_mail_includes_trash(folder),
            )
            folder.startsWith("label:") -> {
                val label_token = folder.removePrefix("label:")
                repository.fetch_inbox(limit = limit, item_type = null, label_token = label_token, offset = cursor?.toIntOrNull(), order = list_order)
            }
            folder.startsWith("tag:") -> {
                val tag_token = folder.removePrefix("tag:")
                repository.fetch_inbox(limit = limit, item_type = null, tag_token = tag_token, offset = cursor?.toIntOrNull(), order = list_order)
            }
            folder.startsWith("routing:") -> {
                val routing_token = folder.removePrefix("routing:")
                repository.fetch_inbox(limit = limit, item_type = null, routing_token = routing_token, offset = cursor?.toIntOrNull(), order = list_order)
            }
            else -> repository.fetch_inbox(limit = limit, item_type = null, label_token = folder, offset = cursor?.toIntOrNull(), order = list_order)
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
    to_addresses = to_addresses?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
    routing_token = routing_token,
    raw_item = org.astermail.android.api.mail.MailItem(
        id = id,
        is_external = is_external,
        has_recipient_key = has_recipient_key,
        thread_token = thread_token,
        routing_token = routing_token,
    ),
)

internal fun folder_matches_item(folder: String, item: InboxItem): Boolean = when (folder) {
    "inbox" -> !item.is_trashed && !item.is_archived && !item.is_spam
    "starred" -> item.is_starred && !item.is_trashed
    "trash" -> item.is_trashed
    "spam" -> item.is_spam
    "archive" -> item.is_archived
    "sent" -> item.raw_item.item_type == "sent" && !item.is_trashed
    "drafts" -> item.raw_item.item_type == "draft" && !item.is_trashed
    "scheduled" -> item.raw_item.item_type == "scheduled" && !item.is_trashed
    "outbox" -> item.raw_item.item_type == "outbox" && !item.is_trashed
    "snoozed" -> !item.is_trashed
    else -> when {
        is_all_mail_folder(folder) ->
            (all_mail_includes_trash(folder) || !item.is_trashed) &&
                (all_mail_includes_spam(folder) || !item.is_spam)
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
