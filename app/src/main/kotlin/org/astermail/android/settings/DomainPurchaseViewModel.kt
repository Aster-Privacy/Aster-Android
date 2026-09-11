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

package org.astermail.android.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.domains.DOMAIN_SEARCH_RATE_LIMITED_CODE
import org.astermail.android.api.domains.DomainCheckoutRequest
import org.astermail.android.api.domains.DomainOrder
import org.astermail.android.api.domains.DomainOrderRenewRequest
import org.astermail.android.api.domains.DomainPurchaseApi
import org.astermail.android.api.domains.DomainPurchaseConflict
import org.astermail.android.api.domains.DomainPurchasePaused
import org.astermail.android.api.domains.DomainSearchResult
import org.astermail.android.api.domains.RETRY_AFTER_SECS_KEY

enum class DomainPurchaseErrorKind { generic, taken, limit, slow_down, paused, not_allowed }

val domain_order_terminal_statuses =
    setOf("complete", "failed", "refund_pending", "refunded", "expired", "lapsed", "cancelled")

val domain_order_hidden_statuses = setOf("expired", "refunded", "failed", "cancelled")

fun is_domain_order_in_flight(status: String): Boolean =
    status !in domain_order_terminal_statuses && status != "pending_payment"

private const val SHORT_RETRY_MAX_SECS = 2L

fun domain_search_retry_after_secs(error: ApiError.RateLimited): Long? =
    error.details[RETRY_AFTER_SECS_KEY]?.trim()?.toLongOrNull()?.takeIf { it > 0 }

fun is_domain_search_throttled(error: ApiError.RateLimited): Boolean =
    error.code == DOMAIN_SEARCH_RATE_LIMITED_CODE ||
        (domain_search_retry_after_secs(error) ?: 0L) > SHORT_RETRY_MAX_SECS

private const val DEFAULT_THROTTLE_RETRY_SECS = 60L
private const val MAX_THROTTLE_RETRY_SECS = 300L

fun domain_search_throttle_delay_ms(error: ApiError.RateLimited): Long =
    (domain_search_retry_after_secs(error) ?: DEFAULT_THROTTLE_RETRY_SECS)
        .coerceIn(1L, MAX_THROTTLE_RETRY_SECS) * 1_000L

data class DomainPurchaseUiState(
    val query: String = "",
    val searching: Boolean = false,
    val searched_query: String = "",
    val results: List<DomainSearchResult> = emptyList(),
    val suggestions: List<DomainSearchResult> = emptyList(),
    val has_more_suggestions: Boolean = false,
    val next_suggest_page: Int = 1,
    val loading_more_suggestions: Boolean = false,
    val search_failed: Boolean = false,
    val search_rate_limited: Boolean = false,
    val more_suggestions_rate_limited: Boolean = false,
    val selected: DomainSearchResult? = null,
    val years: Int = 1,
    val payment_method: String = "stripe",
    val buying: Boolean = false,
    val checkout_error: DomainPurchaseErrorKind? = null,
    val checkout_url: String? = null,
    val orders: List<DomainOrder> = emptyList(),
    val orders_loading: Boolean = false,
    val cancelling_order_id: String? = null,
    val renewing_order_id: String? = null,
    val order_action_error: DomainPurchaseErrorKind? = null,
    val order: DomainOrder? = null,
    val order_load_failed: Boolean = false,
    val resume_order_id: String? = null,
)

