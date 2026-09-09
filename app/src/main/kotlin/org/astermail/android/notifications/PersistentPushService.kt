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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.astermail.android.MainActivity
import org.astermail.android.R
import org.astermail.android.api.BuildConfig
import org.astermail.android.api.auth.RefreshOutcome
import org.astermail.android.api.auth.SessionRefresher
import org.astermail.android.storage.TokenStore
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

private fun localized(context: Context): Context =
    org.astermail.android.settings.app_language.wrap(context)

fun websocket_url_for(base_url: String): String {
    val base = base_url.trim().trimEnd('/')
    val scheme_swapped = when {
        base.startsWith("https://", ignoreCase = true) -> "wss://" + base.substring(8)
        base.startsWith("http://", ignoreCase = true) -> "ws://" + base.substring(7)
        base.startsWith("wss://", ignoreCase = true) || base.startsWith("ws://", ignoreCase = true) -> base
        else -> "wss://" + base
    }
    return scheme_swapped + "/ws"
}

class PersistentPushService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SessionEntryPoint {
        fun session_refresher(): SessionRefresher
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val generation = AtomicLong(0L)

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var authenticated = false

    @Volatile
    private var had_connected = false

    private var connect_job: Job? = null

    private var reconnect_job: Job? = null

    private var heartbeat_job: Job? = null

    @Volatile
    private var reconnect_attempt = 0

    @Volatile
    private var last_refresh_attempt_ms = 0L

    private var network_callback: ConnectivityManager.NetworkCallback? = null

