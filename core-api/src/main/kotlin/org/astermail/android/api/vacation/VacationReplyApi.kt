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

package org.astermail.android.api.vacation

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
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
data class VacationReplyResponse(
    val id: String,
    val subject: String,
    val body: String,
    val is_enabled: Boolean,
    val start_date: String? = null,
    val end_date: String? = null,
    val external_only: Boolean = false,
    val reply_count: Int = 0,
    val last_replied_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class UpsertVacationReplyRequest(
    val subject: String,
    val body: String,
    val is_enabled: Boolean,
    val start_date: String? = null,
    val end_date: String? = null,
    val external_only: Boolean = false,
)

@Serializable
data class ToggleVacationReplyRequest(val is_enabled: Boolean)

@Serializable
data class DeleteVacationReplyResponse(val success: Boolean = false)

interface VacationReplyApi {
    suspend fun get(): VacationReplyResponse?
    suspend fun upsert(request: UpsertVacationReplyRequest): VacationReplyResponse
    suspend fun delete(): DeleteVacationReplyResponse
    suspend fun toggle(is_enabled: Boolean): VacationReplyResponse
}

class VacationReplyApiImpl(private val client: ApiClient) : VacationReplyApi {
    private val base = "/api/mail/v1/vacation_reply"

    override suspend fun get(): VacationReplyResponse? {
        val response = client.http.get("${client.base_url}$base")
        if (response.status.value == 204 || response.status.value == 404) return null
        return decode_or_throw_nullable(response)
    }

    override suspend fun upsert(request: UpsertVacationReplyRequest): VacationReplyResponse {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun delete(): DeleteVacationReplyResponse {
        val response = client.http.delete("${client.base_url}$base") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun toggle(is_enabled: Boolean): VacationReplyResponse {
        val response = client.http.patch("${client.base_url}$base/toggle") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(ToggleVacationReplyRequest(is_enabled = is_enabled))
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

    private suspend inline fun <reified T> decode_or_throw_nullable(response: HttpResponse): T? {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        val text = try { response.body<String>() } catch (_: Throwable) { return null }
        if (text.isBlank() || text == "null") return null
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<T>(
                kotlinx.serialization.serializer(),
                text,
            )
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
