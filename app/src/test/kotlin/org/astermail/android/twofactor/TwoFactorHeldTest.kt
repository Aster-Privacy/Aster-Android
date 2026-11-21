//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.twofactor

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.totp.TotpApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.guard.guard_hold_kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwoFactorHeldTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a held turn off keeps two step verification on and shows the hold`() = runTest(dispatcher) {
        val totp_api = mockk<TotpApi>(relaxed = true)
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.derive_password_hash_b64(any()) } returns "hash"
        coEvery { totp_api.disable(any()) } throws
            ApiError.GuardHeld("c1", "second_factor_off", "2026-11-14T11:00:00Z")
        val vm = TwoFactorViewModel(totp_api, mockk(relaxed = true), auth, mockk(relaxed = true))
        advanceUntilIdle()

        vm.start_disable()
        vm.update_password("pw")
        vm.update_code("123456")
        vm.confirm_disable()
        vm.state.first { it.held != null || it.error != null }
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(guard_hold_kind.second_factor_off, s.held?.kind)
        assertNull(s.error)
        assertFalse(s.is_busy)
        assertEquals(TwoFactorMode.Disable, s.mode)
    }
}
