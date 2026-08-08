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

import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.settings.SettingsViewModel

@Composable
fun ConnectionScreen(
    on_back: () -> Unit,
) {
    val vm: SettingsViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val colors = AsterMaterial.colors

    LaunchedEffect(Unit) { vm.load_connection_preference() }

    detail_scaffold(title = stringResource(R.string.settings_connection), on_back = on_back) {
        Text(
            text = stringResource(R.string.connection_description),
            color = colors.text_tertiary,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = AsterSpacing.lg),
        )
        v_gap(AsterSpacing.lg)
        section_label(stringResource(R.string.connection_method_header))
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            connection_option_row(
                icon = TablerIcons.Bolt,
                title = stringResource(R.string.connection_direct),
                subtitle = stringResource(R.string.connection_direct_description),
                selected = state.connection_method == "direct",
            ) { vm.update_connection_preference("direct") }
            AsterDivider(modifier = Modifier)
            connection_option_row(
                icon = TablerIcons.Router,
                title = stringResource(R.string.connection_cdn_relay),
                subtitle = stringResource(R.string.connection_cdn_relay_description),
                selected = state.connection_method == "cdn_relay",
            ) { vm.update_connection_preference("cdn_relay") }
        }
        if (state.connection_saving) {
            v_gap(AsterSpacing.md)
            Row(
                modifier = Modifier.padding(horizontal = AsterSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(AsterSpacing.sm))
                Text(text = stringResource(R.string.connection_saving), color = colors.text_tertiary, fontSize = 13.sp)
            }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
private fun connection_option_row(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = on_click)
            .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.text_secondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = colors.text_primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = colors.text_tertiary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(AsterSpacing.md))
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(
                    width = 2.dp,
                    color = if (selected) colors.accent_blue else colors.border_primary,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(colors.accent_blue, CircleShape),
                )
            }
        }
    }
}
