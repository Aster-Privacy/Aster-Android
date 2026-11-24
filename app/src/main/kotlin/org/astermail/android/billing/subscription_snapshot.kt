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

import org.astermail.android.api.ApiError
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.api.settings.SubscriptionInfo
import org.astermail.android.api.settings.SubscriptionPlanNested

enum class subscription_payment_source { play, card, crypto, none }

enum class subscription_status_kind { free, active, trialing, past_due, canceling, expired, lifetime }

private val ENDED_STATUSES = setOf("expired", "canceled", "cancelled", "incomplete_expired", "unpaid")

fun SubscriptionResponse.to_subscription_info(): SubscriptionInfo? {
    val code = plan.code.trim()
    val name = plan.name.trim()
    if (code.isEmpty() && name.isEmpty()) return null
    return SubscriptionInfo(
        status = status.trim().takeIf { it.isNotEmpty() },
        currency = currency?.trim()?.takeIf { it.isNotEmpty() },
        current_period_end = current_period_end,
        cancel_at_period_end = cancel_at_period_end,
        plan = SubscriptionPlanNested(
            id = plan.id.takeIf { it.isNotBlank() },
            code = code.takeIf { it.isNotEmpty() },
            name = name.takeIf { it.isNotEmpty() },
            price_cents = plan.price_cents,
            billing_period = plan.billing_period,
            storage_limit_bytes = plan.storage_limit_bytes,
        ),
        payment_failed_at = payment_failed_at,
        grace_period_end = grace_period_end,
        payment_provider = payment_provider,
        has_stripe_subscription = has_stripe_subscription,
        is_lifetime = is_lifetime,
    )
}

fun subscription_payment_source_of(
    payment_provider: String?,
    has_stripe_subscription: Boolean?,
    is_paid_plan: Boolean,
): subscription_payment_source {
    if (!is_paid_plan) return subscription_payment_source.none
    val provider = payment_provider?.trim()?.lowercase().orEmpty()
    return when {
        is_google_play_provider(provider) -> subscription_payment_source.play
        is_crypto_provider(provider) -> subscription_payment_source.crypto
        provider == "stripe" || provider == "card" -> subscription_payment_source.card
        has_stripe_subscription == true -> subscription_payment_source.card
        else -> subscription_payment_source.none
    }
}

fun subscription_status_kind_of(
    status: String?,
    is_paid_plan: Boolean,
    is_lifetime: Boolean,
    cancel_at_period_end: Boolean,
    payment_failed: Boolean,
): subscription_status_kind {
    if (!is_paid_plan) return subscription_status_kind.free
    if (is_lifetime) return subscription_status_kind.lifetime
    val normalized = status?.trim()?.lowercase().orEmpty()
    return when {
        normalized in ENDED_STATUSES -> subscription_status_kind.expired
        payment_failed || normalized == "past_due" -> subscription_status_kind.past_due
        cancel_at_period_end -> subscription_status_kind.canceling
        normalized == "trialing" -> subscription_status_kind.trialing
        else -> subscription_status_kind.active
    }
}

fun is_top_tier_plan(plan_code: String?): Boolean {
    val code = plan_code?.trim()?.lowercase().orEmpty()
    if (code.isEmpty() || code == "free") return false
    return !has_higher_plan_tier(code)
}

fun is_transient_billing_failure(t: Throwable): Boolean {
    var current: Throwable? = t
    repeat(6) {
        val step = current ?: return false
        when (step) {
            is ApiError.NetworkError -> return true
            is ApiError.ServerError -> return step.code >= 500
            is java.io.IOException -> return true
        }
        current = step.cause?.takeIf { it !== step }
    }
    return false
}
