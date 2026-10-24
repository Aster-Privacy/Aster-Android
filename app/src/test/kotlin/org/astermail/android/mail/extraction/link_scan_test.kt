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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkScanTest {

    private fun elapsed_ms(block: () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    @Test
    fun `a long run of repeated schemes is scanned in linear time`() {
        val hostile = "http://".repeat(400_000 / 7)
        val ms = elapsed_ms {
            assertNull(extract_tracking_url(hostile, null))
            assertNull(extract_tracking_url(hostile, "<div>$hostile</div>"))
            assertNull(extract_order_url(hostile, null))
        }
        assertTrue("took $ms ms", ms < 3_000)
    }

    @Test
    fun `a hostile shipping message is parsed quickly end to end`() {
        val hostile = "Your package has shipped. Track it here: " + "http://".repeat(60_000)
        val ms = elapsed_ms {
            extract_shipping_details("Your order has shipped", hostile, "<p>$hostile</p>", "ship@example.com")
        }
        assertTrue("took $ms ms", ms < 5_000)
    }

    @Test
    fun `the tracking link comes from an anchor, not an image`() {
        val html = """
            <img src="https://cdn.example.com/track/pixel.gif">
            <img src="https://cdn.example.com/tracking/banner.png">
            <a href="https://shop.example.com/orders/1/track">Track your package</a>
        """.trimIndent()
        assertEquals("https://shop.example.com/orders/1/track", extract_tracking_url("", html))
    }

    @Test
    fun `tracking keywords keep their priority order`() {
        val html = """
            <a href="https://shop.example.com/delivery/info">Delivery info</a>
            <a href="https://shop.example.com/shipment/7">Shipment</a>
            <a href="https://carrier.example.com/track?id=7">Track</a>
        """.trimIndent()
        assertEquals("https://carrier.example.com/track?id=7", extract_tracking_url("", html))
    }

    @Test
    fun `a plain text tracking link still works and loses trailing junk`() {
        assertEquals(
            "https://carrier.example.com/track?id=9",
            extract_tracking_url("Follow it at https://carrier.example.com/track?id=9\"] today", null),
        )
    }

    @Test
    fun `html without a tracking anchor falls back to the text body`() {
        assertEquals(
            "https://carrier.example.com/track?id=9",
            extract_tracking_url(
                "See https://carrier.example.com/track?id=9",
                "<img src=\"https://cdn.example.com/track.png\"><p>See below</p>",
            ),
        )
    }

    @Test
    fun `the keyword must follow the scheme`() {
        assertNull(extract_tracking_url("https://track", null))
        assertFalse(contains_after_scheme("https://track", "track"))
        assertTrue(contains_after_scheme("https://xtrack", "track"))
        assertTrue(contains_after_scheme("HTTPS://example.com/TRACK", "track"))
    }

    @Test
    fun `non web anchors and oversized links are ignored`() {
        val long = "https://example.com/track/" + "a".repeat(link_scan_max_url_length)
        val html = """<a href="javascript:track()">x</a><a href="$long">y</a>"""
        assertNull(extract_tracking_url("", html))
    }

    @Test
    fun `plain text order links still resolve`() {
        assertEquals(
            "https://shop.example.com/orders/123",
            extract_order_url("View it at https://shop.example.com/orders/123.", null),
        )
        assertNull(extract_order_url("Track it at https://shop.example.com/orders/123/track", null))
    }
}
