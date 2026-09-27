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

import java.io.BufferedReader
import java.io.InputStream
import java.util.Locale

const val MAX_IMPORT_FOLDERS = 200

private val SYSTEM_MBOX_LABELS = setOf(
    "inbox", "sent", "sent mail", "sent items", "drafts", "draft", "chat", "chats",
    "starred", "important", "trash", "bin", "spam", "junk", "archived", "all mail",
    "unread", "opened",
)

private val IGNORED_MBOX_LABEL_PREFIXES = listOf("category ", "imap_", "[imap]", "[gmail]")

private const val LABELS_HEADER = "x-gmail-labels:"

fun is_custom_mbox_label(name: String): Boolean {
    val lower = name.trim().lowercase(Locale.ROOT)
    if (lower.isEmpty() || lower in SYSTEM_MBOX_LABELS) return false
    return IGNORED_MBOX_LABEL_PREFIXES.none { lower.startsWith(it) }
}

fun custom_labels_from_header(value: String): List<String> =
    value.split(',').map { it.trim() }.filter { is_custom_mbox_label(it) }

fun scan_mbox_labels(stream: InputStream): List<String> {
    val found = LinkedHashSet<String>()
    val reader = BufferedReader(stream.reader(Charsets.UTF_8), 1 shl 16)
    var in_headers = false
    var header = StringBuilder()
    var capturing = false

    fun flush_header() {
        if (capturing) {
            for (label in custom_labels_from_header(header.toString())) {
                if (found.size >= MAX_IMPORT_FOLDERS) break
                found.add(label)
            }
        }
        header = StringBuilder()
        capturing = false
    }

    while (true) {
        val line = reader.readLine() ?: break
        if (line.startsWith("From ")) {
            flush_header()
            in_headers = true
            continue
        }
        if (!in_headers) continue
        if (line.isEmpty()) {
            flush_header()
            in_headers = false
            continue
        }
        if (line[0] == ' ' || line[0] == '\t') {
            if (capturing) header.append(' ').append(line.trim())
            continue
        }
        flush_header()
        if (line.length > LABELS_HEADER.length && line.substring(0, LABELS_HEADER.length).equals(LABELS_HEADER, ignoreCase = true)) {
            capturing = true
            header.append(line.substring(LABELS_HEADER.length).trim())
        }
    }
    flush_header()
    return found.toList()
}
