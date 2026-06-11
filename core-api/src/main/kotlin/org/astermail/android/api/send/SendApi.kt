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

package org.astermail.android.api.send

import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

@Serializable
data class SimpleSendRequest(
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val is_e2e_encrypted: Boolean = false,
    val encrypted_envelope: String,
    val envelope_nonce: String,
    val folder_token: String? = null,
    val thread_token: String? = null,
    val encrypted_metadata: String? = null,
    val metadata_nonce: String? = null,
    val sender_email: String? = null,
    val sender_alias_hash: String? = null,
    val sender_display_name: String? = null,
    val expires_at: String? = null,
    val attachments: List<SendAttachmentPayload> = emptyList(),
    val forward_original_mail_id: String? = null,
)

@Serializable
data class SendAttachmentPayload(
    val encrypted_data: String,
    val data_nonce: String,
    val sender_encrypted_meta: String,
    val sender_meta_nonce: String,
    val recipient_encrypted_meta: String? = null,
    val size_bytes: Long,
)

@Serializable
data class SimpleSendResponse(
    val success: Boolean = false,
    val message: String = "",
    val mail_item_id: String? = null,
)

@Serializable
data class ExternalSendRequest(
    val encrypted_recipients: String,
    val encrypted_subject: String,
    val encrypted_body: String,
    val ephemeral_key: String,
    val nonce: String,
    val encrypted_envelope: String? = null,
    val envelope_nonce: String? = null,
    val folder_token: String? = null,
    val thread_token: String? = null,
    val encrypted_metadata: String? = null,
    val metadata_nonce: String? = null,
    val sender_email: String? = null,
    val sender_alias_hash: String? = null,
    val sender_display_name: String? = null,
    val expires_at: String? = null,
    val expiry_password: String? = null,
    val acknowledge_server_readable: Boolean = true,
    val attachments: List<ExternalAttachmentPayload> = emptyList(),
)

@Serializable
data class ExternalAttachmentPayload(
    val data: String,
    val filename: String,
    val content_type: String,
    val size_bytes: Long,
    val content_id: String? = null,
)

@Serializable
data class ExternalSendResponse(
    val success: Boolean = false,
    val message: String = "",
    val mail_item_id: String? = null,
)

interface SendApi {
    suspend fun send_simple(request: SimpleSendRequest): SimpleSendResponse
    suspend fun send_external(request: ExternalSendRequest): ExternalSendResponse
}

class SendApiImpl(private val client: ApiClient) : SendApi {
    private val base = "/api/mail/v1/send"

    override suspend fun send_simple(request: SimpleSendRequest): SimpleSendResponse {
        val response = client.http.post("${client.base_url}$base") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun send_external(request: ExternalSendRequest): ExternalSendResponse {
        val response = client.http.post("${client.base_url}$base/external") {
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
        } catch (t: Throwable) {
            throw ApiError.UnknownError(t.message ?: "decode failed")
        }
    }
}
