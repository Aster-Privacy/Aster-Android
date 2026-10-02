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

private fun timestamp_millis(value: String): Long? =
    runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { java.time.Instant.parse(value).toEpochMilli() }
        .getOrNull()

internal fun ThreadMessageDecrypted.has_readable_body(): Boolean =
    !is_undecryptable && !is_body_pending &&
        (body_text.isNotBlank() || !body_html.isNullOrBlank())

internal fun include_opened_message(
    messages: List<ThreadMessageDecrypted>,
    opened: ThreadMessageDecrypted,
): List<ThreadMessageDecrypted> {
    if (messages.isEmpty()) return listOf(opened)
    val index = messages.indexOfFirst { it.id == opened.id }
    if (index == -1) {
        val opened_at = timestamp_millis(opened.timestamp)
            ?: return messages + opened
        val insert_at = messages.indexOfFirst { message ->
            timestamp_millis(message.timestamp)?.let { it > opened_at } == true
        }
        return if (insert_at == -1) {
            messages + opened
        } else {
            messages.toMutableList().apply { add(insert_at, opened) }
        }
    }
    val existing = messages[index]
    if (existing.has_readable_body() || !opened.has_readable_body()) return messages
    return messages.toMutableList().also {
        it[index] = existing.copy(
            body_text = opened.body_text,
            body_html = opened.body_html,
            is_undecryptable = false,
            is_body_pending = false,
        )
    }
}
