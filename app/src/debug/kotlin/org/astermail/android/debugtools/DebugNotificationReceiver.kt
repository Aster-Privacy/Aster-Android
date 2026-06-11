//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// Licensed under AGPL-3.0-or-later. See <https://www.gnu.org/licenses/>.
//

package org.astermail.android.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.astermail.android.notifications.MailPollingWorker

class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sender = intent.getStringExtra("sender")
        val subject = intent.getStringExtra("subject")
        val preview = intent.getStringExtra("preview")
        if (!sender.isNullOrBlank() && !subject.isNullOrBlank()) {
            val id = intent.getIntExtra("id", System.currentTimeMillis().toInt() and 0x7fffffff)
            MailPollingWorker.show_message(
                context = context,
                sender = sender,
                subject = subject,
                preview = preview.orEmpty(),
                message_id = id,
            )
            return
        }
        val count = intent.getIntExtra("count", 1).coerceAtLeast(1)
        MailPollingWorker.show_generic(context, count)
    }
}
