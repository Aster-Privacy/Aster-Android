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

package org.astermail.android.ui.survey

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.survey.FoundUsOutcome
import org.astermail.android.survey.FoundUsViewModel
import org.astermail.android.ui.common.TopToastState
import org.astermail.android.ui.common.app_toast

@Stable
class FoundUsHost internal constructor(
    val is_banner_visible: Boolean,
    val choosing_dismissal: Boolean,
    val on_answer: () -> Unit,
    val on_dismiss: () -> Unit,
    val on_remind_later: () -> Unit,
    val on_dont_ask: () -> Unit,
)

@Composable
fun found_us_host(account_id: String?, is_blocked: Boolean): FoundUsHost {
    val vm: FoundUsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(account_id) { vm.load(account_id) }
    var dialog_open by rememberSaveable(account_id) { mutableStateOf(false) }
    val thanks = stringResource(R.string.found_us_thanks)
    val failed = stringResource(R.string.found_us_send_failed)
    LaunchedEffect(vm) {
        vm.outcomes.collect { outcome ->
            when (outcome) {
                FoundUsOutcome.THANKS -> {
                    dialog_open = false
                    app_toast.show(TopToastState(message = thanks, duration_ms = 6000L))
                }
                FoundUsOutcome.SEND_FAILED -> app_toast.show(failed)
                FoundUsOutcome.CLOSED -> dialog_open = false
            }
        }
    }
    val is_current = account_id != null && state.account_id == account_id
    if (dialog_open && is_current && state.is_eligible) {
        found_us_dialog(
            account_id = account_id,
            is_sending = state.is_sending,
            on_close = { dialog_open = false },
            on_submit = vm::submit,
            on_skip = vm::skip,
        )
    }
    return FoundUsHost(
        is_banner_visible = is_current && state.is_banner_visible && !is_blocked,
        choosing_dismissal = state.choosing_dismissal,
        on_answer = { dialog_open = true },
        on_dismiss = vm::choose_dismissal,
        on_remind_later = vm::remind_later,
        on_dont_ask = vm::dont_ask,
    )
}

@Composable
fun found_us_banner(host: FoundUsHost, modifier: Modifier = Modifier) {
    found_us_banner(
        choosing_dismissal = host.choosing_dismissal,
        on_answer = host.on_answer,
        on_dismiss = host.on_dismiss,
        on_remind_later = host.on_remind_later,
        on_dont_ask = host.on_dont_ask,
        modifier = modifier,
    )
}
