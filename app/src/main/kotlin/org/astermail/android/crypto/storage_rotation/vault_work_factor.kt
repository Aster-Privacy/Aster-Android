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

import kotlinx.coroutines.CancellationException

const val VAULT_WORK_FACTOR_STEP = "vault_work_factor"
const val VAULT_WORK_FACTOR_BACKOFF = "vault_work_factor_backoff"
const val VAULT_WORK_FACTOR_FIRST_BACKOFF_MS = 60L * 60L * 1000L
const val VAULT_WORK_FACTOR_MAX_BACKOFF_MS = 7L * 24L * 60L * 60L * 1000L

enum class VaultWorkFactor { UPGRADED, CURRENT, CONFLICT, NOT_ADMITTED, SKIPPED }

data class WorkFactorBackoff(val next_at_ms: Long, val delay_ms: Long)

fun read_work_factor_backoff(progress: ResealProgress): WorkFactorBackoff? {
    val parts = progress.steps[VAULT_WORK_FACTOR_BACKOFF]?.cursor?.split(":") ?: return null
    if (parts.size != 2) return null
    val next_at = parts[0].toLongOrNull() ?: return null
    val delay = parts[1].toLongOrNull() ?: return null
    return WorkFactorBackoff(next_at, delay)
}

fun next_work_factor_delay(previous: WorkFactorBackoff?): Long {
    val prior = previous?.delay_ms ?: return VAULT_WORK_FACTOR_FIRST_BACKOFF_MS
    if (prior >= VAULT_WORK_FACTOR_MAX_BACKOFF_MS / 2) return VAULT_WORK_FACTOR_MAX_BACKOFF_MS
    return (prior * 2).coerceIn(VAULT_WORK_FACTOR_FIRST_BACKOFF_MS, VAULT_WORK_FACTOR_MAX_BACKOFF_MS)
}

suspend fun run_vault_work_factor_step(
    admitted: Boolean,
    password_ready: () -> Boolean,
    store: ProgressStore,
    now_ms: () -> Long = System::currentTimeMillis,
    raise: suspend () -> VaultWorkFactor,
): VaultWorkFactor {
    if (!admitted || !password_ready()) return VaultWorkFactor.SKIPPED
    val progress = runCatching { store.load() }.getOrNull() ?: ResealProgress(VAULT_WORK_FACTOR_STEP)
    val backoff = read_work_factor_backoff(progress)
    if (backoff != null && now_ms() < backoff.next_at_ms) return VaultWorkFactor.SKIPPED
    val result = try {
        raise()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        VaultWorkFactor.SKIPPED
    }
    when (result) {
        VaultWorkFactor.UPGRADED, VaultWorkFactor.CURRENT -> {
            progress.steps.remove(VAULT_WORK_FACTOR_BACKOFF)
            progress.steps[VAULT_WORK_FACTOR_STEP] = StepProgress(done = true)
        }
        VaultWorkFactor.CONFLICT, VaultWorkFactor.NOT_ADMITTED -> progress.steps.remove(VAULT_WORK_FACTOR_BACKOFF)
        VaultWorkFactor.SKIPPED -> {
            val delay = next_work_factor_delay(backoff)
            progress.steps[VAULT_WORK_FACTOR_BACKOFF] = StepProgress(cursor = "${now_ms() + delay}:$delay")
        }
    }
    runCatching { store.save(progress) }
    return result
}
