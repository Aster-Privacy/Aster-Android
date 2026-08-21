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

package org.astermail.android.api.billing

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class PlanInfo(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val storage_limit_bytes: Long = 0,
    val price_cents: Int = 0,
    val billing_period: String? = null,
)

@Serializable
data class StorageInfo(
    val used_bytes: Long = 0,
    val limit_bytes: Long = 0,
    val total_limit_bytes: Long = 0,
    val percentage_used: Double = 0.0,
    val is_over_limit: Boolean = false,
)

@Serializable
data class SubscriptionResponse(
    val plan: PlanInfo = PlanInfo(),
    val status: String = "",
    val cancel_at_period_end: Boolean = false,
    val current_period_start: String? = null,
    val current_period_end: String? = null,
    val storage: StorageInfo = StorageInfo(),
    val currency: String? = null,
    val payment_failed_at: String? = null,
    val grace_period_end: String? = null,
)

@Serializable
data class AvailablePlan(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val storage_limit_bytes: Long = 0,
    val max_attachment_size_bytes: Long = 0,
    val max_email_aliases: Int = 0,
    val max_custom_domains: Int = 0,
    val price_cents: Int = 0,
    val billing_period: String? = null,
    val stripe_price_id: String? = null,
    val is_current: Boolean = false,
)

@Serializable
data class AvailablePlansResponse(
    val plans: List<AvailablePlan> = emptyList(),
    val current_plan_id: String? = null,
)

@Serializable
data class CheckoutSessionRequest(
    val plan_code: String,
    val billing_interval: String = "month",
    val currency: String? = null,
    val test_mode: Boolean = false,
)

@Serializable
data class CheckoutSessionResponse(
    val session_id: String = "",
    val url: String = "",
)

@Serializable
data class PortalSessionResponse(
    val url: String = "",
)

@Serializable
data class BillingHistoryItem(
    val id: String = "",
    val amount_cents: Int = 0,
    val currency: String = "usd",
    val status: String = "",
    val description: String? = null,
    val plan_name: String? = null,
    val period_start: String? = null,
    val period_end: String? = null,
    val invoice_pdf_url: String? = null,
    val created_at: String = "",
)

@Serializable
data class BillingHistoryResponse(
    val items: List<BillingHistoryItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val per_page: Int = 20,
)

@Serializable
data class CancelSubscriptionRequest(
    val password_hash: String,
)

@Serializable
data class CancelSubscriptionResponse(
    val cancel_at_period_end: Boolean = false,
    val current_period_end: String? = null,
)

@Serializable
data class ReactivateResponse(
    val cancel_at_period_end: Boolean = false,
)

@Serializable
data class SwitchBillingRequest(
    val billing_interval: String,
)

@Serializable
data class SwitchBillingResponse(
    val billing_interval: String = "",
    val new_price_cents: Int = 0,
    val current_period_start: String? = null,
    val current_period_end: String? = null,
)

@Serializable
data class ChangePlanRequest(
    val plan_code: String,
    val billing_interval: String,
)

@Serializable
data class ChangePlanResponse(
    val plan_code: String = "",
    val billing_interval: String = "",
)

@Serializable
data class LimitInfo(
    val limit: Int = 0,
    val current: Int = 0,
    val is_at_limit: Boolean = false,
)

@Serializable
data class StorageLockStatus(
    val used_bytes: Long = 0,
    val limit_bytes: Long = 0,
    val percentage_used: Double = 0.0,
    val is_warning: Boolean = false,
    val is_locked: Boolean = false,
    val lock_started_at: String? = null,
    val days_until_permanent_bounce: Int? = null,
)

@Serializable
data class PlanLimitsResponse(
    val plan_code: String = "free",
    val plan_name: String = "Free",
    val limits: Map<String, LimitInfo> = emptyMap(),
    val storage: StorageLockStatus = StorageLockStatus(),
)

@Serializable
data class PaymentMethodItem(
    val id: String = "",
    val pm_type: String = "",
    val brand: String? = null,
    val last4: String? = null,
    val exp_month: Int? = null,
    val exp_year: Int? = null,
    val display_name: String = "",
    val is_default: Boolean = false,
)

@Serializable
data class PaymentMethodsListResponse(
    val payment_methods: List<PaymentMethodItem> = emptyList(),
)

@Serializable
data class SetupIntentResponse(
    val client_secret: String = "",
)

@Serializable
data class SetDefaultPaymentMethodRequest(
    val payment_method_id: String,
)

@Serializable
data class DetachPaymentMethodRequest(
    val payment_method_id: String,
)

@Serializable
data class GenericSuccessResponse(
    val success: Boolean = true,
)

@Serializable
data class StorageAddonItem(
    val id: String = "",
    val name: String = "",
    val storage_bytes: Long = 0,
    val price_cents: Int = 0,
    val billing_period: String = "month",
    val is_active: Boolean = true,
)

