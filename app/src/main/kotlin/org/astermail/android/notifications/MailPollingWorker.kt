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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
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

        lateinit var client: ApiClient
        val token_provider = object : TokenProvider {
            override suspend fun load(): BearerTokens? {
                val access = token_store.access_token ?: return null
                val refresh = token_store.refresh_token ?: access
                return BearerTokens(access, refresh)
            }
            override suspend fun refresh(): BearerTokens? {
                return try {
                    val response = AuthApiImpl(client).refresh()
                    val existing_refresh = token_store.refresh_token ?: response.access_token
                    token_store.save(response.access_token, existing_refresh)
                    BearerTokens(response.access_token, existing_refresh)
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
        val mail_api = MailApiImpl(client)

        val stats = try {
            kotlinx.coroutines.withTimeout(20_000L) { mail_api.get_stats() }
        } catch (_: ApiError.UnauthorizedError) {
            return Result.success()
        } catch (_: Throwable) {
            return Result.retry()
        }
        val new_unread = stats.unread
        val new_inbox = stats.inbox

        val has_baseline = prefs.contains(KEY_CACHED_INBOX)
        val cached_inbox = prefs.getInt(KEY_CACHED_INBOX, new_inbox)
        val last_notified_inbox = prefs.getInt(KEY_LAST_NOTIFIED_INBOX, -1)

        if (has_baseline && new_inbox > cached_inbox && new_unread > 0 && new_inbox != last_notified_inbox) {
            if (!is_quiet_hours_now(context)) {
                val arrived = (new_inbox - cached_inbox).coerceAtMost(new_unread)
                notify_for_new_mail(arrived)
                prefs.edit().putInt(KEY_LAST_NOTIFIED_INBOX, new_inbox).apply()
            }
        }

        prefs.edit()
            .putInt(KEY_CACHED_UNREAD, new_unread)
            .putInt(KEY_CACHED_INBOX, new_inbox)
            .apply()
        return Result.success()
    }

    private fun show_notification(unread_count: Int) {
        show_generic(context, unread_count)
    }

    private suspend fun notify_for_new_mail(arrived: Int) {
        val repo = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MailRepositoryEntryPoint::class.java,
            ).mail_repository()
        } catch (_: Throwable) {
            null
        }
        if (repo == null) {
            show_generic(context, arrived)
            return
        }
        val page = try {
            kotlinx.coroutines.withTimeout(20_000L) {
                repo.fetch_inbox(limit = arrived.coerceAtMost(5))
            }.getOrNull()
        } catch (_: Throwable) { null }
        val newest = page?.items?.firstOrNull { !it.is_read }
            ?: page?.items?.firstOrNull()
        val sender = (newest?.sender_name?.takeIf { it.isNotBlank() } ?: newest?.sender_email)?.trim()
        val subject = newest?.subject?.takeIf { it.isNotBlank() }
        if (sender.isNullOrBlank() || subject.isNullOrBlank()) {
            show_generic(context, arrived)
            return
        }
        val message_id = newest.id.hashCode() and 0x7fffffff
        show_message(
            context = context,
            sender = sender,
            subject = subject,
            preview = newest.preview,
            message_id = message_id,
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MailRepositoryEntryPoint {
        fun mail_repository(): MailRepository
    }

    companion object {
        const val CHANNEL_ID = "aster_new_mail"
        const val NOTIFICATION_ID = 1001
        const val SUMMARY_NOTIFICATION_ID = 1000
        const val GROUP_KEY_NEW_MAIL = "aster_new_mail_group"
        const val WORK_NAME = "mail_polling"
        const val KEY_TEST_COUNT = "test_count"
        private const val PREFS_NAME = "mail_polling_prefs"
        private const val KEY_CACHED_UNREAD = "cached_unread_count"
        private const val KEY_CACHED_INBOX = "cached_inbox_count"
        private const val KEY_PUSH_ENABLED = "push_notifications_enabled"
        private const val KEY_LAST_NOTIFIED_INBOX = "last_notified_inbox_count"
        private const val KEY_PRIVATE_NOTIFICATIONS = "private_notifications"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"

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

        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<MailPollingWorker>(
                15, TimeUnit.MINUTES,
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
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
        ) {
            if (!can_post(context)) return
            val private_mode = is_private_notifications(context)
            val builder = base_builder(context)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP_KEY_NEW_MAIL)
            if (private_mode) {
                builder
                    .setContentTitle(sender)
                    .setContentText(context.getString(R.string.notif_new_message))
                    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            } else {
                val one_line_subject = subject.replace(Regex("\\s+"), " ").trim()
                val one_line_preview = preview.replace(Regex("\\s+"), " ").trim()
                builder
                    .setContentTitle(sender)
                    .setContentText(one_line_subject)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                if (one_line_preview.isNotBlank()) {
                    builder.setStyle(
                        NotificationCompat.InboxStyle()
                            .addLine(one_line_subject)
                            .addLine(one_line_preview),
                    )
                }
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
                .setColor(0xFF3B82F6.toInt())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setContentIntent(pending)
                .setAutoCancel(true)
        }
    }
}
