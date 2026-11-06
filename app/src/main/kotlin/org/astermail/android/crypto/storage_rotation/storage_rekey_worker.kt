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

package org.astermail.android.crypto.storage_rotation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

val STORAGE_REKEY_RETRY_DELAYS_SECS = longArrayOf(60, 300, 900)

fun storage_rekey_retry_delay(attempt: Int): Long? =
    STORAGE_REKEY_RETRY_DELAYS_SECS.getOrNull(attempt)

fun should_retry_storage_rekey(result: ResealRunResult): Boolean =
    result.status == ResealRunStatus.INTERRUPTED || result.status == ResealRunStatus.FAILED

data class StorageRekeyNextRun(val attempt: Int, val delay_secs: Long)

fun storage_rekey_next_run(result: ResealRunResult, attempt: Int): StorageRekeyNextRun? {
    if (result.status == ResealRunStatus.DEFERRED) {
        val wait = (result.retry_after_secs ?: REKEY_NOT_ADMITTED_WAIT_SECS).coerceAtLeast(REKEY_RETRY_AFTER_MIN_SECS)
        return StorageRekeyNextRun(0, wait)
    }
    if (!should_retry_storage_rekey(result)) return null
    return storage_rekey_retry_delay(attempt)?.let { StorageRekeyNextRun(attempt + 1, it) }
}

class StorageRekeyWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val attempt = inputData.getInt(KEY_ATTEMPT, 0)
        val service = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                StorageRekeyEntryPoint::class.java,
            ).storage_rekey_service()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return Result.failure()
        }
        val result = try {
            service.run(is_cancelled = { isStopped })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            ResealRunResult(ResealRunStatus.FAILED, StepCounts(), "error")
        }
        if (!isStopped) {
            storage_rekey_next_run(result, attempt)?.let { next ->
                schedule(context, next.attempt, next.delay_secs, ExistingWorkPolicy.APPEND_OR_REPLACE)
            }
        }
        return Result.success()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface StorageRekeyEntryPoint {
        fun storage_rekey_service(): StorageRekeyService
    }

    companion object {
        private const val WORK_NAME = "storage_rekey"
        private const val KEY_ATTEMPT = "attempt"

        fun enqueue(context: Context) {
            schedule(context, 0, 0, ExistingWorkPolicy.KEEP)
        }

        private fun schedule(context: Context, attempt: Int, delay_secs: Long, policy: ExistingWorkPolicy) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<StorageRekeyWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delay_secs, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_ATTEMPT to attempt))
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
            }
        }
    }
}
