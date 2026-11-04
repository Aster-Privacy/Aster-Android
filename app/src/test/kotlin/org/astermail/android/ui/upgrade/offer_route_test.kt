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

import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.billing.BillingUiState
import org.astermail.android.billing.PlayOffer
import org.astermail.android.billing.offer_checkout_uses_family_endpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfferRouteTest {

    private val offer = SpecialOfferState(
        available = true,
        percent_off = 50,
        duration_months = 12,
        eligible_terms = listOf(
            "star:year",
            "nova:month", "nova:year",
            "duo:month", "duo:year",
            "family:month", "family:year",
        ),
        theme = "black_friday",
    )

    private val products = listOf(
        GooglePlayProduct("nova_product", "nova", listOf("monthly", "yearly")),
        GooglePlayProduct("star_product", "star", listOf("monthly", "yearly")),
    )

    private fun play_offer(product: String, base: String, offer_id: String?, interval: String, micros: Long) = PlayOffer(
        product_id = product,
        base_plan_id = base,
        offer_token = "token_$base",
        formatted_price = "$" + (micros / 1_000_000.0),
        price_micros = micros,
        currency_code = "USD",
        billing_interval = interval,
        offer_id = offer_id,
        intro_formatted_price = if (offer_id != null) "$" + (micros / 2_000_000.0) else null,
        intro_price_micros = if (offer_id != null) micros / 2 else null,
    )

    private val play_billing = BillingUiState(
        play_products = products,
        play_special_offers = listOf(GooglePlaySpecialOffer("nova_product", "yearly", "bf_year", 50, 12)),
        play_offers = listOf(
            play_offer("nova_product", "yearly", null, "year", 86_990_000),
            play_offer("nova_product", "yearly", "bf_year", "year", 86_990_000),
            play_offer("nova_product", "monthly", null, "month", 8_990_000),
            play_offer("star_product", "monthly", null, "month", 2_990_000),
            play_offer("star_product", "yearly", null, "year", 28_990_000),
        ),
    )

    @Test
    fun play_covered_term_with_offer_routes_to_special_offer() {
        val route = offer_checkout_route(offer, play_billing, "nova", "year", true)
        assertEquals(OfferRoute.Play("nova_product", "yearly"), route)
    }

    @Test
    fun play_covered_term_without_offer_routes_to_list_price() {
        assertEquals(OfferRoute.None, offer_checkout_route(offer, play_billing, "nova", "month", true))
    }

    @Test
    fun star_monthly_never_routes_to_the_offer() {
        assertEquals(OfferRoute.None, offer_checkout_route(offer, play_billing, "star", "month", true))
        assertEquals(OfferRoute.None, offer_checkout_route(offer, BillingUiState(), "star", "month", false))
    }

    @Test
    fun stripe_duo_and_family_use_the_family_endpoint() {
        assertEquals(OfferRoute.Stripe("duo", "year", true), offer_checkout_route(offer, BillingUiState(), "duo", "year", false))
        assertEquals(OfferRoute.Stripe("family", "month", true), offer_checkout_route(offer, BillingUiState(), "family", "month", false))
        assertEquals(OfferRoute.Stripe("nova", "year", false), offer_checkout_route(offer, BillingUiState(), "nova", "year", false))
        assertTrue(offer_checkout_uses_family_endpoint("Duo", true))
        assertFalse(offer_checkout_uses_family_endpoint("duo", false))
        assertFalse(offer_checkout_uses_family_endpoint("nova", true))
    }

    @Test
    fun play_switcher_lists_only_plans_with_a_play_offer() {
        val codes = listOf("star", "nova", "duo")
        assertEquals(listOf("nova"), offer_selectable_plan_codes(offer, play_billing, true, codes))
        assertEquals(codes, offer_selectable_plan_codes(offer, BillingUiState(), false, codes))
    }
}
