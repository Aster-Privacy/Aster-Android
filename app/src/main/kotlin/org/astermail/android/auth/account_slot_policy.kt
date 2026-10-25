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


package org.astermail.android.auth

sealed interface AccountSlotLink {
    data class Linked(val max_accounts: Int) : AccountSlotLink
    data object LimitReached : AccountSlotLink
    data object Unknown : AccountSlotLink
}

enum class AccountSlotDecision { Allow, Reject }

fun decide_account_slot(
    link: AccountSlotLink,
    account_known: Boolean,
    can_add_locally: Boolean,
): AccountSlotDecision = when (link) {
    is AccountSlotLink.Linked -> AccountSlotDecision.Allow
    AccountSlotLink.LimitReached -> if (account_known) AccountSlotDecision.Allow else AccountSlotDecision.Reject
    AccountSlotLink.Unknown -> if (account_known || can_add_locally) AccountSlotDecision.Allow else AccountSlotDecision.Reject
}
