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
import org.astermail.android.api.billing.OfferPreferences
import org.astermail.android.api.billing.SpecialOfferClaimResponse
import org.astermail.android.api.billing.SpecialOfferStatusResponse
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
        coEvery { billing_api.set_offer_preferences(any()) } coAnswers { firstArg<OfferPreferences>() }
        store = OfferPreferencesStore(billing_api)
        vm = SpecialOfferViewModel(billing_api, store)
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
    fun `failed opt out keeps the offer`() = runTest {
        coEvery { billing_api.set_offer_preferences(any()) } throws java.io.IOException("offline")
        vm.load()
        advanceUntilIdle()

        store.set_enabled(false)
        advanceUntilIdle()

        assertTrue(vm.state.value.available)
    }
}
