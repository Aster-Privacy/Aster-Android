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

package org.astermail.android.ui.upgrade

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.astermail.android.R
import org.astermail.android.api.ClientUpdateNotice
import org.astermail.android.design.components.AsterAlertDialog
import org.astermail.android.ui.mail.onboarding_download_url

fun open_app_update(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(market) }.isSuccess
    if (opened) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(onboarding_download_url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
fun ClientUpdatePrompt() {
    val required by ClientUpdateNotice.required.collectAsStateWithLifecycle()
    if (!required) return
    val context = LocalContext.current
    AsterAlertDialog(
        on_dismiss = { ClientUpdateNotice.dismiss() },
        title = stringResource(R.string.client_update_title),
        message = stringResource(R.string.client_update_body),
        confirm_label = stringResource(R.string.client_update_action),
        cancel_label = stringResource(R.string.client_update_later),
        on_confirm = {
            ClientUpdateNotice.dismiss()
            open_app_update(context)
        },
    )
}
