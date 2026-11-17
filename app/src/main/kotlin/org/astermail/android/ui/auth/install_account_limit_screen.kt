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

package org.astermail.android.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.TablerIcons
import compose.icons.tablericons.DeviceMobile
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton

@Composable
fun InstallAccountLimitScreen(
    on_sign_in: () -> Unit,
    on_upgrade: () -> Unit,
    on_back: () -> Unit,
) {
    val colors = AsterMaterial.colors

    BackHandler { on_back() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AsterSpacing.xxl, vertical = AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = TablerIcons.DeviceMobile,
                contentDescription = null,
                tint = colors.accent_blue,
                modifier = Modifier.size(40.dp),
            )

            Spacer(Modifier.height(AsterSpacing.xl))

            Text(
                text = stringResource(R.string.install_limit_title),
                color = colors.text_primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AsterSpacing.md))

            Text(
                text = stringResource(R.string.install_limit_message),
                color = colors.text_secondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(AsterSpacing.xl))

            AsterButton(
                label = stringResource(R.string.install_limit_sign_in),
                onClick = on_sign_in,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(AsterSpacing.md))

            AsterSecondaryButton(
                label = stringResource(R.string.install_limit_upgrade),
                onClick = on_upgrade,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(AsterSpacing.md))

            Text(
                text = stringResource(R.string.install_limit_upgrade_hint),
                color = colors.text_muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
