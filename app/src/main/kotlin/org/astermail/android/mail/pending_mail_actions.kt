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

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.astermail.android.api.ApiError
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.storage.actions.PendingMailActionEntity

enum class PendingActionKind {
    archive,
    unarchive,
    trash,
    restore_trash,
    mark_spam,
    unmark_spam,
    mark_read,
    mark_unread,
    mark_thread_read,
    star,
    unstar,
    star_scope,
    pin,
    unpin,
    snooze,
    unsnooze,
    add_label,
    remove_label,
    add_tag,
    remove_tag,
    move_to_folder,
    scope_action,
    delete_permanent,
    delete_draft,
    empty_trash,
    empty_spam,
    report_spam_senders,
    remove_spam_senders,
    save_draft,
}

@Serializable
data class PendingActionPayload(
    val ids: List<String> = emptyList(),
    val threads: List<String> = emptyList(),
    val covered: List<String> = emptyList(),
    val token: String? = null,
    val from_token: String? = null,
    val folder: String? = null,
    val value: String? = null,
    val key: String? = null,
    val content: String? = null,
    val nonce: String? = null,
    val hash: String? = null,
    val reply_to: String? = null,
    val count: Int = 0,
)

data class PendingMailAction(
    val id: Long,
    val account_id: String,
    val kind: PendingActionKind,
    val payload: PendingActionPayload,
    val created_at_ms: Long,
    val attempts: Int,
) {
    val id_set: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { payload.ids.toHashSet() }
    val thread_set: Set<String> by lazy(LazyThreadSafetyMode.PUBLICATION) { payload.threads.toHashSet() }
}

enum class PendingDrainOutcome { Done, Retry }

internal val pending_action_json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = false
}

internal const val PENDING_ACTION_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

internal const val PENDING_ACTION_MAX_SERVER_ATTEMPTS = 8

const val PENDING_PATCH_MODE = "patch"

const val QUEUED_BATCH_ID = "queued"

const val PENDING_UNKNOWN_COUNT = -1

const val LOCAL_DRAFT_PREFIX = "local-"

internal const val PENDING_DRAFT_MAX_CHARS = 1_000_000

fun is_local_draft_id(id: String?): Boolean = id?.startsWith(LOCAL_DRAFT_PREFIX) == true

internal fun queued_scope_response(count: Int): org.astermail.android.api.mail.BulkScopeResponse =
    org.astermail.android.api.mail.BulkScopeResponse(batch_id = QUEUED_BATCH_ID, affected_count = count)

internal fun encode_pending_payload(payload: PendingActionPayload): String =
    pending_action_json.encodeToString(PendingActionPayload.serializer(), payload)

internal fun PendingMailActionEntity.to_pending_action(): PendingMailAction? {
    val kind = runCatching { PendingActionKind.valueOf(kind) }.getOrNull() ?: return null
    val payload = runCatching {
        pending_action_json.decodeFromString(PendingActionPayload.serializer(), payload)
    }.getOrNull() ?: return null
    return PendingMailAction(id, account_id, kind, payload, created_at_ms, attempts)
}

internal fun pending_action_expired(action: PendingMailAction, now_ms: Long): Boolean =
    now_ms - action.created_at_ms > PENDING_ACTION_MAX_AGE_MS

fun is_transient_failure(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 8) {
        when (current) {
            is java.io.IOException -> return true
            is kotlinx.coroutines.TimeoutCancellationException -> return true
            is io.ktor.client.plugins.HttpRequestTimeoutException -> return true
            is io.ktor.client.network.sockets.ConnectTimeoutException -> return true
            is ApiError.NetworkError -> return true
            is ApiError.ServerError -> return current.code in 500..599
            is ApiError.RateLimited -> return true
            is ApiError -> return false
        }
        if (current.cause === current) break
        current = current.cause
        depth++
    }
    return false
}

fun is_connectivity_failure(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 8) {
        when (current) {
            is java.net.UnknownHostException -> return true
            is java.net.ConnectException -> return true
            is java.net.NoRouteToHostException -> return true
            is io.ktor.client.network.sockets.ConnectTimeoutException -> return true
            is ApiError.NetworkError -> return true
            is ApiError -> return false
        }
        if (current.cause === current) break
        current = current.cause
        depth++
    }
    return false
}

fun is_server_side_failure(error: Throwable?): Boolean {
    var current = error
    var depth = 0
    while (current != null && depth < 8) {
        if (current is ApiError.ServerError || current is ApiError.RateLimited) return true
        if (current.cause === current) break
        current = current.cause
        depth++
    }
    return false
}

internal fun pending_action_exhausted(action: PendingMailAction, error: Throwable?): Boolean =
    is_server_side_failure(error) && action.attempts + 1 >= PENDING_ACTION_MAX_SERVER_ATTEMPTS

private class OverlayState(var item: InboxItem) {
    var gone = false
    var snoozed: Boolean? = null
}

private fun InboxItem.with_flags(
    archived: Boolean? = null,
    trashed: Boolean? = null,
    spam: Boolean? = null,
): InboxItem = copy(
    is_archived = archived ?: is_archived,
    is_trashed = trashed ?: is_trashed,
    is_spam = spam ?: is_spam,
)

