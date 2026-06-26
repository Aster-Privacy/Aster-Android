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

package org.astermail.android.mail

import org.astermail.android.BuildConfig
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.astermail.android.R
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.astermail.android.api.mail.BulkScopeFilter
import org.astermail.android.api.mail.BulkScopeRequest
import org.astermail.android.api.mail.BulkScopeResponse
import org.astermail.android.api.mail.CreateMailItemRequest
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.api.mail.PatchMetadataRequest
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.mail.ThreadWithMessages
import org.astermail.android.api.labels.LabelsApi
import org.astermail.android.api.scheduled.CreateScheduledRequest
import org.astermail.android.api.scheduled.ScheduledApi
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.api.send.ExternalSendRequest
import org.astermail.android.api.send.SendApi
import org.astermail.android.api.send.SimpleSendRequest
import org.astermail.android.api.send.SimpleSendResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.storage.SessionKeyStore

data class DecryptedEnvelope(
    val subject: String,
    val body_text: String,
    val body_html: String?,
    val from_name: String,
    val from_email: String,
    val to: List<Pair<String, String>>,
    val cc: List<Pair<String, String>>,
    val sent_at: String?,
    val raw_headers: List<Pair<String, String>> = emptyList(),
    val list_unsubscribe: String? = null,
    val sender_verification: String? = null,
)

const val ASTER_SUBJECT_BUNDLE_PREFIX = "ASTER_BUNDLE_V2"

internal data class SubjectBundle(val subject: String?, val body: String)

internal fun extract_subject_bundle(body: String): SubjectBundle {
    if (body.isEmpty() || !body.startsWith(ASTER_SUBJECT_BUNDLE_PREFIX)) {
        return SubjectBundle(null, body)
    }
    val payload = body.substring(ASTER_SUBJECT_BUNDLE_PREFIX.length)
    try {
        val obj = org.json.JSONObject(payload)
        val s = obj.opt("s")
        val b = obj.opt("b")
        if (s is String && b is String) {
            return SubjectBundle(s, b)
        }
    } catch (_: Throwable) {
    }
    return SubjectBundle(null, body)
}

data class InboxItem(
    val id: String,
    val thread_token: String?,
    val thread_message_count: Int,
    val sender_name: String,
    val sender_email: String,
    val subject: String,
    val preview: String,
    val timestamp: String,
    val is_read: Boolean,
    val is_starred: Boolean,
    val is_encrypted: Boolean,
    val has_attachments: Boolean,
    val is_trashed: Boolean,
    val is_archived: Boolean,
    val is_spam: Boolean,
    val labels: List<String>,
    val tag_tokens: List<String> = emptyList(),
    val category: String = "primary",
    val raw_item: MailItem,
)

data class AttachmentMeta(
    val filename: String,
    val content_type: String,
    val session_key: String,
    val content_id: String? = null,
)

data class ThreadMessageDecrypted(
    val id: String,
    val sender_name: String,
    val sender_email: String,
    val to_label: String,
    val timestamp: String,
    val body_text: String,
    val body_html: String?,
    val is_encrypted: Boolean,
    val is_read: Boolean,
    val raw_item: ThreadMessageItem,
    val to_addresses: List<String> = emptyList(),
    val cc_addresses: List<String> = emptyList(),
    val has_attachments: Boolean = false,
)

