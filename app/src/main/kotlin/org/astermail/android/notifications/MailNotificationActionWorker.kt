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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import org.astermail.android.mail.MailReadEvents
import org.astermail.android.mail.MailRepository
import org.astermail.android.mail.SearchIndexManager
import java.util.concurrent.TimeUnit

enum class MailActionAttempt { Done, Retry, GiveUp }

class MailNotificationActionWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val item_id = inputData.getString(KEY_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val action = inputData.getString(KEY_ACTION) ?: return Result.success()
        if (action !in supported_actions) return Result.success()
        val entry_point = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MailNotificationActionEntryPoint::class.java,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        val repo = entry_point?.mail_repository()
        val search_index = entry_point?.search_index_manager()
        val is_mark_read = action == ACTION_MARK_READ
        if (is_mark_read && runAttemptCount == 0) {
            runCatching { search_index?.update_read(item_id, true) }
            MailReadEvents.emit(MailReadEvents.Event.Applied(item_id))
        }
        val succeeded = repo != null && when (action) {
            ACTION_ARCHIVE -> repo.archive(listOf(item_id))
            ACTION_TRASH -> repo.trash(listOf(item_id))
            else -> repo.mark_read(item_id, true)
        }.isSuccess
        val outcome = attempt_result(succeeded, runAttemptCount)
        if (is_mark_read && outcome == MailActionAttempt.Done) {
            runCatching { search_index?.update_read(item_id, true) }
            MailReadEvents.emit(MailReadEvents.Event.Confirmed(item_id))
        }
        return when (outcome) {
            MailActionAttempt.Done -> Result.success()
            MailActionAttempt.Retry -> Result.retry()
            MailActionAttempt.GiveUp -> {
                if (is_mark_read) {
                    runCatching { search_index?.update_read(item_id, false) }
                    MailReadEvents.emit(MailReadEvents.Event.Failed(item_id))
                }
                runCatching { MailPollingWorker.show_action_failed(context, item_id, action) }
                Result.failure()
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MailNotificationActionEntryPoint {
        fun mail_repository(): MailRepository
        fun search_index_manager(): SearchIndexManager
    }

    companion object {
        const val KEY_ITEM_ID = "mail_notification_item_id"
        const val KEY_ACTION = "mail_notification_action"
        const val ACTION_ARCHIVE = "archive"
        const val ACTION_TRASH = "trash"
        const val ACTION_MARK_READ = "mark_read"
        private const val WORK_PREFIX = "mail_notification_action_"
        const val MAX_ATTEMPTS = 5

        private val supported_actions = setOf(ACTION_ARCHIVE, ACTION_TRASH, ACTION_MARK_READ)

        fun attempt_result(succeeded: Boolean, attempt: Int): MailActionAttempt = when {
            succeeded -> MailActionAttempt.Done
            attempt + 1 < MAX_ATTEMPTS -> MailActionAttempt.Retry
            else -> MailActionAttempt.GiveUp
        }

        fun enqueue(context: Context, item_id: String, action: String) {
            if (item_id.isBlank()) return
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<MailNotificationActionWorker>()
                .setConstraints(constraints)
                .setInputData(
                    Data.Builder()
                        .putString(KEY_ITEM_ID, item_id)
                        .putString(KEY_ACTION, action)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_PREFIX + action + "_" + item_id,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
