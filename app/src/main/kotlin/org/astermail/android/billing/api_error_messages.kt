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

import android.content.Context
import kotlinx.coroutines.CancellationException
import org.astermail.android.R
import org.astermail.android.api.ApiError

fun localized_api_error(context: Context, t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    val detail = (t as? ApiError)?.message?.takeIf { it.isNotBlank() }
    return when (t) {
        is ApiError.NetworkError -> context.getString(R.string.error_network)
        is ApiError.UnauthorizedError -> context.getString(R.string.session_expired_sign_in)
        is ApiError.InvalidCredentials -> context.getString(R.string.incorrect_password)
        is ApiError.StepUpRequired -> context.getString(R.string.device_verification_failed)
        is ApiError.PaymentRequired -> context.getString(R.string.error_payment_required)
        is ApiError.PlanLimitExceeded -> context.getString(R.string.error_plan_limit_reached)
        is ApiError.StorageQuotaExceeded -> context.getString(R.string.error_storage_full)
        is ApiError.AttachmentTooLarge -> context.getString(R.string.error_attachment_too_large)
        is ApiError.SendQuotaReached -> context.getString(R.string.error_send_quota_reached)
        is ApiError.ForbiddenError -> detail ?: context.getString(R.string.error_forbidden)
        is ApiError.NotFoundError -> context.getString(R.string.error_not_found)
        is ApiError.ServerError -> context.getString(R.string.error_server)
        is ApiError.RateLimited -> context.getString(R.string.error_rate_limited)
        is ApiError.Conflict -> detail ?: context.getString(R.string.error_conflict)
        is ApiError.ValidationError -> detail ?: fallback
        is ApiError.UnknownError -> fallback
        else -> fallback
    }
}

fun is_upgrade_error(t: Throwable?, plan_code: String? = null): Boolean {
    val remediable = t is ApiError.SendQuotaReached ||
        t is ApiError.PlanLimitExceeded ||
        t is ApiError.PaymentRequired
    return remediable && has_higher_plan_tier(plan_code)
}

fun is_billing_link_error(t: Throwable?): Boolean = t is ApiError.PaymentRequired

fun billing_interval_label(context: Context, interval: String?): String =
    when (normalize_billing_interval(interval)) {
        "month" -> context.getString(R.string.interval_month)
        "year" -> context.getString(R.string.interval_year)
        else -> context.getString(R.string.interval_generic)
    }

fun billing_interval_per_label(context: Context, interval: String?): String =
    when (normalize_billing_interval(interval)) {
        "month" -> context.getString(R.string.plan_price_per_month)
        "year" -> context.getString(R.string.plan_price_per_year)
        else -> context.getString(R.string.plan_price_per_period)
    }
