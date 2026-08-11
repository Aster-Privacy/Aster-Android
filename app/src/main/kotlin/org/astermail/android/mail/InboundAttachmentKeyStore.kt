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

data class InboundAttachmentEntry(
    val key: String,
    val filename: String? = null,
    val content_type: String? = null,
    val content_id: String? = null,
    val size: Long? = null,
)

object InboundAttachmentKeyStore {

    private const val MAX_ENTRIES = 4096

    private val keys = BoundedKeyCache(MAX_ENTRIES)

    private val metas = object : LinkedHashMap<String, InboundAttachmentEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, InboundAttachmentEntry>?,
        ): Boolean = size > MAX_ENTRIES
    }

    private val unreadable = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Boolean>?,
        ): Boolean = size > MAX_ENTRIES
    }

    private val lock = Any()

    fun register(mail_item_id: String?, seq_num: Int?, key: String?) {
        register(mail_item_id, seq_num, key, null, null, null, null)
    }

    fun register(
        mail_item_id: String?,
        seq_num: Int?,
        key: String?,
        filename: String?,
        content_type: String?,
        content_id: String?,
        size: Long?,
    ) {
        if (mail_item_id.isNullOrBlank() || seq_num == null || key.isNullOrBlank()) return
        val id = entry_key(mail_item_id, seq_num)
        synchronized(lock) {
            keys.put(id, key.toByteArray(Charsets.UTF_8))
            unreadable.remove(id)
            if (filename == null && content_type == null && content_id == null && size == null) {
                metas.remove(id)
            } else {
                metas[id] = InboundAttachmentEntry(
                    key = key,
                    filename = filename,
                    content_type = content_type,
                    content_id = content_id,
                    size = size,
                )
            }
        }
    }

    fun register_from_envelope_json(mail_item_id: String?, envelope_json: String?) {
        if (mail_item_id.isNullOrBlank() || envelope_json.isNullOrBlank()) return
        runCatching {
            val root = org.json.JSONObject(envelope_json)
            val entries = root.optJSONArray("attachment_keys") ?: return
            for (index in 0 until entries.length()) {
                val entry = entries.optJSONObject(index) ?: continue
                if (!entry.has("seq")) continue
                register(
                    mail_item_id = mail_item_id,
                    seq_num = entry.optInt("seq", -1).takeIf { it >= 0 },
                    key = entry.optString("key", "").ifBlank { null },
                    filename = optional_string(entry, "filename"),
                    content_type = optional_string(entry, "content_type"),
                    content_id = optional_string(entry, "content_id"),
                    size = if (entry.has("size") && !entry.isNull("size")) {
                        entry.optLong("size", -1L).takeIf { it >= 0L }
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun key(mail_item_id: String?, seq_num: Int?): String? {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return null
        return synchronized(lock) {
            keys.get(entry_key(mail_item_id, seq_num))?.let { String(it, Charsets.UTF_8) }
        }
    }

    fun entry(mail_item_id: String?, seq_num: Int?): InboundAttachmentEntry? {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return null
        val id = entry_key(mail_item_id, seq_num)
        return synchronized(lock) {
            val key = keys.get(id)?.let { String(it, Charsets.UTF_8) } ?: return@synchronized null
            metas[id]?.copy(key = key) ?: InboundAttachmentEntry(key = key)
        }
    }

    fun mark_unreadable(mail_item_id: String?, seq_num: Int?) {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return
        synchronized(lock) { unreadable[entry_key(mail_item_id, seq_num)] = true }
    }

    fun is_unreadable(mail_item_id: String?, seq_num: Int?): Boolean {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return false
        return synchronized(lock) { unreadable.containsKey(entry_key(mail_item_id, seq_num)) }
    }

    fun clear() {
        synchronized(lock) {
            keys.clear()
            metas.clear()
            unreadable.clear()
        }
    }

    fun size(): Int = synchronized(lock) { keys.size() }

    private fun optional_string(entry: org.json.JSONObject, name: String): String? {
        if (!entry.has(name) || entry.isNull(name)) return null
        return entry.optString(name, "").ifBlank { null }
    }

    private fun entry_key(mail_item_id: String, seq_num: Int): String = "$mail_item_id:$seq_num"
}
