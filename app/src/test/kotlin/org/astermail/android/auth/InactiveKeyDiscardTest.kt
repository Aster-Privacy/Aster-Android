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


package org.astermail.android.auth

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.recovery.ConsumeInactiveKeySetRequest
import org.astermail.android.api.recovery.ConsumeInactiveKeySetResponse
import org.astermail.android.api.recovery.InactiveKeySetInfo
import org.astermail.android.api.recovery.ListInactiveKeySetsResponse
import org.astermail.android.api.recovery.RecoveryApi
import org.junit.Assert.assertEquals
import org.junit.Test

class InactiveKeyDiscardTest {

    private val recovery_api: RecoveryApi = mockk()

    private fun listed(vararg ids: String) {
        coEvery { recovery_api.list_inactive_key_sets() } returns
            ListInactiveKeySetsResponse(ids.map { InactiveKeySetInfo(id = it) })
    }

    @Test
    fun consumes_every_listed_set_and_returns_the_count() = runTest {
        listed("a", "b", "c")
        coEvery { recovery_api.consume_inactive_key_set(any()) } returns ConsumeInactiveKeySetResponse(true)

        assertEquals(3, consume_all_inactive_key_sets(recovery_api))

        coVerify(exactly = 1) { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("a")) }
        coVerify(exactly = 1) { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("b")) }
        coVerify(exactly = 1) { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("c")) }
    }

    @Test
    fun returns_zero_when_listing_fails() = runTest {
        coEvery { recovery_api.list_inactive_key_sets() } throws RuntimeException("offline")

        assertEquals(0, consume_all_inactive_key_sets(recovery_api))

        coVerify(exactly = 0) { recovery_api.consume_inactive_key_set(any()) }
    }

    @Test
    fun returns_zero_when_nothing_is_listed() = runTest {
        listed()

        assertEquals(0, consume_all_inactive_key_sets(recovery_api))

        coVerify(exactly = 0) { recovery_api.consume_inactive_key_set(any()) }
    }

    @Test
    fun keeps_consuming_after_one_set_fails_and_counts_only_successes() = runTest {
        listed("a", "b", "c")
        coEvery { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("a")) } returns
            ConsumeInactiveKeySetResponse(true)
        coEvery { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("b")) } throws
            RuntimeException("server down")
        coEvery { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("c")) } returns
            ConsumeInactiveKeySetResponse(false)

        assertEquals(1, consume_all_inactive_key_sets(recovery_api))

        coVerify(exactly = 1) { recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest("c")) }
    }
}
