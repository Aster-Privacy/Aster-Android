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

package org.astermail.android.api.tags

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
data class TagItem(
    val id: String,
    val tag_token: String,
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_color: String? = null,
    val color_nonce: String? = null,
    val encrypted_icon: String? = null,
    val icon_nonce: String? = null,
    val sort_order: Int = 0,
    val item_count: Long? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class TagsListResponse(
    val tags: List<TagItem> = emptyList(),
    val total: Long = 0,
    val has_more: Boolean = false,
)

@Serializable
data class CreateTagRequest(
    val tag_token: String,
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_color: String? = null,
    val color_nonce: String? = null,
    val encrypted_icon: String? = null,
    val icon_nonce: String? = null,
    val sort_order: Int? = null,
)

@Serializable
data class CreateTagResponse(
    val id: String? = null,
    val tag_token: String? = null,
    val success: Boolean = false,
)

@Serializable
data class UpdateTagRequest(
    val encrypted_name: String? = null,
    val name_nonce: String? = null,
    val encrypted_color: String? = null,
    val color_nonce: String? = null,
    val encrypted_icon: String? = null,
    val icon_nonce: String? = null,
    val sort_order: Int? = null,
)

interface TagsApi {
    suspend fun list_tags(include_counts: Boolean = true): TagsListResponse
    suspend fun create_tag(request: CreateTagRequest): CreateTagResponse
    suspend fun update_tag(tag_id: String, request: UpdateTagRequest)
    suspend fun delete_tag(tag_id: String)
}

class TagsApiImpl(private val client: ApiClient) : TagsApi {
    private val tags_base = "/api/mail/v1/tags"

    override suspend fun list_tags(include_counts: Boolean): TagsListResponse {
        val response = client.http.get("${client.base_url}$tags_base") {
            parameter("include_counts", include_counts)
        }
        return decode_or_throw(response)
    }

    override suspend fun create_tag(request: CreateTagRequest): CreateTagResponse {
        val response = client.http.post("${client.base_url}$tags_base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_tag(tag_id: String, request: UpdateTagRequest) {
        val response = client.http.put("${client.base_url}$tags_base/$tag_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    override suspend fun delete_tag(tag_id: String) {
        val response = client.http.delete("${client.base_url}$tags_base/$tag_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
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
}
