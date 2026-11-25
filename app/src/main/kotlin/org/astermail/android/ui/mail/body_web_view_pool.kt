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

import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.first

internal const val BODY_WEB_VIEW_WARM_DELAY_MS = 900L

internal object body_web_view_pool {
    private var spare: mail_body_web_view? = null
    private var spare_context: Context? = null
    private var pending: Runnable? = null
    private val main = Handler(Looper.getMainLooper())

    fun warm(ctx: Context) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        if (spare != null || pending != null) return
        val owner = lifecycle_owner_of(ctx) ?: return
        val task = Runnable {
            Looper.myQueue().addIdleHandler {
                pending = null
                if (spare == null && web_view_support.is_available(ctx)) {
                    runCatching { mail_body_web_view(ctx) }.getOrNull()?.let { view ->
                        spare = view
                        spare_context = ctx
                        owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                            override fun onDestroy(owner: LifecycleOwner) {
                                owner.lifecycle.removeObserver(this)
                                release(ctx)
                            }
                        })
                    }
                }
                false
            }
        }
        pending = task
        main.postDelayed(task, BODY_WEB_VIEW_WARM_DELAY_MS)
    }

    fun take(ctx: Context): mail_body_web_view? {
        if (Looper.myLooper() != Looper.getMainLooper()) return null
        val view = spare ?: return null
        if (spare_context !== ctx || view.destroyed || view.parent != null) return null
        spare = null
        spare_context = null
        return view
    }

    fun release(ctx: Context? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) return
        pending?.let { main.removeCallbacks(it) }
        pending = null
        if (ctx != null && spare_context !== ctx) return
        val view = spare ?: return
        spare = null
        spare_context = null
        runCatching { view.release_body() }
    }

    private fun lifecycle_owner_of(ctx: Context): LifecycleOwner? {
        var current: Context? = ctx
        while (current != null) {
            if (current is LifecycleOwner) return current
            current = (current as? ContextWrapper)?.baseContext
        }
        return null
    }
}

@Composable
internal fun warm_body_web_view(ready: Boolean, busy: () -> Boolean) {
    val ctx = LocalContext.current
    val latest_busy = androidx.compose.runtime.rememberUpdatedState(busy)
    LaunchedEffect(ctx, ready) {
        if (!ready) return@LaunchedEffect
        androidx.compose.runtime.snapshotFlow { latest_busy.value() }.first { !it }
        body_web_view_pool.warm(ctx)
    }
}
