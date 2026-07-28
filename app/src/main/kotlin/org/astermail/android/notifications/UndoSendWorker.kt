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
import org.astermail.android.mail.PendingSendOutcome
import java.util.concurrent.TimeUnit

class UndoSendWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val pending_id = inputData.getString(KEY_PENDING_ID) ?: return Result.success()
        val owner_id = inputData.getString(KEY_OWNER_ID)
        val repo = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                UndoSendEntryPoint::class.java,
            ).mail_repository()
        } catch (_: Throwable) {
            return Result.retry()
        }
        return when (repo.run_pending_send(pending_id, owner_id, runAttemptCount)) {
            PendingSendOutcome.SENT, PendingSendOutcome.GONE, PendingSendOutcome.FAILED -> Result.success()
            PendingSendOutcome.RETRY -> Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface UndoSendEntryPoint {
        fun mail_repository(): MailRepository
    }

    companion object {
        const val KEY_PENDING_ID = "pending_send_id"
        const val KEY_OWNER_ID = "pending_send_owner_id"
        private const val WORK_PREFIX = "undo_send_"

        private fun work_name(pending_id: String): String = WORK_PREFIX + pending_id

        fun enqueue(context: Context, pending_id: String, initial_delay_ms: Long, owner_id: String? = null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val data = Data.Builder().putString(KEY_PENDING_ID, pending_id)
            owner_id?.let { data.putString(KEY_OWNER_ID, it) }
            val request = OneTimeWorkRequestBuilder<UndoSendWorker>()
                .setConstraints(constraints)
                .setInitialDelay(initial_delay_ms.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(data.build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                work_name(pending_id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun enqueue_if_absent(context: Context, pending_id: String, initial_delay_ms: Long) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<UndoSendWorker>()
                .setConstraints(constraints)
                .setInitialDelay(initial_delay_ms.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
                .setInputData(Data.Builder().putString(KEY_PENDING_ID, pending_id).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                work_name(pending_id),
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context, pending_id: String) {
            WorkManager.getInstance(context).cancelUniqueWork(work_name(pending_id))
        }
    }
}
