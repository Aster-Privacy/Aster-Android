// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

object app_toast {
    val state = MutableStateFlow<TopToastState?>(null)

    fun show(message: String) {
        state.value = TopToastState(message = message)
    }

    fun show(toast: TopToastState) {
        state.value = toast
    }

    fun dismiss() {
        state.value = null
    }
}

@Composable
fun app_toast_host() {
    val current by app_toast.state.collectAsStateWithLifecycle()
    top_toast_overlay(
        state = current,
        on_dismiss = { app_toast.dismiss() },
        duration_ms = 2600,
    )
}
