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
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class AccountStatusResponse(
    val status: String = "active",
    val deletion_scheduled_at: String? = null,
    val days_until_deletion: Long? = null,
)

@Serializable
data class CancelDeletionResponse(
    val success: Boolean = false,
)

interface AccountApi {
    suspend fun get_status(): AccountStatusResponse
    suspend fun cancel_deletion(): CancelDeletionResponse
}

class AccountApiImpl(private val client: ApiClient) : AccountApi {
    private val base = "/api/core/v1/account"

    override suspend fun get_status(): AccountStatusResponse =
        decode_or_throw(client.http.get("${client.base_url}$base/status"))

    override suspend fun cancel_deletion(): CancelDeletionResponse {
        client.fetch_csrf_if_needed()
        val response = client.http.post("${client.base_url}$base/cancel-deletion") {
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
