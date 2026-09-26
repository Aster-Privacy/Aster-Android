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

import java.time.Instant
import org.astermail.android.api.billing.BillingHistoryItem
import org.astermail.android.api.billing.PlanInfo
import org.astermail.android.api.billing.SubscriptionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResubscribeKindTest {

    private val now = Instant.parse("2026-09-24T12:00:00Z")

    private fun subscription(code: String, cancel_at_period_end: Boolean = false, stripe: Boolean? = null) =
        SubscriptionResponse(
            plan = PlanInfo(code = code),
            cancel_at_period_end = cancel_at_period_end,
            has_stripe_subscription = stripe,
        )

    private fun paid(period_end: String?, created_at: String = "2026-01-01T00:00:00Z", status: String = "paid") =
        BillingHistoryItem(status = status, plan_name = "Nova", period_end = period_end, created_at = created_at)

    @Test
    fun pending_stripe_cancellation_reactivates() {
        val kind = resubscribe_kind(subscription("nova", cancel_at_period_end = true, stripe = true), emptyList(), now)
        assertEquals(ResubscribeKind.Reactivate, kind)
    }

    @Test
    fun pending_cancellation_without_stripe_is_hidden() {
        assertNull(resubscribe_kind(subscription("nova", cancel_at_period_end = true, stripe = false), emptyList(), now))
    }

    @Test
    fun active_paid_plan_is_hidden() {
        assertNull(resubscribe_kind(subscription("nova", stripe = true), listOf(paid("2026-09-20T00:00:00Z")), now))
    }

    @Test
    fun plan_ended_within_sixty_days_offers_plans() {
        val kind = resubscribe_kind(subscription("free"), listOf(paid("2026-07-27T00:00:00Z")), now)
        assertEquals(ResubscribeKind.Choose, kind)
    }

    @Test
    fun plan_cancelled_early_with_future_period_end_offers_plans() {
        val kind = resubscribe_kind(subscription("free"), listOf(paid("2027-09-24T19:02:53.274066Z")), now)
        assertEquals(ResubscribeKind.Choose, kind)
    }

    @Test
    fun plan_ended_more_than_sixty_days_ago_is_hidden() {
        assertNull(resubscribe_kind(subscription("free"), listOf(paid("2026-07-25T00:00:00Z")), now))
    }

    @Test
    fun missing_period_end_falls_back_to_created_at() {
        val kind = resubscribe_kind(subscription("free"), listOf(paid(null, created_at = "2026-09-01T00:00:00+00:00")), now)
        assertEquals(ResubscribeKind.Choose, kind)
    }

    @Test
    fun unpaid_or_unparseable_history_is_hidden() {
        val history = listOf(paid("2026-09-20T00:00:00Z", status = "failed"), paid("not a date"))
        assertNull(resubscribe_kind(subscription("free"), history, now))
    }

    @Test
    fun free_user_who_never_paid_is_hidden() {
        assertNull(resubscribe_kind(subscription("free"), emptyList(), now))
    }
}
