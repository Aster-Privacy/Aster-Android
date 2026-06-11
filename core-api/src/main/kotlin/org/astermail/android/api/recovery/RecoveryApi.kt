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

package org.astermail.android.api.recovery

import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class InitiateRecoveryRequest(
    val code_hash: String,
    val email: String,
)

@Serializable
data class InitiateRecoveryResponse(
    val encrypted_vault_backup: String,
    val vault_backup_nonce: String,
    val recovery_key_salt: String,
    val encrypted_recovery_key: String,
    val recovery_key_nonce: String,
    val code_salt: String,
    val recovery_token: String,
)

@Serializable
data class InitiateEmailRecoveryRequest(
    val email: String,
)

@Serializable
data class InitiateEmailRecoveryResponse(
    val success: Boolean,
)

@Serializable
data class ValidateEmailRecoveryRequest(
    val token: String,
)

@Serializable
data class ValidateEmailRecoveryResponse(
    val encrypted_vault_backup: String,
    val vault_backup_nonce: String,
    val vault_backup_salt: String,
    val email_vault_key: String,
    val recovery_token: String,
)

@Serializable
data class RecoveryShareData(
    val code_hash: String,
    val code_salt: String,
    val encrypted_recovery_key: String,
    val recovery_key_nonce: String,
)

@Serializable
data class NewEmailRecoveryBackup(
    val encrypted_vault_backup: String,
    val vault_backup_nonce: String,
    val vault_backup_salt: String,
    val email_vault_key: String,
)

@Serializable
data class CompleteRecoveryRequest(
    val recovery_token: String,
    val new_password_hash: String,
    val new_password_salt: String,
    val new_encrypted_vault: String,
    val new_vault_nonce: String,
    val new_recovery_shares: List<RecoveryShareData>,
    val new_encrypted_vault_backup: String,
    val new_vault_backup_nonce: String,
    val new_recovery_key_salt: String,
    val new_email_recovery_backup: NewEmailRecoveryBackup? = null,
)

@Serializable
data class CompleteRecoveryResponse(
    val success: Boolean,
)

interface RecoveryApi {
    suspend fun initiate(request: InitiateRecoveryRequest): InitiateRecoveryResponse
    suspend fun initiate_email(request: InitiateEmailRecoveryRequest): InitiateEmailRecoveryResponse
    suspend fun validate_email(request: ValidateEmailRecoveryRequest): ValidateEmailRecoveryResponse
    suspend fun complete(request: CompleteRecoveryRequest): CompleteRecoveryResponse
}

class RecoveryApiImpl(private val client: ApiClient) : RecoveryApi {
    private val base = "/api/core/v1/recovery"

    override suspend fun initiate(request: InitiateRecoveryRequest): InitiateRecoveryResponse {
        val response = client.http.post("${client.base_url}$base/initiate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun initiate_email(request: InitiateEmailRecoveryRequest): InitiateEmailRecoveryResponse {
        val response = client.http.post("${client.base_url}$base/email-initiate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun validate_email(request: ValidateEmailRecoveryRequest): ValidateEmailRecoveryResponse {
        val response = client.http.post("${client.base_url}$base/email-validate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun complete(request: CompleteRecoveryRequest): CompleteRecoveryResponse {
        val response = client.http.post("${client.base_url}$base/complete") {
            contentType(ContentType.Application.Json)
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
