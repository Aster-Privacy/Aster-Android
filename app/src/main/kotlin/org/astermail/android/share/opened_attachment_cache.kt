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

package org.astermail.android.share

import java.io.File
import java.io.IOException
import java.util.UUID

const val OPENED_ATTACHMENTS_DIR_NAME = "opened_attachments"
const val OPENED_ATTACHMENT_MAX_AGE_MS = 60L * 60L * 1000L
private const val FALLBACK_ATTACHMENT_NAME = "attachment"

fun opened_attachments_dir(cache_dir: File): File = File(cache_dir, OPENED_ATTACHMENTS_DIR_NAME)

fun write_opened_attachment(
    cache_dir: File,
    file_name: String,
    bytes: ByteArray,
    now_ms: Long = System.currentTimeMillis(),
): File {
    prune_opened_attachments(cache_dir, now_ms)
    val holder = File(opened_attachments_dir(cache_dir), UUID.randomUUID().toString())
    if (!holder.mkdirs()) throw IOException("opened attachment directory unavailable")
    val leaf = File(file_name.replace('\\', '/')).name
        .takeUnless { it.isBlank() || it == "." || it == ".." }
        ?: FALLBACK_ATTACHMENT_NAME
    val target = File(holder, leaf)
    if (target.canonicalFile.parentFile != holder.canonicalFile) {
        throw IOException("opened attachment name escapes its directory")
    }
    target.writeBytes(bytes)
    holder.setLastModified(now_ms)
    return target
}

fun prune_opened_attachments(
    cache_dir: File,
    now_ms: Long = System.currentTimeMillis(),
    max_age_ms: Long = OPENED_ATTACHMENT_MAX_AGE_MS,
): Int {
    val entries = opened_attachments_dir(cache_dir).listFiles() ?: return 0
    var removed = 0
    for (entry in entries) {
        if (now_ms - entry.lastModified() < max_age_ms) continue
        if (runCatching { entry.deleteRecursively() }.getOrDefault(false)) removed += 1
    }
    return removed
}

fun clear_opened_attachments(cache_dir: File): Int {
    val entries = opened_attachments_dir(cache_dir).listFiles() ?: return 0
    var removed = 0
    for (entry in entries) {
        if (runCatching { entry.deleteRecursively() }.getOrDefault(false)) removed += 1
    }
    return removed
}
