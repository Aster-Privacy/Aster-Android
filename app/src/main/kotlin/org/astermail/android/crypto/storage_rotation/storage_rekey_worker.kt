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

const val STORAGE_REKEY_RETIRE_WAIT_SECS = 6 * 3600L

const val STORAGE_REKEY_RETIRE_MAX_ATTEMPTS = 2

const val STORAGE_REKEY_RETIRE_MAX_WAIT_SECS = 7 * 24 * 3600L

fun storage_rekey_retire_wait(retire_attempt: Int): Long {
    if (retire_attempt < STORAGE_REKEY_RETIRE_MAX_ATTEMPTS) return STORAGE_REKEY_RETIRE_WAIT_SECS
    val doublings = (retire_attempt - STORAGE_REKEY_RETIRE_MAX_ATTEMPTS + 1).coerceAtMost(8)
    return (STORAGE_REKEY_RETIRE_WAIT_SECS shl doublings).coerceAtMost(STORAGE_REKEY_RETIRE_MAX_WAIT_SECS)
}

fun should_retry_storage_rekey(result: ResealRunResult): Boolean =
    result.status == ResealRunStatus.INTERRUPTED || result.status == ResealRunStatus.FAILED

data class StorageRekeyNextRun(val attempt: Int, val delay_secs: Long, val retire_attempt: Int = 0)

fun storage_rekey_next_run(result: ResealRunResult, attempt: Int, retire_attempt: Int = 0): StorageRekeyNextRun? {
    if (result.status == ResealRunStatus.DEFERRED) {
        val wait = (result.retry_after_secs ?: REKEY_NOT_ADMITTED_WAIT_SECS).coerceAtLeast(REKEY_RETRY_AFTER_MIN_SECS)
        return StorageRekeyNextRun(0, wait, retire_attempt)
    }
    if (!should_retry_storage_rekey(result)) return null
    if (result.status == ResealRunStatus.INTERRUPTED && result.reason == LEGACY_KEY_RETIRE_PENDING) {
        val next_attempt = (retire_attempt + 1).coerceAtMost(STORAGE_REKEY_RETIRE_MAX_ATTEMPTS + 8)
        return StorageRekeyNextRun(attempt, storage_rekey_retire_wait(retire_attempt), next_attempt)
    }
    return storage_rekey_retry_delay(attempt)?.let { StorageRekeyNextRun(attempt + 1, it, retire_attempt) }
}

class StorageRekeyWorker(
    private val context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val attempt = inputData.getInt(KEY_ATTEMPT, 0)
        val retire_attempt = inputData.getInt(KEY_RETIRE_ATTEMPT, 0)
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
            storage_rekey_next_run(result, attempt, retire_attempt)?.let { next ->
                schedule(context, next.attempt, next.retire_attempt, next.delay_secs, ExistingWorkPolicy.APPEND_OR_REPLACE)
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
        private const val KEY_RETIRE_ATTEMPT = "retire_attempt"

        fun enqueue(context: Context) {
            schedule(context, 0, 0, 0, ExistingWorkPolicy.KEEP)
        }

        private fun schedule(
            context: Context,
            attempt: Int,
            retire_attempt: Int,
            delay_secs: Long,
            policy: ExistingWorkPolicy,
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<StorageRekeyWorker>()
                .setConstraints(constraints)
                .setInitialDelay(delay_secs, TimeUnit.SECONDS)
                .setInputData(workDataOf(KEY_ATTEMPT to attempt, KEY_RETIRE_ATTEMPT to retire_attempt))
                .build()
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
            }
        }
    }
}
