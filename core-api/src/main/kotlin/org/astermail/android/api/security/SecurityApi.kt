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

package org.astermail.android.api.security

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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class LoginAlertStatus(
    val enabled: Boolean = false,
)

@Serializable
data class SetLoginAlertRequest(
    val enabled: Boolean,
)

@Serializable
data class HardwareKey(
    val id: String = "",
    @SerialName("name_encrypted") val name: String? = null,
    @SerialName("type") val key_type: String = "",
    @SerialName("registered_at") val created_at: String = "",
    @SerialName("last_used") val last_used_at: String? = null,
) {
    val display_name: String get() = name?.takeIf { it.isNotBlank() } ?: ""
}

@Serializable
data class HardwareKeysResponse(
    val keys: List<HardwareKey> = emptyList(),
)

@Serializable
data class RenameHardwareKeyRequest(
    val friendly_name: String,
)

@Serializable
data class RenameHardwareKeyResponse(
    val success: Boolean = false,
    val name_encrypted: String? = null,
)

@Serializable
data class TrustedDevice(
    val id: String = "",
    val label: String = "",
    val last_used_at: String? = null,
    val expires_at: String? = null,
    val ip_snippet: String? = null,
)

@Serializable
data class TrustedDevicesResponse(
    val devices: List<TrustedDevice> = emptyList(),
)

@Serializable
data class AuditEvent(
    val id: String = "",
    val event_type: String = "",
    val severity: String = "",
    val ip_address: String? = null,
    val user_agent: String? = null,
    @SerialName("timestamp") val created_at: String = "",
    @SerialName("description_encrypted") val details: String? = null,
)

@Serializable
data class AuditLogResponse(
    @SerialName("entries") val events: List<AuditEvent> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class VanguardStatus(
    val enabled: Boolean = false,
)

interface SecurityApi {
    suspend fun get_login_alerts(): LoginAlertStatus
    suspend fun set_login_alerts(request: SetLoginAlertRequest): LoginAlertStatus
    suspend fun list_hardware_keys(): HardwareKeysResponse
    suspend fun delete_hardware_key(key_id: String)
    suspend fun rename_hardware_key(key_id: String, name: String): RenameHardwareKeyResponse
    suspend fun list_trusted_devices(): TrustedDevicesResponse
    suspend fun revoke_trusted_device(device_id: String)
    suspend fun revoke_all_trusted_devices()
    suspend fun get_audit_log(page: Int = 1, per_page: Int = 20): AuditLogResponse
    suspend fun get_vanguard_status(): VanguardStatus
    suspend fun enable_vanguard(): VanguardStatus
    suspend fun disable_vanguard(): VanguardStatus
}

class SecurityApiImpl(private val client: ApiClient) : SecurityApi {
    private val base = "/api/core/v1"

    override suspend fun get_login_alerts(): LoginAlertStatus =
        decode_or_throw(client.http.get("${client.base_url}$base/auth/login-alerts"))

    override suspend fun set_login_alerts(request: SetLoginAlertRequest): LoginAlertStatus {
        val response = client.http.put("${client.base_url}$base/auth/login-alerts") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun list_hardware_keys(): HardwareKeysResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/auth/hardware-keys"))

    override suspend fun delete_hardware_key(key_id: String) {
        val response = client.http.delete("${client.base_url}$base/auth/hardware-keys/$key_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) throw client.map_http_status(response.status.value, "")
    }

    override suspend fun rename_hardware_key(key_id: String, name: String): RenameHardwareKeyResponse {
        val response = client.http.put("${client.base_url}$base/auth/hardware-keys/$key_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(RenameHardwareKeyRequest(friendly_name = name))
        }
        return decode_or_throw(response)
    }

    override suspend fun list_trusted_devices(): TrustedDevicesResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/auth/trusted-devices"))

    override suspend fun revoke_trusted_device(device_id: String) {
        val response = client.http.delete("${client.base_url}$base/auth/trusted-devices/$device_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) throw client.map_http_status(response.status.value, "")
    }

    override suspend fun revoke_all_trusted_devices() {
        val response = client.http.delete("${client.base_url}$base/auth/trusted-devices") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) throw client.map_http_status(response.status.value, "")
    }

    override suspend fun get_audit_log(page: Int, per_page: Int): AuditLogResponse {
        val response = client.http.get("${client.base_url}$base/security/audit") {
            url { parameters.append("page", page.toString()); parameters.append("per_page", per_page.toString()) }
        }
        return decode_or_throw(response)
    }

    override suspend fun get_vanguard_status(): VanguardStatus =
        decode_or_throw(client.http.get("${client.base_url}$base/security/vanguard"))

    override suspend fun enable_vanguard(): VanguardStatus {
        val response = client.http.post("${client.base_url}$base/security/vanguard/enable") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun disable_vanguard(): VanguardStatus {
        val response = client.http.delete("${client.base_url}$base/security/vanguard/disable") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
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
