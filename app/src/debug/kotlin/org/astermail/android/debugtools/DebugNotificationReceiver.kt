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
//

package org.astermail.android.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.astermail.android.notifications.MailPollingWorker

class DebugNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("force_poll", false)) {
            MailPollingWorker.enqueue_forced_notify(context)
            return
        }
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
