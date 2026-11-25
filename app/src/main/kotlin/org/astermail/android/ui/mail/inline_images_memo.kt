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

object inline_images_memo {
    private const val capacity = 16
    private const val max_total_chars = 24_000_000L

    private class memo_entry(val html_hash: Int, val html_length: Int, val images: Map<String, String>, val chars: Long)

    private val lock = Any()
    private var total_chars = 0L
    private val entries = LinkedHashMap<String, memo_entry>(capacity, 0.75f, true)

    fun get(message_id: String, html: String): Map<String, String>? {
        if (html.isEmpty()) return null
        val entry = synchronized(lock) { entries[message_id] } ?: return null
        return if (entry.html_length == html.length && entry.html_hash == html.hashCode()) entry.images else null
    }

    fun put(message_id: String, html: String, images: Map<String, String>) {
        if (html.isEmpty() || images.isEmpty()) return
        val chars = images.values.sumOf { it.length.toLong() }
        if (chars > max_total_chars) return
        synchronized(lock) {
            entries.remove(message_id)?.let { total_chars -= it.chars }
            entries[message_id] = memo_entry(html.hashCode(), html.length, images, chars)
            total_chars += chars
            val iterator = entries.entries.iterator()
            while ((entries.size > capacity || total_chars > max_total_chars) && iterator.hasNext()) {
                val eldest = iterator.next()
                if (eldest.key == message_id) continue
                total_chars -= eldest.value.chars
                iterator.remove()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            total_chars = 0L
        }
    }
}
