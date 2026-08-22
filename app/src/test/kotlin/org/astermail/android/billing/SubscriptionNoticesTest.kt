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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.BillingHistoryItem
import org.junit.Test

class SubscriptionNoticesTest {

    @Test
    fun `payment failed due date prefers grace period end`() {
        val due = payment_failed_due_date(
            status = "past_due",
            payment_failed_at = "2026-08-20T10:00:00Z",
            grace_period_end = "2026-08-27T10:00:00Z",
            current_period_end = "2026-09-01T10:00:00Z",
        )
        assertEquals("2026-08-27", due)
    }

    @Test
    fun `payment failed due date falls back to period end`() {
        val due = payment_failed_due_date(
            status = "active",
            payment_failed_at = "2026-08-20T10:00:00Z",
            grace_period_end = null,
            current_period_end = "2026-09-01T10:00:00Z",
        )
        assertEquals("2026-09-01", due)
    }

    @Test
    fun `no banner without a failed payment or for an ended subscription`() {
        assertNull(payment_failed_due_date("active", null, "2026-08-27", "2026-09-01"))
        assertNull(payment_failed_due_date("canceled", "2026-08-20", "2026-08-27", "2026-09-01"))
    }

    private val plans = listOf(
        AvailablePlan(code = "star", price_cents = 299, billing_period = "month"),
        AvailablePlan(code = "star", price_cents = 2899, billing_period = "year"),
        AvailablePlan(code = "free", price_cents = 0, billing_period = "month"),
    )

    @Test
    fun `normalizes the billing interval to month or year`() {
        assertEquals("year", normalize_billing_interval("year"))
        assertEquals("year", normalize_billing_interval("Yearly"))
        assertEquals("month", normalize_billing_interval("month"))
        assertEquals("month", normalize_billing_interval(null))
    }

    @Test
    fun `reads plan prices from the plans response`() {
        assertEquals(299, api_plan_price_cents(plans, "star", "month"))
        assertEquals(2899, api_plan_price_cents(plans, "star", "year"))
        assertNull(api_plan_price_cents(plans, "nova", "month"))
        assertNull(api_plan_price_cents(plans, "free", "month"))
    }

    @Test
    fun `yearly savings percent compares against twelve monthly payments`() {
        assertEquals(19, yearly_savings_percent(299, 2899))
        assertNull(yearly_savings_percent(299, 3588))
        assertNull(yearly_savings_percent(null, 2899))
    }

    private val history = listOf(
        BillingHistoryItem(id = "a", amount_cents = 899, plan_name = "Nova", period_end = "2026-06-01T00:00:00Z"),
        BillingHistoryItem(id = "b", amount_cents = 899, plan_name = "Nova", period_end = "2026-07-01T00:00:00Z"),
        BillingHistoryItem(id = "c", amount_cents = 0, plan_name = "Free", period_end = "2026-12-01T00:00:00Z"),
    )

    @Test
    fun `lapsed plan comes from the latest paid history item`() {
        val lapsed = lapsed_paid_plan("free", history, "2026-08-21")
        assertEquals(lapsed_plan(plan_name = "Nova", ended_on = "2026-07-01"), lapsed)
    }

    @Test
    fun `no lapsed plan while paid or before the period ends`() {
        assertNull(lapsed_paid_plan("nova", history, "2026-08-21"))
        assertNull(lapsed_paid_plan("free", history, "2026-06-15"))
        assertNull(lapsed_paid_plan("free", emptyList(), "2026-08-21"))
    }

    @Test
    fun `ranks every plan tier including duo and family`() {
        val ordered = listOf("free", "star", "nova", "duo", "supernova", "family").map { plan_tier_rank(it) }
        assertEquals(listOf(0, 1, 2, 3, 4, 5), ordered)
        assertTrue(plan_tier_rank("duo") > plan_tier_rank("nova"))
        assertTrue(plan_tier_rank("family") > plan_tier_rank("supernova"))
        assertEquals(-1, plan_tier_rank("unknown"))
        assertEquals(-1, plan_tier_rank(null))
    }

    @Test
    fun `derives plan codes from display names`() {
        assertEquals("family", plan_code_from_name("Aster Family"))
        assertEquals("duo", plan_code_from_name("Duo"))
        assertEquals("supernova", plan_code_from_name("Supernova"))
        assertEquals("nova", plan_code_from_name("Nova"))
        assertEquals("free", plan_code_from_name(null))
    }

    @Test
    fun `formats money with the subscription currency`() {
        assertEquals("€12.34", format_money(1234, "eur", java.util.Locale.US))
        assertEquals("$12.34", format_money(1234, "USD", java.util.Locale.US))
        assertTrue(format_money(1234, "usd", java.util.Locale.GERMANY).contains("12,34"))
        assertTrue(format_money(1234, "bogus", java.util.Locale.US).contains("12.34"))
    }
}