@Serializable
data class UserActiveAddon(
    val user_addon_id: String = "",
    val addon_id: String = "",
    val size_label: String = "",
    val size_bytes: Long = 0,
    val price_cents: Int = 0,
    val state: String = "",
    val created_at: String = "",
    val cancel_at_period_end: Boolean = false,
    val current_period_end: String? = null,
)

@Serializable
data class StorageAddonsResponse(
    val available_addons: List<StorageAddonItem> = emptyList(),
    val active_addons: List<UserActiveAddon> = emptyList(),
)

@Serializable
data class PurchaseAddonRequest(
    val addon_id: String,
)

@Serializable
data class PurchaseAddonResponse(
    val url: String = "",
)

@Serializable
data class CryptoCheckoutRequest(
    val plan_code: String,
    val term_months: Int,
)

@Serializable
data class CryptoAddonCheckoutRequest(
    val addon_id: String,
    val term_months: Int,
)

@Serializable
data class CryptoNativeCoin(
    val currency: String = "",
    val chain: String = "",
    val display_name: String = "",
    val decimals: Int = 0,
    val recommended: Boolean = false,
)

@Serializable
data class CryptoNativeCoinsResponse(
    val enabled: Boolean = false,
    val coins: List<CryptoNativeCoin> = emptyList(),
)

@Serializable
data class CreateCryptoNativeInvoiceRequest(
    val plan_code: String,
    val term_months: Int,
    val currency: String,
    val chain: String,
)

@Serializable
data class CryptoNativeInvoiceResponse(
    val id: String = "",
    val currency: String = "",
    val chain: String = "",
    val display_name: String = "",
    val address: String = "",
    val amount_atomic: String = "0",
    val amount_decimal: String = "0",
    val decimals: Int = 0,
    val usd_cents: Long = 0,
    val rate_locked_usd: String = "0",
    val payment_uri: String = "",
    val min_confirmations: Int = 0,
    val status: String = "",
    val expires_at: String = "",
    val created_at: String = "",
)

@Serializable
data class CryptoNativeInvoiceStatus(
    val id: String = "",
    val currency: String = "",
    val chain: String = "",
    val display_name: String = "",
    val address: String = "",
    val amount_atomic: String = "0",
    val amount_decimal: String = "0",
    val amount_received_atomic: String = "0",
    val amount_received_decimal: String = "0",
    val amount_due_atomic: String = "",
    val amount_due_decimal: String = "",
    val decimals: Int = 0,
    val usd_cents: Long = 0,
    val status: String = "",
    val confirmations: Int = 0,
    val min_confirmations: Int = 0,
    val txids: List<String> = emptyList(),
    val payment_uri: String = "",
    val expires_at: String = "",
    val watch_until: String = "",
    val created_at: String = "",
    val completed_at: String? = null,
    val server_time: String = "",
)

@Serializable
data class CryptoNativeCancelResponse(
    val id: String = "",
    val status: String = "",
)

@Serializable
data class CryptoNativePendingInvoice(
    val id: String = "",
    val currency: String = "",
    val chain: String = "",
    val display_name: String = "",
    val status: String = "",
    val usd_cents: Long = 0,
    val amount_decimal: String = "0",
    val expires_at: String = "",
    val created_at: String = "",
)

@Serializable
data class CryptoNativePendingInvoicesResponse(
    val invoices: List<CryptoNativePendingInvoice> = emptyList(),
)

interface BillingApi {
    suspend fun get_subscription(): SubscriptionResponse
    suspend fun get_available_plans(): AvailablePlansResponse
    suspend fun get_plan_limits(): PlanLimitsResponse
    suspend fun create_checkout_session(request: CheckoutSessionRequest): CheckoutSessionResponse
    suspend fun create_portal_session(): PortalSessionResponse
    suspend fun get_billing_history(page: Int = 1, per_page: Int = 20): BillingHistoryResponse
    suspend fun cancel_subscription(request: CancelSubscriptionRequest): CancelSubscriptionResponse
    suspend fun reactivate_subscription(): ReactivateResponse
    suspend fun switch_billing_interval(request: SwitchBillingRequest): SwitchBillingResponse
    suspend fun change_plan(request: ChangePlanRequest): ChangePlanResponse
    suspend fun list_payment_methods(): PaymentMethodsListResponse
    suspend fun set_default_payment_method(request: SetDefaultPaymentMethodRequest): GenericSuccessResponse
    suspend fun detach_payment_method(request: DetachPaymentMethodRequest): GenericSuccessResponse
    suspend fun get_storage_addons(): StorageAddonsResponse
    suspend fun purchase_storage_addon(request: PurchaseAddonRequest): PurchaseAddonResponse
    suspend fun create_crypto_checkout_session(request: CryptoCheckoutRequest): CheckoutSessionResponse
    suspend fun purchase_storage_addon_crypto(request: CryptoAddonCheckoutRequest): PurchaseAddonResponse
    suspend fun get_crypto_native_coins(): CryptoNativeCoinsResponse
    suspend fun create_crypto_native_invoice(request: CreateCryptoNativeInvoiceRequest): CryptoNativeInvoiceResponse
    suspend fun get_crypto_native_invoice(invoice_id: String): CryptoNativeInvoiceStatus
    suspend fun cancel_crypto_native_invoice(invoice_id: String): CryptoNativeCancelResponse
    suspend fun list_pending_crypto_invoices(): CryptoNativePendingInvoicesResponse
}

