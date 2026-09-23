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
import org.junit.Assert.assertTrue
import org.junit.Test

class order_link_test {
    private val stripe_receipt_html = """
        <html><body>
        <table><tr><td>Receipt from Aster Privacy</td></tr>
        <tr><td><a href="https://stripe-images.s3.amazonaws.com/emails/receipt_assets/card/visa-dark@2x.png"><img src="https://stripe-images.s3.amazonaws.com/emails/receipt_assets/card/visa-dark@2x.png" alt=""></a> Visa - 4242</td></tr>
        <tr><td><a href="https://stripe.com">Powered by Stripe</a></td></tr>
        <tr><td><a href="https://pay.stripe.com/receipts/payment/CAcaFwoVYWNjdF8xTjVhYmM">Download receipt</a></td></tr>
        </table></body></html>
    """.trimIndent()

    @Test
    fun `picks the hosted stripe receipt over the card brand image`() {
        assertEquals(
            "https://pay.stripe.com/receipts/payment/CAcaFwoVYWNjdF8xTjVhYmM",
            extract_order_url("Receipt from Aster Privacy", stripe_receipt_html),
        )
    }

    @Test
    fun `reads anchors from an html body when no separate html part exists`() {
        assertEquals(
            "https://pay.stripe.com/receipts/payment/CAcaFwoVYWNjdF8xTjVhYmM",
            extract_order_url(stripe_receipt_html, null),
        )
    }

    @Test
    fun `picks a hosted receipt linked only through view in browser`() {
        val html = """
            <a href="https://cdn.example.com/order/logo.png"><img src="https://cdn.example.com/order/logo.png"></a>
            <a href="https://pay.stripe.com/receipts/acct_1/rcpt_2">View it in your browser</a>
        """.trimIndent()
        assertEquals("https://pay.stripe.com/receipts/acct_1/rcpt_2", extract_order_url("", html))
    }

    @Test
    fun `never picks an image only link even when its url mentions an order`() {
        val html = """<a href="https://shop.example.com/order/12345"><img src="https://shop.example.com/banner.jpg"></a>"""
        assertNull(extract_order_url("", html))
    }

    @Test
    fun `never picks a link to an image file`() {
        assertEquals(0, score_order_link("https://cdn.example.com/receipt/visa@2x.png", "Receipt"))
        assertEquals(0, score_order_link("https://cdn.example.com/invoice/brand.svg?v=2", "Invoice"))
    }

    @Test
    fun `prefers view order text over a generic link with an order url`() {
        val html = """
            <a href="https://shop.example.com/orders/help">Help</a>
            <a href="https://shop.example.com/account/abc">View order</a>
        """.trimIndent()
        assertEquals("https://shop.example.com/account/abc", extract_order_url("", html))
    }

    @Test
    fun `prefers the invoice link over a homepage link`() {
        val html = """
            <a href="https://shop.example.com/">Shop Example</a>
            <a href="https://invoice.stripe.com/i/acct_1/live_abc">View invoice</a>
        """.trimIndent()
        assertEquals("https://invoice.stripe.com/i/acct_1/live_abc", extract_order_url("", html))
    }

    @Test
    fun `skips tracking and unsubscribe links`() {
        val html = """
            <a href="https://shop.example.com/unsubscribe?order=1">Unsubscribe from order emails</a>
            <a href="https://shop.example.com/track/order/1">Track order</a>
            <a href="https://shop.example.com/orders/1">Order details</a>
        """.trimIndent()
        assertEquals("https://shop.example.com/orders/1", extract_order_url("", html))
    }

    @Test
    fun `falls back to a plain text order url`() {
        val body = "Thanks for your purchase. See https://shop.example.com/orders/991. Questions? Reply here."
        assertEquals("https://shop.example.com/orders/991", extract_order_url(body, null))
    }

    @Test
    fun `plain text fallback skips image urls`() {
        val body = "Card https://cdn.example.com/receipt_assets/visa.png then https://shop.example.com/receipt/7"
        assertEquals("https://shop.example.com/receipt/7", extract_order_url(body, null))
    }

    @Test
    fun `returns null when no link qualifies`() {
        val html = """<a href="https://shop.example.com/">Shop</a><a href="mailto:help@example.com">Order help</a>"""
        assertNull(extract_order_url("No links here", html))
    }

    @Test
    fun `scores a hosted stripe receipt above plain keyword links`() {
        val hosted = score_order_link("https://pay.stripe.com/receipts/acct/rcpt", "Receipt")
        val generic = score_order_link("https://shop.example.com/receipt/1", "Receipt")
        assertTrue(hosted > generic)
    }
}
