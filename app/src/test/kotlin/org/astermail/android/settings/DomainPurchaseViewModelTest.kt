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

package org.astermail.android.settings

import android.app.Application
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.domains.DomainPurchaseApi
import org.astermail.android.api.domains.DomainSearchResponse
import org.astermail.android.api.domains.DomainSearchResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DomainPurchaseViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var purchase_api: DomainPurchaseApi
    private lateinit var vm: DomainPurchaseViewModel

    private val found = DomainSearchResponse(
        results = listOf(DomainSearchResult(domain = "example.com", available = true, price_cents = 1200)),
        suggestions = listOf(DomainSearchResult(domain = "example.net", available = true, price_cents = 1300)),
        has_more_suggestions = true,
        next_suggest_page = 2,
    )

    private fun throttled(retry_after_secs: Int = 60) = ApiError.RateLimited(
        detail = "Too many searches. Try again in a minute.",
        code = "DOMAIN_SEARCH_RATE_LIMITED",
        details = mapOf("retry_after_secs" to retry_after_secs.toString()),
    )

    private fun min_gap() = ApiError.RateLimited(
        detail = "rate limited",
        code = "RATE_LIMIT_EXCEEDED",
        details = mapOf("retry_after_secs" to "1"),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        purchase_api = mockk(relaxed = true)
        vm = DomainPurchaseViewModel(mockk<Application>(relaxed = true), purchase_api)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.pass_debounce() {
        advanceTimeBy(801)
        runCurrent()
    }

    @Test
    fun `typing debounces so only the final query is searched`() = runTest {
        coEvery { purchase_api.search(any(), any()) } returns found
        vm.set_query("exa")
        advanceTimeBy(400)
        vm.set_query("examp")
        advanceTimeBy(400)
        vm.set_query("example")
        pass_debounce()

        coVerify(exactly = 1) { purchase_api.search(any(), any()) }
        coVerify(exactly = 1) { purchase_api.search("example", any()) }
    }

    @Test
    fun `a throttled search shows the message and does not retry immediately`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws throttled(60)
        vm.set_query("example")
        pass_debounce()

        val state = vm.state.value
        assertTrue(state.search_rate_limited)
        assertFalse(state.search_failed)
        assertFalse(state.searching)
        coVerify(exactly = 1) { purchase_api.search(any(), any()) }

        advanceTimeBy(30_000)
        runCurrent()
        coVerify(exactly = 1) { purchase_api.search(any(), any()) }
    }

    @Test
    fun `a throttled search retries once after the delay and never loops`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws throttled(60)
        vm.set_query("example")
        pass_debounce()

        advanceTimeBy(60_001)
        runCurrent()
        coVerify(exactly = 2) { purchase_api.search(any(), any()) }
        assertTrue(vm.state.value.search_rate_limited)

        advanceUntilIdle()
        coVerify(exactly = 2) { purchase_api.search(any(), any()) }
    }

    @Test
    fun `the scheduled retry succeeds and clears the message`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws throttled(5) andThen found
        vm.set_query("example")
        pass_debounce()
        assertTrue(vm.state.value.search_rate_limited)

        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.search_rate_limited)
        assertEquals("example", state.searched_query)
        assertEquals(found.results, state.results)
    }

    @Test
    fun `a long retry after without the throttle code is treated as throttled`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws ApiError.RateLimited(
            code = "RATE_LIMIT_EXCEEDED",
            details = mapOf("retry_after_secs" to "30"),
        )
        vm.set_query("example")
        pass_debounce()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(vm.state.value.search_rate_limited)
        coVerify(exactly = 1) { purchase_api.search(any(), any()) }
    }

    @Test
    fun `changing the query cancels the scheduled retry`() = runTest {
        coEvery { purchase_api.search("example", any()) } throws throttled(60)
        coEvery { purchase_api.search("another", any()) } returns found
        vm.set_query("example")
        pass_debounce()
        assertTrue(vm.state.value.search_rate_limited)

        vm.set_query("another")
        assertFalse(vm.state.value.search_rate_limited)
        advanceUntilIdle()

        coVerify(exactly = 1) { purchase_api.search("example", any()) }
        coVerify(exactly = 1) { purchase_api.search("another", any()) }
    }

    @Test
    fun `the minimum gap limit retries silently once`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws min_gap() andThen found
        vm.set_query("example")
        pass_debounce()

        coVerify(exactly = 1) { purchase_api.search(any(), any()) }
        assertFalse(vm.state.value.search_rate_limited)
        assertTrue(vm.state.value.searching)

        advanceTimeBy(1_101)
        runCurrent()
        coVerify(exactly = 2) { purchase_api.search(any(), any()) }
        assertEquals(found.results, vm.state.value.results)
        assertFalse(vm.state.value.search_rate_limited)
        assertFalse(vm.state.value.search_failed)
    }

    @Test
    fun `the minimum gap limit stops after one retry`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws min_gap()
        vm.set_query("example")
        advanceUntilIdle()

        coVerify(exactly = 2) { purchase_api.search(any(), any()) }
        assertTrue(vm.state.value.search_rate_limited)
        assertFalse(vm.state.value.searching)
    }

    @Test
    fun `other failures still show the generic search error`() = runTest {
        coEvery { purchase_api.search(any(), any()) } throws ApiError.ServerError(503)
        vm.set_query("example")
        advanceUntilIdle()

        assertTrue(vm.state.value.search_failed)
        assertFalse(vm.state.value.search_rate_limited)
        coVerify(exactly = 1) { purchase_api.search(any(), any()) }
    }

    @Test
    fun `throttled load more shows the message and does not retry`() = runTest {
        coEvery { purchase_api.search("example", null) } returns found
        coEvery { purchase_api.search("example", 2) } throws throttled(60)
        vm.set_query("example")
        advanceUntilIdle()

        vm.load_more_suggestions()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.more_suggestions_rate_limited)
        assertFalse(state.loading_more_suggestions)
        assertEquals(found.suggestions, state.suggestions)
        coVerify(exactly = 1) { purchase_api.search("example", 2) }
    }

    @Test
    fun `load more retries once on the minimum gap limit`() = runTest {
        val more = DomainSearchResponse(
            suggestions = listOf(DomainSearchResult(domain = "example.org", available = true, price_cents = 1100)),
            has_more_suggestions = false,
            next_suggest_page = 3,
        )
        coEvery { purchase_api.search("example", null) } returns found
        coEvery { purchase_api.search("example", 2) } throws min_gap() andThen more
        vm.set_query("example")
        advanceUntilIdle()

        vm.load_more_suggestions()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.more_suggestions_rate_limited)
        assertEquals(found.suggestions + more.suggestions, state.suggestions)
        coVerify(exactly = 2) { purchase_api.search("example", 2) }
    }

    @Test
    fun `throttle classification follows the code and retry after`() {
        assertTrue(is_domain_search_throttled(throttled(1)))
        assertFalse(is_domain_search_throttled(min_gap()))
        assertFalse(is_domain_search_throttled(ApiError.RateLimited()))
        assertTrue(
            is_domain_search_throttled(ApiError.RateLimited(details = mapOf("retry_after_secs" to "3"))),
        )
    }
}
