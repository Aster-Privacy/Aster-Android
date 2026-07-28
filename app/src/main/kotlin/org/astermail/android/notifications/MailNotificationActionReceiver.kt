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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MailNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val item_id = intent.getStringExtra(EXTRA_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return
        val action = when (intent.action) {
            ACTION_ARCHIVE -> MailNotificationActionWorker.ACTION_ARCHIVE
            ACTION_TRASH -> MailNotificationActionWorker.ACTION_TRASH
            ACTION_MARK_READ -> MailNotificationActionWorker.ACTION_MARK_READ
            else -> return
        }
        val notification_id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        runCatching {
            if (notification_id != 0) {
                MailPollingWorker.cancel_notification_id(context, notification_id)
            } else {
                MailPollingWorker.cancel_message_notification(context, item_id)
            }
        }
        runCatching { MailPollingWorker.mark_item_notified(context, item_id) }
        MailNotificationActionWorker.enqueue(context, item_id, action)
    }

    companion object {
        const val ACTION_ARCHIVE = "org.astermail.android.MAIL_NOTIFICATION_ARCHIVE"
        const val ACTION_TRASH = "org.astermail.android.MAIL_NOTIFICATION_TRASH"
        const val ACTION_MARK_READ = "org.astermail.android.MAIL_NOTIFICATION_MARK_READ"
        const val EXTRA_ITEM_ID = "mail_notification_item_id"
        const val EXTRA_NOTIFICATION_ID = "mail_notification_id"
    }
}
