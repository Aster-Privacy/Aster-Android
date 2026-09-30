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

package org.astermail.android.mail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
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
import java.util.concurrent.TimeUnit

class PendingMailActionWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                PendingMailActionEntryPoint::class.java,
            ).mail_repository()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return if (runAttemptCount < MAX_SETUP_ATTEMPTS) Result.retry() else Result.failure()
        }
        return when (repo.drain_pending_actions()) {
            PendingDrainOutcome.Done -> Result.success()
            PendingDrainOutcome.Retry -> Result.retry()
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PendingMailActionEntryPoint {
        fun mail_repository(): MailRepository
    }

    companion object {
        private const val WORK_NAME = "pending_mail_actions"
        private const val MAX_SETUP_ATTEMPTS = 10

        fun enqueue(context: Context, restart: Boolean = false) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<PendingMailActionWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
