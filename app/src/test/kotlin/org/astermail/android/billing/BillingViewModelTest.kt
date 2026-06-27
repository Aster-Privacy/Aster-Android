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

import android.app.Application
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.PlanInfo
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.auth.AuthRepository
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BillingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var billing_api: BillingApi
    private lateinit var auth_repository: AuthRepository
    private lateinit var vm: BillingViewModel

    private val free_sub = SubscriptionResponse(plan = PlanInfo(code = "free", price_cents = 0), status = "active")
    private val paid_sub = SubscriptionResponse(plan = PlanInfo(code = "pro", price_cents = 999), status = "active")

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        application = mockk(relaxed = true)
        billing_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        vm = BillingViewModel(application, billing_api, auth_repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `emits review request after a free to paid purchase`() = runTest {
        coEvery { billing_api.get_subscription() } returns free_sub
        vm.load_subscription()
        advanceUntilIdle()

        vm.review_request.test {
            vm.consume_checkout_url()
            coEvery { billing_api.get_subscription() } returns paid_sub
            vm.on_resume()
            advanceUntilIdle()
            awaitItem()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `does not emit when checkout abandoned and still on free plan`() = runTest {
        coEvery { billing_api.get_subscription() } returns free_sub
        vm.load_subscription()
        advanceUntilIdle()

        vm.review_request.test {
            vm.consume_checkout_url()
            coEvery { billing_api.get_subscription() } returns free_sub
            vm.on_resume()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `does not emit when already paid before checkout`() = runTest {
        coEvery { billing_api.get_subscription() } returns paid_sub
        vm.load_subscription()
        advanceUntilIdle()

        vm.review_request.test {
            vm.consume_checkout_url()
            coEvery { billing_api.get_subscription() } returns paid_sub
            vm.on_resume()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun `does not emit when returning from billing portal`() = runTest {
        coEvery { billing_api.get_subscription() } returns free_sub
        vm.load_subscription()
        advanceUntilIdle()

        vm.review_request.test {
            vm.consume_portal_url()
            coEvery { billing_api.get_subscription() } returns paid_sub
            vm.on_resume()
            advanceUntilIdle()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
    }
}
