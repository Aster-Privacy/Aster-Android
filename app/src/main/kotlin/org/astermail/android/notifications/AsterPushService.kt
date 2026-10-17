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
import android.os.PowerManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

class AsterPushService : PushService() {

    override fun onMessage(message: PushMessage, instance: String) {
        val app = applicationContext
        if (!message.decrypted) {
            MailPollingWorker.enqueue_forced_notify(app)
            return
        }
        val payload = String(message.content, Charsets.UTF_8)
        run_off_main(app, on_rejected = { MailPollingWorker.enqueue_forced_notify(app) }) {
            deliver_payload(app, payload)
        }
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val app = applicationContext
        val url = endpoint.url
        val keys = endpoint.pubKeySet
        val p256dh = keys?.pubKey
        val auth = keys?.auth
        run_off_main(app) {
            UnifiedPushState.save_endpoint(app, url, p256dh, auth)
            if (p256dh != null && auth != null) {
                UnifiedPushState.register_with_backend(
                    context = app,
                    endpoint_url = url,
                    p256dh = p256dh,
                    auth = auth,
                )
            }
        }
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        val app = applicationContext
        run_off_main(app) {
            if (reason == FailedReason.VAPID_REQUIRED) {
                UnifiedPushState.reregister_with_vapid(app)
            } else {
                UnifiedPushState.clear_endpoint(app)
            }
        }
    }

    override fun onUnregistered(instance: String) {
        val app = applicationContext
        run_off_main(app) { UnifiedPushState.clear_endpoint(app) }
    }

    private fun deliver_payload(context: Context, payload: String) {
        val result = runCatching { handle_push_payload(context, payload) }
            .getOrDefault(PushResult.NeedsFetch)
        if (result == PushResult.NeedsFetch) {
            runCatching { MailPollingWorker.enqueue_forced_notify(context) }
        }
    }

    private fun run_off_main(context: Context, on_rejected: () -> Unit = {}, block: () -> Unit) {
        val lock = acquire_wake_lock(context)
        val submitted = runCatching {
            push_executor.execute {
                try {
                    runCatching(block)
                } finally {
                    runCatching { if (lock?.isHeld == true) lock.release() }
                }
            }
        }.isSuccess
        if (!submitted) {
            runCatching { if (lock?.isHeld == true) lock.release() }
            runCatching(on_rejected)
        }
    }

    private fun acquire_wake_lock(context: Context): PowerManager.WakeLock? = runCatching {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, push_wake_lock_tag)?.apply {
            setReferenceCounted(false)
            acquire(push_wake_lock_timeout_ms)
        }
    }.getOrNull()

    companion object {
        private const val push_wake_lock_tag = "aster:unified_push"
        private const val push_wake_lock_timeout_ms = 60_000L
        private val push_executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "aster-unified-push").apply { isDaemon = true }
        }
    }
}
