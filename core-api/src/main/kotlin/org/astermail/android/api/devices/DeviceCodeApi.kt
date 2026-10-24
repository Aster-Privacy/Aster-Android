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

package org.astermail.android.api.devices

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

const val DEVICE_CODE_LENGTH = 8

@Serializable
data class DeviceCodeVerifyRequest(
    val code: String,
)

@Serializable
data class PendingDevice(
    val ed25519_pk: String = "",
    val mlkem_pk: String = "",
    val x25519_pk: String = "",
    val machine_name: String = "",
    val device_type: String = "",
)

@Serializable
data class DeviceCodeConfirmRequest(
    val code: String,
    val sealed_envelope: String,
)

@Serializable
data class DeviceCodeConfirmResponse(
    val device_id: String = "",
    val machine_name: String = "",
)

sealed class DeviceLinkError(message: String) : Exception(message) {
    object CodeNotFound : DeviceLinkError("invalid or expired code")
    object AlreadyLinked : DeviceLinkError("device already linked to another account")
    object PlanUpgradeRequired : DeviceLinkError("plan upgrade required")
    object ServiceUnavailable : DeviceLinkError("device linking is unavailable")
}

interface DeviceCodeApi {
    suspend fun verify_code(code: String): PendingDevice
    suspend fun confirm_code(code: String, sealed_envelope: String): DeviceCodeConfirmResponse
}

fun normalize_device_code(raw: String): String =
    raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }
        .uppercase()
        .take(DEVICE_CODE_LENGTH)

fun format_device_code(raw: String): String {
    val normalized = normalize_device_code(raw)
    return if (normalized.length > 4) {
        "${normalized.substring(0, 4)}-${normalized.substring(4)}"
    } else {
        normalized
    }
}

fun map_device_link_error(status: Int, body: String): DeviceLinkError? {
    val fields = parse_device_error_fields(body)
    val error = fields["error"].orEmpty()
    val code = fields["code"].orEmpty()

    if (error == "plan_upgrade_required") return DeviceLinkError.PlanUpgradeRequired
    if (status == 409 || code == "CONFLICT") return DeviceLinkError.AlreadyLinked
    if (status == 404 || code == "NOT_FOUND") return DeviceLinkError.CodeNotFound
    if (status == 503) return DeviceLinkError.ServiceUnavailable
    return null
}

private val device_error_json = Json { ignoreUnknownKeys = true }

private fun parse_device_error_fields(body: String): Map<String, String> {
    if (body.isBlank()) return emptyMap()
    return try {
        val obj = device_error_json.parseToJsonElement(body) as? JsonObject ?: return emptyMap()
        obj.entries.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.content?.let { key to it }
        }.toMap()
    } catch (_: Throwable) {
        emptyMap()
    }
}

class DeviceCodeApiImpl(private val client: ApiClient) : DeviceCodeApi {

    private val base = "/api/core/v1/auth/device/code"

    override suspend fun verify_code(code: String): PendingDevice {
        val normalized = require_normalized(code)
        val response = client.http.post("${client.base_url}$base/verify") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(DeviceCodeVerifyRequest(code = normalized))
        }
        return decode_or_throw(response)
    }

    override suspend fun confirm_code(
        code: String,
        sealed_envelope: String,
    ): DeviceCodeConfirmResponse {
        val normalized = require_normalized(code)
        val response = client.http.post("${client.base_url}$base/confirm") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(
                DeviceCodeConfirmRequest(
                    code = normalized,
                    sealed_envelope = sealed_envelope,
                ),
            )
        }
        return decode_or_throw(response)
    }

    private fun require_normalized(code: String): String {
        val normalized = normalize_device_code(code)
        if (normalized.length != DEVICE_CODE_LENGTH) throw DeviceLinkError.CodeNotFound
        return normalized
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw map_error(response.status.value, body)
        }
        return try {
            response.body()
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }

    fun map_error(status: Int, body: String): Throwable =
        map_device_link_error(status, body) ?: client.map_http_status(status, body)
}
