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

import org.astermail.android.api.billing.StorageAddonItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAddonPricingTest {
    private val gb = 1_073_741_824L
    private val new_catalog = listOf(
        StorageAddonItem(id = "a", storage_bytes = 50 * gb, price_cents = 199, yearly_price_cents = 1999),
        StorageAddonItem(id = "b", storage_bytes = 200 * gb, price_cents = 499, yearly_price_cents = 4999),
        StorageAddonItem(id = "c", storage_bytes = 1024 * gb, price_cents = 1299, yearly_price_cents = 12999),
    )
    private val old_catalog = listOf(
        StorageAddonItem(id = "x", storage_bytes = 100 * gb, price_cents = 299),
    )

    @Test
    fun yearly_is_offered_only_when_every_addon_has_a_yearly_price() {
        assertTrue(card_addons_sell_yearly(new_catalog))
        assertFalse(card_addons_sell_yearly(old_catalog))
        assertFalse(card_addons_sell_yearly(new_catalog + old_catalog))
        assertFalse(card_addons_sell_yearly(emptyList()))
    }

    @Test
    fun savings_badge_uses_the_smallest_saving() {
        assertEquals(16, card_addon_yearly_savings_percent(new_catalog))
        assertNull(card_addon_yearly_savings_percent(old_catalog))
    }

    @Test
    fun price_follows_the_chosen_interval() {
        assertEquals(4999, card_addon_price_cents(new_catalog, 200 * gb, "year"))
        assertEquals(499, card_addon_price_cents(new_catalog, 200 * gb, "month"))
        assertEquals(299, card_addon_price_cents(old_catalog, 100 * gb, "year"))
        assertNull(card_addon_price_cents(new_catalog, 5 * gb, "month"))
    }
}
