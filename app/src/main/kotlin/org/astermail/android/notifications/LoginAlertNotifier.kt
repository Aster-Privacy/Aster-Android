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
import kotlinx.coroutines.CancellationException
import org.astermail.android.MainActivity
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.settings.SettingsApi
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.guard_event
import org.astermail.android.guard.guard_local_approvals
import org.astermail.android.guard.guard_notification_spec_for
import org.astermail.android.guard.guard_open_target
import org.astermail.android.guard.guard_prompt_push
import org.astermail.android.guard.GUARD_PROMPT_DEFAULT_TIMEOUT_MS
import org.astermail.android.guard.guard_prompt_lookup
import org.astermail.android.guard.guard_is_own_device_event
import org.astermail.android.guard.guard_prompt_can_look_up
import org.astermail.android.guard.guard_prompt_should_post
import org.astermail.android.guard.guard_prompt_timeout_ms
import org.astermail.android.guard.guard_push
import org.astermail.android.guard.guard_should_show
import org.astermail.android.guard.guard_cancelled_push
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.TokenStore
import java.util.UUID
import java.util.concurrent.TimeUnit

enum class LoginAlertDecision { Show, Skip, Retry, GiveUp }

enum class RevokeDecision { Revoked, Retry, Failed }

private fun localized(context: Context): Context =
    org.astermail.android.settings.app_language.wrap(context)

object LoginAlertNotifier {
    const val CHANNEL_ID = "aster_login_alerts_v2"
    private const val LEGACY_CHANNEL_ID = "aster_login_alerts"
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
                ?: return
            create_alert_channel(manager, channel, vibrate = true, replaced_ids = listOf(LEGACY_CHANNEL_ID))
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
            putExtra(internal_launch_token.EXTRA_TOKEN, internal_launch_token.get(context))
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
            putExtra(internal_launch_token.EXTRA_TOKEN, internal_launch_token.get(context))
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

    const val GUARD_CHANNEL_ID = "aster_guard_v1"
    const val KEY_GUARD_EVENT = "guard_event"
    const val KEY_GUARD_ID = "guard_id"
    const val KEY_GUARD_ACCOUNT = "guard_account"
    const val GUARD_NOTIFICATION_ID = 1900
    private const val ACTION_SLOT_GUARD_OPEN = 1
    private const val ACTION_SLOT_GUARD_REVIEW = 2
    private const val ACTION_SLOT_GUARD_PANIC = 3

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface GuardAlertEntryPoint {
        fun guard_api(): GuardApi
        fun guard_device_store(): GuardDeviceStore
        fun account_store(): AccountStore
        fun token_store(): TokenStore
    }

