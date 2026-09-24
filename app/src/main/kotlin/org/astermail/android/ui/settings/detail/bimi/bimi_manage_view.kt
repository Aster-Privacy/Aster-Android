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

package org.astermail.android.ui.settings.detail.bimi

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.api.domains.BimiState
import org.astermail.android.api.settings.CustomDomain
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.design.components.AsterSecondaryButton
import org.astermail.android.design.components.DialogConfirmStyle
import org.astermail.android.settings.BimiUiState
import org.astermail.android.settings.BimiViewModel
import org.astermail.android.ui.settings.detail.v_gap

@Composable
internal fun bimi_manage_view(
    state: BimiUiState,
    domain: CustomDomain?,
    domain_name: String,
    vm: BimiViewModel,
) {
    val colors = AsterMaterial.colors
    var confirm_turn_off by remember { mutableStateOf(false) }

    if (confirm_turn_off) {
        AsterAlertDialog(
            on_dismiss = { confirm_turn_off = false },
            title = stringResource(R.string.domain_bimi_turn_off_title),
            message = stringResource(R.string.domain_bimi_turn_off_body),
            confirm_label = stringResource(R.string.domain_bimi_turn_off_confirm),
            cancel_label = stringResource(R.string.cancel),
            confirm_style = DialogConfirmStyle.destructive,
            on_confirm = {
                confirm_turn_off = false
                vm.turn_off()
            },
        )
    }

    bimi_preview(domain_name = domain_name, png = state.view?.preview_png)
    v_gap(AsterSpacing.lg)
    bimi_state_line(state.bimi_state, state.view?.last_checked_at)

    if (state.bimi_state == BimiState.external) {
        v_gap(AsterSpacing.sm)
        Text(
            text = stringResource(R.string.domain_bimi_row_external),
            color = colors.text_tertiary,
            fontSize = 12.sp,
        )
    }

    if (state.bimi_state == BimiState.attention) {
        v_gap(AsterSpacing.md)
        bimi_requirements(view = state.view, domain = domain)
    }

    if (state.is_published) {
        v_gap(AsterSpacing.md)
        bimi_dns_status(state = state, show_check_button = false, vm = vm)
    }

    v_gap(AsterSpacing.lg)
    AsterSecondaryButton(
        label = stringResource(R.string.domain_bimi_replace_logo),
        onClick = vm::replace_logo,
        enabled = !state.turning_off,
    )
    v_gap(AsterSpacing.sm)
    AsterSecondaryButton(
        label = stringResource(if (state.checking) R.string.domain_bimi_checking else R.string.domain_bimi_check_again),
        onClick = vm::check,
        enabled = !state.checking && !state.turning_off,
        is_loading = state.checking,
    )
    v_gap(AsterSpacing.sm)
    AsterGhostButton(
        label = stringResource(R.string.domain_bimi_turn_off),
        onClick = { confirm_turn_off = true },
        enabled = !state.turning_off,
        is_loading = state.turning_off,
    )
}
