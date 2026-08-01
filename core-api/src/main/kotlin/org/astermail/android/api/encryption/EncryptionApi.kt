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

package org.astermail.android.api.encryption

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
data class PgpKeyInfo(
    val fingerprint: String = "",
    val key_id: String = "",
    val algorithm: String = "",
    val key_size: Int = 0,
    val created_at: String = "",
    val expires_at: String? = null,
    val public_key_armored: String = "",
    val decrypt_count: Int = 0,
    val last_used_decrypt_at: String? = null,
)

@Serializable
data class RecoveryCodesStatus(
    val total_codes: Int = 0,
    val available_codes: Int = 0,
    val created_at: String? = null,
)

@Serializable
data class RegenerateRecoveryCodesResponse(
    val codes: List<String> = emptyList(),
    val info: RecoveryCodesStatus = RecoveryCodesStatus(),
)

@Serializable
data class EncryptionSettings(
    val auto_discover_keys: Boolean = true,
    val encrypt_by_default: Boolean = false,
)

@Serializable
data class UpdateEncryptionSettingsRequest(
    val auto_discover_keys: Boolean? = null,
    val encrypt_by_default: Boolean? = null,
)

@Serializable
data class WkdStatusResponse(
    val published: Boolean = false,
    val url: String? = null,
)

@Serializable
data class KeyserverStatusResponse(
    val published: Boolean = false,
    val fingerprint: String? = null,
)

@Serializable
data class PublishKeyResponse(
    val success: Boolean = false,
    val url: String? = null,
)

@Serializable
data class ExportKeyRequest(
    val include_private: Boolean = false,
    val password_hash: String = "",
    val format: String = "armored",
    val totp_code: String? = null,
)

@Serializable
data class ExportKeyResponse(
    val public_key_armored: String = "",
    val fingerprint: String = "",
    val private_key_encrypted: String? = null,
    val encrypted_private_key_blob: String? = null,
    val private_key_nonce: String? = null,
    val client_side_decryption: Boolean = false,
)

@Serializable
data class EncryptionSaltResponse(
    val salt: String = "",
    val totp_required: Boolean = false,
)

@Serializable
data class RepublishPgpKeyRequest(
    val fingerprint: String,
    val key_id: String,
    val public_key_armored: String,
    val encrypted_private_key: String,
    val private_key_nonce: String,
    val algorithm: String = "ecc_curve25519",
    val key_size: Int = 256,
)

@Serializable
data class RepublishPgpKeyResponse(
    val fingerprint: String = "",
    val success: Boolean = false,
)

interface EncryptionApi {
    suspend fun get_pgp_key_info(): PgpKeyInfo
    suspend fun get_recovery_codes_status(): RecoveryCodesStatus
    suspend fun get_encryption_settings(): EncryptionSettings
    suspend fun update_encryption_settings(request: UpdateEncryptionSettingsRequest): Boolean
    suspend fun get_encryption_salt(): EncryptionSaltResponse
    suspend fun export_public_key(): ExportKeyResponse
    suspend fun export_private_key(request: ExportKeyRequest): ExportKeyResponse
    suspend fun regenerate_recovery_codes(): RegenerateRecoveryCodesResponse
    suspend fun get_wkd_status(): WkdStatusResponse
    suspend fun get_keyserver_status(): KeyserverStatusResponse
    suspend fun publish_to_wkd(): PublishKeyResponse
    suspend fun unpublish_from_wkd(): PublishKeyResponse
    suspend fun publish_to_keyserver(): PublishKeyResponse
    suspend fun republish_pgp_key(request: RepublishPgpKeyRequest): RepublishPgpKeyResponse
}

class EncryptionApiImpl(private val client: ApiClient) : EncryptionApi {
    private val crypto_base = "/api/crypto/v1"
    private val settings_base = "/api/settings/v1"

    override suspend fun get_pgp_key_info(): PgpKeyInfo =
        decode_or_throw(client.http.get("${client.base_url}$crypto_base/encryption/pgp-key"))

    override suspend fun get_recovery_codes_status(): RecoveryCodesStatus =
        decode_or_throw(client.http.get("${client.base_url}$crypto_base/encryption/recovery-status"))

    override suspend fun get_encryption_settings(): EncryptionSettings =
        decode_or_throw(client.http.get("${client.base_url}$settings_base/encryption"))

    override suspend fun update_encryption_settings(request: UpdateEncryptionSettingsRequest): Boolean {
        val response = client.http.put("${client.base_url}$settings_base/encryption") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return response.status.value in 200..299
    }

    override suspend fun get_encryption_salt(): EncryptionSaltResponse =
        decode_or_throw(client.http.get("${client.base_url}$crypto_base/encryption/salt") {
            parameter("skip_cache", "true")
        })

    override suspend fun export_public_key(): ExportKeyResponse {
        val response = client.http.post("${client.base_url}$crypto_base/keys/pgp/export") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(ExportKeyRequest(include_private = false))
        }
        return decode_or_throw(response)
    }

    override suspend fun export_private_key(request: ExportKeyRequest): ExportKeyResponse {
        val response = client.http.post("${client.base_url}$crypto_base/keys/pgp/export") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun regenerate_recovery_codes(): RegenerateRecoveryCodesResponse {
        val response = client.http.post("${client.base_url}$crypto_base/encryption/regenerate-recovery-codes") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun get_wkd_status(): WkdStatusResponse =
        decode_or_throw(client.http.get("${client.base_url}$crypto_base/keys/publish/wkd/status"))

    override suspend fun get_keyserver_status(): KeyserverStatusResponse =
        decode_or_throw(client.http.get("${client.base_url}$crypto_base/keys/publish/keyserver/status"))

    override suspend fun publish_to_wkd(): PublishKeyResponse {
        val response = client.http.post("${client.base_url}$crypto_base/keys/publish/wkd") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun unpublish_from_wkd(): PublishKeyResponse {
        val response = client.http.delete("${client.base_url}$crypto_base/keys/publish/wkd") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun publish_to_keyserver(): PublishKeyResponse {
        val response = client.http.post("${client.base_url}$crypto_base/keys/publish/keyserver") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun republish_pgp_key(request: RepublishPgpKeyRequest): RepublishPgpKeyResponse {
        val response = client.http.post("${client.base_url}$crypto_base/keys/pgp/republish") {
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
        return try { response.body() } catch (t: kotlin.coroutines.cancellation.CancellationException) { throw t } catch (t: Throwable) { throw ApiError.UnknownError(t.message ?: "decode failed") }
    }
}
