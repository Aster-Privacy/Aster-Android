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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.mail.is_sendable_address
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model
import org.astermail.android.ui.common.sender_id_for_email

@Composable
fun DefaultSenderScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors
    val external_accounts_vm: org.astermail.android.imports.ExternalAccountsViewModel = hiltViewModel()
    val external_accounts_state by external_accounts_vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.load_profile()
        vm.load_aliases()
        vm.load_custom_domain_addresses()
        vm.load_default_sender()
    }

    val user_email = state.user?.email.orEmpty()
    val external_senders = remember(external_accounts_state) {
        org.astermail.android.imports.external_sender_map(external_accounts_state)
    }
    val addresses = remember(
        user_email,
        state.aliases,
        state.custom_domain_addresses,
        external_senders,
    ) {
        val options = mutableListOf<String>()
        if (user_email.isNotBlank()) options.add(user_email)
        state.aliases
            .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
            .forEach { if (it.address !in options) options.add(it.address) }
        state.custom_domain_addresses
            .filter { it.is_enabled && !it.decryption_failed && is_sendable_address(it.address) }
            .forEach { if (it.address !in options) options.add(it.address) }
        external_senders.keys.forEach { if (is_sendable_address(it) && it !in options) options.add(it) }
        options
    }

    val selected_email = remember(state.default_sender_id, addresses, user_email, external_senders) {
        val resolved = org.astermail.android.ui.common.resolve_primary_sender_email(
            state.default_sender_id,
            user_email,
            state.aliases,
            state.ghost_aliases,
            state.custom_domain_addresses,
            external_senders,
        )
        if (resolved in addresses) resolved else user_email
    }

    detail_scaffold(
        title = stringResource(R.string.settings_default_sender),
        on_back = on_back,
    ) {
        Text(
            text = stringResource(R.string.default_sender_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = AsterSpacing.xs, end = AsterSpacing.xs, bottom = AsterSpacing.md),
        )

        AsterCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (addresses.isEmpty()) {
                    Text(
                        text = stringResource(R.string.default_sender_no_addresses),
                        color = colors.text_tertiary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(AsterSpacing.lg),
                    )
                } else {
                    addresses.forEach { address ->
                        choice_option_row(
                            label = address,
                            subtitle = if (address == user_email) {
                                stringResource(R.string.default_sender_primary_note)
                            } else {
                                null
                            },
                            selected = address == selected_email,
                            on_click = {
                                vm.set_default_sender(
                                    sender_id_for_email(
                                        address,
                                        user_email,
                                        state.aliases,
                                        state.ghost_aliases,
                                        state.custom_domain_addresses,
                                        external_senders,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
