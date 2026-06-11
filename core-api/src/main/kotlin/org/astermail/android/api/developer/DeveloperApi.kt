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

package org.astermail.android.api.developer

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class ApiKeyInfo(
    val id: String = "",
    val name: String = "",
    val prefix: String = "",
    val created_at: String? = null,
    val last_used_at: String? = null,
)

@Serializable
data class ApiKeyListResponse(
    val api_keys: List<ApiKeyInfo> = emptyList(),
)

@Serializable
data class CreateApiKeyRequest(
    val name: String,
)

@Serializable
data class CreateApiKeyResponse(
    val id: String = "",
    val key: String = "",
    val prefix: String = "",
    val success: Boolean = false,
)

@Serializable
data class WebhookInfo(
    val id: String = "",
    val url: String = "",
    val events: List<String> = emptyList(),
    val enabled: Boolean = true,
    val created_at: String? = null,
)

@Serializable
data class WebhookListResponse(
    val webhooks: List<WebhookInfo> = emptyList(),
)

interface DeveloperApi {
    suspend fun list_api_keys(): ApiKeyListResponse
    suspend fun create_api_key(request: CreateApiKeyRequest): CreateApiKeyResponse
    suspend fun revoke_api_key(key_id: String)
    suspend fun list_webhooks(): WebhookListResponse
}

class DeveloperApiImpl(private val client: ApiClient) : DeveloperApi {
    private val base = "/api/developer/v1"

    override suspend fun list_api_keys(): ApiKeyListResponse {
        val response = client.http.get("${client.base_url}$base/api-keys")
        return decode_or_throw(response)
    }

    override suspend fun create_api_key(request: CreateApiKeyRequest): CreateApiKeyResponse {
        val response = client.http.post("${client.base_url}$base/api-keys") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun revoke_api_key(key_id: String) {
        val response = client.http.delete("${client.base_url}$base/api-keys/$key_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
    }

    override suspend fun list_webhooks(): WebhookListResponse {
        val response = client.http.get("${client.base_url}$base/webhooks")
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
