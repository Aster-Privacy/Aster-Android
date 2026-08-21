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
}
