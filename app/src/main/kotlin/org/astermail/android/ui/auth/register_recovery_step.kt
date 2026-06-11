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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.astermail.android.R
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterSecondaryButton

@Composable
fun RegisterRecoveryStep(
    mnemonic: String,
    on_continue: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    val words = remember(mnemonic) { mnemonic.trim().split("\\s+".toRegex()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AsterSpacing.xxl),
    ) {
        Spacer(Modifier.height(AsterSpacing.sm))
        Text(
            text = stringResource(R.string.save_recovery_phrase),
            color = colors.text_primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recovery_phrase_description),
            color = colors.text_tertiary,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border_primary, SquircleShape(18.dp))
                .background(colors.bg_secondary, SquircleShape(18.dp))
                .clickable {
                    copy_recovery(context, mnemonic)
                    copied = true
                }
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val rows = words.chunked(2)
            rows.forEachIndexed { row_idx, pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.md),
                ) {
                    pair.forEachIndexed { col_idx, word ->
                        val number = row_idx * 2 + col_idx + 1
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "$number.",
                                color = colors.text_muted,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(28.dp),
                            )
                            Text(
                                text = word,
                                color = colors.text_primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(AsterSpacing.md))

        Text(
            text = if (copied) stringResource(R.string.copied_to_clipboard) else stringResource(R.string.tap_phrase_to_copy),
            color = if (copied) colors.accent_blue else colors.text_tertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        AsterButton(
            label = stringResource(R.string.copy_to_clipboard),
            onClick = {
                copy_recovery(context, mnemonic)
                copied = true
            },
        )

        Spacer(Modifier.height(AsterSpacing.md))

        AsterSecondaryButton(
            label = stringResource(R.string.continue_action),
            onClick = on_continue,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))
    }
}

private fun copy_recovery(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("recovery key", text)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard?.setPrimaryClip(clip)
}
