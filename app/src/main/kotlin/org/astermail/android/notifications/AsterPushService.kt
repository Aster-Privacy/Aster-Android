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

import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class AsterPushService : PushService() {

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            MailPollingWorker.enqueue_forced_notify(this)
            return
        }
        val result = runCatching {
            handle_push_payload(this, String(message.content, Charsets.UTF_8))
        }.getOrDefault(PushResult.NeedsFetch)
        if (result == PushResult.NeedsFetch) {
            MailPollingWorker.enqueue_forced_notify(this)
        }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val keys = endpoint.pubKeySet
        UnifiedPushState.save_endpoint(this, endpoint.url, keys?.pubKey, keys?.auth)
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
