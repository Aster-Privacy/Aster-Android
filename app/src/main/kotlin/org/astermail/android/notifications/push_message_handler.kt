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

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import org.astermail.android.mail.safe_display_text
import org.json.JSONObject

enum class PushResult { Shown, NeedsFetch, Ignore }

fun handle_push_payload(context: Context, payload: String): PushResult {
    val obj = JSONObject(payload)
    val type = obj.optString("type")
    if (type == "test") {
        MailPollingWorker.show_generic(context, 1)
        return PushResult.Shown
    }
    if (type == "login_alert") {
        val session_id = obj.optString("session_id", "")
        if (session_id.isBlank()) return PushResult.Ignore
        LoginAlertNotifier.enqueue(
            context = context,
            session_id = session_id,
            device = obj.optString("device", ""),
            browser = obj.optString("browser", ""),
            location = obj.optString("location", ""),
            time = obj.optString("time", ""),
        )
        return PushResult.Shown
    }
    if (type != "new_mail" && type != "wake") return PushResult.Ignore
    runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MailPollingWorker.MailRepositoryEntryPoint::class.java,
        ).mail_repository().signal_new_mail()
    }
    if (!MailPollingWorker.is_notify_new_email(context)) return PushResult.Ignore
    val entry = try {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            MailPollingWorker.MailRepositoryEntryPoint::class.java,
        )
    } catch (_: Throwable) {
        return PushResult.NeedsFetch
    }
    val app_lock_configured = runCatching { entry.app_lock_store().is_configured() }.getOrDefault(true)
    if (org.astermail.android.security.LockdownStore.is_enabled(context) || app_lock_configured) {
        MailPollingWorker.show_generic(context, 1)
        return PushResult.Shown
    }
    if (type == "wake") return PushResult.NeedsFetch
    if (MailPollingWorker.muted_folder_tokens(context).isNotEmpty()) {
        return PushResult.NeedsFetch
    }
    val item_id = obj.optString("item_id", "")
    if (item_id.isBlank()) return PushResult.NeedsFetch
    if (MailPollingWorker.was_item_notified(context, item_id)) {
        return PushResult.Ignore
    }
    val encrypted_envelope = obj.optString("encrypted_envelope", "").takeIf { it.isNotBlank() }
        ?: return PushResult.NeedsFetch
    val envelope_nonce = obj.optString("envelope_nonce", "").takeIf { it.isNotBlank() }
    val repo = entry.mail_repository()
    val envelope = repo.decrypt_envelope_public(encrypted_envelope, envelope_nonce, item_id)
        ?: return PushResult.NeedsFetch
    if (envelope.is_undecryptable) return PushResult.NeedsFetch
    val forwarding = org.astermail.android.ui.mail.resolve_forwarding_display(
        envelope.from_email,
        envelope.raw_headers,
    )
    val sender = safe_display_text(
        forwarding?.display_sender_name
            ?: envelope.from_name.takeIf { it.isNotBlank() }
            ?: envelope.from_email,
    )
    val subject = safe_display_text(envelope.subject)
    if (sender.isBlank() || subject.isBlank()) return PushResult.NeedsFetch
    val notification_id = if (item_id.isNotBlank()) {
        MailPollingWorker.message_notification_id(item_id.hashCode())
    } else {
        MailPollingWorker.message_notification_id(System.currentTimeMillis().toInt())
    }
    if (item_id.isNotBlank()) {
        MailPollingWorker.mark_item_notified(context, item_id)
    }
    MailPollingWorker.show_message(
        context = context,
        sender = sender,
        subject = subject,
        preview = repo.notification_preview(envelope),
        message_id = notification_id,
        item_id = item_id,
    )
    return PushResult.Shown
}