    private val ws_client: OkHttpClient by lazy {
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
            .pingInterval(Duration.ofSeconds(SOCKET_PING_SECONDS))
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        create_channel(this)
        runCatching { enter_foreground() }
        register_network_callback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            set_enabled(this, false)
            stop_cleanly()
            return START_NOT_STICKY
        }
        if (!is_enabled(this)) {
            stop_cleanly()
            return START_NOT_STICKY
        }
        runCatching { enter_foreground() }
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        unregister_network_callback()
        close_socket()
        heartbeat_job?.cancel()
        reconnect_job?.cancel()
        connect_job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stop_cleanly()
    }

    private fun enter_foreground() {
        val notification = build_notification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun build_notification(): android.app.Notification {
        val strings = localized(this)
        val open_intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val open_pending = PendingIntent.getActivity(
            this,
            FOREGROUND_NOTIFICATION_ID,
            open_intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop_pending = PendingIntent.getService(
            this,
            FOREGROUND_NOTIFICATION_ID + 1,
            Intent(this, PersistentPushService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3B82F6.toInt())
            .setContentTitle(strings.getString(R.string.notif_persistent_push_title))
            .setContentText(strings.getString(R.string.notif_persistent_push_body))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setShowWhen(false)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open_pending)
            .addAction(0, strings.getString(R.string.notif_persistent_push_stop), stop_pending)
            .build()
    }

    private fun register_network_callback() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!is_enabled(this@PersistentPushService)) return
                reconnect_attempt = 0
                if (!authenticated) connect()
            }
        }
        val registered = runCatching { manager.registerNetworkCallback(request, callback) }.isSuccess
        if (registered) network_callback = callback
    }

    private fun unregister_network_callback() {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = network_callback ?: return
        runCatching { manager.unregisterNetworkCallback(callback) }
        network_callback = null
    }

    private fun has_network(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val active = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(active) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun connect() {
        if (!is_enabled(this)) {
            stop_cleanly()
            return
        }
        reconnect_job?.cancel()
        connect_job?.cancel()
        connect_job = scope.launch {
            val token = runCatching { TokenStore(applicationContext).access_token }.getOrNull()
            if (token.isNullOrBlank()) {
                stop_cleanly()
                return@launch
            }
            if (!has_network()) {
                schedule_reconnect()
                return@launch
            }
            open_socket(token)
        }
    }

    private fun open_socket(token: String) {
        val marker = generation.incrementAndGet()
        close_socket()
        val request = Request.Builder().url(websocket_url()).build()
        socket = runCatching {
            ws_client.newWebSocket(request, AsterSocketListener(marker, token))
        }.getOrElse {
            schedule_reconnect()
            null
        }
    }

    private fun close_socket() {
        val current = socket
        socket = null
        authenticated = false
        heartbeat_job?.cancel()
        heartbeat_job = null
        runCatching { current?.close(NORMAL_CLOSURE, null) }
        runCatching { current?.cancel() }
    }

    private fun stop_cleanly() {
        close_socket()
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    private fun schedule_reconnect() {
        if (!is_enabled(this)) {
            stop_cleanly()
            return
        }
        reconnect_job?.cancel()
        val attempt = reconnect_attempt.coerceAtMost(MAX_BACKOFF_STEPS)
        reconnect_attempt = (reconnect_attempt + 1).coerceAtMost(MAX_BACKOFF_STEPS)
        val ceiling = (MIN_RECONNECT_DELAY_MS shl attempt).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        val wait = Random.nextLong(MIN_RECONNECT_DELAY_MS, ceiling + 1L)
        reconnect_job = scope.launch {
            delay(wait)
            if (isActive) connect()
        }
    }

    private fun start_heartbeat(marker: Long) {
        heartbeat_job?.cancel()
        heartbeat_job = scope.launch {
            while (isActive && generation.get() == marker) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (generation.get() != marker) return@launch
                val sent = runCatching { socket?.send(PING_FRAME) == true }.getOrDefault(false)
                if (!sent) return@launch
            }
        }
    }

    private fun deliver_new_mail() {
        val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val lock = runCatching {
            power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
        }.getOrNull()
        try {
            val result = runCatching { handle_push_payload(this, WAKE_PAYLOAD) }
                .getOrDefault(PushResult.NeedsFetch)
            if (result == PushResult.NeedsFetch) {
                runCatching { MailPollingWorker.enqueue_forced_notify(this) }
            }
        } finally {
            runCatching { if (lock?.isHeld == true) lock.release() }
        }
    }

    private inner class AsterSocketListener(
        private val marker: Long,
        private val token: String,
    ) : WebSocketListener() {

        private fun stale(): Boolean = generation.get() != marker

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (stale()) {
                runCatching { webSocket.cancel() }
                return
            }
            val payload = JSONObject()
                .put("type", "auth")
                .put("token", token)
                .toString()
            runCatching { webSocket.send(payload) }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (stale()) return
            if (text.length > MAX_INBOUND_MESSAGE_CHARS) return
            val type = runCatching { JSONObject(text).optString("type") }.getOrNull() ?: return
            when (type) {
                "auth_success" -> {
                    authenticated = true
                    reconnect_attempt = 0
                    start_heartbeat(marker)
                    if (had_connected) {
                        runCatching {
                            MailPollingWorker.enqueue_forced_notify(this@PersistentPushService)
                        }
                    }
                    had_connected = true
                }
                "auth_error" -> {
                    authenticated = false
                    runCatching { webSocket.close(NORMAL_CLOSURE, null) }
                    recover_session()
                }
                "session_revoked" -> stop_cleanly()
                "ping" -> runCatching { webSocket.send(PONG_FRAME) }
                "new_mail", "new_reaction" -> deliver_new_mail()
                else -> Unit
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (stale()) return
            authenticated = false
            heartbeat_job?.cancel()
            schedule_reconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (stale()) return
            authenticated = false
            heartbeat_job?.cancel()
            schedule_reconnect()
        }
    }

    private fun recover_session() {
        val now = System.currentTimeMillis()
        if (now - last_refresh_attempt_ms < REFRESH_COOLDOWN_MS) {
            schedule_reconnect()
            return
        }
        last_refresh_attempt_ms = now
        scope.launch {
            val refresher = runCatching {
                EntryPointAccessors
                    .fromApplication(applicationContext, SessionEntryPoint::class.java)
                    .session_refresher()
            }.getOrNull()
            val outcome = if (refresher == null) {
                RefreshOutcome.Transient
            } else {
                runCatching { refresher.refresh() }.getOrDefault(RefreshOutcome.Transient)
            }
            if (outcome == RefreshOutcome.AuthFailed) {
                stop_cleanly()
            } else {
                schedule_reconnect()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "aster_persistent_push"
        const val FOREGROUND_NOTIFICATION_ID = 1100
        const val ACTION_STOP = "org.astermail.android.action.STOP_PERSISTENT_PUSH"

        private const val PREFS_NAME = "persistent_push_prefs"
        private const val KEY_ENABLED = "persistent_push_enabled"
        private const val NORMAL_CLOSURE = 1000
        private const val WAKE_LOCK_TAG = "aster:persistent_push"
        private const val WAKE_LOCK_TIMEOUT_MS = 30_000L
        private const val MIN_RECONNECT_DELAY_MS = 5_000L
        private const val MAX_RECONNECT_DELAY_MS = 300_000L
        private const val MAX_BACKOFF_STEPS = 6
        private const val HEARTBEAT_INTERVAL_MS = 240_000L
        private const val REFRESH_COOLDOWN_MS = 60_000L
        private const val SOCKET_PING_SECONDS = 45L
        private const val MAX_INBOUND_MESSAGE_CHARS = 65_536
        private const val WAKE_PAYLOAD = "{\"type\":\"wake\"}"
        private const val PING_FRAME = "{\"type\":\"ping\"}"
        private const val PONG_FRAME = "{\"type\":\"pong\"}"

        fun websocket_url(): String = websocket_url_for(BuildConfig.API_BASE_URL)

        fun is_enabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)

        fun set_enabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
            if (enabled) start(context) else stop(context)
        }

        fun start_if_enabled(context: Context) {
            if (is_enabled(context)) start(context)
        }

        fun start(context: Context) {
            val intent = Intent(context.applicationContext, PersistentPushService::class.java)
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(
                    context.applicationContext,
                    intent,
                )
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context.applicationContext, PersistentPushService::class.java)
            runCatching { context.applicationContext.stopService(intent) }
        }

        fun create_channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                localized(context).getString(R.string.notif_channel_persistent_push_name),
                NotificationManager.IMPORTANCE_MIN,
            )
            channel.description = localized(context)
                .getString(R.string.notif_channel_persistent_push_description)
            channel.setShowBadge(false)
            channel.enableVibration(false)
            channel.setSound(null, null)
            manager.createNotificationChannel(channel)
        }
    }
}