private fun InboxItem.with_pin(pinned: Boolean): InboxItem {
    if ((raw_item.metadata?.is_pinned ?: false) == pinned) return this
    val meta = (raw_item.metadata ?: MailItemMetadata()).copy(is_pinned = pinned)
    return copy(raw_item = raw_item.copy(metadata = meta))
}

private fun InboxItem.with_tag(token: String, applied: Boolean): InboxItem {
    val next = if (applied) {
        if (tag_tokens.contains(token)) return this else tag_tokens + token
    } else {
        if (!tag_tokens.contains(token)) return this else tag_tokens - token
    }
    return copy(tag_tokens = next, raw_item = raw_item.copy(tag_tokens = next))
}

private fun InboxItem.with_label(token: String, applied: Boolean): InboxItem = when {
    applied && !labels.contains(token) -> copy(labels = labels + token)
    !applied && labels.contains(token) -> copy(labels = labels - token)
    else -> this
}

private fun apply_bulk_effect(state: OverlayState, action: String) {
    val item = state.item
    state.item = when (action) {
        "archive" -> item.with_flags(archived = true, trashed = false, spam = false)
        "unarchive" -> item.with_flags(archived = false)
        "trash" -> item.with_flags(trashed = true, archived = false)
        "restore_trash" -> item.with_flags(trashed = false, spam = false)
        "mark_spam" -> item.with_flags(spam = true, trashed = false, archived = false)
        "unmark_spam" -> item.with_flags(spam = false, trashed = false)
        "mark_read" -> item.copy(is_read = true)
        "mark_unread" -> item.copy(is_read = false)
        else -> item
    }
}

private fun targets(action: PendingMailAction, item: InboxItem): Boolean = when (action.kind) {
    PendingActionKind.star_scope,
    PendingActionKind.scope_action -> action.payload.folder?.let { folder_matches_item(it, item) } == true
    PendingActionKind.empty_trash -> item.is_trashed
    PendingActionKind.empty_spam -> item.is_spam
    PendingActionKind.mark_thread_read -> item.thread_token != null && item.thread_token in action.thread_set
    PendingActionKind.trash -> item.id in action.id_set ||
        (item.thread_token != null && item.thread_token in action.thread_set)
    PendingActionKind.report_spam_senders,
    PendingActionKind.remove_spam_senders,
    PendingActionKind.save_draft -> false
    else -> item.id in action.id_set
}

private fun apply_action(state: OverlayState, action: PendingMailAction) {
    if (!targets(action, state.item)) return
    val item = state.item
    val payload = action.payload
    when (action.kind) {
        PendingActionKind.archive,
        PendingActionKind.unarchive,
        PendingActionKind.trash,
        PendingActionKind.restore_trash,
        PendingActionKind.mark_spam,
        PendingActionKind.unmark_spam,
        PendingActionKind.mark_read,
        PendingActionKind.mark_unread -> apply_bulk_effect(state, action.kind.name)
        PendingActionKind.mark_thread_read -> state.item = item.copy(is_read = true)
        PendingActionKind.scope_action -> apply_bulk_effect(state, payload.value.orEmpty())
        PendingActionKind.star -> state.item = item.copy(is_starred = true)
        PendingActionKind.unstar -> state.item = item.copy(is_starred = false)
        PendingActionKind.star_scope -> state.item = item.copy(is_starred = payload.value == "true")
        PendingActionKind.pin -> state.item = item.with_pin(true)
        PendingActionKind.unpin -> state.item = item.with_pin(false)
        PendingActionKind.snooze -> state.snoozed = true
        PendingActionKind.unsnooze -> state.snoozed = false
        PendingActionKind.add_label -> payload.token?.let { state.item = item.with_label(it, true) }
        PendingActionKind.remove_label -> payload.token?.let { state.item = item.with_label(it, false) }
        PendingActionKind.add_tag -> payload.token?.let { state.item = item.with_tag(it, true) }
        PendingActionKind.remove_tag -> payload.token?.let { state.item = item.with_tag(it, false) }
        PendingActionKind.move_to_folder -> {
            var next = item
            payload.token?.let { next = next.with_label(it, true) }
            payload.from_token?.takeIf { it != payload.token }?.let { next = next.with_label(it, false) }
            state.item = next
        }
        PendingActionKind.delete_permanent,
        PendingActionKind.delete_draft,
        PendingActionKind.empty_trash,
        PendingActionKind.empty_spam -> state.gone = true
        PendingActionKind.report_spam_senders,
        PendingActionKind.remove_spam_senders,
        PendingActionKind.save_draft -> Unit
    }
}

fun apply_pending_actions(
    folder: String,
    items: List<InboxItem>,
    actions: List<PendingMailAction>,
): List<InboxItem> {
    if (actions.isEmpty() || items.isEmpty()) return items
    val out = ArrayList<InboxItem>(items.size)
    items.forEach { original ->
        val state = OverlayState(original)
        actions.forEach { apply_action(state, it) }
        if (state.gone) return@forEach
        val patched = state.item
        if (state.snoozed == true && folder != "snoozed" && folder_matches_item(folder, original)) return@forEach
        if (state.snoozed == false && folder == "snoozed") return@forEach
        if (patched !== original &&
            folder_matches_item(folder, original) &&
            !folder_matches_item(folder, patched)
        ) {
            return@forEach
        }
        out.add(patched)
    }
    return out
}
