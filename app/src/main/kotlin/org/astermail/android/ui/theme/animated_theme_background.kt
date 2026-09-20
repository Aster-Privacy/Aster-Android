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

package org.astermail.android.ui.theme

import android.content.Context
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val system_animation_poll_ms = 10_000L

@Stable
class ThemeAnimation internal constructor(internal val drawable: Drawable) {
    internal var frame_nanos by mutableLongStateOf(0L)
}

object animated_theme_image {
    private val lock = Any()

    private var held_version = 0L
    private var held_source: ByteArray? = null
    private var held_drawable: Drawable? = null

    fun load(context: Context, version: Long): Drawable? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P || version <= 0L) return null
        synchronized(lock) {
            held_drawable?.let { if (held_version == version) return it }
            release_locked()
            val bytes = custom_theme_image.load_animation(context) ?: return null
            val drawable = runCatching { decode_theme_animation(bytes) }.getOrNull() ?: return null
            held_version = version
            held_source = bytes
            held_drawable = drawable
            return drawable
        }
    }

    fun release() {
        synchronized(lock) { release_locked() }
    }

    private fun release_locked() {
        held_drawable?.let { stop(it) }
        held_drawable = null
        held_source = null
        held_version = 0L
    }

    internal fun start(drawable: Drawable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val animated = drawable as? AnimatedImageDrawable ?: return
        runCatching { if (!animated.isRunning) animated.start() }
    }

    internal fun stop(drawable: Drawable) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val animated = drawable as? AnimatedImageDrawable ?: return
        runCatching { if (animated.isRunning) animated.stop() }
    }
}

internal fun system_allows_theme_animation(context: Context): Boolean {
    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    if (power != null && power.isPowerSaveMode) return false
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return scale != 0f
}

@Composable
private fun remember_system_animation_allowed(active: Boolean): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var allowed by remember(context) { mutableStateOf(system_allows_theme_animation(context)) }
    LaunchedEffect(active, context, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                allowed = withContext(Dispatchers.IO) { system_allows_theme_animation(context) }
                delay(system_animation_poll_ms)
            }
        }
    }
    return allowed
}

@Composable
fun remember_theme_animation(enabled: Boolean): ThemeAnimation? {
    val meta by custom_theme_image.meta.collectAsState()
    val background_id = local_background_image.current
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val version = remember(meta, background_id) {
        meta?.takeIf { it.animated && background_id == custom_theme_background }?.version ?: 0L
    }
    val candidate = enabled && theme_animation_supported() && version > 0L
    val system_allowed = remember_system_animation_allowed(candidate)
    val wanted = candidate && system_allowed
    var animation by remember { mutableStateOf<ThemeAnimation?>(null) }
    LaunchedEffect(wanted, version, context) {
        if (!wanted) {
            animation = null
            withContext(NonCancellable + Dispatchers.IO) { animated_theme_image.release() }
            return@LaunchedEffect
        }
        val drawable = withContext(Dispatchers.IO) { animated_theme_image.load(context, version) }
        animation = drawable?.let { ThemeAnimation(it) }
    }
    val current = animation
    if (current != null) {
        LaunchedEffect(current, lifecycle) {
            try {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    animated_theme_image.start(current.drawable)
                    try {
                        while (true) {
                            withFrameNanos { current.frame_nanos = it }
                        }
                    } finally {
                        animated_theme_image.stop(current.drawable)
                    }
                }
            } finally {
                animated_theme_image.stop(current.drawable)
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { animated_theme_image.release() }
    }
    return current
}

fun DrawScope.draw_theme_animation(animation: ThemeAnimation) {
    if (animation.frame_nanos < 0L) return
    val drawable = animation.drawable
    val width = drawable.intrinsicWidth
    val height = drawable.intrinsicHeight
    if (width <= 0 || height <= 0) return
    if (size.width <= 0f || size.height <= 0f) return
    val scale = maxOf(size.width / width, size.height / height)
    drawIntoCanvas { canvas ->
        val target = canvas.nativeCanvas
        val checkpoint = target.save()
        target.clipRect(0f, 0f, size.width, size.height)
        target.translate((size.width - width * scale) / 2f, (size.height - height * scale) / 2f)
        target.scale(scale, scale)
        drawable.setBounds(0, 0, width, height)
        drawable.draw(target)
        target.restoreToCount(checkpoint)
    }
}
