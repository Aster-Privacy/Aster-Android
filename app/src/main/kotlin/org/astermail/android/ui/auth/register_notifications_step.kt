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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import org.astermail.android.R
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterSpacing
import org.astermail.android.design.SquircleShape
import org.astermail.android.design.components.AsterButton
import org.astermail.android.design.components.AsterGhostButton
import org.astermail.android.notifications.MailPollingWorker
import org.astermail.android.notifications.PersistentPushService

private fun mark_notification_permission_asked(context: Context) {
    context.getSharedPreferences("aster_perms", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("notif_perm_asked", true)
        .apply()
}

private fun enable_push(context: Context) {
    MailPollingWorker.set_push_enabled(context, true)
    PersistentPushService.start_if_enabled(context)
}

@Composable
fun RegisterNotificationsStep(
    on_done: () -> Unit,
) {
    val colors = AsterMaterial.colors
    val context = LocalContext.current
    val turned_on_message = stringResource(R.string.notifications_turned_on)
    val blocked_message = stringResource(R.string.notifications_blocked_hint)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            enable_push(context)
            Toast.makeText(context, turned_on_message, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, blocked_message, Toast.LENGTH_LONG).show()
        }
        on_done()
    }

    val turn_on: () -> Unit = {
        mark_notification_permission_asked(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                enable_push(context)
                Toast.makeText(context, turned_on_message, Toast.LENGTH_SHORT).show()
                on_done()
            } else {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            enable_push(context)
            Toast.makeText(context, turned_on_message, Toast.LENGTH_SHORT).show()
            on_done()
        }
    }

    auth_centered_column {
        Image(
            painter = painterResource(R.drawable.aster_wordmark),
            contentDescription = null,
            modifier = Modifier.height(40.dp),
        )

        Spacer(Modifier.height(AsterSpacing.xl))

        Text(
            text = stringResource(R.string.notifications_step_title),
            color = colors.text_primary,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(AsterSpacing.md))
        Text(
            text = stringResource(R.string.notifications_step_desc),
            color = colors.text_tertiary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(AsterSpacing.xxl))

        notification_preview_card()

        Spacer(Modifier.height(AsterSpacing.xxl))

        AsterButton(
            label = stringResource(R.string.turn_on),
            onClick = turn_on,
        )

        Spacer(Modifier.height(AsterSpacing.sm))

        AsterGhostButton(
            label = stringResource(R.string.skip_for_now),
            onClick = {
                mark_notification_permission_asked(context)
                on_done()
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun notification_preview_card() {
    val colors = AsterMaterial.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bg_card, SquircleShape(16.dp))
            .border(1.dp, colors.border_primary, SquircleShape(16.dp))
            .padding(AsterSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.aster_logo),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(SquircleShape(10.dp)),
        )
        Spacer(Modifier.width(AsterSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    color = colors.text_tertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.notification_preview_time),
                    color = colors.text_muted,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.notification_preview_title),
                color = colors.text_primary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.notification_preview_body),
                color = colors.text_secondary,
                fontSize = 13.sp,
            )
        }
    }
}
