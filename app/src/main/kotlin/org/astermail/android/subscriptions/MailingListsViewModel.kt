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
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.subscriptions.TrackSubscriptionRequest
import org.astermail.android.api.subscriptions.UnsubscribeRequest
import org.astermail.android.ui.mail.UnsubscribeInfo
import org.astermail.android.ui.mail.UnsubscribeOutcome
import org.astermail.android.ui.mail.execute_unsubscribe
import org.astermail.android.ui.mail.get_manual_unsubscribe_url

private const val SCAN_TIMEOUT_MS = 90_000L
private const val TRACK_BUDGET_MS = 45_000L
private const val subscription_page_size = 100
private const val max_subscription_pages = 50
private const val PRIME_WAIT_MS = 20_000L
private const val SUCCESS_HOLD_MS = 700L
private const val FAILURE_HOLD_MS = 1_800L
private const val BULK_CONCURRENCY = 4

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
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MailingListsState())
    val state: StateFlow<MailingListsState> = _state.asStateFlow()

    private var load_job: Job? = null
    private var scan_job: Job? = null
    private var prime_job: Job? = null

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

    fun load() {
        if (load_job?.isActive == true) return
        _state.value = _state.value.copy(is_loading = true, error = null, load_error = null)
        load_job = viewModelScope.launch {
            try {
                val tombstones = cache_sync.sync_tombstones()
                val items = load_all_subscriptions().filterNot {
                    is_deleted_subscription(tombstones, it.sender_email, it.last_received)
                }
                if (items.size < subscription_page_size * max_subscription_pages) {
                    unsubscribed_store.replace_confirmed(
                        items.filter { it.status == "unsubscribed" }.map { it.sender_email },
                    )
                }
                val stats = try {
                    api.stats()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
                val local_tokens = unsubscribed_store.tokens.value
                _state.update { current ->
                    current.copy(
                        items = items.map { item ->
                            val settled = item.id in current.succeeded_ids ||
                                unsubscribed_store.token_for(item.sender_email) in local_tokens
                            if (item.status == "active" && settled) item.copy(status = "unsubscribed") else item
                        },
                        stats = stats,
                        load_error = null,
                    )
                }
                if (items.any { it.status == "active" }) prime_info_cache()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    load_error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.failed_to_load)),
                )
            } finally {
                _state.value = _state.value.copy(is_loading = false)
            }
        }
    }

    private suspend fun load_all_subscriptions(): List<MailingListSubscription> {
        val collected = mutableListOf<MailingListSubscription>()
        var page = 0
        while (page < max_subscription_pages) {
            val response = api.list(limit = subscription_page_size, offset = page * subscription_page_size)
            collected += response.subscriptions
            page++
            if (!response.has_more || response.subscriptions.size < subscription_page_size) break
        }
        return collected
    }

    fun scan(force_full: Boolean = false) {
        if (scan_job?.isActive == true) return
        val full = force_full || !info_cache.is_primed()
        prime_job?.cancel()
        prime_job = null
        _state.value = _state.value.copy(is_scanning = true, error = null, message = null)
        scan_job = viewModelScope.launch {
            var scan_error: String? = null
            var cancelled = false
            var tracked = 0
            try {
                val discovered = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    scanner.scan(force_full = full)
                }.also { found ->
                    if (found != null) {
                        remember_info(found)
                        if (full) info_cache.mark_primed()
                    }
                }.orEmpty().let { found ->
                    val tombstones = cache_sync.sync_tombstones()
                    found.filterNot { is_deleted_subscription(tombstones, it.sender_email, it.last_received) }
                }
                val track_deadline = SystemClock.elapsedRealtime() + TRACK_BUDGET_MS
                for (sender in discovered) {
                    if (SystemClock.elapsedRealtime() > track_deadline) break
                    val result = try {
                        api.track_subscription(
                            TrackSubscriptionRequest(
                                sender_email = sender.sender_email,
                                sender_name = sender.sender_name.ifBlank { null },
                                unsubscribe_link = sender.unsubscribe_link,
                                list_unsubscribe_header = sender.list_unsubscribe_header,
                                category = sender.category,
                            ),
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }
                    if (result?.is_new == true) tracked += 1
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } catch (t: Throwable) {
                scan_error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.scan_failed))
            } finally {
                _state.value = _state.value.copy(
                    is_scanning = false,
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
            if (scan_error == null) load()
        }
    }

    fun cancel_scan() {
        scan_job?.cancel()
        scan_job = null
        _state.value = _state.value.copy(is_scanning = false)
    }

    suspend fun proxy_unsubscribe(request: ProxyUnsubscribeRequest): Boolean {
        return try {
            api.proxy_unsubscribe(request).success
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
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
        val job = prime_job?.takeIf { it.isActive } ?: scan_job?.takeIf { it.isActive } ?: return null
        withTimeoutOrNull(PRIME_WAIT_MS) { job.join() }
        return info_cache.get(item.sender_email)
    }

    private suspend fun client_unsubscribe(info: UnsubscribeInfo): Boolean =
        execute_unsubscribe(info) { request -> proxy_unsubscribe(request) } == UnsubscribeOutcome.unsubscribed

    private fun manual_or_failed(info: UnsubscribeInfo?): UnsubscribeAttempt {
        val url = info?.let { get_manual_unsubscribe_url(it) }?.takeIf { is_safe_unsubscribe_target(it) }
            ?: return UnsubscribeAttempt.failed
        return UnsubscribeAttempt.manual(url, info.list_unsubscribe_header)
    }

    private suspend fun server_unsubscribe(subscription_id: String): Boolean =
        try {
            api.unsubscribe(UnsubscribeRequest(subscription_id)).success
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            false
        }

    private suspend fun attempt_unsubscribe(item: MailingListSubscription): UnsubscribeAttempt {
        val cached = info_for(item)
        if (cached != null) {
            if (client_unsubscribe(cached)) {
                return UnsubscribeAttempt.done(false, cached.list_unsubscribe_header)
            }
            return manual_or_failed(cached)
        }
        val fallback = fallback_unsubscribe_info(item)
        if (item.has_list_unsubscribe && server_unsubscribe(item.id)) {
            return UnsubscribeAttempt.done(recorded_on_server = true)
        }
        if (fallback?.unsubscribe_mailto != null && client_unsubscribe(fallback)) {
            return UnsubscribeAttempt.done(recorded_on_server = false)
        }
        return manual_or_failed(fallback)
    }

    private suspend fun safe_attempt(item: MailingListSubscription): UnsubscribeAttempt =
        try {
            attempt_unsubscribe(item)
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
        _state.update {
            it.copy(
                pending_ids = it.pending_ids + subscription_id,
                failed_ids = it.failed_ids - subscription_id,
                manual_prompt = it.manual_prompt?.takeIf { prompt -> prompt.subscription_id != subscription_id },
            )
        }
        viewModelScope.launch {
            try {
                when (val attempt = safe_attempt(item)) {
                    is UnsubscribeAttempt.done -> {
                        record_success(item, attempt.recorded_on_server, attempt.list_unsubscribe_header)
                        _state.update { it.copy(message = context.getString(R.string.toast_unsubscribed)) }
                        settle_success(setOf(subscription_id))
                    }
                    is UnsubscribeAttempt.manual -> _state.update {
                        it.copy(
                            pending_ids = it.pending_ids - subscription_id,
                            manual_prompt = ManualUnsubscribePrompt(
                                subscription_id = item.id,
                                sender_email = item.sender_email,
                                sender_name = item.sender_name,
                                url = attempt.url,
                                list_unsubscribe_header = attempt.list_unsubscribe_header,
                            ),
                        )
                    }
                    UnsubscribeAttempt.failed -> {
                        flash_failure(setOf(subscription_id))
                        _state.update { it.copy(error = context.getString(R.string.could_not_unsubscribe)) }
                    }
                }
            } finally {
                _state.update { it.copy(pending_ids = it.pending_ids - subscription_id) }
            }
        }
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

    fun bulk_unsubscribe(requested_ids: List<String>) {
        val requested = requested_ids.toSet()
        val targets = _state.value.items.filter {
            it.id in requested && it.status == "active" && !is_busy(it.id)
        }
        if (targets.isEmpty()) return
        val ids = targets.map { it.id }.toSet()
        _state.update { it.copy(pending_ids = it.pending_ids + ids, failed_ids = it.failed_ids - ids) }
        viewModelScope.launch {
            try {
                val semaphore = Semaphore(BULK_CONCURRENCY)
                val results = coroutineScope {
                    targets.map { item ->
                        async { semaphore.withPermit { item to safe_attempt(item) } }
                    }.awaitAll()
                }
                val succeeded = results.mapNotNull { (item, attempt) ->
                    (attempt as? UnsubscribeAttempt.done)?.let { item to it }
                }
                succeeded.forEach { (item, attempt) ->
                    record_success(item, attempt.recorded_on_server, attempt.list_unsubscribe_header)
                }
                val remaining = results.size - succeeded.size
                flash_failure(results.filter { it.second !is UnsubscribeAttempt.done }.map { it.first.id }.toSet())
                _state.update {
                    it.copy(
                        message = if (succeeded.isNotEmpty()) {
                            context.resources.getQuantityString(
                                R.plurals.unsubscribed_count,
                                succeeded.size,
                                succeeded.size,
                            )
                        } else {
                            it.message
                        },
                        error = if (remaining > 0) {
                            context.resources.getQuantityString(
                                R.plurals.unsubscribe_needs_manual_count,
                                remaining,
                                remaining,
                            )
                        } else {
                            it.error
                        },
                    )
                }
                settle_success(succeeded.map { it.first.id }.toSet())
            } finally {
                _state.update { it.copy(pending_ids = it.pending_ids - ids) }
            }
        }
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
