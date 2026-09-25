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

package org.astermail.android.ui.upgrade

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.ui.auth.within_sign_up_quiet_period

internal const val SPECIAL_OFFER_DEFAULT_PLAN_CODE = "nova"
internal const val SPECIAL_OFFER_DEFAULT_PERCENT_OFF = 50
internal const val SPECIAL_OFFER_DEFAULT_DURATION_MONTHS = 12
internal const val SPECIAL_OFFER_CURRENCY = "usd"
private const val SPECIAL_OFFER_MIN_CENTS = 50L

internal val SPECIAL_OFFER_CRYPTO_TERMS = listOf(1, 3, 6, 12)

internal fun special_offer_price_cents(list_cents: Long, percent_off: Int): Long {
    val discount_cents = (list_cents * percent_off + 50) / 100
    return (list_cents - discount_cents).coerceAtLeast(SPECIAL_OFFER_MIN_CENTS)
}

internal fun special_offer_crypto_term_cents(monthly_cents: Long?, yearly_cents: Long?, term_months: Int): Long? =
    when (term_months) {
        1, 3, 6 -> monthly_cents?.let { it * term_months }
        12 -> yearly_cents
        else -> null
    }

enum class SpecialOfferStep { offer, payment_method, crypto_term }

data class SpecialOfferState(
    val is_loaded: Boolean = false,
    val available: Boolean = false,
    val auto_show: Boolean = false,
    val percent_off: Int = 0,
    val duration_months: Int = 0,
    val plan_code: String = SPECIAL_OFFER_DEFAULT_PLAN_CODE,
    val is_open: Boolean = false,
    val is_claiming: Boolean = false,
    val is_accepting: Boolean = false,
    val is_accepted: Boolean = false,
    val accept_failed: Boolean = false,
    val offer_expired: Boolean = false,
    val owns_checkout: Boolean = false,
    val step: SpecialOfferStep = SpecialOfferStep.offer,
) {
    val effective_percent_off: Int
        get() = if (percent_off > 0) percent_off else SPECIAL_OFFER_DEFAULT_PERCENT_OFF

    val effective_duration_months: Int
        get() = if (duration_months > 0) duration_months else SPECIAL_OFFER_DEFAULT_DURATION_MONTHS

    fun applies_to(plan_code: String?): Boolean =
        available && plan_code != null && plan_code.equals(this.plan_code, ignoreCase = true)

    fun applies_to_crypto_term(plan_code: String?, term_months: Int): Boolean =
        applies_to(plan_code) && term_months in SPECIAL_OFFER_CRYPTO_TERMS
}

