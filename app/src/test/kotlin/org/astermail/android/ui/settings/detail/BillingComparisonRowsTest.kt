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

package org.astermail.android.ui.settings.detail

import java.io.File
import org.astermail.android.billing.parse_plan_comparison
import org.astermail.android.billing.plan_comparison_feed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingComparisonRowsTest {
    private val feed: plan_comparison_feed =
        parse_plan_comparison(File("src/main/assets/plan_comparison/en.json").readText())!!

    @Test
    fun advantage_rows_come_from_the_feed() {
        val rows = billing_advantage_rows_from_feed("nova", feed)
        assertEquals(5, rows.size)
        val storage = rows.first()
        assertEquals("500 GB", storage.paid_value)
        assertEquals("10 GB", storage.free_value)
        val external = rows.last()
        assertEquals("5", external.paid_value)
        assertEquals(null, external.free_value)
    }

    @Test
    fun advantage_rows_skip_plans_the_feed_does_not_know() {
        assertTrue(billing_advantage_rows_from_feed("unknown", feed).isEmpty())
    }

    @Test
    fun row_differs_only_when_values_change_across_columns() {
        val codes = listOf("free", "star", "nova", "supernova")
        assertTrue(compare_row_differs(feed.row("storage")!!, codes))
        assertFalse(compare_row_differs(feed.row("key_rotation")!!, codes))
        assertFalse(compare_row_differs(feed.row("storage")!!, listOf("nova")))
    }
}
