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

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.Flow
import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.GooglePlayAddonProduct
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.api.billing.StorageAddonItem

const val PLAY_STORE_PACKAGE = "com.android.vending"
const val GOOGLE_PLAY_PROVIDER = "google_play"
const val PLAY_BLOCKED_ACTIVE_SUBSCRIPTION = "active_subscription"
const val PLAY_MONTHLY_BASE_PLAN = "monthly"
const val PLAY_YEARLY_BASE_PLAN = "yearly"

data class PlayOffer(
    val product_id: String,
    val base_plan_id: String,
    val offer_token: String,
    val formatted_price: String,
    val price_micros: Long,
    val currency_code: String,
    val billing_interval: String,
    val offer_id: String? = null,
    val intro_formatted_price: String? = null,
    val intro_price_micros: Long? = null,
)

enum class PlayReplacementMode { WITH_TIME_PRORATION, CHARGE_PRORATED_PRICE }

data class PlayOwnedPurchase(
    val purchase_token: String,
    val product_ids: List<String>,
    val is_purchased: Boolean,
    val is_pending: Boolean,
    val is_acknowledged: Boolean,
)

sealed interface PlayPurchaseOutcome {
    data class Purchased(val purchases: List<PlayOwnedPurchase>) : PlayPurchaseOutcome
    data object Pending : PlayPurchaseOutcome
    data object Cancelled : PlayPurchaseOutcome
    data object AlreadyOwned : PlayPurchaseOutcome
    data object Unavailable : PlayPurchaseOutcome
    data object PaymentDeclined : PlayPurchaseOutcome
    data class Failed(val response_code: Int) : PlayPurchaseOutcome
}

interface PlayStore {
    val is_supported: Boolean
    val purchase_updates: Flow<Unit>
    suspend fun query_offers(context: Context, product_ids: List<String>): List<PlayOffer>
    suspend fun purchase(
        activity: Activity,
        offer: PlayOffer,
        obfuscated_account_id: String,
        old_purchase_token: String?,
        replacement_mode: PlayReplacementMode,
    ): PlayPurchaseOutcome
    suspend fun owned_purchases(context: Context): List<PlayOwnedPurchase>?
}

data class PlayPurchaseRequest(
    val offer: PlayOffer,
    val obfuscated_account_id: String,
    val old_purchase_token: String?,
    val replacement_mode: PlayReplacementMode = PlayReplacementMode.WITH_TIME_PRORATION,
    val special_offer: Boolean = false,
)

fun installed_from_play(context: Context): Boolean = runCatching {
    val pm = context.packageManager
    val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        pm.getInstallSourceInfo(context.packageName).installingPackageName
    } else {
        @Suppress("DEPRECATION")
        pm.getInstallerPackageName(context.packageName)
    }
    installer == PLAY_STORE_PACKAGE
}.getOrDefault(false)

fun is_play_install(context: Context, store: PlayStore = PlayBilling): Boolean =
    store.is_supported && installed_from_play(context)

@Composable
fun remember_play_install(): Boolean {
    val context = LocalContext.current
    return remember(context) { is_play_install(context) }
}

fun is_google_play_provider(payment_provider: String?): Boolean =
    payment_provider?.trim()?.lowercase() == GOOGLE_PLAY_PROVIDER

fun play_billing_interval(billing_period: String?): String? = when (billing_period?.uppercase()) {
    "P1M", "P4W", "P30D" -> "month"
    "P1Y", "P12M", "P52W" -> "year"
    else -> null
}

fun play_price_cents(price_micros: Long): Int = (price_micros / 10_000L).toInt()

fun play_manage_subscription_url(package_name: String, product_id: String?): String {
    val base = "https://play.google.com/store/account/subscriptions"
    return if (product_id.isNullOrBlank()) {
        "$base?package=$package_name"
    } else {
        "$base?sku=$product_id&package=$package_name"
    }
}

fun play_product_for_plan(products: List<GooglePlayProduct>, plan_code: String?): GooglePlayProduct? =
    products.firstOrNull { it.plan_code.equals(plan_code, ignoreCase = true) }

fun play_offer_for(
    offers: List<PlayOffer>,
    products: List<GooglePlayProduct>,
    plan_code: String,
    billing_interval: String,
): PlayOffer? {
    val product = play_product_for_plan(products, plan_code) ?: return null
    val interval = if (billing_interval == "year") "year" else "month"
    val base_plan_id = play_base_plan_id(interval)
    val product_offers = offers.filter { it.product_id == product.product_id && it.offer_id == null }
    product_offers.firstOrNull { it.base_plan_id == base_plan_id }?.let { return it }
    if (base_plan_id in product.base_plan_ids) return null
    return product_offers.firstOrNull { offer ->
        offer.billing_interval == interval &&
            (product.base_plan_ids.isEmpty() || offer.base_plan_id in product.base_plan_ids)
    }
}

fun play_base_plan_id(billing_interval: String): String =
    if (billing_interval == "year") PLAY_YEARLY_BASE_PLAN else PLAY_MONTHLY_BASE_PLAN

