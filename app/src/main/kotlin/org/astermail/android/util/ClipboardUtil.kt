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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper

private const val SENSITIVE_CLIP_CLEAR_DELAY_MS = 60_000L

fun schedule_sensitive_clipboard_clear(context: Context) {
    val app_context = context.applicationContext
    val clipboard = app_context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    val copied_at = runCatching { clipboard.primaryClipDescription?.timestamp }.getOrNull() ?: return
    Handler(Looper.getMainLooper()).postDelayed({
        val current_at = runCatching { clipboard.primaryClipDescription?.timestamp }.getOrNull()
        if (current_at != copied_at) return@postDelayed
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }, SENSITIVE_CLIP_CLEAR_DELAY_MS)
}
