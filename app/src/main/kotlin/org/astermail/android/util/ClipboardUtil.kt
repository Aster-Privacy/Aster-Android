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

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper

private const val SENSITIVE_CLIP_CLEAR_DELAY_MS = 60_000L

fun schedule_sensitive_clipboard_clear(context: Context, copied_text: String) {
    val app_context = context.applicationContext
    Handler(Looper.getMainLooper()).postDelayed({
        val clipboard = app_context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return@postDelayed
        val current = clipboard.primaryClip
        val current_text = if (current != null && current.itemCount > 0) {
            current.getItemAt(0).coerceToText(app_context)?.toString()
        } else {
            null
        }
        if (current_text == copied_text) {
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
        }
    }, SENSITIVE_CLIP_CLEAR_DELAY_MS)
}
