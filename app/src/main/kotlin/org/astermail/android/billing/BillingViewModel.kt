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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import org.astermail.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.BillingHistoryItem
import org.astermail.android.api.billing.CancelSubscriptionRequest
import org.astermail.android.api.billing.CheckoutSessionRequest
import org.astermail.android.api.billing.DetachPaymentMethodRequest
import org.astermail.android.api.billing.PaymentMethodItem
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
    val is_loading: Boolean = false,
    val is_acting: Boolean = false,
    val acting_action: String? = null,
    val error: String? = null,
    val info: String? = null,
    val checkout_url: String? = null,
    val portal_url: String? = null,
    val awaiting_checkout: Boolean = false,
    val awaiting_portal: Boolean = false,
)

@HiltViewModel
class BillingViewModel @Inject constructor(
    application: Application,
    private val billing_api: BillingApi,
    private val auth_repository: AuthRepository,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _state = MutableStateFlow(BillingUiState())
    val state: StateFlow<BillingUiState> = _state.asStateFlow()

    fun load_all() {
        load_subscription()
        load_plans()
        load_limits()
        load_history()
    }

    fun load_subscription() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            try {
                val sub = billing_api.get_subscription()
                _state.value = _state.value.copy(subscription = sub, is_loading = false, error = null)
            } catch (t: Throwable) {
                android.util.Log.w("BillingVM", "get_subscription failed", t)
                _state.value = _state.value.copy(
                    is_loading = false,
                    subscription = null,
                    error = null,
                )
            }
        }
    }

    fun load_plans() {
        viewModelScope.launch {
            try {
                val response = billing_api.get_available_plans()
                _state.value = _state.value.copy(available_plans = response.plans)
            } catch (_: Throwable) {
            }
        }
    }

    fun load_limits() {
        viewModelScope.launch {
            try {
                val limits = billing_api.get_plan_limits()
                _state.value = _state.value.copy(limits = limits)
            } catch (_: Throwable) {
            }
        }
    }

    fun load_history() {
        viewModelScope.launch {
            try {
                val response = billing_api.get_billing_history(page = 1, per_page = 20)
                _state.value = _state.value.copy(history = response.items)
            } catch (_: Throwable) {
            }
        }
    }

    fun start_checkout(plan_code: String, billing_interval: String = "month") {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "checkout_$plan_code", error = null, checkout_url = null)
            try {
                val response = billing_api.create_checkout_session(
                    CheckoutSessionRequest(plan_code = plan_code, billing_interval = billing_interval),
                )
                _state.value = _state.value.copy(is_acting = false, acting_action = null, checkout_url = response.url)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = t.message ?: ctx.getString(R.string.could_not_start_checkout),
                )
            }
        }
    }

    fun open_portal() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "portal", error = null, portal_url = null)
            try {
                val response = billing_api.create_portal_session()
                _state.value = _state.value.copy(is_acting = false, acting_action = null, portal_url = response.url)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = t.message ?: ctx.getString(R.string.could_not_open_portal),
                )
            }
        }
    }

    fun cancel_subscription(password: String) {
        val password_hash = auth_repository.derive_password_hash_b64(password)
        if (password_hash == null) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.session_expired_sign_in))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "cancel", error = null, info = null)
            try {
                val response = billing_api.cancel_subscription(
                    CancelSubscriptionRequest(password_hash = password_hash),
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
                    error = t.message ?: ctx.getString(R.string.cancel_failed),
                )
            }
        }
    }

    fun reactivate_subscription() {
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
                    error = t.message ?: ctx.getString(R.string.reactivate_failed),
                )
            }
        }
    }

    fun switch_billing(billing_interval: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_acting = true, acting_action = "switch", error = null, info = null)
            try {
                val response = billing_api.switch_billing_interval(
                    SwitchBillingRequest(billing_interval = billing_interval),
                )
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    info = ctx.getString(R.string.billing_changed_to, response.billing_interval),
                )
                load_subscription()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    is_acting = false,
                    acting_action = null,
                    error = t.message ?: ctx.getString(R.string.switch_failed),
                )
            }
        }
    }

    fun consume_checkout_url() {
        _state.value = _state.value.copy(checkout_url = null, awaiting_checkout = true)
    }

    fun consume_portal_url() {
        _state.value = _state.value.copy(portal_url = null, awaiting_portal = true)
    }

    fun on_resume() {
        val s = _state.value
        if (s.awaiting_checkout || s.awaiting_portal) {
            _state.value = s.copy(awaiting_checkout = false, awaiting_portal = false)
            load_subscription()
            load_payment_methods()
        }
    }

    fun clear_messages() {
        _state.value = _state.value.copy(error = null, info = null)
    }

    fun load_payment_methods() {
        viewModelScope.launch {
            try {
                val response = billing_api.list_payment_methods()
                _state.value = _state.value.copy(payment_methods = response.payment_methods)
            } catch (_: Throwable) {
            }
        }
    }

    fun set_default_payment_method(payment_method_id: String) {
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
                    error = t.message ?: ctx.getString(R.string.set_default_failed),
                )
            }
        }
    }

    fun detach_payment_method(payment_method_id: String) {
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
                    error = t.message ?: ctx.getString(R.string.remove_payment_failed),
                )
            }
        }
    }
}
