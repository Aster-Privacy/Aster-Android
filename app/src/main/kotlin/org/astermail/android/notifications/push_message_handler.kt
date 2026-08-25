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

private const val max_alert_field_length = 96

private fun sanitize_alert_field(raw: String): String {
    val collapsed = raw
        .filter { it.code in 0x20..0x7e || it.code > 0xa0 }
        .replace(Regex("\\s+"), " ")
        .trim()
    return if (collapsed.length > max_alert_field_length) {
        collapsed.take(max_alert_field_length)
    } else {
        collapsed
    }
}

fun handle_push_payload(context: Context, payload: String): PushResult {
    val obj = JSONObject(payload)
    val type = obj.optString("type")
    if (type == "test") {
        return if (MailPollingWorker.show_generic(context, 1)) {
            PushResult.Shown
        } else {
            PushResult.Ignore
        }
    }
    if (type == "login_alert") {
        val session_id = obj.optString("session_id", "")
        if (session_id.isBlank()) return PushResult.Ignore
        LoginAlertNotifier.enqueue(
            context = context,
            session_id = session_id,
            device = sanitize_alert_field(obj.optString("device", "")),
            browser = sanitize_alert_field(obj.optString("browser", "")),
            location = sanitize_alert_field(obj.optString("location", "")),
            time = sanitize_alert_field(obj.optString("time", "")),
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
        val locked_item_id = obj.optString("item_id", "")
        if (locked_item_id.isBlank()) {
            return if (MailPollingWorker.show_generic(context, 1)) {
                PushResult.Shown
            } else {
                PushResult.Ignore
            }
        }
        if (!MailPollingWorker.claim_item_notification(context, locked_item_id)) {
            return PushResult.Ignore
        }
        if (!MailPollingWorker.show_generic_for_item(context, locked_item_id)) {
            MailPollingWorker.release_item_claim(context, locked_item_id)
            return PushResult.Ignore
        }
        return PushResult.Shown
    }
    if (type == "wake") return PushResult.NeedsFetch
    if (!MailPollingWorker.protected_folder_state_known(context)) {
        return PushResult.NeedsFetch
    }
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
    if (!MailPollingWorker.claim_item_notification(context, item_id)) {
        return PushResult.Ignore
    }
    val posted = MailPollingWorker.show_message(
        context = context,
        sender = sender,
        subject = subject,
        preview = repo.notification_preview(envelope),
        message_id = notification_id,
        item_id = item_id,
    )
    if (!posted) {
        MailPollingWorker.release_item_claim(context, item_id)
        return PushResult.NeedsFetch
    }
    return PushResult.Shown
}
