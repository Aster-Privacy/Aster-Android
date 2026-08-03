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

package org.astermail.android.api.domains

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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

object DomainPurchaseConflict : Exception("domain conflict")
object DomainPurchasePaused : Exception("domain purchases paused")

@Serializable
data class DomainSearchResult(
    val domain: String,
    val available: Boolean = false,
    val price_cents: Int? = null,
    val renewal_price_cents: Int? = null,
    val currency: String = "usd",
)

@Serializable
data class DomainSearchResponse(
    val results: List<DomainSearchResult> = emptyList(),
    val suggestions: List<DomainSearchResult> = emptyList(),
    val has_more_suggestions: Boolean = false,
    val next_suggest_page: Int = 1,
)

@Serializable
data class DomainCheckoutRequest(
    val domain: String,
    val years: Int,
    val payment_method: String,
    val captcha_token: String? = null,
)

@Serializable
data class DomainCheckoutResponse(
    val order_id: String = "",
    val checkout_url: String = "",
)

@Serializable
data class DomainOrder(
    val id: String,
    val domain: String = "",
    val status: String = "",
    val order_type: String = "",
    val fulfillment_step: String? = null,
    val years: Int = 1,
    val price_cents: Int = 0,
    val currency: String = "usd",
    val custom_domain_id: String? = null,
    val expires_at: String? = null,
    val last_error: String? = null,
    val created_at: String = "",
)

@Serializable
data class DomainOrderListResponse(
    val orders: List<DomainOrder> = emptyList(),
)

@Serializable
data class DomainOrderCancelResponse(
    val success: Boolean = false,
)

@Serializable
data class DomainOrderRenewRequest(
    val years: Int,
    val payment_method: String,
    val captcha_token: String? = null,
)

interface DomainPurchaseApi {
    suspend fun search(query: String, suggest_page: Int? = null): DomainSearchResponse
    suspend fun checkout(request: DomainCheckoutRequest): DomainCheckoutResponse
    suspend fun list_orders(): DomainOrderListResponse
    suspend fun get_order(order_id: String): DomainOrder
    suspend fun cancel_order(order_id: String): DomainOrderCancelResponse
    suspend fun renew_order(order_id: String, request: DomainOrderRenewRequest): DomainCheckoutResponse
}

class DomainPurchaseApiImpl(private val client: ApiClient) : DomainPurchaseApi {
    private val purchase_base = "/api/addresses/v1/domains/purchase"

    override suspend fun search(query: String, suggest_page: Int?): DomainSearchResponse {
        val response = client.http.get("${client.base_url}$purchase_base/search") {
            parameter("query", query)
            suggest_page?.let { parameter("suggest_page", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun checkout(request: DomainCheckoutRequest): DomainCheckoutResponse {
        val response = client.http.post("${client.base_url}$purchase_base/checkout") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun list_orders(): DomainOrderListResponse {
        val response = client.http.get("${client.base_url}$purchase_base/orders")
        return decode_or_throw(response)
    }

    override suspend fun get_order(order_id: String): DomainOrder {
        val response = client.http.get("${client.base_url}$purchase_base/orders/${order_id.encodeURLPathPart()}")
        return decode_or_throw(response)
    }

    override suspend fun cancel_order(order_id: String): DomainOrderCancelResponse {
        val response = client.http.post("${client.base_url}$purchase_base/orders/${order_id.encodeURLPathPart()}/cancel") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun renew_order(order_id: String, request: DomainOrderRenewRequest): DomainCheckoutResponse {
        val response = client.http.post("${client.base_url}$purchase_base/orders/${order_id.encodeURLPathPart()}/renew") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    private fun parse_server_code(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val obj = client.json.parseToJsonElement(body) as? JsonObject ?: return null
            obj["code"]?.jsonPrimitive?.content
        } catch (_: Throwable) {
            null
        }
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            if (response.status.value == 409) throw DomainPurchaseConflict
            if (parse_server_code(body) == "SERVICE_UNAVAILABLE") throw DomainPurchasePaused
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
