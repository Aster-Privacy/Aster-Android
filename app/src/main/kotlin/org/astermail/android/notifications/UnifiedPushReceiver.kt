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
import org.astermail.android.mail.MailRepository
import org.json.JSONObject
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class UnifiedPushReceiver : MessagingReceiver() {

    override fun onMessage(context: Context, message: PushMessage, instance: String) {
        val handled = runCatching { handle_push(context, message) }.getOrDefault(false)
        if (!handled) {
            MailPollingWorker.show_generic(context, 1)
        }
    }

    private fun handle_push(context: Context, message: PushMessage): Boolean {
        val text = String(message.content, Charsets.UTF_8)
        val obj = JSONObject(text)
        if (obj.optString("type") != "new_mail") return false
        val item_id = obj.optString("item_id", "")
        val encrypted_envelope = obj.optString("encrypted_envelope", "").takeIf { it.isNotBlank() }
            ?: return false
        val envelope_nonce = obj.optString("envelope_nonce", "").takeIf { it.isNotBlank() }
        val repo = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MailPollingWorker.MailRepositoryEntryPoint::class.java,
            ).mail_repository()
        } catch (_: Throwable) {
            return false
        }
        val envelope = repo.decrypt_envelope_public(encrypted_envelope, envelope_nonce) ?: return false
        val sender = envelope.from_name.takeIf { it.isNotBlank() } ?: envelope.from_email
        val subject = envelope.subject
        if (sender.isBlank() || subject.isBlank()) return false
        val notification_id = if (item_id.isNotBlank()) {
            item_id.hashCode() and 0x7fffffff
        } else {
            (System.currentTimeMillis().toInt()) and 0x7fffffff
        }
        MailPollingWorker.show_message(
            context = context,
            sender = sender,
            subject = subject,
            preview = envelope.body_text,
            message_id = notification_id,
        )
        return true
    }

    override fun onNewEndpoint(context: Context, endpoint: PushEndpoint, instance: String) {
        UnifiedPushState.save_endpoint(context, endpoint.url)
        val keys = endpoint.pubKeySet
        if (keys != null) {
            UnifiedPushState.register_with_backend(
                context = context,
                endpoint_url = endpoint.url,
                p256dh = keys.pubKey,
                auth = keys.auth,
            )
        }
    }

    override fun onRegistrationFailed(
        context: Context,
        reason: org.unifiedpush.android.connector.FailedReason,
        instance: String,
    ) {
        UnifiedPushState.clear_endpoint(context)
    }

    override fun onUnregistered(context: Context, instance: String) {
        UnifiedPushState.clear_endpoint(context)
    }
}
