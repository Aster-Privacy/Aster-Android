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
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
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

@Serializable
data class ScheduledSummary(
    val id: String,
    val recipient_count: Int = 0,
    val has_attachments: Boolean = false,
    val scheduled_at: String,
    val status: String = "pending",
    val created_at: String? = null,
    val is_external: Boolean = false,
)

@Serializable
data class ListScheduledResponse(
    val items: List<ScheduledSummary> = emptyList(),
    val total: Long = 0,
    val limit: Long = 0,
    val offset: Long = 0,
)

@Serializable
data class ScheduledDetailResponse(
    val id: String,
    val encrypted_envelope: String,
    val envelope_nonce: String,
    val encrypted_recipients: String? = null,
    val recipients_nonce: String? = null,
    val recipient_count: Int = 0,
    val thread_token: String? = null,
    val reply_to_id: String? = null,
    val has_attachments: Boolean = false,
    val attachment_count: Int = 0,
    val size_bytes: Long = 0,
    val scheduled_at: String,
    val status: String = "pending",
    val failure_reason: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val is_external: Boolean = false,
    val ephemeral_key: String? = null,
    val base_nonce: String? = null,
)

@Serializable
data class UpdateScheduledRequest(
    val scheduled_at: String? = null,
    val cancel: Boolean? = null,
    val send_now: Boolean? = null,
)

@Serializable
data class UpdateScheduledResponse(
    val id: String? = null,
    val status: String? = null,
    val scheduled_at: String? = null,
    val cancelled_at: String? = null,
)

interface ScheduledApi {
    suspend fun create_scheduled(request: CreateScheduledRequest): CreateScheduledResponse

    suspend fun list_scheduled(limit: Int = 50, offset: Int = 0): ListScheduledResponse

    suspend fun get_scheduled(id: String): ScheduledDetailResponse

    suspend fun cancel_scheduled(id: String): UpdateScheduledResponse

    suspend fun reschedule(id: String, scheduled_at: String): UpdateScheduledResponse

    suspend fun send_now(id: String): UpdateScheduledResponse

    suspend fun delete_scheduled(id: String)
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

    override suspend fun list_scheduled(limit: Int, offset: Int): ListScheduledResponse {
        val response = client.http.get("${client.base_url}$base") {
            parameter("limit", limit)
            parameter("offset", offset)
        }
        return decode_or_throw(response)
    }

    override suspend fun get_scheduled(id: String): ScheduledDetailResponse {
        val response = client.http.get("${client.base_url}$base/$id")
        return decode_or_throw(response)
    }

    override suspend fun cancel_scheduled(id: String): UpdateScheduledResponse =
        patch_scheduled(id, UpdateScheduledRequest(cancel = true))

    override suspend fun reschedule(id: String, scheduled_at: String): UpdateScheduledResponse =
        patch_scheduled(id, UpdateScheduledRequest(scheduled_at = scheduled_at))

    override suspend fun send_now(id: String): UpdateScheduledResponse =
        patch_scheduled(id, UpdateScheduledRequest(send_now = true))

    override suspend fun delete_scheduled(id: String) {
        val response = client.http.delete("${client.base_url}$base/$id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    private suspend fun patch_scheduled(
        id: String,
        request: UpdateScheduledRequest,
    ): UpdateScheduledResponse {
        val response = client.http.patch("${client.base_url}$base/$id") {
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
