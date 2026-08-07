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

package org.astermail.android.api.family

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class ReservedAddress(
    val id: String,
    val username: String,
    val email_domain: String,
    val label: String? = null,
    val allocated_storage_bytes: Long,
    val status: String,
    val is_minor: Boolean,
    val claim_url: String? = null,
    val claimed_user_id: String? = null,
    val claimed_at: String? = null,
    val created_at: String,
)

@Serializable
data class FamilySeatBreakdown(
    val active_members: Int = 0,
    val pending_invites: Int = 0,
    val reserved_addresses: Int = 0,
    val grace_members: Int = 0,
)

@Serializable
data class ListReservationsResponse(
    val reservations: List<ReservedAddress>,
    val max_members: Int,
    val seats_used: Int,
    val seats: FamilySeatBreakdown? = null,
)

@Serializable
data class RegenerateClaimLinkResponse(
    val claim_url: String,
)

interface FamilyApi {
    suspend fun list_reservations(): ListReservationsResponse
    suspend fun release_reservation(id: String)
    suspend fun regenerate_claim_link(id: String): RegenerateClaimLinkResponse
}

class FamilyApiImpl(private val client: ApiClient) : FamilyApi {
    private val base = "/api/payments/v1/family"

    override suspend fun list_reservations(): ListReservationsResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/reservations"))

    override suspend fun release_reservation(id: String) {
        client.fetch_csrf_if_needed()
        val response = client.http.delete("${client.base_url}$base/reservations/$id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun regenerate_claim_link(id: String): RegenerateClaimLinkResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/reservations/$id/claim-link") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
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
