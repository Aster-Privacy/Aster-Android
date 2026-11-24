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

package org.astermail.android.settings

data class primary_address_eligibility_snapshot(
    val eligible: Boolean,
    val lock_reason: String?,
    val current_address: String,
    val next_change_available_at: String?,
    val renames_allowed: Int,
)

internal class primary_address_eligibility_memo {
    private var key: String? = null
    private var value: primary_address_eligibility_snapshot? = null

    @Synchronized
    fun read(account_key: String?): primary_address_eligibility_snapshot? {
        if (account_key.isNullOrBlank()) return null
        return if (key == account_key) value else null
    }

    @Synchronized
    fun write(account_key: String?, snapshot: primary_address_eligibility_snapshot) {
        if (account_key.isNullOrBlank()) return
        key = account_key
        value = snapshot
    }

    @Synchronized
    fun clear() {
        key = null
        value = null
    }
}

object primary_address_eligibility_cache {
    internal val memo = primary_address_eligibility_memo()
}

fun PrimaryAddressUiState.with_eligibility(
    snapshot: primary_address_eligibility_snapshot?,
): PrimaryAddressUiState {
    if (snapshot == null) return this
    return copy(
        eligible = snapshot.eligible,
        lock_reason = snapshot.lock_reason,
        current_address = snapshot.current_address,
        next_change_available_at = snapshot.next_change_available_at,
        renames_allowed = snapshot.renames_allowed,
        eligibility_failed = false,
        eligibility_checked = true,
    )
}
