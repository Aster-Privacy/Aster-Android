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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class purchase_scan_time_test {

    private fun receipt(body: String, html: String? = null) =
        extract_purchase_details("Your receipt", "Order #AB-12345\n$body", "orders@shop.com", "Shop", Locale.US, html)

    @Test(timeout = 2_000)
    fun `long digit group runs finish quickly`() {
        receipt("Total: 1" + ",000".repeat(20_000) + "%")
        receipt("Total: " + "1".repeat(100_000) + "%")
        receipt("Total: " + "111 ".repeat(30_000) + "%")
        receipt("Tax 12." + "1".repeat(60_000) + "%")
    }

    @Test(timeout = 2_000)
    fun `long whitespace runs after labels finish quickly`() {
        val spaces = " ".repeat(50_000)
        receipt("Total$spaces:$spaces-${spaces}x")
        receipt("Order #$spaces")
        receipt("Order number$spaces#${spaces}x")
        receipt("Promo$spaces code$spaces applied${spaces}x")
        receipt("Tax$spaces($spaces")
        assertFalse(is_purchase_email("Purchase confirmation", "order total$spaces:${spaces}x"))
        assertFalse(is_purchase_email("Order #", "$spaces#${spaces}x"))
    }

    @Test(timeout = 2_000)
    fun `long item like lines finish quickly`() {
        receipt("1 x " + "a - ".repeat(25_000))
        receipt("a".repeat(100_000))
        receipt(" ".repeat(100_000))
        receipt("Thing" + " ".repeat(50_000) + "(Qty")
        receipt("Item: ".repeat(20_000))
        receipt("ab" + " ".repeat(50_000) + "x 2 $5")
    }

    @Test(timeout = 2_000)
    fun `long forwarded headers finish quickly`() {
        assertNull(detect_forwarded_origin("\nfrom:" + " ".repeat(50_000)))
        assertNull(detect_forwarded_origin("from:" + "\n".repeat(50_000)))
        assertNull(detect_forwarded_origin("from: " + "a".repeat(100_000)))
    }

    @Test(timeout = 2_000)
    fun `unclosed anchors in receipt markup finish quickly`() {
        assertNull(extract_receipt_url("x", "<a ".repeat(60_000), listOf("hello@shop.example.com")))
        assertNull(extract_receipt_url("x", "<a href ".repeat(20_000) + ">", listOf("hello@shop.example.com")))
        assertNull(
            extract_receipt_url(
                "x",
                "<a href='https://orders.shop.example.com/r/1'>" + "<".repeat(150_000) + "</a>",
                listOf("hello@shop.example.com"),
            ),
        )
        receipt("x", "<a href=\"https://shop.example.com\">".repeat(5_000))
    }

    @Test
    fun `large grouped amounts are still read`() {
        assertEquals(1234567.89, receipt("Total: $1,234,567.89").total!!.value, 0.0001)
        assertEquals(1234567.0, receipt("Total: ₹12,34,567").total!!.value, 0.0001)
        assertEquals(3.30, receipt("Tax (8.25%): $3.30\nTotal: $43.30").tax!!.value, 0.0001)
    }

    @Test
    fun `items are still read from every line format`() {
        val names = receipt(
            "Mug (Qty: 2) $8.00\nPlate (Qty: 1) $5.00\nTotal: $13.00",
        ).items.map { it.name }
        assertTrue(names.toString(), "Mug" in names && "Plate" in names)

        val listed = receipt("Item: Blue Mug Qty: 2 Price: $8.00\nTotal: $16.00").items
        assertEquals("Blue Mug", listed[0].name)
        assertEquals(2, listed[0].quantity)

        val counted = receipt("Coffee beans × 2 $24.00\nTotal: $24.00").items
        assertEquals("Coffee beans", counted[0].name)
        assertEquals(2, counted[0].quantity)
        assertEquals(12.0, counted[0].unit_price!!.value, 0.0001)

        val leading = receipt("2 x Ceramic Bowl - $9.00\nTotal: $18.00").items
        assertEquals("Ceramic Bowl", leading[0].name)
        assertEquals(18.0, leading[0].total_price!!.value, 0.0001)
    }

    @Test
    fun `receipt anchors are still found`() {
        val url = extract_receipt_url(
            "View your receipt",
            "<a name=\"top\">x</a><abbr>y</abbr><p>Thanks</p>" +
                "<A class=\"btn\" HREF='https://orders.shop.example.com/r/7'><span>View your</span> <b>receipt</b></A>",
            listOf("hello@shop.example.com"),
        )
        assertEquals("https://orders.shop.example.com/r/7", url)
        assertEquals(
            "https://pay.stripe.com/receipts/payment/abc",
            extract_receipt_url(
                "x",
                "<a href=\"https://pay.stripe.com/receipts/payment/abc\">Open</a>",
                listOf("hello@shop.example.com"),
            ),
        )
    }

    @Test
    fun `forwarded origins and purchase checks still work`() {
        val origin = detect_forwarded_origin(
            "Hi\n\n---------- Forwarded message ---------\nFrom: Shop Orders <orders@shop.com>\n" +
                "Date: Mon, 1 Jan 2026\nSubject: Fwd: Your order\nTo: me@example.com\n",
        )
        assertNotNull(origin)
        assertEquals("orders@shop.com", origin!!.from_email)
        assertEquals("Shop Orders", origin.from_name)
        assertEquals("Your order", origin.subject)
        assertTrue(is_purchase_email("Order #12345 confirmed", "Order total: $24.00"))
        assertTrue(is_purchase_email("Purchase confirmation", "Thanks for your order. Order total: $24.00"))
    }
}
