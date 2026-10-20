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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.R
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.CancelSubscriptionRequest
import org.astermail.android.api.billing.CancelSubscriptionResponse
import org.astermail.android.api.billing.ChangePlanRequest
import org.astermail.android.api.billing.ChangePlanResponse
import org.astermail.android.api.billing.OnboardingChecklistResponse
import org.astermail.android.api.billing.PlanChangePreviewResponse
import org.astermail.android.api.billing.PlanInfo
import org.astermail.android.api.billing.PortalSessionResponse
import org.astermail.android.api.billing.SubscriptionResponse
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.PreferencesCacheStore
import org.astermail.android.storage.SessionKeyStore
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
        coEvery { auth_repository.stored_password_hash_b64() } returns "cached_hash"
        io.mockk.every { auth_repository.active_account_id } returns kotlinx.coroutines.flow.MutableStateFlow("user1")
        vm = BillingViewModel(application, billing_api, auth_repository, PreferencesCacheStore(null), SessionKeyStore(null))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `keeps the current subscription when a refresh fails`() = runTest {
        coEvery { billing_api.get_subscription() } returns paid_sub
        vm.load_subscription()
        advanceUntilIdle()

        coEvery { billing_api.get_subscription() } throws java.io.IOException("offline")
        vm.load_subscription()
        advanceUntilIdle()

        assertEquals(paid_sub, vm.state.value.subscription)
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
    fun `cancel sends the chosen reason without asking for the password`() = runTest {
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
                    password_hash = "cached_hash",
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
                    password_hash = "cached_hash",
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
                    password_hash = "cached_hash",
                    cancel_reason = null,
                    cancel_reason_text = null,
                ),
            )
        }
    }

    @Test
    fun `cancel reports a signed out session instead of failing silently`() = runTest {
        every { application.getString(R.string.session_expired_sign_in) } returns "Your session expired. Sign in again."
        coEvery { auth_repository.stored_password_hash_b64() } returns null
        vm.cancel_subscription()
        advanceUntilIdle()

        coVerify(exactly = 0) { billing_api.cancel_subscription(any()) }
        assertEquals("Your session expired. Sign in again.", vm.state.value.error)
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `cancel refused during another billing action reports it instead of hanging`() = runTest {
        every { application.getString(R.string.billing_action_in_progress) } returns "Another billing change is still in progress."
        val gate = CompletableDeferred<PortalSessionResponse>()
        coEvery { billing_api.create_portal_session() } coAnswers { gate.await() }
        vm.open_portal()
        advanceUntilIdle()
        assertTrue(vm.state.value.is_acting)
        assertEquals("portal", vm.state.value.acting_action)

        val started = vm.cancel_subscription("too_expensive", "no longer needed")
        advanceUntilIdle()

        assertFalse(started)
        assertEquals("Another billing change is still in progress.", vm.state.value.error)
        assertEquals("portal", vm.state.value.acting_action)
        coVerify(exactly = 0) { billing_api.cancel_subscription(any()) }

        gate.complete(PortalSessionResponse(url = "https://billing.invalid/portal"))
        advanceUntilIdle()
    }

    @Test
    fun `cancel marks itself acting before the request is dispatched`() = runTest {
        coEvery { billing_api.cancel_subscription(any()) } returns CancelSubscriptionResponse(cancel_at_period_end = true)

        val started = vm.cancel_subscription()

        assertTrue(started)
        assertTrue(vm.state.value.is_acting)
        assertEquals("cancel", vm.state.value.acting_action)
        assertNull(vm.state.value.error)
        advanceUntilIdle()
        assertFalse(vm.state.value.is_acting)
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

    private fun signed_in_vm(): BillingViewModel {
        val store = SessionKeyStore(null).also { it.put_user_id("user1") }
        return BillingViewModel(application, billing_api, auth_repository, PreferencesCacheStore(null), store)
    }

    @Test
    fun `a finished onboarding checklist is not refetched on inbox entry`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns
            OnboardingChecklistResponse(tasks = mapOf("compose" to true, "import_mail" to true))
        val billing = signed_in_vm()
        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        coVerify(exactly = 1) { billing_api.get_onboarding_checklist() }
    }

    @Test
    fun `an open onboarding checklist is refetched on inbox entry`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns
            OnboardingChecklistResponse(tasks = mapOf("compose" to true, "import_mail" to false))
        val billing = signed_in_vm()
        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        coVerify(exactly = 2) { billing_api.get_onboarding_checklist() }
    }

    @Test
    fun `a forced onboarding load always refetches`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns
            OnboardingChecklistResponse(dismissed_at = "2026-01-01T00:00:00Z")
        val billing = signed_in_vm()
        billing.load_onboarding_checklist()
        advanceUntilIdle()

        billing.load_onboarding_checklist()
        advanceUntilIdle()

        coVerify(exactly = 2) { billing_api.get_onboarding_checklist() }
    }

    private val open_checklist = OnboardingChecklistResponse(tasks = mapOf("first_email" to true, "import_mail" to false))

    @Test
    fun `a dismissed checklist stays dismissed when a stale load returns`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns open_checklist
        val billing = signed_in_vm()
        billing.load_onboarding_checklist()
        advanceUntilIdle()

        val stale_load = CompletableDeferred<OnboardingChecklistResponse>()
        coEvery { billing_api.get_onboarding_checklist() } coAnswers { stale_load.await() }
        billing.load_onboarding_checklist()
        billing.dismiss_onboarding_checklist()
        advanceUntilIdle()
        stale_load.complete(open_checklist)
        advanceUntilIdle()

        assertNotNull(billing.state.value.onboarding?.dismissed_at)
        assertEquals(open_checklist.tasks, billing.state.value.onboarding?.tasks)
    }

    @Test
    fun `a dismissed checklist stays dismissed across a reload on resume`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns open_checklist
        val billing = signed_in_vm()
        billing.load_onboarding_checklist()
        advanceUntilIdle()

        billing.dismiss_onboarding_checklist()
        advanceUntilIdle()
        billing.load_onboarding_checklist()
        advanceUntilIdle()

        assertNotNull(billing.state.value.onboarding?.dismissed_at)
        coVerify(exactly = 1) { billing_api.dismiss_onboarding_checklist() }
    }

    @Test
    fun `a dismissed checklist is not refetched on resume`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns open_checklist
        val billing = signed_in_vm()
        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        billing.dismiss_onboarding_checklist()
        advanceUntilIdle()
        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        assertNotNull(billing.state.value.onboarding?.dismissed_at)
        coVerify(exactly = 1) { billing_api.get_onboarding_checklist() }
    }

    @Test
    fun `a checklist dismissed by another account is still fetched on resume`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns open_checklist
        val store = SessionKeyStore(null).also { it.put_user_id("user1") }
        val billing = BillingViewModel(application, billing_api, auth_repository, PreferencesCacheStore(null), store)
        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()
        billing.dismiss_onboarding_checklist()
        advanceUntilIdle()

        store.put_user_id("user2")
        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        assertNull(billing.state.value.onboarding?.dismissed_at)
        coVerify(exactly = 2) { billing_api.get_onboarding_checklist() }
    }

    @Test
    fun `a failed dismissal restores the checklist`() = runTest {
        coEvery { billing_api.get_onboarding_checklist() } returns open_checklist
        coEvery { billing_api.dismiss_onboarding_checklist() } throws java.io.IOException("offline")
        val billing = signed_in_vm()
        billing.load_onboarding_checklist()
        advanceUntilIdle()

        billing.dismiss_onboarding_checklist()
        assertNotNull(billing.state.value.onboarding?.dismissed_at)
        advanceUntilIdle()

        assertNull(billing.state.value.onboarding?.dismissed_at)
        assertEquals(open_checklist.tasks, billing.state.value.onboarding?.tasks)

        billing.load_onboarding_checklist(force = false)
        advanceUntilIdle()

        assertNull(billing.state.value.onboarding?.dismissed_at)
        coVerify(exactly = 2) { billing_api.get_onboarding_checklist() }
    }

    private val lifetime_sub = SubscriptionResponse(
        plan = PlanInfo(code = "supernova", price_cents = 16080, billing_period = "year"),
        status = "active",
        current_period_end = "2126-09-30T00:00:00Z",
        is_lifetime = true,
    )

    private fun load_lifetime() {
        coEvery { billing_api.get_subscription() } returns lifetime_sub
        vm.load_subscription()
    }

    @Test
    fun `a lifetime plan blocks checkout without contacting the server`() = runTest {
        load_lifetime()
        advanceUntilIdle()

        vm.start_checkout("supernova", "year")
        advanceUntilIdle()

        coVerify(exactly = 0) { billing_api.create_checkout_session(any()) }
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.checkout_url)
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `a lifetime plan blocks plan changes, interval switches, and cancellation`() = runTest {
        load_lifetime()
        advanceUntilIdle()

        vm.change_plan("star", "year")
        advanceUntilIdle()
        vm.switch_billing("month")
        advanceUntilIdle()
        val cancelled = vm.cancel_subscription("too_expensive", null)
        advanceUntilIdle()

        assertFalse(cancelled)
        coVerify(exactly = 0) { billing_api.change_plan(any()) }
        coVerify(exactly = 0) { billing_api.switch_billing_interval(any()) }
        coVerify(exactly = 0) { billing_api.cancel_subscription(any()) }
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `a lifetime plan blocks crypto plan checkout and invoices`() = runTest {
        load_lifetime()
        advanceUntilIdle()

        vm.start_crypto_checkout("supernova", 12)
        advanceUntilIdle()
        vm.create_crypto_native_invoice("supernova", 12, "btc", "bitcoin")
        advanceUntilIdle()

        coVerify(exactly = 0) { billing_api.create_crypto_checkout_session(any()) }
        coVerify(exactly = 0) { billing_api.create_crypto_native_invoice(any()) }
    }

    @Test
    fun `a regular yearly plan still reaches checkout`() = runTest {
        coEvery { billing_api.get_subscription() } returns lifetime_sub.copy(is_lifetime = false)
        vm.load_subscription()
        advanceUntilIdle()

        vm.change_plan("star", "year")
        advanceUntilIdle()

        coVerify(exactly = 1) { billing_api.change_plan(ChangePlanRequest(plan_code = "star", billing_interval = "year")) }
    }
}
