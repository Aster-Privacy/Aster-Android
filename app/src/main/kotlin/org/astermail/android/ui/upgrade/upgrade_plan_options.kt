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

package org.astermail.android.ui.upgrade

import org.astermail.android.api.billing.AvailablePlan

private val family_plan_codes = setOf("duo", "family")

const val UPGRADE_PLAN_OPTION_LIMIT = 3

fun plan_monthly_equivalent_cents(plan: AvailablePlan): Int = when {
    plan.billing_period == "year" && plan.price_cents > 0 -> plan.price_cents / 12
    plan.price_cents > 0 -> plan.price_cents
    plan.yearly_price_cents > 0 -> plan.yearly_price_cents / 12
    else -> 0
}

fun upgrade_plans_split_by_period(plans: List<AvailablePlan>): Boolean =
    plans.any { it.billing_period == "year" && it.price_cents > 0 }

fun upgrade_has_yearly(plans: List<AvailablePlan>): Boolean =
    upgrade_plans_split_by_period(plans) || plans.any { it.yearly_price_cents > 0 }

fun upgrade_has_monthly(plans: List<AvailablePlan>): Boolean =
    plans.any { (it.billing_period ?: "month") == "month" && it.price_cents > 0 }

fun upgrade_plan_options(
    plans: List<AvailablePlan>,
    interval: String = "month",
    limit: Int = UPGRADE_PLAN_OPTION_LIMIT,
): List<AvailablePlan> {
    if (plans.isEmpty()) return emptyList()

    val for_interval = if (!upgrade_plans_split_by_period(plans)) {
        plans
    } else {
        plans.filter {
            val period = it.billing_period ?: "month"
            if (interval == "year") period == "year" else period == "month"
        }
    }
    val current_cents = plans
        .filter { it.is_current }
        .maxOfOrNull { plan_monthly_equivalent_cents(it) } ?: 0

    return for_interval
        .filter { it.code.lowercase() !in family_plan_codes }
        .filter { !it.is_current }
        .filter { plan_monthly_equivalent_cents(it) > current_cents }
        .sortedBy { plan_monthly_equivalent_cents(it) }
        .take(limit)
}

fun upgrade_yearly_save_percent(plans: List<AvailablePlan>): Int {
    var best = 0
    if (upgrade_plans_split_by_period(plans)) {
        val yearly = plans
            .filter { it.billing_period == "year" && it.price_cents > 0 }
            .associateBy { it.code }
        plans
            .filter { (it.billing_period ?: "month") == "month" && it.price_cents > 0 }
            .forEach { monthly ->
                val match = yearly[monthly.code] ?: return@forEach
                best = maxOf(best, yearly_save_percent(monthly.price_cents, match.price_cents))
            }
    } else {
        plans.forEach { plan ->
            best = maxOf(best, yearly_save_percent(plan.price_cents, plan.yearly_price_cents))
        }
    }
    return best
}

private fun yearly_save_percent(monthly_cents: Int, yearly_cents: Int): Int {
    val full_year = monthly_cents * 12
    if (monthly_cents <= 0 || yearly_cents <= 0 || yearly_cents >= full_year) return 0
    return ((full_year - yearly_cents) * 100) / full_year
}
