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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.auth.AuthRepository

internal const val SPECIAL_OFFER_DEFAULT_PLAN_CODE = "nova"
internal const val SPECIAL_OFFER_DEFAULT_PERCENT_OFF = 50
internal const val SPECIAL_OFFER_DEFAULT_DURATION_MONTHS = 12
internal const val SPECIAL_OFFER_CURRENCY = "usd"
private const val SPECIAL_OFFER_MIN_CENTS = 50L

internal const val SPECIAL_OFFER_CARD_INTERVAL = "month"
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
    val owns_checkout: Boolean = false,
    val step: SpecialOfferStep = SpecialOfferStep.offer,
) {
    val effective_percent_off: Int
        get() = if (percent_off > 0) percent_off else SPECIAL_OFFER_DEFAULT_PERCENT_OFF

    val effective_duration_months: Int
        get() = if (duration_months > 0) duration_months else SPECIAL_OFFER_DEFAULT_DURATION_MONTHS

    fun applies_to(plan_code: String?): Boolean =
        available && plan_code != null && plan_code.equals(this.plan_code, ignoreCase = true)

    fun applies_to_card(plan_code: String?, billing_interval: String?): Boolean =
        applies_to(plan_code) && billing_interval == SPECIAL_OFFER_CARD_INTERVAL

    fun applies_to_crypto_term(plan_code: String?, term_months: Int): Boolean =
        applies_to(plan_code) && term_months in SPECIAL_OFFER_CRYPTO_TERMS
}

@HiltViewModel
class SpecialOfferViewModel @Inject constructor(
    private val billing_api: BillingApi,
    private val auth_repository: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SpecialOfferState())
    val state: StateFlow<SpecialOfferState> = _state.asStateFlow()

    private var account_id: String? = null
    private var generation = 0L
    private var load_succeeded = false
    private var load_job: Job? = null
    private var account_job = SupervisorJob(viewModelScope.coroutineContext[Job])

    init {
        viewModelScope.launch {
            auth_repository.active_account_id.collect { id -> switch_account(id) }
        }
    }

    private fun switch_account(id: String?) {
        if (id == account_id) return
        account_id = id
        generation += 1
        account_job.cancel()
        account_job = SupervisorJob(viewModelScope.coroutineContext[Job])
        load_job = null
        load_succeeded = false
        _state.value = SpecialOfferState()
        if (id != null) fetch()
    }

    private fun launch_for_account(block: suspend CoroutineScope.() -> Unit): Job =
        viewModelScope.launch(account_job, block = block)

    fun retry_load() {
        if (account_id == null || load_succeeded || load_job?.isActive == true) return
        fetch()
    }

    private fun fetch() {
        val expected = generation
        load_job = launch_for_account {
            try {
                val status = billing_api.get_special_offer()
                if (expected != generation) return@launch_for_account
                load_succeeded = true
                _state.update {
                    it.copy(
                        is_loaded = true,
                        available = status.available,
                        auto_show = status.auto_show,
                        percent_off = status.percent_off,
                        duration_months = status.duration_months,
                        plan_code = status.plan_code.ifBlank { SPECIAL_OFFER_DEFAULT_PLAN_CODE },
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (expected != generation) return@launch_for_account
                _state.update { it.copy(is_loaded = true, available = false, auto_show = false) }
            }
        }
    }

    fun claim_and_open() {
        val current = _state.value
        if (!current.auto_show || current.is_claiming || current.is_open) return
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
                    is_open = granted,
                    auto_show = false,
                    accept_failed = false,
                    step = SpecialOfferStep.offer,
                )
            }
        }
    }

    fun close() {
        _state.update {
            it.copy(
                is_open = false,
                accept_failed = false,
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
            val ok = try {
                billing_api.accept_special_offer().ok
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                false
            }
            if (expected != generation) return@launch_for_account
            _state.update {
                it.copy(
                    is_accepting = false,
                    is_accepted = ok,
                    accept_failed = !ok,
                    step = if (ok) SpecialOfferStep.payment_method else SpecialOfferStep.offer,
                )
            }
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
        launch_for_account {
            try {
                billing_api.dismiss_special_offer()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
            }
        }
    }
}
