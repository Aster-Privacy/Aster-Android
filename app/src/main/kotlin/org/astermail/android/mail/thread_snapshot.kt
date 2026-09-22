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
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.crypto.PgpSignatureStatus

val thread_snapshot_json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class thread_snapshot_message(
    val id: String,
    val sender_name: String,
    val sender_email: String,
    val to_label: String,
    val timestamp: String,
    val body_text: String,
    val body_html: String? = null,
    val is_encrypted: Boolean = false,
    val is_read: Boolean = false,
    val raw_item: ThreadMessageItem,
    val to_addresses: List<String> = emptyList(),
    val cc_addresses: List<String> = emptyList(),
    val bcc_addresses: List<String> = emptyList(),
    val has_attachments: Boolean = false,
    val raw_headers: List<List<String>> = emptyList(),
    val subject: String = "",
    val display_sender_name: String? = null,
    val display_sender_email: String? = null,
    val pgp_encrypted: Boolean = false,
    val pgp_signature: String = PgpSignatureStatus.NONE.name,
)

fun thread_snapshot_of(message: ThreadMessageDecrypted): thread_snapshot_message = thread_snapshot_message(
    id = message.id,
    sender_name = message.sender_name,
    sender_email = message.sender_email,
    to_label = message.to_label,
    timestamp = message.timestamp,
    body_text = message.body_text,
    body_html = message.body_html,
    is_encrypted = message.is_encrypted,
    is_read = message.is_read,
    raw_item = message.raw_item,
    to_addresses = message.to_addresses,
    cc_addresses = message.cc_addresses,
    bcc_addresses = message.bcc_addresses,
    has_attachments = message.has_attachments,
    raw_headers = message.raw_headers.map { listOf(it.first, it.second) },
    subject = message.subject,
    display_sender_name = message.display_sender_name,
    display_sender_email = message.display_sender_email,
    pgp_encrypted = message.pgp_encrypted,
    pgp_signature = message.pgp_signature.name,
)

fun thread_message_of(snapshot: thread_snapshot_message): ThreadMessageDecrypted = ThreadMessageDecrypted(
    id = snapshot.id,
    sender_name = snapshot.sender_name,
    sender_email = snapshot.sender_email,
    to_label = snapshot.to_label,
    timestamp = snapshot.timestamp,
    body_text = snapshot.body_text,
    body_html = snapshot.body_html,
    is_encrypted = snapshot.is_encrypted,
    is_read = snapshot.is_read,
    raw_item = snapshot.raw_item,
    to_addresses = snapshot.to_addresses,
    cc_addresses = snapshot.cc_addresses,
    bcc_addresses = snapshot.bcc_addresses,
    has_attachments = snapshot.has_attachments,
    raw_headers = snapshot.raw_headers.mapNotNull {
        if (it.size == 2) it[0] to it[1] else null
    },
    subject = snapshot.subject,
    display_sender_name = snapshot.display_sender_name,
    display_sender_email = snapshot.display_sender_email,
    pgp_encrypted = snapshot.pgp_encrypted,
    pgp_signature = runCatching { PgpSignatureStatus.valueOf(snapshot.pgp_signature) }
        .getOrDefault(PgpSignatureStatus.NONE),
)
