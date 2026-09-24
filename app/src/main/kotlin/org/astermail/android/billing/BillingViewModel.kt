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

import kotlinx.coroutines.CancellationException
import org.astermail.android.BuildConfig
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.astermail.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import org.astermail.android.api.billing.GooglePlayActiveAddon
import org.astermail.android.api.billing.GooglePlayAddonProduct
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.api.billing.GooglePlayVerifyRequest
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
    val checkout_abandoned_interval: String? = null,
    val cancel_impact: org.astermail.android.api.billing.CancelImpactResponse? = null,
    val cancel_impact_loading: Boolean = false,
    val credits: org.astermail.android.api.billing.CreditBalanceResponse? = null,
    val academic: org.astermail.android.api.billing.AcademicDiscountStatusResponse? = null,
    val onboarding: org.astermail.android.api.billing.OnboardingChecklistResponse? = null,
    val play_enabled: Boolean = false,
    val play_account_id: String? = null,
    val play_products: List<GooglePlayProduct> = emptyList(),
    val play_offers: List<PlayOffer> = emptyList(),
    val play_blocked_reason: String? = null,
    val play_currency: String? = null,
    val play_purchase_request: PlayPurchaseRequest? = null,
    val play_addon_products: List<GooglePlayAddonProduct> = emptyList(),
    val play_special_offer: GooglePlaySpecialOffer? = null,
    val play_special_offer_eligible: Boolean = false,
    val play_active_plan: String? = null,
    val play_active_addons: List<GooglePlayActiveAddon> = emptyList(),
)

internal sealed interface PlayTarget {
    data class Plan(val plan_code: String, val billing_interval: String) : PlayTarget
    data class Addon(val storage_bytes: Long) : PlayTarget
    data object SpecialOffer : PlayTarget
}

object AvailablePlansCache {
    @Volatile
    private var cached_plans: List<AvailablePlan> = emptyList()

    fun plans(): List<AvailablePlan> = cached_plans

    fun update(plans: List<AvailablePlan>) {
        if (plans.isNotEmpty()) cached_plans = plans
    }

    fun reset() {
        cached_plans = emptyList()
    }
}

const val CHECKOUT_POLL_INTERVAL_MS = 3_000L
const val CHECKOUT_POLL_TIMEOUT_MS = 60_000L
const val CHECKOUT_QUIET_POLL_TIMEOUT_MS = 15_000L
const val CHECKOUT_POLL_REQUEST_TIMEOUT_MS = 10_000L
const val CHECKOUT_TARGET_PREFS = "aster_billing_checkout_target"
const val CHECKOUT_TARGET_PLAN_KEY = "plan_code"
const val CHECKOUT_TARGET_INTERVAL_KEY = "billing_interval"

fun checkout_interval_for_term_months(term_months: Int): String =
    if (term_months >= 12) "year" else "month"
const val SIGN_IN_WAIT_TIMEOUT_MS = 15_000L

