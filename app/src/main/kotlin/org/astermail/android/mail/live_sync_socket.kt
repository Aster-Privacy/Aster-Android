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

package org.astermail.android.mail

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.astermail.android.api.BuildConfig
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.astermail.android.notifications.websocket_url_for
import org.astermail.android.storage.TokenStore
import org.json.JSONObject
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

enum class LiveSyncEvent { connected, mail_changed, new_mail }

enum class LiveSyncFrame { auth_success, auth_error, session_revoked, ping, mail_mutation, new_mail }

fun parse_live_sync_frame(text: String): LiveSyncFrame? {
    if (text.length > LIVE_SYNC_MAX_FRAME_CHARS) return null
    val type = runCatching { JSONObject(text).optString("type") }.getOrNull() ?: return null
    return when (type) {
        "auth_success" -> LiveSyncFrame.auth_success
        "auth_error" -> LiveSyncFrame.auth_error
        "session_revoked" -> LiveSyncFrame.session_revoked
        "ping" -> LiveSyncFrame.ping
        "mail_mutation" -> LiveSyncFrame.mail_mutation
        "new_mail", "new_reaction" -> LiveSyncFrame.new_mail
        else -> null
    }
}

private const val LIVE_SYNC_MAX_FRAME_CHARS = 65_536

@Singleton
class LiveSyncSocket @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session_refresher: SessionRefresher,
) {

    private enum class SessionEnd { closed, auth_failed, revoked }

    private val client: OkHttpClient by lazy {
        val specs = if (BuildConfig.API_BASE_URL.startsWith("http://")) {
            listOf(ConnectionSpec.CLEARTEXT, ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
        } else {
            listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS)
        }
        OkHttpClient.Builder()
            .connectionSpecs(specs)
            .retryOnConnectionFailure(true)
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ZERO)
            .writeTimeout(Duration.ofSeconds(15))
            .pingInterval(Duration.ofSeconds(PING_SECONDS))
            .build()
    }

    suspend fun run(on_event: (LiveSyncEvent) -> Unit) {
        var attempt = 0
        var last_refresh_ms = 0L
        while (currentCoroutineContext().isActive) {
            val token = runCatching { TokenStore(context).access_token }.getOrNull()
            if (token.isNullOrBlank()) return
            var authenticated = false
            val end = session(token) { event ->
                if (event == LiveSyncEvent.connected) authenticated = true
                on_event(event)
            }
            when (end) {
                SessionEnd.revoked -> return
                SessionEnd.auth_failed -> {
                    val now = System.currentTimeMillis()
                    if (now - last_refresh_ms >= REFRESH_COOLDOWN_MS) {
                        last_refresh_ms = now
                        val outcome = runCatching { session_refresher.refresh() }
                            .getOrDefault(RefreshOutcome.Transient)
                        if (outcome == RefreshOutcome.AuthFailed) return
                        if (outcome == RefreshOutcome.Success) continue
                    }
                }
                SessionEnd.closed -> Unit
            }
            if (authenticated) attempt = 0
            val ceiling = (MIN_RECONNECT_MS shl attempt.coerceAtMost(MAX_BACKOFF_STEPS))
                .coerceAtMost(MAX_RECONNECT_MS)
            attempt += 1
            delay(Random.nextLong(MIN_RECONNECT_MS, ceiling + 1L))
        }
    }

    private suspend fun session(token: String, on_event: (LiveSyncEvent) -> Unit): SessionEnd {
        val frames = Channel<String>(Channel.BUFFERED)
        val request = Request.Builder().url(websocket_url_for(BuildConfig.API_BASE_URL)).build()
        val socket = runCatching {
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        val auth = JSONObject().put("type", "auth").put("token", token).toString()
                        runCatching { webSocket.send(auth) }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        frames.trySend(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        frames.close()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        frames.close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        frames.close()
                    }
                },
            )
        }.getOrNull() ?: return SessionEnd.closed
        try {
            for (text in frames) {
                when (parse_live_sync_frame(text)) {
                    LiveSyncFrame.auth_success -> on_event(LiveSyncEvent.connected)
                    LiveSyncFrame.auth_error -> return SessionEnd.auth_failed
                    LiveSyncFrame.session_revoked -> return SessionEnd.revoked
                    LiveSyncFrame.ping -> runCatching { socket.send(PONG_FRAME) }
                    LiveSyncFrame.mail_mutation -> on_event(LiveSyncEvent.mail_changed)
                    LiveSyncFrame.new_mail -> on_event(LiveSyncEvent.new_mail)
                    null -> Unit
                }
            }
            return SessionEnd.closed
        } finally {
            runCatching { socket.close(NORMAL_CLOSURE, null) }
            runCatching { socket.cancel() }
        }
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val PING_SECONDS = 30L
        const val MIN_RECONNECT_MS = 2_000L
        const val MAX_RECONNECT_MS = 60_000L
        const val MAX_BACKOFF_STEPS = 5
        const val REFRESH_COOLDOWN_MS = 60_000L
        const val PONG_FRAME = "{\"type\":\"pong\"}"
    }
}
