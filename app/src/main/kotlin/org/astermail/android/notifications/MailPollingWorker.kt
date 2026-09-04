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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.MainActivity
import org.astermail.android.R
import org.astermail.android.api.ApiClient
import org.astermail.android.api.ApiError
import org.astermail.android.api.BuildConfig
import org.astermail.android.api.TokenProvider
import org.astermail.android.api.auth.AuthApiImpl
import org.astermail.android.api.billing.BillingApiImpl
import org.astermail.android.api.mail.MailApiImpl
import org.astermail.android.mail.MailRepository
import org.astermail.android.storage.TokenStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.auth.providers.BearerTokens
import java.util.concurrent.TimeUnit

private fun localized(context: Context): Context =
    org.astermail.android.settings.app_language.wrap(context)

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
            UnifiedPushState.sync_registration(context)
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
                } catch (cancelled: CancellationException) {
                    throw cancelled
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
            allow_cleartext_for_test = BuildConfig.API_BASE_URL.startsWith("http://"),
        )
        try {
            check_billing_state(prefs, client)

            return try {
                poll_and_notify(context, prefs, MailApiImpl(client))
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Result.retry()
            }
        } finally {
            client.close()
        }
    }

    private suspend fun check_billing_state(
        prefs: android.content.SharedPreferences,
        client: ApiClient,
    ) {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_BILLING_CHECK_MS, 0L)

        if (now - last < BILLING_CHECK_INTERVAL_MS) return

        prefs.edit().putLong(KEY_LAST_BILLING_CHECK_MS, now).apply()

        runCatching {
            val subscription = kotlinx.coroutines.withTimeout(20_000L) {
                BillingApiImpl(client).get_subscription()
            }

            org.astermail.android.billing.PaymentFailedNotifier.observe(
                context,
                subscription.status,
                subscription.payment_failed_at,
                subscription.current_period_end,
                subscription.plan.name,
            )
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
        } catch (cancelled: CancellationException) {
            throw cancelled
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
        val cached_notifiable = prefs.getInt(KEY_CACHED_NOTIFIABLE, new_notifiable)

        val unread_arrivals = new_unread - cached_unread
        val notifiable_arrivals = new_notifiable - cached_notifiable
        val arrived = maxOf(unread_arrivals, notifiable_arrivals)

        val has_pending_new_mail = has_baseline && arrived > 0
        val suppressed_by_quiet_hours = has_pending_new_mail && is_quiet_hours_now(context)
        var deferred = false
        if (has_pending_new_mail && !suppressed_by_quiet_hours) {
            deferred = !notify_for_new_mail(arrived)
            if (!deferred) {
                prefs.edit().putInt(KEY_LAST_NOTIFIED_COUNT, new_unread).apply()
            }
        }

        val editor = prefs.edit()
        if (!suppressed_by_quiet_hours && !deferred) {
            editor.putInt(KEY_CACHED_UNREAD, new_unread)
            editor.putInt(KEY_CACHED_NOTIFIABLE, new_notifiable)
        } else if (new_notifiable < cached_notifiable) {
            editor.putInt(KEY_CACHED_NOTIFIABLE, new_notifiable)
        }
        editor.apply()
        schedule_next(context)
        return Result.success()
    }

    private fun show_notification(unread_count: Int) {
        show_generic(context, unread_count)
    }

    private suspend fun notify_for_new_mail(arrived: Int): Boolean {
        if (!is_notify_new_email(context) && !is_notify_replies(context)) return true
        if (message_notified_recently(context)) return false
        val entry = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MailRepositoryEntryPoint::class.java,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        val app_lock_configured = runCatching { entry?.app_lock_store()?.is_configured() ?: true }.getOrDefault(true)
        if (org.astermail.android.security.LockdownStore.is_enabled(context) || app_lock_configured) {
            show_generic(context, arrived)
            return true
        }
        val repo = entry?.mail_repository()
        if (repo == null) {
            show_generic(context, arrived)
            return true
        }
        var newest: org.astermail.android.mail.InboxItem? = null
        var sender: String? = null
        var fetched_any_page = false
        var forced_heal_armed = false
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
                val folders = try {
                    kotlinx.coroutines.withTimeout(20_000L) {
                        repo.list_notifiable_folders()
                    }.getOrNull()
                } catch (_: Throwable) { null }
                if (folders != null) fetched_any_page = true
                val muted = muted_folder_tokens(context)
                for (folder in folders.orEmpty()) {
                    if (folder.label_token in muted) continue
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

            if (candidate?.is_undecryptable == true &&
                !forced_heal_armed &&
                attempt < 2 &&
                repo.is_sealed_inbound_nonce(candidate.raw_item.envelope_nonce)
            ) {
                forced_heal_armed = true
                repo.begin_decrypt_retry()
                return@repeat
            }

            val candidate_sender = if (candidate?.is_undecryptable == true) {
                localized(context).getString(org.astermail.android.R.string.encrypted)
            } else {
                (
                    candidate?.display_sender_name
                        ?: candidate?.sender_name?.takeIf { it.isNotBlank() }
                        ?: candidate?.sender_email
                    )?.trim()
            }
            if (candidate != null &&
                !candidate_sender.isNullOrBlank() &&
                claim_item_notification(context, candidate.id)
            ) {
                newest = candidate
                sender = candidate_sender
            }
        }
        val fresh = newest
        val resolved_sender = sender
        if (fresh == null || resolved_sender.isNullOrBlank()) {
            if (!fetched_any_page) {
                show_generic(context, arrived)
            }
            return true
        }
        val subject = if (fresh.is_undecryptable) {
            localized(context).getString(org.astermail.android.R.string.decrypt_failed_title)
        } else {
            fresh.subject.trim()
        }
        val preview = if (fresh.is_undecryptable) {
            localized(context).getString(org.astermail.android.R.string.undecryptable_message_preview)
        } else {
            fresh.preview
        }
        val message_id = message_notification_id(fresh.id.hashCode())
        return post_message_notification(
            item_id = fresh.id,
            sender = resolved_sender,
            subject = subject,
            preview = preview,
            message_id = message_id,
        )
    }

    private suspend fun post_message_notification(
        item_id: String,
        sender: String,
        subject: String,
        preview: String,
        message_id: Int,
    ): Boolean {
        return notify_lock.withLock {
            if (message_notified_recently(context)) {
                release_item_notification(context, item_id)
                return@withLock false
            }
            val posted = runCatching {
                show_message(
                    context = context,
                    sender = sender,
                    subject = subject,
                    preview = preview,
                    message_id = message_id,
                    item_id = item_id,
                )
            }.isSuccess
            if (!posted) release_item_notification(context, item_id)
            posted
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MailRepositoryEntryPoint {
        fun mail_repository(): MailRepository
        fun app_lock_store(): org.astermail.android.security.AppLockStore
    }

    companion object {
        const val CHANNEL_ID = "aster_new_mail"
        const val CHANNEL_ID_SOUND_ONLY = "aster_new_mail_sound_only"
        const val CHANNEL_ID_VIBRATE_ONLY = "aster_new_mail_vibrate_only"
        const val CHANNEL_ID_SILENT = "aster_new_mail_silent"
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
        private const val KEY_NOTIFY_REPLIES = "notify_replies"
        private const val KEY_SOUND_ENABLED = "notification_sound_enabled"
        private const val KEY_VIBRATE_ENABLED = "notification_vibrate_enabled"
        private const val KEY_MUTED_FOLDER_TOKENS = "muted_folder_tokens"
        private const val KEY_MUTED_NOTIFICATION_CATEGORIES = "muted_notification_categories"
        private const val KEY_PROTECTED_FOLDER_TOKENS = "protected_folder_tokens"
        private const val KEY_PROTECTED_FOLDER_TOKENS_KNOWN = "protected_folder_tokens_known"
        private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
        private const val KEY_QUIET_HOURS_START = "quiet_hours_start"
        private const val KEY_QUIET_HOURS_END = "quiet_hours_end"
        private const val KEY_LAST_WORK_START_MS = "last_work_start_ms"
        private const val KEY_LAST_BILLING_CHECK_MS = "last_billing_check_ms"
        private const val BILLING_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
        private const val WORKER_IN_FLIGHT_GRACE_MS = 90_000L
        private const val KEY_LAST_MESSAGE_NOTIFY_MS = "last_message_notify_ms"
        const val MESSAGE_NOTIFY_DEDUPE_WINDOW_MS = 90_000L
        private const val KEY_LAST_GENERIC_COUNT = "last_generic_notify_count"
        private const val KEY_LAST_GENERIC_MS = "last_generic_notify_ms"
        private const val GENERIC_NOTIFY_COOLDOWN_MS = 60_000L
        private val notify_lock = Mutex()
        val FORCED_NOTIFY_POLICY: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE
        private const val SENDER_MAX_LENGTH = 80
        private const val SUBJECT_MAX_LENGTH = 120

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

        fun is_sound_enabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SOUND_ENABLED, true)
        }

        fun is_vibrate_enabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_VIBRATE_ENABLED, true)
        }

        fun set_notification_alerts(context: Context, sound: Boolean, vibrate: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, sound)
                .putBoolean(KEY_VIBRATE_ENABLED, vibrate)
                .apply()
            create_channel(context)
        }

        fun notification_channel_id(sound: Boolean, vibrate: Boolean): String = when {
            sound && vibrate -> CHANNEL_ID
            sound -> CHANNEL_ID_SOUND_ONLY
            vibrate -> CHANNEL_ID_VIBRATE_ONLY
            else -> CHANNEL_ID_SILENT
        }

        fun alert_channel_ids(): List<String> =
            listOf(CHANNEL_ID, CHANNEL_ID_SOUND_ONLY, CHANNEL_ID_VIBRATE_ONLY, CHANNEL_ID_SILENT)

        fun active_channel_id(context: Context): String =
            notification_channel_id(is_sound_enabled(context), is_vibrate_enabled(context))

        fun create_channel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val active = active_channel_id(context)
            val channel = NotificationChannel(
                active,
                localized(context).getString(R.string.notif_channel_new_mail_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
            channel.description = localized(context)
                .getString(R.string.notif_channel_new_mail_description)
            channel.setShowBadge(true)
            channel.enableVibration(is_vibrate_enabled(context))
            if (!is_sound_enabled(context)) {
                channel.setSound(null, null)
            }
            manager.createNotificationChannel(channel)
            for (id in alert_channel_ids()) {
                if (id != active) {
                    runCatching { manager.deleteNotificationChannel(id) }
                }
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

        fun is_notify_replies(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_NOTIFY_REPLIES, true)
        }

        fun is_envelope_notifiable_by_type(
            context: Context,
            envelope: org.astermail.android.mail.DecryptedEnvelope,
        ): Boolean {
            val is_reply = envelope.raw_headers.any { (name, value) ->
                value.isNotBlank() &&
                    (name.equals("in-reply-to", ignoreCase = true) ||
                        name.equals("references", ignoreCase = true))
            }
            return if (is_reply) is_notify_replies(context) else is_notify_new_email(context)
        }

        fun is_item_notifiable_by_type(
            context: Context,
            item: org.astermail.android.mail.InboxItem,
        ): Boolean {
            return if (item.thread_message_count > 1) {
                is_notify_replies(context)
            } else {
                is_notify_new_email(context)
            }
        }

        private fun stored_token_set(context: Context, key: String): Set<String> {
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(key, "") ?: ""
            return stored.split('\n').filter { it.isNotBlank() }.toSet()
        }

        fun muted_folder_tokens(context: Context): Set<String> =
            stored_token_set(context, KEY_MUTED_FOLDER_TOKENS) +
                stored_token_set(context, KEY_PROTECTED_FOLDER_TOKENS)

        fun protected_folder_tokens(context: Context): Set<String> =
            stored_token_set(context, KEY_PROTECTED_FOLDER_TOKENS)

        fun protected_folder_state_known(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_PROTECTED_FOLDER_TOKENS_KNOWN, false)

        fun set_protected_folder_tokens(context: Context, tokens: Collection<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    KEY_PROTECTED_FOLDER_TOKENS,
                    tokens.filter { it.isNotBlank() }.distinct().joinToString("\n"),
                )
                .putBoolean(KEY_PROTECTED_FOLDER_TOKENS_KNOWN, true)
                .apply()
        }

        fun set_muted_folder_tokens(context: Context, tokens: Collection<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    KEY_MUTED_FOLDER_TOKENS,
                    tokens.filter { it.isNotBlank() }.distinct().joinToString("\n"),
                )
                .apply()
            MutedFolderSync.push(context, tokens)
        }

        fun muted_notification_categories(context: Context): Set<String> =
            stored_token_set(context, KEY_MUTED_NOTIFICATION_CATEGORIES)

        fun set_muted_notification_categories(context: Context, categories: Collection<String>) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    KEY_MUTED_NOTIFICATION_CATEGORIES,
                    categories.filter { it.isNotBlank() && it != "primary" }
                        .distinct()
                        .joinToString("\n"),
                )
                .apply()
        }

        fun is_item_in_muted_category(
            item: org.astermail.android.mail.InboxItem,
            muted: Set<String>,
        ): Boolean {
            if (muted.isEmpty()) return false
            return org.astermail.android.mail.category_for_tab(item.category) in muted
        }

        fun is_item_in_muted_folder(
            item: org.astermail.android.mail.InboxItem,
            muted: Set<String>,
        ): Boolean {
            if (muted.isEmpty()) return false
            val tokens = (
                item.labels +
                    listOfNotNull(item.raw_item.folder_token) +
                    (item.raw_item.folders?.mapNotNull { it.folder_token } ?: emptyList())
                ).distinct()
            return tokens.any { it in muted }
        }

        fun set_notify_new_email(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIFY_NEW_EMAIL, enabled)
                .apply()
        }

        fun set_notify_replies(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_NOTIFY_REPLIES, enabled)
                .apply()
        }

        fun reset_new_mail_baseline(context: Context) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_CACHED_UNREAD)
                .remove(KEY_CACHED_NOTIFIABLE)
                .remove(KEY_LAST_NOTIFIED_COUNT)
                .remove(KEY_NOTIFIED_ITEM_IDS)
                .remove(KEY_LAST_MESSAGE_NOTIFY_MS)
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
                .setInitialDelay(
                    org.astermail.android.api.network.poll_chain_delay_minutes(
                        org.astermail.android.network.low_network_monitor.is_active(context),
                    ),
                    TimeUnit.MINUTES,
                )
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
                .apply {
                    if (org.astermail.android.api.network.should_run_expedited_poll(
                            org.astermail.android.network.low_network_monitor.is_active(context),
                        )
                    ) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .setInputData(Data.Builder().putBoolean(KEY_FORCE_NOTIFY, true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                FORCED_NOTIFY_POLICY,
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

        fun is_within_message_dedupe_window(last_notify_ms: Long, now_ms: Long): Boolean {
            if (last_notify_ms <= 0L) return false
            val elapsed = now_ms - last_notify_ms
            return elapsed in 0 until MESSAGE_NOTIFY_DEDUPE_WINDOW_MS
        }

        fun should_post_group_summary(active_mail_notifications: Int): Boolean {
            return active_mail_notifications >= 2
        }

        private fun message_notified_recently(context: Context): Boolean {
            val last = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_LAST_MESSAGE_NOTIFY_MS, 0L)
            return is_within_message_dedupe_window(last, System.currentTimeMillis())
        }

        @Synchronized
        fun show_generic(context: Context, unread_count: Int) {
            if (!can_post(context)) return
            if (message_notified_recently(context)) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val repeat_of_last = prefs.getInt(KEY_LAST_GENERIC_COUNT, -1) == unread_count &&
                now - prefs.getLong(KEY_LAST_GENERIC_MS, 0L) < GENERIC_NOTIFY_COOLDOWN_MS
            if (repeat_of_last) return
            prefs.edit()
                .putInt(KEY_LAST_GENERIC_COUNT, unread_count)
                .putLong(KEY_LAST_GENERIC_MS, now)
                .commit()
            val text = localized(context).resources.getQuantityString(
                R.plurals.new_mail_notification, unread_count, unread_count,
            )
            val notification = base_builder(context)
                .setContentTitle(localized(context).getString(R.string.app_name))
                .setContentText(text)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setGroup(GROUP_KEY_NEW_MAIL)
                .build()
            val manager = NotificationManagerCompat.from(context)
            manager.notify(NOTIFICATION_ID, notification)
            post_group_summary(context, manager, NOTIFICATION_ID)
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
            val safe_sender = org.astermail.android.mail.safe_display_text(sender, SENDER_MAX_LENGTH)
                .ifBlank { localized(context).getString(R.string.app_name) }
            val one_line_subject = org.astermail.android.mail.safe_display_text(subject, SUBJECT_MAX_LENGTH)
                .ifBlank { localized(context).getString(R.string.notif_new_message) }
            val one_line_preview = org.astermail.android.mail.safe_display_text(preview)
            val builder = base_builder(context)
                .setWhen(System.currentTimeMillis())
                .setShowWhen(true)
                .setGroup(GROUP_KEY_NEW_MAIL)
                .setContentTitle(safe_sender)
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
                add_message_actions(context, builder, item_id, message_id)
            }
            if (one_line_preview.isNotBlank() && one_line_preview != one_line_subject) {
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
                        .setContentTitle(localized(context).getString(R.string.app_name))
                        .setContentText(localized(context).getString(R.string.notif_new_message))
                        .setGroup(GROUP_KEY_NEW_MAIL)
                        .build(),
                )
            } else {
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            val manager = NotificationManagerCompat.from(context)
            manager.cancel(NOTIFICATION_ID)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_GENERIC_COUNT)
                .remove(KEY_LAST_GENERIC_MS)
                .putLong(KEY_LAST_MESSAGE_NOTIFY_MS, System.currentTimeMillis())
                .commit()
            manager.notify(message_id, builder.build())
            post_group_summary(context, manager, message_id)
        }

        private fun add_message_actions(
            context: Context,
            builder: NotificationCompat.Builder,
            item_id: String,
            message_id: Int,
        ) {
            val actions = listOf(
                Triple(
                    MailNotificationActionReceiver.ACTION_ARCHIVE,
                    R.drawable.ic_action_archive,
                    R.string.archive,
                ),
                Triple(
                    MailNotificationActionReceiver.ACTION_TRASH,
                    R.drawable.ic_action_trash,
                    R.string.delete,
                ),
                Triple(
                    MailNotificationActionReceiver.ACTION_MARK_READ,
                    R.drawable.ic_action_mark_read,
                    R.string.mark_read_action,
                ),
            )
            actions.forEachIndexed { index, (action, icon, label) ->
                val intent = Intent(context, MailNotificationActionReceiver::class.java).apply {
                    this.action = action
                    putExtra(MailNotificationActionReceiver.EXTRA_ITEM_ID, item_id)
                    putExtra(MailNotificationActionReceiver.EXTRA_NOTIFICATION_ID, message_id)
                }
                val pending = PendingIntent.getBroadcast(
                    context,
                    message_id * ACTION_REQUEST_STRIDE + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(
                    NotificationCompat.Action.Builder(icon, localized(context).getString(label), pending)
                        .setShowsUserInterface(false)
                        .build(),
                )
            }
        }

        private const val ACTION_REQUEST_STRIDE = 8

        private fun active_mail_notification_count(context: Context, just_posted_id: Int): Int {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return if (just_posted_id == 0) 0 else 1
            return runCatching {
                val ids = manager.activeNotifications
                    .map { it.id }
                    .filter { it >= MESSAGE_ID_BASE || it == NOTIFICATION_ID }
                    .toMutableSet()
                if (just_posted_id != 0) ids.add(just_posted_id)
                ids.size
            }.getOrDefault(if (just_posted_id == 0) 0 else 1)
        }

        private fun post_group_summary(context: Context, manager: NotificationManagerCompat, just_posted_id: Int) {
            if (!should_post_group_summary(active_mail_notification_count(context, just_posted_id))) {
                manager.cancel(SUMMARY_NOTIFICATION_ID)
                return
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val summary = NotificationCompat.Builder(context, active_channel_id(context))
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(app_large_icon(context))
                .setColor(0xFF3B82F6.toInt())
                .setContentTitle(localized(context).getString(R.string.notif_group_summary_new_mail))
                .setGroup(GROUP_KEY_NEW_MAIL)
                .setGroupSummary(true)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
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
            val muted = muted_folder_tokens(context)
            val muted_categories = muted_notification_categories(context)
            val sign_in_marker = NotificationDedupe.sign_in_marker(context)
            return items.firstOrNull {
                !it.is_read &&
                    !was_item_notified(context, it.id) &&
                    !is_item_in_muted_folder(it, muted) &&
                    !is_item_in_muted_category(it, muted_categories) &&
                    !NotificationDedupe.is_probable_sign_in_alert_mail(sign_in_marker, it.sender_email) &&
                    is_item_notifiable_by_type(context, it)
            }
        }

        @Synchronized
        fun was_item_notified(context: Context, item_id: String): Boolean {
            if (item_id.isBlank()) return false
            val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_NOTIFIED_ITEM_IDS, "") ?: ""
            return stored.split('\n').contains(item_id)
        }

        @Synchronized
        fun claim_item_notification(context: Context, item_id: String): Boolean {
            if (item_id.isBlank()) return false
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_NOTIFIED_ITEM_IDS, "") ?: ""
            val ids = stored.split('\n').filter { it.isNotBlank() }
            if (ids.contains(item_id)) return false
            val updated = ids.toMutableList()
            updated.add(item_id)
            while (updated.size > NOTIFIED_ITEM_IDS_MAX) updated.removeAt(0)
            prefs.edit().putString(KEY_NOTIFIED_ITEM_IDS, updated.joinToString("\n")).commit()
            return true
        }

        @Synchronized
        fun release_item_notification(context: Context, item_id: String) {
            if (item_id.isBlank()) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val stored = prefs.getString(KEY_NOTIFIED_ITEM_IDS, "") ?: ""
            val ids = stored.split('\n').filter { it.isNotBlank() && it != item_id }
            prefs.edit().putString(KEY_NOTIFIED_ITEM_IDS, ids.joinToString("\n")).commit()
        }

        fun action_failed_title_res(action: String): Int? = when (action) {
            MailNotificationActionWorker.ACTION_ARCHIVE -> R.string.failed_to_archive
            MailNotificationActionWorker.ACTION_TRASH -> R.string.failed_to_trash
            MailNotificationActionWorker.ACTION_MARK_READ -> R.string.failed_mark_read
            else -> null
        }

        fun show_action_failed(context: Context, item_id: String, action: String) {
            if (item_id.isBlank()) return
            if (!can_post(context)) return
            val title_res = action_failed_title_res(action) ?: return
            create_channel(context)
            val message_id = message_notification_id(item_id.hashCode())
            val open_intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_EMAIL_ID, item_id)
            }
            val pending = PendingIntent.getActivity(
                context, message_id, open_intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = base_builder(context)
                .setContentTitle(localized(context).getString(title_res))
                .setContentIntent(pending)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setGroup(GROUP_KEY_NEW_MAIL)
                .build()
            val manager = NotificationManagerCompat.from(context)
            manager.notify(message_id, notification)
            post_group_summary(context, manager, message_id)
        }

        @Synchronized
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

        fun cancel_notification_id(context: Context, notification_id: Int) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            manager.cancel(notification_id)
            clear_summary_if_empty(manager, setOf(notification_id))
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
            val sound = is_sound_enabled(context)
            val vibrate = is_vibrate_enabled(context)
            return NotificationCompat.Builder(context, active_channel_id(context))
                .setSilent(!sound && !vibrate)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(app_large_icon(context))
                .setColor(0xFF3B82F6.toInt())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_EMAIL)
                .setContentIntent(pending)
                .setAutoCancel(true)
        }

        @Volatile
        private var cached_large_icon: android.graphics.Bitmap? = null

        private fun app_large_icon(context: Context) = cached_large_icon ?: runCatching {
            BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher_round)
                ?: BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        }.getOrNull()?.also { cached_large_icon = it }
    }
}
