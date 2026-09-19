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

import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.storage.search.FolderRowEntity

const val folder_cache_row_limit = 120
const val folder_cache_persist_debounce_ms = 600L

private fun join_tokens(values: List<String>): String? =
    values.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(",")

private fun split_tokens(value: String?): List<String> =
    value?.split(",")?.filter { it.isNotBlank() }.orEmpty()

fun folder_cache_rows(
    folder: String,
    items: List<InboxItem>,
    cached_at: Long,
    limit: Int = folder_cache_row_limit,
): List<FolderRowEntity> {
    val seen = HashSet<String>()
    val rows = ArrayList<FolderRowEntity>(minOf(items.size, limit))
    for (item in items) {
        if (rows.size >= limit) break
        if (item.id.isBlank() || !seen.add(item.id)) continue
        rows.add(
            FolderRowEntity(
                folder = folder,
                id = item.id,
                position = rows.size,
                thread_token = item.thread_token,
                thread_message_count = item.thread_message_count,
                sender_name = item.sender_name,
                sender_email = item.sender_email,
                subject = item.subject,
                preview = item.preview,
                timestamp = item.timestamp,
                is_read = item.is_read,
                is_starred = item.is_starred,
                is_encrypted = item.is_encrypted,
                has_attachments = item.has_attachments,
                attachment_count = item.raw_item.attachment_count ?: 0,
                is_trashed = item.is_trashed,
                is_archived = item.is_archived,
                is_spam = item.is_spam,
                is_pinned = item.raw_item.metadata?.is_pinned ?: (item.raw_item.is_pinned == true),
                labels = join_tokens(item.labels).orEmpty(),
                tag_tokens = join_tokens(item.tag_tokens),
                category = item.category,
                received_on = item.received_on,
                display_sender_name = item.display_sender_name,
                display_sender_email = item.display_sender_email,
                to_addresses = join_tokens(item.to_addresses),
                routing_token = item.routing_token,
                item_type = item.raw_item.item_type,
                is_external = item.raw_item.is_external,
                system_origin = item.raw_item.system_origin,
                has_recipient_key = item.raw_item.has_recipient_key,
                cached_at = cached_at,
            ),
        )
    }
    return rows
}

fun FolderRowEntity.to_inbox_item(): InboxItem = InboxItem(
    id = id,
    thread_token = thread_token,
    thread_message_count = thread_message_count,
    sender_name = sender_name,
    sender_email = sender_email,
    subject = subject,
    preview = preview,
    timestamp = timestamp,
    is_read = is_read,
    is_starred = is_starred,
    is_encrypted = is_encrypted,
    has_attachments = has_attachments,
    is_trashed = is_trashed,
    is_archived = is_archived,
    is_spam = is_spam,
    labels = split_tokens(labels),
    tag_tokens = split_tokens(tag_tokens),
    category = category,
    received_on = received_on,
    display_sender_name = display_sender_name,
    display_sender_email = display_sender_email,
    to_addresses = split_tokens(to_addresses),
    routing_token = routing_token,
    raw_item = MailItem(
        id = id,
        item_type = item_type,
        is_external = is_external,
        system_origin = system_origin,
        has_recipient_key = has_recipient_key,
        thread_token = thread_token,
        routing_token = routing_token,
        thread_message_count = thread_message_count,
        tag_tokens = split_tokens(tag_tokens),
        message_ts = timestamp,
        is_read = is_read,
        is_starred = is_starred,
        is_trashed = is_trashed,
        is_archived = is_archived,
        is_spam = is_spam,
        is_pinned = is_pinned,
        has_attachments = has_attachments,
        attachment_count = attachment_count,
        metadata = MailItemMetadata(
            is_read = is_read,
            is_starred = is_starred,
            is_pinned = is_pinned,
            is_trashed = is_trashed,
            is_archived = is_archived,
            is_spam = is_spam,
            has_attachments = has_attachments,
            attachment_count = attachment_count,
            message_ts = timestamp,
            item_type = item_type,
            category = category,
        ),
    ),
)

fun folder_cache_prune_ids(cached_ids: List<String>, keep_ids: List<String>): List<String> {
    val keep = keep_ids.toHashSet()
    return cached_ids.filterNot { keep.contains(it) }.distinct()
}

fun folder_cache_should_persist(
    items_empty: Boolean,
    is_loading: Boolean,
    initial: Boolean,
    has_error: Boolean,
): Boolean = if (!items_empty) true else !is_loading && !initial && !has_error

fun folder_cache_skeleton_allowed(cache_pending: Boolean, cached_count: Int): Boolean =
    !cache_pending && cached_count == 0

fun folder_cache_apply_overrides(
    items: List<InboxItem>,
    read_override: (String) -> Boolean?,
    star_override: (String) -> Boolean?,
): List<InboxItem> = items.map { item ->
    val read = read_override(item.id)
    val starred = star_override(item.id)
    var updated = item
    if (read != null && read != updated.is_read) updated = updated.copy(is_read = read)
    if (starred != null && starred != updated.is_starred) updated = updated.copy(is_starred = starred)
    updated
}
