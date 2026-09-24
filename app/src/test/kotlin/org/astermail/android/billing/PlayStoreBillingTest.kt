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

package org.astermail.android.billing

import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.GooglePlayAddonProduct
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.api.billing.StorageAddonItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStoreBillingTest {

    private val products = listOf(
        GooglePlayProduct(product_id = "star", plan_code = "star", base_plan_ids = listOf("monthly", "yearly")),
        GooglePlayProduct(product_id = "nova", plan_code = "nova", base_plan_ids = listOf("monthly", "yearly")),
    )

    private fun offer(product_id: String, interval: String, micros: Long, currency: String = "USD") = PlayOffer(
        product_id = product_id,
        base_plan_id = if (interval == "year") "yearly" else "monthly",
        offer_token = "$product_id-$interval",
        formatted_price = "",
        price_micros = micros,
        currency_code = currency,
        billing_interval = interval,
    )

    private val offers = listOf(
        offer("star", "month", 3_490_000),
        offer("star", "year", 33_990_000),
        offer("nova", "month", 9_990_000),
    )

    @Test
    fun `maps play billing periods to intervals`() {
        assertEquals("month", play_billing_interval("P1M"))
        assertEquals("year", play_billing_interval("p1y"))
        assertEquals("year", play_billing_interval("P12M"))
        assertNull(play_billing_interval("P3M"))
        assertNull(play_billing_interval(null))
    }

    @Test
    fun `converts micros to minor units`() {
        assertEquals(349, play_price_cents(3_490_000))
        assertEquals(120_000, play_price_cents(1_200_000_000))
    }

    @Test
    fun `builds the play subscriptions link`() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=star&package=org.astermail.android",
            play_manage_subscription_url("org.astermail.android", "star"),
        )
        assertEquals(
            "https://play.google.com/store/account/subscriptions?package=org.astermail.android",
            play_manage_subscription_url("org.astermail.android", null),
        )
    }

    @Test
    fun `finds the offer for a plan and interval`() {
        assertEquals("star-year", play_offer_for(offers, products, "STAR", "year")?.offer_token)
        assertEquals("nova-month", play_offer_for(offers, products, "nova", "month")?.offer_token)
        assertNull(play_offer_for(offers, products, "nova", "year"))
        assertNull(play_offer_for(offers, products, "supernova", "month"))
    }

    @Test
    fun `reprices plans and drops plans play does not sell`() {
        val plans = listOf(
            AvailablePlan(code = "free", price_cents = 0),
            AvailablePlan(code = "star", price_cents = 299, yearly_price_cents = 2899),
            AvailablePlan(code = "nova", price_cents = 899, yearly_price_cents = 8699),
            AvailablePlan(code = "duo", price_cents = 1299, yearly_price_cents = 12499),
        )
        val priced = apply_play_prices(plans, offers, products)
        assertEquals(listOf("free", "star", "nova"), priced.map { it.code })
        assertEquals(349, priced[1].price_cents)
        assertEquals(3399, priced[1].yearly_price_cents)
        assertEquals(999, priced[2].price_cents)
        assertEquals(0, priced[2].yearly_price_cents)
    }

    @Test
    fun `reprices yearly plan rows from the annual offer`() {
        val plans = listOf(AvailablePlan(code = "star", price_cents = 2899, billing_period = "year"))
        assertEquals(3399, apply_play_prices(plans, offers, products).single().price_cents)
    }

    @Test
    fun `keeps only free plans without offers`() {
        val plans = listOf(AvailablePlan(code = "free", price_cents = 0), AvailablePlan(code = "duo", price_cents = 1299))
        assertEquals(listOf("free"), apply_play_prices(plans, emptyList(), products).map { it.code })
    }

    @Test
    fun `matches the offer by base plan id`() {
        val mislabeled = listOf(
            PlayOffer("star", "yearly", "star-yearly", "", 33_990_000, "USD", "month"),
            PlayOffer("star", "monthly", "star-monthly", "", 3_490_000, "USD", "year"),
        )
        assertEquals("star-yearly", play_offer_for(mislabeled, products, "star", "year")?.offer_token)
        assertEquals("star-monthly", play_offer_for(mislabeled, products, "star", "month")?.offer_token)
        val unknown = listOf(PlayOffer("star", "legacy", "star-legacy", "", 3_490_000, "USD", "month"))
        assertNull(play_offer_for(unknown, products, "star", "month"))
    }

    @Test
    fun `uses the play formatted price as the label`() {
        val yen = listOf(PlayOffer("star", "monthly", "star-monthly", "¥500", 500_000_000, "JPY", "month"))
        assertEquals("¥500", play_price_label(yen, products, "star", "month"))
        assertNull(play_price_label(yen, products, "star", "year"))
    }

    @Test
    fun `detects the google play provider`() {
        assertTrue(is_google_play_provider(" Google_Play "))
        assertFalse(is_google_play_provider("stripe"))
        assertFalse(is_google_play_provider(null))
    }

    private val gb = 1024L * 1024 * 1024
    private val addon_products = listOf(
        GooglePlayAddonProduct("storage_5gb", "5 GB", 5 * gb, listOf("monthly")),
        GooglePlayAddonProduct("storage_1tb", "1 TB", 1024 * gb, listOf("monthly")),
    )
    private val half_price = offer("nova", "month", 8_990_000).copy(offer_token = "nova-half", offer_id = "half-price-12m")

    @Test
    fun `base plan lookup ignores developer offers`() {
        val picked = play_offer_for(listOf(half_price) + offers, products, "nova", "month")
        assertEquals("nova-month", picked?.offer_token)
        assertNull(play_offer_for(listOf(half_price), products, "nova", "month"))
    }

    @Test
    fun `finds the special offer by product, base plan, and offer id`() {
        val special = GooglePlaySpecialOffer("nova", "monthly", "half-price-12m", 50, 12)
        assertEquals("nova-half", play_special_offer_for(offers + half_price, special)?.offer_token)
        assertNull(play_special_offer_for(offers, special))
        assertNull(play_special_offer_for(offers + half_price, special.copy(offer_id = "")))
        assertNull(play_special_offer_for(offers + half_price, null))
    }

    @Test
    fun `matches add-on products by size`() {
        val addon_offers = listOf(offer("storage_5gb", "month", 990_000), offer("storage_1tb", "month", 19_990_000))
        assertEquals("storage_5gb-month", play_addon_offer_for(addon_offers, addon_products, 5 * gb)?.offer_token)
        assertEquals("storage_1tb-month", play_addon_offer_for(addon_offers, addon_products, 1024 * gb)?.offer_token)
        assertNull(play_addon_offer_for(addon_offers, addon_products, 7 * gb))
        assertNull(play_addon_offer_for(addon_offers, addon_products, 0))
    }

    @Test
    fun `prices add-ons from play and drops the ones play does not sell`() {
        val addons = listOf(
            StorageAddonItem(id = "a", storage_bytes = 5 * gb, price_cents = 99),
            StorageAddonItem(id = "b", storage_bytes = 1024 * gb, price_cents = 1999),
        )
        val priced = apply_play_addon_prices(addons, listOf(offer("storage_5gb", "month", 1_190_000)), addon_products)
        assertEquals(listOf("a"), priced.map { it.id })
        assertEquals(119, priced.single().price_cents)
    }

    @Test
    fun `charges the prorated price only for a same-interval upgrade`() {
        val star_m = offer("star", "month", 2_990_000)
        val nova_m = offer("nova", "month", 8_990_000)
        val star_y = offer("star", "year", 28_990_000)
        assertEquals(PlayReplacementMode.CHARGE_PRORATED_PRICE, play_replacement_mode(star_m, nova_m))
        assertEquals(PlayReplacementMode.WITH_TIME_PRORATION, play_replacement_mode(nova_m, star_m))
        assertEquals(PlayReplacementMode.WITH_TIME_PRORATION, play_replacement_mode(star_m, star_y))
        assertEquals(PlayReplacementMode.WITH_TIME_PRORATION, play_replacement_mode(null, nova_m))
        assertEquals(PlayReplacementMode.WITH_TIME_PRORATION, play_replacement_mode(star_m, half_price))
        assertEquals(
            PlayReplacementMode.WITH_TIME_PRORATION,
            play_replacement_mode(star_m, nova_m.copy(currency_code = "EUR")),
        )
    }
}
