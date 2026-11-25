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

package org.astermail.android.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

const val steady_flag_show_after_ms = 120L
const val steady_flag_min_visible_ms = 600L
const val steady_flag_hide_grace_ms = 250L

@Composable
fun remember_steady_flag(
    active: Boolean,
    show_after_ms: Long = steady_flag_show_after_ms,
    min_visible_ms: Long = steady_flag_min_visible_ms,
    hide_grace_ms: Long = steady_flag_hide_grace_ms,
): Boolean {
    var shown by remember { mutableStateOf(false) }
    var shown_at by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            if (shown) return@LaunchedEffect
            delay(show_after_ms)
            shown_at = android.os.SystemClock.uptimeMillis()
            shown = true
        } else {
            if (!shown) return@LaunchedEffect
            val elapsed = android.os.SystemClock.uptimeMillis() - shown_at
            delay((min_visible_ms - elapsed).coerceAtLeast(0L) + hide_grace_ms)
            shown = false
        }
    }
    return shown
}

@Composable
fun <T : Any> remember_steady_value(value: T?, hide_grace_ms: Long = steady_flag_hide_grace_ms + steady_flag_min_visible_ms): T? {
    var held by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value != null) {
            held = value
        } else {
            delay(hide_grace_ms)
            held = null
        }
    }
    return if (value != null) value else held
}