class BillingApiImpl(private val client: ApiClient) : BillingApi {
    private val base = "/api/payments/v1"

    override suspend fun get_subscription(): SubscriptionResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/subscription"))

    override suspend fun get_available_plans(): AvailablePlansResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/plans"))

    override suspend fun get_plan_limits(): PlanLimitsResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/plans/limits"))

    override suspend fun create_checkout_session(request: CheckoutSessionRequest): CheckoutSessionResponse {
        val response = client.http.post("${client.base_url}$base/checkout-session") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun create_portal_session(): PortalSessionResponse {
        val response = client.http.post("${client.base_url}$base/portal-session") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun get_billing_history(page: Int, per_page: Int): BillingHistoryResponse {
        val response = client.http.get("${client.base_url}$base/history") {
            parameter("page", page)
            parameter("per_page", per_page)
        }
        return decode_or_throw(response)
    }

    override suspend fun cancel_subscription(request: CancelSubscriptionRequest): CancelSubscriptionResponse {
        val response = client.http.post("${client.base_url}$base/cancel") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun reactivate_subscription(): ReactivateResponse {
        val response = client.http.post("${client.base_url}$base/reactivate") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun switch_billing_interval(request: SwitchBillingRequest): SwitchBillingResponse {
        val response = client.http.post("${client.base_url}$base/switch-billing") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun change_plan(request: ChangePlanRequest): ChangePlanResponse {
        val response = client.http.post("${client.base_url}$base/change-plan") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun list_payment_methods(): PaymentMethodsListResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/payment-methods"))

    override suspend fun set_default_payment_method(request: SetDefaultPaymentMethodRequest): GenericSuccessResponse {
        val response = client.http.post("${client.base_url}$base/payment-methods/default") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun detach_payment_method(request: DetachPaymentMethodRequest): GenericSuccessResponse {
        val response = client.http.post("${client.base_url}$base/payment-methods/detach") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun get_storage_addons(): StorageAddonsResponse =
        decode_or_throw(client.http.get("${client.base_url}/api/sync/v1/storage/addons"))

    override suspend fun purchase_storage_addon(request: PurchaseAddonRequest): PurchaseAddonResponse {
        val response = client.http.post("${client.base_url}/api/sync/v1/storage/addons/purchase") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun create_crypto_checkout_session(request: CryptoCheckoutRequest): CheckoutSessionResponse {
        val response = client.http.post("${client.base_url}$base/crypto/checkout-session") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun purchase_storage_addon_crypto(request: CryptoAddonCheckoutRequest): PurchaseAddonResponse {
        val response = client.http.post("${client.base_url}/api/sync/v1/storage/addons/crypto-checkout") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun get_crypto_native_coins(): CryptoNativeCoinsResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/crypto-native/coins"))

    override suspend fun create_crypto_native_invoice(request: CreateCryptoNativeInvoiceRequest): CryptoNativeInvoiceResponse {
        val response = client.http.post("${client.base_url}$base/crypto-native/invoice") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun get_crypto_native_invoice(invoice_id: String): CryptoNativeInvoiceStatus =
        decode_or_throw(
            client.http.get(
                "${client.base_url}$base/crypto-native/invoice/${invoice_id.encodeURLPathPart()}",
            ),
        )

    override suspend fun cancel_crypto_native_invoice(invoice_id: String): CryptoNativeCancelResponse {
        val encoded_id = invoice_id.encodeURLPathPart()
        val response = client.http.post("${client.base_url}$base/crypto-native/invoice/$encoded_id/cancel") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun list_pending_crypto_invoices(): CryptoNativePendingInvoicesResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/crypto-native/invoices/pending"))

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        val response_content_type = response.contentType()
        if (response_content_type != null && !response_content_type.match(ContentType.Application.Json)) {
            throw ApiError.ServerError(response.status.value)
        }
        return try {
            response.body()
        } catch (t: Throwable) {
            throw ApiError.ServerError(response.status.value)
        }
    }
}
