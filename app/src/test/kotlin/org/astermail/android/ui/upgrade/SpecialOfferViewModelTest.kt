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

package org.astermail.android.ui.upgrade

import android.content.Context
import android.content.SharedPreferences
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.OfferPreferences
import org.astermail.android.api.billing.SpecialOfferClaimResponse
import org.astermail.android.api.billing.SpecialOfferStatusResponse
import org.astermail.android.auth.AuthRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpecialOfferViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var billing_api: BillingApi
    private lateinit var store: OfferPreferencesStore
    private lateinit var vm: SpecialOfferViewModel
    private var server_offers_enabled = true
    private val accounts = mutableMapOf<SpecialOfferViewModel, MutableStateFlow<String?>>()

    private fun offer_vm(context: Context = mockk(relaxed = true)): SpecialOfferViewModel {
        val account = MutableStateFlow<String?>(null)
        val auth = mockk<AuthRepository>(relaxed = true)
        every { auth.active_account_id } returns account
        return SpecialOfferViewModel(billing_api, auth, store, context).also { accounts[it] = account }
    }

    private fun cache_context(cache: MutableMap<String, Any?>): Context {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putBoolean(any(), any()) } answers { cache[firstArg()] = secondArg<Boolean>(); editor }
        every { editor.putInt(any(), any()) } answers { cache[firstArg()] = secondArg<Int>(); editor }
        every { editor.putLong(any(), any()) } answers { cache[firstArg()] = secondArg<Long>(); editor }
        every { editor.putString(any(), any()) } answers { cache[firstArg()] = secondArg<String?>(); editor }
        every { editor.apply() } just Runs
        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getBoolean(any(), any()) } answers { cache[firstArg()] as? Boolean ?: secondArg() }
        every { prefs.getInt(any(), any()) } answers { cache[firstArg()] as? Int ?: secondArg() }
        every { prefs.getLong(any(), any()) } answers { cache[firstArg()] as? Long ?: secondArg() }
        every { prefs.getString(any(), any()) } answers { cache[firstArg()] as? String ?: secondArg() }
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    private fun cached_offer(cached_at: Long): MutableMap<String, Any?> = mutableMapOf(
        "account.available" to true,
        "account.percent_off" to 50,
        "account.duration_months" to 12,
        "account.plan_code" to "nova",
        "account.cached_at" to cached_at,
    )

    private fun SpecialOfferViewModel.load() {
        accounts.getValue(this).value = "account"
    }

    private val auto_show_offer = SpecialOfferStatusResponse(
        available = true,
        auto_show = true,
        percent_off = 50,
        duration_months = 12,
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        billing_api = mockk(relaxed = true)
        coEvery { billing_api.get_special_offer() } returns auto_show_offer
        coEvery { billing_api.claim_special_offer() } returns SpecialOfferClaimResponse(granted = true)
        server_offers_enabled = true
        coEvery { billing_api.get_offer_preferences() } coAnswers {
            OfferPreferences(in_app_offers_enabled = server_offers_enabled)
        }
        coEvery { billing_api.set_offer_preferences(any()) } coAnswers {
            val preferences = firstArg<OfferPreferences>()
            server_offers_enabled = preferences.in_app_offers_enabled
            preferences
        }
        store = OfferPreferencesStore(billing_api)
        vm = offer_vm()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `turning offers off closes the sheet and stops auto show`() = runTest {
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value.auto_show)
        vm.claim_and_open()
        advanceUntilIdle()
        assertTrue(vm.state.value.is_open)

        store.set_enabled(false)
        advanceUntilIdle()

        assertFalse(vm.state.value.is_open)
        assertFalse(vm.state.value.auto_show)
        assertFalse(vm.state.value.available)
    }

    @Test
    fun `claim does nothing while offers are off`() = runTest {
        store.set_enabled(false)
        advanceUntilIdle()

        vm.claim_and_open()
        advanceUntilIdle()

        assertFalse(vm.state.value.is_open)
        coVerify(exactly = 0) { billing_api.claim_special_offer() }
    }

    @Test
    fun `load after opting out never auto shows`() = runTest {
        store.set_enabled(false)
        advanceUntilIdle()

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.state.value.auto_show)
        assertFalse(vm.state.value.available)
    }

    @Test
    fun `turning offers back on refreshes without opening the sheet`() = runTest {
        vm.load()
        advanceUntilIdle()
        store.set_enabled(false)
        advanceUntilIdle()

        store.set_enabled(true)
        advanceUntilIdle()

        coVerify(exactly = 2) { billing_api.get_special_offer() }
        assertTrue(vm.state.value.available)
        assertFalse(vm.state.value.auto_show)
        assertFalse(vm.state.value.is_open)
    }

    @Test
    fun `offer hidden by an opt out reopens from the entry point`() = runTest {
        vm.load()
        advanceUntilIdle()
        store.set_enabled(false)
        advanceUntilIdle()
        store.set_enabled(true)
        advanceUntilIdle()

        vm.claim_and_open()
        advanceUntilIdle()
        assertFalse(vm.state.value.is_open)
        coVerify(exactly = 0) { billing_api.claim_special_offer() }

        vm.reopen()
        assertTrue(vm.state.value.is_open)
    }

    @Test
    fun `failed opt out keeps the offer`() = runTest {
        coEvery { billing_api.set_offer_preferences(any()) } throws java.io.IOException("offline")
        vm.load()
        advanceUntilIdle()

        store.set_enabled(false)
        advanceUntilIdle()

        assertTrue(vm.state.value.available)
    }

    @Test
    fun `load reads the preference of the signed in account`() = runTest {
        server_offers_enabled = false

        vm.load()
        advanceUntilIdle()

        coVerify(exactly = 1) { billing_api.get_offer_preferences() }
        assertFalse(vm.state.value.available)
        assertFalse(vm.state.value.auto_show)
    }

    @Test
    fun `switching to an account with offers on shows its offer`() = runTest {
        store.set_enabled(false)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        assertFalse(vm.state.value.available)

        server_offers_enabled = true
        store.reset()
        accounts.getValue(vm).value = "second_account"
        advanceUntilIdle()

        assertTrue(store.state.value.enabled)
        assertTrue(vm.state.value.available)
        vm.claim_and_open()
        advanceUntilIdle()
        assertTrue(vm.state.value.is_open)
    }

    @Test
    fun `stale opt out does not hide the offer for a new account`() = runTest {
        store.set_enabled(false)
        advanceUntilIdle()
        server_offers_enabled = true
        val fresh_vm = offer_vm()

        fresh_vm.load()
        advanceUntilIdle()

        assertTrue(store.state.value.enabled)
        assertTrue(fresh_vm.state.value.available)
    }

    @Test
    fun `failed preference load trusts the server offer`() = runTest {
        store.set_enabled(false)
        advanceUntilIdle()
        coEvery { billing_api.get_offer_preferences() } throws java.io.IOException("offline")
        val fresh_vm = offer_vm()

        fresh_vm.load()
        advanceUntilIdle()

        assertTrue(fresh_vm.state.value.available)
        fresh_vm.claim_and_open()
        advanceUntilIdle()
        assertTrue(fresh_vm.state.value.is_open)
    }

    @Test
    fun `failed preference load keeps a server opt out`() = runTest {
        coEvery { billing_api.get_offer_preferences() } throws java.io.IOException("offline")
        coEvery { billing_api.get_special_offer() } returns auto_show_offer.copy(available = false, auto_show = false)

        vm.load()
        advanceUntilIdle()

        assertFalse(vm.state.value.available)
        assertFalse(vm.state.value.auto_show)
    }

    @Test
    fun `sign up quiet period hides the offer and blocks the claim`() = runTest {
        var quiet = true
        vm.in_quiet_period = { quiet }

        vm.load()
        advanceUntilIdle()
        vm.claim_and_open()
        advanceUntilIdle()

        assertFalse(vm.state.value.available)
        assertFalse(vm.state.value.auto_show)
        assertFalse(vm.state.value.is_open)
        coVerify(exactly = 0) { billing_api.claim_special_offer() }

        quiet = false
        vm.retry_load()
        advanceUntilIdle()

        assertTrue(vm.state.value.available)
        assertTrue(vm.state.value.auto_show)
    }

    @Test
    fun `retry after a successful load outside the quiet period does not refetch`() = runTest {
        vm.load()
        advanceUntilIdle()

        vm.retry_load()
        advanceUntilIdle()

        coVerify(exactly = 1) { billing_api.get_special_offer() }
    }

    @Test
    fun `fresh cached offer shows while the first load is in flight`() = runTest {
        val now = 1_000_000_000L
        val never = CompletableDeferred<SpecialOfferStatusResponse>()
        coEvery { billing_api.get_special_offer() } coAnswers { never.await() }
        val cached_vm = offer_vm(cache_context(cached_offer(now - 60_000L)))
        cached_vm.now_ms = { now }
        cached_vm.in_quiet_period = { false }

        cached_vm.load()
        advanceUntilIdle()

        assertTrue(cached_vm.state.value.available)
        never.cancel()
    }

    @Test
    fun `expired cached offer is ignored`() = runTest {
        val now = 1_000_000_000L
        val never = CompletableDeferred<SpecialOfferStatusResponse>()
        coEvery { billing_api.get_special_offer() } coAnswers { never.await() }
        val cached_vm = offer_vm(cache_context(cached_offer(now - 13L * 60L * 60L * 1000L)))
        cached_vm.now_ms = { now }
        cached_vm.in_quiet_period = { false }

        cached_vm.load()
        advanceUntilIdle()

        assertFalse(cached_vm.state.value.available)
        never.cancel()
    }

    @Test
    fun `failed first load drops the cached badge`() = runTest {
        val now = 1_000_000_000L
        coEvery { billing_api.get_special_offer() } throws java.io.IOException("offline")
        val cached_vm = offer_vm(cache_context(cached_offer(now - 60_000L)))
        cached_vm.now_ms = { now }
        cached_vm.in_quiet_period = { false }

        cached_vm.load()
        advanceUntilIdle()

        assertFalse(cached_vm.state.value.available)
    }

    @Test
    fun `failed refresh after a good load keeps the offer`() = runTest {
        vm.load()
        advanceUntilIdle()
        assertTrue(vm.state.value.available)
        coEvery { billing_api.get_special_offer() } throws java.io.IOException("offline")

        vm.on_plan_code("free")
        vm.on_plan_code("nova")
        advanceUntilIdle()

        assertTrue(vm.state.value.available)
    }
}
