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
import java.util.Locale

class money_parsing_test {
    private val swedish = Locale.forLanguageTag("sv-SE")

    private fun receipt(body: String, from: String = "orders@shop.com", locale: Locale = Locale.US) =
        extract_purchase_details("Your receipt", "Order #AB-12345\n$body", from, "Shop", locale)

    private fun total(body: String, from: String = "orders@shop.com", locale: Locale = Locale.US) =
        receipt(body, from, locale).total

    @Test
    fun `reads a swedish krona receipt with comma decimals`() {
        val result = receipt(
            "Subtotal: 690,06 kr\nDiscount: -345,03 kr\nShipping: 191,09 kr\nTax: 134,08 kr\nTotal: 670,20 kr",
            "order@butik.se",
            swedish,
        )
        assertEquals("SEK", result.total?.currency)
        assertEquals(670.20, result.total!!.value, 0.0001)
        assertEquals(690.06, result.subtotal!!.value, 0.0001)
        assertEquals(345.03, result.discount!!.value, 0.0001)
        assertEquals(191.09, result.shipping_cost!!.value, 0.0001)
        assertEquals(134.08, result.tax!!.value, 0.0001)
        assertFalse(result.total!!.formatted.contains("$"))
        assertTrue(result.total!!.formatted.endsWith("kr"))
        assertTrue(result.total!!.formatted.startsWith("670"))
    }

    @Test
    fun `never shows a krona receipt as dollars even from a com sender in english`() {
        val result = receipt("Subtotal: 690,06 kr\nTotal: 670,20 kr")
        assertEquals(670.20, result.total!!.value, 0.0001)
        assertFalse(result.total!!.formatted.contains("$"))
        assertEquals("670.20 kr", result.total!!.formatted)
    }

    @Test
    fun `picks norwegian or danish krone from the sender domain`() {
        assertEquals("NOK", total("Total: 1 299,00 kr", "kvittering@butikk.no")?.currency)
        assertEquals(1299.0, total("Total: 1 299,00 kr", "kvittering@butikk.no")!!.value, 0.0001)
        assertEquals("DKK", total("Total: 1.299,00 kr.", "kvittering@butik.dk")?.currency)
        assertEquals(1299.0, total("Total: 1.299,00 kr.", "kvittering@butik.dk")!!.value, 0.0001)
    }

    @Test
    fun `explicit codes beat sender hints`() {
        assertEquals("NOK", total("Total: 499,00 NOK", "orders@shop.se")?.currency)
        assertEquals("DKK", total("Subtotal: 100,00 kr\nTotal: 125,00 DKK")?.currency)
    }

    @Test
    fun `reads euro amounts in european and english styles`() {
        assertEquals(1234.56, total("Total: 1.234,56 €")!!.value, 0.0001)
        assertEquals(1234.56, total("Total: €1,234.56")!!.value, 0.0001)
        assertEquals(1234.56, total("Total: 1 234,56 €")!!.value, 0.0001)
        assertEquals(1234.56, total("Total: 1 234,56 €")!!.value, 0.0001)
        assertEquals(1234.56, total("Total: 1 234,56 EUR")!!.value, 0.0001)
        assertEquals(19.99, total("Total: 19,99 €")!!.value, 0.0001)
        assertEquals("EUR", total("Total: 19,99 €")?.currency)
        assertEquals(1234.0, total("Total: 1.234 €")!!.value, 0.0001)
    }

    @Test
    fun `reads swiss francs with apostrophe grouping`() {
        val amount = total("Total: CHF 1'234.50")
        assertEquals("CHF", amount?.currency)
        assertEquals(1234.50, amount!!.value, 0.0001)
        assertEquals(1234.50, total("Total: 1’234.50 CHF")!!.value, 0.0001)
        assertEquals("CHF", total("Total: Fr. 42.00")?.currency)
    }

    @Test
    fun `reads zero decimal currencies`() {
        val yen = total("Total: ¥12,800")
        assertEquals("JPY", yen?.currency)
        assertEquals(12800.0, yen!!.value, 0.0001)
        assertEquals("¥12,800", yen.formatted)
        val won = total("Total: ₩45,000")
        assertEquals("KRW", won?.currency)
        assertEquals(45000.0, won!!.value, 0.0001)
        assertEquals(1990.0, total("Total: 1.990 ISK")!!.value, 0.0001)
    }

    @Test
    fun `reads three decimal currencies`() {
        val dinar = total("Total: KWD 12.500")
        assertEquals("KWD", dinar?.currency)
        assertEquals(12.5, dinar!!.value, 0.0001)
        assertEquals("KWD 12.500", dinar.formatted)
        assertEquals(1250.5, total("Total: 1,250.500 BHD")!!.value, 0.0001)
    }

    @Test
    fun `reads central european currencies`() {
        val zloty = total("Total: 1 249,99 zł")
        assertEquals("PLN", zloty?.currency)
        assertEquals(1249.99, zloty!!.value, 0.0001)
        assertEquals("CZK", total("Total: 2 490 Kč")?.currency)
        assertEquals(2490.0, total("Total: 2 490 Kč")!!.value, 0.0001)
        assertEquals("HUF", total("Total: 12 990 Ft")?.currency)
        assertEquals("RON", total("Total: 249,90 lei")?.currency)
        assertEquals(249.90, total("Total: 249,90 lei")!!.value, 0.0001)
    }

