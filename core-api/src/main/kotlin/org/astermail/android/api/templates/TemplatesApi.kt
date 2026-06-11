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

package org.astermail.android.api.templates

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
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class TemplateItem(
    val id: String,
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_category: String,
    val category_nonce: String,
    val encrypted_content: String,
    val content_nonce: String,
    val sort_order: Int = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class TemplatesListResponse(
    val templates: List<TemplateItem> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class CreateTemplateRequest(
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_category: String,
    val category_nonce: String,
    val encrypted_content: String,
    val content_nonce: String,
    val sort_order: Int = 0,
)

@Serializable
data class CreateTemplateResponse(
    val id: String,
    val success: Boolean = false,
    val created_at: String? = null,
)

@Serializable
data class UpdateTemplateRequest(
    val encrypted_name: String? = null,
    val name_nonce: String? = null,
    val encrypted_category: String? = null,
    val category_nonce: String? = null,
    val encrypted_content: String? = null,
    val content_nonce: String? = null,
    val sort_order: Int? = null,
)

interface TemplatesApi {
    suspend fun list(): TemplatesListResponse
    suspend fun create(request: CreateTemplateRequest): CreateTemplateResponse
    suspend fun update(template_id: String, request: UpdateTemplateRequest)
    suspend fun delete(template_id: String)
}

class TemplatesApiImpl(private val client: ApiClient) : TemplatesApi {
    private val base = "/api/mail/v1/templates"

    override suspend fun list(): TemplatesListResponse {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun create(request: CreateTemplateRequest): CreateTemplateResponse {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update(template_id: String, request: UpdateTemplateRequest) {
        val response = client.http.put("${client.base_url}$base/$template_id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        ensure_success(response)
    }

    override suspend fun delete(template_id: String) {
        val response = client.http.delete("${client.base_url}$base/$template_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        ensure_success(response)
    }

    private suspend fun ensure_success(response: HttpResponse) {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }

    private suspend inline fun <reified T> decode_or_throw(response: HttpResponse): T {
        ensure_success(response)
        return try {
            response.body()
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
