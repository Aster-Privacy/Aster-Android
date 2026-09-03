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

import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.BillingHistoryItem

private val ACTIVE_STATUSES = setOf("active", "trialing", "past_due")
private val GOOD_STANDING_STATUSES = setOf("active", "trialing")
private val CRYPTO_PROVIDERS = setOf("stripe_crypto", "crypto_native")

fun is_crypto_provider(payment_provider: String?): Boolean =
    payment_provider?.trim()?.lowercase() in CRYPTO_PROVIDERS

fun can_offer_plan_alternatives(
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

fun payment_failed_due_date(
    status: String?,
    payment_failed_at: String?,
    grace_period_end: String?,
    current_period_end: String?,
    cancel_at_period_end: Boolean = false,
): String? {
    if (cancel_at_period_end) return null
    if (status !in ACTIVE_STATUSES) return null
    if (payment_failed_at.isNullOrBlank() && status != "past_due") return null
    val raw = grace_period_end?.takeIf { it.isNotBlank() } ?: current_period_end?.takeIf { it.isNotBlank() }
    return raw?.take(10) ?: ""
}

fun payment_failure_key(status: String?, payment_failed_at: String?, current_period_end: String?): String? {
    if (status !in ACTIVE_STATUSES) return null
    if (payment_failed_at.isNullOrBlank() && status != "past_due") return null
    return "${payment_failed_at.orEmpty()}|${current_period_end.orEmpty()}"
}

const val CRYPTO_RENEWAL_WINDOW_DAYS = 7L

fun crypto_renewal_due(
    payment_provider: String?,
    paid_until: String?,
    status: String?,
    today: String,
): Int? {
    if (!is_crypto_provider(payment_provider)) return null
    if (status !in ACTIVE_STATUSES) return null
    val end = paid_until?.takeIf { it.isNotBlank() }?.take(10) ?: return null
    val days = runCatching {
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(today), java.time.LocalDate.parse(end))
    }.getOrNull() ?: return null
    return if (days in 0..CRYPTO_RENEWAL_WINDOW_DAYS) days.toInt() else null
}

fun lapse_dismissal_key(lapsed: lapsed_plan): String = "${lapsed.plan_name}|${lapsed.ended_on}"

fun alias_limit_near(current: Int?, limit: Int?): Boolean {
    if (current == null || limit == null || limit <= 0) return false
    return current * 100 >= limit * 80 && current < limit
}

private val TOP_PLAN_CODES = setOf("supernova", "family_full")

fun has_higher_plan_tier(plan_code: String?): Boolean {
    val code = plan_code?.trim()?.lowercase().orEmpty()
    if (code.isBlank()) return true
    return code !in TOP_PLAN_CODES
}

val CANCEL_REASONS = listOf(
    "too_expensive",
    "not_using",
    "missing_feature",
    "switched_provider",
    "bugs",
    "privacy_trust",
    "just_testing",
    "other",
)

const val MAX_CANCEL_REASON_TEXT = 2000

fun clamp_cancel_reason_text(input: String?): String? =
    input?.trim()?.take(MAX_CANCEL_REASON_TEXT)?.takeIf { it.isNotEmpty() }

fun payment_failed_days_left(due_date: String?, today: String): Int? {
    val due = due_date?.takeIf { it.length >= 10 } ?: return null
    val days = try {
        java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(today.take(10)), java.time.LocalDate.parse(due.take(10)))
    } catch (_: Throwable) {
        return null
    }
    return days.toInt().takeIf { it >= 1 }
}

fun normalize_billing_interval(raw: String?): String {
    val value = raw?.trim()?.lowercase().orEmpty()
    return when {
        value.isBlank() -> "month"
        value.startsWith("year") || value == "annual" || value == "annually" || value == "yearly" -> "year"
        value.startsWith("month") || value == "monthly" -> "month"
        else -> value
    }
}

fun api_plan_price_cents(plans: List<AvailablePlan>, code: String, billing_interval: String): Int? {
    val period_match = plans.firstOrNull {
        it.code == code && it.billing_period == billing_interval && it.price_cents > 0
    }
    if (period_match != null) return period_match.price_cents
    val plan = plans.firstOrNull { it.code == code } ?: return null
    val cents = if (billing_interval == "year") plan.yearly_price_cents else plan.price_cents
    return cents.takeIf { it > 0 }
}

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

private val INDIVIDUAL_PLAN_LADDER = listOf("star", "nova", "supernova")

private val FAMILY_PLAN_LADDER = listOf("duo", "family")

fun cheaper_plan_code(code: String?): String? {
    val normalized = code?.trim()?.lowercase() ?: return null
    for (ladder in listOf(INDIVIDUAL_PLAN_LADDER, FAMILY_PLAN_LADDER)) {
        val index = ladder.indexOf(normalized)
        if (index > 0) return ladder[index - 1]
    }

    return null
}

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

fun required_plan_name(from_payload: String?, fallback: String): String =
    from_payload?.trim()?.takeIf { it.isNotBlank() } ?: fallback

fun plan_display_name(plans: List<AvailablePlan>, code: String): String? =
    plans.firstOrNull { it.code.equals(code, ignoreCase = true) && it.name.isNotBlank() }?.name