@HiltViewModel
class DomainPurchaseViewModel @Inject constructor(
    application: Application,
    private val purchase_api: DomainPurchaseApi,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(DomainPurchaseUiState())
    val state: StateFlow<DomainPurchaseUiState> = _state.asStateFlow()

    private var search_job: Job? = null
    private var search_cooldown: Job? = null

    private val prefs
        get() = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun set_query(raw: String) {
        val query = raw.lowercase(java.util.Locale.ROOT).trimStart()
        val cooling = is_cooling_down()
        _state.update { it.copy(query = query, search_failed = false, search_rate_limited = cooling) }
        search_job?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) {
            _state.update {
                it.copy(
                    searching = false,
                    search_rate_limited = false,
                    searched_query = "",
                    results = emptyList(),
                    suggestions = emptyList(),
                    has_more_suggestions = false,
                    next_suggest_page = 1,
                )
            }
            return
        }
        _state.update { it.copy(searching = !cooling) }
        search_job = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            wait_for_cooldown()
            run_search(trimmed)
        }
    }

    fun retry_search() {
        val trimmed = _state.value.query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return
        search_job?.cancel()
        val cooling = is_cooling_down()
        _state.update { it.copy(searching = !cooling, search_failed = false, search_rate_limited = cooling) }
        search_job = viewModelScope.launch {
            wait_for_cooldown()
            run_search(trimmed)
        }
    }

    private fun is_cooling_down(): Boolean = search_cooldown?.isActive == true

    private fun start_cooldown(ms: Long) {
        search_cooldown?.cancel()
        search_cooldown = viewModelScope.launch { delay(ms) }
    }

    private suspend fun wait_for_cooldown() {
        val cooldown = search_cooldown?.takeIf { it.isActive } ?: return
        _state.update { it.copy(searching = false, search_rate_limited = true) }
        cooldown.join()
        _state.update { it.copy(searching = true, search_rate_limited = false) }
    }

    private suspend fun run_search(trimmed: String, retried: Boolean = false) {
        try {
            val response = purchase_api.search(trimmed)
            if (_state.value.query.trim() != trimmed) return
            _state.update {
                it.copy(
                    searching = false,
                    searched_query = trimmed,
                    results = response.results,
                    suggestions = response.suggestions,
                    has_more_suggestions = response.has_more_suggestions,
                    next_suggest_page = response.next_suggest_page,
                    search_failed = false,
                    search_rate_limited = false,
                    more_suggestions_rate_limited = false,
                )
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (_state.value.query.trim() != trimmed) return
            if (t is ApiError.RateLimited) {
                handle_search_rate_limited(trimmed, t, retried)
                return
            }
            _state.update { it.copy(searching = false, search_failed = true, search_rate_limited = false) }
        }
    }

    private suspend fun handle_search_rate_limited(trimmed: String, error: ApiError.RateLimited, retried: Boolean) {
        val throttled = is_domain_search_throttled(error)
        if (!retried && !throttled) {
            delay(short_retry_delay_ms(error))
            if (_state.value.query.trim() == trimmed) run_search(trimmed, retried = true)
            return
        }
        start_cooldown(rate_limit_cooldown_ms(error))
        _state.update { it.copy(searching = false, search_failed = false, search_rate_limited = true) }
        if (retried) return
        search_cooldown?.join()
        if (_state.value.query.trim() != trimmed) return
        _state.update { it.copy(searching = true, search_rate_limited = false) }
        run_search(trimmed, retried = true)
    }

    fun load_more_suggestions() {
        val current = _state.value
        if (current.loading_more_suggestions || current.searched_query.isBlank()) return
        if (is_cooling_down()) {
            _state.update { it.copy(more_suggestions_rate_limited = true) }
            return
        }
        _state.update { it.copy(loading_more_suggestions = true, more_suggestions_rate_limited = false) }
        viewModelScope.launch {
            fetch_more_suggestions(current.searched_query, current.next_suggest_page)
        }
    }

    private suspend fun fetch_more_suggestions(query: String, page: Int, retried: Boolean = false) {
        try {
            val response = purchase_api.search(query, page)
            if (_state.value.searched_query != query) {
                _state.update { it.copy(loading_more_suggestions = false) }
                return
            }
            _state.update {
                val seen = it.suggestions.map { s -> s.domain }.toSet()
                it.copy(
                    loading_more_suggestions = false,
                    suggestions = it.suggestions + response.suggestions.filter { s -> s.domain !in seen },
                    has_more_suggestions = response.has_more_suggestions,
                    next_suggest_page = response.next_suggest_page,
                    more_suggestions_rate_limited = false,
                )
            }
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            if (t is ApiError.RateLimited && !retried && !is_domain_search_throttled(t)) {
                delay(short_retry_delay_ms(t))
                if (_state.value.searched_query == query) {
                    fetch_more_suggestions(query, page, retried = true)
                } else {
                    _state.update { it.copy(loading_more_suggestions = false) }
                }
                return
            }
            if (t is ApiError.RateLimited) start_cooldown(rate_limit_cooldown_ms(t))
            _state.update {
                it.copy(
                    loading_more_suggestions = false,
                    more_suggestions_rate_limited = t is ApiError.RateLimited && it.searched_query == query,
                )
            }
        }
    }

    private fun short_retry_delay_ms(error: ApiError.RateLimited): Long =
        maxOf(RATE_LIMIT_RETRY_MS, (domain_search_retry_after_secs(error) ?: 0L) * 1_000L)

    private fun rate_limit_cooldown_ms(error: ApiError.RateLimited): Long =
        if (is_domain_search_throttled(error)) domain_search_throttle_delay_ms(error) else short_retry_delay_ms(error)

    fun select_result(result: DomainSearchResult) {
        _state.update { it.copy(selected = result, years = 1, checkout_error = null) }
    }

    fun clear_selected() {
        _state.update { it.copy(selected = null, checkout_error = null) }
    }

    fun set_years(years: Int) {
        _state.update { it.copy(years = years.coerceIn(1, 3)) }
    }

    fun set_payment_method(method: String) {
        _state.update { it.copy(payment_method = method) }
    }

    fun start_checkout(captcha_token: String?) {
        val current = _state.value
        val selected = current.selected ?: return
        if (current.buying) return
        _state.update { it.copy(buying = true, checkout_error = null) }
        viewModelScope.launch {
            try {
                val response = purchase_api.checkout(
                    DomainCheckoutRequest(
                        domain = selected.domain,
                        years = current.years,
                        payment_method = current.payment_method,
                        captcha_token = captcha_token,
                    ),
                )
                store_pending(response.order_id, response.checkout_url)
                _state.update { it.copy(buying = false, checkout_url = response.checkout_url) }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(buying = false, checkout_error = error_kind_of(t)) }
            }
        }
    }

    fun consume_checkout_url() {
        _state.update { it.copy(checkout_url = null) }
    }

    fun complete_purchase(order: DomainOrder) {
        if (_state.value.buying) return
        if (open_stored_checkout(order.id)) return
        _state.update { it.copy(buying = true, order_action_error = null) }
        viewModelScope.launch {
            try {
                val response = purchase_api.checkout(
                    DomainCheckoutRequest(
                        domain = order.domain,
                        years = order.years.coerceIn(1, 3),
                        payment_method = "stripe",
                        captcha_token = null,
                    ),
                )
                store_pending(response.order_id, response.checkout_url)
                _state.update { it.copy(buying = false, checkout_url = response.checkout_url) }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(buying = false, order_action_error = error_kind_of(t)) }
            }
        }
    }

    fun load_orders() {
        if (_state.value.orders_loading) return
        _state.update { it.copy(orders_loading = true) }
        viewModelScope.launch {
            try {
                val response = purchase_api.list_orders()
                _state.update {
                    it.copy(
                        orders_loading = false,
                        orders = response.orders.filter { order ->
                            order.order_type == "registration" &&
                                order.status !in domain_order_hidden_statuses
                        },
                    )
                }
            } catch (t: CancellationException) {
                throw t
            } catch (_: Throwable) {
                _state.update { it.copy(orders_loading = false) }
            }
        }
    }

    fun cancel_order(order_id: String) {
        if (_state.value.cancelling_order_id != null) return
        _state.update { it.copy(cancelling_order_id = order_id, order_action_error = null) }
        viewModelScope.launch {
            try {
                val response = purchase_api.cancel_order(order_id)
                if (response.success) {
                    if (pending_order_id() == order_id) clear_pending()
                    _state.update {
                        it.copy(
                            cancelling_order_id = null,
                            orders = it.orders.filter { order -> order.id != order_id },
                        )
                    }
                } else {
                    _state.update {
                        it.copy(cancelling_order_id = null, order_action_error = DomainPurchaseErrorKind.generic)
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(cancelling_order_id = null, order_action_error = error_kind_of(t)) }
                load_orders()
            }
        }
    }

    fun renew_order(order_id: String) {
        if (_state.value.renewing_order_id != null) return
        _state.update { it.copy(renewing_order_id = order_id, order_action_error = null) }
        viewModelScope.launch {
            try {
                val response = purchase_api.renew_order(
                    order_id,
                    DomainOrderRenewRequest(years = 1, payment_method = "stripe"),
                )
                store_pending(response.order_id, response.checkout_url)
                _state.update { it.copy(renewing_order_id = null, checkout_url = response.checkout_url) }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                _state.update { it.copy(renewing_order_id = null, order_action_error = error_kind_of(t)) }
            }
        }
    }

    fun pending_order_id(): String? =
        prefs.getString(KEY_PENDING_ORDER_ID, null)?.takeIf { it.isNotBlank() }

    fun stored_checkout_url(order_id: String): String? {
        if (pending_order_id() != order_id) return null
        return prefs.getString(KEY_PENDING_CHECKOUT_URL, null)?.takeIf { it.isNotBlank() }
    }

    fun open_stored_checkout(order_id: String): Boolean {
        val url = stored_checkout_url(order_id) ?: return false
        _state.update { it.copy(checkout_url = url) }
        return true
    }

    fun check_pending_order() {
        val order_id = pending_order_id() ?: return
        viewModelScope.launch {
            try {
                val order = purchase_api.get_order(order_id)
                when {
                    order.status == "pending_payment" -> load_orders()
                    is_domain_order_in_flight(order.status) -> {
                        clear_pending()
                        _state.update { it.copy(resume_order_id = order.id) }
                    }
                    else -> {
                        clear_pending()
                        load_orders()
                    }
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (t is ApiError.NotFoundError) clear_pending()
            }
        }
    }

    fun consume_resume_order() {
        _state.update { it.copy(resume_order_id = null) }
    }

    suspend fun poll_order(order_id: String) {
        var failures = 0
        _state.update { it.copy(order_load_failed = false) }
        while (true) {
            try {
                val order = purchase_api.get_order(order_id)
                failures = 0
                _state.update { it.copy(order = order, order_load_failed = false) }
                if (order.status in domain_order_terminal_statuses) {
                    if (pending_order_id() == order_id) clear_pending()
                    return
                }
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                if (t is ApiError.NotFoundError) {
                    _state.update { it.copy(order_load_failed = it.order == null) }
                    return
                }
                failures += 1
                if (failures >= MAX_POLL_FAILURES && _state.value.order == null) {
                    _state.update { it.copy(order_load_failed = true) }
                }
            }
            delay(ORDER_POLL_INTERVAL_MS)
        }
    }

    private fun store_pending(order_id: String, checkout_url: String) {
        prefs.edit()
            .putString(KEY_PENDING_ORDER_ID, order_id)
            .putString(KEY_PENDING_CHECKOUT_URL, checkout_url)
            .apply()
    }

    private fun clear_pending() {
        prefs.edit()
            .remove(KEY_PENDING_ORDER_ID)
            .remove(KEY_PENDING_CHECKOUT_URL)
            .apply()
    }

    private fun error_kind_of(t: Throwable): DomainPurchaseErrorKind = when (t) {
        is DomainPurchaseConflict -> DomainPurchaseErrorKind.taken
        is DomainPurchasePaused -> DomainPurchaseErrorKind.paused
        is ApiError.PlanLimitExceeded -> DomainPurchaseErrorKind.limit
        is ApiError.ForbiddenError -> DomainPurchaseErrorKind.not_allowed
        is ApiError.RateLimited -> DomainPurchaseErrorKind.slow_down
        else -> DomainPurchaseErrorKind.generic
    }

    companion object {
        private const val PREFS_NAME = "aster_domain_purchase"
        private const val KEY_PENDING_ORDER_ID = "pending_order_id"
        private const val KEY_PENDING_CHECKOUT_URL = "pending_checkout_url"
        private const val MIN_QUERY_LENGTH = 3
        private const val SEARCH_DEBOUNCE_MS = 800L
        private const val RATE_LIMIT_RETRY_MS = 1_100L
        private const val ORDER_POLL_INTERVAL_MS = 5_000L
        private const val MAX_POLL_FAILURES = 3
    }
}
