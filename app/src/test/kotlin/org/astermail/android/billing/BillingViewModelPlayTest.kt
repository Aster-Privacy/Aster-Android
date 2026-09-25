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
import kotlinx.coroutines.CompletableDeferred
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
import org.astermail.android.api.ApiError
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.CheckoutSessionRequest
import org.astermail.android.api.billing.GooglePlayAddonProduct
import org.astermail.android.api.billing.GooglePlayConfigResponse
import org.astermail.android.api.billing.GooglePlayProduct
import org.astermail.android.api.billing.GooglePlaySpecialOffer
import org.astermail.android.api.billing.StorageAddonItem
import org.astermail.android.api.billing.StorageAddonsResponse
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
            replacement_mode: PlayReplacementMode,
        ): PlayPurchaseOutcome {
            purchases += PlayPurchaseRequest(offer, obfuscated_account_id, old_purchase_token, replacement_mode)
            return outcome
        }
        override suspend fun owned_purchases(context: Context) = owned
    }

    private val dispatcher = StandardTestDispatcher()
    private lateinit var billing_api: BillingApi
    private lateinit var auth_repository: AuthRepository
    private lateinit var store: fake_play_store
    private lateinit var vm: BillingViewModel
    private val active_account = MutableStateFlow<String?>("user1")

    private val star_month = PlayOffer("star", "monthly", "tok_star_m", "$3.49", 3_490_000, "EUR", "month")
    private val star_year = PlayOffer("star", "yearly", "tok_star_y", "$33.99", 33_990_000, "EUR", "year")
    private val enabled_config = GooglePlayConfigResponse(
        enabled = true,
        obfuscated_account_id = "acct_hash",
        products = listOf(GooglePlayProduct("star", "star", listOf("monthly", "yearly"))),
    )
    private val confirmed = GooglePlayVerifyResponse(plan_code = "star", pending = false)

    private val five_gb = 5L * 1024 * 1024 * 1024
    private val nova_month = PlayOffer("nova", "monthly", "tok_nova_m", "$8.99", 8_990_000, "EUR", "month")
    private val nova_half = PlayOffer(
        "nova", "monthly", "tok_nova_half", "$8.99", 8_990_000, "EUR", "month",
        offer_id = "half-price-12m",
        intro_formatted_price = "$4.49",
        intro_price_micros = 4_490_000,
    )
    private val addon_5gb = PlayOffer("storage_5gb", "monthly", "tok_5gb", "$0.99", 990_000, "EUR", "month")
    private val full_config = enabled_config.copy(
        products = listOf(
            GooglePlayProduct("star", "star", listOf("monthly", "yearly")),
            GooglePlayProduct("nova", "nova", listOf("monthly", "yearly")),
        ),
        addon_products = listOf(GooglePlayAddonProduct("storage_5gb", "5 GB", five_gb, listOf("monthly"))),
        special_offer = GooglePlaySpecialOffer("nova", "monthly", "half-price-12m", 50, 12),
    )
    private val addons_response = StorageAddonsResponse(
        available_addons = listOf(StorageAddonItem(id = "addon_5", name = "5 GB", storage_bytes = five_gb, price_cents = 99)),
    )

    private fun use_full_catalog() {
        store.offers = listOf(star_month, star_year, nova_month, nova_half, addon_5gb)
        coEvery { billing_api.get_storage_addons() } returns addons_response
    }

    private fun owned_star(token: String, acknowledged: Boolean) =
        PlayOwnedPurchase(token, listOf("star"), is_purchased = true, is_pending = false, is_acknowledged = acknowledged)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        val application: Application = mockk(relaxed = true)
        billing_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        every { auth_repository.is_signed_in } returns MutableStateFlow(true)
        every { auth_repository.active_account_id } returns active_account
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
    fun `never opens stripe checkout on a play install when play is disabled on the server`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns GooglePlayConfigResponse(enabled = false)
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        assertFalse(vm.state.value.play_enabled)
        assertNull(vm.state.value.play_purchase_request)
        assertNull(vm.state.value.checkout_url)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.is_acting)
        coVerify(exactly = 0) { billing_api.create_checkout_session(any<CheckoutSessionRequest>()) }
    }

    @Test
    fun `never opens stripe checkout on a play install when the config request fails`() = runTest {
        coEvery { billing_api.get_google_play_config() } throws ApiError.NetworkError
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        assertNull(vm.state.value.play_purchase_request)
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { billing_api.create_checkout_session(any<CheckoutSessionRequest>()) }
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
    fun `passes the owned token when a play subscriber changes plan`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349),
            status = "active",
            payment_provider = "google_play",
        )
        store.owned = listOf(owned_star("tok_old", acknowledged = true))
        vm.start_checkout("star", "year")
        advanceUntilIdle()
        val request = vm.state.value.play_purchase_request
        assertNotNull(request)
        assertEquals("tok_old", request!!.old_purchase_token)
        coVerify(exactly = 0) { billing_api.verify_google_play_purchase(any()) }
    }

    @Test
    fun `opens play subscriptions when a play subscriber owns nothing on this device`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349),
            status = "active",
            payment_provider = "google_play",
        )
        vm.start_checkout("star", "year")
        advanceUntilIdle()
        assertNull(vm.state.value.play_purchase_request)
        assertTrue(vm.state.value.portal_url.orEmpty().startsWith("https://play.google.com/store/account/subscriptions"))
    }

    @Test
    fun `redeems an owned but unverified purchase instead of launching a new one`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed
        store.owned = listOf(owned_star("purchase_3", acknowledged = true))
        vm.start_checkout("star", "month")
        advanceUntilIdle()
        assertNull(vm.state.value.play_purchase_request)
        assertTrue(store.purchases.isEmpty())
        coVerify(atLeast = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_3")) }
        assertNotNull(vm.state.value.info)
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `verifies a completed purchase with the server`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(plan = PlanInfo(code = "star", price_cents = 349), status = "active")
        store.outcome = PlayPurchaseOutcome.Purchased(listOf(owned_star("purchase_1", acknowledged = false)))
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
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed
        store.owned = listOf(owned_star("purchase_2", acknowledged = false))
        vm.ensure_play_config()
        advanceUntilIdle()
        vm.redeem_play_purchases()
        advanceUntilIdle()
        coVerify(exactly = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_2")) }
        assertTrue(vm.state.value.play_enabled)
    }

    @Test
    fun `re-verifies an acknowledged purchase the backend does not attribute to play`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed
        store.owned = listOf(owned_star("purchase_5", acknowledged = true))
        vm.ensure_play_config()
        advanceUntilIdle()
        coVerify(exactly = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_5")) }
    }

    @Test
    fun `retries verification after a server error`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        var calls = 0
        coEvery { billing_api.verify_google_play_purchase(any()) } coAnswers {
            calls += 1
            if (calls < 3) throw ApiError.ServerError(503)
            confirmed
        }
        store.owned = listOf(owned_star("purchase_6", acknowledged = false))
        vm.ensure_play_config()
        advanceUntilIdle()
        assertEquals(3, calls)
    }

    @Test
    fun `does not retry verification after a client error`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        var calls = 0
        coEvery { billing_api.verify_google_play_purchase(any()) } coAnswers {
            calls += 1
            throw ApiError.ServerError(400)
        }
        store.owned = listOf(owned_star("purchase_7", acknowledged = false))
        vm.ensure_play_config()
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `queues a forced redeem that arrives while another is running`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        coEvery { billing_api.verify_google_play_purchase(any()) } coAnswers {
            calls += 1
            gate.await()
            confirmed
        }
        store.owned = listOf(owned_star("purchase_8", acknowledged = false))
        vm.ensure_play_config()
        advanceUntilIdle()
        assertEquals(1, calls)
        vm.redeem_play_purchases(force = true)
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `resets play state when the active account changes`() = runTest {
        coEvery { billing_api.get_google_play_config() } returns enabled_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed
        store.owned = listOf(owned_star("purchase_9", acknowledged = false))
        advanceUntilIdle()
        vm.ensure_play_config()
        advanceUntilIdle()
        assertTrue(vm.state.value.play_enabled)
        coVerify(exactly = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_9")) }
        active_account.value = "user2"
        advanceUntilIdle()
        assertFalse(vm.state.value.play_enabled)
        assertNull(vm.state.value.play_account_id)
        assertTrue(vm.state.value.play_offers.isEmpty())
        vm.ensure_play_config()
        advanceUntilIdle()
        coVerify(exactly = 2) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "purchase_9")) }
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

    @Test
    fun `blocks stripe management for a stripe subscriber on a play install`() = runTest {
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349),
            status = "active",
            payment_provider = "stripe",
        )
        vm.load_subscription()
        advanceUntilIdle()
        vm.open_portal()
        advanceUntilIdle()
        assertNull(vm.state.value.portal_url)
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { billing_api.create_portal_session() }
    }

    @Test
    fun `buys a storage add-on through play without replacing the plan`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(purchase_blocked_reason = "active_subscription")
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349),
            status = "active",
            payment_provider = "stripe",
        )
        vm.load_storage_addons()
        advanceUntilIdle()
        vm.purchase_storage_addon("addon_5")
        advanceUntilIdle()
        val request = vm.state.value.play_purchase_request
        assertNotNull(request)
        assertEquals("tok_5gb", request!!.offer.offer_token)
        assertNull(request.old_purchase_token)
        coVerify(exactly = 0) { billing_api.purchase_storage_addon(any()) }
    }

    @Test
    fun `refuses an unknown add-on on a play install`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config
        vm.load_storage_addons()
        advanceUntilIdle()
        vm.purchase_storage_addon("missing")
        advanceUntilIdle()
        assertNull(vm.state.value.play_purchase_request)
        assertNotNull(vm.state.value.error)
        coVerify(exactly = 0) { billing_api.purchase_storage_addon(any()) }
    }

    @Test
    fun `launches the play offer token for an eligible special offer`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(special_offer_eligible = true)
        vm.start_play_special_offer()
        advanceUntilIdle()
        val request = vm.state.value.play_purchase_request
        assertNotNull(request)
        assertEquals("tok_nova_half", request!!.offer.offer_token)
        assertEquals("half-price-12m", request.offer.offer_id)
    }

    @Test
    fun `does not launch the special offer when the account is not eligible`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(special_offer_eligible = false)
        vm.start_play_special_offer()
        advanceUntilIdle()
        assertNull(vm.state.value.play_purchase_request)
        assertNotNull(vm.state.value.error)
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `a pending special offer purchase confirms the offer once play completes it`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(special_offer_eligible = true)
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed.copy(plan_code = "nova")
        store.outcome = PlayPurchaseOutcome.Pending
        vm.start_play_special_offer()
        advanceUntilIdle()
        vm.launch_play_purchase(mockk(relaxed = true))
        advanceUntilIdle()
        assertNull(vm.state.value.play_confirmed_product)

        store.owned = listOf(
            PlayOwnedPurchase("purchase_offer", listOf("nova"), is_purchased = true, is_pending = false, is_acknowledged = false),
        )
        vm.redeem_play_purchases()
        advanceUntilIdle()

        assertEquals("nova", vm.state.value.play_confirmed_product)
        assertTrue(vm.state.value.play_confirmed_special_offer)
    }

    @Test
    fun `a pending plan purchase never confirms the special offer`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(special_offer_eligible = true)
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed.copy(plan_code = "nova")
        store.outcome = PlayPurchaseOutcome.Pending
        vm.start_checkout("nova", "month")
        advanceUntilIdle()
        vm.launch_play_purchase(mockk(relaxed = true))
        advanceUntilIdle()

        store.owned = listOf(
            PlayOwnedPurchase("purchase_plan", listOf("nova"), is_purchased = true, is_pending = false, is_acknowledged = false),
        )
        vm.redeem_play_purchases()
        advanceUntilIdle()

        assertFalse(vm.state.value.play_confirmed_special_offer)
    }

    @Test
    fun `plan purchases never use the special offer token`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(special_offer_eligible = true)
        vm.start_checkout("nova", "month")
        advanceUntilIdle()
        assertEquals("tok_nova_m", vm.state.value.play_purchase_request?.offer?.offer_token)
    }

    @Test
    fun `reports an ineligible offer purchase as an error`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config.copy(special_offer_eligible = true)
        coEvery { billing_api.verify_google_play_purchase(any()) } throws ApiError.Conflict("ineligible", PLAY_SPECIAL_OFFER_INELIGIBLE)
        store.outcome = PlayPurchaseOutcome.Purchased(
            listOf(PlayOwnedPurchase("purchase_offer", listOf("nova"), is_purchased = true, is_pending = false, is_acknowledged = false)),
        )
        vm.start_play_special_offer()
        advanceUntilIdle()
        vm.launch_play_purchase(mockk(relaxed = true))
        advanceUntilIdle()
        assertNotNull(vm.state.value.error)
        assertNull(vm.state.value.info)
        assertFalse(vm.state.value.is_acting)
    }

    @Test
    fun `charges the prorated price when a play subscriber upgrades`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349, billing_period = "month"),
            status = "active",
            payment_provider = "google_play",
        )
        store.owned = listOf(owned_star("tok_old", acknowledged = true))
        vm.start_checkout("nova", "month")
        advanceUntilIdle()
        val request = vm.state.value.play_purchase_request
        assertEquals("tok_old", request?.old_purchase_token)
        assertEquals(PlayReplacementMode.CHARGE_PRORATED_PRICE, request?.replacement_mode)
    }

    @Test
    fun `uses time proration when a play subscriber switches to yearly`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config
        coEvery { billing_api.get_subscription() } returns SubscriptionResponse(
            plan = PlanInfo(code = "star", price_cents = 349, billing_period = "month"),
            status = "active",
            payment_provider = "google_play",
        )
        store.owned = listOf(owned_star("tok_old", acknowledged = true))
        vm.load_subscription()
        advanceUntilIdle()
        vm.switch_billing("year")
        advanceUntilIdle()
        val request = vm.state.value.play_purchase_request
        assertEquals("tok_star_y", request?.offer?.offer_token)
        assertEquals(PlayReplacementMode.WITH_TIME_PRORATION, request?.replacement_mode)
    }

    @Test
    fun `restore reports when there is nothing to restore`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config
        vm.restore_play_purchases()
        advanceUntilIdle()
        assertNotNull(vm.state.value.info)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.is_acting)
        coVerify(exactly = 0) { billing_api.verify_google_play_purchase(any()) }
    }

    @Test
    fun `restore verifies every owned catalog purchase`() = runTest {
        use_full_catalog()
        coEvery { billing_api.get_google_play_config() } returns full_config
        coEvery { billing_api.verify_google_play_purchase(any()) } returns confirmed
        store.owned = listOf(
            owned_star("tok_plan", acknowledged = true),
            PlayOwnedPurchase("tok_addon", listOf("storage_5gb"), is_purchased = true, is_pending = false, is_acknowledged = true),
            PlayOwnedPurchase("tok_other", listOf("unrelated"), is_purchased = true, is_pending = false, is_acknowledged = true),
        )
        vm.restore_play_purchases()
        advanceUntilIdle()
        coVerify(atLeast = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("star", "tok_plan")) }
        coVerify(atLeast = 1) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("storage_5gb", "tok_addon")) }
        coVerify(exactly = 0) { billing_api.verify_google_play_purchase(GooglePlayVerifyRequest("unrelated", "tok_other")) }
        assertNotNull(vm.state.value.info)
        assertFalse(vm.state.value.is_acting)
    }
}
