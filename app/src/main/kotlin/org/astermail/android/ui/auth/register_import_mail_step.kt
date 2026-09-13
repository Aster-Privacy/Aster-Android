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

import compose.icons.TablerIcons
import compose.icons.tablericons.Inbox
import compose.icons.tablericons.Lock

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton

@Composable
fun RegisterImportMailStep(
    on_import: () -> Unit,
    on_skip: () -> Unit,
) {
    val colors = AsterMaterial.colors

    auth_centered_column {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.import_mail_step_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.import_mail_step_desc),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg_card, SquircleShape(16.dp))
                .border(1.dp, colors.border_primary, SquircleShape(16.dp))
                .padding(AsterSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(colors.bg_secondary, SquircleShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = TablerIcons.Inbox,
                    contentDescription = null,
                    tint = colors.accent_blue,
                    modifier = Modifier.size(28.dp),
                )
            }
            Spacer(Modifier.height(AsterSpacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TablerIcons.Lock,
                    contentDescription = null,
                    tint = colors.text_muted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.import_mail_privacy_note),
                    color = colors.text_tertiary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(AsterSpacing.xxl))

        AsterButton(
            label = stringResource(R.string.import_mail_action),
            onClick = on_import,
        )

        Spacer(Modifier.height(AsterSpacing.sm))

        AsterGhostButton(
            label = stringResource(R.string.import_mail_skip),
            onClick = on_skip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
