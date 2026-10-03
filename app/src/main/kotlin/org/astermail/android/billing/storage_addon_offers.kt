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

const val STORAGE_ADDON_NUDGE_PERCENT = 80

private const val BYTES_PER_GIB = 1_073_741_824L
private const val BYTES_PER_GB = 1_000_000_000L
private const val FEATURED_ADDON_GB = 200L
private const val SUPERNOVA_NUDGE_GB = 1000L

data class storage_addon_quote(
    val addon_id: String,
    val name: String,
    val storage_bytes: Long,
    val monthly_cents: Int?,
    val yearly_cents: Int?,
    val monthly_label: String? = null,
    val yearly_label: String? = null,
) {
    fun sells(interval: String): Boolean = cents_for(interval) != null

    fun cents_for(interval: String): Int? = if (interval == "year") yearly_cents else monthly_cents

    fun label_for(interval: String): String? = if (interval == "year") yearly_label else monthly_label
}

fun yearly_per_month_cents(yearly_cents: Int): Int = (yearly_cents + 6) / 12

fun storage_addon_save_percent(quote: storage_addon_quote): Int? =
    yearly_savings_percent(quote.monthly_cents, quote.yearly_cents)?.takeIf { it > 0 }

fun storage_addon_per_month_cents(quote: storage_addon_quote, interval: String): Int? =
    if (interval == "year") quote.yearly_cents?.let(::yearly_per_month_cents) else quote.monthly_cents

fun storage_addons_sell_yearly(quotes: List<storage_addon_quote>): Boolean =
    quotes.isNotEmpty() && quotes.all { it.yearly_cents != null && it.monthly_cents != null }

fun storage_addons_badge_percent(quotes: List<storage_addon_quote>): Int? =
    quotes.mapNotNull(::storage_addon_save_percent).minOrNull()

fun effective_storage_addon_interval(chosen: String?, quotes: List<storage_addon_quote>): String =
    if (!storage_addons_sell_yearly(quotes)) "month" else chosen ?: "year"

private fun is_size_gb(bytes: Long, gb: Long): Boolean = bytes == gb * BYTES_PER_GIB || bytes == gb * BYTES_PER_GB

fun is_featured_storage_addon(bytes: Long): Boolean = is_size_gb(bytes, FEATURED_ADDON_GB)

fun is_supernova_nudge_addon(bytes: Long): Boolean = bytes >= SUPERNOVA_NUDGE_GB * BYTES_PER_GB

fun featured_storage_addon_id(quotes: List<storage_addon_quote>): String? =
    quotes.firstOrNull { is_featured_storage_addon(it.storage_bytes) }?.addon_id

fun storage_usage_percent(used_bytes: Long, limit_bytes: Long): Int? {
    if (limit_bytes <= 0L) return null
    return (used_bytes.coerceAtLeast(0L).toDouble() / limit_bytes.toDouble() * 100.0).toInt().coerceAtMost(100)
}

fun shows_storage_usage_nudge(used_bytes: Long, limit_bytes: Long): Boolean =
    (storage_usage_percent(used_bytes, limit_bytes) ?: 0) >= STORAGE_ADDON_NUDGE_PERCENT

private fun positive(cents: Int?): Int? = cents?.takeIf { it > 0 }

fun card_storage_addon_quotes(addons: List<StorageAddonItem>): List<storage_addon_quote> =
    addons.mapNotNull { addon ->
        val monthly = positive(addon.price_cents) ?: return@mapNotNull null
        storage_addon_quote(
            addon_id = addon.id,
            name = addon.name,
            storage_bytes = addon.storage_bytes,
            monthly_cents = monthly,
            yearly_cents = positive(addon.yearly_price_cents),
        )
    }

fun play_storage_addon_quotes(
    addons: List<StorageAddonItem>,
    offers: List<PlayOffer>,
    addon_products: List<GooglePlayAddonProduct>,
): List<storage_addon_quote> = addons.mapNotNull { addon ->
    val monthly = play_addon_offer_for(offers, addon_products, addon.storage_bytes, "month")
    val yearly = play_addon_offer_for(offers, addon_products, addon.storage_bytes, "year")
    if (monthly == null && yearly == null) return@mapNotNull null
    storage_addon_quote(
        addon_id = addon.id,
        name = addon.name,
        storage_bytes = addon.storage_bytes,
        monthly_cents = monthly?.let { positive(play_price_cents(it.price_micros)) },
        yearly_cents = yearly?.let { positive(play_price_cents(it.price_micros)) },
        monthly_label = monthly?.formatted_price?.takeIf { it.isNotBlank() },
        yearly_label = yearly?.formatted_price?.takeIf { it.isNotBlank() },
    )
}
