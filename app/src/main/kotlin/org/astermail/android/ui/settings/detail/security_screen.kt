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

package org.astermail.android.ui.settings.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import org.astermail.android.R
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel

@Composable
fun SecurityScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load_security_status() }

    val sec = state.security_status
    val totp_sub = if (sec != null) {
        if (sec.totp_enabled) stringResource(R.string.enabled) else stringResource(R.string.disabled)
    } else stringResource(R.string.two_factor_subtitle_add)

    detail_scaffold(title = stringResource(R.string.security), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.change_password),
                subtitle = stringResource(R.string.change_password_subtitle),
                icon = Icons.Outlined.Lock,
                on_click = { on_open("change_password") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.two_factor_auth),
                subtitle = totp_sub,
                icon = Icons.Outlined.VerifiedUser,
                on_click = { on_open("two_factor") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.active_sessions),
                subtitle = stringResource(R.string.devices_signed_in),
                icon = Icons.Outlined.Devices,
                on_click = { on_open("sessions") },
            )
            AsterDivider()
            detail_row(
                title = stringResource(R.string.recovery_key),
                subtitle = stringResource(R.string.backup_access),
                icon = Icons.Outlined.VpnKey,
                on_click = { on_open("recovery_key_view") },
            )
        }
        v_gap(AsterSpacing.lg)
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(
                title = stringResource(R.string.delete_account),
                subtitle = stringResource(R.string.delete_account_subtitle),
                icon = Icons.Outlined.DeleteForever,
                on_click = { on_open("delete_account") },
            )
        }
        v_gap(AsterSpacing.xxl)
    }
}
