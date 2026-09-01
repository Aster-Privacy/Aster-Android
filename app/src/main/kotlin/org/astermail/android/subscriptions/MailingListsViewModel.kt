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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.R
import org.astermail.android.api.subscriptions.BulkUnsubscribeRequest
import org.astermail.android.api.subscriptions.MailingListStats
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.api.subscriptions.ProxyUnsubscribeRequest
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.subscriptions.TrackSubscriptionRequest
import org.astermail.android.api.subscriptions.UnsubscribeRequest

private const val SCAN_TIMEOUT_MS = 90_000L
private const val TRACK_BUDGET_MS = 45_000L
private const val subscription_page_size = 100
private const val max_subscription_pages = 50

data class MailingListsState(
    val is_loading: Boolean = false,
    val is_scanning: Boolean = false,
    val items: List<MailingListSubscription> = emptyList(),
    val stats: MailingListStats? = null,
    val error: String? = null,
    val load_error: String? = null,
    val pending_ids: Set<String> = emptySet(),
    val message: String? = null,
)

@HiltViewModel
class MailingListsViewModel @Inject constructor(
    private val api: SubscriptionsApi,
    private val scanner: SubscriptionScanner,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MailingListsState())
    val state: StateFlow<MailingListsState> = _state.asStateFlow()

    private var load_job: Job? = null
    private var scan_job: Job? = null

    fun load() {
        if (load_job?.isActive == true) return
        _state.value = _state.value.copy(is_loading = true, error = null, load_error = null)
        load_job = viewModelScope.launch {
            try {
                val items = load_all_subscriptions()
                val stats = try {
                    api.stats()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null
                }
                _state.value = _state.value.copy(
                    items = items,
                    stats = stats,
                    load_error = null,
                )
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
        _state.value = _state.value.copy(is_scanning = true, error = null, message = null)
        scan_job = viewModelScope.launch {
            var scan_error: String? = null
            var cancelled = false
            var tracked = 0
            try {
                val discovered = withTimeoutOrNull(SCAN_TIMEOUT_MS) {
                    scanner.scan(force_full = force_full)
                }.orEmpty()
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


    fun unsubscribe(subscription_id: String) {
        if (subscription_id in _state.value.pending_ids) return
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + subscription_id)
        viewModelScope.launch {
            try {
                api.unsubscribe(UnsubscribeRequest(subscription_id))
                _state.value = _state.value.copy(
                    items = _state.value.items.map { item ->
                        if (item.id == subscription_id) item.copy(status = "unsubscribed") else item
                    },
                    message = context.getString(R.string.toast_unsubscribed),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.unsubscribe_failed)),
                )
            } finally {
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - subscription_id,
                )
            }
        }
    }

    fun bulk_unsubscribe(requested_ids: List<String>) {
        val ids = requested_ids.distinct().filter { it !in _state.value.pending_ids }
        if (ids.isEmpty()) return
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + ids)
        viewModelScope.launch {
            try {
                api.bulk_unsubscribe(BulkUnsubscribeRequest(ids))
                _state.value = _state.value.copy(
                    items = _state.value.items.map { item ->
                        if (item.id in ids) item.copy(status = "unsubscribed") else item
                    },
                    message = context.resources.getQuantityString(R.plurals.unsubscribed_count, ids.size, ids.size),
                )
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    error = org.astermail.android.localized_api_error(context, t, context.getString(R.string.bulk_unsubscribe_failed)),
                )
            } finally {
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - ids.toSet(),
                )
            }
        }
    }

    fun reactivate(subscription_id: String) {
        if (subscription_id in _state.value.pending_ids) return
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + subscription_id)
        viewModelScope.launch {
            try {
                api.reactivate(subscription_id)
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
