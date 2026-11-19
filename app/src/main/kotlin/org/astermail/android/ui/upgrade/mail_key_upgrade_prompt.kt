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

package org.astermail.android.ui.upgrade

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import org.astermail.android.R
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.mail.MailKeyUpgrade
import org.astermail.android.mail.MailKeyUpgradePhase
import org.astermail.android.mail.MailKeyUpgradeState

@HiltViewModel
class MailKeyUpgradeViewModel @Inject constructor(
    private val upgrade: MailKeyUpgrade,
) : ViewModel() {
    val state: StateFlow<MailKeyUpgradeState> = upgrade.state

    fun sync(active: Boolean) {
        if (active) upgrade.start() else upgrade.stop()
    }

    fun retry() = upgrade.retry()
}

@Composable
fun MailKeyUpgradePrompt(active: Boolean) {
    val vm: MailKeyUpgradeViewModel = hiltViewModel()
    LaunchedEffect(active) { vm.sync(active) }
    val state by vm.state.collectAsStateWithLifecycle()
    if (!active || !state.is_blocking) return
    val failed = state.phase == MailKeyUpgradePhase.FAILED
    var mounts by remember { mutableStateOf(0) }
    key(mounts) {
        AsterAlertDialog(
            on_dismiss = { mounts += 1 },
            title = stringResource(R.string.mail_key_upgrade_title),
            message = stringResource(if (failed) R.string.mail_key_upgrade_failed else R.string.mail_key_upgrade_running),
            confirm_label = stringResource(R.string.mail_key_upgrade_retry),
            on_confirm = { vm.retry() },
            is_busy = !failed,
            dismiss_on_confirm = false,
            extra_content = {
                Text(
                    text = pluralStringResource(R.plurals.mail_key_upgrade_progress, state.processed, state.processed),
                )
            },
        )
    }
}
