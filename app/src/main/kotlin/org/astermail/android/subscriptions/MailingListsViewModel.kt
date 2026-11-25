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

package org.astermail.android.subscriptions

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.subscriptions.DeleteSubscriptionsRequest
import org.astermail.android.api.subscriptions.MailingListStats
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.api.subscriptions.MailingListsResponse
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.subscriptions.TrackSubscriptionRequest
import org.astermail.android.api.subscriptions.UnsubscribeRequest
import org.astermail.android.ui.mail.UnsubscribeInfo
import org.astermail.android.ui.mail.ProxyUnsubscribeResult
import org.astermail.android.ui.mail.UnsubscribeOutcome
import org.astermail.android.ui.mail.execute_unsubscribe_checked
import org.astermail.android.ui.mail.get_manual_unsubscribe_url

private const val SCAN_TIMEOUT_MS = 90_000L
private const val TRACK_BUDGET_MS = 45_000L
private const val subscription_page_size = 100
private const val max_subscription_pages = 50
private const val PRIME_WAIT_MS = 3_000L
private const val SUCCESS_HOLD_MS = 700L
private const val FAILURE_HOLD_MS = 1_800L
private const val UNSUBSCRIBE_CONCURRENCY = 4
private const val ATTEMPT_TIMEOUT_MS = 140_000L
private const val BATCH_SETTLE_MS = 350L
private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
private const val LIST_CONCURRENCY = 4
private const val TRACK_CONCURRENCY = 6
internal const val PROVISIONAL_PREFIX = "scan:"

data class ManualUnsubscribePrompt(
    val subscription_id: String,
    val sender_email: String,
    val sender_name: String,
    val url: String,
    val list_unsubscribe_header: String? = null,
)

internal sealed interface UnsubscribeAttempt {
    data class done(
        val recorded_on_server: Boolean,
        val list_unsubscribe_header: String? = null,
    ) : UnsubscribeAttempt
    data class manual(val url: String, val list_unsubscribe_header: String? = null) : UnsubscribeAttempt
    data object failed : UnsubscribeAttempt
    data object rate_limited : UnsubscribeAttempt
}

data class MailingListsState(
    val is_loading: Boolean = false,
    val is_scanning: Boolean = false,
    val items: List<MailingListSubscription> = emptyList(),
    val stats: MailingListStats? = null,
    val error: String? = null,
    val load_error: String? = null,
    val pending_ids: Set<String> = emptySet(),
    val succeeded_ids: Set<String> = emptySet(),
    val failed_ids: Set<String> = emptySet(),
    val manual_prompt: ManualUnsubscribePrompt? = null,
    val message: String? = null,
    val scan_scanned: Int = 0,
    val scan_total: Int = 0,
    val scan_found: Int = 0,
)

internal const val BULK_UNSUBSCRIBE_LIMIT = 50
internal const val DELETE_SUBSCRIPTIONS_LIMIT = 500

internal fun is_missing_delete_endpoint(error: Throwable): Boolean =
    error is ApiError.NotFoundError || (error is ApiError.ServerError && error.code == 405)

internal suspend fun delete_subscription_senders(
    senders: List<String>,
    delete_chunk: suspend (List<String>) -> Unit,
): Boolean {
    try {
        senders.chunked(DELETE_SUBSCRIPTIONS_LIMIT).forEach { delete_chunk(it) }
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        if (is_missing_delete_endpoint(t)) return false
        throw t
    }
}

