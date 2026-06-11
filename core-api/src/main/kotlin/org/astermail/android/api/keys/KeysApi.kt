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

package org.astermail.android.api.keys

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient

@Serializable
data class PublicKeyResponse(
    val username: String,
    val public_key: String,
)

@Serializable
data class ExternalKeyInfo(
    val email: String,
    val found: Boolean,
    val public_key: String? = null,
    val fingerprint: String? = null,
    val source: String? = null,
    val expires_at: String? = null,
)

@Serializable
data class DiscoverKeyRequest(val email: String)

interface KeysApi {
    suspend fun get_recipient_public_key(username: String, email: String? = null): PublicKeyResponse
    suspend fun discover_external_key(email: String): ExternalKeyInfo
}

class KeysApiImpl(private val client: ApiClient) : KeysApi {
    private val base = "/api/crypto/v1/keys"

    override suspend fun get_recipient_public_key(username: String, email: String?): PublicKeyResponse {
        val suffix = if (email != null) "?email=$email" else ""
        val response = client.http.get("${client.base_url}$base/public/$username$suffix")
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return response.body()
    }

    override suspend fun discover_external_key(email: String): ExternalKeyInfo {
        val response = client.http.post("${client.base_url}$base/external/discover") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(DiscoverKeyRequest(email))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return response.body()
    }
}
