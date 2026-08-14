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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AsterSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AsterSpacing.md),
        ) {
            connection_option_card(
                image = R.drawable.settings_direct,
                title = stringResource(R.string.connection_direct),
                subtitle = stringResource(R.string.connection_direct_description),
                selected = state.connection_method == "direct",
                modifier = Modifier.fillMaxWidth(),
            ) { vm.update_connection_preference("direct") }
            connection_option_card(
                image = R.drawable.settings_cdn,
                title = stringResource(R.string.connection_cdn_relay),
                subtitle = stringResource(R.string.connection_cdn_relay_description),
                selected = state.connection_method == "cdn_relay",
                modifier = Modifier.fillMaxWidth(),
            ) { vm.update_connection_preference("cdn_relay") }
        }
        v_gap(AsterSpacing.xxl)
    }
}

@Composable
internal fun illustrated_option_card(
    image: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    on_click: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val shape = SquircleShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(colors.bg_card, shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent_blue else colors.border_primary,
                shape = shape,
            )
            .clickable(onClick = on_click)
            .padding(AsterSpacing.sm),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(24f / 9f)
                .clip(SquircleShape(12.dp))
                .background(colors.bg_secondary),
        ) {
            Image(
                painter = painterResource(image),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(AsterSpacing.xs)
                        .size(22.dp)
                        .background(colors.accent_blue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TablerIcons.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = title,
            color = colors.text_primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = AsterSpacing.xs),
        )
        Text(
            text = subtitle,
            color = colors.text_tertiary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = AsterSpacing.xs, vertical = 2.dp),
        )
        Spacer(Modifier.height(AsterSpacing.xs))
    }
}

@Composable
private fun connection_option_card(
    image: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    on_click: () -> Unit,
) = illustrated_option_card(image, title, subtitle, selected, modifier, on_click)