@HiltViewModel
class MailingListsViewModel @Inject constructor(
    private val api: SubscriptionsApi,
    private val scanner: SubscriptionScanner,
    private val unsubscribed_store: UnsubscribedSendersStore,
    private val deleted_store: DeletedSubscriptionsStore,
    private val cache_sync: SubscriptionCacheSync,
    private val info_cache: UnsubscribeInfoCache,
    private val list_memory: SubscriptionListMemory,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MailingListsState())
    val state: StateFlow<MailingListsState> = _state.asStateFlow()

    private var load_job: Job? = null
    private var scan_job: Job? = null
    private var prime_job: Job? = null
    private var flush_job: Job? = null
    private val unsubscribe_gate = Semaphore(UNSUBSCRIBE_CONCURRENCY)
    private val batch = UnsubscribeBatch()
    @Volatile private var rate_limited_until = 0L

    val unsubscribed_tokens: StateFlow<Set<String>> = unsubscribed_store.tokens

    init {
        unsubscribed_store.load_cached()
    }

    fun sender_token(sender_email: String): String = unsubscribed_store.token_for(sender_email)

    fun refresh_unsubscribed() = unsubscribed_store.refresh()

    fun record_unsubscribed(
        sender_email: String,
        sender_name: String?,
        unsubscribe_link: String?,
        list_unsubscribe_header: String?,
    ) = unsubscribed_store.record_unsubscribed(
        sender_email,
        sender_name,
        unsubscribe_link,
        list_unsubscribe_header,
    )

    private fun settle_items(
        items: List<MailingListSubscription>,
        succeeded_ids: Set<String>,
    ): List<MailingListSubscription> {
        val local_tokens = unsubscribed_store.tokens.value
        return items.map { item ->
            val settled = item.id in succeeded_ids ||
                unsubscribed_store.token_for(item.sender_email) in local_tokens
            if (item.status == "active" && settled) item.copy(status = "unsubscribed") else item
        }
    }

    fun load() {
        if (load_job?.isActive == true) return
        val cached = list_memory.get()
        _state.update { current ->
            if (current.items.isEmpty() && cached != null) {
                current.copy(
                    items = settle_items(cached.items, current.succeeded_ids),
                    stats = current.stats ?: cached.stats,
                    is_loading = true,
                    error = null,
                    load_error = null,
                )
            } else {
                current.copy(is_loading = true, error = null, load_error = null)
            }
        }
        load_job = viewModelScope.launch {
            try {
                coroutineScope {
                    val tombstones_job = async { cache_sync.sync_tombstones() }
                    val stats_job = async {
                        try {
                            api.stats()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            null
                        }
                    }
                    val first = api.list(limit = subscription_page_size, offset = 0)
                    val tombstones = tombstones_job.await()
                    val visible = { list: List<MailingListSubscription> ->
                        list.filterNot { is_deleted_subscription(tombstones, it.sender_email, it.last_received) }
                    }
                    if (_state.value.items.isEmpty()) {
                        _state.update { current ->
                            current.copy(items = settle_items(visible(first.subscriptions), current.succeeded_ids))
                        }
                    }
                    val items = visible(
                        (first.subscriptions + load_remaining_pages(first)).distinctBy { it.id },
                    )
                    if (items.size < subscription_page_size * max_subscription_pages) {
                        unsubscribed_store.replace_confirmed(
                            items.filter { it.status == "unsubscribed" }.map { it.sender_email },
                        )
                    }
                    val stats = stats_job.await()
                    _state.update { current ->
                        current.copy(
                            items = settle_items(items, current.succeeded_ids),
                            stats = stats ?: current.stats,
                            load_error = null,
                        )
                    }
                    list_memory.put(items, stats)
                    if (items.any { it.status == "active" }) prime_info_cache()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        load_error = org.astermail.android.localized_api_error(
                            context,
                            t,
                            context.getString(R.string.failed_to_load),
                        ),
                    )
                }
            } finally {
                _state.update { it.copy(is_loading = false) }
            }
        }
    }

    private suspend fun load_remaining_pages(first: MailingListsResponse): List<MailingListSubscription> {
        if (!first.has_more || first.subscriptions.size < subscription_page_size) return emptyList()
        if (first.total > subscription_page_size) {
            val page_count = minOf(
                (first.total + subscription_page_size - 1) / subscription_page_size,
                max_subscription_pages,
            )
            val gate = Semaphore(LIST_CONCURRENCY)
            return coroutineScope {
                (1 until page_count).map { page ->
                    async {
                        gate.withPermit {
                            api.list(limit = subscription_page_size, offset = page * subscription_page_size)
                                .subscriptions
                        }
                    }
                }.awaitAll().flatten()
            }
        }
        val collected = mutableListOf<MailingListSubscription>()
        var page = 1
        while (page < max_subscription_pages) {
            val response = api.list(limit = subscription_page_size, offset = page * subscription_page_size)
            collected += response.subscriptions
            page++
            if (!response.has_more || response.subscriptions.size < subscription_page_size) break
        }
        return collected
    }

    private fun provisional_item(sender: DiscoveredSubscription): MailingListSubscription =
        MailingListSubscription(
            id = PROVISIONAL_PREFIX + sender.sender_email,
            sender_email = sender.sender_email,
            sender_name = sender.sender_name,
            domain = sender.domain,
            email_count = sender.email_count,
            last_received = sender.last_received,
            unsubscribe_link = sender.unsubscribe_link,
            category = sender.category,
        )

    private fun publish_scan_progress(progress: SubscriptionScanProgress, tombstones: Map<String, String>) {
        remember_info(progress.found)
        val found = progress.found.filterNot {
            is_deleted_subscription(tombstones, it.sender_email, it.last_received)
        }
        _state.update { current ->
            val known = current.items.map { normalize_sender_email(it.sender_email) }.toSet()
            val fresh = found
                .filter { normalize_sender_email(it.sender_email) !in known }
                .map { provisional_item(it) }
            current.copy(
                items = if (fresh.isEmpty()) current.items else current.items + settle_items(fresh, current.succeeded_ids),
                scan_scanned = progress.scanned,
                scan_total = progress.total,
                scan_found = found.size,
            )
        }
    }

    private suspend fun track_discovered(discovered: List<DiscoveredSubscription>): Int {
        val track_deadline = SystemClock.elapsedRealtime() + TRACK_BUDGET_MS
        val gate = Semaphore(TRACK_CONCURRENCY)
        return coroutineScope {
            discovered.map { sender ->
                async {
                    gate.withPermit {
                        if (SystemClock.elapsedRealtime() > track_deadline) return@withPermit false
                        try {
                            api.track_subscription(
                                TrackSubscriptionRequest(
                                    sender_email = sender.sender_email,
                                    sender_name = sender.sender_name.ifBlank { null },
                                    unsubscribe_link = sender.unsubscribe_link,
                                    list_unsubscribe_header = sender.list_unsubscribe_header,
                                    category = sender.category,
                                ),
                            ).is_new
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            false
                        }
                    }
                }
            }.awaitAll().count { it }
        }
    }

    fun scan(force_full: Boolean = false) {
        if (scan_job?.isActive == true) return
        val full = force_full || !info_cache.is_primed()
        prime_job?.cancel()
        prime_job = null
        _state.update {
            it.copy(
                is_scanning = true,
                error = null,
                message = null,
                scan_scanned = 0,
                scan_total = 0,
                scan_found = 0,
            )
        }
        scan_job = viewModelScope.launch {
            var scan_error: String? = null
            var cancelled = false
            var tracked = 0
            try {
                val tombstones = cache_sync.sync_tombstones()
                val discovered = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    scanner.scan(force_full = full) { progress -> publish_scan_progress(progress, tombstones) }
                }.also { found ->
                    if (found != null) {
                        remember_info(found)
                        if (full) info_cache.mark_primed()
                    }
                }.orEmpty().filterNot {
                    is_deleted_subscription(tombstones, it.sender_email, it.last_received)
                }
                tracked = track_discovered(discovered)
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (t: Throwable) {
                scan_error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.scan_failed))
            } finally {
                _state.update {
                    it.copy(
                        is_scanning = false,
                        scan_scanned = 0,
                        scan_total = 0,
                        scan_found = 0,
                        error = scan_error,
                        message = when {
                            cancelled || scan_error != null -> null
                            tracked > 0 -> context.resources.getQuantityString(
                                R.plurals.subscriptions_found_count,
                                tracked,
                                tracked,
                            )
                            else -> context.getString(R.string.scan_complete)
                        },
                    )
                }
            }
            if (scan_error == null) {
                load_job?.join()
                load()
            }
        }
    }

    fun cancel_scan() {
        scan_job?.cancel()
        scan_job = null
        _state.update { it.copy(is_scanning = false, scan_scanned = 0, scan_total = 0, scan_found = 0) }
    }

    suspend fun proxy_unsubscribe(request: ProxyUnsubscribeRequest): Boolean =
        proxy_unsubscribe_checked(request) == ProxyUnsubscribeResult.accepted

    suspend fun proxy_unsubscribe_checked(request: ProxyUnsubscribeRequest): ProxyUnsubscribeResult =
        guarded_request { api.proxy_unsubscribe(request).success }

    private fun is_rate_limited(): Boolean = SystemClock.elapsedRealtime() < rate_limited_until

    private suspend fun guarded_request(call: suspend () -> Boolean): ProxyUnsubscribeResult {
        if (is_rate_limited()) return ProxyUnsubscribeResult.rate_limited
        return try {
            if (call()) ProxyUnsubscribeResult.accepted else ProxyUnsubscribeResult.rejected
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            classify_unsubscribe_error(t).also { result ->
                if (result == ProxyUnsubscribeResult.rate_limited) {
                    rate_limited_until = SystemClock.elapsedRealtime() + RATE_LIMIT_COOLDOWN_MS
                }
            }
        }
    }

    private fun remember_info(found: List<DiscoveredSubscription>) {
        info_cache.put_all(
            found.mapNotNull { sender -> sender.unsubscribe_info?.let { sender.sender_email to it } }.toMap(),
        )
    }

    private fun prime_info_cache() {
        if (prime_job?.isActive == true || scan_job?.isActive == true) return
        if (info_cache.is_primed()) return
        prime_job = viewModelScope.launch {
            val found = try {
                withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    scanner.scan(force_full = true, persist_watermark = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            } ?: return@launch
            remember_info(found)
            info_cache.mark_primed()
        }
    }

    private suspend fun info_for(item: MailingListSubscription): UnsubscribeInfo? {
        info_cache.get(item.sender_email)?.let { return it }
        if (item.has_list_unsubscribe && !item.id.startsWith(PROVISIONAL_PREFIX)) return null
        val job = prime_job?.takeIf { it.isActive } ?: scan_job?.takeIf { it.isActive } ?: return null
        withTimeoutOrNull(PRIME_WAIT_MS) { job.join() }
        return info_cache.get(item.sender_email)
    }

    private suspend fun client_unsubscribe(info: UnsubscribeInfo): UnsubscribeOutcome =
        execute_unsubscribe_checked(info) { request -> proxy_unsubscribe_checked(request) }

    private fun manual_or_failed(info: UnsubscribeInfo?): UnsubscribeAttempt {
        val url = info?.let { get_manual_unsubscribe_url(it) }?.takeIf { is_safe_unsubscribe_target(it) }
            ?: return UnsubscribeAttempt.failed
        return UnsubscribeAttempt.manual(url, info.list_unsubscribe_header)
    }

    private suspend fun server_unsubscribe(subscription_id: String): ProxyUnsubscribeResult =
        guarded_request { api.unsubscribe(UnsubscribeRequest(subscription_id)).success }

    private suspend fun attempt_unsubscribe(item: MailingListSubscription): UnsubscribeAttempt {
        if (is_rate_limited()) return UnsubscribeAttempt.rate_limited
        val cached = info_for(item)
        if (cached != null) {
            return when (client_unsubscribe(cached)) {
                UnsubscribeOutcome.unsubscribed -> UnsubscribeAttempt.done(false, cached.list_unsubscribe_header)
                UnsubscribeOutcome.rate_limited -> UnsubscribeAttempt.rate_limited
                UnsubscribeOutcome.manual_required -> manual_or_failed(cached)
            }
        }
        val fallback = fallback_unsubscribe_info(item)
        if (item.has_list_unsubscribe && !item.id.startsWith(PROVISIONAL_PREFIX)) {
            when (server_unsubscribe(item.id)) {
                ProxyUnsubscribeResult.accepted -> return UnsubscribeAttempt.done(recorded_on_server = true)
                ProxyUnsubscribeResult.rate_limited -> return UnsubscribeAttempt.rate_limited
                ProxyUnsubscribeResult.rejected -> Unit
            }
        }
        if (fallback?.unsubscribe_mailto != null) {
            when (client_unsubscribe(fallback)) {
                UnsubscribeOutcome.unsubscribed -> return UnsubscribeAttempt.done(recorded_on_server = false)
                UnsubscribeOutcome.rate_limited -> return UnsubscribeAttempt.rate_limited
                UnsubscribeOutcome.manual_required -> Unit
            }
        }
        return manual_or_failed(fallback)
    }

    private suspend fun safe_attempt(item: MailingListSubscription): UnsubscribeAttempt =
        try {
            withTimeoutOrNull(ATTEMPT_TIMEOUT_MS) { attempt_unsubscribe(item) } ?: UnsubscribeAttempt.failed
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            UnsubscribeAttempt.failed
        }

    private fun record_success(
        item: MailingListSubscription,
        recorded_on_server: Boolean,
        list_unsubscribe_header: String?,
    ) {
        if (recorded_on_server) {
            unsubscribed_store.mark_unsubscribed(item.sender_email)
        } else {
            unsubscribed_store.record_unsubscribed(
                item.sender_email,
                item.sender_name.ifBlank { null },
                item.unsubscribe_link,
                list_unsubscribe_header,
            )
        }
    }

    private suspend fun settle_success(ids: Set<String>) {
        if (ids.isEmpty()) return
        _state.update { it.copy(succeeded_ids = it.succeeded_ids + ids, pending_ids = it.pending_ids - ids) }
        try {
            delay(SUCCESS_HOLD_MS)
        } finally {
            _state.update { current ->
                current.copy(
                    items = current.items.map { item ->
                        if (item.id in ids) item.copy(status = "unsubscribed") else item
                    },
                    succeeded_ids = current.succeeded_ids - ids,
                )
            }
        }
    }

    private fun flash_failure(ids: Set<String>) {
        if (ids.isEmpty()) return
        _state.update { it.copy(failed_ids = it.failed_ids + ids, pending_ids = it.pending_ids - ids) }
        viewModelScope.launch {
            delay(FAILURE_HOLD_MS)
            _state.update { it.copy(failed_ids = it.failed_ids - ids) }
        }
    }

    private fun is_busy(id: String): Boolean {
        val current = _state.value
        return id in current.pending_ids || id in current.succeeded_ids
    }

    fun unsubscribe(subscription_id: String) {
        val item = _state.value.items.firstOrNull { it.id == subscription_id } ?: return
        if (item.status != "active" || is_busy(subscription_id)) return
        start_unsubscribes(listOf(item))
    }

    fun bulk_unsubscribe(requested_ids: List<String>) {
        val requested = requested_ids.toSet()
        val targets = _state.value.items.filter {
            it.id in requested && it.status == "active" && !is_busy(it.id)
        }.distinctBy { it.id }
        if (targets.isEmpty()) return
        start_unsubscribes(targets)
    }

    private fun start_unsubscribes(targets: List<MailingListSubscription>) {
        val ids = targets.map { it.id }.toSet()
        flush_job?.cancel()
        flush_job = null
        batch.begin(targets.size)
        _state.update {
            it.copy(
                pending_ids = it.pending_ids + ids,
                failed_ids = it.failed_ids - ids,
                manual_prompt = it.manual_prompt?.takeIf { prompt -> prompt.subscription_id !in ids },
            )
        }
        targets.forEach { item -> viewModelScope.launch { run_unsubscribe(item) } }
    }

    private suspend fun run_unsubscribe(item: MailingListSubscription) {
        var kind = UnsubscribeResultKind.failed
        var prompt: ManualUnsubscribePrompt? = null
        try {
            when (val attempt = unsubscribe_gate.withPermit { safe_attempt(item) }) {
                is UnsubscribeAttempt.done -> {
                    record_success(item, attempt.recorded_on_server, attempt.list_unsubscribe_header)
                    kind = UnsubscribeResultKind.succeeded
                    viewModelScope.launch { settle_success(setOf(item.id)) }
                }
                is UnsubscribeAttempt.manual -> {
                    kind = UnsubscribeResultKind.needs_website
                    prompt = ManualUnsubscribePrompt(
                        subscription_id = item.id,
                        sender_email = item.sender_email,
                        sender_name = item.sender_name,
                        url = attempt.url,
                        list_unsubscribe_header = attempt.list_unsubscribe_header,
                    )
                }
                UnsubscribeAttempt.rate_limited -> {
                    kind = UnsubscribeResultKind.rate_limited
                    flash_failure(setOf(item.id))
                }
                UnsubscribeAttempt.failed -> flash_failure(setOf(item.id))
            }
        } finally {
            if (kind != UnsubscribeResultKind.succeeded) {
                _state.update { it.copy(pending_ids = it.pending_ids - item.id) }
            }
            batch.finish(kind, prompt)
            schedule_batch_summary()
        }
    }

    private fun schedule_batch_summary() {
        if (!batch.is_idle) return
        flush_job?.cancel()
        flush_job = viewModelScope.launch {
            delay(BATCH_SETTLE_MS)
            val result = batch.drain() ?: return@launch
            publish_batch(result)
        }
    }

    private fun publish_batch(result: UnsubscribeBatchResult) {
        val prompt = result.single_prompt
        if (prompt != null) {
            _state.update { it.copy(manual_prompt = prompt) }
            return
        }
        val text = summarize_unsubscribe_tally(result.tally).joinToString("\n") { line -> summary_text(line) }
        if (text.isNotEmpty()) _state.update { it.copy(message = text) }
    }

    private fun summary_text(line: UnsubscribeSummaryLine): String = when (line) {
        UnsubscribeSummaryLine.unsubscribed_one -> context.getString(R.string.toast_unsubscribed)
        is UnsubscribeSummaryLine.unsubscribed_many ->
            context.resources.getQuantityString(R.plurals.unsubscribed_count, line.count, line.count)
        is UnsubscribeSummaryLine.needs_website ->
            context.resources.getQuantityString(R.plurals.unsubscribe_needs_manual_count, line.count, line.count)
        UnsubscribeSummaryLine.failed_one -> context.getString(R.string.could_not_unsubscribe)
        is UnsubscribeSummaryLine.failed_many ->
            context.resources.getQuantityString(R.plurals.unsubscribe_failed_count, line.count, line.count)
        UnsubscribeSummaryLine.rate_limited -> context.getString(R.string.unsubscribe_rate_limited)
    }

    fun confirm_manual_unsubscribe(prompt: ManualUnsubscribePrompt) {
        _state.update { current -> current.copy(manual_prompt = current.manual_prompt?.takeIf { it != prompt }) }
        val item = _state.value.items.firstOrNull { it.id == prompt.subscription_id } ?: return
        if (item.status != "active" || is_busy(item.id)) return
        record_success(item, recorded_on_server = false, list_unsubscribe_header = prompt.list_unsubscribe_header)
        viewModelScope.launch { settle_success(setOf(item.id)) }
    }

    fun dismiss_manual_prompt() {
        _state.update { it.copy(manual_prompt = null) }
    }

    fun reactivate(subscription_id: String) {
        if (subscription_id in _state.value.pending_ids) return
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + subscription_id)
        viewModelScope.launch {
            try {
                api.reactivate(subscription_id)
                _state.value.items.firstOrNull { it.id == subscription_id }?.let {
                    unsubscribed_store.mark_active(it.sender_email)
                }
                _state.value = _state.value.copy(
                    items = _state.value.items.map { item ->
                        if (item.id == subscription_id) item.copy(status = "active") else item
                    },
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.reactivate_failed)),
                )
            } finally {
                _state.value = _state.value.copy(pending_ids = _state.value.pending_ids - subscription_id)
            }
        }
    }

    fun delete(requested_ids: List<String>) {
        val targets = _state.value.items
            .filter { it.id in requested_ids.toSet() && it.id !in _state.value.pending_ids }
        if (targets.isEmpty()) return
        val ids = targets.map { it.id }.toSet()
        val senders = targets.map { normalize_sender_email(it.sender_email) }.filter { it.contains('@') }.distinct()
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + ids)
        viewModelScope.launch {
            try {
                delete_subscription_senders(senders) { chunk ->
                    api.delete_senders(DeleteSubscriptionsRequest(chunk))
                }
                deleted_store.record_deleted(senders)
                cache_sync.sync_tombstones()
                _state.value = _state.value.copy(
                    items = _state.value.items.filterNot { it.id in ids },
                    message = context.resources.getQuantityString(
                        R.plurals.subscriptions_deleted_count,
                        ids.size,
                        ids.size,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    error = org.astermail.android.localized_api_error(
                        context,
                        t,
                        context.getString(R.string.delete_subscriptions_failed),
                    ),
                )
            } finally {
                _state.value = _state.value.copy(pending_ids = _state.value.pending_ids - ids)
            }
        }
    }

    fun auto_scan_if_empty() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            if (_state.value.items.isEmpty() &&
                _state.value.load_error == null &&
                scan_job?.isActive != true &&
                load_job?.isActive != true
            ) {
                scan(force_full = true)
            }
        }
    }

    fun clear_message() {
        _state.value = _state.value.copy(message = null)
    }

    fun clear_error() {
        _state.value = _state.value.copy(error = null)
    }
}
