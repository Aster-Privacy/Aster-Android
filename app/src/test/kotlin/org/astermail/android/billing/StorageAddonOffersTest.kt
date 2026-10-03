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

import org.astermail.android.api.billing.GooglePlayAddonProduct
import org.astermail.android.api.billing.StorageAddonItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAddonOffersTest {
    private val gb = 1_073_741_824L
    private val new_catalog = listOf(
        StorageAddonItem(id = "a", storage_bytes = 50 * gb, price_cents = 199, yearly_price_cents = 1999),
        StorageAddonItem(id = "b", storage_bytes = 200 * gb, price_cents = 499, yearly_price_cents = 4999),
        StorageAddonItem(id = "c", storage_bytes = 1024 * gb, price_cents = 1299, yearly_price_cents = 12999),
    )
    private val old_catalog = listOf(
        StorageAddonItem(id = "x", storage_bytes = 100 * gb, price_cents = 299),
        StorageAddonItem(id = "y", storage_bytes = 500 * gb, price_cents = 999),
    )

    private fun offer(product: String, base_plan: String, micros: Long, label: String) = PlayOffer(
        product_id = product,
        base_plan_id = base_plan,
        offer_token = "t_$product$base_plan",
        formatted_price = label,
        price_micros = micros,
        currency_code = "EUR",
        billing_interval = if (base_plan == PLAY_YEARLY_BASE_PLAN) "year" else "month",
    )

    @Test
    fun yearly_per_month_rounds_to_the_nearest_cent() {
        assertEquals(167, yearly_per_month_cents(1999))
        assertEquals(417, yearly_per_month_cents(4999))
        assertEquals(1083, yearly_per_month_cents(12999))
        assertEquals(100, yearly_per_month_cents(1200))
    }

    @Test
    fun savings_are_computed_and_rounded_down() {
        val quotes = card_storage_addon_quotes(new_catalog)
        assertEquals(listOf(16, 16, 16), quotes.map { storage_addon_save_percent(it) })
        val quote = storage_addon_quote("z", "", 10 * gb, monthly_cents = 100, yearly_cents = 1000)
        assertEquals(16, storage_addon_save_percent(quote))
        assertEquals(50, storage_addon_save_percent(quote.copy(yearly_cents = 600)))
        assertNull(storage_addon_save_percent(quote.copy(yearly_cents = 1200)))
        assertNull(storage_addon_save_percent(quote.copy(yearly_cents = null)))
        assertEquals(16, storage_addons_badge_percent(quotes))
    }

    @Test
    fun per_month_follows_the_interval() {
        val quote = card_storage_addon_quotes(new_catalog).first { it.addon_id == "b" }
        assertEquals(417, storage_addon_per_month_cents(quote, "year"))
        assertEquals(499, storage_addon_per_month_cents(quote, "month"))
        assertEquals(4999, quote.cents_for("year"))
        assertEquals(499, quote.cents_for("month"))
    }

    @Test
    fun yearly_defaults_only_when_every_addon_sells_yearly() {
        val fresh = card_storage_addon_quotes(new_catalog)
        val legacy = card_storage_addon_quotes(old_catalog)
        assertTrue(storage_addons_sell_yearly(fresh))
        assertFalse(storage_addons_sell_yearly(legacy))
        assertFalse(storage_addons_sell_yearly(fresh + legacy))
        assertFalse(storage_addons_sell_yearly(emptyList()))
        assertEquals("year", effective_storage_addon_interval(null, fresh))
        assertEquals("month", effective_storage_addon_interval("month", fresh))
        assertEquals("month", effective_storage_addon_interval(null, legacy))
        assertEquals("month", effective_storage_addon_interval("year", legacy))
        assertNull(storage_addons_badge_percent(legacy))
    }

    @Test
    fun featured_and_supernova_sizes() {
        val fresh = card_storage_addon_quotes(new_catalog)
        assertEquals("b", featured_storage_addon_id(fresh))
        assertNull(featured_storage_addon_id(card_storage_addon_quotes(old_catalog)))
        assertTrue(is_featured_storage_addon(200_000_000_000L))
        assertFalse(is_supernova_nudge_addon(500 * gb))
        assertTrue(is_supernova_nudge_addon(1024 * gb))
        assertTrue(is_supernova_nudge_addon(1_000_000_000_000L))
    }

    @Test
    fun usage_nudge_starts_at_eighty_percent() {
        assertNull(storage_usage_percent(10, 0))
        assertEquals(79, storage_usage_percent(79, 100))
        assertEquals(100, storage_usage_percent(150, 100))
        assertFalse(shows_storage_usage_nudge(79, 100))
        assertTrue(shows_storage_usage_nudge(80, 100))
        assertFalse(shows_storage_usage_nudge(80, 0))
    }

    @Test
    fun card_quotes_skip_free_items_and_ignore_zero_yearly() {
        val quotes = card_storage_addon_quotes(
            listOf(
                StorageAddonItem(id = "f", storage_bytes = gb, price_cents = 0),
                StorageAddonItem(id = "g", storage_bytes = 2 * gb, price_cents = 100, yearly_price_cents = 0),
            ),
        )
        assertEquals(listOf("g"), quotes.map { it.addon_id })
        assertNull(quotes.single().yearly_cents)
    }

    @Test
    fun play_quotes_use_store_prices_and_labels() {
        val products = listOf(
            GooglePlayAddonProduct(product_id = "storage_50gb", size_bytes = 50 * gb, base_plan_ids = listOf("monthly", "yearly")),
            GooglePlayAddonProduct(product_id = "storage_200gb", size_bytes = 200 * gb, base_plan_ids = listOf("monthly", "yearly")),
        )
        val offers = listOf(
            offer("storage_50gb", PLAY_MONTHLY_BASE_PLAN, 2_190_000, "2,19 €"),
            offer("storage_50gb", PLAY_YEARLY_BASE_PLAN, 21_990_000, "21,99 €"),
            offer("storage_200gb", PLAY_MONTHLY_BASE_PLAN, 5_490_000, "5,49 €"),
            offer("storage_200gb", PLAY_YEARLY_BASE_PLAN, 54_990_000, "54,99 €"),
        )
        val quotes = play_storage_addon_quotes(new_catalog, offers, products)
        assertEquals(listOf("a", "b"), quotes.map { it.addon_id })
        val featured = quotes.first { it.addon_id == "b" }
        assertEquals(549, featured.monthly_cents)
        assertEquals(5499, featured.yearly_cents)
        assertEquals("54,99 €", featured.label_for("year"))
        assertEquals("5,49 €", featured.label_for("month"))
        assertEquals(458, storage_addon_per_month_cents(featured, "year"))
        assertEquals(16, storage_addon_save_percent(featured))
        assertTrue(storage_addons_sell_yearly(quotes))

        val monthly_only = play_storage_addon_quotes(new_catalog, offers.filter { it.base_plan_id == PLAY_MONTHLY_BASE_PLAN }, products)
        assertFalse(storage_addons_sell_yearly(monthly_only))
        assertEquals("month", effective_storage_addon_interval(null, monthly_only))
    }
}
