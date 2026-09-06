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

package org.astermail.android.api.autoforward

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class ForwardingDestinationStatus(
    val address: String = "",
    val is_internal: Boolean = false,
    val confirmed: Boolean = true,
    val confirmation_sent_at: String? = null,
)

@Serializable
data class ForwardingRule(
    val id: String = "",
    val name: String = "",
    val is_enabled: Boolean = true,
    val forward_to: List<String> = emptyList(),
    val keep_copy: Boolean = true,
    val created_at: String? = null,
    val destinations: List<ForwardingDestinationStatus> = emptyList(),
    val pending_confirmation: Boolean = false,
    val last_error_code: String? = null,
    val last_error: String? = null,
    val last_error_address: String? = null,
    val last_failed_at: String? = null,
    val failed_count: Long = 0,
) {
    val target_address: String get() = forward_to.firstOrNull().orEmpty()
    val is_failing: Boolean get() = is_enabled && !last_error_code.isNullOrBlank()
    val encryption_blocked: Boolean get() = last_error_code == "encryption_required_no_key"
    val failing_address: String get() = last_error_address?.takeIf { it.isNotBlank() } ?: target_address
    val enabled: Boolean get() = is_enabled
    val pending_destinations: List<ForwardingDestinationStatus>
        get() = destinations.filter { !it.confirmed }
    val all_destinations_internal: Boolean
        get() = destinations.isNotEmpty() && destinations.all { it.is_internal }
}

@Serializable
data class ForwardingRulesResponse(
    val rules: List<ForwardingRule> = emptyList(),
)

@Serializable
data class CreateForwardingRuleRequest(
    val name: String,
    val forward_to: List<String>,
    val conditions: List<JsonObject> = emptyList(),
    val keep_copy: Boolean = true,
    val priority: Int = 0,
)

@Serializable
data class UpdateForwardingRuleRequest(
    val id: String,
    val name: String? = null,
    val forward_to: List<String>? = null,
    val keep_copy: Boolean? = null,
    val is_enabled: Boolean? = null,
)

@Serializable
data class ToggleForwardingRuleRequest(
    val id: String,
    val is_enabled: Boolean,
)

@Serializable
data class ResendForwardingConfirmationRequest(
    val id: String,
    val address: String,
)

interface AutoForwardApi {
    suspend fun list_rules(): ForwardingRulesResponse
    suspend fun create_rule(request: CreateForwardingRuleRequest): ForwardingRule
    suspend fun update_rule(request: UpdateForwardingRuleRequest)
    suspend fun toggle_rule(request: ToggleForwardingRuleRequest)
    suspend fun delete_rule(rule_id: String)
    suspend fun resend_confirmation(request: ResendForwardingConfirmationRequest)
}

class AutoForwardApiImpl(private val client: ApiClient) : AutoForwardApi {
    private val base = "/api/mail/v1/auto_forward"

    override suspend fun list_rules(): ForwardingRulesResponse {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun create_rule(request: CreateForwardingRuleRequest): ForwardingRule {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_rule(request: UpdateForwardingRuleRequest) {
        val response = client.http.put("${client.base_url}$base/update") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun toggle_rule(request: ToggleForwardingRuleRequest) {
        val response = client.http.patch("${client.base_url}$base/toggle") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun resend_confirmation(request: ResendForwardingConfirmationRequest) {
        val response = client.http.post("${client.base_url}$base/resend_confirmation") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun delete_rule(rule_id: String) {
        val response = client.http.delete("${client.base_url}$base") {
            url { parameters.append("id", rule_id) }
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
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
