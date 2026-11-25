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

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import org.astermail.android.R
import org.astermail.android.ui.common.show_copy_failed_toast
import org.astermail.android.ui.common.system_confirms_copy
import org.astermail.android.ui.common.write_sensitive_to_clipboard

class MailNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_DISMISSED) {
            val dismissed_id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
            if (dismissed_id != 0) {
                runCatching { MailPollingWorker.on_notification_dismissed(context, dismissed_id) }
            }
            return
        }
        if (intent.action == ACTION_COPY_CODE) {
            if (gate(context, 0, posted_with_auth = true) != NotificationActionGate.Apply) return
            copy_code(context, intent.getStringExtra(EXTRA_CODE))
            return
        }
        val item_id = intent.getStringExtra(EXTRA_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return
        val action = when (intent.action) {
            ACTION_ARCHIVE -> MailNotificationActionWorker.ACTION_ARCHIVE
            ACTION_TRASH -> MailNotificationActionWorker.ACTION_TRASH
            ACTION_MARK_READ -> MailNotificationActionWorker.ACTION_MARK_READ
            ACTION_SPAM -> MailNotificationActionWorker.ACTION_SPAM
            else -> return
        }
        val notification_id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val posted_with_auth = runCatching {
            MailPollingWorker.actions_posted_with_auth(context, notification_id)
        }.getOrDefault(false)
        if (gate(context, notification_id, posted_with_auth) != NotificationActionGate.Apply) return
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

    private fun gate(context: Context, notification_id: Int, posted_with_auth: Boolean): NotificationActionGate {
        val device_locked = runCatching {
            (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceLocked
        }.getOrNull() ?: true
        val gate = NotificationActionAuthPolicy.gate(device_locked, posted_with_auth, Build.VERSION.SDK_INT)
        if (gate == NotificationActionGate.RequireUnlock) {
            runCatching { MailPollingWorker.require_unlock_for_actions(context, notification_id) }
        }
        if (gate != NotificationActionGate.Apply) {
            val local = org.astermail.android.settings.app_language.wrap(context)
            runCatching {
                Toast.makeText(context, local.getString(R.string.notif_action_unlock_required), Toast.LENGTH_SHORT).show()
            }
        }
        return gate
    }

    private fun copy_code(context: Context, code: String?) {
        if (code.isNullOrBlank()) return
        val local = org.astermail.android.settings.app_language.wrap(context)
        val copied = write_sensitive_to_clipboard(context, local.getString(R.string.app_name), code)
        if (!copied) {
            show_copy_failed_toast(local)
        } else if (!system_confirms_copy()) {
            Toast.makeText(context, local.getString(R.string.code_copied), Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val ACTION_ARCHIVE = "org.astermail.android.MAIL_NOTIFICATION_ARCHIVE"
        const val ACTION_TRASH = "org.astermail.android.MAIL_NOTIFICATION_TRASH"
        const val ACTION_MARK_READ = "org.astermail.android.MAIL_NOTIFICATION_MARK_READ"
        const val ACTION_SPAM = "org.astermail.android.MAIL_NOTIFICATION_SPAM"
        const val ACTION_DISMISSED = "org.astermail.android.MAIL_NOTIFICATION_DISMISSED"
        const val ACTION_COPY_CODE = "org.astermail.android.MAIL_NOTIFICATION_COPY_CODE"
        const val EXTRA_CODE = "mail_notification_code"
        const val EXTRA_ITEM_ID = "mail_notification_item_id"
        const val EXTRA_NOTIFICATION_ID = "mail_notification_id"
    }
}
