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

import org.astermail.android.storage.search.MessageBodyIndexEntity
import org.astermail.android.ui.search.fold_text
import org.astermail.android.ui.search.parse_item_timestamp

const val body_search_max_chars = 32_768
const val body_search_html_input_limit = 262_144
const val body_search_match_limit = 5_000

private val body_word_split = Regex("""[^\p{L}\p{N}]+""")

fun body_search_plain_text(body_text: String, body_html: String?): String {
    val text_usable = body_text.isNotBlank() && !looks_like_html_body(body_text)
    if (text_usable) return body_text
    val html = body_html?.takeIf { it.isNotBlank() } ?: body_text
    if (html.isBlank()) return ""
    return html_to_plain_text(html.take(body_search_html_input_limit))
}

fun build_body_index_text(
    body_text: String,
    body_html: String?,
    attachment_names: List<String> = emptyList(),
): String {
    val body = fold_text(body_search_plain_text(body_text, body_html).take(body_search_max_chars))
    val names = attachment_names.filter { it.isNotBlank() }.joinToString("\n") { fold_text(it) }
    return if (names.isEmpty()) body else "$body\n$names"
}

fun body_query_words(term: String): List<String> =
    fold_text(term).split(body_word_split).filter { it.isNotEmpty() }

fun body_match_expression(term: String): String? {
    val words = body_query_words(term)
    if (words.isEmpty()) return null
    if (words.size == 1) return "${words.first()}*"
    return words.joinToString(" ", prefix = "\"", postfix = "\"") { "$it*" }
}

fun body_backfill_allowed(on_wifi: Boolean, on_ethernet: Boolean): Boolean = on_wifi || on_ethernet

fun body_index_rows(
    messages: List<ThreadMessageDecrypted>,
    attachment_names: (String) -> List<String>,
    now: Long,
): List<MessageBodyIndexEntity> = messages
    .filterNot { it.is_body_pending || it.is_pgp_placeholder }
    .map { message ->
        val body = if (message.is_undecryptable) {
            ""
        } else {
            build_body_index_text(message.body_text, message.body_html, attachment_names(message.id))
        }
        MessageBodyIndexEntity(
            message_id = message.id,
            body = body,
            received_at = parse_item_timestamp(message.timestamp).takeIf { it > 0L } ?: now,
            indexed_at = now,
        )
    }

fun placeholder_index_rows(ids: Collection<String>, now: Long): List<MessageBodyIndexEntity> =
    ids.map { MessageBodyIndexEntity(message_id = it, body = "", received_at = 0L, indexed_at = now) }
