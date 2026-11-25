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

object sanitized_html_memo {
    private const val capacity = 8
    private const val max_input_chars = 400_000

    private data class memo_key(val html: String, val options: EmailHtmlSanitizer.SanitizeOptions)

    private val lock = Any()
    private val entries = object : LinkedHashMap<memo_key, String>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<memo_key, String>?): Boolean =
            size > capacity
    }

    @Volatile
    private var last_options: EmailHtmlSanitizer.SanitizeOptions? = null

    fun sanitize(html: String, options: EmailHtmlSanitizer.SanitizeOptions): String {
        last_options = options
        if (html.isBlank() || html.length > max_input_chars) return EmailHtmlSanitizer.sanitize(html, options)
        val key = memo_key(html, options)
        synchronized(lock) { entries[key] }?.let { return it }
        val sanitized = EmailHtmlSanitizer.sanitize(html, options)
        synchronized(lock) { entries[key] = sanitized }
        return sanitized
    }

    fun warm(html: String?) {
        val options = last_options ?: return
        if (html.isNullOrBlank() || html.length > max_input_chars) return
        val key = memo_key(html, options)
        if (synchronized(lock) { entries.containsKey(key) }) return
        val sanitized = EmailHtmlSanitizer.sanitize(html, options)
        synchronized(lock) { entries[key] = sanitized }
    }

    fun clear() {
        synchronized(lock) { entries.clear() }
        last_options = null
    }
}
