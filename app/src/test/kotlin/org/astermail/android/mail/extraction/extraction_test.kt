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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class extraction_test {
    private fun purchase(subject: String, body: String, from_email: String, from_name: String) =
        extract_purchase_details(subject, body, from_email, from_name, Locale.US)

    @Test
    fun `does not read a plain word after order as an order number`() {
        val result = purchase(
            "Your Aster Privacy receipt",
            "Thanks for your order Download your invoice below. Total: \$86.99.",
            "receipts@astermail.org",
            "Aster Privacy",
        )
        assertNull(result.order_id)
    }

    @Test
    fun `still reads a real order number`() {
        val result = purchase("Your receipt", "Order #INV-90210 was placed. Total: \$12.00.", "receipts@store.com", "Store")
        assertEquals("INV-90210", result.order_id)
    }

    @Test
    fun `reads a marketplace order number`() {
        val result = purchase("Your order", "Order 112-1234567-1234567 confirmed. Total: \$12.00.", "orders@store.com", "Store")
        assertEquals("112-1234567-1234567", result.order_id)
    }

    @Test
    fun `does not read a word after confirmation as a confirmation number`() {
        val result = purchase(
            "Your receipt",
            "Confirmation pending. Order #A1234 placed. Total: \$12.00.",
            "receipts@store.com",
            "Store",
        )
        assertNull(result.confirmation_number)
    }

    @Test
    fun `does not read the subtotal as the total`() {
        val result = purchase(
            "Purchase receipt",
            "Subtotal: \$86.99\nDiscount: -\$1.00\nTotal: \$85.99",
            "receipts@astermail.org",
            "Aster Privacy",
        )
        assertEquals(86.99, result.subtotal?.value)
        assertEquals(1.0, result.discount?.value)
        assertEquals(85.99, result.total?.value)
    }

    @Test
    fun `drops a discount that the total does not reflect`() {
        val result = purchase(
            "Purchase receipt",
            "Subtotal: \$86.99\nMember savings: \$1.00\nOrder total: \$86.99",
            "receipts@astermail.org",
            "Aster Privacy",
        )
        assertEquals(86.99, result.total?.value)
        assertNull(result.discount)
    }

    @Test
    fun `drops a breakdown whose parts exceed the total`() {
        val result = purchase(
            "Purchase receipt",
            "Subtotal: \$500.00\nTax: \$40.00\nTotal: \$12.00",
            "receipts@store.com",
            "Store",
        )
        assertEquals(12.0, result.total?.value)
        assertNull(result.subtotal)
        assertNull(result.tax)
    }

    @Test
    fun `keeps a breakdown that adds up`() {
        val result = purchase(
            "Purchase receipt",
            "Subtotal: \$100.00\nShipping: \$5.00\nTax: \$8.00\nTotal: \$113.00",
            "receipts@store.com",
            "Store",
        )
        assertEquals(100.0, result.subtotal?.value)
        assertEquals(5.0, result.shipping_cost?.value)
        assertEquals(8.0, result.tax?.value)
        assertEquals(113.0, result.total?.value)
    }

    @Test
    fun `reads a rupee total without turning it into dollars`() {
        val result = purchase("Your subscription receipt", "Order #AS-4471\nTotal: ₹8,629.00", "receipts@astermail.org", "Aster Privacy")
        assertEquals("INR", result.total?.currency)
        assertEquals(8629.0, result.total?.value)
        assertEquals("₹8,629.00", result.total?.formatted)
    }

    @Test
    fun `keeps lakh grouping intact`() {
        val result = purchase("Your receipt", "Order #AS-4471\nTotal: ₹1,00,000.00", "receipts@store.in", "Store")
        assertEquals(100000.0, result.total?.value)
    }

    @Test
    fun `uses the email currency for an amount written without a symbol`() {
        val result = purchase(
            "Your receipt",
            "Order #AS-4471\nSubtotal: ₹500.00\nTax: 90.00\nTotal: ₹590.00",
            "receipts@store.in",
            "Store",
        )
        assertEquals("INR", result.tax?.currency)
    }

    @Test
    fun `reads a trailing currency code`() {
        val result = purchase("Your receipt", "Order #AS-4471\nTotal: 42.00 EUR", "receipts@store.eu", "Store")
        assertEquals("EUR", result.total?.currency)
        assertEquals(42.0, result.total?.value)
    }

    @Test
    fun `ignores prose that merely mentions an order`() {
        assertFalse(
            is_purchase_email(
                "In order to continue",
                "In order to download the report, sign in. You purchased nothing.",
            ),
        )
    }

    @Test
    fun `still detects a real receipt`() {
        assertTrue(
            is_purchase_email(
                "Your receipt from Aster Privacy",
                "Thank you for your order. Order #AS-4471. Total: \$86.99.",
            ),
        )
    }

    @Test
    fun `reads a ups tracking number regardless of surrounding numbers`() {
        val result = extract_shipping_details(
            "Your UPS package has shipped",
            "Order 8005551234567 total \$129.00. Tracking number: 1Z999AA10123456784. It is on its way.",
            null,
            "pkginfo@ups.com",
        )
        assertEquals(ShippingCarrier.ups, result.carrier)
        assertEquals("1Z999AA10123456784", result.tracking_number)
    }

    @Test
    fun `reads the amazon tba number and does not flip carrier to a bare number`() {
        val result = extract_shipping_details(
            "Shipped: Your Amazon.com order",
            "Arriving tomorrow. Order #112-1234567-1234567. Tracking ID TBA303194762541. Total 500000000000.",
            null,
            "shipment-tracking@amazon.com",
        )
        assertEquals(ShippingCarrier.amazon, result.carrier)
        assertEquals("TBA303194762541", result.tracking_number)
    }

    @Test
    fun `mines the tracking number out of the tracking url`() {
        val result = extract_shipping_details(
            "USPS shipment update",
            "Track it: <a href=\"https://tools.usps.com/go/TrackConfirmAction?tLabels=9400111899223817171718\">here</a>. Reference 12345678901234.",
            "<a href=\"https://tools.usps.com/go/TrackConfirmAction?tLabels=9400111899223817171718\">here</a>",
            "no-reply@usps.com",
        )
        assertEquals(ShippingCarrier.usps, result.carrier)
        assertEquals("9400111899223817171718", result.tracking_number)
    }

    @Test
    fun `does not treat a bare order number as a fedex tracking number without context`() {
        val result = extract_shipping_details(
            "Your order confirmation",
            "Thanks for your order 481516234200. Your total is \$42.00.",
            null,
            "orders@fedex.com",
        )
        assertNull(result.tracking_number)
    }

    @Test
    fun `uses a bare fedex number only inside a tracking context`() {
        val result = extract_shipping_details(
            "FedEx shipment",
            "Your tracking number is 770123456789 and it left the facility.",
            null,
            "tracking@fedex.com",
        )
        assertEquals(ShippingCarrier.fedex, result.carrier)
        assertEquals("770123456789", result.tracking_number)
    }

    @Test
    fun `does not report delivered for a future delivery`() {
        val result = extract_shipping_details(
            "Your package is on the way",
            "Your order will be delivered on Tuesday. Estimated delivery: Tuesday, March 5.",
            null,
            "ship@ups.com",
        )
        assertNotEquals(ShippingStatus.delivered, result.status)
    }

    @Test
    fun `reports delivered when the subject says delivered`() {
        val result = extract_shipping_details(
            "Delivered: your Amazon package",
            "Your package was delivered to the front door.",
            null,
            "shipment-tracking@amazon.com",
        )
        assertEquals(ShippingStatus.delivered, result.status)
    }

    @Test
    fun `reports out for delivery ahead of any delivered keyword`() {
        val result = extract_shipping_details(
            "Out for delivery",
            "Your package is out for delivery and will be delivered today.",
            null,
            "ship@fedex.com",
        )
        assertEquals(ShippingStatus.out_for_delivery, result.status)
    }

    @Test
    fun `reports label created ahead of the broad shipped keyword`() {
        val result = extract_shipping_details(
            "Shipping label created",
            "A shipping label was created and your item will be shipped soon.",
            null,
            "orders@example.com",
        )
        assertEquals(ShippingStatus.label_created, result.status)
    }

    @Test
    fun `does not read a carrier out of an unrelated word`() {
        val result = extract_shipping_details(
            "Your subscription groups were updated",
            "We reorganized your groups and backups this week.",
            null,
            "no-reply@example.com",
        )
        assertNotEquals(ShippingCarrier.ups, result.carrier)
    }

    @Test
    fun `still matches a real carrier mention on a word boundary`() {
        val result = extract_shipping_details(
            "Shipment update",
            "Your parcel was handed to UPS for delivery.",
            null,
            "no-reply@example.com",
        )
        assertEquals(ShippingCarrier.ups, result.carrier)
    }

    @Test
    fun `keeps an item name that starts with a digit`() {
        val result = purchase(
            "Your receipt",
            "Order #ABC-12345.\n3M Command Strips (Qty:2) \$12.00\nOrder total: \$24.00.",
            "receipts@store.com",
            "Store Receipts",
        )
        val item = result.items[0]
        assertEquals("3M Command Strips", item.name)
        assertEquals(2, item.quantity)
    }

    @Test
    fun `formats a non usd item total with the right symbol`() {
        val result = purchase(
            "Ihre Rechnung",
            "Bestellung #DE-98765. 2 x Kaffeebecher - €8.50. Gesamt: €17.00.",
            "rechnung@shop.de",
            "Shop Rechnung",
        )
        assertEquals("€17.00", result.items[0].total_price?.formatted)
    }

    @Test
    fun `strips only trailing role words from the merchant name`() {
        val result = purchase("Your receipt", "Order #ABC-12345. Total: \$24.00.", "receipts@shippingsupplies.com", "Shipping Supplies Inc Order")
        assertEquals("Shipping Supplies Inc", result.merchant_name)
    }

    @Test
    fun `extract email details builds a tracking url for a known carrier`() {
        val result = extract_email_details(
            "Your UPS package has shipped",
            "Tracking number: 1Z999AA10123456784. It is on its way.",
            null,
            "pkginfo@ups.com",
            "UPS",
            Locale.US,
        )
        assertTrue(result.has_shipping_details)
        assertEquals("https://www.ups.com/track?tracknum=1Z999AA10123456784", result.shipping?.tracking_url)
        assertFalse(result.is_empty)
    }
}
