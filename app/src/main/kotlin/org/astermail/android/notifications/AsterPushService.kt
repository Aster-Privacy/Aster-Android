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
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class AsterPushService : PushService() {

    private enum class PushResult { Shown, NeedsFetch, Ignore }

    override fun onMessage(message: PushMessage, instance: String) {
        val result = runCatching { handle_push(this, message) }.getOrDefault(PushResult.NeedsFetch)
        if (result == PushResult.NeedsFetch) {
            MailPollingWorker.enqueue_forced_notify(this)
        }
    }

    private fun handle_push(context: Context, message: PushMessage): PushResult {
        val text = String(message.content, Charsets.UTF_8)
        val obj = JSONObject(text)
        val type = obj.optString("type")
        if (type == "test") {
            MailPollingWorker.show_generic(context, 1)
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
        val app_lock_configured = runCatching { entry.app_lock_store().is_configured() }.getOrNull() == true
        if (org.astermail.android.security.LockdownStore.is_enabled(context) || app_lock_configured) {
            MailPollingWorker.show_generic(context, 1)
            return PushResult.Shown
        }
        if (type == "wake") return PushResult.NeedsFetch
        val item_id = obj.optString("item_id", "")
        if (item_id.isNotBlank() && MailPollingWorker.was_item_notified(context, item_id)) {
            return PushResult.Ignore
        }
        val encrypted_envelope = obj.optString("encrypted_envelope", "").takeIf { it.isNotBlank() }
            ?: return PushResult.NeedsFetch
        val envelope_nonce = obj.optString("envelope_nonce", "").takeIf { it.isNotBlank() }
        val repo = entry.mail_repository()
        val envelope = repo.decrypt_envelope_public(encrypted_envelope, envelope_nonce)
            ?: return PushResult.NeedsFetch
        val sender = envelope.from_name.takeIf { it.isNotBlank() } ?: envelope.from_email
        val subject = envelope.subject
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
            preview = envelope.body_text,
            message_id = notification_id,
        )
        return PushResult.Shown
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        UnifiedPushState.save_endpoint(this, endpoint.url)
        val keys = endpoint.pubKeySet
        if (keys != null) {
            UnifiedPushState.register_with_backend(
                context = this,
                endpoint_url = endpoint.url,
                p256dh = keys.pubKey,
                auth = keys.auth,
            )
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        if (reason == FailedReason.VAPID_REQUIRED) {
            UnifiedPushState.reregister_with_vapid(this)
        } else {
            UnifiedPushState.clear_endpoint(this)
        }
    }

    override fun onUnregistered(instance: String) {
        UnifiedPushState.clear_endpoint(this)
    }
}
