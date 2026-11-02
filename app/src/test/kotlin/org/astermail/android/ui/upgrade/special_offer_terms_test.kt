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

import org.astermail.android.api.billing.GooglePlayConfigResponse
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.api.billing.SpecialOfferStatusResponse
import org.astermail.android.billing.play_special_offer_for
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpecialOfferTermsTest {

    private val terms = listOf(
        "star:year",
        "nova:month", "nova:year",
        "supernova:month", "supernova:year",
        "duo:month", "duo:year",
        "family:month", "family:year",
    )

    private val black_friday = SpecialOfferStatusResponse(
        available = true,
        percent_off = 50,
        duration_months = 12,
        eligible_terms = terms,
        theme = "black_friday",
    )

    private fun offer(product: String, base_plan: String, offer_id: String) = GooglePlaySpecialOffer(
        product_id = product,
        base_plan_id = base_plan,
        offer_id = offer_id,
        percent_off = 50,
        duration_months = 12,
    )

    private val nova_monthly = offer("nova", "monthly", "half-price-12m")
    private val nova_yearly = offer("nova", "yearly", "half-price-1y")

    private val config = GooglePlayConfigResponse(
        special_offer = nova_monthly,
        special_offer_yearly = nova_yearly,
        special_offers = listOf(
            nova_monthly,
            nova_yearly,
            offer("family", "monthly", "half-price-12m"),
            offer("family", "yearly", "half-price-1y"),
        ),
    )

    @Test
    fun black_friday_skips_only_star_monthly() {
        assertFalse(offer_covers(black_friday, "star", "month"))
        assertTrue(offer_covers(black_friday, "star", "year"))
        assertTrue(offer_covers(black_friday, "supernova", "year"))
        assertTrue(offer_covers(black_friday, "Family", "month"))
    }

    @Test
    fun unavailable_status_covers_nothing() {
        assertFalse(offer_covers(black_friday.copy(available = false), "nova", "month"))
    }

    @Test
    fun legacy_default_covers_only_nova() {
        val legacy = SpecialOfferStatusResponse(available = true)
        assertTrue(offer_covers(legacy, "nova", "month"))
        assertTrue(offer_covers(legacy, "nova", "year"))
        assertFalse(offer_covers(legacy, "supernova", "month"))
        assertFalse(offer_covers(legacy, "star", "year"))
    }

    @Test
    fun play_lookup_is_null_for_uncovered_term() {
        assertNull(play_special_offer_for(config, "star", "monthly"))
    }

    @Test
    fun play_lookup_reads_special_offers_list() {
        assertEquals("half-price-1y", play_special_offer_for(config, "family", "yearly")?.offer_id)
        assertEquals("half-price-12m", play_special_offer_for(config, "family", "monthly")?.offer_id)
    }

    @Test
    fun play_lookup_falls_back_to_legacy_fields() {
        val legacy_only = config.copy(special_offers = emptyList())
        assertEquals("half-price-12m", play_special_offer_for(legacy_only, "nova", "monthly")?.offer_id)
        assertEquals("half-price-1y", play_special_offer_for(legacy_only, "nova", "yearly")?.offer_id)
        assertNull(play_special_offer_for(legacy_only, "family", "yearly"))
    }
}
