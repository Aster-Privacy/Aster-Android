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

import kotlinx.serialization.Serializable

@Serializable
data class MailItemMetadata(
    val is_read: Boolean = false,
    val is_starred: Boolean = false,
    val is_pinned: Boolean = false,
    val is_trashed: Boolean = false,
    val is_archived: Boolean = false,
    val is_spam: Boolean = false,
    val size_bytes: Long = 0,
    val has_attachments: Boolean = false,
    val attachment_count: Int = 0,
    val scheduled_at: String? = null,
    val send_status: String? = null,
    val snoozed_until: String? = null,
    val trashed_at: String? = null,
    val message_ts: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val item_type: String? = null,
)

@Serializable
data class MailItemLabel(
    val folder_token: String? = null,
    val name: String? = null,
    val color: String? = null,
)

@Serializable
data class MailItemFolder(
    val folder_token: String? = null,
    val name: String? = null,
)

@Serializable
data class MailItem(
    val id: String,
    val item_type: String? = null,
    val encrypted_envelope: String? = null,
    val envelope_nonce: String? = null,
    val ephemeral_key: String? = null,
    val ephemeral_pq_key: String? = null,
    val sender_sealed: String? = null,
    val folder_token: String? = null,
    val is_external: Boolean = false,
    val has_recipient_key: Boolean? = null,
    val thread_token: String? = null,
    val thread_message_count: Int? = null,
    val created_at: String? = null,
    val labels: List<MailItemLabel>? = null,
    val encrypted_metadata: String? = null,
    val metadata_nonce: String? = null,
    val metadata_version: Int? = null,
    val scheduled_at: String? = null,
    val send_status: String? = null,
    val message_ts: String? = null,
    val snoozed_until: String? = null,
    val is_trashed: Boolean? = null,
    val is_spam: Boolean? = null,
    val is_read: Boolean? = null,
    val folders: List<MailItemFolder>? = null,
    val tag_tokens: List<String>? = null,
    val metadata: MailItemMetadata? = null,
    val expires_at: String? = null,
    val expiry_type: String? = null,
    val phishing_level: String? = null,
    val spf_result: String? = null,
    val dkim_result: String? = null,
    val dmarc_result: String? = null,
)

@Serializable
data class MailItemsListResponse(
    val items: List<MailItem> = emptyList(),
    val total: Int = 0,
    val next_cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class DraftItem(
    val id: String,
    val draft_type: String = "compose",
    val encrypted_content: String = "",
    val content_nonce: String = "",
    val reply_to_id: String? = null,
    val forward_from_id: String? = null,
    val thread_token: String? = null,
    val version: Int = 1,
    val content_hash: String = "",
    val size_bytes: Long = 0,
    val created_at: String? = null,
    val updated_at: String? = null,
    val expires_at: String? = null,
)

@Serializable
data class DraftsListResponse(
    val items: List<DraftItem> = emptyList(),
    val next_cursor: String? = null,
    val has_more: Boolean = false,
)

@Serializable
data class MailThread(
    val user_id: String? = null,
    val thread_token: String,
    val encrypted_meta: String? = null,
    val meta_nonce: String? = null,
    val message_count: Int = 0,
    val unread_count: Int = 0,
    val latest_ts: String? = null,
    val created_at: String? = null,
)

@Serializable
data class ThreadMessageItem(
    val id: String,
    val item_type: String? = null,
    val encrypted_envelope: String? = null,
    val envelope_nonce: String? = null,
    val encrypted_metadata: String? = null,
    val metadata_nonce: String? = null,
    val metadata_version: Int? = null,
    val is_external: Boolean? = null,
    val has_recipient_key: Boolean? = null,
    val ephemeral_key: String? = null,
    val ephemeral_pq_key: String? = null,
    val send_status: String? = null,
    val message_ts: String? = null,
    val created_at: String? = null,
    val metadata: MailItemMetadata? = null,
    val spf_result: String? = null,
    val dkim_result: String? = null,
    val dmarc_result: String? = null,
)

@Serializable
data class ThreadWithMessages(
    val thread: MailThread? = null,
    val messages: List<ThreadMessageItem> = emptyList(),
)

@Serializable
data class MailUserStatsResponse(
    val total_items: Int = 0,
    val inbox: Int = 0,
    val sent: Int = 0,
    val drafts: Int = 0,
    val scheduled: Int = 0,
    val starred: Int = 0,
    val archived: Int = 0,
    val spam: Int = 0,
    val trash: Int = 0,
    val unread: Int = 0,
    val storage_used_bytes: Long = 0,
    val storage_total_bytes: Long = 0,
)

@Serializable
data class PatchMetadataRequest(
    val encrypted_metadata: String? = null,
    val metadata_nonce: String? = null,
    val is_read: Boolean? = null,
    val is_starred: Boolean? = null,
    val is_pinned: Boolean? = null,
    val is_trashed: Boolean? = null,
    val is_archived: Boolean? = null,
    val is_spam: Boolean? = null,
)

@Serializable
data class PatchMetadataResponse(
    val success: Boolean = false,
    val updated_count: Int = 0,
)

@Serializable
data class BulkScopeRequest(
    val action: String,
    val ids: List<String>? = null,
    val scope: BulkScopeFilter? = null,
    val exclude_ids: List<String>? = null,
)

@Serializable
data class BulkScopeFilter(
    val item_type: String? = null,
    val is_archived: Boolean? = null,
    val is_trashed: Boolean? = null,
    val is_spam: Boolean? = null,
    val is_starred: Boolean? = null,
    val is_snoozed: Boolean? = null,
)

@Serializable
data class BulkScopeResponse(
    val batch_id: String? = null,
    val affected_count: Int = 0,
    val undoable: Boolean = false,
)

@Serializable
data class CreateMailItemRequest(
    val item_type: String,
    val encrypted_envelope: String,
    val envelope_nonce: String,
    val folder_token: String = "",
    val content_hash: String = "",
    val ephemeral_key: String? = null,
    val ephemeral_pq_key: String? = null,
    val sender_sealed: String? = null,
    val scheduled_at: String? = null,
    val is_external: Boolean? = null,
    val thread_token: String? = null,
    val encrypted_metadata: String? = null,
    val metadata_nonce: String? = null,
)

@Serializable
data class CreateMailItemResponse(
    val id: String? = null,
    val success: Boolean = false,
)

@Serializable
data class DeleteResponse(
    val success: Boolean = false,
    val deleted_count: Int = 0,
    val status: String? = null,
)

@Serializable
data class SyncMailItemsResponse(
    val items: List<MailItem> = emptyList(),
    val next_cursor: String? = null,
    val has_more: Boolean = false,
    val sync_token: String? = null,
)
