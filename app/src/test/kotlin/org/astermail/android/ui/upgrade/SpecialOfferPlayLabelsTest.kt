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

package org.astermail.android.ui.upgrade

import java.util.Locale
import org.astermail.android.billing.PlayOffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SpecialOfferPlayLabelsTest {

    private lateinit var saved_locale: Locale

    @Before
    fun setup() {
        saved_locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun teardown() {
        Locale.setDefault(saved_locale)
    }

    private fun offer(
        micros: Long,
        currency: String,
        formatted: String = "",
        intro_formatted: String? = null,
        intro_micros: Long? = null,
    ) = PlayOffer(
        product_id = "nova",
        base_plan_id = "monthly",
        offer_token = "tok",
        formatted_price = formatted,
        price_micros = micros,
        currency_code = currency,
        billing_interval = "month",
        offer_id = "half-price-12m",
        intro_formatted_price = intro_formatted,
        intro_price_micros = intro_micros,
    )

    @Test
    fun `no play offer means no labels`() {
        assertNull(special_offer_play_labels(null, 50))
    }

    @Test
    fun `play formatted prices win`() {
        val labels = special_offer_play_labels(offer(8_990_000, "EUR", "8,99 €", "4,49 €", 4_490_000), 50)!!
        assertEquals("8,99 €", labels.original)
        assertEquals("4,49 €", labels.discounted)
    }

    @Test
    fun `missing intro label uses the intro price in the play currency`() {
        val labels = special_offer_play_labels(offer(8_990_000, "EUR", "8,99 €", intro_micros = 4_490_000), 50)!!
        assertEquals("€4.49", labels.discounted)
    }

    @Test
    fun `yen prices keep their scale and never fall back to dollars`() {
        val labels = special_offer_play_labels(offer(1_200_000_000, "JPY"), 50)!!
        assertEquals("¥1,200", labels.original)
        assertEquals("¥600", labels.discounted)
    }

    @Test
    fun `won prices keep their scale`() {
        val labels = special_offer_play_labels(offer(14_900_000_000, "KRW", intro_micros = 7_450_000_000), 50)!!
        assertEquals("₩14,900", labels.original)
        assertEquals("₩7,450", labels.discounted)
    }
}
