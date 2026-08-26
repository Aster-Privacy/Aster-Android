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

package org.astermail.android.api.mail

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.net.URLEncoder
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError

private const val list_messages_timeout_millis = 45_000L

private fun url_encode_path(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

interface MailApi {
    suspend fun list_messages(
        limit: Int? = null,
        cursor: String? = null,
        offset: Int? = null,
        item_type: String? = null,
        is_starred: Boolean? = null,
        is_trashed: Boolean? = null,
        is_archived: Boolean? = null,
        is_spam: Boolean? = null,
        include_spam: Boolean? = null,
        include_trash: Boolean? = null,
        label_token: String? = null,
        tag_token: String? = null,
        group_by_thread: Boolean? = null,
        is_snoozed: Boolean? = null,
        routing_token: String? = null,
        order: String? = null,
        skip_total: Boolean? = null,
        include_envelope: Boolean? = null,
    ): MailItemsListResponse

    suspend fun get_message(item_id: String): MailItem

    suspend fun list_drafts(limit: Int? = null, cursor: String? = null): DraftsListResponse

    suspend fun create_draft(request: CreateDraftRequestBody): CreateDraftResponse

    suspend fun update_draft(draft_id: String, request: UpdateDraftRequestBody): UpdateDraftResponse

    suspend fun get_draft(draft_id: String): DraftItem

    suspend fun get_thread_draft(thread_token: String): DraftItem?

    suspend fun get_stats(): MailUserStatsResponse

    suspend fun get_thread_messages(thread_token: String): ThreadWithMessages

    suspend fun mark_thread_read(thread_token: String)

    suspend fun trash_thread(thread_token: String, is_trashed: Boolean)

    suspend fun patch_metadata(item_id: String, request: PatchMetadataRequest): PatchMetadataResponse

    suspend fun bulk_action(request: BulkScopeRequest): BulkScopeResponse

    suspend fun bulk_patch_metadata(request: BulkPatchMetadataRequest): BulkPatchMetadataResponse

    suspend fun bulk_add_label(request: BulkLabelRequest): BulkLabelResponse

    suspend fun bulk_remove_label(request: BulkLabelRequest): BulkLabelResponse

    suspend fun bulk_add_tag(request: BulkTagRequest): BulkLabelResponse

    suspend fun bulk_remove_tag(request: BulkTagRequest): BulkLabelResponse

    suspend fun report_spam_sender(request: SpamSenderRequest): SpamSenderResponse

    suspend fun remove_spam_sender(sender_hash: String, sender_domain_hash: String?): SpamSenderResponse

    suspend fun delete_message(item_id: String): DeleteResponse

    suspend fun delete_draft(draft_id: String): DeleteResponse

    suspend fun delete_permanent(item_id: String): DeleteResponse

    suspend fun empty_trash(): DeleteResponse

    suspend fun empty_spam(): DeleteResponse

    suspend fun bulk_delete_permanent(request: BulkPermanentDeleteRequest): DeleteResponse

    suspend fun create_message(request: CreateMailItemRequest): CreateMailItemResponse

    suspend fun sync_messages(
        since: String? = null,
        limit: Int? = null,
        cursor: String? = null,
    ): SyncMailItemsResponse

    suspend fun list_attachments(mail_item_id: String): AttachmentListResponse

    suspend fun create_attachment(
        mail_item_id: String,
        request: CreateAttachmentRequestBody,
    ): AttachmentResponse

    suspend fun get_attachment(attachment_id: String): AttachmentResponse

    suspend fun batch_attachment_meta(mail_item_ids: List<String>): BatchAttachmentMetaResponse

    suspend fun add_label_to_item(item_id: String, label_token: String)


    suspend fun remove_label_from_item(item_id: String, label_token: String)

    suspend fun add_tag_to_item(item_id: String, tag_token: String)

    suspend fun remove_tag_from_item(item_id: String, tag_token: String)

    suspend fun create_thread(thread_token: String, encrypted_meta: String, meta_nonce: String)

    suspend fun link_mail_to_thread(item_id: String, thread_token: String)
}

@kotlinx.serialization.Serializable
data class AddLabelRequestBody(val folder_token: String)

@kotlinx.serialization.Serializable
data class AddTagRequestBody(val tag_token: String)

@kotlinx.serialization.Serializable
data class CreateThreadRequestBody(
    val thread_token: String,
    val encrypted_meta: String,
    val meta_nonce: String,
)

@kotlinx.serialization.Serializable
data class LinkToThreadRequestBody(val thread_token: String)

@kotlinx.serialization.Serializable
data class CreateAttachmentRequestBody(
    val encrypted_data: String,
    val data_nonce: String,
    val encrypted_meta: String,
    val meta_nonce: String,
    val seq_num: Int? = null,
)

class MailApiImpl(private val client: ApiClient) : MailApi {
    private val base = "/api/mail/v1"
    private val large_payload_timeout_ms = 60_000L

    override suspend fun list_messages(
        limit: Int?,
        cursor: String?,
        offset: Int?,
        item_type: String?,
        is_starred: Boolean?,
        is_trashed: Boolean?,
        is_archived: Boolean?,
        is_spam: Boolean?,
        include_spam: Boolean?,
        include_trash: Boolean?,
        label_token: String?,
        tag_token: String?,
        group_by_thread: Boolean?,
        is_snoozed: Boolean?,
        routing_token: String?,
        order: String?,
        skip_total: Boolean?,
        include_envelope: Boolean?,
    ): MailItemsListResponse {
        val response = client.http.get("${client.base_url}$base/messages") {
            timeout {
                requestTimeoutMillis = list_messages_timeout_millis
                socketTimeoutMillis = list_messages_timeout_millis
            }
            limit?.let { parameter("limit", it) }
            cursor?.let { parameter("cursor", it) }
            offset?.let { parameter("offset", it) }
            item_type?.let { parameter("item_type", it) }
            is_starred?.let { parameter("is_starred", it) }
            is_trashed?.let { parameter("is_trashed", it) }
            is_archived?.let { parameter("is_archived", it) }
            is_spam?.let { parameter("is_spam", it) }
            include_spam?.let { parameter("include_spam", it) }
            include_trash?.let { parameter("include_trash", it) }
            label_token?.let { parameter("label_token", it) }
            tag_token?.let { parameter("tag_token", it) }
            group_by_thread?.let { parameter("group_by_thread", it) }
            is_snoozed?.let { parameter("is_snoozed", it) }
            routing_token?.let { parameter("routing_token", it) }
            order?.let { parameter("order", it) }
            skip_total?.let { parameter("skip_total", it) }
            include_envelope?.let { parameter("include_envelope", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun get_message(item_id: String): MailItem {
        val response = client.http.get("${client.base_url}$base/messages/$item_id")
        return decode_or_throw(response)
    }

    override suspend fun list_drafts(limit: Int?, cursor: String?): DraftsListResponse {
        val response = client.http.get("${client.base_url}$base/drafts") {
            limit?.let { parameter("limit", it) }
            cursor?.let { parameter("cursor", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun create_draft(request: CreateDraftRequestBody): CreateDraftResponse {
        val response = client.http.post("${client.base_url}$base/drafts") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun update_draft(
        draft_id: String,
        request: UpdateDraftRequestBody,
    ): UpdateDraftResponse {
        val response = client.http.put(
            "${client.base_url}$base/drafts/${url_encode_path(draft_id)}",
        ) {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        if (response.status.value == 409) {
            return try {
                response.body()
            } catch (t: kotlin.coroutines.cancellation.CancellationException) {
                throw t
            } catch (_: Throwable) {
                UpdateDraftResponse()
            }
        }
        return decode_or_throw(response)
    }

    override suspend fun get_draft(draft_id: String): DraftItem {
        val response = client.http.get(
            "${client.base_url}$base/drafts/${url_encode_path(draft_id)}",
        )
        return decode_or_throw(response)
    }

    override suspend fun get_thread_draft(thread_token: String): DraftItem? {
        val response = client.http.get(
            "${client.base_url}$base/drafts/thread/${url_encode_path(thread_token)}",
        )
        return decode_or_throw<DraftItem?>(response)
    }

    override suspend fun get_stats(): MailUserStatsResponse {
        val response = client.http.get("${client.base_url}$base/messages/stats")
        return decode_or_throw(response)
    }

    override suspend fun get_thread_messages(thread_token: String): ThreadWithMessages {
        val response = client.http.get(
            "${client.base_url}$base/messages/threads/${url_encode_path(thread_token)}/messages",
        ) {
            timeout { requestTimeoutMillis = large_payload_timeout_ms }
        }
        return decode_or_throw(response)
    }

    override suspend fun mark_thread_read(thread_token: String) {
        val response = client.http.put(
            "${client.base_url}$base/messages/threads/${url_encode_path(thread_token)}/read",
        ) {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody("{}")
        }
        throw_if_error(response)
    }

    override suspend fun trash_thread(thread_token: String, is_trashed: Boolean) {
        val response = client.http.put(
            "${client.base_url}$base/messages/threads/${url_encode_path(thread_token)}/trash",
        ) {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(mapOf("is_trashed" to is_trashed))
        }
        throw_if_error(response)
    }

    override suspend fun patch_metadata(
        item_id: String,
        request: PatchMetadataRequest,
    ): PatchMetadataResponse {
        val response = client.http.put("${client.base_url}$base/messages/$item_id/metadata") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun bulk_action(request: BulkScopeRequest): BulkScopeResponse {
        val response = client.http.post("${client.base_url}$base/messages/bulk/scope") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun bulk_patch_metadata(
        request: BulkPatchMetadataRequest,
    ): BulkPatchMetadataResponse {
        val response = client.http.put("${client.base_url}$base/messages/bulk/metadata") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun bulk_add_label(request: BulkLabelRequest): BulkLabelResponse =
        post_bulk_label("${client.base_url}$base/messages/bulk/labels", request)

    override suspend fun bulk_remove_label(request: BulkLabelRequest): BulkLabelResponse =
        post_bulk_label("${client.base_url}$base/messages/bulk/labels/remove", request)

    override suspend fun bulk_add_tag(request: BulkTagRequest): BulkLabelResponse =
        post_bulk_tag("${client.base_url}$base/messages/bulk/tags", request)

    override suspend fun bulk_remove_tag(request: BulkTagRequest): BulkLabelResponse =
        post_bulk_tag("${client.base_url}$base/messages/bulk/tags/remove", request)

    private suspend fun post_bulk_label(url: String, request: BulkLabelRequest): BulkLabelResponse {
        val response = client.http.post(url) {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    private suspend fun post_bulk_tag(url: String, request: BulkTagRequest): BulkLabelResponse {
        val response = client.http.post(url) {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun report_spam_sender(request: SpamSenderRequest): SpamSenderResponse {
        val response = client.http.post("${client.base_url}$base/spam_senders") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun remove_spam_sender(
        sender_hash: String,
        sender_domain_hash: String?,
    ): SpamSenderResponse {
        val response = client.http.delete("${client.base_url}$base/spam_senders") {
            parameter("sender_hash", sender_hash)
            sender_domain_hash?.let { parameter("sender_domain_hash", it) }
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_message(item_id: String): DeleteResponse {
        val response = client.http.delete("${client.base_url}$base/messages/$item_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_draft(draft_id: String): DeleteResponse {
        val response = client.http.delete("${client.base_url}$base/drafts/$draft_id") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun delete_permanent(item_id: String): DeleteResponse {
        val response = client.http.delete("${client.base_url}$base/messages/$item_id/permanent") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun empty_trash(): DeleteResponse {
        val response = client.http.delete("${client.base_url}$base/messages/trash") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun empty_spam(): DeleteResponse {
        val response = client.http.delete("${client.base_url}$base/messages/spam") {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun bulk_delete_permanent(request: BulkPermanentDeleteRequest): DeleteResponse {
        val response = client.http.delete("${client.base_url}$base/messages/trash/bulk") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun create_message(request: CreateMailItemRequest): CreateMailItemResponse {
        val response = client.http.post("${client.base_url}$base/messages") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun sync_messages(
        since: String?,
        limit: Int?,
        cursor: String?,
    ): SyncMailItemsResponse {
        val response = client.http.get("${client.base_url}$base/messages/sync") {
            since?.let { parameter("since", it) }
            limit?.let { parameter("limit", it) }
            cursor?.let { parameter("cursor", it) }
        }
        return decode_or_throw(response)
    }

    override suspend fun list_attachments(mail_item_id: String): AttachmentListResponse {
        val response = client.http.get("${client.base_url}$base/attachments/by-mail/$mail_item_id") {
            timeout { requestTimeoutMillis = large_payload_timeout_ms }
        }
        return decode_or_throw(response)
    }

    override suspend fun create_attachment(
        mail_item_id: String,
        request: CreateAttachmentRequestBody,
    ): AttachmentResponse {
        val response = client.http.post("${client.base_url}$base/attachments/by-mail/$mail_item_id") {
            timeout { requestTimeoutMillis = large_payload_timeout_ms }
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(request)
        }
        return decode_or_throw(response)
    }

    override suspend fun get_attachment(attachment_id: String): AttachmentResponse {
        val response = client.http.get("${client.base_url}$base/attachments/$attachment_id") {
            timeout { requestTimeoutMillis = large_payload_timeout_ms }
        }
        return decode_or_throw(response)
    }

    override suspend fun batch_attachment_meta(
        mail_item_ids: List<String>,
    ): BatchAttachmentMetaResponse {
        val response = client.http.post("${client.base_url}$base/attachments/meta/batch") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(BatchAttachmentMetaRequest(mail_item_ids))
        }
        return decode_or_throw(response)
    }

    override suspend fun add_label_to_item(item_id: String, label_token: String) {
        val response = client.http.post("${client.base_url}$base/messages/$item_id/labels") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(AddLabelRequestBody(folder_token = label_token))
        }
        throw_if_error(response)
    }

    override suspend fun remove_label_from_item(item_id: String, label_token: String) {
        val response = client.http.delete(
            "${client.base_url}$base/messages/$item_id/labels/${url_encode_path(label_token)}",
        ) {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        throw_if_error(response)
    }

    override suspend fun add_tag_to_item(item_id: String, tag_token: String) {
        val response = client.http.post("${client.base_url}$base/messages/$item_id/tags") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(AddTagRequestBody(tag_token = tag_token))
        }
        throw_if_error(response)
    }

    override suspend fun remove_tag_from_item(item_id: String, tag_token: String) {
        val response = client.http.delete(
            "${client.base_url}$base/messages/$item_id/tags/${url_encode_path(tag_token)}",
        ) {
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
        }
        throw_if_error(response)
    }

    override suspend fun create_thread(thread_token: String, encrypted_meta: String, meta_nonce: String) {
        val response = client.http.post("${client.base_url}$base/messages/threads") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(CreateThreadRequestBody(thread_token, encrypted_meta, meta_nonce))
        }
        throw_if_error(response)
    }

    override suspend fun link_mail_to_thread(item_id: String, thread_token: String) {
        val response = client.http.put("${client.base_url}$base/messages/$item_id/thread") {
            contentType(ContentType.Application.Json)
            client.get_csrf()?.let { header("X-CSRF-Token", it) }
            setBody(LinkToThreadRequestBody(thread_token))
        }
        throw_if_error(response)
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

    private suspend fun throw_if_error(response: HttpResponse) {
        if (response.status.value !in 200..299) {
            val body = try { response.body<String>() } catch (_: Throwable) { "" }
            throw client.map_http_status(response.status.value, body)
        }
    }
}
