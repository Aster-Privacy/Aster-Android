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

import compose.icons.TablerIcons
import compose.icons.tablericons.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.settings.shared_settings_view_model

internal const val CONNECTION_METHOD_DIRECT = "direct"
internal const val CONNECTION_METHOD_CDN_RELAY = "cdn_relay"

private data class ConnectionMethodOption(
    val id: String,
    val label: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
fun ConnectionScreen(on_back: () -> Unit) {
    val vm: SettingsViewModel = shared_settings_view_model()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_connection_preference() }

    val options = listOf(
        ConnectionMethodOption(
            id = CONNECTION_METHOD_DIRECT,
            label = stringResource(R.string.connection_direct),
            description = stringResource(R.string.connection_direct_description),
            icon = TablerIcons.Bolt,
            color = colors.accent_blue,
        ),
        ConnectionMethodOption(
            id = CONNECTION_METHOD_CDN_RELAY,
            label = stringResource(R.string.connection_cdn_relay),
            description = stringResource(R.string.connection_cdn_relay_description),
            icon = TablerIcons.Shield,
            color = colors.success,
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
            AsterCard(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, option ->
                    connection_method_option(
                        option = option,
                        selected = state.connection_method == option.id,
                        enabled = !state.connection_saving,
                        on_click = { vm.update_connection_preference(option.id) },
                    )
                    if (i < options.lastIndex) AsterDivider(modifier = Modifier)
                }
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun connection_method_option(
    option: ConnectionMethodOption,
    selected: Boolean,
    enabled: Boolean,
    on_click: () -> Unit,
) {
    choice_option_row(
        label = option.label,
        selected = selected,
        on_click = on_click,
        subtitle = option.description,
        enabled = enabled,
        leading = {
            Icon(
                imageVector = option.icon,
                contentDescription = null,
                tint = option.color,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
