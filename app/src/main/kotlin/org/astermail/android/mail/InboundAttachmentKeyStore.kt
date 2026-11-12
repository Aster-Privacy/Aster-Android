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
    val sha256: String? = null,
    val is_inline: Boolean = false,
    val sealed: Boolean = false,
)

object InboundAttachmentKeyStore {

    private const val MAX_ENTRIES = 4096
    private const val MAX_SEALED_ITEMS = 1024
    private const val UNREADABLE_TTL_MS = 60_000L

    private val keys = BoundedKeyCache(MAX_ENTRIES)

    private val sealed_items = object : LinkedHashMap<String, Map<Int, InboundAttachmentEntry>>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Map<Int, InboundAttachmentEntry>>?,
        ): Boolean = size > MAX_SEALED_ITEMS
    }

    private val metas = object : LinkedHashMap<String, InboundAttachmentEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, InboundAttachmentEntry>?,
        ): Boolean = size > MAX_ENTRIES
    }

    private val unreadable = object : LinkedHashMap<String, Long>(64, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Long>?,
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
            if (sealed_items.containsKey(mail_item_id)) return
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

    fun register_sealed_manifest(mail_item_id: String?, manifest: org.json.JSONArray?): Boolean {
        if (mail_item_id.isNullOrBlank() || manifest == null || manifest.length() == 0) return false
        val entries = LinkedHashMap<Int, InboundAttachmentEntry>()
        for (index in 0 until manifest.length()) {
            val raw = manifest.opt(index) as? org.json.JSONObject ?: return false
            val (seq, entry) = read_sealed_entry(raw) ?: return false
            entries[seq] = entry
        }
        synchronized(lock) {
            sealed_items[mail_item_id] = entries
        }
        return true
    }

    fun is_sealed(mail_item_id: String?): Boolean {
        if (mail_item_id.isNullOrBlank()) return false
        return synchronized(lock) { sealed_items.containsKey(mail_item_id) }
    }

    fun is_row_listed(mail_item_id: String?, seq_num: Int?): Boolean {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return true
        return synchronized(lock) { sealed_items[mail_item_id]?.containsKey(seq_num) ?: true }
    }

    fun key(mail_item_id: String?, seq_num: Int?): String? {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return null
        return synchronized(lock) {
            sealed_items[mail_item_id]?.let { return@synchronized it[seq_num]?.key }
            keys.get(entry_key(mail_item_id, seq_num))?.let { String(it, Charsets.UTF_8) }
        }
    }

    fun entry(mail_item_id: String?, seq_num: Int?): InboundAttachmentEntry? {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return null
        val id = entry_key(mail_item_id, seq_num)
        return synchronized(lock) {
            sealed_items[mail_item_id]?.let { return@synchronized it[seq_num] }
            val key = keys.get(id)?.let { String(it, Charsets.UTF_8) } ?: return@synchronized null
            metas[id]?.copy(key = key) ?: InboundAttachmentEntry(key = key)
        }
    }

    fun mark_unreadable(mail_item_id: String?, seq_num: Int?) {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return
        synchronized(lock) {
            unreadable[entry_key(mail_item_id, seq_num)] = android.os.SystemClock.elapsedRealtime()
        }
    }

    fun is_unreadable(mail_item_id: String?, seq_num: Int?): Boolean {
        if (mail_item_id.isNullOrBlank() || seq_num == null) return false
        val id = entry_key(mail_item_id, seq_num)
        return synchronized(lock) {
            val marked_at = unreadable[id] ?: return@synchronized false
            if (android.os.SystemClock.elapsedRealtime() - marked_at < UNREADABLE_TTL_MS) {
                true
            } else {
                unreadable.remove(id)
                false
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            keys.clear()
            sealed_items.clear()
            metas.clear()
            unreadable.clear()
        }
    }

    fun size(): Int = synchronized(lock) { keys.size() }

    private fun optional_string(entry: org.json.JSONObject, name: String): String? {
        if (!entry.has(name) || entry.isNull(name)) return null
        return entry.optString(name, "").ifBlank { null }
    }

    private fun read_sealed_entry(raw: org.json.JSONObject): Pair<Int, InboundAttachmentEntry>? {
        val seq_value = raw.opt("seq") as? Number ?: return null
        val seq_double = seq_value.toDouble()
        if (seq_double < 0 || seq_double > Int.MAX_VALUE || seq_double != Math.floor(seq_double)) return null
        val key = raw.opt("key") as? String
        if (key.isNullOrEmpty()) return null
        val size = (raw.opt("size") as? Number)?.toDouble()
            ?.takeIf { it >= 0 && it == Math.floor(it) }
            ?.toLong()
        return seq_double.toInt() to InboundAttachmentEntry(
            key = key,
            filename = sealed_text(raw, "filename"),
            content_type = sealed_text(raw, "content_type"),
            content_id = sealed_text(raw, "content_id"),
            size = size,
            sha256 = sealed_text(raw, "sha256")?.trim()?.lowercase(java.util.Locale.ROOT),
            is_inline = raw.opt("is_inline") == true,
            sealed = true,
        )
    }

    private fun sealed_text(raw: org.json.JSONObject, name: String): String? =
        (raw.opt(name) as? String)?.takeIf { it.isNotEmpty() }

    private fun entry_key(mail_item_id: String, seq_num: Int): String = "$mail_item_id:$seq_num"
}