@HiltViewModel
class SpecialOfferViewModel @Inject constructor(
    private val billing_api: BillingApi,
    private val auth_repository: AuthRepository,
    private val offer_preferences: OfferPreferencesStore,
    @ApplicationContext context: Context,
) : ViewModel() {
    private val offer_cache = context.getSharedPreferences(OFFER_CACHE_PREFS, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(SpecialOfferState())
    val state: StateFlow<SpecialOfferState> = _state.asStateFlow()

    private var account_id: String? = null
    private var generation = 0L
    private var load_succeeded = false
    private var last_plan_code: String? = null
    private var load_job: Job? = null
    private var account_job = SupervisorJob(viewModelScope.coroutineContext[Job])
    private var auto_show_suppressed = false
    private var preference_fallback = false
    private var quiet_blocked = false

    internal var in_quiet_period: () -> Boolean = { within_sign_up_quiet_period(context) }
    internal var now_ms: () -> Long = System::currentTimeMillis

    init {
        viewModelScope.launch {
            auth_repository.active_account_id.collect { id -> switch_account(id) }
        }
        viewModelScope.launch {
            offer_preferences.state.map { it.enabled }.distinctUntilChanged().collect { enabled ->
                preference_fallback = false
                if (!enabled) {
                    suppress()
                } else if (auto_show_suppressed) {
                    refresh()
                }
            }
        }
    }

    private fun offers_enabled(): Boolean = preference_fallback || offer_preferences.state.value.enabled

    private fun suppress() {
        auto_show_suppressed = true
        _state.update { it.copy(is_open = false, available = false, auto_show = false) }
    }

    private fun refresh() {
        if (account_id == null) return
        load_job?.cancel()
        fetch()
    }

    private fun switch_account(id: String?) {
        if (id == account_id) return
        account_id = id
        generation += 1
        account_job.cancel()
        account_job = SupervisorJob(viewModelScope.coroutineContext[Job])
        load_job = null
        load_succeeded = false
        last_plan_code = null
        auto_show_suppressed = false
        preference_fallback = false
        quiet_blocked = false
        _state.value = if (id != null) cached_state(id) else SpecialOfferState()
        if (id != null) fetch()
    }

    private fun cached_state(id: String): SpecialOfferState {
        if (!offer_preferences.state.value.enabled || in_quiet_period()) return SpecialOfferState()
        if (!offer_cache.getBoolean("$id.available", false)) return SpecialOfferState()
        val age_ms = now_ms() - offer_cache.getLong("$id.cached_at", 0L)
        if (age_ms < 0L || age_ms > OFFER_CACHE_TTL_MS) return SpecialOfferState()
        return SpecialOfferState(
            available = true,
            percent_off = offer_cache.getInt("$id.percent_off", 0),
            duration_months = offer_cache.getInt("$id.duration_months", 0),
            plan_code = offer_cache.getString("$id.plan_code", null) ?: SPECIAL_OFFER_DEFAULT_PLAN_CODE,
        )
    }

    private fun store_cache() {
        val id = account_id ?: return
        val current = _state.value
        offer_cache.edit()
            .putBoolean("$id.available", current.available)
            .putInt("$id.percent_off", current.percent_off)
            .putInt("$id.duration_months", current.duration_months)
            .putString("$id.plan_code", current.plan_code)
            .putLong("$id.cached_at", now_ms())
            .apply()
    }

    private fun launch_for_account(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(account_job, block = block)

    fun retry_load() {
        if (account_id == null || load_job?.isActive == true) return
        if (load_succeeded && !(quiet_blocked && !in_quiet_period())) return
        fetch()
    }

    fun on_plan_code(plan_code: String?) {
        val previous = last_plan_code
        if (plan_code != null) last_plan_code = plan_code
        if (account_id == null || previous == null || plan_code == null || previous == plan_code) return
        load_job?.cancel()
        fetch()
    }

    private fun fetch() {
        val expected = generation
        load_job = launch_for_account {
            try {
                val (status, preference_loaded) = coroutineScope {
                    val status_request = async { billing_api.get_special_offer() }
                    val preference_request = async { offer_preferences.load() }
                    status_request.await() to preference_request.await()
                }
                if (expected != generation) return@launch_for_account
                load_succeeded = true
                preference_fallback = !preference_loaded
                if (preference_fallback) auto_show_suppressed = false
                quiet_blocked = in_quiet_period()
                val enabled = offers_enabled() && !quiet_blocked
                _state.update {
                    it.copy(
                        is_loaded = true,
                        available = status.available && enabled,
                        auto_show = status.auto_show && enabled && !auto_show_suppressed,
                        percent_off = status.percent_off,
                        duration_months = status.duration_months,
                        plan_code = status.plan_code.ifBlank { SPECIAL_OFFER_DEFAULT_PLAN_CODE },
                    )
                }
                store_cache()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (expected != generation) return@launch_for_account
                _state.update {
                    it.copy(
                        is_loaded = true,
                        available = it.available && load_succeeded,
                        auto_show = false,
                    )
                }
            }
        }
    }

    fun claim_and_open() {
        val current = _state.value
        if (!current.auto_show || current.is_claiming || current.is_open || !offers_enabled() || in_quiet_period()) return
        val expected = generation
        _state.update { it.copy(is_claiming = true) }
        launch_for_account {
            val granted = try {
                billing_api.claim_special_offer().granted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                false
            }
            if (expected != generation) return@launch_for_account
            _state.update {
                it.copy(
                    is_claiming = false,
                    is_open = granted && offers_enabled(),
                    auto_show = false,
                    accept_failed = false,
                    step = SpecialOfferStep.offer,
                )
            }
        }
    }

    fun reopen() {
        _state.update {
            if (!it.available || it.is_open || it.is_claiming) {
                it
            } else {
                it.copy(is_open = true, auto_show = false, accept_failed = false, offer_expired = false, step = SpecialOfferStep.offer)
            }
        }
    }

    fun close() {
        _state.update {
            it.copy(
                is_open = false,
                accept_failed = false,
                offer_expired = false,
                owns_checkout = false,
                step = SpecialOfferStep.offer,
            )
        }
    }

    fun accept() {
        val current = _state.value
        if (current.is_accepting || current.owns_checkout) return
        if (current.is_accepted) {
            _state.update { it.copy(accept_failed = false, step = SpecialOfferStep.payment_method) }
            return
        }
        val expected = generation
        _state.update { it.copy(is_accepting = true, accept_failed = false) }
        launch_for_account {
            val ok: Boolean? = try {
                billing_api.accept_special_offer().ok
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                null
            }
            if (expected != generation) return@launch_for_account
            _state.update {
                it.copy(
                    is_accepting = false,
                    is_accepted = ok == true,
                    accept_failed = ok == null,
                    offer_expired = ok == false,
                    available = it.available && ok != false,
                    auto_show = it.auto_show && ok != false,
                    step = if (ok == true) SpecialOfferStep.payment_method else SpecialOfferStep.offer,
                )
            }
            if (ok == false) store_cache()
        }
    }

    fun show_step(step: SpecialOfferStep) {
        _state.update { it.copy(step = step) }
    }

    fun begin_checkout() {
        _state.update { it.copy(owns_checkout = true, accept_failed = false, step = SpecialOfferStep.offer) }
    }

    fun release_checkout() {
        _state.update { it.copy(owns_checkout = false) }
    }

    fun mark_redeemed() {
        _state.update { it.copy(available = false, auto_show = false, owns_checkout = false) }
        store_cache()
    }

    fun dismiss_forever() {
        _state.update {
            it.copy(
                is_open = false,
                available = false,
                auto_show = false,
                accept_failed = false,
                owns_checkout = false,
                step = SpecialOfferStep.offer,
            )
        }
        store_cache()
        launch_for_account {
            var attempt = 0
            var sent = false
            while (!sent && attempt < DISMISS_ATTEMPTS) {
                try {
                    billing_api.dismiss_special_offer()
                    sent = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    attempt += 1
                    if (attempt < DISMISS_ATTEMPTS) kotlinx.coroutines.delay(DISMISS_RETRY_MS * attempt)
                }
            }
        }
    }
}

private const val OFFER_CACHE_PREFS = "special_offer_cache"
private const val OFFER_CACHE_TTL_MS = 12L * 60L * 60L * 1000L
private const val DISMISS_ATTEMPTS = 4
private const val DISMISS_RETRY_MS = 2000L
