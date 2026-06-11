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

package org.astermail.android.api.subscriptions

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class MailingListSubscription(
    val id: String,
    val sender_email: String = "",
    val sender_name: String = "",
    val domain: String = "",
    val email_count: Int = 0,
    val last_received: String = "",
    val unsubscribe_link: String? = null,
    val has_list_unsubscribe: Boolean = false,
    val category: String = "unknown",
    val risk_level: String = "safe",
    val status: String = "active",
)

@Serializable
data class MailingListsResponse(
    val subscriptions: List<MailingListSubscription> = emptyList(),
    val total: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class MailingListStats(
    val total_subscriptions: Int = 0,
    val active: Int = 0,
    val unsubscribed: Int = 0,
    val newsletters: Int = 0,
    val marketing: Int = 0,
    val social: Int = 0,
    val total_emails_from_subscriptions: Int = 0,
)

@Serializable
data class ProxyUnsubscribeRequest(
    val method: String,
    val url: String? = null,
    val mailto_address: String? = null,
    val list_unsubscribe_post: String? = null,
)

@Serializable
data class ProxyUnsubscribeResponse(
    val success: Boolean = false,
    val method: String = "",
    val message: String? = null,
)

@Serializable
data class UnsubscribeRequest(
    val subscription_id: String,
    val method: String = "auto",
)

@Serializable
data class UnsubscribeResult(
    val success: Boolean = false,
    val subscription_id: String = "",
    val message: String? = null,
)

@Serializable
data class BulkUnsubscribeRequest(
    val subscription_ids: List<String>,
)

@Serializable
data class BulkUnsubscribeResult(
    val success: Boolean = false,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
)

@Serializable
data class ScanResult(
    val success: Boolean = false,
    val new_subscriptions: Int = 0,
    val updated_subscriptions: Int = 0,
    val message: String = "",
)

@Serializable
data class TrackSubscriptionRequest(
    val sender_email: String,
    val sender_name: String? = null,
    val unsubscribe_link: String? = null,
    val list_unsubscribe_header: String? = null,
    val category: String? = null,
)

@Serializable
data class TrackSubscriptionResponse(
    val success: Boolean = false,
    val subscription_id: String = "",
    val is_new: Boolean = false,
)

interface SubscriptionsApi {
    suspend fun list(
        limit: Int? = null,
        offset: Int? = null,
        category: String? = null,
        status: String? = null,
        search: String? = null,
    ): MailingListsResponse

    suspend fun stats(): MailingListStats
    suspend fun unsubscribe(request: UnsubscribeRequest): UnsubscribeResult
    suspend fun bulk_unsubscribe(request: BulkUnsubscribeRequest): BulkUnsubscribeResult
    suspend fun proxy_unsubscribe(request: ProxyUnsubscribeRequest): ProxyUnsubscribeResponse
    suspend fun track_subscription(request: TrackSubscriptionRequest): TrackSubscriptionResponse
    suspend fun reactivate(subscription_id: String): UnsubscribeResult
    suspend fun scan(): ScanResult
    suspend fun delete(subscription_id: String): UnsubscribeResult
}

class SubscriptionsApiImpl(private val client: ApiClient) : SubscriptionsApi {
    private val base = "/api/mail/v1/subscriptions"

    override suspend fun list(
        limit: Int?,
        offset: Int?,
        category: String?,
        status: String?,
        search: String?,
    ): MailingListsResponse {
        val response = client.http.get("${client.base_url}$base") {
            limit?.let { parameter("limit", it) }
            offset?.let { parameter("offset", it) }
            category?.let { parameter("category", it) }
            status?.let { parameter("status", it) }
            search?.let { parameter("search", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun stats(): MailingListStats {
        val response = client.http.get("${client.base_url}$base/stats")
        return decode_or_throw(response)
    }

    override suspend fun unsubscribe(request: UnsubscribeRequest): UnsubscribeResult {
        val response = client.http.post("${client.base_url}$base/unsubscribe") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun bulk_unsubscribe(request: BulkUnsubscribeRequest): BulkUnsubscribeResult {
        val response = client.http.post("${client.base_url}$base/bulk-unsubscribe") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun proxy_unsubscribe(request: ProxyUnsubscribeRequest): ProxyUnsubscribeResponse {
        val response = client.http.post("${client.base_url}$base/proxy-unsubscribe") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun track_subscription(request: TrackSubscriptionRequest): TrackSubscriptionResponse {
        val response = client.http.post("${client.base_url}$base/track") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun reactivate(subscription_id: String): UnsubscribeResult {
        val body = mapOf("subscription_id" to subscription_id)
        val response = client.http.post("${client.base_url}$base/reactivate") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(body)
        }
        return decode_or_throw(response)
    }

    override suspend fun scan(): ScanResult {
        val response = client.http.post("${client.base_url}$base/scan") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun delete(subscription_id: String): UnsubscribeResult {
        val response = client.http.delete("${client.base_url}$base/$subscription_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
