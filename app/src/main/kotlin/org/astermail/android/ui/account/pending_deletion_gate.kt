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

package org.astermail.android.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.account.PendingDeletionViewModel
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.components.AsterButton

@Composable
fun PendingDeletionGate(
    on_reactivated: () -> Unit,
    on_signed_out: (Boolean) -> Unit,
    view_model: PendingDeletionViewModel = hiltViewModel(),
) {
    val state by view_model.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { view_model.check() }

    if (!state.visible) return

    val colors = AsterMaterial.colors

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.bg_secondary)
                .border(1.dp, colors.border_primary, RoundedCornerShape(18.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.pending_deletion_title),
                color = colors.text_primary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val days = state.days_remaining
            Text(
                text = if (days != null && days > 0L) {
                    pluralStringResource(R.plurals.pending_deletion_body_days, days.toInt(), days.toInt())
                } else {
                    stringResource(R.string.pending_deletion_body)
                },
                color = colors.text_secondary,
                fontSize = 14.sp,
            )
            val error = state.error
            if (error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = error,
                    color = AsterMaterial.colors.accent_blue,
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            AsterButton(
                label = if (state.is_cancelling) {
                    stringResource(R.string.pending_deletion_restoring)
                } else {
                    stringResource(R.string.pending_deletion_keep)
                },
                onClick = { view_model.keep_account(on_reactivated) },
                enabled = !state.is_cancelling,
                is_loading = state.is_cancelling,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !state.is_cancelling) {
                        view_model.sign_out(on_signed_out)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.pending_deletion_sign_out),
                    color = colors.text_muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
