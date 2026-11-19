// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import java.io.File
import java.io.IOException
import java.util.UUID

private const val MAX_DOWNLOAD_NAME_ATTEMPTS = 1000
internal const val MAX_FILE_NAME_BYTES = 255
private const val COUNTER_RESERVE_BYTES = 7
private const val MAX_EXTENSION_BYTES = 32

internal fun truncate_utf8_bytes(value: String, max_bytes: Int): String {
    var used = 0
    var end = 0
    while (end < value.length) {
        val code_point = value.codePointAt(end)
        val units = Character.charCount(code_point)
        val size = when {
            code_point < 0x80 -> 1
            code_point < 0x800 -> 2
            code_point < 0x10000 -> 3
            else -> 4
        }
        if (used + size > max_bytes) break
        used += size
        end += units
    }
    return value.substring(0, end)
}

internal fun split_file_name(name: String): Pair<String, String> {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) return name to ""
    val extension = name.substring(dot)
    if (extension.toByteArray(Charsets.UTF_8).size > MAX_EXTENSION_BYTES) return name to ""
    return name.substring(0, dot) to extension
}

internal fun fit_file_name(stem: String, suffix: String, extension: String): String {
    val room = MAX_FILE_NAME_BYTES - suffix.toByteArray(Charsets.UTF_8).size -
        extension.toByteArray(Charsets.UTF_8).size
    val cut = truncate_utf8_bytes(stem, room).trimEnd(' ', '.')
    return cut.ifEmpty { "attachment" } + suffix + extension
}

internal fun fit_attachment_name(name: String): String {
    val (stem, extension) = split_file_name(name)
    val fitted = fit_file_name(stem, "", extension)
    return if (fitted.toByteArray(Charsets.UTF_8).size + COUNTER_RESERVE_BYTES <= MAX_FILE_NAME_BYTES) {
        fitted
    } else {
        fit_file_name(
            truncate_utf8_bytes(stem, MAX_FILE_NAME_BYTES - COUNTER_RESERVE_BYTES -
                extension.toByteArray(Charsets.UTF_8).size),
            "",
            extension,
        )
    }
}

internal fun write_download_file(
    dir: File,
    name: String,
    bytes: ByteArray,
    writer: (File, ByteArray) -> Unit = { target, data -> target.writeBytes(data) },
): File? {
    val (stem, extension) = split_file_name(name)
    val dir_prefix = dir.canonicalPath + File.separator
    for (attempt in 0 until MAX_DOWNLOAD_NAME_ATTEMPTS) {
        val suffix = if (attempt == 0) "" else " ($attempt)"
        val file = File(dir, fit_file_name(stem, suffix, extension))
        if (!file.canonicalPath.startsWith(dir_prefix)) return null
        if (file.createNewFile()) {
            try {
                writer(file, bytes)
            } catch (failure: Exception) {
                file.delete()
                return null
            }
            return file
        }
    }
    return null
}

internal const val SHARED_ATTACHMENTS_DIR_NAME = "shared_attachments"
internal const val SHARED_IMPORTS_DIR_NAME = "imports"
private const val SHARED_ATTACHMENT_MAX_AGE_MS = 60L * 60L * 1000L

internal fun write_share_file(
    cache_dir: File,
    safe_name: String,
    bytes: ByteArray,
    now_ms: Long = System.currentTimeMillis(),
): File {
    val root = File(cache_dir, SHARED_ATTACHMENTS_DIR_NAME).apply { mkdirs() }
    root.listFiles()?.forEach { entry ->
        if (entry.name == SHARED_IMPORTS_DIR_NAME) return@forEach
        if (now_ms - entry.lastModified() >= SHARED_ATTACHMENT_MAX_AGE_MS) {
            runCatching { entry.deleteRecursively() }
        }
    }
    val holder = File(root, UUID.randomUUID().toString())
    if (!holder.mkdirs()) throw IOException("share directory unavailable")
    val target = File(holder, safe_name)
    target.writeBytes(bytes)
    holder.setLastModified(now_ms)
    return target
}
