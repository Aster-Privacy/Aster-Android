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

import android.app.Activity
import android.app.Application
import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.CheckoutSessionRequest
import org.astermail.android.api.billing.GooglePlayConfigResponse
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlayVerifyRequest
import org.astermail.android.api.billing.GooglePlayVerifyResponse
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
class BillingViewModelPlayTest {

    private class fake_play_store : PlayStore {
        override val is_supported = true
        override val purchase_updates: Flow<Unit> = MutableSharedFlow()
        var offers: List<PlayOffer> = emptyList()
        var owned: List<PlayOwnedPurchase> = emptyList()
        var outcome: PlayPurchaseOutcome = PlayPurchaseOutcome.Cancelled
        val purchases = mutableListOf<PlayPurchaseRequest>()
        override suspend fun query_offers(context: Context, product_ids: List<String>) = offers
        override suspend fun purchase(
            activity: Activity,
            offer: PlayOffer,
            obfuscated_account_id: String,
            old_purchase_token: String?,
        ): PlayPurchaseOutcome {
            purchases += PlayPurchaseRequest(offer, obfuscated_account_id, old_purchase_token)
            return outcome
        }
        override suspend fun owned_purchases(context: Context) = owned
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var billing_api: BillingApi
    private lateinit var auth_repository: AuthRepository
    private lateinit var store: fake_play_store
    private lateinit var vm: BillingViewModel

    private val star_month = PlayOffer("star", "monthly", "tok_star_m", "$3.49", 3_490_000, "EUR", "month")
    private val star_year = PlayOffer("star", "yearly", "tok_star_y", "$33.99", 33_990_000, "EUR", "year")
    private val enabled_config = GooglePlayConfigResponse(
        enabled = true,
        obfuscated_account_id = "acct_hash",
        products = listOf(GooglePlayProduct("star", "star", listOf("monthly", "yearly"))),
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val application: Application = mockk(relaxed = true)
        billing_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        every { auth_repository.is_signed_in } returns MutableStateFlow(true)
        store = fake_play_store().apply { offers = listOf(star_month, star_year) }
        vm = BillingViewModel(application, billing_api, auth_repository)
        vm.play_store = store
        vm.play_install_check = { true }
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `stays on stripe checkout when play is disabled on the server`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns GooglePlayConfigResponse(enabled = false)
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        assertFalse(vm.state.value.play_enabled)
        assertNull(vm.state.value.play_purchase_request)
        coVerify { billing_api.create_checkout_session(any<CheckoutSessionRequest>()) }
    }

    @Test
    fun `stays on stripe checkout for sideloaded installs`() = runTest {
        vm.play_install_check = { false }
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        coVerify(exactly = 0) { billing_api.get_google_play_config() }
        coVerify { billing_api.create_checkout_session(any<CheckoutSessionRequest>()) }
    }

    @Test
    fun `prepares a play purchase instead of stripe checkout`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        vm.start_checkout("star", "year")
        advanceUntilIdle()
        val request = vm.state.value.play_purchase_request
        assertNotNull(request)
        assertEquals("tok_star_y", request!!.offer.offer_token)
        assertEquals("acct_hash", request.obfuscated_account_id)
        assertNull(request.old_purchase_token)
        assertEquals("eur", vm.state.value.play_currency)
        assertFalse(vm.state.value.is_acting)
        coVerify(exactly = 0) { billing_api.create_checkout_session(any<CheckoutSessionRequest>()) }
    }

    @Test
    fun `blocks a second subscription from another provider`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config.copy(purchase_blocked_reason = "active_subscription")
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        assertNull(vm.state.value.play_purchase_request)
        assertNotNull(vm.state.value.error)
    }

    @Test
    fun `verifies a completed purchase with the server`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns GooglePlayVerifyResponse(plan_code = "star", pending = false)
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(plan = PlanInfo(code = "star", price_cents = 349), status = "active")
        store.outcome = PlayPurchaseOutcome.Purchased(
            listOf(PlayOwnedPurchase("purchase_1", listOf("star"), is_purchased = true, is_pending = false, is_acknowledged = false)),
        )
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        vm.launch_play_purchase(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(1, store.purchases.size)
        coVerify(exactly = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_1")) }
        assertNull(vm.state.value.play_purchase_request)
        assertFalse(vm.state.value.is_acting)
        assertNotNull(vm.state.value.info)
    }

    @Test
    fun `does not verify a cancelled purchase`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        vm.launch_play_purchase(mockk(relaxed = true))
        advanceUntilIdle()
        coVerify(exactly = 0) { billing_api.verify_google_play_purchase(any()) }
        assertFalse(vm.state.value.is_acting)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `redeems unacknowledged purchases once`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns GooglePlayVerifyResponse(plan_code = "star", pending = false)
        store.owned = listOf(PlayOwnedPurchase("purchase_2", listOf("star"), is_purchased = true, is_pending = false, is_acknowledged = false))
        vm.ensure_play_config()
        advanceUntilIdle()
        vm.redeem_play_purchases()
        advanceUntilIdle()
        coVerify(exactly = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_2")) }
        assertTrue(vm.state.value.play_enabled)
    }

    @Test
    fun `routes play subscribers to the play subscriptions page`() = runTest {
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349),
            status = "active",
            payment_provider = "google_play",
        )
        vm.load_subscription()
        advanceUntilIdle()
        vm.open_portal()
        advanceUntilIdle()
        assertTrue(vm.state.value.portal_url.orEmpty().startsWith("https://play.google.com/store/account/subscriptions"))
        coVerify(exactly = 0) { billing_api.create_portal_session() }
    }
}
