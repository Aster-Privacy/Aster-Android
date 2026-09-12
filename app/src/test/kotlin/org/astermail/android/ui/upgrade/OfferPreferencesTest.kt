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
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.api.billing.OfferPreferences
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OfferPreferencesTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var billing_api: BillingApi
    private lateinit var store: OfferPreferencesStore

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        billing_api = mockk(relaxed = true)
        store = OfferPreferencesStore(billing_api)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load applies the server preference`() = runTest {
        coEvery { billing_api.get_offer_preferences() } returns OfferPreferences(in_app_offers_enabled = false)

        assertTrue(store.load())

        assertFalse(store.state.value.enabled)
        assertTrue(store.state.value.loaded)
    }

    @Test
    fun `failed load leaves the switch hidden`() = runTest {
        coEvery { billing_api.get_offer_preferences() } throws java.io.IOException("offline")

        assertFalse(store.load())

        assertFalse(store.state.value.loaded)
        assertTrue(store.state.value.enabled)
    }

    @Test
    fun `turning offers off updates the state before the request finishes`() = runTest {
        val response = CompletableDeferred<OfferPreferences>()
        coEvery { billing_api.set_offer_preferences(any()) } coAnswers { response.await() }

        val result = async { store.set_enabled(false) }
        runCurrent()
        assertFalse(store.state.value.enabled)

        response.complete(OfferPreferences(in_app_offers_enabled = false))
        assertTrue(result.await())
        assertFalse(store.state.value.enabled)
    }

    @Test
    fun `failed save rolls the switch back`() = runTest {
        coEvery { billing_api.set_offer_preferences(any()) } throws java.io.IOException("offline")

        assertFalse(store.set_enabled(false))

        assertTrue(store.state.value.enabled)
    }

    @Test
    fun `stale load does not overwrite a newer optimistic change`() = runTest {
        val load_response = CompletableDeferred<OfferPreferences>()
        coEvery { billing_api.get_offer_preferences() } coAnswers { load_response.await() }
        coEvery { billing_api.set_offer_preferences(any()) } returns OfferPreferences(in_app_offers_enabled = false)

        val load = async { store.load() }
        runCurrent()
        store.set_enabled(false)
        load_response.complete(OfferPreferences(in_app_offers_enabled = true))
        load.await()

        assertFalse(store.state.value.enabled)
    }

    @Test
    fun `view model reports a failed save and rolls back`() = runTest {
        coEvery { billing_api.set_offer_preferences(any()) } throws java.io.IOException("offline")
        val vm = OfferPreferencesViewModel(store)

        vm.set_enabled(false)
        advanceUntilIdle()

        assertTrue(vm.state.value.save_failed)
        assertTrue(vm.state.value.enabled)

        vm.clear_save_failed()
        advanceUntilIdle()
        assertFalse(vm.state.value.save_failed)
    }

    @Test
    fun `view model shows the switch once preferences load`() = runTest {
        coEvery { billing_api.get_offer_preferences() } returns OfferPreferences(in_app_offers_enabled = true)
        val vm = OfferPreferencesViewModel(store)
        assertFalse(vm.state.value.available)

        vm.load()
        advanceUntilIdle()

        assertTrue(vm.state.value.available)
        assertTrue(vm.state.value.enabled)
    }
}
