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

fun card_addons_sell_yearly(addons: List<StorageAddonItem>): Boolean =
    addons.isNotEmpty() && addons.all { (it.yearly_price_cents ?: 0) > 0 }

fun card_addon_yearly_savings_percent(addons: List<StorageAddonItem>): Int? =
    addons.mapNotNull { yearly_savings_percent(it.price_cents, it.yearly_price_cents) }.minOrNull()

fun card_addon_price_cents(addons: List<StorageAddonItem>, bytes: Long, interval: String): Int? {
    val addon = addons.firstOrNull { it.storage_bytes == bytes } ?: return null
    val yearly = addon.yearly_price_cents?.takeIf { it > 0 }
    return if (interval == "year" && yearly != null) yearly else addon.price_cents
}
