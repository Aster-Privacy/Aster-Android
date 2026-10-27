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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.astermail.android.api.mail.ThreadMessageItem

const val OPTIMISTIC_SEND_ID_PREFIX = "pending_send_"
const val OPTIMISTIC_SEND_STATUS = "sending"
const val OPTIMISTIC_SENT_STATUS = "sent"
const val OPTIMISTIC_SEND_CLOCK_SKEW_MS = 15_000L
const val OPTIMISTIC_SEND_LINGER_MS = 30_000L

fun is_optimistic_send_id(id: String): Boolean = id.startsWith(OPTIMISTIC_SEND_ID_PREFIX)

fun optimistic_send_message_id(pending_id: String): String = OPTIMISTIC_SEND_ID_PREFIX + pending_id

data class OptimisticSend(
    val pending_id: String,
    val account_id: String?,
    val thread_token: String?,
    val reply_to_id: String?,
    val started_at_ms: Long,
    val message: ThreadMessageDecrypted,
    val committed: Boolean = false,
    val mail_item_id: String? = null,
    val send_at_ms: Long = started_at_ms,
)

class OptimisticSendStore {
    private val _sends = MutableStateFlow<List<OptimisticSend>>(emptyList())
    val sends: StateFlow<List<OptimisticSend>> = _sends.asStateFlow()

    fun add(send: OptimisticSend) {
        _sends.update { list -> list.filterNot { it.pending_id == send.pending_id } + send }
    }

    fun get(pending_id: String): OptimisticSend? = _sends.value.firstOrNull { it.pending_id == pending_id }

    fun remove(pending_id: String): Boolean {
        var removed = false
        _sends.update { list ->
            val next = list.filterNot { it.pending_id == pending_id }
            removed = next.size != list.size
            next
        }
        return removed
    }

    fun mark_committed(pending_id: String, mail_item_id: String?): Boolean {
        var marked = false
        _sends.update { list ->
            marked = false
            list.map { send ->
                if (send.pending_id == pending_id && !send.committed) {
                    marked = true
                    send.copy(committed = true, mail_item_id = mail_item_id?.takeIf { it.isNotBlank() })
                } else {
                    send
                }
            }
        }
        return marked
    }

    fun clear() {
        _sends.value = emptyList()
    }
}

fun build_optimistic_send_message(
    pending_id: String,
    started_at_ms: Long,
    sender_email: String?,
    sender_display_name: String?,
    to: List<String>,
    cc: List<String>,
    bcc: List<String>,
    subject: String,
    body_html: String,
    has_attachments: Boolean,
): ThreadMessageDecrypted {
    val id = optimistic_send_message_id(pending_id)
    val ts = java.time.Instant.ofEpochMilli(started_at_ms).toString()
    val raw = ThreadMessageItem(
        id = id,
        item_type = "sent",
        send_status = OPTIMISTIC_SEND_STATUS,
        message_ts = ts,
        created_at = ts,
        is_read = true,
        has_attachments = has_attachments,
    )
    val recipients = to.filter { it.isNotBlank() }
    return ThreadMessageDecrypted(
        id = id,
        sender_name = sender_display_name.orEmpty(),
        sender_email = sender_email.orEmpty(),
        to_label = recipients.joinToString(", "),
        timestamp = ts,
        body_text = optimistic_plain_text(body_html),
        body_html = body_html,
        is_encrypted = true,
        is_read = true,
        raw_item = raw,
        to_addresses = recipients,
        cc_addresses = cc.filter { it.isNotBlank() },
        bcc_addresses = bcc.filter { it.isNotBlank() },
        has_attachments = has_attachments,
        subject = subject,
    )
}

private val optimistic_break_re = Regex("(?i)<br\\s*/?>|</p>|</div>")
private val optimistic_tag_re = Regex("<[^>]+>")
private val optimistic_space_re = Regex("[ \\t\\x0B\\f\\r]+")

private fun optimistic_plain_text(html: String): String {
    val broken = optimistic_break_re.replace(html, "\n")
    val stripped = optimistic_tag_re.replace(broken, "")
    return optimistic_space_re.replace(stripped, " ")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .trim()
}

private fun iso_to_epoch_ms(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
}

fun optimistic_send_has_server_copy(send: OptimisticSend, server_messages: List<ThreadMessageDecrypted>): Boolean {
    if (!send.committed) return false
    val real = server_messages.filterNot { is_optimistic_send_id(it.id) }
    return server_copy_of(send, real) != null
}

private fun server_copy_of(send: OptimisticSend, real: List<ThreadMessageDecrypted>): ThreadMessageDecrypted? {
    val mail_item_id = send.mail_item_id
    if (mail_item_id != null) real.firstOrNull { it.id == mail_item_id }?.let { return it }
    val floor = send.started_at_ms - OPTIMISTIC_SEND_CLOCK_SKEW_MS
    return real.lastOrNull { message ->
        message.raw_item.item_type == "sent" &&
            (iso_to_epoch_ms(message.raw_item.created_at ?: message.timestamp) ?: Long.MIN_VALUE) >= floor
    }
}

fun optimistic_send_aliases(
    sends: List<OptimisticSend>,
    account_id: String?,
    server_messages: List<ThreadMessageDecrypted>,
): Map<String, String> {
    if (sends.isEmpty()) return emptyMap()
    val real = server_messages.filterNot { is_optimistic_send_id(it.id) }
    val aliases = HashMap<String, String>()
    sends
        .filter { it.committed && it.account_id == account_id }
        .sortedBy { it.started_at_ms }
        .forEach { send ->
            val copy = server_copy_of(send, real.filter { it.id !in aliases }) ?: return@forEach
            aliases[copy.id] = send.message.id
        }
    return aliases
}

fun optimistic_sends_for_thread(
    sends: List<OptimisticSend>,
    account_id: String?,
    thread_token: String?,
    item_id: String?,
    server_messages: List<ThreadMessageDecrypted>,
): List<ThreadMessageDecrypted> {
    if (sends.isEmpty() || item_id.isNullOrBlank()) return emptyList()
    val message_ids = server_messages.map { it.id }.toSet() + item_id
    val token = thread_token?.takeIf { it.isNotBlank() }
    return sends
        .filter { send -> send.account_id == account_id }
        .filter { send ->
            (token != null && send.thread_token == token) ||
                (!send.reply_to_id.isNullOrBlank() && send.reply_to_id in message_ids)
        }
        .filterNot { send -> optimistic_send_has_server_copy(send, server_messages) }
        .sortedBy { it.started_at_ms }
        .map { send ->
            send.message.copy(
                pending_send_at_ms = send.send_at_ms,
                raw_item = send.message.raw_item.copy(
                    send_status = if (send.committed) OPTIMISTIC_SENT_STATUS else OPTIMISTIC_SEND_STATUS,
                ),
            )
        }
}