fun play_price_label(
    offers: List<PlayOffer>,
    products: List<GooglePlayProduct>,
    plan_code: String,
    billing_interval: String,
): String? = play_offer_for(offers, products, plan_code, billing_interval)
    ?.formatted_price
    ?.takeIf { it.isNotBlank() }

fun apply_play_prices(
    plans: List<AvailablePlan>,
    offers: List<PlayOffer>,
    products: List<GooglePlayProduct>,
): List<AvailablePlan> {
    if (offers.isEmpty() || products.isEmpty()) return plans.filter { it.price_cents <= 0 && it.yearly_price_cents <= 0 }
    return plans.mapNotNull { plan ->
        if (plan.price_cents <= 0 && plan.yearly_price_cents <= 0) return@mapNotNull plan
        val monthly = play_offer_for(offers, products, plan.code, "month")
        val yearly = play_offer_for(offers, products, plan.code, "year")
        if (plan.billing_period == "year") {
            yearly?.let { plan.copy(price_cents = play_price_cents(it.price_micros), yearly_price_cents = 0) }
        } else if (monthly == null && yearly == null) {
            null
        } else {
            plan.copy(
                price_cents = monthly?.let { play_price_cents(it.price_micros) } ?: 0,
                yearly_price_cents = yearly?.let { play_price_cents(it.price_micros) } ?: 0,
            )
        }
    }
}

fun play_special_offer_for(offers: List<PlayOffer>, special: GooglePlaySpecialOffer?): PlayOffer? {
    if (special == null || special.offer_id.isBlank()) return null
    return offers.firstOrNull {
        it.product_id == special.product_id &&
            it.base_plan_id == special.base_plan_id &&
            it.offer_id == special.offer_id
    }
}

fun play_addon_product_for(addon_products: List<GooglePlayAddonProduct>, storage_bytes: Long): GooglePlayAddonProduct? =
    if (storage_bytes <= 0) null else addon_products.firstOrNull { it.size_bytes == storage_bytes }

fun play_addon_offer_for(
    offers: List<PlayOffer>,
    addon_products: List<GooglePlayAddonProduct>,
    storage_bytes: Long,
    billing_interval: String = "month",
): PlayOffer? {
    val product = play_addon_product_for(addon_products, storage_bytes) ?: return null
    val interval = if (billing_interval == "year") "year" else "month"
    val base_plan_id = play_base_plan_id(interval)
    val product_offers = offers.filter { it.product_id == product.product_id && it.offer_id == null }
    product_offers.firstOrNull { it.base_plan_id == base_plan_id }?.let { return it }
    if (base_plan_id in product.base_plan_ids) return null
    return product_offers.firstOrNull { offer ->
        offer.billing_interval == interval &&
            (product.base_plan_ids.isEmpty() || offer.base_plan_id in product.base_plan_ids)
    }
}

fun apply_play_addon_prices(
    addons: List<StorageAddonItem>,
    offers: List<PlayOffer>,
    addon_products: List<GooglePlayAddonProduct>,
    billing_interval: String = "month",
): List<StorageAddonItem> = addons.mapNotNull { addon ->
    val offer = play_addon_offer_for(offers, addon_products, addon.storage_bytes, billing_interval)
        ?: return@mapNotNull null
    addon.copy(price_cents = play_price_cents(offer.price_micros))
}

fun play_addon_price_label(
    offers: List<PlayOffer>,
    addon_products: List<GooglePlayAddonProduct>,
    storage_bytes: Long,
    billing_interval: String = "month",
): String? = play_addon_offer_for(offers, addon_products, storage_bytes, billing_interval)
    ?.formatted_price
    ?.takeIf { it.isNotBlank() }

fun play_addon_sells_yearly(offers: List<PlayOffer>, addon_products: List<GooglePlayAddonProduct>): Boolean =
    addon_products.any { play_addon_offer_for(offers, addon_products, it.size_bytes, "year") != null }

fun play_addon_yearly_savings_percent(
    offers: List<PlayOffer>,
    addon_products: List<GooglePlayAddonProduct>,
): Int? = addon_products.mapNotNull { product ->
    val monthly = play_addon_offer_for(offers, addon_products, product.size_bytes, "month") ?: return@mapNotNull null
    val yearly = play_addon_offer_for(offers, addon_products, product.size_bytes, "year") ?: return@mapNotNull null
    yearly_savings_percent(play_price_cents(monthly.price_micros), play_price_cents(yearly.price_micros))
}.minOrNull()

fun play_replacement_mode(current: PlayOffer?, next: PlayOffer): PlayReplacementMode =
    if (
        current != null &&
        current.offer_id == null &&
        next.offer_id == null &&
        current.product_id != next.product_id &&
        current.billing_interval == next.billing_interval &&
        current.currency_code.equals(next.currency_code, ignoreCase = true) &&
        next.price_micros > current.price_micros
    ) {
        PlayReplacementMode.CHARGE_PRORATED_PRICE
    } else {
        PlayReplacementMode.WITH_TIME_PRORATION
    }
