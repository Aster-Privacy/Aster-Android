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

package org.astermail.android.mail.ratchet

enum class IdentityPinDecision {
    PIN_FIRST,
    KEEP,
    FLAG_DRIFT,
    REPLACE,
}

object RatchetIdentityPinRules {

    private const val account_prefix = "acct/"

    fun decide(stored_fingerprint: String?, current_fingerprint: String, confirmed: Boolean): IdentityPinDecision = when {
        stored_fingerprint.isNullOrBlank() -> IdentityPinDecision.PIN_FIRST
        stored_fingerprint == current_fingerprint -> IdentityPinDecision.KEEP
        confirmed -> IdentityPinDecision.REPLACE
        else -> IdentityPinDecision.FLAG_DRIFT
    }

    fun pq_prekey_accepts(consumed_by_ephemeral_key: String?, ephemeral_key: String): Boolean =
        consumed_by_ephemeral_key.isNullOrBlank() || consumed_by_ephemeral_key == ephemeral_key

    fun scoped_key(account_id: String?, legacy_key: String): String {
        val scope = account_id?.trim().orEmpty()
        return if (scope.isEmpty()) legacy_key else "$account_prefix$scope/$legacy_key"
    }

    fun account_key_prefix(account_id: String): String = "$account_prefix${account_id.trim()}/"

    fun belongs_to_account(key: String, account_id: String): Boolean =
        account_id.isNotBlank() && key.startsWith(account_key_prefix(account_id))
}
