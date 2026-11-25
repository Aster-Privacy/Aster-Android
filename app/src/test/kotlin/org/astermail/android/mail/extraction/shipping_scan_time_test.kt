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

package org.astermail.android.mail.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class shipping_scan_time_test {

    private fun generic_number(body: String): String? =
        extract_shipping_details("Update", body, null, "noreply@shop.example").tracking_number

    @Test(timeout = 2_000)
    fun `long whitespace after tracking words finishes quickly`() {
        assertNull(generic_number("tracking" + " ".repeat(30_000) + "x"))
        assertNull(generic_number("tracking number" + " ".repeat(30_000) + "x"))
    }

    @Test(timeout = 2_000)
    fun `long letter runs after date words finish quickly`() {
        val letters = "a".repeat(100_000)
        assertNull(extract_estimated_delivery("Estimated delivery $letters"))
        assertNull(extract_estimated_delivery("Arriving by $letters"))
        val result = extract_shipping_details("Shipped", "shipped on $letters delivered $letters", null, "x@shop.example")
        assertNull(result.shipped_date)
    }

    @Test(timeout = 2_000)
    fun `long quote runs inside links finish quickly`() {
        val token = "https://carrier.example.com/track" + "'".repeat(2_000) + "x"
        assertEquals(token, extract_tracking_url("$token ".repeat(90), null))
    }

    @Test
    fun `generic tracking numbers are still read`() {
        assertEquals("ABCD12345678", generic_number("Your tracking number: ABCD12345678 is ready"))
        assertEquals("ABCD12345678", generic_number("tracking # ABCD12345678"))
        assertEquals("ABCD12345678", generic_number("tracking:ABCD12345678"))
        assertEquals("ABCD12345678", generic_number("Tracking ABCD12345678"))
        assertEquals("ABCD12345678", generic_number("tracking no. ABCD12345678"))
        assertEquals("ABCD12345678", generic_number("Suivi : ABCD12345678"))
        assertEquals("number12345678", generic_number("tracking number12345678"))
        assertNull(generic_number("trackingnumber12345678"))
        assertNull(generic_number("tracking #ABCD12345678"))
    }

    @Test
    fun `delivery dates are still read`() {
        assertEquals("Friday, January 5, 2027", extract_estimated_delivery("Estimated delivery: Friday, January 5, 2027"))
        assertEquals("January 5", extract_estimated_delivery("Arriving by January 5"))
        assertEquals("Mon Jan 5", extract_estimated_delivery("Delivery by Mon Jan 5"))
        assertEquals("Fri,Jan 5", extract_estimated_delivery("Expected delivery Fri,Jan 5"))
        assertNull(extract_estimated_delivery("Estimated delivery: A 5"))
    }

    @Test
    fun `trailing link punctuation is still removed`() {
        assertEquals(
            "https://carrier.example.com/track?id=9",
            extract_tracking_url("See https://carrier.example.com/track?id=9'] now", null),
        )
    }
}
