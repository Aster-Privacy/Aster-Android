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

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.astermail.android.BuildConfig
import org.astermail.android.R
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.components.AsterCard
import org.astermail.android.design.components.AsterDivider
import org.astermail.android.design.components.AsterSecondaryButton

@Composable
fun DiagnosticsScreen(
    on_back: () -> Unit,
    on_open: (id: String) -> Unit = {},
) {
    val context = LocalContext.current
    val diag_text = buildString {
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Package: ${BuildConfig.APPLICATION_ID}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    val copied_text = stringResource(R.string.copied_to_clipboard)
    val clip_label = stringResource(R.string.settings_diagnostics_clip_label)

    detail_scaffold(title = stringResource(R.string.diagnostics), on_back = on_back) {
        AsterCard(modifier = Modifier.fillMaxWidth()) {
            detail_row(title = stringResource(R.string.app_version), subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            AsterDivider()
            detail_row(title = stringResource(R.string.package_name), subtitle = BuildConfig.APPLICATION_ID)
            AsterDivider()
            detail_row(title = stringResource(R.string.device), subtitle = "${Build.MANUFACTURER} ${Build.MODEL}")
            AsterDivider()
            detail_row(title = stringResource(R.string.android), subtitle = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
        v_gap(AsterSpacing.lg)
        AsterSecondaryButton(
            label = stringResource(R.string.copy_diagnostics),
            onClick = {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(clip_label, diag_text)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    clip.description.extras = extras
                }
                cm.setPrimaryClip(clip)
                Toast.makeText(context, copied_text, Toast.LENGTH_SHORT).show()
            },
        )
        v_gap(AsterSpacing.xxl)
    }
}