    @Test
    fun `reads other symbols`() {
        assertEquals("BRL", total("Total: R$ 1.234,56")?.currency)
        assertEquals(1234.56, total("Total: R$ 1.234,56")!!.value, 0.0001)
        assertEquals("RUB", total("Total: 4 500 ₽")?.currency)
        assertEquals("TRY", total("Total: ₺1.250,00")?.currency)
        assertEquals(1250.0, total("Total: ₺1.250,00")!!.value, 0.0001)
        assertEquals("ILS", total("Total: ₪89.90")?.currency)
        assertEquals("PHP", total("Total: ₱1,500.00")?.currency)
        assertEquals("THB", total("Total: ฿990.00")?.currency)
        assertEquals("ZAR", total("Total: R 349.99")?.currency)
        assertEquals("INR", total("Total: Rs. 1,499.00")?.currency)
        assertEquals("GBP", total("Total: £12.99")?.currency)
    }

    @Test
    fun `reads prefixed dollar variants`() {
        assertEquals("CAD", total("Total: CA$25.00")?.currency)
        assertEquals("AUD", total("Total: A$25.00")?.currency)
        assertEquals("NZD", total("Total: NZ$25.00")?.currency)
        assertEquals("HKD", total("Total: HK$250.00")?.currency)
        assertEquals("SGD", total("Total: S$25.00")?.currency)
        assertEquals("MXN", total("Total: MX$499.00")?.currency)
        assertEquals("USD", total("Total: US$25.00")?.currency)
        assertEquals("USD", total("Total: \$25.00")?.currency)
    }

    @Test
    fun `resolves a bare dollar sign from the sender domain`() {
        assertEquals("CAD", total("Total: \$25.00", "orders@shop.ca")?.currency)
        assertEquals("AUD", total("Total: \$25.00", "orders@shop.com.au")?.currency)
        assertEquals("USD", total("Total: \$25.00", "orders@shop.com", Locale.CANADA)?.currency)
    }

    @Test
    fun `keeps indian lakh grouping`() {
        assertEquals(100000.0, total("Total: ₹1,00,000.00")!!.value, 0.0001)
        assertEquals(1234567.0, total("Total: ₹12,34,567")!!.value, 0.0001)
    }

    @Test
    fun `does not invent a currency when the email names none`() {
        val amount = total("Total: 42.00")
        assertEquals("", amount?.currency)
        assertEquals(42.0, amount!!.value, 0.0001)
        assertEquals("42.00", amount.formatted)
    }

    @Test
    fun `ignores percentages next to amounts`() {
        val result = receipt("Discount: 10.5% off\nTotal: €20.00")
        assertNull(result.discount)
        assertEquals(20.0, result.total!!.value, 0.0001)
    }

    @Test
    fun `english words never count as currency codes`() {
        assertEquals(
            "SEK",
            receipt("TOP 10 deals\nALL 3 sizes\n6 ft cable\nTotal: 199,00 kr", "order@butik.se", swedish).total?.currency,
        )
    }

    @Test
    fun `reads a swedish colon dash price`() {
        assertEquals(199.0, total("Total: 199:-", "order@butik.se", swedish)!!.value, 0.0001)
        assertEquals("SEK", total("Total: 199:-", "order@butik.se", swedish)?.currency)
    }

    @Test
    fun `item prices keep their own currency and decimals`() {
        val result = receipt(
            "2 x Kaffekopp - 89,50 kr\nTotal: 179,00 kr",
            "order@butik.se",
            swedish,
        )
        val item = result.items.first()
        assertEquals(89.50, item.unit_price!!.value, 0.0001)
        assertEquals(179.0, item.total_price!!.value, 0.0001)
        assertEquals("SEK", item.total_price!!.currency)
    }

    @Test
    fun `parses numbers by separator rules`() {
        assertEquals(690.06, parse_number("690,06", "SEK")!!, 0.0001)
        assertEquals(1234.0, parse_number("1,234", "USD")!!, 0.0001)
        assertEquals(1234.0, parse_number("1.234", "EUR")!!, 0.0001)
        assertEquals(1.234, parse_number("1.234", "KWD")!!, 0.0001)
        assertEquals(1234567.89, parse_number("1.234.567,89", "EUR")!!, 0.0001)
        assertEquals(1234567.89, parse_number("1,234,567.89", "USD")!!, 0.0001)
        assertEquals(12.5, parse_number("12,5", "EUR")!!, 0.0001)
        assertEquals(1234.5, parse_number("1 234,5", "SEK")!!, 0.0001)
    }

    @Test
    fun `formats every currency with its own symbol`() {
        assertEquals("670,20 kr", format_amount(670.20, "SEK", swedish).replace(' ', ' '))
        assertEquals("€19.99", format_amount(19.99, "EUR", Locale.US))
        assertEquals("¥12,800", format_amount(12800.0, "JPY", Locale.US))
        assertEquals("1,249.99 zł", format_amount(1249.99, "PLN", Locale.US))
        assertEquals("KWD 12.500", format_amount(12.5, "KWD", Locale.US))
        assertEquals("AED 99.00", format_amount(99.0, "AED", Locale.US))
    }
}
