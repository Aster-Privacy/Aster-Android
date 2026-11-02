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

import org.astermail.android.api.billing.AvailablePlan
import org.astermail.android.api.billing.GooglePlayConfigResponse
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.billing.BillingUiState
import org.astermail.android.billing.PlayOffer
import org.astermail.android.billing.format_money
import org.astermail.android.billing.api_plan_price_cents
import org.astermail.android.billing.play_offer_for
import org.astermail.android.billing.play_base_plan_id
import org.astermail.android.billing.play_price_cents
import org.astermail.android.billing.play_product_for_plan
import org.astermail.android.billing.play_special_offer_for

internal val SPECIAL_OFFER_PLAN_ORDER = listOf("star", "nova", "supernova", "duo", "family")

private val SPECIAL_OFFER_STRIPE_LIST_CENTS: Map<String, Pair<Long, Long>> = mapOf(
    "star" to (299L to 2899L),
    "nova" to (899L to 8699L),
    "supernova" to (1799L to 17399L),
    "duo" to (1299L to 11999L),
    "family" to (2699L to 26399L),
)

internal data class offer_card_price(val list_cents: Long, val offer_cents: Long)

internal fun special_offer_plan_codes(eligible_terms: List<String>): List<String> {
    val present = eligible_terms.map { it.substringBefore(':').lowercase() }.toSet()
    return SPECIAL_OFFER_PLAN_ORDER.filter { it in present }
}

internal fun special_offer_list_cents(
    plan_code: String,
    interval: String,
    play_install: Boolean,
    play_offers: List<PlayOffer>,
    play_products: List<GooglePlayProduct>,
    plans: List<AvailablePlan>,
): Long? {
    if (play_install) {
        return play_offer_for(play_offers, play_products, plan_code, interval)
            ?.let { play_price_cents(it.price_micros).toLong() }
    }
    api_plan_price_cents(plans, plan_code, interval)?.let { return it.toLong() }
    val fallback = SPECIAL_OFFER_STRIPE_LIST_CENTS[plan_code.lowercase()] ?: return null
    return if (interval == "year") fallback.second else fallback.first
}

internal fun offer_card_price_for(
    offer: SpecialOfferState,
    plan_code: String,
    interval: String,
    list_cents: Long?,
): offer_card_price? {
    if (list_cents == null || list_cents <= 0L) return null
    if (!offer.covers(plan_code, interval)) return null
    return offer_card_price(list_cents, special_offer_price_cents(list_cents, offer.effective_percent_off))
}

internal data class play_special_target(
    val product_id: String,
    val base_plan_id: String,
    val special: GooglePlaySpecialOffer?,
    val offer: PlayOffer?,
)

internal fun play_special_target_for(
    config: GooglePlayConfigResponse,
    play_offers: List<PlayOffer>,
    play_products: List<GooglePlayProduct>,
    plan_code: String,
    interval: String,
): play_special_target {
    val product_id = play_product_for_plan(play_products, plan_code)?.product_id ?: plan_code
    val base_plan_id = play_base_plan_id(interval)
    val special = play_special_offer_for(config, product_id, base_plan_id)
    return play_special_target(product_id, base_plan_id, special, play_special_offer_for(play_offers, special))
}

internal fun play_offer_list_has_special(
    config: GooglePlayConfigResponse,
    play_offers: List<PlayOffer>,
): Boolean {
    val candidates = config.special_offers + listOfNotNull(config.special_offer, config.special_offer_yearly)
    return candidates.any { play_special_offer_for(play_offers, it) != null }
}

internal fun offer_discounted_label(
    offer: SpecialOfferState,
    billing: BillingUiState,
    play_install: Boolean,
    plan_code: String,
    interval: String,
    list_cents: Long?,
    currency: String,
): String? {
    if (offer_checkout_route(offer, billing, plan_code, interval, play_install) == OfferRoute.None) return null
    val card = offer_card_price_for(offer, plan_code, interval, list_cents) ?: return null
    if (!play_install) return format_money(card.offer_cents, currency)
    val target = play_special_target_for(offer_play_config(billing), billing.play_offers, billing.play_products, plan_code, interval)
    val percent = target.special?.percent_off?.takeIf { it > 0 } ?: offer.effective_percent_off
    return special_offer_play_labels(target.offer, percent)?.discounted
}
