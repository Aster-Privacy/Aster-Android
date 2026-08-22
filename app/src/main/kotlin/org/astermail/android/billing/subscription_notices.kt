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
import org.astermail.android.api.billing.BillingHistoryItem

private val ACTIVE_STATUSES = setOf("active", "trialing", "past_due")
private val GOOD_STANDING_STATUSES = setOf("active", "trialing")
private val CRYPTO_PROVIDERS = setOf("stripe_crypto", "crypto_native")

fun is_crypto_provider(payment_provider: String?): Boolean =
    payment_provider?.trim()?.lowercase() in CRYPTO_PROVIDERS

fun can_offer_save_offers(
    plan_code: String?,
    status: String?,
    payment_failed_at: String?,
    grace_period_end: String?,
    cancel_at_period_end: Boolean,
    payment_provider: String?,
    has_stripe_subscription: Boolean?,
): Boolean {
    if (plan_code.isNullOrBlank() || plan_code == "free") return false
    if (status?.trim()?.lowercase() !in GOOD_STANDING_STATUSES) return false
    if (!payment_failed_at.isNullOrBlank() || !grace_period_end.isNullOrBlank()) return false
    if (cancel_at_period_end) return false
    if (is_crypto_provider(payment_provider)) return false
    if (has_stripe_subscription == false) return false
    return true
}

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

fun normalize_billing_interval(raw: String?): String =
    if (raw?.trim()?.lowercase()?.startsWith("year") == true) "year" else "month"

fun api_plan_price_cents(plans: List<AvailablePlan>, code: String, billing_interval: String): Int? =
    plans.firstOrNull { it.code == code && it.billing_period == billing_interval && it.price_cents > 0 }?.price_cents

fun yearly_savings_percent(monthly_cents: Int?, yearly_cents: Int?): Int? {
    if (monthly_cents == null || yearly_cents == null) return null
    val full_year = monthly_cents * 12
    if (full_year <= 0 || yearly_cents >= full_year) return null
    return ((full_year - yearly_cents) * 100.0 / full_year).toInt()
}

data class lapsed_plan(val plan_name: String, val ended_on: String)

fun lapsed_paid_plan(
    current_plan_code: String,
    history: List<BillingHistoryItem>,
    today: String,
): lapsed_plan? {
    if (current_plan_code != "free") return null
    val latest = history
        .filter { it.amount_cents > 0 && !it.plan_name.isNullOrBlank() && !it.period_end.isNullOrBlank() }
        .maxByOrNull { it.period_end.orEmpty() } ?: return null
    val ended_on = latest.period_end.orEmpty().take(10)
    if (ended_on >= today.take(10)) return null
    return lapsed_plan(plan_name = latest.plan_name.orEmpty(), ended_on = ended_on)
}

private val PLAN_TIER_RANKS = mapOf(
    "free" to 0,
    "star" to 1,
    "nova" to 2,
    "duo" to 3,
    "supernova" to 4,
    "family" to 5,
)

fun plan_tier_rank(code: String?): Int = PLAN_TIER_RANKS[code?.trim()?.lowercase()] ?: -1

fun plan_code_from_name(plan_name: String?): String {
    val lower = plan_name?.trim()?.lowercase().orEmpty()
    return when {
        lower.contains("supernova") -> "supernova"
        lower.contains("family") -> "family"
        lower.contains("duo") -> "duo"
        lower.contains("nova") -> "nova"
        lower.contains("star") -> "star"
        lower.isBlank() || lower.contains("free") -> "free"
        else -> lower
    }
}
