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

package org.astermail.android.notifications

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class LoginAlertActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notification_id = intent.getIntExtra(LoginAlertNotifier.KEY_NOTIFICATION_ID, 0)
        if (notification_id == 0) return
        when (intent.action) {
            ACTION_DISMISS -> {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.cancel(notification_id)
            }
            ACTION_REVOKE -> {
                val session_id = intent.getStringExtra(LoginAlertNotifier.KEY_SESSION_ID).orEmpty()
                if (session_id.isBlank()) return
                LoginAlertNotifier.show_revoking(context, notification_id)
                LoginAlertRevokeWorker.enqueue(context, session_id, notification_id)
            }
        }
    }

    companion object {
        const val ACTION_DISMISS = "org.astermail.android.LOGIN_ALERT_DISMISS"
        const val ACTION_REVOKE = "org.astermail.android.LOGIN_ALERT_REVOKE"
    }
}