    fun create_guard_channel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                GUARD_CHANNEL_ID,
                localized(context).getString(R.string.notif_channel_guard_name),
                NotificationManager.IMPORTANCE_HIGH,
            )
            channel.description = localized(context).getString(R.string.notif_channel_guard_description)
            channel.setShowBadge(true)
            channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            create_alert_channel(manager, channel, vibrate = true, replaced_ids = emptyList())
        }
    }

    fun guard_notification_tag(push: guard_push): String =
        "guard:${push.event.name}:${push.id.lowercase()}:${push.account_id.lowercase()}"

    fun guard_request_code(push: guard_push, slot: Int): Int = "${guard_notification_tag(push)}:$slot".hashCode()

    fun guard_work_name(push: guard_push): String = "guard_${push.event.name}_${push.id}"

    fun enqueue_guard(context: Context, push: guard_push) {
        guard_cancelled_push(push)?.let { cancel_guard_target(context, it) }
        if (!guard_should_show(push, approved_here = guard_local_approvals::approved_here)) return
        val request = OneTimeWorkRequestBuilder<GuardAlertWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(KEY_GUARD_EVENT, push.event.name)
                    .putString(KEY_GUARD_ID, push.id)
                    .putString(KEY_GUARD_ACCOUNT, push.account_id)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            guard_work_name(push),
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun guard_title_res(event: guard_event): Int? = when (event) {
        guard_event.sign_in_request -> R.string.notif_guard_sign_in_title
        guard_event.change_held -> R.string.notif_guard_change_held_title
        guard_event.rescue_started -> R.string.notif_guard_rescue_started_title
        else -> null
    }

    fun guard_body_res(event: guard_event): Int? = when (event) {
        guard_event.sign_in_request -> R.string.notif_guard_sign_in_body
        guard_event.change_held -> R.string.notif_guard_change_held_body
        guard_event.rescue_started -> R.string.notif_guard_rescue_started_body
        else -> null
    }

    fun guard_open_intent(context: Context, push: guard_push): Intent {
        val spec = guard_notification_spec_for(push.event)
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            when (spec.target) {
                guard_open_target.request -> putExtra(MainActivity.EXTRA_OPEN_GUARD_REQUEST, push.id)
                guard_open_target.changes -> putExtra(MainActivity.EXTRA_OPEN_GUARD_CHANGES, true)
                guard_open_target.guard_settings -> putExtra(MainActivity.EXTRA_OPEN_GUARD_SETTINGS, true)
                guard_open_target.app -> Unit
            }
            putExtra(MainActivity.EXTRA_GUARD_ACCOUNT_ID, push.account_id)
            putExtra(internal_launch_token.EXTRA_TOKEN, internal_launch_token.get(context))
        }
    }

    fun guard_panic_intent(context: Context, push: guard_push): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_GUARD_PANIC, true)
            putExtra(MainActivity.EXTRA_GUARD_ACCOUNT_ID, push.account_id)
            putExtra(internal_launch_token.EXTRA_TOKEN, internal_launch_token.get(context))
        }

    private fun guard_distinct(intent: Intent, push: guard_push, slot: Int): Intent = intent.apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) identifier = "${guard_notification_tag(push)}:$slot"
    }

    fun show_guard(
        context: Context,
        push: guard_push,
        timeout_ms: Long = GUARD_PROMPT_DEFAULT_TIMEOUT_MS,
    ) {
        val spec = guard_notification_spec_for(push.event)
        if (!spec.shows) return
        val title_res = guard_title_res(push.event) ?: return
        val body_res = guard_body_res(push.event) ?: return
        if (!can_post(context)) return
        create_guard_channel(context)
        val tag = guard_notification_tag(push)
        val ctx = localized(context)
        val pending = PendingIntent.getActivity(
            context, guard_request_code(push, ACTION_SLOT_GUARD_OPEN),
            guard_distinct(guard_open_intent(context, push), push, ACTION_SLOT_GUARD_OPEN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = ctx.getString(title_res)
        val body = ctx.getString(body_res)
        val builder = guard_builder(context)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
        if (push.event == guard_event.sign_in_request) builder.setTimeoutAfter(timeout_ms)
        if (spec.review_action) {
            val review_pending = PendingIntent.getActivity(
                context,
                guard_request_code(push, ACTION_SLOT_GUARD_REVIEW),
                guard_distinct(guard_open_intent(context, push), push, ACTION_SLOT_GUARD_REVIEW),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    ctx.getString(R.string.notif_guard_action_review),
                    review_pending,
                ).build(),
            )
        }
        if (spec.panic_action) {
            val panic_pending = PendingIntent.getActivity(
                context,
                guard_request_code(push, ACTION_SLOT_GUARD_PANIC),
                guard_distinct(guard_panic_intent(context, push), push, ACTION_SLOT_GUARD_PANIC),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(
                    R.drawable.ic_notification,
                    ctx.getString(R.string.notif_guard_action_panic),
                    panic_pending,
                ).build(),
            )
        }
        if (spec.private_details) {
            val public_version = guard_builder(context)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pending)
                .build()
            builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public_version)
        }
        NotificationManagerCompat.from(context).notify(tag, GUARD_NOTIFICATION_ID, builder.build())
    }

    fun cancel_guard(context: Context, push: guard_push) {
        NotificationManagerCompat.from(context).cancel(guard_notification_tag(push), GUARD_NOTIFICATION_ID)
    }

    fun cancel_guard_for_account(context: Context, account_id: String) {
        val suffix = ":${account_id.lowercase()}"
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        runCatching {
            manager.activeNotifications
                .filter { it.id == GUARD_NOTIFICATION_ID && guard_tag_matches_account(it.tag, suffix) }
                .forEach { manager.cancel(it.tag, it.id) }
        }
    }

    private fun guard_tag_matches_account(tag: String?, suffix: String): Boolean =
        tag != null && tag.startsWith("guard:") && tag.endsWith(suffix)

    fun cancel_guard_prompt(context: Context, push: guard_push) {
        cancel_guard_target(context, guard_prompt_push(push))
    }

    fun cancel_guard_target(context: Context, target: guard_push) {
        runCatching { WorkManager.getInstance(context).cancelUniqueWork(guard_work_name(target)) }
        cancel_guard(context, target)
    }

    private fun guard_builder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, GUARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_guard_notification)
            .setColor(0xFF3B82F6.toInt())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)

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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
            } catch (cancelled: CancellationException) {
                throw cancelled
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

class GuardAlertWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val event = inputData.getString(LoginAlertNotifier.KEY_GUARD_EVENT)
            ?.let { name -> guard_event.entries.firstOrNull { it.name == name } }
            ?: return Result.success()
        val account_id = inputData.getString(LoginAlertNotifier.KEY_GUARD_ACCOUNT).orEmpty()
        if (account_id.isBlank()) return Result.success()
        val push = guard_push(event, inputData.getString(LoginAlertNotifier.KEY_GUARD_ID).orEmpty(), account_id)
        if (!guard_should_show(push, approved_here = guard_local_approvals::approved_here)) return Result.success()
        val entry = runCatching {
            EntryPointAccessors.fromApplication(
                applicationContext,
                LoginAlertNotifier.GuardAlertEntryPoint::class.java,
            )
        }.getOrNull()
        if (event != guard_event.sign_in_request) {
            val own = runCatching {
                entry?.guard_device_store()?.device_id(UUID.fromString(push.account_id))
            }.getOrNull()
            if (guard_is_own_device_event(push, own)) return Result.success()
            LoginAlertNotifier.show_guard(applicationContext, push)
            return Result.success()
        }
        if (entry == null) return Result.success()
        val current = runCatching { entry.account_store().get_current_id() }.getOrNull()
        val (lookup, item) = if (guard_prompt_can_look_up(push.account_id, current)) {
            find_request(entry, push)
        } else {
            guard_prompt_lookup.unknown to null
        }
        val timeout = guard_prompt_timeout_ms(item?.expires_at, org.astermail.android.api.network.server_clock.now_ms())
        if (!guard_prompt_should_post(lookup, timeout)) {
            LoginAlertNotifier.cancel_guard(applicationContext, push)
            return Result.success()
        }
        LoginAlertNotifier.show_guard(applicationContext, push, timeout)
        return Result.success()
    }

    private suspend fun find_request(
        entry: LoginAlertNotifier.GuardAlertEntryPoint,
        push: guard_push,
    ): Pair<guard_prompt_lookup, org.astermail.android.api.guard.GuardRequestItem?> {
        if (entry.token_store().access_token == null) return guard_prompt_lookup.unknown to null
        return try {
            val item = entry.guard_api().requests().requests.firstOrNull { it.id.equals(push.id, ignoreCase = true) }
            if (item == null) guard_prompt_lookup.absent to null else guard_prompt_lookup.found to item
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            guard_prompt_lookup.unknown to null
        }
    }

}
