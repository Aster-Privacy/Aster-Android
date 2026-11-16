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

import org.astermail.android.ui.mail.MessageAttachment

object DecryptedMimeAttachments {

    const val ID_PREFIX = "mime:"
    private const val MAX_STORED_MESSAGES = 50
    private const val MAX_STORED_BYTES = 128L * 1024 * 1024

    private val store = LinkedHashMap<String, List<MimeAttachmentPart>>(16, 0.75f, true)

    fun keep_from_plaintext(mail_item_id: String?, plaintext: String) {
        if (mail_item_id.isNullOrBlank()) return
        val parts = MimeAttachmentExtractor.extract(plaintext)
        if (parts.isEmpty()) return
        put(mail_item_id, parts)
    }

    fun put(mail_item_id: String, parts: List<MimeAttachmentPart>) {
        synchronized(store) {
            store[mail_item_id] = parts
            var total = store.values.sumOf { list -> list.sumOf { it.data.size.toLong() } }
            val iterator = store.entries.iterator()
            while (store.size > 1 && (store.size > MAX_STORED_MESSAGES || total > MAX_STORED_BYTES)) {
                val oldest = iterator.next()
                if (oldest.key == mail_item_id) continue
                total -= oldest.value.sumOf { it.data.size.toLong() }
                iterator.remove()
            }
        }
    }

    fun has(mail_item_id: String): Boolean = synchronized(store) { store.containsKey(mail_item_id) }

    fun attachments_for(mail_item_id: String): List<MessageAttachment> {
        val parts = synchronized(store) { store[mail_item_id] } ?: return emptyList()
        return parts.mapIndexed { index, part ->
            MessageAttachment(
                id = attachment_id(mail_item_id, index),
                filename = part.filename,
                content_type = part.content_type,
                size_bytes = part.data.size.toLong(),
                content_id = part.content_id,
                mail_item_id = mail_item_id,
            )
        }
    }

    fun merge_into(
        mail_item_id: String,
        server: List<MessageAttachment>,
    ): List<MessageAttachment> {
        val local = attachments_for(mail_item_id)
        if (local.isEmpty()) return server
        return server.filterNot { is_local(it.id) } + local
    }

    fun is_local(attachment_id: String): Boolean = attachment_id.startsWith(ID_PREFIX)

    fun bytes_for(attachment_id: String): ByteArray? {
        if (!is_local(attachment_id)) return null
        val mail_item_id = attachment_id.removePrefix(ID_PREFIX).substringBeforeLast(':')
        val index = attachment_id.substringAfterLast(':').toIntOrNull() ?: return null
        val parts = synchronized(store) { store[mail_item_id] } ?: return null
        return parts.getOrNull(index)?.data?.copyOf()
    }

    fun attachment_for(attachment_id: String): MessageAttachment? {
        if (!is_local(attachment_id)) return null
        val mail_item_id = attachment_id.removePrefix(ID_PREFIX).substringBeforeLast(':')
        return attachments_for(mail_item_id).firstOrNull { it.id == attachment_id }
    }

    fun clear() {
        synchronized(store) { store.clear() }
    }

    private fun attachment_id(mail_item_id: String, index: Int): String =
        "$ID_PREFIX$mail_item_id:$index"
}
