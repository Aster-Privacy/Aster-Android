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

package org.astermail.android.api.recovery_email

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class RecoveryEmailStateResponse(
    val encrypted_email: String? = null,
    val email_nonce: String? = null,
    val verified: Boolean = false,
    val has_server_enc: Boolean = false,
    val exists: Boolean? = null,
    val step_up_required: Boolean? = null,
)

@Serializable
data class SaveRecoveryEmailRequest(
    val encrypted_email: String,
    val email_nonce: String,
    val email_hash: String,
    val plaintext_email: String,
    val password_hash: String? = null,
    val totp_code: String? = null,
)

@Serializable
data class RemoveRecoveryEmailRequest(
    val password_hash: String,
    val totp_code: String? = null,
)

@Serializable
data class ResendRecoveryEmailRequest(
    val plaintext_email: String? = null,
)

@Serializable
private data class RecoveryEmailErrorBody(
    val error: String? = null,
    val code: String? = null,
)

class RecoveryEmailError(
    val code: String,
    val user_message: String? = null,
) : Throwable(user_message ?: code)

@Serializable
data class RecoveryEmailSuccessResponse(
    val success: Boolean = false,
)

interface RecoveryEmailApi {
    suspend fun get_state(): RecoveryEmailStateResponse
    suspend fun save(request: SaveRecoveryEmailRequest): RecoveryEmailSuccessResponse
    suspend fun resend(plaintext_email: String?): RecoveryEmailSuccessResponse
    suspend fun remove(request: RemoveRecoveryEmailRequest): RecoveryEmailSuccessResponse
}

class RecoveryEmailApiImpl(private val client: ApiClient) : RecoveryEmailApi {
    private val base = "/api/core/v1/recovery/email"

    override suspend fun get_state(): RecoveryEmailStateResponse {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun save(request: SaveRecoveryEmailRequest): RecoveryEmailSuccessResponse {
        val response = client.http.put("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun resend(plaintext_email: String?): RecoveryEmailSuccessResponse {
        val response = client.http.post("${client.base_url}$base/resend") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(ResendRecoveryEmailRequest(plaintext_email = plaintext_email))
        }
        return decode_or_throw(response)
    }

    override suspend fun remove(request: RemoveRecoveryEmailRequest): RecoveryEmailSuccessResponse {
        val response = client.http.delete("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        val status = response.status.value
        if (status !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            val parsed = try {
                error_json.decodeFromString<RecoveryEmailErrorBody>(body)
            } catch (_: Throwable) {
                null
            }
            val message = parsed?.error?.takeIf { it.isNotBlank() }
            when {
                parsed?.code == "STEP_UP_REQUIRED" ->
                    throw RecoveryEmailError(STEP_UP_REQUIRED, message)
                parsed?.code == "TOTP_REQUIRED" ->
                    throw RecoveryEmailError(TOTP_REQUIRED, message)
                parsed?.code == "INVALID_INPUT" ->
                    throw RecoveryEmailError(INVALID_INPUT, message)
                parsed?.code == "EMAIL_SEND_FAILED" ->
                    throw RecoveryEmailError(EMAIL_SEND_FAILED, message)
                status == 409 -> throw ApiError.UnknownError(RECOVERY_EMAIL_IN_USE)
                status == 429 -> throw ApiError.UnknownError(RECOVERY_EMAIL_COOLDOWN)
                else -> throw client.map_http_status(status, body)
            }
        }
        return try {
            response.body()
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }

    companion object {
        const val RECOVERY_EMAIL_IN_USE = "recovery_email_in_use"
        const val RECOVERY_EMAIL_COOLDOWN = "recovery_email_cooldown"
        const val STEP_UP_REQUIRED = "step_up_required"
        const val TOTP_REQUIRED = "totp_required"
        const val INVALID_INPUT = "invalid_input"
        const val EMAIL_SEND_FAILED = "email_send_failed"

        private val error_json = Json { ignoreUnknownKeys = true }
    }
}
