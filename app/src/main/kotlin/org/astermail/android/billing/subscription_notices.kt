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

package org.astermail.android.billing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

private val ACTIVE_STATUSES = setOf("active", "trialing", "past_due")

object payment_failed_banner_session {
    var dismissed by mutableStateOf(false)
}

fun payment_failed_due_date(
    status: String?,
    payment_failed_at: String?,
    grace_period_end: String?,
    current_period_end: String?,
): String? {
    if (payment_failed_at.isNullOrBlank()) return null
    if (status !in ACTIVE_STATUSES) return null
    val raw = grace_period_end?.takeIf { it.isNotBlank() } ?: current_period_end?.takeIf { it.isNotBlank() }
    return raw?.take(10)
}
