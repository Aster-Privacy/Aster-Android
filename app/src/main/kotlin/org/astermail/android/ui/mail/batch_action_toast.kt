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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.mail.MailViewModel
import org.astermail.android.ui.common.TopToastState
import org.astermail.android.ui.common.app_toast

@Composable
fun batch_action_toast(mail_vm: MailViewModel) {
    val batch_action by mail_vm.batch_action_state.collectAsStateWithLifecycle()
    LaunchedEffect(batch_action) {
        val ba = batch_action
        if (ba == null) {
            app_toast.dismiss_accumulated()
            return@LaunchedEffect
        }
        app_toast.show(
            TopToastState(
                message = ba.message,
                undo_label = ba.undo_label,
                on_undo = { ba.on_undo(); mail_vm.clear_batch_action(ba.action_key) },
                on_timeout = { mail_vm.clear_batch_action(ba.action_key) },
                on_close = { mail_vm.clear_batch_action(ba.action_key) },
                accumulation_key = ba.action_key,
            ),
        )
    }
}
