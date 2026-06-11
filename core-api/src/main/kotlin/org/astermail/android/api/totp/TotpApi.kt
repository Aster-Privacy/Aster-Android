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

package org.astermail.android.api.totp

import io.ktor.client.call.body
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
data class TotpStatusResponse(
    val enabled: Boolean = false,
    val backup_codes_remaining: Int = 0,
    val verified_at: String? = null,
)

@Serializable
data class TotpSetupInitiateResponse(
    val secret: String,
    val otpauth_uri: String,
    val setup_token: String,
)

@Serializable
data class TotpSetupVerifyRequest(
    val code: String,
    val setup_token: String,
)

@Serializable
data class TotpSetupVerifyResponse(
    val success: Boolean = false,
    val backup_codes: List<String> = emptyList(),
)

@Serializable
data class TotpDisableRequest(
    val code: String,
    val password_hash: String,
)

@Serializable
data class TotpDisableResponse(val success: Boolean = false)

@Serializable
data class TotpRegenerateBackupCodesRequest(val code: String)

@Serializable
data class TotpRegenerateBackupCodesResponse(val backup_codes: List<String> = emptyList())

interface TotpApi {
    suspend fun status(): TotpStatusResponse
    suspend fun initiate_setup(): TotpSetupInitiateResponse
    suspend fun verify_setup(request: TotpSetupVerifyRequest): TotpSetupVerifyResponse
    suspend fun disable(request: TotpDisableRequest): TotpDisableResponse
    suspend fun regenerate_backup_codes(
        request: TotpRegenerateBackupCodesRequest,
    ): TotpRegenerateBackupCodesResponse
}

class TotpApiImpl(private val client: ApiClient) : TotpApi {
    private val base = "/api/core/v1/auth/totp"

    override suspend fun status(): TotpStatusResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/status"))

    override suspend fun initiate_setup(): TotpSetupInitiateResponse {
        val response = client.http.post("${client.base_url}$base/setup/initiate") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
        }
        return decode_or_throw(response)
    }

    override suspend fun verify_setup(request: TotpSetupVerifyRequest): TotpSetupVerifyResponse {
        val response = client.http.post("${client.base_url}$base/setup/verify") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun disable(request: TotpDisableRequest): TotpDisableResponse {
        val response = client.http.post("${client.base_url}$base/disable") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun regenerate_backup_codes(
        request: TotpRegenerateBackupCodesRequest,
    ): TotpRegenerateBackupCodesResponse {
        val response = client.http.post("${client.base_url}$base/backup-codes/regenerate") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
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
