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

package org.astermail.android.ui.mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.mail.UndoSendViewModel
import org.astermail.android.ui.common.TopToastState
import org.astermail.android.ui.common.app_toast

@Composable
fun undo_send_toast(on_view: () -> Unit, undo_vm: UndoSendViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val pending by undo_vm.pending_undo_send.collectAsStateWithLifecycle()
    var dismissed_send_id by remember { mutableStateOf<Long?>(null) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(pending?.started_at_ms) {
        val p = pending
        if (p == null) {
            if (shown) {
                app_toast.dismiss()
                shown = false
            }
            return@LaunchedEffect
        }
        val end_ms = p.started_at_ms + p.duration_ms
        while (true) {
            if (dismissed_send_id == p.started_at_ms) {
                app_toast.dismiss()
                shown = false
                return@LaunchedEffect
            }
            val remaining_ms = end_ms - System.currentTimeMillis()
            if (remaining_ms <= 0) break
            val seconds_left = ((remaining_ms + 999) / 1000).toInt().coerceAtLeast(1)
            app_toast.show(
                TopToastState(
                    message = context.getString(R.string.sending_in_countdown, seconds_left),
                    undo_label = context.getString(R.string.undo),
                    on_undo = { p.undo() },
                    secondary_label = context.getString(R.string.view_message),
                    on_secondary = on_view,
                    on_tap = on_view,
                    show_close = true,
                    on_close = { dismissed_send_id = p.started_at_ms },
                    duration_ms = remaining_ms,
                    key = p.started_at_ms,
                ),
            )
            shown = true
            kotlinx.coroutines.delay(1000L - (remaining_ms % 1000L))
        }
        app_toast.dismiss()
        shown = false
    }
}
