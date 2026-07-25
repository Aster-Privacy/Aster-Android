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
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.astermail.android.MainActivity
import org.astermail.android.R
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.BuildConfig
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.mail.MailApiImpl
import org.astermail.android.mail.MailRepository
import org.astermail.android.storage.TokenStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.auth.providers.BearerTokens
import java.util.concurrent.TimeUnit

class MailPollingWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_WORK_START_MS, System.currentTimeMillis()).apply()
        if (org.astermail.android.BuildConfig.DEBUG) {
            val test_count = inputData.getInt(KEY_TEST_COUNT, 0)
            if (test_count > 0) {
                show_notification(test_count)
                return Result.success()
            }
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_PUSH_ENABLED, true)) return Result.success()

        val token_store = TokenStore(context)
        if (token_store.access_token == null) return Result.success()

        if (UnifiedPushState.has_pending_registration(context)) {
            UnifiedPushState.try_register(context)
        } else if (UnifiedPushState.subscription_stale(context)) {
            UnifiedPushState.refresh_backend_subscription(context)
        }

        if (inputData.getBoolean(KEY_FORCE_NOTIFY, false)) {
            notify_for_new_mail(1)
            return Result.success()
        }

        lateinit var client: ApiClient
        val token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? {
                val access = token_store.access_token ?: return null
                val refresh = token_store.refresh_token ?: access
                return BearerTokens(access, refresh)
            }
            override suspend fun refresh(): BearerTokens? {
                return try {
                    val current_refresh = token_store.refresh_token
                    val response = AuthApiImpl(client).refresh(current_refresh)
                    val new_refresh = response.refresh_token ?: current_refresh ?: response.access_token
                    token_store.save(response.access_token, new_refresh)
                    BearerTokens(response.access_token, new_refresh)
                } catch (t: Throwable) {
                    val is_definitive_auth_failure = t is ApiError.UnauthorizedError ||
                        t is ApiError.ForbiddenError
                    if (is_definitive_auth_failure) {
                        null
                    } else {
                        load()
                    }
                }
            }
            override suspend fun clear() {}
        }

        client = ApiClient(
            base_url = BuildConfig.API_BASE_URL,
            token_provider = token_provider,
        )
        try {
            return poll_and_notify(context, prefs, MailApiImpl(client))
        } finally {
            client.close()
        }
    }

    private suspend fun poll_and_notify(
        context: Context,
        prefs: android.content.SharedPreferences,
        mail_api: MailApiImpl,
    ): Result {
        val stats = try {
            kotlinx.coroutines.withTimeout(20_000L) { mail_api.get_stats() }
        } catch (_: ApiError.UnauthorizedError) {
            schedule_next(context)
            return Result.success()
        } catch (_: Throwable) {
            return Result.retry()
        }
        // `unread` is a mailbox-wide count (inbox + every custom folder/label), so it still
        // increases when a mail rule routes a new message straight into a folder other than
        // Inbox. `notifiable`/`inbox` are scoped to the primary inbox view and miss those
        // messages entirely, which used to mean rule-routed mail never triggered a notification.
        val new_unread = stats.unread
        val new_notifiable = stats.notifiable ?: stats.inbox

        val has_baseline = prefs.contains(KEY_CACHED_UNREAD)
        val cached_unread = prefs.getInt(KEY_CACHED_UNREAD, new_unread)
        val last_notified = prefs.getInt(KEY_LAST_NOTIFIED_COUNT, -1)

        val has_pending_new_mail = has_baseline && new_unread > cached_unread &&
            new_unread != last_notified
        val suppressed_by_quiet_hours = has_pending_new_mail && is_quiet_hours_now(context)
        if (has_pending_new_mail && !suppressed_by_quiet_hours) {
            val arrived = new_unread - cached_unread
            notify_for_new_mail(arrived)
            prefs.edit().putInt(KEY_LAST_NOTIFIED_COUNT, new_unread).apply()
        }

        val editor = prefs.edit().putInt(KEY_CACHED_NOTIFIABLE, new_notifiable)
        if (!suppressed_by_quiet_hours) {
            editor.putInt(KEY_CACHED_UNREAD, new_unread)
        }
        editor.apply()
        schedule_next(context)
        return Result.success()
    }

    private fun show_notification(unread_count: Int) {
        show_generic(context, unread_count)
    }

    private suspend fun notify_for_new_mail(arrived: Int) {
        if (!is_notify_new_email(context)) return
        val entry = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MailRepositoryEntryPoint::class.java,
            )
        } catch (_: Throwable) {
            null
        }
        val app_lock_configured = runCatching { entry?.app_lock_store()?.is_configured() ?: true }.getOrDefault(true)
        if (org.astermail.android.security.LockdownStore.is_enabled(context) || app_lock_configured) {
            show_generic(context, arrived)
            return
        }
        val repo = entry?.mail_repository()
        if (repo == null) {
            show_generic(context, arrived)
            return
        }
        var newest: org.astermail.android.mail.InboxItem? = null
        var sender: String? = null
        var fetched_any_page = false
        repeat(3) { attempt ->
            if (newest != null) return@repeat
            if (attempt > 0) kotlinx.coroutines.delay(1_500L)
            val page = try {
                kotlinx.coroutines.withTimeout(20_000L) {
                    repo.fetch_inbox(limit = arrived.coerceIn(1, 5))
                }.getOrNull()
            } catch (_: Throwable) { null }
            if (page != null) fetched_any_page = true
            var candidate = page?.items?.let { pick_notifiable_candidate(context, it) }

            if (candidate == null) {
                // Nothing new sitting in Inbox — the message may have been auto-filed into a
                // custom folder by a mail rule before the app ever saw it. Scan folders that
                // currently report unread mail so those messages still get notified.
                val folders = try {
                    kotlinx.coroutines.withTimeout(20_000L) {
                        repo.list_notifiable_folders()
                    }.getOrNull()
                } catch (_: Throwable) { null }
                if (folders != null) fetched_any_page = true
                for (folder in folders.orEmpty()) {
                    val folder_page = try {
                        kotlinx.coroutines.withTimeout(20_000L) {
                            repo.fetch_inbox(limit = arrived.coerceIn(1, 5), label_token = folder.label_token)
                        }.getOrNull()
                    } catch (_: Throwable) { null }
                    if (folder_page != null) fetched_any_page = true
                    candidate = folder_page?.items?.let { pick_notifiable_candidate(context, it) }
                    if (candidate != null) break
                }
            }

            val candidate_sender = (
                candidate?.display_sender_name
                    ?: candidate?.sender_name?.takeIf { it.isNotBlank() }
                    ?: candidate?.sender_email
                )?.trim()
            if (candidate != null && !candidate_sender.isNullOrBlank()) {
                newest = candidate
                sender = candidate_sender
            }
        }
        val fresh = newest
        if (fresh == null || sender.isNullOrBlank()) {
            if (!fetched_any_page) {
                show_generic(context, arrived)
            }
            return
        }
        val subject = fresh.subject.trim()
        val message_id = message_notification_id(fresh.id.hashCode())
        mark_item_notified(context, fresh.id)
        show_message(
            context = context,
            sender = sender!!,
            subject = subject,
            preview = fresh.preview,
            message_id = message_id,
            item_id = fresh.id,
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MailRepositoryEntryPoint {
        fun mail_repository(): MailRepository
        fun app_lock_store(): org.astermail.android.security.AppLockStore
    }

    companion object {
        const val CHANNEL_ID = "aster_new_mail"
        const val NOTIFICATION_ID = 1001
        const val SUMMARY_NOTIFICATION_ID = 1000
        const val GROUP_KEY_NEW_MAIL = "aster_new_mail_group"
        const val WORK_NAME = "mail_polling"
        const val WORK_NAME_CHAIN = "mail_polling_chain"
        const val WORK_NAME_IMMEDIATE = "mail_polling_immediate"
        const val KEY_TEST_COUNT = "test_count"
        const val KEY_FORCE_NOTIFY = "force_notify"
        private const val PREFS_NAME = "mail_polling_prefs"
        private const val KEY_CACHED_UNREAD = "cached_unread_count"
        private const val KEY_CACHED_NOTIFIABLE = "cached_notifiable_count"
        private const val KEY_PUSH_ENABLED = "push_notifications_enabled"
        private const val KEY_LAST_NOTIFIED_COUNT = "last_notified_notifiable_count"
        private const val KEY_NOTIFIED_ITEM_IDS = "notified_item_ids"
        private const val NOTIFIED_ITEM_IDS_MAX = 100
        private const val KEY_PRIVATE_NOTIFICATIONS = "private_notifications"
        private const val KEY_NOTIFY_NEW_EMAIL = "notify_new_email"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        private const val KEY_LAST_WORK_START_MS = "last_work_start_ms"
        private const val WORKER_IN_FLIGHT_GRACE_MS = 90_000L

        fun set_quiet_hours(context: Context, enabled: Boolean, start: String, end: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_QUIET_HOURS_ENABLED, enabled)
                .putString(KEY_QUIET_HOURS_START, start)
                .putString(KEY_QUIET_HOURS_END, end)
                .apply()
        }

        fun is_quiet_hours_now(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_QUIET_HOURS_ENABLED, false)) return false
            val start = prefs.getString(KEY_QUIET_HOURS_START, "22:00") ?: "22:00"
            val end = prefs.getString(KEY_QUIET_HOURS_END, "07:00") ?: "07:00"
            val start_min = parse_hhmm(start) ?: return false
            val end_min = parse_hhmm(end) ?: return false
            val cal = java.util.Calendar.getInstance()
            val now_min = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
            return if (start_min == end_min) false
            else if (start_min < end_min) now_min in start_min until end_min
            else now_min >= start_min || now_min < end_min
        }

        private fun parse_hhmm(value: String): Int? {
            val parts = value.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val m = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return h * 60 + m
        }

        fun create_channel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_new_mail_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                )
                channel.description = context.getString(R.string.notif_channel_new_mail_description)
                channel.setShowBadge(true)
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                manager?.createNotificationChannel(channel)
            }
        }

        fun is_private_notifications(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PRIVATE_NOTIFICATIONS, true)
        }

        fun set_private_notifications(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PRIVATE_NOTIFICATIONS, enabled)
                .apply()
        }

        fun is_notify_new_email(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFY_NEW_EMAIL, true)
        }

        fun set_notify_new_email(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIFY_NEW_EMAIL, enabled)
                .apply()
        }

        fun reset_new_mail_baseline(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CACHED_UNREAD)
                .remove(KEY_CACHED_NOTIFIABLE)
                .remove(KEY_LAST_NOTIFIED_COUNT)
                .remove(KEY_NOTIFIED_ITEM_IDS)
                .apply()
        }

        fun schedule_next(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_PUSH_ENABLED, true)) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val next = OneTimeWorkRequestBuilder<MailPollingWorker>()
                .setConstraints(constraints)
                .setInitialDelay(3, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_CHAIN,
                ExistingWorkPolicy.REPLACE,
                next,
            )
        }

        fun enqueue(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_PUSH_ENABLED, true)) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            // A worker that started very recently is very likely the reason this process is
            // alive at all (e.g. JobScheduler cold-starting the app to run the polling chain).
            // Re-enqueuing WORK_NAME_CHAIN here would race the in-flight run and can cancel it
            // before doWork() gets a chance to check for new mail, so skip it in that window;
            // doWork() reschedules itself via schedule_next() once it finishes either way.
            val worker_likely_in_flight =
                System.currentTimeMillis() - prefs.getLong(KEY_LAST_WORK_START_MS, 0L) < WORKER_IN_FLIGHT_GRACE_MS
            if (!worker_likely_in_flight) {
                val immediate = OneTimeWorkRequestBuilder<MailPollingWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    WORK_NAME_CHAIN,
                    ExistingWorkPolicy.KEEP,
                    immediate,
                )
            }
            val backup = PeriodicWorkRequestBuilder<MailPollingWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                backup,
            )
        }

        fun enqueue_forced_notify(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_PUSH_ENABLED, true)) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<MailPollingWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInputData(Data.Builder().putBoolean(KEY_FORCE_NOTIFY, true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueue_immediate(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(KEY_PUSH_ENABLED, true)) return
            val request = OneTimeWorkRequestBuilder<MailPollingWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun set_push_enabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PUSH_ENABLED, enabled)
                .apply()
            if (enabled) {
                enqueue(context)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_CHAIN)
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_IMMEDIATE)
            }
        }

        fun show_generic(context: Context, unread_count: Int) {
            if (!can_post(context)) return
            val text = context.resources.getQuantityString(
                R.plurals.new_mail_notification, unread_count, unread_count,
            )
            val notification = base_builder(context)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setGroup(GROUP_KEY_NEW_MAIL)
                .build()
            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIFICATION_ID, notification)
            post_group_summary(context, manager)
        }

        fun show_message(
            context: Context,
            sender: String,
            subject: String,
            preview: String,
            message_id: Int,
            item_id: String = "",
        ) {
            if (!can_post(context)) return
            val private_mode = is_private_notifications(context)
            val one_line_subject = subject.replace(Regex("\\s+"), " ").trim()
                .ifBlank { context.getString(R.string.notif_new_message) }
            val one_line_preview = preview.replace(Regex("\\s+"), " ").trim()
            val builder = base_builder(context)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP_KEY_NEW_MAIL)
                .setContentTitle(sender)
                .setContentText(one_line_subject)
            if (item_id.isNotBlank()) {
                val open_intent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_OPEN_EMAIL_ID, item_id)
                }
                builder.setContentIntent(
                    PendingIntent.getActivity(
                        context, message_id, open_intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            if (one_line_preview.isNotBlank()) {
                builder.setStyle(
                    NotificationCompat.InboxStyle()
                        .addLine(one_line_subject)
                        .addLine(one_line_preview),
                )
            }
            if (private_mode) {
                builder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                builder.setPublicVersion(
                    base_builder(context)
                        .setContentTitle(context.getString(R.string.app_name))
                        .setContentText(context.getString(R.string.notif_new_message))
                        .setGroup(GROUP_KEY_NEW_MAIL)
                        .build(),
                )
            } else {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            val manager = NotificationManagerCompat.from(context)
            manager.notify(message_id, builder.build())
            post_group_summary(context, manager)
        }

        private fun post_group_summary(context: Context, manager: NotificationManagerCompat) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val summary = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(app_large_icon(context))
                .setColor(0xFF3B82F6.toInt())
                .setContentTitle(context.getString(R.string.notif_group_summary_new_mail))
                .setGroup(GROUP_KEY_NEW_MAIL)
                .setGroupSummary(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
            manager.notify(SUMMARY_NOTIFICATION_ID, summary)
        }

        const val MESSAGE_ID_BASE = 2000

        fun message_notification_id(seed: Int): Int {
            return MESSAGE_ID_BASE + ((seed and 0x7fffffff) % 1_000_000)
        }

        fun pick_notifiable_candidate(
            context: Context,
            items: List<org.astermail.android.mail.InboxItem>,
        ): org.astermail.android.mail.InboxItem? {
            return items.firstOrNull { !it.is_read && !was_item_notified(context, it.id) }
        }

        fun was_item_notified(context: Context, item_id: String): Boolean {
            if (item_id.isBlank()) return false
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_NOTIFIED_ITEM_IDS, "") ?: ""
            return stored.split('\n').contains(item_id)
        }

        fun mark_item_notified(context: Context, item_id: String) {
            if (item_id.isBlank()) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_NOTIFIED_ITEM_IDS, "") ?: ""
            val ids = stored.split('\n')
                .filter { it.isNotBlank() && it != item_id }
                .toMutableList()
            ids.add(item_id)
            while (ids.size > NOTIFIED_ITEM_IDS_MAX) ids.removeAt(0)
            prefs.edit().putString(KEY_NOTIFIED_ITEM_IDS, ids.joinToString("\n")).apply()
        }

        fun cancel_message_notification(context: Context, item_id: String) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val cancelled = message_notification_id(item_id.hashCode())
            manager.cancel(cancelled)
            clear_summary_if_empty(manager, setOf(cancelled))
        }

        fun cancel_message_notifications(context: Context, item_ids: List<String>) {
            if (item_ids.isEmpty()) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val cancelled = item_ids.map { message_notification_id(it.hashCode()) }.toSet()
            cancelled.forEach { manager.cancel(it) }
            clear_summary_if_empty(manager, cancelled)
        }

        fun clear_all_mail_notifications(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.activeNotifications.forEach { active ->
                if (active.id >= MESSAGE_ID_BASE || active.id == SUMMARY_NOTIFICATION_ID || active.id == NOTIFICATION_ID) {
                    manager.cancel(active.id)
                }
            }
        }

        private fun clear_summary_if_empty(manager: NotificationManager, just_cancelled: Set<Int>) {
            val has_remaining_message = manager.activeNotifications.any {
                it.id >= MESSAGE_ID_BASE && it.id !in just_cancelled
            }
            if (!has_remaining_message) {
                manager.cancel(SUMMARY_NOTIFICATION_ID)
                manager.cancel(NOTIFICATION_ID)
            }
        }

        private fun can_post(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return ContextCompat.checkSelfPermission(
                    context, Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            }
            return true
        }

        private fun base_builder(context: Context): NotificationCompat.Builder {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(app_large_icon(context))
                .setColor(0xFF3B82F6.toInt())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setContentIntent(pending)
                .setAutoCancel(true)
        }

        private fun app_large_icon(context: Context) = runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round)
                ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()
    }
}
