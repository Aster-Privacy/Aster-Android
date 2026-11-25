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

package org.astermail.android.ui.mail

import org.astermail.android.mail.InboundAttachmentEntry
import org.astermail.android.mail.ThreadMessageDecrypted

internal const val pending_attachment_prefix = "pending:"
internal const val pending_attachment_max_slots = 50
private const val pending_attachment_fallback_type = "application/octet-stream"

internal fun MessageAttachment.is_pending_slot(): Boolean = id.startsWith(pending_attachment_prefix)

internal fun expected_attachment_count(msg: ThreadMessageDecrypted): Int {
    val declared = msg.raw_item.attachment_count ?: 0
    return when {
        declared > 0 -> declared
        msg.has_attachments || msg.raw_item.has_attachments == true -> 1
        else -> 0
    }
}

internal fun pending_attachment_slots(
    message_id: String,
    expected_count: Int,
    hints: List<InboundAttachmentEntry>,
): List<MessageAttachment> {
    val count = maxOf(expected_count, hints.size).coerceIn(0, pending_attachment_max_slots)
    if (count == 0) return emptyList()
    return List(count) { index ->
        val hint = hints.getOrNull(index)
        MessageAttachment(
            id = "$pending_attachment_prefix$message_id:$index",
            filename = hint?.filename?.trim().orEmpty(),
            content_type = hint?.content_type?.takeIf { it.isNotBlank() } ?: pending_attachment_fallback_type,
            size_bytes = hint?.size?.coerceAtLeast(0L) ?: 0L,
        )
    }
}

internal fun attachments_awaiting_ids(
    messages: List<ThreadMessageDecrypted>,
    loaded: Map<String, List<MessageAttachment>>,
    loading: Set<String>,
    settled: Set<String>,
    thread_loading: Boolean,
    failed: Boolean,
): Set<String> {
    if (failed) return emptySet()
    return messages
        .asSequence()
        .filter { loaded[it.id].isNullOrEmpty() }
        .filter { msg ->
            msg.id in loading ||
                (thread_loading && msg.id !in settled && expected_attachment_count(msg) > 0)
        }
        .map { it.id }
        .toSet()
}
