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

package org.astermail.android.subscriptions

import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DeleteSubscriptionSendersTest {
    private val senders = listOf("news@shop.example", "deals@store.example")

    @Test
    fun reports_a_server_delete_when_the_endpoint_answers() = runTest {
        val sent = mutableListOf<List<String>>()

        val deleted = delete_subscription_senders(senders) { sent += it }

        assertTrue(deleted)
        assertEquals(listOf(senders), sent)
    }

    @Test
    fun splits_large_requests_into_chunks() = runTest {
        val many = (1..(DELETE_SUBSCRIPTIONS_LIMIT + 1)).map { "list$it@example.com" }
        val sizes = mutableListOf<Int>()

        delete_subscription_senders(many) { sizes += it.size }

        assertEquals(listOf(DELETE_SUBSCRIPTIONS_LIMIT, 1), sizes)
    }

    @Test
    fun falls_back_to_local_hiding_when_the_endpoint_returns_not_found() = runTest {
        val deleted = delete_subscription_senders(senders) { throw ApiError.NotFoundError }

        assertFalse(deleted)
    }

    @Test
    fun falls_back_to_local_hiding_when_the_server_rejects_the_method() = runTest {
        val deleted = delete_subscription_senders(senders) { throw ApiError.ServerError(405) }

        assertFalse(deleted)
    }

    @Test
    fun surfaces_other_server_failures() = runTest {
        val failure = ApiError.ServerError(500)
        try {
            delete_subscription_senders(senders) { throw failure }
            fail("expected the server error to propagate")
        } catch (t: ApiError) {
            assertSame(failure, t)
        }
    }

    @Test
    fun surfaces_network_failures() = runTest {
        try {
            delete_subscription_senders(senders) { throw ApiError.NetworkError }
            fail("expected the network error to propagate")
        } catch (t: ApiError) {
            assertSame(ApiError.NetworkError, t)
        }
    }

    @Test
    fun recognizes_only_missing_endpoint_errors() {
        assertTrue(is_missing_delete_endpoint(ApiError.NotFoundError))
        assertTrue(is_missing_delete_endpoint(ApiError.ServerError(405)))
        assertFalse(is_missing_delete_endpoint(ApiError.ServerError(500)))
        assertFalse(is_missing_delete_endpoint(ApiError.NetworkError))
        assertFalse(is_missing_delete_endpoint(IllegalStateException("boom")))
    }
}
