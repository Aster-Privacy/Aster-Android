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
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient

@Serializable
data class PublicKeyResponse(
    val username: String,
    val public_key: String,
)

@Serializable
data class UpdateVaultRequest(
    val encrypted_vault: String,
    val vault_nonce: String,
    val expected_user_id: String? = null,
    val vault_key_fingerprints: List<String>? = null,
)

@Serializable
data class UpdateVaultResponse(
    val success: Boolean,
)

@Serializable
data class CurrentVaultResponse(
    val encrypted_vault: String,
    val vault_nonce: String,
    val vault_format: Int = 1,
    val updated_at: String? = null,
)

@Serializable
data class AccountKeyTokenResponse(
    val token: String,
    val key_fingerprint: String,
    val version: Long,
    val updated_at: String? = null,
)

@Serializable
data class AccountKeyTokenHistoryEntry(
    val token: String,
    val key_fingerprint: String,
    val version: Long,
    val archived_at: String? = null,
)

@Serializable
data class PutAccountKeyTokenRequest(
    val token: String,
    val key_fingerprint: String,
)

@Serializable
data class AccountKeyTokenHistoryResponse(
    val entries: List<AccountKeyTokenHistoryEntry> = emptyList(),
)

sealed class CurrentVaultResult {
    data class Available(val encrypted_vault: String, val vault_nonce: String) : CurrentVaultResult()
    object Missing : CurrentVaultResult()
    object Unavailable : CurrentVaultResult()
}

@Serializable
data class ExternalKeyFingerprintChange(
    val prior_fingerprint: String,
    val new_fingerprint: String,
    val source: String = "",
    val observed_at: String = "",
)

@Serializable
data class ExternalKeyInfo(
    val email: String,
    val found: Boolean,
    val public_key: String? = null,
    val fingerprint: String? = null,
    val source: String? = null,
    val expires_at: String? = null,
    val fingerprint_change: ExternalKeyFingerprintChange? = null,
)

@Serializable
data class DiscoverKeyRequest(val email: String)

@Serializable
data class DiscoverKeysRequest(val emails: List<String>)

@Serializable
data class DiscoverKeysResponse(val keys: List<ExternalKeyInfo> = emptyList())

@Serializable
data class AcknowledgeFingerprintChangeRequest(
    val email: String,
    val prior_fingerprint: String,
    val new_fingerprint: String,
)

@Serializable
data class AcknowledgeFingerprintChangeResponse(val acknowledged: Boolean = false)

interface KeysApi {
    suspend fun get_recipient_public_key(username: String, email: String? = null): PublicKeyResponse
    suspend fun discover_external_key(email: String): ExternalKeyInfo
    suspend fun discover_external_keys_batch(emails: List<String>): List<ExternalKeyInfo>
    suspend fun acknowledge_external_key_fingerprint_change(
        email: String,
        prior_fingerprint: String,
        new_fingerprint: String,
    ): Boolean
    suspend fun update_vault(
        encrypted_vault: String,
        vault_nonce: String,
        expected_user_id: String?,
        vault_key_fingerprints: List<String>? = null,
    ): Boolean
    suspend fun fetch_current_vault(): CurrentVaultResult
    suspend fun get_account_key_token(): AccountKeyTokenResponse? = null
    suspend fun get_account_key_token_history(): List<AccountKeyTokenHistoryEntry> = emptyList()
    suspend fun get_account_key_token_history_or_null(): List<AccountKeyTokenHistoryEntry>? = null
    suspend fun put_account_key_token_if_absent(
        token: String,
        key_fingerprint: String,
    ): AccountKeyTokenResponse? = null
}

class KeysApiImpl(private val client: ApiClient) : KeysApi {
    private val base = "/api/crypto/v1/keys"

