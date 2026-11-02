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
import org.astermail.android.billing.BillingUiState
import org.astermail.android.billing.BillingViewModel
import org.astermail.android.billing.offer_checkout_uses_family_endpoint

internal sealed interface OfferRoute {
    data object None : OfferRoute
    data class Play(val product_id: String, val base_plan_id: String) : OfferRoute
    data class Stripe(val plan_code: String, val interval: String, val family_endpoint: Boolean) : OfferRoute
}

internal fun offer_play_config(billing: BillingUiState) = GooglePlayConfigResponse(
    special_offer = billing.play_special_offer,
    special_offer_yearly = billing.play_special_offer_yearly,
    special_offers = billing.play_special_offers,
)

internal fun offer_checkout_route(
    offer: SpecialOfferState,
    billing: BillingUiState,
    plan_code: String,
    interval: String,
    is_play: Boolean,
): OfferRoute {
    if (!offer.covers(plan_code, interval)) return OfferRoute.None
    if (!is_play) {
        return OfferRoute.Stripe(plan_code, interval, offer_checkout_uses_family_endpoint(plan_code, true))
    }
    val target = play_special_target_for(
        offer_play_config(billing),
        billing.play_offers,
        billing.play_products,
        plan_code,
        interval,
    )
    if (target.special == null || target.offer == null) return OfferRoute.None
    return OfferRoute.Play(target.product_id, target.base_plan_id)
}

internal fun offer_selectable_plan_codes(
    offer: SpecialOfferState,
    billing: BillingUiState,
    is_play: Boolean,
    plan_codes: List<String>,
): List<String> {
    if (!is_play) return plan_codes
    return plan_codes.filter { code ->
        listOf("month", "year").any { offer_checkout_route(offer, billing, code, it, true) != OfferRoute.None }
    }
}

internal fun BillingViewModel.start_routed_checkout(
    route: OfferRoute,
    plan_code: String,
    interval: String,
    currency: String,
) {
    when (route) {
        is OfferRoute.Play -> start_play_special_offer(route.product_id, route.base_plan_id)
        is OfferRoute.Stripe -> start_checkout(plan_code, interval, currency, special_offer = true)
        OfferRoute.None -> start_checkout(plan_code, interval, currency)
    }
}
