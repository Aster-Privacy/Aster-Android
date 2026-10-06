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

import compose.icons.TablerIcons
import compose.icons.tablericons.Mail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import org.astermail.android.R
import org.astermail.android.mail.UndoSendViewModel
import org.astermail.android.ui.common.TopToastState
import org.astermail.android.ui.common.app_toast

@Composable
fun undo_send_toast(on_view: () -> Unit, undo_vm: UndoSendViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val pending by undo_vm.pending_undo_send.collectAsStateWithLifecycle()
    val dismissed_send_id by undo_vm.dismissed_send_id.collectAsStateWithLifecycle()
    val view by rememberUpdatedState(on_view)
    val send_id = pending?.started_at_ms
    LaunchedEffect(send_id, dismissed_send_id == send_id) {
        val p = pending ?: return@LaunchedEffect
        val id = p.started_at_ms
        if (dismissed_send_id == id) return@LaunchedEffect
        val end_ms = id + p.duration_ms
        try {
            while (true) {
                val remaining_ms = end_ms - System.currentTimeMillis()
                if (remaining_ms <= 0) break
                val seconds_left = ((remaining_ms + 999) / 1000).toInt().coerceAtLeast(1)
                app_toast.show_sticky(
                    TopToastState(
                        message = context.getString(R.string.sending_in_countdown, seconds_left),
                        undo_label = context.getString(R.string.undo),
                        on_undo = {
                            undo_vm.dismiss(id)
                            p.undo()
                        },
                        secondary_label = context.getString(R.string.view_message),
                        secondary_icon = TablerIcons.Mail,
                        on_secondary = { view() },
                        on_tap = { view() },
                        show_close = true,
                        on_close = { undo_vm.dismiss(id) },
                        key = id,
                    ),
                )
                delay(1000L - (remaining_ms % 1000L))
            }
        } finally {
            app_toast.hide_sticky(id)
        }
    }
}
