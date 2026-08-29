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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
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
import java.util.concurrent.TimeUnit

enum class LoginAlertDecision { Show, Skip, Retry, GiveUp }

enum class RevokeDecision { Revoked, Retry, Failed }

private fun localized(context: Context): Context =
    org.astermail.android.settings.app_language.wrap(context)

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
    const val ALERT_MAX_ATTEMPTS = 5
    const val REVOKE_MAX_ATTEMPTS = 5
    private const val REVOKE_PROGRESS_TIMEOUT_MS = 15L * 60L * 1000L
    private const val ACTION_REQUEST_STRIDE = 8
    private const val ACTION_SLOT_DISMISS = 0
    private const val ACTION_SLOT_REVOKE = 1

    fun alert_decision(own_session: Boolean?, attempt: Int): LoginAlertDecision = when {
        own_session == true -> LoginAlertDecision.Skip
        own_session == false -> LoginAlertDecision.Show
        attempt + 1 < ALERT_MAX_ATTEMPTS -> LoginAlertDecision.Retry
        else -> LoginAlertDecision.GiveUp
    }

    fun revoke_decision(error: Throwable?, attempt: Int): RevokeDecision = when {
        error == null -> RevokeDecision.Revoked
        error is ApiError.NotFoundError -> RevokeDecision.Revoked
        error is ApiError.UnauthorizedError -> RevokeDecision.Failed
        error is ApiError.ForbiddenError -> RevokeDecision.Failed
        error is ApiError.ValidationError -> RevokeDecision.Failed
        attempt + 1 < REVOKE_MAX_ATTEMPTS -> RevokeDecision.Retry
        else -> RevokeDecision.Failed
    }

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
                localized(context).getString(R.string.notif_channel_login_alerts_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.description = localized(context).getString(R.string.notif_channel_login_alerts_description)
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
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
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

        val dismiss_action = build_action(
            context = context,
            notification_id = id,
            session_id = session_id,
            action = LoginAlertActionReceiver.ACTION_DISMISS,
            slot = ACTION_SLOT_DISMISS,
            icon = R.drawable.ic_action_mark_read,
            label = R.string.notif_login_alert_action_yes,
        )
        val revoke_action = build_action(
            context = context,
            notification_id = id,
            session_id = session_id,
            action = LoginAlertActionReceiver.ACTION_REVOKE,
            slot = ACTION_SLOT_REVOKE,
            icon = R.drawable.ic_action_revoke_session,
            label = R.string.notif_login_alert_action_revoke,
        )

        val public_version = base_builder(context)
            .setContentTitle(localized(context).getString(R.string.notif_login_alert_title))
            .setContentText(localized(context).getString(R.string.notif_login_alert_public_body))
            .setWhen(when_ms)
            .setShowWhen(true)
            .setContentIntent(content_pending)
            .addAction(dismiss_action)
            .addAction(revoke_action)
            .build()

        val summary = details.ifBlank {
            localized(context).getString(R.string.notif_login_alert_public_body)
        }
        val builder = base_builder(context)
            .setContentTitle(localized(context).getString(R.string.notif_login_alert_title))
            .setContentText(summary)
            .setSubText(localized(context).getString(R.string.notif_login_alert_inbox_hint))
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setWhen(when_ms)
            .setShowWhen(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(content_pending)
            .addAction(dismiss_action)
            .addAction(revoke_action)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public_version)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun show_revoking(context: Context, notification_id: Int) {
        if (!can_post(context)) return
        val notification = base_builder(context)
            .setContentTitle(localized(context).getString(R.string.notif_login_alert_revoking))
            .setProgress(0, 0, true)
            .setOngoing(false)
            .setAutoCancel(false)
            .setTimeoutAfter(REVOKE_PROGRESS_TIMEOUT_MS)
            .build()
        NotificationManagerCompat.from(context).notify(notification_id, notification)
    }

    fun show_revoke_result(context: Context, notification_id: Int, success: Boolean) {
        if (!can_post(context)) return
        val title = if (success) {
            localized(context).getString(R.string.notif_login_alert_revoked)
        } else {
            localized(context).getString(R.string.notif_login_alert_revoke_failed)
        }
        val body = if (success) {
            localized(context).getString(R.string.notif_login_alert_revoked_body)
        } else {
            localized(context).getString(R.string.notif_login_alert_revoke_failed_body)
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

    fun action_request_code(notification_id: Int, slot: Int): Int =
        notification_id * ACTION_REQUEST_STRIDE + slot

    private fun build_action(
        context: Context,
        notification_id: Int,
        session_id: String,
        action: String,
        slot: Int,
        icon: Int,
        label: Int,
    ): NotificationCompat.Action {
        val intent = Intent(context, LoginAlertActionReceiver::class.java).apply {
            this.action = action
            putExtra(KEY_SESSION_ID, session_id)
            putExtra(KEY_NOTIFICATION_ID, notification_id)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            action_request_code(notification_id, slot),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(icon, localized(context).getString(label), pending)
            .setShowsUserInterface(false)
            .setAuthenticationRequired(false)
            .build()
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
            null
        }

        return when (LoginAlertNotifier.alert_decision(own_session, runAttemptCount)) {
            LoginAlertDecision.Skip -> Result.success()
            LoginAlertDecision.Retry -> Result.retry()
            LoginAlertDecision.GiveUp -> Result.failure()
            LoginAlertDecision.Show -> {
                LoginAlertNotifier.show_alert(
                    context = applicationContext,
                    session_id = session_id,
                    device = device,
                    browser = browser,
                    location = location,
                    time = time,
                )
                Result.success()
            }
        }
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

        LoginAlertNotifier.show_revoking(applicationContext, notification_id)

        val entry = runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                LoginAlertNotifier.LoginAlertEntryPoint::class.java,
            )
        }.getOrNull()

        val error = if (entry == null) {
            IllegalStateException("revoke unavailable")
        } else {
            try {
                entry.settings_api().revoke_session(session_id)
                null
            } catch (t: Throwable) {
                t
            }
        }

        return when (LoginAlertNotifier.revoke_decision(error, runAttemptCount)) {
            RevokeDecision.Retry -> Result.retry()
            RevokeDecision.Revoked -> {
                LoginAlertNotifier.show_revoke_result(applicationContext, notification_id, true)
                Result.success()
            }
            RevokeDecision.Failed -> {
                LoginAlertNotifier.show_revoke_result(applicationContext, notification_id, false)
                Result.failure()
            }
        }
    }

    companion object {
        fun enqueue(context: Context, session_id: String, notification_id: Int) {
            val request = OneTimeWorkRequestBuilder<LoginAlertRevokeWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 20, TimeUnit.SECONDS)
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
