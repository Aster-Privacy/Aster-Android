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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.CancelSubscriptionRequest
import org.astermail.android.api.billing.CancelSubscriptionResponse
import org.astermail.android.api.billing.ChangePlanRequest
import org.astermail.android.api.billing.ChangePlanResponse
import org.astermail.android.api.billing.PlanChangePreviewResponse
import org.astermail.android.api.billing.PlanInfo
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.auth.AuthRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `change plan posts the plan and reloads the subscription`() = runTest {
        coEvery { billing_api.get_subscription() } returns paid_sub
        coEvery { billing_api.change_plan(any()) } returns ChangePlanResponse(plan_code = "star", billing_interval = "year")
        vm.change_plan("star", "year")
        advanceUntilIdle()

        coVerify { billing_api.change_plan(ChangePlanRequest(plan_code = "star", billing_interval = "year")) }
        coVerify { billing_api.get_subscription() }
    }

    @Test
    fun `plan change preview exposes the prorated amount due today`() = runTest {
        coEvery { billing_api.preview_plan_change("star", "year") } returns
            PlanChangePreviewResponse(credit_cents = 150, amount_due_cents = 2749, currency = "eur")
        vm.load_plan_change_preview("star", "year")
        advanceUntilIdle()

        val preview = vm.state.value.plan_change_preview
        assertEquals(2749L, preview?.amount_due_cents)
        assertEquals("eur", preview?.currency)
        assertFalse(vm.state.value.plan_change_preview_loading)

        vm.clear_plan_change_preview()
        assertNull(vm.state.value.plan_change_preview)
    }

    @Test
    fun `plan change preview failure falls back without an amount`() = runTest {
        coEvery { billing_api.preview_plan_change(any(), any()) } throws RuntimeException("boom")
        vm.load_plan_change_preview("star", "year")
        advanceUntilIdle()

        assertNull(vm.state.value.plan_change_preview)
        assertTrue(vm.state.value.plan_change_preview_failed)
        assertFalse(vm.state.value.plan_change_preview_loading)
    }

    @Test
    fun `cancel sends the chosen reason without any credential`() = runTest {
        coEvery { billing_api.cancel_subscription(any()) } returns CancelSubscriptionResponse(cancel_at_period_end = true)
        vm.cancel_subscription("too_expensive", "  " + "x".repeat(2500) + "  ")
        advanceUntilIdle()
        vm.state.test {
            var latest = awaitItem()
            while (latest.is_acting) latest = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            billing_api.cancel_subscription(
                CancelSubscriptionRequest(
                    cancel_reason = "too_expensive",
                    cancel_reason_text = "x".repeat(2000),
                ),
            )
        }
        coVerify(exactly = 0) { auth_repository.derive_password_hash_b64(any()) }
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `cancel proceeds without a reason`() = runTest {
        coEvery { billing_api.cancel_subscription(any()) } returns CancelSubscriptionResponse(cancel_at_period_end = true)
        vm.cancel_subscription()
        advanceUntilIdle()
        vm.state.test {
            var latest = awaitItem()
            while (latest.is_acting) latest = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            billing_api.cancel_subscription(
                CancelSubscriptionRequest(
                    cancel_reason = null,
                    cancel_reason_text = null,
                ),
            )
        }
        coVerify(exactly = 0) { auth_repository.derive_password_hash_b64(any()) }
    }

    @Test
    fun `cancel drops unknown reasons but still cancels`() = runTest {
        coEvery { billing_api.cancel_subscription(any()) } returns CancelSubscriptionResponse(cancel_at_period_end = true)
        vm.cancel_subscription("because")
        advanceUntilIdle()
        vm.state.test {
            var latest = awaitItem()
            while (latest.is_acting) latest = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            billing_api.cancel_subscription(
                CancelSubscriptionRequest(
                    cancel_reason = null,
                    cancel_reason_text = null,
                ),
            )
        }
    }

    @Test
    fun `keeps the last known subscription when a refresh fails`() = runTest {
        coEvery { billing_api.get_subscription() } returns paid_sub
        vm.load_subscription()
        advanceUntilIdle()
        assertEquals("pro", vm.state.value.subscription?.plan?.code)

        coEvery { billing_api.get_subscription() } throws RuntimeException("offline")
        vm.load_subscription()
        advanceUntilIdle()

        assertEquals("pro", vm.state.value.subscription?.plan?.code)
        assertNotNull(vm.state.value.subscription_error)

        vm.clear_subscription_error()
        assertNull(vm.state.value.subscription_error)
    }
}
