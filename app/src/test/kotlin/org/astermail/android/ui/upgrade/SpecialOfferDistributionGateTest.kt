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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.BuildConfig
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.auth.AuthRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpecialOfferDistributionGateTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var billing_api: BillingApi
    private val account = MutableStateFlow<String?>(null)

    private fun cached_offer_context(): Context {
        val cache = mapOf<String, Any>(
            "account.available" to true,
            "account.percent_off" to 50,
            "account.duration_months" to 12,
            "account.plan_code" to "nova",
            "account.cached_at" to System.currentTimeMillis(),
        )
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { prefs.getBoolean(any(), any()) } answers { cache[firstArg()] as? Boolean ?: secondArg() }
        every { prefs.getInt(any(), any()) } answers { cache[firstArg()] as? Int ?: secondArg() }
        every { prefs.getLong(any(), any()) } answers { cache[firstArg()] as? Long ?: secondArg() }
        every { prefs.getString(any(), any()) } answers { cache[firstArg()] as? String ?: secondArg() }
        val context = mockk<Context>()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return context
    }

    private fun offer_vm(): SpecialOfferViewModel {
        val auth = mockk<AuthRepository>(relaxed = true)
        every { auth.active_account_id } returns account
        return SpecialOfferViewModel(billing_api, auth, OfferPreferencesStore(billing_api), cached_offer_context())
            .also { it.in_quiet_period = { false } }
    }

    @Before
    fun setup() {
        assumeFalse(BuildConfig.SPECIAL_OFFER)
        Dispatchers.setMain(dispatcher)
        billing_api = mockk(relaxed = true)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `builds without offers never fetch or show one`() = runTest {
        val vm = offer_vm()
        account.value = "account"
        advanceUntilIdle()

        vm.claim_and_open()
        advanceUntilIdle()

        assertFalse(vm.state.value.available)
        assertFalse(vm.state.value.auto_show)
        assertFalse(vm.state.value.is_open)
        coVerify(exactly = 0) { billing_api.get_special_offer() }
        coVerify(exactly = 0) { billing_api.claim_special_offer() }
    }
}
