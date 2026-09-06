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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun plan(
    code: String,
    price_cents: Int = 0,
    yearly_price_cents: Int = 0,
    billing_period: String? = "month",
    is_current: Boolean = false,
) = AvailablePlan(
    id = code,
    code = code,
    name = code.replaceFirstChar { it.uppercase() },
    storage_limit_bytes = 1024L * 1024L * 1024L,
    max_email_aliases = 5,
    max_custom_domains = 1,
    price_cents = price_cents,
    yearly_price_cents = yearly_price_cents,
    billing_period = billing_period,
    is_current = is_current,
)

private val combined_plans = listOf(
    plan("free", is_current = true),
    plan("star", price_cents = 300, yearly_price_cents = 2880),
    plan("nova", price_cents = 600, yearly_price_cents = 5760),
    plan("supernova", price_cents = 1200, yearly_price_cents = 11520),
    plan("duo", price_cents = 900, yearly_price_cents = 8640),
    plan("family", price_cents = 1800, yearly_price_cents = 17280),
)

class UpgradePlanOptionsTest {

    @Test
    fun `offers paid individual plans above the current plan`() {
        val options = upgrade_plan_options(combined_plans, "month")

        assertEquals(listOf("star", "nova", "supernova"), options.map { it.code })
    }

    @Test
    fun `hides plans at or below the current plan`() {
        val plans = combined_plans.map {
            if (it.code == "nova") it.copy(is_current = true) else it.copy(is_current = false)
        }

        val options = upgrade_plan_options(plans, "month")

        assertEquals(listOf("supernova"), options.map { it.code })
    }

    @Test
    fun `never offers family plans in the sheet`() {
        val options = upgrade_plan_options(combined_plans, "year")

        assertFalse(options.any { it.code == "duo" || it.code == "family" })
    }

    @Test
    fun `caps the number of options`() {
        val plans = listOf(
            plan("free", is_current = true),
            plan("a", price_cents = 100),
            plan("b", price_cents = 200),
            plan("c", price_cents = 300),
            plan("d", price_cents = 400),
        )

        assertEquals(3, upgrade_plan_options(plans).size)
    }

    @Test
    fun `keeps only rows for the selected period when plans are split`() {
        val plans = listOf(
            plan("free", is_current = true),
            plan("star", price_cents = 300, billing_period = "month"),
            plan("star", price_cents = 2880, billing_period = "year"),
            plan("nova", price_cents = 600, billing_period = "month"),
            plan("nova", price_cents = 5760, billing_period = "year"),
        )

        assertTrue(upgrade_plans_split_by_period(plans))
        assertEquals(
            listOf(300, 600),
            upgrade_plan_options(plans, "month").map { it.price_cents },
        )
        assertEquals(
            listOf(2880, 5760),
            upgrade_plan_options(plans, "year").map { it.price_cents },
        )
    }

    @Test
    fun `returns nothing when there is nothing to upgrade to`() {
        val plans = listOf(
            plan("free"),
            plan("supernova", price_cents = 1200, is_current = true),
        )

        assertTrue(upgrade_plan_options(plans, "month").isEmpty())
        assertTrue(upgrade_plan_options(emptyList(), "month").isEmpty())
    }

    @Test
    fun `detects available billing periods`() {
        assertTrue(upgrade_has_monthly(combined_plans))
        assertTrue(upgrade_has_yearly(combined_plans))

        val monthly_only = listOf(plan("star", price_cents = 300))
        assertTrue(upgrade_has_monthly(monthly_only))
        assertFalse(upgrade_has_yearly(monthly_only))
    }

    @Test
    fun `computes the best yearly saving`() {
        assertEquals(20, upgrade_yearly_save_percent(combined_plans))

        val split = listOf(
            plan("star", price_cents = 300, billing_period = "month"),
            plan("star", price_cents = 2880, billing_period = "year"),
        )
        assertEquals(20, upgrade_yearly_save_percent(split))

        assertEquals(0, upgrade_yearly_save_percent(listOf(plan("star", price_cents = 300))))
    }

    @Test
    fun `monthly equivalent normalizes yearly rows`() {
        assertEquals(300, plan_monthly_equivalent_cents(plan("star", price_cents = 300)))
        assertEquals(
            240,
            plan_monthly_equivalent_cents(plan("star", price_cents = 2880, billing_period = "year")),
        )
        assertEquals(0, plan_monthly_equivalent_cents(plan("free")))
    }
}
