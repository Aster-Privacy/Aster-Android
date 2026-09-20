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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

internal const val CONNECTION_METHOD_DIRECT = "direct"
internal const val CONNECTION_METHOD_CDN_RELAY = "cdn_relay"

private data class ConnectionMethodOption(
    val id: String,
    val label: String,
    val description: String,
    val image: Int,
)

@Composable
fun ConnectionScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_connection_preference() }

    val relay_connected = stringResource(R.string.connection_relay_connected)
    val relay_restored = stringResource(R.string.connection_relay_restored)
    val relay_disconnected = stringResource(R.string.connection_relay_disconnected)
    var pending_method by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf<String?>(null)
    }
    LaunchedEffect(state.connection_saving) {
        val requested = pending_method
        if (!state.connection_saving && requested != null) {
            pending_method = null
            val message = when {
                state.connection_method != requested -> relay_disconnected
                requested == CONNECTION_METHOD_CDN_RELAY -> relay_connected
                else -> relay_restored
            }
            org.astermail.android.ui.common.app_toast.show(message)
        }
    }

    val options = listOf(
        ConnectionMethodOption(
            id = CONNECTION_METHOD_DIRECT,
            label = stringResource(R.string.connection_direct),
            description = stringResource(R.string.connection_direct_description),
            image = R.drawable.settings_direct,
        ),
        ConnectionMethodOption(
            id = CONNECTION_METHOD_CDN_RELAY,
            label = stringResource(R.string.connection_cdn_relay),
            description = stringResource(R.string.connection_cdn_relay_description),
            image = R.drawable.settings_cdn,
        ),
    )

    detail_scaffold(
        title = stringResource(R.string.settings_connection),
        on_back = on_back,
    ) {
        Text(
            text = stringResource(R.string.connection_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(bottom = AsterSpacing.xs),
        )
        if (state.connection_loading) {
            preferences_load_placeholder()
        } else {
            section_label(stringResource(R.string.connection_method_header))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
            ) {
                options.forEach { option ->
                    illustrated_option_card(
                        image = option.image,
                        title = option.label,
                        subtitle = option.description,
                        selected = state.connection_method == option.id,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connection_option_${option.id}"),
                        on_click = {
                            if (!state.connection_saving && state.connection_method != option.id) {
                                pending_method = option.id
                                vm.update_connection_preference(option.id)
                            }
                        },
                    )
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}
