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

package org.astermail.android.design.components

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

const val aster_popup_scrim_alpha = 0.32f

val LocalAsterPopupOverlay: ProvidableCompositionLocal<@Composable () -> Unit> =
    staticCompositionLocalOf { {} }

fun aster_host_window(context: Context): Window? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current.window
        current = current.baseContext
    }
    return null
}

@Suppress("DEPRECATION")
fun apply_popup_system_bars(
    window: Window,
    host: Window?,
    light_status_bars: Boolean? = null,
    light_navigation_bars: Boolean? = null,
) {
    val decor = window.decorView
    window.clearFlags(
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
    )
    window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        window.isNavigationBarContrastEnforced = false
        window.isStatusBarContrastEnforced = false
    }
    val host_controller = host?.let { WindowCompat.getInsetsController(it, it.decorView) }
    val controller = WindowCompat.getInsetsController(window, decor)
    val status = light_status_bars ?: host_controller?.isAppearanceLightStatusBars
    val navigation = light_navigation_bars ?: host_controller?.isAppearanceLightNavigationBars
    if (status != null && controller.isAppearanceLightStatusBars != status) {
        controller.isAppearanceLightStatusBars = status
    }
    if (navigation != null && controller.isAppearanceLightNavigationBars != navigation) {
        controller.isAppearanceLightNavigationBars = navigation
    }
}

fun prepare_platform_dialog(dialog: Dialog) {
    val window = dialog.window ?: return
    apply_popup_system_bars(window = window, host = aster_host_window(dialog.context))
}

fun Dialog.aster_show() {
    show()
    prepare_platform_dialog(this)
}

fun prepare_popup_window(
    window: Window,
    host: Window?,
    light_status_bars: Boolean? = null,
    light_navigation_bars: Boolean? = null,
    full_window: Boolean = false,
) {
    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
    window.setDimAmount(0f)
    if (full_window) {
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }
    WindowCompat.setDecorFitsSystemWindows(window, false)
    apply_popup_system_bars(
        window = window,
        host = host,
        light_status_bars = light_status_bars,
        light_navigation_bars = light_navigation_bars,
    )
}

@Composable
fun aster_popup_window_effect(
    light_status_bars: Boolean? = null,
    light_navigation_bars: Boolean? = null,
    full_window: Boolean = false,
) {
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        prepare_popup_window(
            window = window,
            host = aster_host_window(context),
            light_status_bars = light_status_bars,
            light_navigation_bars = light_navigation_bars,
            full_window = full_window,
        )
    }
}
