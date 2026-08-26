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
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.write_to_clipboard
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
    codes: List<String>,
    backup_failed: Boolean = false,
    is_retrying_backup: Boolean = false,
    on_retry_backup: () -> Unit = {},
    on_continue: () -> Unit,
) {
    org.astermail.android.ui.common.secure_screen()
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val request_storage_access = org.astermail.android.util.remember_downloads_permission_gate()
    val copied_message = stringResource(R.string.copied_to_clipboard)
    val saved_message = stringResource(R.string.saved_file, RECOVERY_CODES_FILE_NAME)
    val failed_message = stringResource(R.string.failed_to_save)

    auth_centered_column {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.new_recovery_codes_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.new_recovery_codes_message),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, colors.border_primary, SquircleShape(18.dp))
                .background(colors.bg_secondary, SquircleShape(18.dp))
                .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            codes.forEachIndexed { index, code ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
                ) {
                    Text(
                        text = "${index + 1}.",
                        color = colors.text_muted,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(
                        text = code,
                        color = colors.text_primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        if (backup_failed) {
            Spacer(Modifier.height(AsterSpacing.lg))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.danger, SquircleShape(18.dp))
                    .background(colors.bg_secondary, SquircleShape(18.dp))
                    .padding(horizontal = AsterSpacing.lg, vertical = AsterSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AsterSpacing.sm),
            ) {
                Text(
                    text = stringResource(R.string.recovery_backup_not_saved),
                    color = colors.danger,
                    fontSize = 13.sp,
                )
                AsterSecondaryButton(
                    label = stringResource(R.string.retry),
                    onClick = on_retry_backup,
                    is_loading = is_retrying_backup,
                )
            }
        }

        Spacer(Modifier.height(AsterSpacing.xl))

        AsterButton(
            label = stringResource(R.string.copy_to_clipboard),
            onClick = {
                if (copy_recovery_codes(context, codes)) {
                    Toast.makeText(context, copied_message, Toast.LENGTH_SHORT).show()
                } else {
                    show_copy_failed_toast(context)
                }
            },
        )

        Spacer(Modifier.height(AsterSpacing.md))

        AsterSecondaryButton(
            label = stringResource(R.string.download),
            onClick = {
                request_storage_access {
                    val saved = download_recovery_codes(context, codes)
                    Toast.makeText(context, if (saved) saved_message else failed_message, Toast.LENGTH_SHORT).show()
                }
            },
        )

        Spacer(Modifier.height(AsterSpacing.md))

        AsterSecondaryButton(
            label = stringResource(R.string.continue_action),
            onClick = on_continue,
        )
    }
}

private const val RECOVERY_CODES_FILE_NAME = "aster-recovery-codes.txt"

private fun copy_recovery_codes(context: Context, codes: List<String>): Boolean {
    val text = codes.joinToString("\n")
    val clip = ClipData.newPlainText("recovery codes", text)
    clip.description.extras = android.os.PersistableBundle().apply {
        putBoolean("android.content.extra.IS_SENSITIVE", true)
    }
    if (!write_to_clipboard(context, clip)) return false
    org.astermail.android.util.schedule_sensitive_clipboard_clear(context, text)
    return true
}

private fun download_recovery_codes(context: Context, codes: List<String>): Boolean {
    val bytes = codes.joinToString("\n").toByteArray()
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, RECOVERY_CODES_FILE_NAME)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                true
            } else {
                false
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val file = java.io.File(dir, RECOVERY_CODES_FILE_NAME)
            if (!file.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)) {
                return false
            }
            file.writeBytes(bytes)
            true
        }
    } catch (_: Throwable) {
        false
    }
}
