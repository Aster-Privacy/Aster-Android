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

package org.astermail.android.api

import kotlinx.coroutines.CancellationException

private val raw_body_pattern = Regex("^http [0-9]{3}$", RegexOption.IGNORE_CASE)

private fun looks_like_a_raw_body(text: String): Boolean {
    val trimmed = text.trim()
    return trimmed.startsWith("{") ||
        trimmed.startsWith("[") ||
        trimmed.startsWith("<") ||
        raw_body_pattern.matches(trimmed)
}

private fun detail_unless_default(detail: String, default_detail: String): String? =
    detail.takeIf { it.isNotBlank() && !it.trim().equals(default_detail, ignoreCase = true) }

private fun validation_detail(messages: List<String>): String? {
    val joined = messages.filter { it.isNotBlank() }.joinToString("; ")
    return detail_unless_default(joined, "bad request")
        ?.let { detail_unless_default(it, "unprocessable request") }
}

fun server_supplied_detail(t: Throwable): String? {
    val error = t as? ApiError ?: return null
    return when (error) {
        is ApiError.ForbiddenError -> detail_unless_default(error.detail, "forbidden")
        is ApiError.PlanLimitExceeded -> detail_unless_default(error.detail, "plan limit reached")
        is ApiError.StorageQuotaExceeded -> detail_unless_default(error.detail, "storage full")
        is ApiError.Conflict -> detail_unless_default(error.detail, "conflict")
        is ApiError.RateLimited -> detail_unless_default(error.detail, "rate limited")
        is ApiError.ValidationError -> validation_detail(error.messages)
        is ApiError.UnknownError -> error.detail.takeIf { it.isNotBlank() && !looks_like_a_raw_body(it) }
        is ApiError.PaymentRequired -> detail_unless_default(error.detail, "payment required")
        is ApiError.AttachmentTooLarge -> detail_unless_default(error.detail, "attachment too large")
        is ApiError.SendQuotaReached -> detail_unless_default(error.detail, "send quota reached")
        is ApiError.StepUpRequired -> detail_unless_default(error.detail, "step up required")
        ApiError.InvalidCredentials,
        ApiError.NetworkError,
        ApiError.UnauthorizedError,
        ApiError.NotFoundError,
        is ApiError.ServerError,
        -> null
    }
}

fun user_facing_error(t: Throwable, fallback: String): String {
    if (t is CancellationException) throw t
    return server_supplied_detail(t) ?: fallback
}
