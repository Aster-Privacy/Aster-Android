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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.subscriptions.BulkUnsubscribeRequest
import org.astermail.android.api.subscriptions.MailingListStats
import org.astermail.android.api.subscriptions.MailingListSubscription
import org.astermail.android.api.subscriptions.SubscriptionsApi
import org.astermail.android.api.subscriptions.UnsubscribeRequest

data class MailingListsState(
    val is_loading: Boolean = false,
    val is_scanning: Boolean = false,
    val items: List<MailingListSubscription> = emptyList(),
    val stats: MailingListStats? = null,
    val error: String? = null,
    val pending_ids: Set<String> = emptySet(),
    val message: String? = null,
)

@HiltViewModel
class MailingListsViewModel @Inject constructor(
    private val api: SubscriptionsApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(MailingListsState())
    val state: StateFlow<MailingListsState> = _state.asStateFlow()

    fun load() {
        if (_state.value.is_loading) return
        _state.value = _state.value.copy(is_loading = true, error = null)
        viewModelScope.launch {
            try {
                val response = api.list(limit = 200)
                val stats = try { api.stats() } catch (_: Throwable) { null }
                _state.value = _state.value.copy(
                    items = response.subscriptions,
                    stats = stats,
                    is_loading = false,
                    error = null,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_loading = false,
                    error = t.message ?: context.getString(R.string.failed_to_load),
                )
            }
        }
    }

    fun scan() {
        if (_state.value.is_scanning) return
        _state.value = _state.value.copy(is_scanning = true, error = null, message = null)
        viewModelScope.launch {
            try {
                val result = api.scan()
                _state.value = _state.value.copy(
                    is_scanning = false,
                    message = result.message.ifBlank { context.getString(R.string.scan_complete) },
                )
                load()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_scanning = false,
                    error = t.message ?: context.getString(R.string.scan_failed),
                )
            }
        }
    }

    fun unsubscribe(subscription_id: String) {
        if (subscription_id in _state.value.pending_ids) return
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + subscription_id)
        viewModelScope.launch {
            try {
                api.unsubscribe(UnsubscribeRequest(subscription_id))
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - subscription_id,
                    items = _state.value.items.map { item ->
                        if (item.id == subscription_id) item.copy(status = "unsubscribed") else item
                    },
                    message = context.getString(R.string.toast_unsubscribed),
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - subscription_id,
                    error = t.message ?: context.getString(R.string.unsubscribe_failed),
                )
            }
        }
    }

    fun bulk_unsubscribe(ids: List<String>) {
        if (ids.isEmpty()) return
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + ids)
        viewModelScope.launch {
            try {
                api.bulk_unsubscribe(BulkUnsubscribeRequest(ids))
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - ids.toSet(),
                    items = _state.value.items.map { item ->
                        if (item.id in ids) item.copy(status = "unsubscribed") else item
                    },
                    message = context.resources.getQuantityString(R.plurals.unsubscribed_count, ids.size, ids.size),
                )
                load()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - ids.toSet(),
                    error = t.message ?: context.getString(R.string.bulk_unsubscribe_failed),
                )
            }
        }
    }

    fun reactivate(subscription_id: String) {
        _state.value = _state.value.copy(pending_ids = _state.value.pending_ids + subscription_id)
        viewModelScope.launch {
            try {
                api.reactivate(subscription_id)
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - subscription_id,
                    items = _state.value.items.map { item ->
                        if (item.id == subscription_id) item.copy(status = "active") else item
                    },
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    pending_ids = _state.value.pending_ids - subscription_id,
                    error = t.message ?: context.getString(R.string.reactivate_failed),
                )
            }
        }
    }

    fun auto_scan_if_empty() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            if (_state.value.items.isEmpty() && !_state.value.is_scanning && !_state.value.is_loading) {
                scan()
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
