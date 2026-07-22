// SPDX-License-Identifier: AGPL-3.0-only
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

package org.astermail.android.api.scheduled

import io.ktor.client.call.body
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
data class CreateScheduledRequest(
    val encrypted_envelope: String,
    val envelope_nonce: String,
    val encrypted_recipients: String,
    val recipients_nonce: String,
    val recipient_count: Int,
    val scheduled_at: String,
    val folder_token: String? = null,
    val thread_token: String? = null,
    val reply_to_id: String? = null,
    val is_external: Boolean? = null,
    val ephemeral_key: String? = null,
    val base_nonce: String? = null,
    val has_attachments: Boolean? = null,
    val attachment_count: Int? = null,
    val size_bytes: Long? = null,
    val sender_alias_hash: String? = null,
)

@Serializable
data class CreateScheduledResponse(
    val id: String? = null,
    val success: Boolean = false,
)

interface ScheduledApi {
    suspend fun create_scheduled(request: CreateScheduledRequest): CreateScheduledResponse
}

class ScheduledApiImpl(private val client: ApiClient) : ScheduledApi {
    private val base = "/api/mail/v1/scheduled"

    override suspend fun create_scheduled(request: CreateScheduledRequest): CreateScheduledResponse {
        val response = client.http.post("${client.base_url}$base") {
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
