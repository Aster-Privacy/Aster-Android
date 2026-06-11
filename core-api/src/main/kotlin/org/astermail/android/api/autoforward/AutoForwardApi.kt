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
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class ForwardingRule(
    val id: String = "",
    val target_address: String = "",
    val enabled: Boolean = true,
    val keep_copy: Boolean = true,
    val created_at: String? = null,
)

@Serializable
data class ForwardingRulesResponse(
    val rules: List<ForwardingRule> = emptyList(),
)

@Serializable
data class CreateForwardingRuleRequest(
    val target_address: String,
    val keep_copy: Boolean = true,
)

@Serializable
data class UpdateForwardingRuleRequest(
    val target_address: String? = null,
    val keep_copy: Boolean? = null,
)

@Serializable
data class ToggleForwardingRuleRequest(
    val id: String,
    val enabled: Boolean,
)

interface AutoForwardApi {
    suspend fun list_rules(): ForwardingRulesResponse
    suspend fun create_rule(request: CreateForwardingRuleRequest): ForwardingRule
    suspend fun update_rule(request: UpdateForwardingRuleRequest)
    suspend fun toggle_rule(request: ToggleForwardingRuleRequest)
    suspend fun delete_rule(rule_id: String)
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

    override suspend fun delete_rule(rule_id: String) {
        val response = client.http.delete("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(mapOf("id" to rule_id))
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
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
