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

enum class VaultWorkFactor { UPGRADED, CURRENT, CONFLICT, NOT_ADMITTED, SKIPPED }

suspend fun run_vault_work_factor_step(
    admitted: Boolean,
    password_ready: () -> Boolean,
    store: ProgressStore,
    raise: suspend () -> VaultWorkFactor,
): VaultWorkFactor {
    if (!admitted || !password_ready()) return VaultWorkFactor.SKIPPED
    val progress = runCatching { store.load() }.getOrNull() ?: ResealProgress(VAULT_WORK_FACTOR_STEP)
    if (progress.steps[VAULT_WORK_FACTOR_STEP]?.done == true) return VaultWorkFactor.CURRENT
    val result = try {
        raise()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        VaultWorkFactor.SKIPPED
    }
    if (result == VaultWorkFactor.UPGRADED || result == VaultWorkFactor.CURRENT) {
        progress.steps[VAULT_WORK_FACTOR_STEP] = StepProgress(done = true)
        runCatching { store.save(progress) }
    }
    return result
}
