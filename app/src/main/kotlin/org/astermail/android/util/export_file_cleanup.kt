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

package org.astermail.android.util

import android.content.Context
import java.io.File

const val EXPORTS_CACHE_DIR_NAME = "exports"

private const val SENSITIVE_EXPORT_MAX_AGE_MS = 60L * 60L * 1000L

fun is_sensitive_export_file_name(name: String): Boolean {
    val lower = name.lowercase(java.util.Locale.ROOT)
    return lower.endsWith(".txt") && lower.contains("recovery") ||
        lower.startsWith("aster-aliases-export-") && lower.endsWith(".zip")
}

fun purge_sensitive_export_files(context: Context, max_age_ms: Long = SENSITIVE_EXPORT_MAX_AGE_MS): Int {
    val dir = File(context.cacheDir, EXPORTS_CACHE_DIR_NAME)
    val files = dir.listFiles() ?: return 0
    val now = System.currentTimeMillis()
    var removed = 0
    for (file in files) {
        if (!file.isFile || !is_sensitive_export_file_name(file.name)) continue
        if (max_age_ms > 0 && now - file.lastModified() < max_age_ms) continue
        if (runCatching { file.delete() }.getOrDefault(false)) removed += 1
    }
    return removed
}

fun delete_export_file_quietly(file: File) {
    runCatching { if (file.exists()) file.delete() }
}
