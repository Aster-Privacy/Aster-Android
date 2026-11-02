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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferPriceTest {

    private val black_friday = SpecialOfferState(
        available = true,
        percent_off = 50,
        duration_months = 12,
        eligible_terms = listOf(
            "star:year",
            "nova:month", "nova:year",
            "supernova:month", "supernova:year",
            "duo:month", "duo:year",
            "family:month", "family:year",
        ),
        theme = "black_friday",
    )

    @Test
    fun halves_899_to_449() {
        assertEquals(449L, special_offer_price_cents(899L, 50))
    }

    @Test
    fun card_price_uses_the_given_list_price() {
        val price = offer_card_price_for(black_friday, "nova", "month", 899L)
        assertEquals(899L, price?.list_cents)
        assertEquals(449L, price?.offer_cents)
    }

    @Test
    fun star_monthly_is_never_discounted() {
        assertNull(offer_card_price_for(black_friday, "star", "month", 299L))
        assertEquals(1449L, offer_card_price_for(black_friday, "star", "year", 2899L)?.offer_cents)
    }

    @Test
    fun missing_list_price_shows_no_discount() {
        assertNull(offer_card_price_for(black_friday, "nova", "year", null))
        assertNull(offer_card_price_for(black_friday, "nova", "year", 0L))
    }

    @Test
    fun unavailable_offer_shows_no_discount() {
        assertNull(offer_card_price_for(black_friday.copy(available = false), "nova", "year", 8699L))
    }

    @Test
    fun crypto_star_one_month_is_not_covered_but_twelve_is() {
        assertFalse(black_friday.applies_to_crypto_term("star", 1))
        assertTrue(black_friday.applies_to_crypto_term("star", 12))
        assertTrue(black_friday.applies_to_crypto_term("nova", 3))
        assertFalse(black_friday.applies_to_crypto_term("nova", 24))
    }

    @Test
    fun plan_codes_follow_the_display_order() {
        assertEquals(
            listOf("star", "nova", "supernova", "duo", "family"),
            special_offer_plan_codes(black_friday.eligible_terms),
        )
        assertEquals(listOf("nova"), special_offer_plan_codes(SPECIAL_OFFER_DEFAULT_TERMS))
    }
}
