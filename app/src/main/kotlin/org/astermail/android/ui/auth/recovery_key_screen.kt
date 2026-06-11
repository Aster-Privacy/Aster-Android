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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
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
import org.astermail.android.design.components.AsterTopBar

@Composable
fun RecoveryKeyScreen(
    mnemonic: String,
    on_continue: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    var saved by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg_primary)
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            AsterTopBar(title = "", on_back = {})

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AsterSpacing.xxl),
            ) {
                Spacer(Modifier.height(AsterSpacing.sm))
                Text(
                    text = stringResource(R.string.save_recovery_key),
                    color = colors.text_primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.recovery_key_description),
                    color = colors.text_tertiary,
                    fontSize = 15.sp,
                )

                Spacer(Modifier.height(AsterSpacing.xxl))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = colors.border_primary,
                            shape = SquircleShape(18.dp),
                        )
                        .background(colors.bg_secondary, shape = SquircleShape(18.dp))
                        .padding(AsterSpacing.lg),
                ) {
                    Text(
                        text = mnemonic,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(Modifier.height(AsterSpacing.md))

                Text(
                    text = if (copied) stringResource(R.string.copied_to_clipboard) else stringResource(R.string.tap_to_copy),
                    color = if (copied) colors.accent_blue else colors.text_tertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable {
                        copy_to_clipboard(context, mnemonic)
                        copied = true
                    },
                )

                Spacer(Modifier.height(AsterSpacing.xl))

                Row(
                    modifier = Modifier.fillMaxWidth().clickable { saved = !saved },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val box_bg = if (saved) colors.accent_blue else Color.Transparent
                    val box_border = if (saved) colors.accent_blue else colors.border_primary
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(box_bg, shape = RoundedCornerShape(4.dp))
                            .border(1.5.dp, box_border, shape = RoundedCornerShape(4.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (saved) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                    Spacer(Modifier.size(AsterSpacing.md))
                    Text(
                        text = stringResource(R.string.saved_recovery_key_checkbox),
                        color = colors.text_secondary,
                        fontSize = 14.sp,
                    )
                }

                Spacer(Modifier.height(AsterSpacing.xxl))

                AsterButton(
                    label = stringResource(R.string.continue_action),
                    onClick = on_continue,
                    enabled = saved,
                )

                Spacer(Modifier.height(AsterSpacing.xxl))
            }
        }
    }
}

private fun copy_to_clipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("recovery key", text)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    clipboard?.setPrimaryClip(clip)
}

