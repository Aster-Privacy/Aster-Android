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

import androidx.activity.ComponentActivity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.astermail.android.design.AsterMotionScale

@OptIn(InternalComposeUiApi::class, ExperimentalComposeUiApi::class)
fun install_aster_motion_scale(activity: ComponentActivity) {
    AsterMotionScale.refresh_system_scale(activity)
    activity.lifecycle.addObserver(
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AsterMotionScale.refresh_system_scale(activity)
            }
        },
    )
    val root = activity.window.decorView
    if (root.compositionContext != null) return
    root.compositionContext = root.createLifecycleAwareWindowRecomposer(
        coroutineContext = AsterMotionScale,
        lifecycle = activity.lifecycle,
    )
}