@Singleton
class MailRepository @Inject constructor(
    private val mail_api: MailApi,
    private val send_api: SendApi,
    private val snooze_api: org.astermail.android.api.snooze.SnoozeApi,
    private val labels_api: LabelsApi,
    private val session_key_store: SessionKeyStore,
    private val scheduled_api: ScheduledApi,
    private val ratchet_decryptor: org.astermail.android.mail.ratchet.RatchetDecryptor,
    private val ratchet_encryptor: org.astermail.android.mail.ratchet.RatchetEncryptor,
    @ApplicationContext private val context: Context,
) {
    private val pbkdf2_key_cache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    private val identity_key_cache = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()
    @Volatile private var cached_sent_folder_token: String? = null
    fun get_user_email(): String? = session_key_store.get_user_email()

    data class PendingUndoSend(
        val started_at_ms: Long,
        val duration_ms: Long,
        val draft_id: String?,
        val to: List<String>,
        val cc: List<String>,
        val bcc: List<String>,
        val subject: String,
        val body_html: String,
        val sender_email: String?,
        val sender_display_name: String?,
        val attachment_names: List<String>,
        val undo: () -> Unit,
    )

    private val app_scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default,
    )
    private val _pending_undo_send = kotlinx.coroutines.flow.MutableStateFlow<PendingUndoSend?>(null)
    val pending_undo_send: kotlinx.coroutines.flow.StateFlow<PendingUndoSend?> = _pending_undo_send
    private val pending_send_job_ref = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>(null)
    private val _send_result_events = kotlinx.coroutines.flow.MutableSharedFlow<Result<Unit>>(extraBufferCapacity = 8)
    val send_result_events: kotlinx.coroutines.flow.SharedFlow<Result<Unit>> = _send_result_events

    fun schedule_send_with_undo(
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body_html: String,
        sender_email: String?,
        sender_display_name: String?,
        thread_token: String? = null,
        expires_at: String? = null,
        expiry_password: String? = null,
        attachments: List<ExternalAttachmentPayload> = emptyList(),
        sender_alias_hash: String? = null,
        suppress_branding: Boolean? = null,
        undo_seconds: Int,
        draft_id: String? = null,
        on_completed: ((Result<Unit>) -> Unit)? = null,
    ) {
        pending_send_job_ref.getAndSet(null)?.cancel()
        val delay_ms = undo_seconds.coerceAtLeast(1) * 1000L
        val canceled_flag = java.util.concurrent.atomic.AtomicBoolean(false)
        val self_ref = java.util.concurrent.atomic.AtomicReference<kotlinx.coroutines.Job?>(null)
        val job = app_scope.launch {
            kotlinx.coroutines.delay(delay_ms)
            _pending_undo_send.value = null
            if (!canceled_flag.get()) {
                val result = send_email(
                    to = to,
                    cc = cc,
                    bcc = bcc,
                    subject = subject,
                    body_html = body_html,
                    sender_email = sender_email,
                    sender_display_name = sender_display_name,
                    thread_token = thread_token,
                    expires_at = expires_at,
                    expiry_password = expiry_password,
                    attachments = attachments,
                    sender_alias_hash = sender_alias_hash,
                    suppress_branding = suppress_branding,
                )
                val unit_result: Result<Unit> = result.map { }
                if (result.isSuccess) {
                    draft_id?.takeIf { it.isNotBlank() }?.let { runCatching { delete_draft(it) } }
                }
                _send_result_events.tryEmit(unit_result)
                on_completed?.invoke(unit_result)
            }
            self_ref.get()?.let { pending_send_job_ref.compareAndSet(it, null) }
        }
        self_ref.set(job)
        pending_send_job_ref.set(job)
        _pending_undo_send.value = PendingUndoSend(
            started_at_ms = System.currentTimeMillis(),
            duration_ms = delay_ms,
            draft_id = draft_id?.takeIf { it.isNotBlank() },
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body_html = body_html,
            sender_email = sender_email,
            sender_display_name = sender_display_name,
            attachment_names = attachments.map { it.filename },
            undo = {
                canceled_flag.set(true)
                job.cancel()
                pending_send_job_ref.compareAndSet(job, null)
                _pending_undo_send.value = null
            },
        )
    }

    fun clear_caches() {
        pbkdf2_key_cache.values.forEach { it.fill(0) }
        pbkdf2_key_cache.clear()
        identity_key_cache.values.forEach { it.fill(0) }
        identity_key_cache.clear()
        cached_sent_folder_token = null
    }

    suspend fun fetch_inbox(
        limit: Int = 50,
        cursor: String? = null,
        item_type: String = "received",
        label_token: String? = null,
        tag_token: String? = null,
    ): Result<InboxPage> = runCatching {
        val is_received = item_type == "received"
        val response = mail_api.list_messages(
            limit = limit,
            cursor = cursor,
            item_type = item_type,
            label_token = label_token,
            tag_token = tag_token,
            is_snoozed = if (is_received) false else null,
        )
        val filtered_raw = if (is_received) {
            val now_iso = java.time.Instant.now().toString()
            response.items.filter { raw ->
                val until = raw.snoozed_until ?: raw.metadata?.snoozed_until
                until == null || until <= now_iso
            }
        } else response.items
        val items = decrypt_items_parallel(filtered_raw)
        InboxPage(
            items = items,
            has_more = response.has_more,
            next_cursor = response.next_cursor,
            total = response.total,
        )
    }

    suspend fun fetch_sent(limit: Int = 50, cursor: String? = null): Result<InboxPage> {
        return fetch_inbox(limit, cursor, "sent", label_token = null)
    }

    suspend fun fetch_drafts(limit: Int = 50, cursor: String? = null): Result<InboxPage> = runCatching {
        val response = mail_api.list_drafts(limit = limit, cursor = cursor)
        val items = response.items.map { draft -> decrypt_draft_item(draft) }
        InboxPage(
            items = items,
            has_more = response.has_more,
            next_cursor = response.next_cursor,
            total = items.size,
        )
    }

    suspend fun fetch_starred(limit: Int = 50, cursor: String? = null): Result<InboxPage> = runCatching {
        val response = mail_api.list_messages(limit = limit, cursor = cursor, is_starred = true)
        val items = decrypt_items_parallel(response.items)
        InboxPage(items, response.has_more, response.next_cursor, response.total)
    }

    suspend fun fetch_trash(limit: Int = 50, cursor: String? = null): Result<InboxPage> = runCatching {
        val response = mail_api.list_messages(limit = limit, cursor = cursor, is_trashed = true)
        val items = decrypt_items_parallel(response.items)
        InboxPage(items, response.has_more, response.next_cursor, response.total)
    }

    suspend fun fetch_spam(limit: Int = 50, cursor: String? = null): Result<InboxPage> = runCatching {
        val response = mail_api.list_messages(limit = limit, cursor = cursor, is_spam = true)
        val items = decrypt_items_parallel(response.items)
        InboxPage(items, response.has_more, response.next_cursor, response.total)
    }

    suspend fun fetch_archive(limit: Int = 50, cursor: String? = null): Result<InboxPage> = runCatching {
        val response = mail_api.list_messages(limit = limit, cursor = cursor, is_archived = true)
        val items = decrypt_items_parallel(response.items)
        InboxPage(items, response.has_more, response.next_cursor, response.total)
    }

    suspend fun fetch_scheduled(limit: Int = 50, cursor: String? = null): Result<InboxPage> =
        fetch_inbox(limit, cursor, "scheduled")

    suspend fun fetch_snoozed(limit: Int = 50, cursor: String? = null): Result<InboxPage> = runCatching {
        val response = mail_api.list_messages(limit = limit, cursor = cursor, is_snoozed = true)
        val items = decrypt_items_parallel(response.items)
        InboxPage(items, response.has_more, response.next_cursor, response.total)
    }

    suspend fun fetch_draft_for_compose(
        draft_id: String,
    ): Result<Pair<InboxItem, DecryptedEnvelope?>> = runCatching {
        var cursor: String? = null
        var draft: org.astermail.android.api.mail.DraftItem? = null
        var pages = 0
        while (pages < 50) {
            val response = mail_api.list_drafts(limit = 100, cursor = cursor)
            draft = response.items.firstOrNull { it.id == draft_id }
            if (draft != null) break
            if (!response.has_more || response.next_cursor == null) break
            cursor = response.next_cursor
            pages++
        }
        val found = draft ?: throw IllegalStateException("draft not found")
        val envelope = try_decrypt_envelope(found.encrypted_content, found.content_nonce)
        val item = decrypt_draft_item(found)
        Pair(item, envelope)
    }

    suspend fun fetch_all_for_search(max_pages: Int = 20): Result<List<InboxItem>> = runCatching {
        val seen = HashSet<String>()
        val all = mutableListOf<InboxItem>()
        suspend fun drain(is_trashed: Boolean? = null, is_archived: Boolean? = null, is_spam: Boolean? = null) {
            var cursor: String? = null
            repeat(max_pages) {
                val response = mail_api.list_messages(
                    limit = 50,
                    cursor = cursor,
                    is_trashed = is_trashed,
                    is_archived = is_archived,
                    is_spam = is_spam,
                )
                val items = decrypt_items_parallel(response.items)
                for (item in items) if (seen.add(item.id)) all.add(item)
                if (!response.has_more || response.next_cursor == null) return
                cursor = response.next_cursor
            }
        }
        drain()
        drain(is_trashed = true)
        drain(is_archived = true)
        drain(is_spam = true)
        all.toList()
    }

    suspend fun fetch_thread(thread_token: String): Result<List<ThreadMessageDecrypted>> = runCatching {
        val response = mail_api.get_thread_messages(thread_token)
        coroutineScope {
            response.messages.map { msg ->
                async(Dispatchers.IO) { decrypt_thread_message(msg) }
            }.awaitAll()
        }
    }

    suspend fun fetch_single_message(item_id: String): Result<InboxItem> = runCatching {
        val item = mail_api.get_message(item_id)
        decrypt_inbox_item(item)
    }

    suspend fun get_stats(): Result<MailUserStatsResponse> = runCatching {
        mail_api.get_stats()
    }

    suspend fun mark_read(item_id: String, is_read: Boolean, raw_item: MailItem? = null): Result<Unit> = runCatching {
        val request = build_metadata_patch(raw_item, mapOf("is_read" to is_read))
        mail_api.patch_metadata(item_id, request)
        Unit
    }

    suspend fun toggle_star(item_id: String, is_starred: Boolean, raw_item: MailItem? = null): Result<Unit> = runCatching {
        val request = build_metadata_patch(raw_item, mapOf("is_starred" to is_starred))
        mail_api.patch_metadata(item_id, request)
        Unit
    }

    suspend fun toggle_pin(item_id: String, is_pinned: Boolean, raw_item: MailItem? = null): Result<Unit> = runCatching {
        val request = build_metadata_patch(raw_item, mapOf("is_pinned" to is_pinned))
        mail_api.patch_metadata(item_id, request)
        Unit
    }

    suspend fun snooze(item_id: String, snoozed_until_iso: String): Result<Unit> = runCatching {
        snooze_api.snooze(
            org.astermail.android.api.snooze.SnoozeRequest(
                mail_item_id = item_id,
                snoozed_until = snoozed_until_iso,
            ),
        )
        Unit
    }

    suspend fun unsnooze(item_id: String): Result<Unit> = runCatching {
        snooze_api.unsnooze_by_mail_item(item_id)
    }

    suspend fun add_label_to_item(item_id: String, label_token: String): Result<Unit> = runCatching {
        mail_api.add_label_to_item(item_id, label_token)
    }

    suspend fun remove_label_from_item(item_id: String, label_token: String): Result<Unit> = runCatching {
        mail_api.remove_label_from_item(item_id, label_token)
    }

    suspend fun add_tag_to_item(item_id: String, tag_token: String): Result<Unit> = runCatching {
        mail_api.add_tag_to_item(item_id, tag_token)
    }

    suspend fun remove_tag_from_item(item_id: String, tag_token: String): Result<Unit> = runCatching {
        mail_api.remove_tag_from_item(item_id, tag_token)
    }

    suspend fun archive(item_ids: List<String>, raw_items: List<MailItem?> = emptyList()): Result<Unit> = runCatching {
        mail_api.bulk_action(BulkScopeRequest(action = "archive", ids = item_ids))
        item_ids.forEachIndexed { index, item_id ->
            val raw_item = raw_items.getOrNull(index)
            val request = build_metadata_patch(
                raw_item,
                mapOf(
                    "is_archived" to true,
                    "is_trashed" to false,
                    "is_spam" to false,
                ),
            )
            runCatching { mail_api.patch_metadata(item_id, request) }
        }
        Unit
    }

    suspend fun trash(item_ids: List<String>, raw_items: List<MailItem?> = emptyList()): Result<Unit> = runCatching {
        mail_api.bulk_action(BulkScopeRequest(action = "trash", ids = item_ids))
        Unit
    }

    suspend fun mark_spam(item_ids: List<String>, raw_items: List<MailItem?> = emptyList()): Result<Unit> = runCatching {
        mail_api.bulk_action(BulkScopeRequest(action = "mark_spam", ids = item_ids))
    }

    suspend fun unmark_spam(item_ids: List<String>): Result<BulkScopeResponse> = runCatching {
        mail_api.bulk_action(BulkScopeRequest(action = "unmark_spam", ids = item_ids))
    }

    suspend fun unarchive(item_ids: List<String>, raw_items: List<MailItem?> = emptyList()): Result<BulkScopeResponse> = runCatching {
        val response = mail_api.bulk_action(BulkScopeRequest(action = "unarchive", ids = item_ids))
        item_ids.forEachIndexed { index, item_id ->
            val raw_item = raw_items.getOrNull(index)
            val request = build_metadata_patch(
                raw_item,
                mapOf("is_archived" to false),
            )
            runCatching { mail_api.patch_metadata(item_id, request) }
        }
        response
    }

    suspend fun restore_trash(item_ids: List<String>): Result<BulkScopeResponse> = runCatching {
        mail_api.bulk_action(BulkScopeRequest(action = "restore_trash", ids = item_ids))
    }

    suspend fun mark_read_bulk(item_ids: List<String>): Result<BulkScopeResponse> = runCatching {
        mail_api.bulk_action(BulkScopeRequest(action = "mark_read", ids = item_ids))
    }

    suspend fun mark_all_read_scope(folder: String): Result<BulkScopeResponse> = runCatching {
        val scope = folder_to_bulk_scope(folder)
        mail_api.bulk_action(BulkScopeRequest(action = "mark_read", scope = scope))
    }

    suspend fun mark_all_unread_scope(folder: String): Result<BulkScopeResponse> = runCatching {
        val scope = folder_to_bulk_scope(folder)
        mail_api.bulk_action(BulkScopeRequest(action = "mark_unread", scope = scope))
    }

    private fun folder_to_bulk_scope(folder: String): org.astermail.android.api.mail.BulkScopeFilter {
        return when (folder) {
            "inbox" -> BulkScopeFilter(item_type = "received")
            "sent" -> BulkScopeFilter(item_type = "sent")
            "starred" -> BulkScopeFilter(is_starred = true)
            "trash" -> BulkScopeFilter(is_trashed = true)
            "spam" -> BulkScopeFilter(is_spam = true)
            "archive" -> BulkScopeFilter(is_archived = true)
            "snoozed" -> BulkScopeFilter(is_snoozed = true)
            else -> BulkScopeFilter()
        }
    }

    suspend fun delete_draft(draft_id: String): Result<Unit> = runCatching {
        mail_api.delete_draft(draft_id)
        Unit
    }

    suspend fun delete_permanent(item_id: String): Result<Unit> = runCatching {
        mail_api.delete_permanent(item_id)
        Unit
    }

    suspend fun empty_trash(): Result<Unit> = runCatching {
        mail_api.empty_trash()
        Unit
    }

    private fun decrypt_draft_item(draft: org.astermail.android.api.mail.DraftItem): InboxItem {
        val envelope = try_decrypt_envelope(draft.encrypted_content, draft.content_nonce)
        val user_email = get_user_email() ?: ""
        return InboxItem(
            id = draft.id,
            thread_token = draft.thread_token ?: draft.id,
            thread_message_count = 1,
            sender_name = context.getString(R.string.sender_draft),
            sender_email = user_email,
            subject = envelope?.subject?.takeIf { it.isNotBlank() } ?: context.getString(R.string.no_subject),
            preview = envelope?.let { clean_preview(it.body_text, it.body_html) } ?: "",
            timestamp = draft.updated_at ?: draft.created_at ?: "",
            is_read = true,
            is_starred = false,
            is_encrypted = true,
            has_attachments = false,
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            labels = emptyList(),
            raw_item = MailItem(
                id = draft.id,
                item_type = "draft",
                encrypted_envelope = draft.encrypted_content,
                envelope_nonce = draft.content_nonce,
                thread_token = draft.thread_token,
                created_at = draft.created_at,
            ),
        )
    }

    suspend fun decrypt_items_for_cache(items: List<MailItem>): List<InboxItem> =
        decrypt_items_parallel(items)

    private suspend fun decrypt_items_parallel(items: List<MailItem>): List<InboxItem> =
        coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) { decrypt_inbox_item(item) }
            }.awaitAll()
        }

    private fun decrypt_inbox_item(item: MailItem): InboxItem {
        val envelope = try_decrypt_envelope(item.encrypted_envelope, item.envelope_nonce)
        val enc_meta = item.encrypted_metadata
        val meta_nonce = item.metadata_nonce
        val meta = item.metadata
            ?: if (!enc_meta.isNullOrBlank() && !meta_nonce.isNullOrBlank()) {
                decrypt_mail_metadata(enc_meta, meta_nonce)
            } else null
        return InboxItem(
            id = item.id,
            thread_token = item.thread_token,
            thread_message_count = item.thread_message_count ?: 1,
            sender_name = envelope?.from_name ?: "",
            sender_email = envelope?.from_email ?: "",
            subject = envelope?.subject ?: "",
            preview = envelope?.let { clean_preview(it.body_text, it.body_html) } ?: "",
            timestamp = item.message_ts ?: item.created_at ?: "",
            is_read = (item.is_read == true) || (meta?.is_read == true) || ((meta?.is_trashed ?: item.is_trashed) == true),
            is_starred = meta?.is_starred ?: false,
            is_encrypted = item.encrypted_envelope != null,
            has_attachments = meta?.has_attachments ?: false,
            is_trashed = meta?.is_trashed ?: item.is_trashed ?: false,
            is_archived = meta?.is_archived ?: false,
            is_spam = meta?.is_spam ?: item.is_spam ?: false,
            labels = item.labels?.mapNotNull { it.folder_token } ?: emptyList(),
            tag_tokens = item.tag_tokens ?: emptyList(),
            category = if (envelope != null) classify(envelope, meta) else "primary",
            raw_item = if (meta != null) item.copy(metadata = meta) else item,
        )
    }

    fun decrypt_single_thread_message(item: ThreadMessageItem): ThreadMessageDecrypted {
        return decrypt_thread_message(item)
    }

    private fun decrypt_thread_message(item: ThreadMessageItem): ThreadMessageDecrypted {
        val envelope = try_decrypt_envelope(item.encrypted_envelope, item.envelope_nonce)
        val meta = item.metadata
        val to_names = envelope?.to?.map { it.first.ifBlank { it.second } } ?: listOf("me")
        return ThreadMessageDecrypted(
            id = item.id,
            sender_name = envelope?.from_name ?: "",
            sender_email = envelope?.from_email ?: "",
            to_label = to_names.joinToString(", "),
            timestamp = item.message_ts ?: item.created_at ?: "",
            body_text = envelope?.body_text ?: "",
            body_html = envelope?.body_html,
            is_encrypted = item.encrypted_envelope != null,
            is_read = meta?.is_read ?: true,
            raw_item = item,
            to_addresses = envelope?.to?.map { it.second } ?: emptyList(),
            cc_addresses = envelope?.cc?.map { it.second } ?: emptyList(),
            has_attachments = meta?.has_attachments ?: false,
        )
    }

    private fun pgp_placeholder_envelope(): DecryptedEnvelope = DecryptedEnvelope(
        subject = context.getString(R.string.pgp_encrypted_subject),
        body_text = context.getString(R.string.pgp_encrypted_body),
        body_html = null,
        from_name = "",
        from_email = "",
        to = emptyList(),
        cc = emptyList(),
        sent_at = null,
    )

    fun decrypt_envelope_public(
        encrypted_envelope: String?,
        envelope_nonce: String?,
    ): DecryptedEnvelope? = try_decrypt_envelope(encrypted_envelope, envelope_nonce)

    fun decrypt_item_for_export(item: MailItem): DecryptedEnvelope? =
        try_decrypt_envelope(item.encrypted_envelope, item.envelope_nonce)

    private fun try_decrypt_envelope(
        encrypted_envelope: String?,
        envelope_nonce: String?,
    ): DecryptedEnvelope? {
        if (encrypted_envelope.isNullOrBlank()) return null
        return try {
            val nonce_bytes = if (envelope_nonce.isNullOrBlank()) null
                else android.util.Base64.decode(envelope_nonce, android.util.Base64.DEFAULT)

            val decrypted: ByteArray = when {
                nonce_bytes == null || nonce_bytes.isEmpty() -> {
                    val raw = android.util.Base64.decode(encrypted_envelope, android.util.Base64.DEFAULT)
                    val text = String(raw, Charsets.UTF_8)
                    if (text.trimStart().startsWith("-----BEGIN PGP")) {
                        val pgp_result = try_pgp_decrypt(text)
                        if (pgp_result != null) {
                            if (MimeParser.looks_like_mime(pgp_result)) {
                                val mime = MimeParser.parse(pgp_result)
                                return DecryptedEnvelope(
                                    subject = "",
                                    body_text = mime.text ?: "",
                                    body_html = mime.html,
                                    from_email = "",
                                    from_name = "",
                                    to = emptyList(),
                                    cc = emptyList(),
                                    sent_at = null,
                                )
                            }
                            pgp_result.toByteArray(Charsets.UTF_8)
                        } else {
                            return pgp_placeholder_envelope()
                        }
                    } else {
                        raw
                    }
                }
                nonce_bytes.size == 1 && nonce_bytes[0] == 1.toByte() -> {
                    decrypt_envelope_pbkdf2(encrypted_envelope)
                }
                else -> {
                    decrypt_envelope_identity_key(encrypted_envelope, nonce_bytes)
                }
            }

            val json_str = String(decrypted, Charsets.UTF_8)
            decrypted.fill(0)
            val envelope = parse_envelope_json(json_str)
            if (envelope != null) decrypt_pgp_body_fields(envelope) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun decrypt_envelope_pbkdf2(encrypted_b64: String): ByteArray {
        val passphrase = session_key_store.get_passphrase()
            ?: throw IllegalStateException("no passphrase")
        try {
            val data = android.util.Base64.decode(encrypted_b64, android.util.Base64.DEFAULT)
            val salt = data.sliceArray(0 until 16)
            val iv = data.sliceArray(16 until 28)
            val ciphertext = data.sliceArray(28 until data.size)

            val salt_hex = salt.joinToString("") { "%02x".format(it) }
            val key_bytes = pbkdf2_key_cache.getOrPut(salt_hex) {
                val c = String(passphrase, Charsets.UTF_8).toCharArray()
                val spec = PBEKeySpec(c, salt, PBKDF2_ITERATIONS, 256)
                val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
                spec.clearPassword()
                c.fill(0.toChar())
                derived
            }

            return try {
                aes_gcm_decrypt(ciphertext, key_bytes, iv)
            } catch (_: Throwable) {
                val legacy = session_key_store.get_legacy_keks()
                if (!legacy.isNullOrEmpty()) {
                    for (kek_b64 in legacy) {
                        try {
                            val kek = android.util.Base64.decode(kek_b64, android.util.Base64.DEFAULT)
                            return aes_gcm_decrypt(ciphertext, kek, iv)
                        } catch (_: Throwable) {
                        }
                    }
                }
                throw IllegalStateException("pbkdf2 decryption failed with all keys")
            }
        } finally {
            passphrase.fill(0)
        }
    }

    private fun decrypt_envelope_identity_key(encrypted_b64: String, nonce: ByteArray): ByteArray {
        val identity_key = session_key_store.get_identity_key()
            ?: throw IllegalStateException("no identity key")
        val ciphertext = android.util.Base64.decode(encrypted_b64, android.util.Base64.DEFAULT)

        for (version in ENVELOPE_VERSIONS) {
            try {
                val key = identity_key_cache.getOrPut(version) {
                    val material = (identity_key + version).toByteArray(Charsets.UTF_8)
                    MessageDigest.getInstance("SHA-256").digest(material)
                }
                return aes_gcm_decrypt(ciphertext, key, nonce)
            } catch (_: Throwable) {
            }
        }

        val previous_keys = session_key_store.get_previous_keys()
        if (!previous_keys.isNullOrEmpty()) {
            for (prev_key in previous_keys) {
                for (version in ENVELOPE_VERSIONS) {
                    try {
                        val cache_key = "prev_${prev_key.hashCode()}_$version"
                        val key = identity_key_cache.getOrPut(cache_key) {
                            val material = (prev_key + version).toByteArray(Charsets.UTF_8)
                            MessageDigest.getInstance("SHA-256").digest(material)
                        }
                        return aes_gcm_decrypt(ciphertext, key, nonce)
                    } catch (_: Throwable) {
                    }
                }
            }
        }

        throw IllegalStateException("all identity key versions failed")
    }

    private fun aes_gcm_decrypt(ciphertext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        if (iv.size != 12) throw IllegalStateException("invalid gcm nonce length")
        if (key.size != 16 && key.size != 24 && key.size != 32) throw IllegalStateException("invalid aes key length")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv),
        )
        return cipher.doFinal(ciphertext)
    }

    private fun aes_gcm_encrypt(plaintext: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(128, iv),
        )
        return cipher.doFinal(plaintext)
    }

    private fun hkdf_sha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hmac_extract = Mac.getInstance("HmacSHA256")
        hmac_extract.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = hmac_extract.doFinal(ikm)

        val hmac_expand = Mac.getInstance("HmacSHA256")
        hmac_expand.init(SecretKeySpec(prk, "HmacSHA256"))
        val blocks = (length + 31) / 32
        val okm = ByteArray(length)
        var t = ByteArray(0)
        for (i in 1..blocks) {
            hmac_expand.reset()
            hmac_expand.update(t)
            hmac_expand.update(info)
            hmac_expand.update(i.toByte())
            t = hmac_expand.doFinal()
            val offset = (i - 1) * 32
            val copy_len = minOf(32, length - offset)
            System.arraycopy(t, 0, okm, offset, copy_len)
        }
        prk.fill(0)
        return okm
    }

    private fun derive_encryption_key(): ByteArray? {
        val passphrase = session_key_store.get_passphrase() ?: return null
        try {
            val prefix = "aster-hkdf-salt-v1:".toByteArray(Charsets.UTF_8)
            val combined = ByteArray(prefix.size + passphrase.size)
            System.arraycopy(prefix, 0, combined, 0, prefix.size)
            System.arraycopy(passphrase, 0, combined, prefix.size, passphrase.size)
            val salt = MessageDigest.getInstance("SHA-256").digest(combined)
            combined.fill(0)

            val info = "aster-storage-encryption-key-v1".toByteArray(Charsets.UTF_8)
            val key = hkdf_sha256(passphrase, salt, info, 32)
            salt.fill(0)
            return key
        } finally {
            passphrase.fill(0)
        }
    }

    private fun derive_metadata_key(): ByteArray? {
        val master = derive_encryption_key() ?: return null
        try {
            val salt = "aster-metadata-salt-v1".toByteArray(Charsets.UTF_8)
            val info = "aster-metadata-encryption-v1:mail-item-metadata".toByteArray(Charsets.UTF_8)
            return hkdf_sha256(master, salt, info, 32)
        } finally {
            master.fill(0)
        }
    }

    private val metadata_json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decrypt_mail_metadata(encrypted_b64: String, nonce_b64: String): MailItemMetadata? {
        val key = derive_metadata_key() ?: return null
        return try {
            val ciphertext = android.util.Base64.decode(encrypted_b64, android.util.Base64.DEFAULT)
            val nonce = android.util.Base64.decode(nonce_b64, android.util.Base64.DEFAULT)
            val plaintext = aes_gcm_decrypt(ciphertext, key, nonce)
            val json_str = String(plaintext, Charsets.UTF_8)
            plaintext.fill(0)
            metadata_json.decodeFromString<MailItemMetadata>(json_str)
        } catch (_: Throwable) {
            null
        } finally {
            key.fill(0)
        }
    }

    private fun encrypt_mail_metadata(metadata: MailItemMetadata): Pair<String, String>? {
        val key = derive_metadata_key() ?: return null
        return try {
            val plaintext = metadata_json.encodeToString(metadata).toByteArray(Charsets.UTF_8)
            val nonce = ByteArray(12)
            SecureRandom().nextBytes(nonce)
            val ciphertext = aes_gcm_encrypt(plaintext, key, nonce)
            plaintext.fill(0)
            val enc_b64 = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP)
            val nonce_b64 = android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP)
            enc_b64 to nonce_b64
        } catch (_: Throwable) {
            null
        } finally {
            key.fill(0)
        }
    }

    private fun build_metadata_patch(raw_item: MailItem?, updates: Map<String, Any>): PatchMetadataRequest {
        var current_metadata: MailItemMetadata? = null
        val enc_meta = raw_item?.encrypted_metadata
        val meta_nonce = raw_item?.metadata_nonce
        if (enc_meta != null && meta_nonce != null) {
            current_metadata = decrypt_mail_metadata(enc_meta, meta_nonce)
        }
        if (current_metadata == null) {
            current_metadata = raw_item?.metadata ?: MailItemMetadata()
        }

        val updated = current_metadata.copy(
            is_read = (updates["is_read"] as? Boolean) ?: current_metadata.is_read,
            is_starred = (updates["is_starred"] as? Boolean) ?: current_metadata.is_starred,
            is_pinned = (updates["is_pinned"] as? Boolean) ?: current_metadata.is_pinned,
            is_trashed = (updates["is_trashed"] as? Boolean) ?: current_metadata.is_trashed,
            is_archived = (updates["is_archived"] as? Boolean) ?: current_metadata.is_archived,
            is_spam = (updates["is_spam"] as? Boolean) ?: current_metadata.is_spam,
        )

        val encrypted = encrypt_mail_metadata(updated)

        return PatchMetadataRequest(
            encrypted_metadata = encrypted?.first,
            metadata_nonce = encrypted?.second,
            is_read = if (updates.containsKey("is_read")) updated.is_read else null,
            is_starred = if (updates.containsKey("is_starred")) updated.is_starred else null,
            is_pinned = if (updates.containsKey("is_pinned")) updated.is_pinned else null,
            is_trashed = if (updates.containsKey("is_trashed")) updated.is_trashed else null,
            is_archived = if (updates.containsKey("is_archived")) updated.is_archived else null,
            is_spam = if (updates.containsKey("is_spam")) updated.is_spam else null,
        )
    }

    fun decrypt_attachment_meta(encrypted_meta: String, meta_nonce: String?): AttachmentMeta? {
        return try {
            val raw = android.util.Base64.decode(encrypted_meta, android.util.Base64.DEFAULT)
            val text = String(raw, Charsets.UTF_8)

            try {
                val json = org.json.JSONObject(text)
                if (json.has("filename") && json.has("content_type")) {
                    return AttachmentMeta(
                        filename = json.getString("filename"),
                        content_type = json.getString("content_type"),
                        session_key = json.optString("session_key", ""),
                        content_id = if (json.has("content_id")) json.getString("content_id") else null,
                    )
                }
            } catch (_: Throwable) {}

            if (text.trimStart().startsWith("-----BEGIN PGP")) {
                val pgp_result = try_pgp_decrypt(text)
                if (pgp_result != null) {
                    val json = org.json.JSONObject(pgp_result)
                    return AttachmentMeta(
                        filename = json.getString("filename"),
                        content_type = json.getString("content_type"),
                        session_key = json.optString("session_key", ""),
                        content_id = if (json.has("content_id")) json.getString("content_id") else null,
                    )
                }
            }

            val nonce_bytes = if (meta_nonce.isNullOrBlank()) null
                else android.util.Base64.decode(meta_nonce, android.util.Base64.DEFAULT)

            val decrypted: ByteArray = when {
                nonce_bytes == null || nonce_bytes.isEmpty() -> {
                    decrypt_envelope_pbkdf2(encrypted_meta)
                }
                nonce_bytes.size == 1 && nonce_bytes[0] == 1.toByte() -> {
                    decrypt_envelope_pbkdf2(encrypted_meta)
                }
                else -> {
                    decrypt_envelope_identity_key(encrypted_meta, nonce_bytes)
                }
            }

            val json_str = String(decrypted, Charsets.UTF_8)
            decrypted.fill(0)
            val json = org.json.JSONObject(json_str)
            AttachmentMeta(
                filename = json.getString("filename"),
                content_type = json.getString("content_type"),
                session_key = json.optString("session_key", ""),
                content_id = if (json.has("content_id")) json.getString("content_id") else null,
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun decrypt_attachment_data(
        encrypted_data_b64: String,
        data_nonce_b64: String,
        session_key_b64: String,
    ): ByteArray {
        if (session_key_b64.isBlank()) {
            return android.util.Base64.decode(encrypted_data_b64, android.util.Base64.DEFAULT)
        }
        val key = android.util.Base64.decode(session_key_b64, android.util.Base64.DEFAULT)
        if (key.isEmpty()) {
            return android.util.Base64.decode(encrypted_data_b64, android.util.Base64.DEFAULT)
        }
        try {
            val ciphertext = android.util.Base64.decode(encrypted_data_b64, android.util.Base64.DEFAULT)
            val nonce = android.util.Base64.decode(data_nonce_b64, android.util.Base64.DEFAULT)
            return aes_gcm_decrypt(ciphertext, key, nonce)
        } finally {
            key.fill(0)
        }
    }

    suspend fun find_messages_with_attachments(mail_item_ids: List<String>): List<String> {
        return try {
            val response = mail_api.batch_attachment_meta(mail_item_ids)
            response.items.filter { it.value.isNotEmpty() }.keys.toList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun fetch_attachments_for_message(
        mail_item_id: String,
    ): List<org.astermail.android.ui.mail.MessageAttachment> {
        return try {
            val api_response = mail_api.list_attachments(mail_item_id)
            val api_attachments = api_response.attachments
            api_attachments.mapNotNull { att ->
                val meta = decrypt_attachment_meta(att.encrypted_meta, att.meta_nonce)
                if (meta != null) {
                    org.astermail.android.ui.mail.MessageAttachment(
                        id = att.id,
                        filename = meta.filename,
                        content_type = meta.content_type,
                        size_bytes = att.size_bytes,
                        encrypted_data = att.encrypted_data,
                        data_nonce = att.data_nonce,
                        session_key = meta.session_key,
                        content_id = meta.content_id,
                    )
                } else null
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    suspend fun download_attachment(
        attachment_id: String,
    ): Pair<org.astermail.android.ui.mail.MessageAttachment, ByteArray>? {
        return try {
            val att = mail_api.get_attachment(attachment_id)
            val meta = decrypt_attachment_meta(att.encrypted_meta, att.meta_nonce) ?: return null
            val data = decrypt_attachment_data(att.encrypted_data, att.data_nonce, meta.session_key)
            Pair(
                org.astermail.android.ui.mail.MessageAttachment(
                    id = att.id,
                    filename = meta.filename,
                    content_type = meta.content_type,
                    size_bytes = att.size_bytes,
                    session_key = meta.session_key,
                ),
                data,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parse_envelope_json(json_str: String): DecryptedEnvelope? {
        return try {
            val obj = org.json.JSONObject(json_str)

            val (from_name_raw, from_email_raw) = parse_from_field(obj)
            val from_email = from_email_raw
            val from_name = from_name_raw.ifBlank {
                from_email.substringBefore('@').ifBlank { from_email }
            }

            val to_arr = if (obj.has("to_recipients")) {
                parse_email_string_list(obj.optJSONArray("to_recipients"))
            } else {
                parse_address_list(obj.optJSONArray("to"))
            }
            val cc_arr = if (obj.has("cc_recipients")) {
                parse_email_string_list(obj.optJSONArray("cc_recipients"))
            } else {
                parse_address_list(obj.optJSONArray("cc"))
            }

            val raw_text = read_string(obj, "body_text", "text_body", "message")
            val raw_html = read_string(obj, "body_html", "html_body")

            val resolved = resolve_body(raw_text, raw_html)

            val raw_headers = parse_raw_headers(obj.optJSONArray("raw_headers"))
            val list_unsubscribe = raw_headers.firstOrNull {
                it.first.equals("list-unsubscribe", ignoreCase = true)
            }?.second

            DecryptedEnvelope(
                subject = obj.optString("subject", ""),
                body_text = resolved.first,
                body_html = resolved.second,
                from_name = from_name,
                from_email = from_email,
                to = to_arr,
                cc = cc_arr,
                sent_at = if (obj.has("sent_at")) obj.getString("sent_at") else null,
                raw_headers = raw_headers,
                list_unsubscribe = list_unsubscribe,
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun parse_raw_headers(arr: org.json.JSONArray?): List<Pair<String, String>> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "")
            if (name.isEmpty()) continue
            result.add(name to obj.optString("value", ""))
        }
        return result
    }

    private fun parse_email_string_list(arr: org.json.JSONArray?): List<Pair<String, String>> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val raw = arr.optString(i, "").trim()
            if (raw.isEmpty()) continue
            val angle = raw.indexOf('<')
            if (angle > 0 && raw.contains('>')) {
                val name = raw.substring(0, angle).trim().trim('"')
                val email = raw.substring(angle + 1, raw.indexOf('>')).trim()
                result.add(name to email)
            } else {
                result.add("" to raw)
            }
        }
        return result
    }

    private fun read_string(obj: org.json.JSONObject, vararg keys: String): String? {
        for (key in keys) {
            if (!obj.has(key) || obj.isNull(key)) continue
            val v = obj.optString(key, "")
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun resolve_body(raw_text: String?, raw_html: String?): Pair<String, String?> {
        var html = raw_html
        var text = raw_text

        html?.let { extracted ->
            MimeExtractor.try_extract_typed(extracted)?.let { mime ->
                if (mime.is_html) html = mime.content
                else { text = text ?: mime.content; html = null }
            }
        }
        text?.let { extracted ->
            MimeExtractor.try_extract_typed(extracted)?.let { mime ->
                if (mime.is_html && html == null) html = mime.content
                else if (!mime.is_html) text = mime.content
            }
        }

        if (html.isNullOrBlank()) html = null

        val body_text = when {
            !text.isNullOrBlank() -> text!!
            html != null -> strip_html(html!!)
            else -> ""
        }

        return body_text to html
    }

    private fun parse_from_field(obj: org.json.JSONObject): Pair<String, String> {
        val from_obj = obj.optJSONObject("from")
        if (from_obj != null) {
            return from_obj.optString("name", "") to from_obj.optString("email", "")
        }
        val from_str = obj.optString("from", "")
        if (from_str.isBlank()) return "" to ""
        val angle = from_str.indexOf('<')
        if (angle > 0 && from_str.contains('>')) {
            val name = from_str.substring(0, angle).trim().trim('"')
            val email = from_str.substring(angle + 1, from_str.indexOf('>')).trim()
            return name to email
        }
        if (from_str.contains('@')) return "" to from_str.trim()
        return from_str.trim() to ""
    }

    private fun parse_address_list(arr: org.json.JSONArray?): List<Pair<String, String>> {
        if (arr == null) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until arr.length()) {
            val item = arr.opt(i) ?: continue
            if (item is org.json.JSONObject) {
                result.add(item.optString("name", "") to item.optString("email", ""))
            } else {
                val s = item.toString()
                val angle = s.indexOf('<')
                if (angle > 0 && s.contains('>')) {
                    result.add(
                        s.substring(0, angle).trim().trim('"') to
                            s.substring(angle + 1, s.indexOf('>')).trim(),
                    )
                } else {
                    result.add("" to s.trim())
                }
            }
        }
        return result
    }

    private fun strip_html(html: String): String {
        var text = html
        text = text.replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("<head[^>]*>[\\s\\S]*?</head>", RegexOption.IGNORE_CASE), " ")
        text = text.replace(Regex("<[^>]+>"), " ")
        text = text.replace("&nbsp;", " ")
        text = text.replace("&amp;", "&")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        text = text.replace("&quot;", "\"")
        text = text.replace("&#39;", "'")
        text = text.replace("&apos;", "'")
        text = text.replace("&mdash;", "-")
        text = text.replace("&ndash;", "-")
        text = text.replace("&hellip;", "...")
        text = text.replace(Regex("&#(\\d+);")) { m ->
            m.groupValues[1].toIntOrNull()?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: " "
        }
        text = text.replace(Regex("&#x([0-9a-fA-F]+);")) { m ->
            m.groupValues[1].toIntOrNull(16)?.let { code -> runCatching { String(Character.toChars(code)) }.getOrNull() } ?: " "
        }
        text = text.replace(Regex("&[a-zA-Z]+;"), " ")
        text = text.replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF\\u00AD\\u034F\\u115F\\u1160\\u17B4\\u17B5\\u180E\\u3164\\uFFA0]"), "")
        text = text.replace(Regex("\\s+"), " ")
        return text.trim()
    }

    private fun clean_preview(body_text: String, body_html: String?): String {
        if (body_html != null && !body_html.contains("-----BEGIN PGP")) {
            val from_html = strip_html(body_html)
            if (from_html.length > 4) return from_html.take(140)
        }
        if (body_text.contains("-----BEGIN PGP")) return ""
        return strip_html(body_text).take(140)
    }

    private fun try_pgp_decrypt(ciphertext: String): String? {
        val identity_key = session_key_store.get_identity_key() ?: return null
        if (!identity_key.contains("-----BEGIN PGP")) return null
        val passphrase = session_key_store.get_passphrase() ?: return null
        return try {
            val chars = String(passphrase, Charsets.UTF_8).toCharArray()
            val result = PgpDecryptor.decrypt(ciphertext, identity_key, chars)
            passphrase.fill(0)
            result
        } catch (_: Throwable) {
            passphrase.fill(0)
            null
        }
    }

    private fun decrypt_pgp_body_fields(envelope: DecryptedEnvelope): DecryptedEnvelope {
        var body_text = envelope.body_text
        var body_html = envelope.body_html

        val ratchet_candidate = when {
            ratchet_decryptor.looks_like_ratchet_envelope(body_text) -> body_text
            body_html != null && ratchet_decryptor.looks_like_ratchet_envelope(body_html!!) -> body_html!!
            else -> null
        }
        if (ratchet_candidate != null) {
            val our_email = session_key_store.get_user_email()
            val sender_email = envelope.from_email
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.d("AsterRatchet", "envelope detected has_keys=${session_key_store.has_ratchet_keys()}")
            }
            if (!our_email.isNullOrBlank() && sender_email.isNotBlank()) {
                val decrypted = kotlinx.coroutines.runBlocking {
                    ratchet_decryptor.try_decrypt(ratchet_candidate, our_email, sender_email)
                }
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.d("AsterRatchet", "decrypt result: ${if (decrypted == org.astermail.android.mail.ratchet.RATCHET_UNDECRYPTABLE_SENTINEL) "UNDECRYPTABLE" else "ok"}")
                }
                if (decrypted != org.astermail.android.mail.ratchet.RATCHET_UNDECRYPTABLE_SENTINEL) {
                    body_text = decrypted
                    body_html = null
                } else {
                    body_text = ""
                    body_html = null
                }
            }
        }

        if (body_text.trimStart().startsWith("-----BEGIN PGP")) {
            val decrypted = try_pgp_decrypt(body_text)
            if (decrypted != null) body_text = decrypted
        }
        if (body_html != null && body_html.trimStart().startsWith("-----BEGIN PGP")) {
            val decrypted = try_pgp_decrypt(body_html)
            if (decrypted != null) body_html = decrypted
        }

        if (MimeParser.looks_like_mime(body_text)) {
            val parsed = MimeParser.parse(body_text)
            if (parsed.text != null || parsed.html != null) {
                body_text = parsed.text ?: ""
                if (parsed.html != null) body_html = parsed.html
            }
        }
        if (body_html != null && MimeParser.looks_like_mime(body_html)) {
            val parsed = MimeParser.parse(body_html)
            if (parsed.html != null) body_html = parsed.html
            else if (parsed.text != null) body_html = null
        }

        var resolved_subject = envelope.subject
        val bundle = extract_subject_bundle(body_text)
        if (bundle.subject != null) {
            body_text = bundle.body
            if (resolved_subject.isBlank()) {
                resolved_subject = bundle.subject
            }
        }

        return if (
            body_text != envelope.body_text ||
            body_html != envelope.body_html ||
            resolved_subject != envelope.subject
        ) {
            envelope.copy(
                subject = resolved_subject,
                body_text = body_text,
                body_html = body_html,
            )
        } else {
            envelope
        }
    }

    suspend fun send_email(
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        subject: String,
        body_html: String,
        sender_email: String? = null,
        sender_display_name: String? = null,
        thread_token: String? = null,
        expires_at: String? = null,
        expiry_password: String? = null,
        attachments: List<ExternalAttachmentPayload> = emptyList(),
        sender_alias_hash: String? = null,
        suppress_branding: Boolean? = null,
    ): Result<SimpleSendResponse> = runCatching {
        val envelope = build_envelope_json(
            subject = subject,
            body_html = body_html,
            from_email = sender_email.orEmpty(),
            from_name = sender_display_name.orEmpty(),
            to = to,
            cc = cc,
        )
        val (encrypted_envelope, envelope_nonce) = encrypt_envelope(envelope)

        val sent_folder_token = try {
            val token = labels_api.list_labels(include_counts = false)
                .labels.firstOrNull { it.folder_type == "sent" }?.label_token
            if (token != null) cached_sent_folder_token = token
            token
        } catch (_: Throwable) { cached_sent_folder_token }

        val all_external = to.any { !it.endsWith("@astermail.org") && !it.endsWith("@aster.cx") } ||
            cc.any { !it.endsWith("@astermail.org") && !it.endsWith("@aster.cx") } ||
            bcc.any { !it.endsWith("@astermail.org") && !it.endsWith("@aster.cx") }

        if (all_external) {
            val ephemeral_key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
            val base_nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

            fun derive_nonce(base: ByteArray, xor_byte: Byte): ByteArray {
                val n = base.copyOf()
                n[11] = (n[11].toInt() xor xor_byte.toInt()).toByte()
                return n
            }

            fun encrypt_field(plaintext: String, nonce: ByteArray): String {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(ephemeral_key, "AES"),
                    GCMParameterSpec(128, nonce),
                )
                return android.util.Base64.encodeToString(
                    cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
                    android.util.Base64.NO_WRAP,
                )
            }

            val recipients_json = org.json.JSONObject().apply {
                put("to", org.json.JSONArray(to))
                if (cc.isNotEmpty()) put("cc", org.json.JSONArray(cc))
                if (bcc.isNotEmpty()) put("bcc", org.json.JSONArray(bcc))
            }.toString()

            val encrypted_recipients = encrypt_field(recipients_json, derive_nonce(base_nonce, 0x01))
            val encrypted_subject = encrypt_field(subject, derive_nonce(base_nonce, 0x02))
            val encrypted_body = encrypt_field(body_html, derive_nonce(base_nonce, 0x03))
            val ephemeral_key_b64 = android.util.Base64.encodeToString(ephemeral_key, android.util.Base64.NO_WRAP)
            ephemeral_key.fill(0)
            val result = send_api.send_external(
                ExternalSendRequest(
                    encrypted_recipients = encrypted_recipients,
                    encrypted_subject = encrypted_subject,
                    encrypted_body = encrypted_body,
                    ephemeral_key = ephemeral_key_b64,
                    nonce = android.util.Base64.encodeToString(base_nonce, android.util.Base64.NO_WRAP),
                    encrypted_envelope = encrypted_envelope,
                    envelope_nonce = envelope_nonce,
                    folder_token = sent_folder_token,
                    sender_email = sender_email,
                    sender_display_name = sender_display_name,
                    expires_at = expires_at,
                    expiry_password = expiry_password,
                    acknowledge_server_readable = true,
                    attachments = attachments,
                    sender_alias_hash = sender_alias_hash,
                    suppress_branding = suppress_branding,
                ),
            )
            SimpleSendResponse(
                success = result.success,
                message = result.message,
                mail_item_id = result.mail_item_id,
            )
        } else {
            val from_addr = sender_email ?: session_key_store.get_user_email() ?: ""
            val internal_recipients = (to + cc + bcc).filter {
                it.endsWith("@astermail.org") || it.endsWith("@aster.cx")
            }
            val ratchet_body = if (
                from_addr.isNotBlank() &&
                internal_recipients.isNotEmpty() &&
                session_key_store.has_ratchet_keys()
            ) {
                try {
                    ratchet_encryptor.encrypt_envelope(from_addr, internal_recipients, body_html)
                } catch (t: Throwable) {
                    if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "ratchet encrypt failed; falling back to legacy body", t)
                    null
                }
            } else null

            val final_body = ratchet_body ?: body_html
            val final_subject = if (ratchet_body != null) "" else subject

            send_api.send_simple(
                SimpleSendRequest(
                    to = to,
                    cc = cc,
                    bcc = bcc,
                    subject = final_subject,
                    body = final_body,
                    is_e2e_encrypted = ratchet_body != null,
                    encrypted_envelope = encrypted_envelope,
                    envelope_nonce = envelope_nonce,
                    folder_token = sent_folder_token,
                    sender_email = sender_email,
                    sender_display_name = sender_display_name,
                    thread_token = thread_token,
                    expires_at = expires_at,
                    sender_alias_hash = sender_alias_hash,
                    suppress_branding = suppress_branding,
                ),
            )
        }
    }

    suspend fun save_draft(
        subject: String,
        body_html: String,
        sender_email: String? = null,
        to: List<String> = emptyList(),
        cc: List<String> = emptyList(),
        existing_draft_id: String? = null,
    ): Result<String> = runCatching {
        val envelope = build_envelope_json(
            subject = subject,
            body_html = body_html,
            from_email = sender_email.orEmpty(),
            from_name = "",
            to = to,
            cc = cc,
        )
        val (encrypted_envelope, envelope_nonce) = encrypt_envelope(envelope)
        val response = mail_api.create_message(
            CreateMailItemRequest(
                item_type = "draft",
                encrypted_envelope = encrypted_envelope,
                envelope_nonce = envelope_nonce,
            ),
        )
        val new_id = response.id ?: throw IllegalStateException("no draft id returned")
        if (!existing_draft_id.isNullOrBlank() && existing_draft_id != new_id) {
            runCatching { mail_api.delete_draft(existing_draft_id) }
        }
        new_id
    }

    suspend fun schedule_email(
        subject: String,
        body_html: String,
        sender_email: String? = null,
        sender_display_name: String? = null,
        to: List<String>,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        scheduled_at: String,
    ): Result<String> = runCatching {
        val is_external = to.any { !it.endsWith("@astermail.org") && !it.endsWith("@aster.cx") } ||
            cc.any { !it.endsWith("@astermail.org") && !it.endsWith("@aster.cx") } ||
            bcc.any { !it.endsWith("@astermail.org") && !it.endsWith("@aster.cx") }

        val ephemeral_key = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val base_nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

        fun derive_nonce(base: ByteArray, xor_byte: Byte): ByteArray {
            val n = base.copyOf()
            n[11] = (n[11].toInt() xor xor_byte.toInt()).toByte()
            return n
        }

        fun encrypt_field(plaintext: String, nonce: ByteArray): String {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                javax.crypto.Cipher.ENCRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(ephemeral_key, "AES"),
                javax.crypto.spec.GCMParameterSpec(128, nonce),
            )
            return android.util.Base64.encodeToString(
                cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)),
                android.util.Base64.NO_WRAP,
            )
        }

        val envelope_nonce_bytes = derive_nonce(base_nonce, 0x01)
        val recipients_nonce_bytes = derive_nonce(base_nonce, 0x02)

        val envelope_obj = org.json.JSONObject().apply {
            put("subject", subject)
            put("body_text", "")
            put("body_html", body_html)
            put("from", org.json.JSONObject().apply {
                put("name", sender_display_name.orEmpty())
                put("email", sender_email.orEmpty())
            })
            put("to", org.json.JSONArray().apply {
                to.forEach { put(org.json.JSONObject().apply { put("name", ""); put("email", it) }) }
            })
            put("cc", org.json.JSONArray().apply {
                cc.forEach { put(org.json.JSONObject().apply { put("name", ""); put("email", it) }) }
            })
            put("bcc", org.json.JSONArray().apply {
                bcc.forEach { put(org.json.JSONObject().apply { put("name", ""); put("email", it) }) }
            })
            put("sent_at", scheduled_at)
        }

        val encrypted_envelope = encrypt_field(envelope_obj.toString(), envelope_nonce_bytes)
        val recipients_json = org.json.JSONArray().apply {
            (to + cc + bcc).forEach { put(it) }
        }.toString()
        val encrypted_recipients = encrypt_field(recipients_json, recipients_nonce_bytes)

        val ephemeral_key_b64 = android.util.Base64.encodeToString(ephemeral_key, android.util.Base64.NO_WRAP)
        ephemeral_key.fill(0)

        val sent_folder_token = try {
            val token = labels_api.list_labels(include_counts = false)
                .labels.firstOrNull { it.folder_type == "sent" }?.label_token
            if (token != null) cached_sent_folder_token = token
            token
        } catch (_: Throwable) { cached_sent_folder_token }

        val response = scheduled_api.create_scheduled(
            CreateScheduledRequest(
                encrypted_envelope = encrypted_envelope,
                envelope_nonce = android.util.Base64.encodeToString(envelope_nonce_bytes, android.util.Base64.NO_WRAP),
                encrypted_recipients = encrypted_recipients,
                recipients_nonce = android.util.Base64.encodeToString(recipients_nonce_bytes, android.util.Base64.NO_WRAP),
                recipient_count = (to + cc + bcc).size,
                scheduled_at = scheduled_at,
                folder_token = sent_folder_token,
                is_external = is_external,
                ephemeral_key = ephemeral_key_b64,
                base_nonce = android.util.Base64.encodeToString(base_nonce, android.util.Base64.NO_WRAP),
            ),
        )
        response.id ?: throw IllegalStateException("no scheduled item id returned")
    }

    private fun build_envelope_json(
        subject: String,
        body_html: String,
        from_email: String,
        from_name: String,
        to: List<String>,
        cc: List<String>,
    ): String {
        val obj = org.json.JSONObject()
        obj.put("subject", subject)
        obj.put("body_text", "")
        obj.put("body_html", body_html)
        obj.put("from", org.json.JSONObject().apply {
            put("name", from_name)
            put("email", from_email)
        })
        obj.put("to", org.json.JSONArray().apply {
            to.forEach { put(org.json.JSONObject().apply { put("name", ""); put("email", it) }) }
        })
        obj.put("cc", org.json.JSONArray().apply {
            cc.forEach { put(org.json.JSONObject().apply { put("name", ""); put("email", it) }) }
        })
        obj.put("sent_at", java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US,
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(java.util.Date()))
        return obj.toString()
    }

    private fun encrypt_envelope(json: String): Pair<String, String> {
        val passphrase = session_key_store.get_passphrase()
        if (passphrase != null) {
            try {
                val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
                val chars = String(passphrase, Charsets.UTF_8).toCharArray()
                val spec = PBEKeySpec(chars, salt, PBKDF2_ITERATIONS, 256)
                chars.fill(0.toChar())
                val key_bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).encoded
                spec.clearPassword()
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.ENCRYPT_MODE,
                    SecretKeySpec(key_bytes, "AES"),
                    GCMParameterSpec(128, nonce),
                )
                val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
                key_bytes.fill(0)
                val combined = salt + nonce + ciphertext
                return Pair(
                    android.util.Base64.encodeToString(combined, android.util.Base64.NO_WRAP),
                    android.util.Base64.encodeToString(byteArrayOf(1), android.util.Base64.NO_WRAP),
                )
            } finally {
                passphrase.fill(0)
            }
        }
        val identity_key = session_key_store.get_identity_key()
            ?: throw IllegalStateException("no key material available")
        val material = (identity_key + "astermail-envelope-v1").toByteArray(Charsets.UTF_8)
        val key = MessageDigest.getInstance("SHA-256").digest(material)
        try {
            val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, nonce),
            )
            val ciphertext = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
            return Pair(
                android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
                android.util.Base64.encodeToString(nonce, android.util.Base64.NO_WRAP),
            )
        } finally {
            material.fill(0)
            key.fill(0)
        }
    }

    companion object {
        private const val PBKDF2_ITERATIONS = 310000
        private val ENVELOPE_VERSIONS = listOf(
            "astermail-envelope-v1",
            "astermail-import-v1",
            "astermail-draft-v2",
        )
    }
}

data class InboxPage(
    val items: List<InboxItem>,
    val has_more: Boolean,
    val next_cursor: String?,
    val total: Int,
)
