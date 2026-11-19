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

package org.astermail.android.security

internal class AppLockDisableSteps(
    val unseal: () -> Boolean,
    val reseal: () -> Unit,
    val close_key: () -> Unit,
    val reopen_key: () -> Unit,
    val write_marker: (Boolean) -> Boolean,
    val clear_pin: () -> Boolean,
)

internal fun run_app_lock_disable(steps: AppLockDisableSteps): Boolean {
    if (!steps.unseal()) {
        steps.reseal()
        return false
    }
    steps.close_key()
    if (!steps.unseal()) {
        steps.reopen_key()
        return false
    }
    if (!steps.write_marker(false)) {
        steps.reopen_key()
        return false
    }
    if (!steps.clear_pin()) {
        steps.write_marker(true)
        steps.reopen_key()
        return false
    }
    return true
}
