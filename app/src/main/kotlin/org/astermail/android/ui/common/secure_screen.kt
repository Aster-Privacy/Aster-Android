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

package org.astermail.android.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.atomic.AtomicInteger

object SecureScreenGuard {
    private val active = AtomicInteger(0)

    fun is_active(): Boolean = active.get() > 0

    fun acquire() {
        active.incrementAndGet()
    }

    fun release() {
        active.updateAndGet { if (it > 0) it - 1 else 0 }
    }
}

interface SecureFlagHost {
    fun enforce_secure_flag()
}

fun Context.find_host_activity(): Activity? {
    var cursor: Context? = this
    while (cursor is ContextWrapper) {
        if (cursor is Activity) return cursor
        cursor = cursor.baseContext
    }
    return null
}

@Composable
fun secure_screen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.find_host_activity()
        SecureScreenGuard.acquire()
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        val root = activity?.window?.decorView?.rootView
        val previous_filter = root?.filterTouchesWhenObscured ?: false
        root?.filterTouchesWhenObscured = true
        onDispose {
            SecureScreenGuard.release()
            root?.filterTouchesWhenObscured = previous_filter
            (activity as? SecureFlagHost)?.enforce_secure_flag()
        }
    }
}
