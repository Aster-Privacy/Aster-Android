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

    @Test
    fun `ignores mail that mentions tracking and delivery in passing`() {
        val subject = "Update on your support request"
        val body = "We are tracking the bug you reported, and the fix was delivered in version 2.3."
        assertFalse(is_shipping_email(subject, body))
        assertFalse(extract_email_details(subject, body, null, "me@example.com", "Me").has_shipping_details)
    }

    @Test
    fun `ignores release notes that shipped and delivered features`() {
        assertFalse(
            is_shipping_email("What's new in October", "We shipped dark mode and delivered the new editor you asked for."),
        )
    }

    @Test
    fun `still detects a shipment`() {
        assertTrue(is_shipping_email("Your order has shipped", "Tracking number: 1Z999AA10123456784. Estimated delivery: Friday."))
        assertTrue(is_shipping_email("Delivered", "Your package was delivered. Tracking # 9400100000000000000000"))
        assertTrue(is_shipping_email("Out for delivery", "Your package is out for delivery and arriving today."))
    }

    @Test
    fun `ignores encryption and tracking pixel wording`() {
        listOf(
            "Messages are encrypted in transit and tracking pixels are blocked.",
            "The server did deliver the envelope. Mail is protected in transit. Blocked tracking pixels.",
            "Scheduled mail is only released at delivery time and was delivered to every recipient; data in transit.",
        ).forEach { body ->
            assertFalse(body, is_shipping_email("Re: Code review notes", body))
        }
    }

    @Test
    fun `ignores a privacy newsletter`() {
        val subject = "Our privacy report for October"
        val body = "Every message is encrypted at rest and in transit, with TLS used for in transit encryption between servers. " +
            "Tracking protection now strips tracking pixels and link tracking from incoming mail, and blocked trackers are listed per message. " +
            "This report was delivered to every subscriber, and your order history stays private."
        assertFalse(is_shipping_email(subject, body))
        assertFalse(extract_email_details(subject, body, null, "news@example.com", "News").has_shipping_details)
    }

    @Test
    fun `ignores a security review reply`() {
        assertFalse(
            is_shipping_email(
                "Re: Review of the scheduled sending flow",
                "Data is secured in transit, but the queued copy was delivered to the server in plain form. " +
                    "Tracking prevention does not cover this path.",
            ),
        )
    }

    @Test
    fun `detects carrier and shop notices`() {
        listOf(
            "UPS Update: Package Scheduled for Delivery Tomorrow" to
                "Your package is in transit. Tracking Number: 1Z999AA10123456784. Estimated delivery: Friday.",
            "USPS Tracking update" to
                "Your item is in transit to the next facility. USPS Tracking 9400100000000000000000.",
            "Your DHL shipment is on its way" to
                "Shipment in transit. Track your parcel with tracking ID JD014600006281230000.",
            "FedEx shipment update" to
                "Your shipment is in transit and was handed to the destination hub. Tracking: 771234567890.",
            "Your Amazon order has shipped" to
                "Arriving tomorrow. Track package TBA123456789012.",
            "Your order is on the way" to
                "Good news! Your order has shipped and is now in transit with Royal Mail. Tracking: RM123456785GB.",
            "In transit" to
                "In transit: your order 4821 left our warehouse. We are tracking it for you.",
            "Delivered: your parcel" to
                "Your parcel was delivered at 14:02. Tracking # CP123456789PT.",
        ).forEach { (subject, body) ->
            assertTrue(subject, is_shipping_email(subject, body))
        }
    }

    @Test
    fun `accepts a stripe receipt link in plain text`() {
        val url = extract_receipt_url(
            "Thanks for your payment. View it here: https://pay.stripe.com/receipts/payment/abc123.",
            null,
            listOf("receipts@stripe.com"),
        )
        assertEquals("https://pay.stripe.com/receipts/payment/abc123", url)
    }

    @Test
    fun `accepts a view your receipt anchor on the sender domain`() {
        val url = extract_receipt_url(
            "View your receipt",
            "<p>Thanks.</p><a href=\"https://orders.shop.example.com/r/42\">View your receipt</a>",
            listOf("hello@shop.example.com"),
        )
        assertEquals("https://orders.shop.example.com/r/42", url)
    }

    @Test
    fun `rejects a receipt anchor on a third party domain`() {
        val url = extract_receipt_url(
            "View your receipt",
            "<a href=\"https://tracker.example.net/r/42\">View your receipt</a>",
            listOf("hello@shop.example.com"),
        )
        assertNull(url)
    }

    @Test
    fun `rejects unsafe receipt links`() {
        listOf(
            "http://pay.stripe.com/receipts/payment/abc123",
            "https://user:pw@pay.stripe.com/receipts/payment/abc123",
            "javascript:alert(1)",
            "https://pay.stripe.com/other/abc123",
            "https://pay.stripe.com.evil.example/receipts/x",
        ).forEach { href ->
            val url = extract_receipt_url(
                "View your receipt",
                "<a href=\"$href\">View your receipt</a>",
                listOf("hello@shop.example.com"),
            )
            assertNull(href, url)
        }
    }

    @Test
    fun `rejects an overlong receipt link`() {
        val href = "https://pay.stripe.com/receipts/" + "a".repeat(2100)
        assertNull(extract_receipt_url("Receipt: $href", null, listOf("receipts@stripe.com")))
    }

    @Test
    fun `plain text links are checked against the allowlist only`() {
        val url = extract_receipt_url(
            "View your receipt: https://orders.shop.example.com/r/42",
            null,
            listOf("hello@shop.example.com"),
        )
        assertNull(url)
    }

    @Test
    fun `classifies receipts and orders`() {
        assertEquals(document_kind_receipt, detect_document_kind("Your receipt from Acme", "Amount paid \$10.00"))
        assertEquals(document_kind_order, detect_document_kind("Order #1234 confirmed", "Thanks for your receipt"))
        assertEquals(document_kind_order, detect_document_kind("Thanks for shopping", "Your order confirmation is below"))
    }

    @Test
    fun `uses the merchant from a payment processor receipt`() {
        val result = purchase(
            "Your receipt from Northwind Traders",
            "Amount paid \$42.00. Date paid: March 3, 2026. Receipt #1234-5678.",
            "receipts@stripe.com",
            "Stripe",
        )
        assertEquals("Northwind Traders", result.merchant_name)
        assertEquals("receipts@stripe.com", result.merchant_source)
        assertEquals(document_kind_receipt, result.document_kind)
    }

    @Test
    fun `ignores the display name of a payment processor`() {
        val result = purchase("Payment received", "Amount paid \$42.00.", "receipts@stripe.com", "Acme Payments via Stripe")
        assertNull(result.merchant_name)
    }

    @Test
    fun `reads the original sender of a forwarded receipt`() {
        val body = "FYI\n\n---------- Forwarded message ---------\nFrom: Northwind Traders <orders@northwind.example>\n" +
            "Date: Tue, Mar 3, 2026\nSubject: Your Northwind Traders receipt\nTo: me@example.com\n\n" +
            "Order #NW-100. Total: \$12.00."
        val result = purchase("Fwd: Your Northwind Traders receipt", body, "friend@example.com", "Friend")
        assertEquals("orders@northwind.example", result.merchant_source)
        assertEquals("Northwind Traders", result.merchant_name)
        assertTrue(result.raw_signals.contains("forwarded_from:orders@northwind.example"))
    }

    @Test
    fun `purchase details carry the receipt url`() {
        val result = extract_purchase_details(
            "Your receipt",
            "Amount paid \$9.00. https://pay.stripe.com/receipts/payment/xyz",
            "receipts@stripe.com",
            "Stripe",
            Locale.US,
        )
        assertEquals("https://pay.stripe.com/receipts/payment/xyz", result.receipt_url)
    }

    private fun details(subject: String, body: String, from_email: String, from_name: String, html: String? = null) =
        extract_email_details(subject, body, html, from_email, from_name, Locale.US)

    @Test
    fun `reads a marketplace order confirmation`() {
        val body = "Hello Sam,\nThank you for your order.\n\nOrder #112-9876543-1234567\nPlaced on March 3, 2026\n\n" +
            "Noise Cancelling Headphones x 1 \$249.00\nUSB-C Cable (2 m) x 2 \$18.00\n\n" +
            "Item Subtotal: \$267.00\nShipping & Handling: \$0.00\nEstimated Tax: \$21.36\nOrder Total: \$288.36\n\n" +
            "Payment method: Visa ending in 4242\n"
        val result = details("Your Amazon.com order #112-9876543-1234567", body, "auto-confirm@amazon.com", "Amazon.com")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("112-9876543-1234567", purchase?.order_id)
        assertEquals("Amazon.com", purchase?.merchant_name)
        assertEquals(document_kind_order, purchase?.document_kind)
        assertEquals(288.36, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals(267.0, purchase?.subtotal?.value ?: 0.0, 0.001)
        assertEquals("4242", purchase?.card_last_four)
        assertEquals(2, purchase?.items?.size)
        assertEquals(2, purchase?.items?.get(1)?.quantity)
        assertEquals(18.0, purchase?.items?.get(1)?.total_price?.value ?: 0.0, 0.001)
        assertNull(purchase?.receipt_url)
    }

    @Test
    fun `reads an independent store order sent through a commerce platform`() {
        val body = "Thank you for your purchase!\n\nHi Sam, we're getting your order ready to be shipped.\n\nOrder #1001\n\n" +
            "1 x Linen Apron - \$38.00\n2 x Oven Mitt - \$12.00\n\nSubtotal: \$62.00\nShipping: \$6.50\nTaxes: \$5.48\nTotal: \$73.98\n"
        val result = details("Order #1001 confirmed", body, "hello@maplekitchen.example", "Maple Kitchen")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Maple Kitchen", purchase?.merchant_name)
        assertEquals("1001", purchase?.order_id)
        assertEquals(document_kind_order, purchase?.document_kind)
        assertEquals(73.98, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals(6.5, purchase?.shipping_cost?.value ?: 0.0, 0.001)
        assertEquals(listOf("Linen Apron", "Oven Mitt"), purchase?.items?.map { it.name })
        assertEquals(24.0, purchase?.items?.get(1)?.total_price?.value ?: 0.0, 0.001)
    }

    @Test
    fun `reads a point of sale receipt and keeps its receipt link`() {
        val html = "<html><body><p>Receipt from Blue Bottle Coffee</p><p>Total \$9.75</p>" +
            "<p>Paid with Mastercard ending in 1881</p>" +
            "<a href=\"https://squareup.com/receipt/preview/abc123\">View full receipt</a></body></html>"
        val body = "Receipt from Blue Bottle Coffee\nTotal \$9.75\nPaid with Mastercard ending in 1881\nView full receipt"
        val result = details("Receipt from Blue Bottle Coffee", body, "receipts@messaging.squareup.com", "Square", html)
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Blue Bottle Coffee", purchase?.merchant_name)
        assertEquals(document_kind_receipt, purchase?.document_kind)
        assertEquals(9.75, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals("https://squareup.com/receipt/preview/abc123", purchase?.receipt_url)
    }

    @Test
    fun `reads a payment service receipt with the payee as merchant`() {
        val body = "You sent a payment of \$54.20 USD to Riverside Books\n\nTransaction ID: 7XY12345AB678901C\n" +
            "March 4, 2026\n\nPayment receipt\n\nTotal: \$54.20 USD\n\nhttps://www.paypal.com/activity/payment/7XY12345AB678901C"
        val result = details("Receipt for your payment to Riverside Books", body, "service@paypal.com", "service@paypal.com")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Riverside Books", purchase?.merchant_name)
        assertEquals("7XY12345AB678901C", purchase?.transaction_id)
        assertEquals(document_kind_receipt, purchase?.document_kind)
        assertEquals(54.2, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals("https://www.paypal.com/activity/payment/7XY12345AB678901C", purchase?.receipt_url)
    }

    @Test
    fun `reads a digital store receipt`() {
        val body = "Receipt\n\nApple ID: sam@example.com\nOrder ID: MLXYZ123AB\nDate: Mar 5, 2026\n\n" +
            "Procreate\nDigital Illustration\n\$12.99\n\nSubtotal \$12.99\nTax \$1.04\nTotal \$14.03\n"
        val result = details("Your receipt from Apple.", body, "no_reply@email.apple.com", "Apple")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Apple", purchase?.merchant_name)
        assertEquals("MLXYZ123AB", purchase?.order_id)
        assertEquals(document_kind_receipt, purchase?.document_kind)
        assertEquals(14.03, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals(1.04, purchase?.tax?.value ?: 0.0, 0.001)
    }

    @Test
    fun `reads a ride receipt`() {
        val body = "Thanks for riding, Sam\n\nTotal \$18.42\nMarch 6, 2026\n\nTrip fare \$14.90\nBooking fee \$2.10\n" +
            "Tolls \$1.42\n\nPayments\nVisa ending 0199 \$18.42\n\nYou paid \$18.42\n"
        val result = details("Your Friday evening trip with Uber", body, "noreply@uber.com", "Uber Receipts")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Uber", purchase?.merchant_name)
        assertEquals(document_kind_receipt, purchase?.document_kind)
        assertEquals(18.42, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals("0199", purchase?.card_last_four)
    }

    @Test
    fun `reads a handmade marketplace order`() {
        val body = "Thanks for your order from WillowWoodworks!\n\nOrder #3214567890\n\n" +
            "Walnut Serving Board (Qty: 1) \$64.00\n\nItem total: \$64.00\nShipping: \$9.00\nSales tax: \$5.84\nOrder total: \$78.84\n" +
            "Paid with card ending in 5555\n"
        val result = details("Your Etsy order from WillowWoodworks", body, "transaction@etsy.com", "Etsy")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("WillowWoodworks", purchase?.merchant_name)
        assertEquals("3214567890", purchase?.order_id)
        assertEquals(78.84, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals(64.0, purchase?.subtotal?.value ?: 0.0, 0.001)
        assertEquals("Walnut Serving Board", purchase?.items?.firstOrNull()?.name)
    }

    @Test
    fun `reads an app store order`() {
        val body = "Thank you for your purchase.\n\nOrder number: GPA.3312-4456-7789-01234\nOrder date: Mar 7, 2026 2:14 PM\n\n" +
            "Item: Stardew Valley Qty: 1 Price: \$4.99\n\nTotal: \$4.99\nPayment method: Mastercard ending in 7788\n"
        val result = details("Your Google Play Order Receipt from Mar 7, 2026", body, "googleplay-noreply@google.com", "Google Play")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Google Play", purchase?.merchant_name)
        assertEquals("GPA.3312-4456-7789-01234", purchase?.order_id)
        assertEquals(4.99, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals("Stardew Valley", purchase?.items?.firstOrNull()?.name)
    }

    @Test
    fun `reads an airline booking receipt`() {
        val body = "Thank you for your booking.\n\nConfirmation code: QX7T2A\nPassenger: Sam Lee\n" +
            "Flight: SEA to AMS, Mar 20, 2026\n\nBase fare \$612.00\nTaxes and fees \$98.40\nTotal \$710.40\n" +
            "Charged to Visa ending in 3030\n"
        val result = details("Your Northwind Air booking confirmation QX7T2A", body, "noreply@northwindair.example", "Northwind Air")
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Northwind Air", purchase?.merchant_name)
        assertEquals("QX7T2A", purchase?.confirmation_number)
        assertEquals(710.4, purchase?.total?.value ?: 0.0, 0.001)
    }

    @Test
    fun `reads a software invoice sent through a billing processor and rejects foreign links`() {
        val html = "<html><body><p>Your receipt from Northwind Cloud</p><p>Invoice #F1A2B3C4-0007</p>" +
            "<p>Amount paid \$29.00</p><p>Date paid March 8, 2026</p>" +
            "<a href=\"https://tracking.example.net/click?u=1\">View your receipt</a>" +
            "<a href=\"https://invoice.stripe.com/i/acct_1/test_abc\">Download invoice</a></body></html>"
        val body = "Your receipt from Northwind Cloud\nInvoice #F1A2B3C4-0007\nAmount paid \$29.00\nDate paid March 8, 2026"
        val result = details("Your receipt from Northwind Cloud #F1A2B3C4-0007", body, "receipts@stripe.com", "Northwind Cloud", html)
        val purchase = result.purchase
        assertTrue(result.has_purchase_details)
        assertEquals("Northwind Cloud", purchase?.merchant_name)
        assertEquals(document_kind_receipt, purchase?.document_kind)
        assertEquals(29.0, purchase?.total?.value ?: 0.0, 0.001)
        assertEquals("https://invoice.stripe.com/i/acct_1/test_abc", purchase?.receipt_url)
    }

    @Test
    fun `leaves the receipt link empty when only an unrelated link claims to be the receipt`() {
        val html = "<html><body><p>Thank you for your order.</p><p>Order #55501</p><p>Order total: \$40.00</p>" +
            "<a href=\"https://links.mailer.example/c/9f8e\">View your receipt</a></body></html>"
        val result = details("Order #55501", "Thank you for your order. Order #55501. Order total: \$40.00", "orders@shop.example", "Shop", html)
        assertTrue(result.has_purchase_details)
        assertNull(result.purchase?.receipt_url)
    }

    @Test
    fun `does not treat a newsletter with prices as a purchase`() {
        val body = "This week's picks\n\nTotal comfort: the new lounge chair, now \$299.00\n" +
            "Order today and save 20% with code SPRING\n\nUnsubscribe | View in browser"
        val result = details("Spring arrivals are here", body, "news@furniture.example", "Furniture Co")
        assertFalse(result.has_purchase_details)
        assertNull(result.purchase)
    }

    @Test
    fun `does not treat a shipping notification as a purchase`() {
        val body = "Good news, your package is on its way.\n\nTracking number: 1Z999AA10123456784\nCarrier: UPS\n" +
            "Estimated delivery: Thursday, March 12\n\nTrack your package: https://www.ups.com/track?tracknum=1Z999AA10123456784"
        val result = details("Your package has shipped", body, "ship-confirm@shop.example", "Shop")
        assertFalse(result.has_purchase_details)
        assertNull(result.purchase)
        assertTrue(result.has_shipping_details)
    }

    @Test
    fun `does not list ledger lines as items`() {
        val body = "Thank you for your order.\nOrder #777\n\nTea Towel x 2 \$16.00\nSubtotal x 1 \$16.00\nTotal x 1 \$18.00\nTotal: \$18.00"
        val result = purchase("Order #777", body, "orders@shop.example", "Shop")
        assertEquals(listOf("Tea Towel"), result.items.map { it.name })
    }
}
