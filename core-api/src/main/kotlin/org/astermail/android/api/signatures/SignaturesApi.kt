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

package org.astermail.android.api.signatures

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
data class Signature(
    val id: String,
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_content: String,
    val content_nonce: String,
    val is_default: Boolean = false,
    val is_html: Boolean = false,
    val alias_id: String? = null,
    val placement: Int? = null,
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class ListSignaturesResponse(
    val signatures: List<Signature> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class CreateSignatureRequest(
    val encrypted_name: String,
    val name_nonce: String,
    val encrypted_content: String,
    val content_nonce: String,
    val is_default: Boolean = false,
    val is_html: Boolean = false,
    val alias_id: String? = null,
    val placement: Int? = null,
)

@Serializable
data class CreateSignatureResponse(
    val id: String,
    val success: Boolean = true,
    val created_at: String = "",
)

@Serializable
data class UpdateSignatureRequest(
    val encrypted_name: String? = null,
    val name_nonce: String? = null,
    val encrypted_content: String? = null,
    val content_nonce: String? = null,
    val is_html: Boolean? = null,
    val alias_id: String? = null,
    val placement: Int? = null,
)

@Serializable
data class UpdateSignatureResponse(
    val success: Boolean = true,
    val updated_at: String = "",
)

@Serializable
data class DeleteSignatureResponse(
    val success: Boolean = true,
)

@Serializable
data class SetDefaultSignatureResponse(
    val success: Boolean = true,
)

interface SignaturesApi {
    suspend fun list_signatures(): ListSignaturesResponse
    suspend fun get_default_signature(): Signature?
    suspend fun get_signature_for_alias(alias_id: String): Signature?
    suspend fun create_signature(request: CreateSignatureRequest): CreateSignatureResponse
    suspend fun update_signature(id: String, request: UpdateSignatureRequest): UpdateSignatureResponse
    suspend fun delete_signature(id: String): DeleteSignatureResponse
    suspend fun set_default_signature(id: String): SetDefaultSignatureResponse
}

class SignaturesApiImpl(private val client: ApiClient) : SignaturesApi {
    private val base = "/api/mail/v1/signatures"

    override suspend fun list_signatures(): ListSignaturesResponse {
        val response = client.http.get("${client.base_url}$base")
        return decode_or_throw(response)
    }

    override suspend fun get_default_signature(): Signature? {
        val response = client.http.get("${client.base_url}$base/default")
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body<Signature>()
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun get_signature_for_alias(alias_id: String): Signature? {
        val response = client.http.get("${client.base_url}$base/for-alias/$alias_id")
        if (response.status.value == 404) return null
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
        return try {
            response.body<Signature>()
        } catch (_: Throwable) {
            null
        }
    }

    override suspend fun create_signature(request: CreateSignatureRequest): CreateSignatureResponse {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_signature(id: String, request: UpdateSignatureRequest): UpdateSignatureResponse {
        val response = client.http.put("${client.base_url}$base/$id") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_signature(id: String): DeleteSignatureResponse {
        val response = client.http.delete("${client.base_url}$base/$id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun set_default_signature(id: String): SetDefaultSignatureResponse {
        val response = client.http.patch("${client.base_url}$base/$id/default") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
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
}