    override suspend fun get_recipient_public_key(username: String, email: String?): PublicKeyResponse {
        val response = client.http.get("${client.base_url}$base/public/${username.encodeURLPathPart()}") {
            if (email != null) parameter("email", email)
        }
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

    override suspend fun discover_external_keys_batch(emails: List<String>): List<ExternalKeyInfo> {
        if (emails.isEmpty()) return emptyList()
        val response = client.http.post("${client.base_url}$base/external/discover/batch") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(DiscoverKeysRequest(emails))
        }
        if (response.status.value !in 200..299) {
            throw client.map_http_status(response.status.value, "")
        }
        return response.body<DiscoverKeysResponse>().keys
    }

    override suspend fun acknowledge_external_key_fingerprint_change(
        email: String,
        prior_fingerprint: String,
        new_fingerprint: String,
    ): Boolean {
        val response = client.http.post(
            "${client.base_url}$base/external/fingerprint-change/acknowledge",
        ) {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(
                AcknowledgeFingerprintChangeRequest(
                    email,
                    prior_fingerprint,
                    new_fingerprint,
                ),
            )
        }
        if (response.status.value !in 200..299) return false
        return response.body<AcknowledgeFingerprintChangeResponse>().acknowledged
    }

    override suspend fun update_vault(
        encrypted_vault: String,
        vault_nonce: String,
        expected_user_id: String?,
        vault_key_fingerprints: List<String>?,
    ): Boolean {
        val response = client.http.put("${client.base_url}$base/vault") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(
                UpdateVaultRequest(
                    encrypted_vault,
                    vault_nonce,
                    expected_user_id,
                    vault_key_fingerprints?.takeIf { it.isNotEmpty() },
                ),
            )
        }
        return response.status.value in 200..299
    }

    override suspend fun fetch_current_vault(): CurrentVaultResult {
        val response = runCatching { client.http.get("${client.base_url}$base/vault") }
            .getOrNull() ?: return CurrentVaultResult.Unavailable
        return when (response.status.value) {
            in 200..299 -> runCatching<CurrentVaultResult> {
                val body: CurrentVaultResponse = response.body()
                CurrentVaultResult.Available(body.encrypted_vault, body.vault_nonce)
            }.getOrDefault(CurrentVaultResult.Unavailable)
            404, 405 -> CurrentVaultResult.Missing
            else -> CurrentVaultResult.Unavailable
        }
    }

    override suspend fun get_account_key_token(): AccountKeyTokenResponse? {
        val response = runCatching { client.http.get("${client.base_url}$base/account-key") }
            .getOrNull() ?: return null
        if (response.status.value !in 200..299) return null
        return runCatching { response.body<AccountKeyTokenResponse>() }.getOrNull()
    }

    override suspend fun get_account_key_token_history(): List<AccountKeyTokenHistoryEntry> {
        val response = runCatching { client.http.get("${client.base_url}$base/account-key/history") }
            .getOrNull() ?: return emptyList()
        if (response.status.value !in 200..299) return emptyList()
        return runCatching { response.body<AccountKeyTokenHistoryResponse>().entries }
            .getOrDefault(emptyList())
    }

    override suspend fun get_account_key_token_history_or_null(): List<AccountKeyTokenHistoryEntry>? {
        val response = runCatching { client.http.get("${client.base_url}$base/account-key/history") }
            .getOrNull() ?: return null
        if (response.status.value !in 200..299) return null
        return runCatching { response.body<AccountKeyTokenHistoryResponse>().entries }.getOrNull()
    }

    override suspend fun put_account_key_token_if_absent(
        token: String,
        key_fingerprint: String,
    ): AccountKeyTokenResponse? {
        val response = runCatching {
            client.http.put("${client.base_url}$base/account-key") {
                contentType(ContentType.Application.Json)
                client.get_csrf()?.let { header("X-CSRF-Token", it) }
                setBody(PutAccountKeyTokenRequest(token, key_fingerprint))
            }
        }.getOrNull() ?: return null
        if (response.status.value !in 200..299) return null
        return runCatching { response.body<AccountKeyTokenResponse>() }.getOrNull()
    }
}
