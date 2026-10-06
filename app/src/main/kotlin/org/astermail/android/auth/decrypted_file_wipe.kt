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

package org.astermail.android.auth

import java.io.File
import org.astermail.android.share.OPENED_ATTACHMENTS_DIR_NAME
import org.astermail.android.util.EXPORTS_CACHE_DIR_NAME

const val OUTBOX_ATTACHMENTS_DIR_NAME = "outbox_attachments"

val DECRYPTED_CACHE_DIR_NAMES: List<String> = listOf(
    "shared_attachments",
    OPENED_ATTACHMENTS_DIR_NAME,
    "draft_attachments",
    "email_img_cache",
    EXPORTS_CACHE_DIR_NAME,
)

private fun delete_children(dir: File): Int {
    val children = dir.listFiles() ?: return 0
    var removed = 0
    for (child in children) {
        if (runCatching { child.deleteRecursively() }.getOrDefault(false)) removed += 1
    }
    return removed
}

fun wipe_decrypted_caches(cache_dir: File): Int =
    DECRYPTED_CACHE_DIR_NAMES.sumOf { delete_children(File(cache_dir, it)) }

fun wipe_outbox_attachments(files_dir: File, pending_ids: Collection<String>?): Int {
    val dir = File(files_dir, OUTBOX_ATTACHMENTS_DIR_NAME)
    if (pending_ids == null) return delete_children(dir)
    var removed = 0
    for (pending_id in pending_ids) {
        if (pending_id.isEmpty() || pending_id.contains('/') || pending_id.contains('\\') || pending_id.contains("..")) {
            continue
        }
        val file = File(dir, "$pending_id.json")
        if (runCatching { file.isFile && file.delete() }.getOrDefault(false)) removed += 1
    }
    return removed
}
