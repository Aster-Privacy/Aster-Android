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
import org.astermail.android.mail.MailRepository
import java.util.concurrent.TimeUnit

class MailNotificationActionWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val item_id = inputData.getString(KEY_ITEM_ID)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val action = inputData.getString(KEY_ACTION) ?: return Result.success()
        val repo = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                MailNotificationActionEntryPoint::class.java,
            ).mail_repository()
        } catch (_: Throwable) {
            return Result.retry()
        }
        val outcome = when (action) {
            ACTION_ARCHIVE -> repo.archive(listOf(item_id))
            ACTION_TRASH -> repo.trash(listOf(item_id))
            ACTION_MARK_READ -> repo.mark_read(item_id, true)
            else -> return Result.success()
        }
        if (outcome.isSuccess) return Result.success()
        return if (runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface MailNotificationActionEntryPoint {
        fun mail_repository(): MailRepository
    }

    companion object {
        const val KEY_ITEM_ID = "mail_notification_item_id"
        const val KEY_ACTION = "mail_notification_action"
        const val ACTION_ARCHIVE = "archive"
        const val ACTION_TRASH = "trash"
        const val ACTION_MARK_READ = "mark_read"
        private const val WORK_PREFIX = "mail_notification_action_"
        private const val MAX_ATTEMPTS = 5

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
