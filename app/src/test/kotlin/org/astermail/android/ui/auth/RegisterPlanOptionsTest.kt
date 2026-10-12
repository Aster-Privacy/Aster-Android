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

package org.astermail.android.ui.auth

import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.billing.PlayOffer
import org.astermail.android.billing.apply_play_prices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun plan(
    code: String,
    price_cents: Int = 0,
    yearly_price_cents: Int = 0,
    billing_period: String? = "month",
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
    is_current = code == "free",
)

private val combined_plans = listOf(
    plan("free"),
    plan("supernova", price_cents = 1799, yearly_price_cents = 17990),
    plan("family", price_cents = 2699, yearly_price_cents = 26990),
    plan("star", price_cents = 299, yearly_price_cents = 2990),
    plan("duo", price_cents = 1299, yearly_price_cents = 12990),
    plan("nova", price_cents = 899, yearly_price_cents = 8990),
)

class RegisterPlanOptionsTest {

    @Test
    fun `offers the three individual plans in price order`() {
        assertEquals(listOf("star", "nova", "supernova"), signup_plan_options(combined_plans, "month").map { it.code })
        assertEquals(listOf("star", "nova", "supernova"), signup_plan_options(combined_plans, "year").map { it.code })
    }

    @Test
    fun `offers no yearly plans when none has a yearly price`() {
        val plans = combined_plans.map { it.copy(yearly_price_cents = 0) }

        assertTrue(signup_plan_options(plans, "year").isEmpty())
        assertEquals(3, signup_plan_options(plans, "month").size)
    }

    @Test
    fun `picks the plan row for the interval when plans are split by period`() {
        val plans = listOf(
            plan("free"),
            plan("nova", price_cents = 899, billing_period = "month"),
            plan("nova", price_cents = 8990, billing_period = "year"),
        )

        assertEquals(899, signup_plan_price_cents(signup_plan_options(plans, "month").single(), "month"))
        assertEquals(8990, signup_plan_price_cents(signup_plan_options(plans, "year").single(), "year"))
        assertEquals(16, signup_plan_save_percent(plans, "nova"))
    }

    @Test
    fun `reports the yearly saving for the selected plan only`() {
        assertEquals(16, signup_plan_save_percent(combined_plans, "nova"))
        assertNull(signup_plan_save_percent(combined_plans, "free"))
    }

    @Test
    fun `reports no saving when yearly costs as much as twelve months`() {
        val plans = listOf(plan("star", price_cents = 300, yearly_price_cents = 3600))

        assertNull(signup_plan_save_percent(plans, "star"))
    }

    @Test
    fun `offers only the plans Google Play sells`() {
        val products = listOf(
            GooglePlayProduct(product_id = "aster_nova", plan_code = "nova", base_plan_ids = listOf("monthly", "yearly")),
        )
        val offers = listOf(
            PlayOffer("aster_nova", "monthly", "token_m", "€8.99", 8_990_000L, "EUR", "month"),
            PlayOffer("aster_nova", "yearly", "token_y", "€89.99", 89_990_000L, "EUR", "year"),
        )

        val priced = apply_play_prices(combined_plans, offers, products)

        assertEquals(listOf("nova"), signup_plan_options(priced, "year").map { it.code })
        assertEquals(8999, signup_plan_price_cents(signup_plan_options(priced, "year").single(), "year"))
    }

    @Test
    fun `offers no paid plan when Google Play returns no offers`() {
        val priced = apply_play_prices(combined_plans, emptyList(), emptyList())

        assertTrue(signup_plan_options(priced, "month").isEmpty())
        assertTrue(signup_plan_options(priced, "year").isEmpty())
    }
}
