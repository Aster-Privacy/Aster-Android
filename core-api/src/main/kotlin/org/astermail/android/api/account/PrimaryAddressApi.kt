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

package org.astermail.android.api.account

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
data class PrimaryAddressEligibilityResponse(
    val eligible: Boolean = false,
    val reason: String? = null,
    val current_address: String = "",
    val next_change_available_at: String? = null,
    val renames_allowed_per_year: Int = 0,
)

@Serializable
data class PrimaryAddressAvailabilityRequest(
    val local_part: String,
    val domain: String,
)

@Serializable
data class PrimaryAddressAvailabilityResponse(
    val available: Boolean = false,
)

@Serializable
data class PrimaryAddressStartRequest(
    val new_local_part: String,
    val new_domain: String,
    val password_hash: String,
)

@Serializable
data class PrimaryAddressStartResponse(
    val expires_at: String = "",
)

@Serializable
data class PrimaryAddressConfirmRequest(
    val code: String,
    val new_user_hash: String,
    val retained_encrypted_local_part: String,
    val retained_local_part_nonce: String,
    val retained_alias_address_hash: String,
    val retained_routing_address_hash: String,
)

@Serializable
data class PrimaryAddressConfirmResponse(
    val new_address: String = "",
    val retained_address: String = "",
    val next_change_available_at: String? = null,
)

interface PrimaryAddressApi {
    suspend fun get_eligibility(): PrimaryAddressEligibilityResponse
    suspend fun check_availability(
        request: PrimaryAddressAvailabilityRequest,
    ): PrimaryAddressAvailabilityResponse
    suspend fun start(request: PrimaryAddressStartRequest): PrimaryAddressStartResponse
    suspend fun resend(): PrimaryAddressStartResponse
    suspend fun confirm(request: PrimaryAddressConfirmRequest): PrimaryAddressConfirmResponse
}

class PrimaryAddressApiImpl(private val client: ApiClient) : PrimaryAddressApi {
    private val base = "/api/core/v1/account/primary-address"

    override suspend fun get_eligibility(): PrimaryAddressEligibilityResponse =
        decode_or_throw(client.http.get("${client.base_url}$base"))

    override suspend fun check_availability(
        request: PrimaryAddressAvailabilityRequest,
    ): PrimaryAddressAvailabilityResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/availability") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun start(request: PrimaryAddressStartRequest): PrimaryAddressStartResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/start") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun resend(): PrimaryAddressStartResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/resend") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(emptyMap<String, String>())
        }
        return decode_or_throw(response)
    }

    override suspend fun confirm(request: PrimaryAddressConfirmRequest): PrimaryAddressConfirmResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/confirm") {
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
        } catch (t: kotlin.coroutines.cancellation.CancellationException) {
            throw t
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
