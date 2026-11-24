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

package org.astermail.android.settings

import android.content.Context
import io.mockk.coEvery
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
import org.astermail.android.api.ApiError
import org.astermail.android.api.totp.TotpApi
import org.astermail.android.api.totp.TotpStatusResponse
import org.astermail.android.auth.AuthRepository
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var totp_api: TotpApi
    private lateinit var auth_repository: AuthRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        totp_api = mockk(relaxed = true)
        auth_repository = mockk(relaxed = true)
        every { auth_repository.is_signed_in } returns MutableStateFlow(true)
        every { auth_repository.session_expired } returns MutableStateFlow(false)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun build() = DeleteAccountViewModel(auth_repository, totp_api, mockk<Context>(relaxed = true))

    @Test
    fun `two factor status survives a transient error on open`() = runTest {
        var calls = 0
        coEvery { totp_api.status() } answers {
            calls++
            if (calls == 1) throw ApiError.ServerError(502)
            TotpStatusResponse(enabled = true)
        }

        val vm = build()
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.totp_enabled)
        assertFalse(state.totp_status_unknown)
        assertFalse(state.totp_status_loading)
    }

    @Test
    fun `two factor status reports a real failure once`() = runTest {
        coEvery { totp_api.status() } throws ApiError.ForbiddenError()

        val vm = build()
        advanceUntilIdle()

        assertTrue(vm.state.value.totp_status_unknown)
        assertFalse(vm.state.value.totp_status_loading)
        coVerify(exactly = 1) { totp_api.status() }
    }
}
