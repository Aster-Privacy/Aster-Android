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

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.astermail.android.MainActivity
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.storage.TokenStore

object LoginAlertNotifier {
    const val CHANNEL_ID = "aster_login_alerts"
    const val KEY_SESSION_ID = "session_id"
    const val KEY_DEVICE = "device"
    const val KEY_BROWSER = "browser"
    const val KEY_LOCATION = "location"
    const val KEY_TIME = "time"
    const val KEY_NOTIFICATION_ID = "notification_id"
    private const val NOTIFICATION_ID_BASE = 1100
    private const val NOTIFICATION_ID_RANGE = 800

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LoginAlertEntryPoint {
        fun settings_api(): SettingsApi
        fun token_store(): TokenStore
    }

    fun create_channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_login_alerts_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.description = context.getString(R.string.notif_channel_login_alerts_description)
            channel.setShowBadge(true)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun notification_id(session_id: String): Int {
        return NOTIFICATION_ID_BASE + ((session_id.hashCode() and 0x7fffffff) % NOTIFICATION_ID_RANGE)
    }

    fun enqueue(
        context: Context,
        session_id: String,
        device: String,
        browser: String,
        location: String,
        time: String,
    ) {
        val request = OneTimeWorkRequestBuilder<LoginAlertWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                Data.Builder()
                    .putString(KEY_SESSION_ID, session_id)
                    .putString(KEY_DEVICE, device)
                    .putString(KEY_BROWSER, browser)
                    .putString(KEY_LOCATION, location)
                    .putString(KEY_TIME, time)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "login_alert_$session_id",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun can_post(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    fun show_alert(
        context: Context,
        session_id: String,
        device: String,
        browser: String,
        location: String,
        time: String,
    ) {
        if (!can_post(context)) return
        create_channel(context)
        val id = notification_id(session_id)
        val details = listOf(device, browser, location)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val when_ms = runCatching {
            java.time.OffsetDateTime.parse(time).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        val open_intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SESSIONS, true)
        }
        val content_pending = PendingIntent.getActivity(
            context, id, open_intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismiss_intent = Intent(context, LoginAlertActionReceiver::class.java).apply {
            action = LoginAlertActionReceiver.ACTION_DISMISS
            putExtra(KEY_NOTIFICATION_ID, id)
        }
        val dismiss_pending = PendingIntent.getBroadcast(
            context, id * 2, dismiss_intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val revoke_intent = Intent(context, LoginAlertActionReceiver::class.java).apply {
            action = LoginAlertActionReceiver.ACTION_REVOKE
            putExtra(KEY_SESSION_ID, session_id)
            putExtra(KEY_NOTIFICATION_ID, id)
        }
        val revoke_pending = PendingIntent.getBroadcast(
            context, id * 2 + 1, revoke_intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = base_builder(context)
            .setContentTitle(context.getString(R.string.notif_login_alert_title))
            .setContentText(details.ifBlank { context.getString(R.string.notif_login_alert_title) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setWhen(when_ms)
            .setShowWhen(true)
            .setContentIntent(content_pending)
            .addAction(0, context.getString(R.string.notif_login_alert_action_yes), dismiss_pending)
            .addAction(0, context.getString(R.string.notif_login_alert_action_revoke), revoke_pending)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                base_builder(context)
                    .setContentTitle(context.getString(R.string.notif_login_alert_title))
                    .setContentText(context.getString(R.string.notif_login_alert_public_body))
                    .build(),
            )
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun show_revoking(context: Context, notification_id: Int) {
        if (!can_post(context)) return
        val notification = base_builder(context)
            .setContentTitle(context.getString(R.string.notif_login_alert_revoking))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()
        NotificationManagerCompat.from(context).notify(notification_id, notification)
    }

    fun show_revoke_result(context: Context, notification_id: Int, success: Boolean) {
        if (!can_post(context)) return
        val title = if (success) {
            context.getString(R.string.notif_login_alert_revoked)
        } else {
            context.getString(R.string.notif_login_alert_revoke_failed)
        }
        val body = if (success) {
            context.getString(R.string.notif_login_alert_revoked_body)
        } else {
            context.getString(R.string.notif_login_alert_revoke_failed_body)
        }
        val open_intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_SESSIONS, true)
        }
        val pending = PendingIntent.getActivity(
            context, notification_id, open_intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = base_builder(context)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(notification_id, notification)
    }

    private fun base_builder(context: Context): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF3B82F6.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
    }
}

class LoginAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session_id = inputData.getString(LoginAlertNotifier.KEY_SESSION_ID).orEmpty()
        if (session_id.isBlank()) return Result.success()
        val device = inputData.getString(LoginAlertNotifier.KEY_DEVICE).orEmpty()
        val browser = inputData.getString(LoginAlertNotifier.KEY_BROWSER).orEmpty()
        val location = inputData.getString(LoginAlertNotifier.KEY_LOCATION).orEmpty()
        val time = inputData.getString(LoginAlertNotifier.KEY_TIME).orEmpty()

        val entry = runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                LoginAlertNotifier.LoginAlertEntryPoint::class.java,
            )
        }.getOrNull() ?: return Result.success()

        if (entry.token_store().access_token == null) return Result.success()

        val own_session = try {
            val sessions = entry.settings_api().list_sessions().sessions
            sessions.firstOrNull { it.id == session_id }?.is_current == true
        } catch (_: ApiError.UnauthorizedError) {
            return Result.success()
        } catch (_: Throwable) {
            false
        }
        if (own_session) return Result.success()

        LoginAlertNotifier.show_alert(
            context = applicationContext,
            session_id = session_id,
            device = device,
            browser = browser,
            location = location,
            time = time,
        )
        return Result.success()
    }
}

class LoginAlertRevokeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session_id = inputData.getString(LoginAlertNotifier.KEY_SESSION_ID).orEmpty()
        val notification_id = inputData.getInt(LoginAlertNotifier.KEY_NOTIFICATION_ID, 0)
        if (session_id.isBlank() || notification_id == 0) return Result.success()

        val entry = runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                LoginAlertNotifier.LoginAlertEntryPoint::class.java,
            )
        }.getOrNull()
        if (entry == null) {
            LoginAlertNotifier.show_revoke_result(applicationContext, notification_id, false)
            return Result.success()
        }

        val success = try {
            entry.settings_api().revoke_session(session_id)
            true
        } catch (_: Throwable) {
            false
        }
        LoginAlertNotifier.show_revoke_result(applicationContext, notification_id, success)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context, session_id: String, notification_id: Int) {
            val request = OneTimeWorkRequestBuilder<LoginAlertRevokeWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(
                    Data.Builder()
                        .putString(LoginAlertNotifier.KEY_SESSION_ID, session_id)
                        .putInt(LoginAlertNotifier.KEY_NOTIFICATION_ID, notification_id)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "login_alert_revoke_$session_id",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
