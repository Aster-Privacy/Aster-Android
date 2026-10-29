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

const val EMAIL_IMAGE_CACHE_DIR_NAME = "email_img_cache"

val DECRYPTED_CACHE_DIR_NAMES: List<String> = listOf(
    "shared_attachments",
    OPENED_ATTACHMENTS_DIR_NAME,
    "draft_attachments",
    EMAIL_IMAGE_CACHE_DIR_NAME,
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

fun remove_email_image_disk_cache(cache_dir: File): Boolean =
    File(cache_dir, EMAIL_IMAGE_CACHE_DIR_NAME).deleteRecursively()

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

const val OUTBOX_ORPHAN_GRACE_MS = 60_000L

fun wipe_orphaned_outbox_attachments(
    files_dir: File,
    live_pending_ids: Collection<String>,
    now_ms: Long = System.currentTimeMillis(),
): Int {
    val entries = File(files_dir, OUTBOX_ATTACHMENTS_DIR_NAME).listFiles() ?: return 0
    val live_names = live_pending_ids.mapTo(HashSet()) { "$it.json" }
    var removed = 0
    for (entry in entries) {
        if (entry.name in live_names) continue
        if (now_ms - entry.lastModified() < OUTBOX_ORPHAN_GRACE_MS) continue
        if (runCatching { entry.deleteRecursively() }.getOrDefault(false)) removed += 1
    }
    return removed
}
