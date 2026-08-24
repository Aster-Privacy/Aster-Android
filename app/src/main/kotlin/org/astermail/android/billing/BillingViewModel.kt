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

package org.astermail.android.billing

import org.astermail.android.BuildConfig
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.astermail.android.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.BillingHistoryItem
import org.astermail.android.api.billing.CancelSubscriptionRequest
import org.astermail.android.api.billing.ChangePlanRequest
import org.astermail.android.api.billing.CheckoutSessionRequest
import org.astermail.android.api.billing.DetachPaymentMethodRequest
import org.astermail.android.api.billing.PaymentMethodItem
import org.astermail.android.api.billing.PlanChangePreviewResponse
import org.astermail.android.api.billing.PlanLimitsResponse
import org.astermail.android.api.billing.SetDefaultPaymentMethodRequest
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.api.billing.SwitchBillingRequest
import org.astermail.android.auth.AuthRepository

data class BillingUiState(
    val subscription: SubscriptionResponse? = null,
    val available_plans: List<AvailablePlan> = emptyList(),
    val limits: PlanLimitsResponse? = null,
    val history: List<BillingHistoryItem> = emptyList(),
    val payment_methods: List<PaymentMethodItem> = emptyList(),
    val storage_addons: org.astermail.android.api.billing.StorageAddonsResponse? = null,
    val is_loading: Boolean = false,
    val is_acting: Boolean = false,
    val acting_action: String? = null,
    val error: String? = null,
    val info: String? = null,
    val checkout_url: String? = null,
    val portal_url: String? = null,
    val awaiting_checkout: Boolean = false,
    val awaiting_portal: Boolean = false,
    val crypto_native_enabled: Boolean = false,
    val crypto_native_coins: List<org.astermail.android.api.billing.CryptoNativeCoin> = emptyList(),
    val pending_crypto_invoices: List<org.astermail.android.api.billing.CryptoNativePendingInvoice> = emptyList(),
    val created_crypto_invoice_id: String? = null,
    val plan_change_preview: PlanChangePreviewResponse? = null,
    val plan_change_preview_loading: Boolean = false,
    val plan_change_preview_failed: Boolean = false,
    val subscription_error: String? = null,
    val plans_failed: Boolean = false,
    val checking_payment: Boolean = false,
    val checkout_abandoned_plan: String? = null,
    val cancel_impact: org.astermail.android.api.billing.CancelImpactResponse? = null,
    val cancel_impact_loading: Boolean = false,
    val credits: org.astermail.android.api.billing.CreditBalanceResponse? = null,
    val academic: org.astermail.android.api.billing.AcademicDiscountStatusResponse? = null,
    val onboarding: org.astermail.android.api.billing.OnboardingChecklistResponse? = null,
)

const val CHECKOUT_POLL_INTERVAL_MS = 3_000L
const val CHECKOUT_POLL_TIMEOUT_MS = 60_000L
const val SIGN_IN_WAIT_TIMEOUT_MS = 15_000L

