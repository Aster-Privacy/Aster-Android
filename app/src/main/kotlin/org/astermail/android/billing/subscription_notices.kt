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
import org.astermail.android.api.billing.AvailablePlan

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

fun api_plan_price_cents(plans: List<AvailablePlan>, code: String, billing_interval: String): Int? =
    plans.firstOrNull { it.code == code && it.billing_period == billing_interval && it.price_cents > 0 }?.price_cents

fun yearly_savings_percent(monthly_cents: Int?, yearly_cents: Int?): Int? {
    if (monthly_cents == null || yearly_cents == null) return null
    val full_year = monthly_cents * 12
    if (full_year <= 0 || yearly_cents >= full_year) return null
    return ((full_year - yearly_cents) * 100.0 / full_year).toInt()
}