@HiltViewModel
class BillingViewModel @Inject constructor(
    application: Application,
    private val billing_api: BillingApi,
    private val auth_repository: AuthRepository,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(BillingUiState(available_plans = AvailablePlansCache.plans()))
    val state: StateFlow<BillingUiState> = _state.asStateFlow()



    private var pending_crypto_invoices_in_flight = false

    private var poll_job: Job? = null

    private var preview_job: Job? = null

    private var preview_request_tag: Pair<String, String>? = null

    private val checkout_target_prefs by lazy {
        ctx.getSharedPreferences(CHECKOUT_TARGET_PREFS, Context.MODE_PRIVATE)
    }

    private fun write_checkout_target(key: String, value: String?) {
        val editor = checkout_target_prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }

    private var pending_checkout_plan: String?
        get() = checkout_target_prefs.getString(CHECKOUT_TARGET_PLAN_KEY, null)
        set(value) = write_checkout_target(CHECKOUT_TARGET_PLAN_KEY, value)

    private var pending_checkout_interval: String?
        get() = checkout_target_prefs.getString(CHECKOUT_TARGET_INTERVAL_KEY, null)
        set(value) = write_checkout_target(CHECKOUT_TARGET_INTERVAL_KEY, value)

    private var snapshot_before_checkout: String? = null

    internal var play_store: PlayStore = PlayBilling

    private val cached_play_install by lazy { is_play_install(ctx, play_store) }

    internal var play_install_check: () -> Boolean = { cached_play_install }

    internal var play_retry_delays_ms: List<Long> = PLAY_VERIFY_RETRY_DELAYS_MS

    private var play_config_job: Job? = null

    private var raw_plans: List<AvailablePlan> = AvailablePlansCache.plans()

    private var play_redeem_job: Job? = null

    private var play_redeem_requested = false

    private var play_redeem_force_requested = false

    private var play_account_seen = false

    private var play_account_key: String? = null

    private var play_generation = 0

    private val redeemed_play_tokens = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val conflicted_play_tokens = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private enum class PlayVerifyResult { Confirmed, Pending, Conflict, Ineligible, Failed }

    private fun priced_plans(plans: List<AvailablePlan>, s: BillingUiState = _state.value): List<AvailablePlan> = when {
        s.play_enabled -> apply_play_prices(plans, s.play_offers, s.play_products)
        play_install_check() -> apply_play_prices(plans, emptyList(), emptyList())
        else -> plans
    }

    private fun switch_play_account(id: String?) {
        if (play_account_seen && id == play_account_key) return
        val first = !play_account_seen
        play_account_seen = true
        play_account_key = id
        if (first) return
        play_generation += 1
        play_config_job?.cancel()
        play_config_job = null
        play_redeem_job?.cancel()
        play_redeem_job = null
        play_redeem_requested = false
        play_redeem_force_requested = false
        redeemed_play_tokens.clear()
        conflicted_play_tokens.clear()
        _state.update {
            val next = it.copy(
                play_enabled = false,
                play_account_id = null,
                play_products = emptyList(),
                play_offers = emptyList(),
                play_blocked_reason = null,
                play_currency = null,
                play_purchase_request = null,
                play_addon_products = emptyList(),
                play_special_offer = null,
                play_special_offer_eligible = false,
                play_active_plan = null,
                play_active_addons = emptyList(),
            )
            next.copy(available_plans = priced_plans(raw_plans.ifEmpty { it.available_plans }, next))
        }
    }

    fun ensure_play_config(): Job? {
        if (!play_install_check()) return null
        val existing = play_config_job
        if (existing != null && (existing.isActive || _state.value.play_enabled)) return existing
        return viewModelScope.launch { load_play_config() }.also { play_config_job = it }
    }

    fun load_play_offers() {
        viewModelScope.launch {
            val job = ensure_play_config() ?: return@launch
            job.join()
            if (_state.value.play_enabled && _state.value.play_offers.isEmpty()) refresh_play_offers()
        }
    }

    private suspend fun load_play_config(redeem: Boolean = true): Boolean {
        val generation = play_generation
        val config = try {
            billing_api.get_google_play_config()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_google_play_config failed", t)
            return false
        }
        if (generation != play_generation) return false
        val account_id = config.obfuscated_account_id?.takeIf { it.isNotBlank() }
        if (!config.enabled || account_id == null || config.products.isEmpty()) {
            _state.update {
                val next = it.copy(
                    play_enabled = false,
                    play_account_id = null,
                    play_products = emptyList(),
                    play_offers = emptyList(),
                    play_blocked_reason = null,
                    play_currency = null,
                    play_addon_products = emptyList(),
                    play_special_offer = null,
                    play_special_offer_eligible = false,
                    play_active_plan = null,
                    play_active_addons = emptyList(),
                )
                next.copy(available_plans = priced_plans(raw_plans.ifEmpty { it.available_plans }, next))
            }
            return true
        }
        val product_ids = (config.products.map { it.product_id } + config.addon_products.map { it.product_id }).toSet()
        val fetched = play_store.query_offers(ctx, product_ids.toList())
        if (generation != play_generation) return false
        val offers = fetched.ifEmpty { _state.value.play_offers.filter { it.product_id in product_ids } }
        _state.update {
            val next = it.copy(
                play_enabled = true,
                play_account_id = account_id,
                play_products = config.products,
                play_offers = offers,
                play_blocked_reason = config.purchase_blocked_reason,
                play_currency = play_currency_of(offers),
                play_addon_products = config.addon_products,
                play_special_offer = config.special_offer,
                play_special_offer_eligible = config.special_offer_eligible && config.special_offer != null,
                play_active_plan = config.active_google_play_plan,
                play_active_addons = config.active_google_play_addons,
            )
            next.copy(available_plans = priced_plans(raw_plans.ifEmpty { it.available_plans }, next))
        }
        if (redeem) redeem_play_purchases()
        return true
    }

    private suspend fun refresh_play_offers() {
        val generation = play_generation
        val s = _state.value
        if (!s.play_enabled || s.play_products.isEmpty()) return
        val offers = play_store.query_offers(ctx, play_catalog_ids(s).toList())
        if (offers.isEmpty() || generation != play_generation) return
        _state.update {
            val next = it.copy(play_offers = offers, play_currency = play_currency_of(offers))
            next.copy(available_plans = priced_plans(raw_plans.ifEmpty { it.available_plans }, next))
        }
    }

    private fun finish_play_action(error: Int? = null, info: Int? = null) {
        _state.update {
            it.copy(
                is_acting = false,
                acting_action = null,
                error = error?.let { res -> ctx.getString(res) } ?: it.error,
                info = info?.let { res -> ctx.getString(res) } ?: it.info,
            )
        }
    }

    private suspend fun fresh_subscription(): SubscriptionResponse? = try {
        billing_api.get_subscription().also { sub -> _state.update { it.copy(subscription = sub) } }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (t: Throwable) {
        if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_subscription failed", t)
        _state.value.subscription
    }

    private fun play_currency_of(offers: List<PlayOffer>): String? =
        (offers.firstOrNull { it.offer_id == null } ?: offers.firstOrNull())?.currency_code?.lowercase()

    private fun play_plan_ids(s: BillingUiState = _state.value): Set<String> = s.play_products.map { it.product_id }.toSet()

    private fun play_addon_ids(s: BillingUiState = _state.value): Set<String> = s.play_addon_products.map { it.product_id }.toSet()

    private fun play_catalog_ids(s: BillingUiState = _state.value): Set<String> = play_plan_ids(s) + play_addon_ids(s)

    private fun backend_play_sub_for(product_id: String, s: BillingUiState = _state.value): Boolean =
        if (product_id in play_addon_ids(s)) {
            s.play_active_addons.any { it.product_id == product_id }
        } else {
            is_google_play_provider(s.subscription?.payment_provider) &&
                play_product_for_plan(s.play_products, s.subscription?.plan?.code)?.product_id == product_id
        }

    private fun finish_play_verify(
        result: PlayVerifyResult,
        failed_is_error: Boolean = true,
        success: Int = R.string.billing_play_success,
    ) {
        when (result) {
            PlayVerifyResult.Confirmed -> finish_play_action(info = success)
            PlayVerifyResult.Pending -> finish_play_action(info = R.string.billing_play_pending)
            PlayVerifyResult.Conflict -> finish_play_action(error = R.string.billing_play_blocked)
            PlayVerifyResult.Ineligible -> finish_play_action(error = R.string.billing_play_offer_unavailable)
            PlayVerifyResult.Failed -> if (failed_is_error) {
                finish_play_action(error = R.string.billing_play_verify_failed)
            } else {
                finish_play_action(info = R.string.billing_play_verify_failed)
            }
        }
    }

    private suspend fun refresh_after_play_verify() {
        reload_subscription()
        if (_state.value.storage_addons != null || _state.value.play_active_addons.isNotEmpty()) refresh_storage_addons()
        load_play_config(redeem = false)
    }

    private suspend fun prepare_play_purchase(plan_code: String, billing_interval: String) =
        prepare_play_purchase(PlayTarget.Plan(plan_code, billing_interval))

    private suspend fun prepare_play_purchase(target: PlayTarget) {
        val generation = play_generation
        play_config_job?.takeIf { it.isActive }?.join()
        val loaded = load_play_config(redeem = false)
        if (generation != play_generation) {
            finish_play_action()
            return
        }
        if (!loaded || !_state.value.play_enabled) {
            finish_play_action(error = R.string.billing_play_unavailable)
            ensure_play_config()
            return
        }
        val sub = fresh_subscription()
        val owned = play_store.owned_purchases(ctx)
        if (generation != play_generation) {
            finish_play_action()
            return
        }
        if (owned == null) {
            finish_play_action(error = R.string.billing_play_unavailable)
            return
        }
        val plan_catalog = play_plan_ids()
        val owned_plans = owned.filter { it.is_purchased && !it.is_pending && it.product_ids.any { id -> id in plan_catalog } }
        val is_play_sub = is_google_play_provider(sub?.payment_provider)
        val is_plan_target = target !is PlayTarget.Addon
        if (is_plan_target && !is_play_sub && owned_plans.isNotEmpty()) {
            play_redeem_job?.takeIf { it.isActive }?.join()
            val result = verify_play_purchases(owned_plans, force = true)
            if (generation != play_generation) {
                finish_play_action()
                return
            }
            refresh_after_play_verify()
            finish_play_verify(result)
            return
        }
        if (is_plan_target && !is_play_sub && _state.value.play_blocked_reason == PLAY_BLOCKED_ACTIVE_SUBSCRIPTION) {
            finish_play_action(error = R.string.billing_play_blocked)
            return
        }
        if (target is PlayTarget.SpecialOffer && !_state.value.play_special_offer_eligible) {
            finish_play_action(error = R.string.billing_play_offer_unavailable)
            return
        }
        var offer = play_offer_for_target(target)
        if (offer == null) {
            refresh_play_offers()
            offer = play_offer_for_target(target)
        }
        val account_id = _state.value.play_account_id
        if (offer == null || account_id == null) {
            finish_play_action(
                error = if (target is PlayTarget.SpecialOffer) R.string.billing_play_offer_unavailable else R.string.billing_play_unavailable,
            )
            return
        }
        val old_token = if (is_plan_target && is_play_sub) {
            val current_product = play_product_for_plan(_state.value.play_products, sub?.plan?.code)?.product_id
            val current = owned_plans.firstOrNull { current_product != null && current_product in it.product_ids }
                ?: owned_plans.firstOrNull()
            if (current == null) {
                open_play_subscriptions()
                finish_play_action(info = R.string.billing_play_not_on_device)
                return
            }
            current.purchase_token
        } else {
            null
        }
        val replacement_mode = if (old_token == null) {
            PlayReplacementMode.WITH_TIME_PRORATION
        } else {
            val current_interval = sub?.plan?.billing_period?.takeIf { it.isNotBlank() }?.let { normalize_billing_interval(it) }
            val current_offer = current_interval?.let { interval ->
                play_offer_for(_state.value.play_offers, _state.value.play_products, sub.plan?.code.orEmpty(), interval)
            }
            play_replacement_mode(current_offer, offer)
        }
        _state.update {
            it.copy(
                is_acting = false,
                acting_action = null,
                play_purchase_request = PlayPurchaseRequest(offer, account_id, old_token, replacement_mode),
            )
        }
    }

    private fun play_offer_for_target(target: PlayTarget): PlayOffer? {
        val s = _state.value
        return when (target) {
            is PlayTarget.Plan -> play_offer_for(s.play_offers, s.play_products, target.plan_code, target.billing_interval)
            is PlayTarget.Addon -> play_addon_offer_for(s.play_offers, s.play_addon_products, target.storage_bytes)
            PlayTarget.SpecialOffer -> play_special_offer_for(s.play_offers, s.play_special_offer)
        }
    }

    private fun start_play_target(action: String, target: PlayTarget) {
        if (_state.value.is_acting) {
            _state.update { it.copy(error = ctx.getString(R.string.billing_action_in_progress), info = null) }
            return
        }
        _state.update { it.copy(is_acting = true, acting_action = action, error = null, info = null, checkout_url = null) }
        viewModelScope.launch {
            if (!await_signed_in()) {
                _state.update { it.copy(is_acting = false, acting_action = null, error = ctx.getString(R.string.session_expired_sign_in)) }
                return@launch
            }
            prepare_play_purchase(target)
        }
    }

    fun start_play_special_offer() {
        if (!play_install_check()) return
        start_play_target("play_special_offer", PlayTarget.SpecialOffer)
    }

    fun restore_play_purchases() {
        if (!play_install_check()) return
        if (_state.value.is_acting) {
            _state.update { it.copy(error = ctx.getString(R.string.billing_action_in_progress), info = null) }
            return
        }
        _state.update { it.copy(is_acting = true, acting_action = "play_restore", error = null, info = null) }
        viewModelScope.launch {
            val generation = play_generation
            play_config_job?.takeIf { it.isActive }?.join()
            val loaded = load_play_config(redeem = false)
            if (generation != play_generation) {
                finish_play_action()
                return@launch
            }
            if (!loaded || !_state.value.play_enabled) {
                finish_play_action(error = R.string.billing_play_unavailable)
                return@launch
            }
            val owned = play_store.owned_purchases(ctx)
            if (generation != play_generation) {
                finish_play_action()
                return@launch
            }
            if (owned == null) {
                finish_play_action(error = R.string.billing_play_unavailable)
                return@launch
            }
            val catalog = play_catalog_ids()
            val candidates = owned.filter { it.is_purchased && !it.is_pending && it.product_ids.any { id -> id in catalog } }
            if (candidates.isEmpty()) {
                finish_play_action(info = R.string.billing_play_restore_none)
                return@launch
            }
            play_redeem_job?.takeIf { it.isActive }?.join()
            val result = verify_play_purchases(candidates, force = true)
            if (generation != play_generation) {
                finish_play_action()
                return@launch
            }
            refresh_after_play_verify()
            if (result == PlayVerifyResult.Confirmed) {
                finish_play_action(info = R.string.billing_play_restored)
            } else {
                finish_play_verify(result)
            }
        }
    }

    fun manage_play_addon(product_id: String?) {
        _state.update {
            it.copy(
                is_acting = false,
                acting_action = null,
                portal_url = play_manage_subscription_url(ctx.packageName, product_id),
            )
        }
    }

    fun launch_play_purchase(activity: android.app.Activity) {
        val request = _state.value.play_purchase_request ?: return
        val generation = play_generation
        _state.update {
            it.copy(
                play_purchase_request = null,
                is_acting = true,
                acting_action = "play_${request.offer.product_id}",
                error = null,
                info = null,
            )
        }
        viewModelScope.launch {
            val outcome = try {
                play_store.purchase(
                    activity,
                    request.offer,
                    request.obfuscated_account_id,
                    request.old_purchase_token,
                    request.replacement_mode,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "play purchase failed", t)
                PlayPurchaseOutcome.Failed(-1)
            }
            if (generation != play_generation) {
                finish_play_action()
                return@launch
            }
            when (outcome) {
                is PlayPurchaseOutcome.Purchased -> {
                    val result = verify_play_purchases(outcome.purchases, force = true)
                    if (generation != play_generation) {
                        finish_play_action()
                        return@launch
                    }
                    refresh_after_play_verify()
                    val success = if (request.offer.product_id in play_addon_ids()) {
                        R.string.billing_play_addon_success
                    } else {
                        R.string.billing_play_success
                    }
                    finish_play_verify(result, failed_is_error = false, success = success)
                }
                PlayPurchaseOutcome.Pending -> finish_play_action(info = R.string.billing_play_pending)
                PlayPurchaseOutcome.Cancelled -> finish_play_action()
                PlayPurchaseOutcome.AlreadyOwned -> {
                    finish_play_action()
                    redeem_play_purchases(force = true)
                }
                PlayPurchaseOutcome.Unavailable -> finish_play_action(error = R.string.billing_play_unavailable)
                is PlayPurchaseOutcome.Failed -> finish_play_action(error = R.string.billing_play_failed)
            }
        }
    }

    private suspend fun verify_play_purchases(purchases: List<PlayOwnedPurchase>, force: Boolean = false): PlayVerifyResult {
        val generation = play_generation
        val catalog = play_catalog_ids()
        var pending = false
        var conflict = false
        var ineligible = false
        var failed = false
        for (purchase in purchases.filter { it.is_purchased && !it.is_pending }) {
            if (generation != play_generation) return PlayVerifyResult.Failed
            if (!force && purchase.purchase_token in redeemed_play_tokens) continue
            val product_id = purchase.product_ids.firstOrNull { it in catalog } ?: continue
            try {
                val response = with_play_retry {
                    billing_api.verify_google_play_purchase(
                        GooglePlayVerifyRequest(product_id = product_id, purchase_token = purchase.purchase_token),
                    )
                }
                if (generation != play_generation) return PlayVerifyResult.Failed
                conflicted_play_tokens.remove(purchase.purchase_token)
                if (response.pending) pending = true else redeemed_play_tokens.add(purchase.purchase_token)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (conflict_error: org.astermail.android.api.ApiError.Conflict) {
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "verify_google_play_purchase conflict", conflict_error)
                conflicted_play_tokens.add(purchase.purchase_token)
                if (conflict_error.code == PLAY_SPECIAL_OFFER_INELIGIBLE) ineligible = true else conflict = true
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "verify_google_play_purchase failed", t)
                failed = true
            }
        }
        return when {
            ineligible -> PlayVerifyResult.Ineligible
            conflict -> PlayVerifyResult.Conflict
            failed -> PlayVerifyResult.Failed
            pending -> PlayVerifyResult.Pending
            else -> PlayVerifyResult.Confirmed
        }
    }

    private suspend fun <T> with_play_retry(block: suspend () -> T): T {
        var attempt = 0
        while (true) {
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                if (attempt >= play_retry_delays_ms.size || !is_retryable_play_error(t)) throw t
                delay(play_retry_delays_ms[attempt])
                attempt += 1
            }
        }
    }

    fun redeem_play_purchases(force: Boolean = false) {
        if (!_state.value.play_enabled) return
        if (play_redeem_job?.isActive == true) {
            play_redeem_requested = true
            if (force) play_redeem_force_requested = true
            return
        }
        val generation = play_generation
        play_redeem_job = viewModelScope.launch {
            var run_force = force
            while (generation == play_generation) {
                run_force = run_force || play_redeem_force_requested
                play_redeem_requested = false
                play_redeem_force_requested = false
                run_play_redeem(run_force, generation)
                if (!play_redeem_requested) break
                run_force = false
            }
        }
    }

    private suspend fun run_play_redeem(force: Boolean, generation: Int) {
        val owned = play_store.owned_purchases(ctx) ?: return
        if (generation != play_generation) return
        val s = _state.value
        val catalog = play_catalog_ids(s)
        val candidates = owned.filter { purchase ->
            val product_id = purchase.product_ids.firstOrNull { it in catalog } ?: return@filter false
            purchase.is_purchased && !purchase.is_pending && when {
                force -> true
                purchase.purchase_token in redeemed_play_tokens -> false
                purchase.purchase_token in conflicted_play_tokens -> false
                !purchase.is_acknowledged -> true
                else -> !backend_play_sub_for(product_id, s)
            }
        }
        if (candidates.isEmpty()) return
        val before = redeemed_play_tokens.size
        verify_play_purchases(candidates, force = force)
        if (generation != play_generation) return
        if (force || redeemed_play_tokens.size > before) refresh_after_play_verify()
    }

    private fun open_play_subscriptions() {
        val s = _state.value
        val product_id = play_product_for_plan(s.play_products, s.subscription?.plan?.code)?.product_id
        _state.update {
            it.copy(
                is_acting = false,
                acting_action = null,
                portal_url = play_manage_subscription_url(ctx.packageName, product_id),
            )
        }
    }

    private fun is_play_subscriber(): Boolean = is_google_play_provider(_state.value.subscription?.payment_provider)

    private fun blocks_external_checkout(): Boolean {
        if (!play_install_check()) return false
        _state.update {
            it.copy(
                is_acting = false,
                acting_action = null,
                error = ctx.getString(R.string.billing_play_unavailable),
                info = null,
                checkout_url = null,
            )
        }
        ensure_play_config()
        return true
    }

    private fun blocks_stripe_management(): Boolean {
        if (!play_install_check()) return false
        _state.update { it.copy(is_acting = false, acting_action = null, error = ctx.getString(R.string.billing_play_blocked), info = null) }
        return true
    }

    init {
        _state.update { it.copy(available_plans = priced_plans(it.available_plans, it)) }
        viewModelScope.launch {
            auth_repository.active_account_id.collect { id -> switch_play_account(id) }
        }
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
            val config_job = ensure_play_config()
            try {
                val response = billing_api.get_available_plans()
                AvailablePlansCache.update(response.plans)
                raw_plans = response.plans
                _state.update { it.copy(available_plans = priced_plans(response.plans, it), plans_failed = response.plans.isEmpty()) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_available_plans failed", t)
                _state.update { it.copy(plans_failed = true) }
            }
            if (config_job != null) {
                config_job.join()
                if (_state.value.play_enabled && _state.value.play_offers.isEmpty()) refresh_play_offers()
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
                _state.update {
                    it.copy(
                        error = org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.failed_to_load),
                        ),
                    )
                }
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
                _state.update {
                    it.copy(
                        error = org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.failed_to_load),
                        ),
                    )
                }
            }
        }
    }

    fun start_checkout(plan_code: String, billing_interval: String = "month", currency: String? = null) {
        if (_state.value.is_acting) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.billing_action_in_progress), info = null)
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "checkout_$plan_code", error = null, checkout_url = null)
            if (!await_signed_in()) {
                _state.value = _state.value.copy(is_acting = false, acting_action = null, error = ctx.getString(R.string.session_expired_sign_in))
                return@launch
            }
            if (play_install_check()) {
                prepare_play_purchase(plan_code, billing_interval)
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
                pending_checkout_interval = billing_interval
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url, checkout_abandoned_plan = null, checkout_abandoned_interval = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (is_play_subscriber()) {
            open_play_subscriptions()
            return
        }
        if (blocks_stripe_management()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "portal", error = null, portal_url = null)
            try {
                val response = billing_api.create_portal_session()
                _state.value = _state.value.copy(is_acting = false, acting_action = null, portal_url = response.url)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (is_play_subscriber()) {
            open_play_subscriptions()
            return true
        }
        val chosen_reason = reason?.takeIf { it in CANCEL_REASONS }
        _state.value = _state.value.copy(is_acting = true, acting_action = "cancel", error = null, info = null)
        viewModelScope.launch {
            val password_hash = auth_repository.stored_password_hash_b64()
            if (password_hash == null) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = ctx.getString(R.string.session_expired_sign_in),
                    info = null,
                )
                return@launch
            }
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (is_play_subscriber()) {
            open_play_subscriptions()
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "reactivate", error = null, info = null)
            try {
                billing_api.reactivate_subscription()
                _state.value = _state.value.copy(is_acting = false, acting_action = null, info = ctx.getString(R.string.subscription_reactivated))
                load_subscription()
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (is_play_subscriber()) {
            change_play_subscription(_state.value.subscription?.plan?.code.orEmpty(), billing_interval)
            return
        }
        if (blocks_stripe_management()) return
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = billing_error(t, ctx.getString(R.string.switch_failed)),
                )
            }
        }
    }

    private fun change_play_subscription(plan_code: String, billing_interval: String) {
        _state.update { it.copy(is_acting = true, acting_action = "change_$plan_code", error = null, info = null) }
        viewModelScope.launch {
            if (plan_code.isNotBlank() && play_install_check()) {
                prepare_play_purchase(plan_code, billing_interval)
            } else {
                open_play_subscriptions()
            }
        }
    }

    fun change_plan(plan_code: String, billing_interval: String) {
        if (_state.value.is_acting) return
        if (is_play_subscriber()) {
            change_play_subscription(plan_code, billing_interval)
            return
        }
        if (blocks_stripe_management()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "change_$plan_code", error = null, info = null)
            try {
                billing_api.change_plan(ChangePlanRequest(plan_code = plan_code, billing_interval = billing_interval))
                _state.value = _state.value.copy(is_acting = false, acting_action = null, info = ctx.getString(R.string.plan_changed))
                load_subscription()
            } catch (cancelled: CancellationException) {
                throw cancelled
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
            billing_return_outcome.success -> poll_after_return(returned_paid = true)
            billing_return_outcome.cancelled -> {
                _state.update {
                    it.copy(
                        awaiting_checkout = false,
                        checkout_abandoned_plan = pending_checkout_plan,
                        checkout_abandoned_interval = pending_checkout_interval,
                    )
                }
            }
            billing_return_outcome.open -> load_subscription()
        }
    }

    fun clear_checkout_abandoned() {
        pending_checkout_plan = null
        pending_checkout_interval = null
        _state.update { it.copy(checkout_abandoned_plan = null, checkout_abandoned_interval = null) }
    }

    private fun poll_after_return(returned_paid: Boolean = false) {
        if (poll_job?.isActive == true) return
        _state.update { it.copy(awaiting_checkout = false, awaiting_portal = false, checking_payment = returned_paid) }
        poll_job = viewModelScope.launch {
            val before = snapshot_before_checkout
            val deadline = if (returned_paid) CHECKOUT_POLL_TIMEOUT_MS else CHECKOUT_QUIET_POLL_TIMEOUT_MS
            val started_at = android.os.SystemClock.elapsedRealtime()
            var changed = false
            try {
                while (true) {
                    kotlinx.coroutines.withTimeoutOrNull(CHECKOUT_POLL_REQUEST_TIMEOUT_MS) { reload_subscription() }
                    if (before == null || subscription_signature(_state.value.subscription) != before) {
                        changed = true
                        break
                    }
                    if (android.os.SystemClock.elapsedRealtime() - started_at >= deadline) break
                    delay(CHECKOUT_POLL_INTERVAL_MS)
                }
            } finally {
                if (!changed) _state.update { it.copy(checking_payment = false, is_loading = false) }
            }
            load_payment_methods()
            load_storage_addons()
            val now_paid = is_active_paid(_state.value.subscription)
            _state.update {
                it.copy(
                    checking_payment = false,
                    checkout_abandoned_plan = if (!changed && !returned_paid && pending_checkout_plan != null) {
                        pending_checkout_plan
                    } else {
                        it.checkout_abandoned_plan
                    },
                    checkout_abandoned_interval = if (!changed && !returned_paid && pending_checkout_plan != null) {
                        pending_checkout_interval
                    } else {
                        it.checkout_abandoned_interval
                    },
                    info = when {
                        changed && now_paid -> ctx.getString(R.string.payment_confirmed)
                        !changed && returned_paid -> ctx.getString(R.string.payment_processing_delayed)
                        else -> it.info
                    },
                )
            }
            if (changed) pending_checkout_plan = null
        }
    }

    fun load_plan_change_preview(plan_code: String, billing_interval: String) {
        preview_job?.cancel()
        val tag = plan_code to billing_interval
        preview_request_tag = tag
        preview_job = viewModelScope.launch {
            _state.update {
                it.copy(plan_change_preview = null, plan_change_preview_loading = true, plan_change_preview_failed = false)
            }
            try {
                val preview = billing_api.preview_plan_change(plan_code, billing_interval)
                if (preview_request_tag != tag) return@launch
                _state.update { it.copy(plan_change_preview = preview, plan_change_preview_loading = false) }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "preview_plan_change failed", t)
                if (preview_request_tag != tag) return@launch
                _state.update { it.copy(plan_change_preview_loading = false, plan_change_preview_failed = true) }
            }
        }
    }

    fun clear_plan_change_preview() {
        preview_job?.cancel()
        preview_job = null
        preview_request_tag = null
        _state.update {
            it.copy(plan_change_preview = null, plan_change_preview_loading = false, plan_change_preview_failed = false)
        }
    }

    fun start_crypto_checkout(plan_code: String, term_months: Int) {
        if (_state.value.is_acting) return
        if (blocks_external_checkout()) return
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
                pending_checkout_interval = checkout_interval_for_term_months(term_months)
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url, checkout_abandoned_plan = null, checkout_abandoned_interval = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        if (blocks_external_checkout()) return
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
                if (t is kotlinx.coroutines.CancellationException) throw t
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
                if (t is kotlinx.coroutines.CancellationException) throw t
            } finally {
                pending_crypto_invoices_in_flight = false
            }
        }
    }

    fun create_crypto_native_invoice(plan_code: String, term_months: Int, currency: String, chain: String) {
        if (_state.value.is_acting) return
        if (blocks_external_checkout()) return
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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
            ensure_play_config()
            try {
                val response = billing_api.get_storage_addons()
                _state.value = _state.value.copy(storage_addons = response)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_storage_addons failed", t)
                _state.update {
                    it.copy(
                        error = org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.failed_to_load),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun refresh_storage_addons() {
        try {
            val response = billing_api.get_storage_addons()
            _state.update { it.copy(storage_addons = response) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("BillingVM", "get_storage_addons failed", t)
        }
    }

    fun purchase_storage_addon(addon_id: String) {
        if (_state.value.is_acting) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.billing_action_in_progress), info = null)
            return
        }
        if (play_install_check()) {
            val bytes = _state.value.storage_addons?.available_addons?.firstOrNull { it.id == addon_id }?.storage_bytes
            if (bytes == null || bytes <= 0) {
                _state.update { it.copy(error = ctx.getString(R.string.billing_play_unavailable), info = null) }
                return
            }
            start_play_target("addon_$addon_id", PlayTarget.Addon(bytes))
            return
        }
        if (blocks_external_checkout()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "addon_$addon_id", error = null, checkout_url = null)
            try {
                val response = billing_api.purchase_storage_addon(
                    org.astermail.android.api.billing.PurchaseAddonRequest(addon_id = addon_id)
                )
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url)
            } catch (cancelled: CancellationException) {
                throw cancelled
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
        snapshot_before_checkout = subscription_signature(_state.value.subscription)
        _state.value = _state.value.copy(checkout_url = null, awaiting_checkout = true)
    }

    fun discard_checkout_url() {
        _state.value = _state.value.copy(checkout_url = null)
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
                _state.update {
                    it.copy(
                        error = org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.failed_to_load),
                        ),
                    )
                }
            }
        }
    }

    fun set_default_payment_method(payment_method_id: String) {
        if (_state.value.is_acting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "set_default_$payment_method_id")
            try {
                val response = billing_api.set_default_payment_method(
                    SetDefaultPaymentMethodRequest(payment_method_id),
                )
                val methods = _state.value.payment_methods.map {
                    it.copy(is_default = it.id == payment_method_id)
                }

                if (response.retry_attempted && !response.retry_succeeded) {
                    _state.value = _state.value.copy(
                        is_acting = false,
                        acting_action = null,
                        error = ctx.getString(R.string.default_payment_still_due),
                        payment_methods = methods,
                    )
                    return@launch
                }

                val info = if (response.retry_succeeded) {
                    R.string.default_payment_settled
                } else {
                    R.string.default_payment_updated
                }

                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    info = ctx.getString(info),
                    payment_methods = methods,
                )

                if (response.retry_succeeded) {
                    load_subscription()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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

internal val PLAY_VERIFY_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L)

internal const val PLAY_SPECIAL_OFFER_INELIGIBLE = "SPECIAL_OFFER_INELIGIBLE"

internal fun is_retryable_play_error(t: Throwable): Boolean = when (t) {
    is org.astermail.android.api.ApiError.NetworkError -> true
    is org.astermail.android.api.ApiError.ServerError -> t.code >= 500
    is java.io.IOException -> true
    else -> false
}