@HiltViewModel
class BillingViewModel @Inject constructor(
    application: Application,
    private val billing_api: BillingApi,
    private val auth_repository: AuthRepository,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    private val _review_request = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val review_request: SharedFlow<Unit> = _review_request.asSharedFlow()

    private var paid_before_checkout = false

    private var pending_crypto_invoices_in_flight = false

    private var poll_job: Job? = null

    private var pending_checkout_plan: String? = null

    private var snapshot_before_checkout: String? = null

    init {
        viewModelScope.launch {
            billing_return_store.outcome.collect { outcome ->
                if (outcome == null) return@collect
                billing_return_store.outcome.value = null
                on_billing_return(outcome)
            }
        }
    }

    private fun subscription_signature(sub: SubscriptionResponse?): String =
        "${sub?.plan?.code}|${sub?.status}|${sub?.current_period_end}|${sub?.storage?.limit_bytes}|${sub?.cancel_at_period_end}"

    private fun is_active_paid(sub: SubscriptionResponse?): Boolean {
        if (sub == null) return false
        val active = sub.status == "active" || sub.status == "trialing"
        return active && sub.plan.price_cents > 0
    }

    fun load_all() {
        load_subscription()
        load_plans()
        load_limits()
        load_history()
    }

    fun load_subscription() {
        viewModelScope.launch { reload_subscription() }
    }

    private suspend fun reload_subscription() {
        _state.update { it.copy(is_loading = true, subscription_error = null) }
        try {
            val sub = billing_api.get_subscription()
            _state.update { it.copy(subscription = sub, is_loading = false, subscription_error = null) }
            PaymentFailedNotifier.observe(ctx, sub.status, sub.payment_failed_at, sub.current_period_end, sub.plan.name)
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_subscription failed", t)
            _state.update {
                it.copy(
                    is_loading = false,
                    subscription_error = localized_api_error(ctx, t, ctx.getString(R.string.subscription_refresh_failed)),
                )
            }
        }
    }

    fun load_plans() {
        viewModelScope.launch {
            try {
                val response = billing_api.get_available_plans()
                _state.update { it.copy(available_plans = response.plans, plans_failed = response.plans.isEmpty()) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_available_plans failed", t)
                _state.update { it.copy(plans_failed = true) }
            }
        }
    }

    fun load_limits() {
        viewModelScope.launch {
            try {
                val limits = billing_api.get_plan_limits()
                _state.update { it.copy(limits = limits) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_plan_limits failed", t)
            }
        }
    }

    fun load_history() {
        viewModelScope.launch {
            try {
                val response = billing_api.get_billing_history(page = 1, per_page = 20)
                _state.update { it.copy(history = response.items) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_billing_history failed", t)
            }
        }
    }

    fun start_checkout(plan_code: String, billing_interval: String = "month", currency: String? = null) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "checkout_$plan_code", error = null, checkout_url = null)
            if (!await_signed_in()) {
                _state.value = _state.value.copy(is_acting = false, acting_action = null, error = ctx.getString(R.string.session_expired_sign_in))
                return@launch
            }
            try {
                val response = billing_api.create_checkout_session(
                    CheckoutSessionRequest(
                        plan_code = plan_code,
                        billing_interval = billing_interval,
                        currency = currency,
                        test_mode = org.astermail.android.BuildConfig.DEBUG,
                        success_url = BILLING_RETURN_SUCCESS,
                        cancel_url = BILLING_RETURN_CANCELLED,
                    ),
                )
                pending_checkout_plan = plan_code
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url, checkout_abandoned_plan = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.could_not_start_checkout)),
                )
            }
        }
    }

    fun open_portal() {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "portal", error = null, portal_url = null)
            try {
                val response = billing_api.create_portal_session()
                _state.value = _state.value.copy(is_acting = false, acting_action = null, portal_url = response.url)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.could_not_open_portal)),
                )
            }
        }
    }

    fun cancel_subscription(reason: String? = null, reason_text: String? = null): Boolean {
        if (_state.value.is_acting) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.billing_action_in_progress), info = null)
            return false
        }
        val chosen_reason = reason?.takeIf { it in CANCEL_REASONS }
        val password_hash = auth_repository.cached_password_hash_b64()
        if (password_hash == null) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.session_expired_sign_in), info = null)
            return false
        }
        _state.value = _state.value.copy(is_acting = true, acting_action = "cancel", error = null, info = null)
        viewModelScope.launch {
            try {
                val response = billing_api.cancel_subscription(
                    CancelSubscriptionRequest(
                        password_hash = password_hash,
                        cancel_reason = chosen_reason,
                        cancel_reason_text = clamp_cancel_reason_text(reason_text),
                    ),
                )
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    info = if (response.cancel_at_period_end) ctx.getString(R.string.subscription_will_end) else ctx.getString(R.string.subscription_cancelled),
                )
                load_subscription()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.cancel_failed)),
                )
            }
        }
        return true
    }

    fun reactivate_subscription() {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "reactivate", error = null, info = null)
            try {
                billing_api.reactivate_subscription()
                _state.value = _state.value.copy(is_acting = false, acting_action = null, info = ctx.getString(R.string.subscription_reactivated))
                load_subscription()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.reactivate_failed)),
                )
            }
        }
    }

    fun switch_billing(billing_interval: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "switch", error = null, info = null)
            try {
                val response = billing_api.switch_billing_interval(
                    SwitchBillingRequest(billing_interval = billing_interval),
                )
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    info = ctx.getString(R.string.billing_changed_to, billing_interval_label(ctx, response.billing_interval)),
                )
                load_subscription()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.switch_failed)),
                )
            }
        }
    }

    fun change_plan(plan_code: String, billing_interval: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "change_$plan_code", error = null, info = null)
            try {
                billing_api.change_plan(ChangePlanRequest(plan_code = plan_code, billing_interval = billing_interval))
                _state.value = _state.value.copy(is_acting = false, acting_action = null, info = ctx.getString(R.string.plan_changed))
                load_subscription()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.change_plan_failed)),
                )
            }
        }
    }

    private fun billing_error(t: Throwable, fallback: String): String = when (t) {
        is org.astermail.android.api.ApiError.InvalidCredentials -> ctx.getString(R.string.incorrect_password)
        is org.astermail.android.api.ApiError.UnauthorizedError -> ctx.getString(R.string.session_expired_sign_in)
        else -> localized_api_error(ctx, t, fallback)
    }

    fun load_cancel_impact() {
        viewModelScope.launch {
            _state.update { it.copy(cancel_impact = null, cancel_impact_loading = true) }
            try {
                val impact = billing_api.get_cancel_impact()
                _state.update { it.copy(cancel_impact = impact, cancel_impact_loading = false) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_cancel_impact failed", t)
                _state.update { it.copy(cancel_impact_loading = false) }
            }
        }
    }

    fun load_credits_and_discounts() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(credits = billing_api.get_credit_balance()) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_credit_balance failed", t)
            }
            try {
                _state.update { it.copy(academic = billing_api.get_academic_discount_status()) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_academic_discount_status failed", t)
            }
        }
    }

    fun load_onboarding_checklist() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(onboarding = billing_api.get_onboarding_checklist()) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_onboarding_checklist failed", t)
            }
        }
    }

    fun dismiss_onboarding_checklist() {
        val current = _state.value.onboarding ?: return
        _state.update { it.copy(onboarding = current.copy(dismissed_at = "local")) }
        viewModelScope.launch {
            try {
                billing_api.dismiss_onboarding_checklist()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "dismiss_onboarding_checklist failed", t)
            }
        }
    }

    fun on_billing_return(outcome: billing_return_outcome) {
        when (outcome) {
            billing_return_outcome.success -> poll_after_return()
            billing_return_outcome.cancelled -> {
                _state.update { it.copy(awaiting_checkout = false, checkout_abandoned_plan = pending_checkout_plan) }
            }
            billing_return_outcome.open -> load_subscription()
        }
    }

    fun clear_checkout_abandoned() {
        _state.update { it.copy(checkout_abandoned_plan = null) }
    }

    private fun poll_after_return() {
        if (poll_job?.isActive == true) return
        val was_checkout = _state.value.awaiting_checkout
        _state.update { it.copy(awaiting_checkout = false, awaiting_portal = false, checking_payment = true) }
        poll_job = viewModelScope.launch {
            val before = snapshot_before_checkout
            val deadline = CHECKOUT_POLL_TIMEOUT_MS
            var elapsed = 0L
            var changed = false
            while (true) {
                reload_subscription()
                if (before == null || subscription_signature(_state.value.subscription) != before) {
                    changed = true
                    break
                }
                if (elapsed >= deadline) break
                delay(CHECKOUT_POLL_INTERVAL_MS)
                elapsed += CHECKOUT_POLL_INTERVAL_MS
            }
            load_payment_methods()
            load_storage_addons()
            val now_paid = is_active_paid(_state.value.subscription)
            _state.update {
                it.copy(
                    checking_payment = false,
                    checkout_abandoned_plan = if (!changed && pending_checkout_plan != null) pending_checkout_plan else it.checkout_abandoned_plan,
                    info = if (changed && now_paid) ctx.getString(R.string.payment_confirmed) else it.info,
                )
            }
            if (changed) pending_checkout_plan = null
            if (was_checkout && changed && !paid_before_checkout && now_paid) {
                _review_request.emit(Unit)
            }
        }
    }

    fun load_plan_change_preview(plan_code: String, billing_interval: String) {
        viewModelScope.launch {
            _state.update {
                it.copy(plan_change_preview = null, plan_change_preview_loading = true, plan_change_preview_failed = false)
            }
            try {
                val preview = billing_api.preview_plan_change(plan_code, billing_interval)
                _state.update { it.copy(plan_change_preview = preview, plan_change_preview_loading = false) }
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "preview_plan_change failed", t)
                _state.update { it.copy(plan_change_preview_loading = false, plan_change_preview_failed = true) }
            }
        }
    }

    fun clear_plan_change_preview() {
        _state.update {
            it.copy(plan_change_preview = null, plan_change_preview_loading = false, plan_change_preview_failed = false)
        }
    }

    fun start_crypto_checkout(plan_code: String, term_months: Int) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "crypto_$plan_code", error = null, checkout_url = null)
            try {
                val response = billing_api.create_crypto_checkout_session(
                    org.astermail.android.api.billing.CryptoCheckoutRequest(
                        plan_code = plan_code,
                        term_months = term_months,
                        success_url = BILLING_RETURN_SUCCESS,
                        cancel_url = BILLING_RETURN_CANCELLED,
                    )
                )
                pending_checkout_plan = plan_code
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url, checkout_abandoned_plan = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false, acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.could_not_start_checkout)),
                )
            }
        }
    }

    fun purchase_addon_crypto(addon_id: String, term_months: Int) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "crypto_addon_$addon_id", error = null, checkout_url = null)
            try {
                val response = billing_api.purchase_storage_addon_crypto(
                    org.astermail.android.api.billing.CryptoAddonCheckoutRequest(
                        addon_id = addon_id,
                        term_months = term_months,
                        success_url = BILLING_RETURN_SUCCESS,
                        cancel_url = BILLING_RETURN_CANCELLED,
                    )
                )
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false, acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.could_not_start_checkout)),
                )
            }
        }
    }

    fun load_crypto_native_coins() {
        if (_state.value.crypto_native_coins.isNotEmpty()) return
        viewModelScope.launch {
            try {
                val response = billing_api.get_crypto_native_coins()
                _state.value = _state.value.copy(
                    crypto_native_enabled = response.enabled,
                    crypto_native_coins = response.coins,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(crypto_native_enabled = false, crypto_native_coins = emptyList())
            }
        }
    }

    fun load_pending_crypto_invoices() {
        if (pending_crypto_invoices_in_flight) return
        pending_crypto_invoices_in_flight = true
        viewModelScope.launch {
            try {
                val response = billing_api.list_pending_crypto_invoices()
                val now_ms = System.currentTimeMillis()
                response.invoices.forEach { resolved_crypto_invoices.observe(it.id, it.created_at) }
                _state.value = _state.value.copy(
                    pending_crypto_invoices = response.invoices.filter {
                        is_resumable_crypto_invoice(it, now_ms)
                    },
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(pending_crypto_invoices = emptyList())
            } finally {
                pending_crypto_invoices_in_flight = false
            }
        }
    }

    fun create_crypto_native_invoice(plan_code: String, term_months: Int, currency: String, chain: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                is_acting = true,
                acting_action = "crypto_native_$plan_code",
                error = null,
                created_crypto_invoice_id = null,
            )
            try {
                val response = billing_api.create_crypto_native_invoice(
                    org.astermail.android.api.billing.CreateCryptoNativeInvoiceRequest(
                        plan_code = plan_code,
                        term_months = term_months,
                        currency = currency,
                        chain = chain,
                    )
                )
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    created_crypto_invoice_id = response.id,
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false, acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.could_not_start_checkout)),
                )
            }
        }
    }

    fun consume_created_crypto_invoice() {
        _state.value = _state.value.copy(created_crypto_invoice_id = null)
    }

    fun load_storage_addons() {
        viewModelScope.launch {
            try {
                val response = billing_api.get_storage_addons()
                _state.value = _state.value.copy(storage_addons = response)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_storage_addons failed", t)
            }
        }
    }

    fun purchase_storage_addon(addon_id: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "addon_$addon_id", error = null, checkout_url = null)
            try {
                val response = billing_api.purchase_storage_addon(
                    org.astermail.android.api.billing.PurchaseAddonRequest(addon_id = addon_id)
                )
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.could_not_start_checkout)),
                )
            }
        }
    }

    fun consume_checkout_url() {
        paid_before_checkout = is_active_paid(_state.value.subscription)
        snapshot_before_checkout = subscription_signature(_state.value.subscription)
        _state.value = _state.value.copy(checkout_url = null, awaiting_checkout = true)
    }

    fun consume_portal_url() {
        _state.value = _state.value.copy(portal_url = null, awaiting_portal = true)
    }

    fun on_resume() {
        val s = _state.value
        if (s.awaiting_checkout) {
            poll_after_return()
        } else if (s.awaiting_portal) {
            _state.value = s.copy(awaiting_portal = false)
            viewModelScope.launch {
                reload_subscription()
                load_payment_methods()
            }
        }
    }

    suspend fun await_signed_in(): Boolean =
        kotlinx.coroutines.withTimeoutOrNull(SIGN_IN_WAIT_TIMEOUT_MS) {
            auth_repository.is_signed_in.first { it }
        } ?: false

    fun clear_messages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun clear_subscription_error() {
        _state.value = _state.value.copy(subscription_error = null)
    }

    fun load_payment_methods() {
        viewModelScope.launch {
            try {
                val response = billing_api.list_payment_methods()
                _state.value = _state.value.copy(payment_methods = response.payment_methods)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "list_payment_methods failed", t)
            }
        }
    }

    fun set_default_payment_method(payment_method_id: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "set_default_$payment_method_id")
            try {
                billing_api.set_default_payment_method(SetDefaultPaymentMethodRequest(payment_method_id))
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    info = ctx.getString(R.string.default_payment_updated),
                    payment_methods = _state.value.payment_methods.map {
                        it.copy(is_default = it.id == payment_method_id)
                    },
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.set_default_failed)),
                )
            }
        }
    }

    fun detach_payment_method(payment_method_id: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "detach_$payment_method_id")
            try {
                billing_api.detach_payment_method(DetachPaymentMethodRequest(payment_method_id))
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    info = ctx.getString(R.string.payment_method_removed),
                    payment_methods = _state.value.payment_methods.filter { it.id != payment_method_id },
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.remove_payment_failed)),
                )
            }
        }
    }
}
