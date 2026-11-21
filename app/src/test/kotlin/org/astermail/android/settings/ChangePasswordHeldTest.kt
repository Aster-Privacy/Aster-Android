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
package org.astermail.android.settings

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.auth.AuthRepository
import org.astermail.android.guard.guard_hold_kind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordHeldTest {
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
    fun `a held password change keeps the form and shows the hold`() = runTest(dispatcher) {
        val auth = mockk<AuthRepository>(relaxed = true)
        coEvery { auth.change_password(any(), any()) } returns
            Result.failure(ApiError.GuardHeld("c1", "password_change", "2026-11-14T11:00:00Z"))
        val vm = ChangePasswordViewModel(auth, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
        vm.set_current("old-password-123")
        vm.set_new("new-password-456")
        vm.set_confirm("new-password-456")

        vm.submit()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(guard_hold_kind.password_change, s.held?.kind)
        assertNull(s.error)
        assertFalse(s.success)
        assertFalse(s.is_submitting)
        assertEquals("new-password-456", s.new_password)
    }
}
