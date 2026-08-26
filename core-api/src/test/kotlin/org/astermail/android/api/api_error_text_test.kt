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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class api_error_text_test {

    private val fallback = "Something went wrong"

    @Test
    fun unauthorized_never_leaks_its_internal_message() {
        assertEquals(fallback, user_facing_error(ApiError.UnauthorizedError, fallback))
        assertNull(server_supplied_detail(ApiError.UnauthorizedError))
    }

    @Test
    fun client_side_placeholders_never_reach_the_user() {
        val errors = listOf(
            ApiError.NetworkError,
            ApiError.UnauthorizedError,
            ApiError.NotFoundError,
            ApiError.ForbiddenError(),
            ApiError.ForbiddenError("forbidden"),
            ApiError.Conflict("conflict"),
            ApiError.RateLimited(),
            ApiError.RateLimited("rate limited"),
            ApiError.RateLimited("Rate limit exceeded. Please try again later"),
            ApiError.ServerError(500),
            ApiError.ServerError(503),
            ApiError.ValidationError(listOf("bad request")),
            ApiError.ValidationError(listOf("unprocessable request")),
            ApiError.ValidationError(listOf("content_nonce must be 12 bytes"), "VALIDATION_ERROR"),
            ApiError.ValidationError(listOf("encrypted content too large"), "VALIDATION_ERROR"),
            ApiError.ValidationError(listOf("too many conditions"), "VALIDATION_ERROR"),
            ApiError.ValidationError(listOf("Invalid regex pattern"), "VALIDATION_ERROR"),
            ApiError.PlanLimitExceeded("plan limit reached", null),
            ApiError.StorageQuotaExceeded("storage full"),
            ApiError.PlanLimitExceeded("You have reached the alias limit for your current plan", "aliases"),
            ApiError.StorageQuotaExceeded("You have used all of your storage"),
            ApiError.UnknownError("http 418"),
        )
        errors.forEach { error ->
            assertEquals("leaked for $error", fallback, user_facing_error(error, fallback))
        }
    }

    @Test
    fun a_rate_limit_with_a_reset_time_keeps_its_own_wording() {
        val error = ApiError.RateLimited(
            "You can try again in 3 minutes.",
            resets_at = "2026-01-01T00:00:00Z",
        )
        assertEquals("You can try again in 3 minutes.", user_facing_error(error, fallback))
    }

    @Test
    fun a_raw_response_body_is_never_shown() {
        val bodies = listOf(
            "<html><body>Bad gateway</body></html>",
            "{\"error\":\"Unauthorized\",\"code\":\"UNAUTHORIZED\"}",
            "[\"nope\"]",
        )
        bodies.forEach { body ->
            assertEquals("leaked for $body", fallback, user_facing_error(ApiError.UnknownError(body), fallback))
            assertNull(server_supplied_detail(ApiError.UnknownError(body)))
        }
    }

    @Test
    fun server_supplied_detail_is_still_shown() {
        assertEquals(
            "Your family plan requires 2FA",
            user_facing_error(ApiError.ForbiddenError("Your family plan requires 2FA"), fallback),
        )
        assertEquals(
            "Username is taken",
            user_facing_error(ApiError.ValidationError(listOf("Username is taken"), "USERNAME_IN_USE"), fallback),
        )
        assertEquals(
            "That domain extension is not supported yet.",
            user_facing_error(
                ApiError.ValidationError(listOf("That domain extension is not supported yet."), "VALIDATION_ERROR"),
                fallback,
            ),
        )
        assertEquals(
            "NOT_ACADEMIC_DOMAIN",
            user_facing_error(ApiError.ValidationError(listOf("NOT_ACADEMIC_DOMAIN"), "VALIDATION_ERROR"), fallback),
        )
        assertEquals(
            "Mailbox is full",
            user_facing_error(ApiError.UnknownError("Mailbox is full"), fallback),
        )
    }

    @Test
    fun non_api_throwables_use_the_fallback() {
        assertEquals(fallback, user_facing_error(IllegalStateException("rekey rejected"), fallback))
    }
}
