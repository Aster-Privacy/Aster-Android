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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

val STORAGE_KEY_PENDING_RETRY_DELAYS_SECS = longArrayOf(60, 300, 900, 1800)

fun is_storage_key_pending(outcome: StorageVaultOutcome): Boolean =
    outcome is StorageVaultOutcome.Skipped && outcome.reason == VAULT_CAS_UNSUPPORTED

object StorageKeyPendingNotice {
    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending.asStateFlow()
    private var announced = false

    fun observe(outcome: StorageVaultOutcome) {
        synchronized(this) {
            if (is_storage_key_pending(outcome)) {
                if (!announced) _pending.value = true
                announced = true
            } else if (outcome !is StorageVaultOutcome.Skipped) {
                announced = false
                _pending.value = false
            }
        }
    }

    fun consume() {
        _pending.value = false
    }

    fun reset() {
        synchronized(this) {
            announced = false
            _pending.value = false
        }
    }
}
