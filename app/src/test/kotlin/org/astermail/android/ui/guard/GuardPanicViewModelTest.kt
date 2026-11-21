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
package org.astermail.android.ui.guard

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.GuardPanicResult
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.guard_event
import org.astermail.android.guard.guard_launch_target
import org.astermail.android.guard.guard_launch_target_for
import org.astermail.android.guard.guard_notification_spec_for
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardPanicViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user_id = UUID.fromString("0b6e2c1a-4f3d-4e5a-9b8c-7d6e5f4a3b2c")
    private val nonce = ByteArray(32) { 5 }
    private lateinit var api: GuardApi
    private val captured = mutableListOf<ByteArray>()
    private val signer = guard_signer { message ->
        captured += message
        guard_sign_result.signed(byteArrayOf(9, 8, 7))
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
        coEvery { api.challenge() } returns GuardChallenge(nonce = DeviceEnvelope.base64url_encode(nonce))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun vm() = GuardPanicViewModel(api, mockk(relaxed = true), guard_current_user { user_id })

    @Test
    fun `confirm signs code 7 over 32 zero bytes`() = runTest(dispatcher) {
        val action = slot<SignedAction>()
        coEvery { api.panic(capture(action)) } returns GuardPanicResult(signed_out_sessions = 2)
        val vm = vm()

        vm.confirm(signer)
        advanceUntilIdle()

        assertEquals(7, GuardActionCode.PANIC.wire)
        assertEquals(1, captured.size)
        assertArrayEquals(GuardDigests.action_message(GuardActionCode.PANIC, user_id, nonce, ByteArray(32)), captured[0])
        assertArrayEquals(ByteArray(32), captured[0].copyOfRange(captured[0].size - 32, captured[0].size))
        assertEquals(DeviceEnvelope.base64url_encode(nonce), action.captured.nonce)
        assertEquals(DeviceEnvelope.base64url_encode(byteArrayOf(9, 8, 7)), action.captured.signature)
    }

    @Test
    fun `done with a password reset window offers change password`() = runTest(dispatcher) {
        coEvery { api.panic(any()) } returns GuardPanicResult(
            signed_out_sessions = 3,
            removed_devices = 1,
            cancelled_changes = 2,
            can_reset_password_now = true,
        )
        val vm = vm()

        vm.confirm(signer)
        advanceUntilIdle()

        val s = vm.state.value
        assertTrue(s is guard_panic_state.done)
        s as guard_panic_state.done
        assertTrue(s.offers_password_change)
        assertEquals(3, s.result.signed_out_sessions)
    }

    @Test
    fun `done without a password reset window does not offer it`() = runTest(dispatcher) {
        coEvery { api.panic(any()) } returns GuardPanicResult(can_reset_password_now = false)
        val vm = vm()

        vm.confirm(signer)
        advanceUntilIdle()

        assertFalse((vm.state.value as guard_panic_state.done).offers_password_change)
    }

    @Test
    fun `a canceled prompt makes no call`() = runTest(dispatcher) {
        val vm = vm()

        vm.confirm(guard_signer { guard_sign_result.canceled })
        advanceUntilIdle()

        assertTrue(vm.state.value is guard_panic_state.confirm)
        coVerify(exactly = 0) { api.panic(any()) }
    }

    @Test
    fun `a server failure shows failed and can be retried`() = runTest(dispatcher) {
        coEvery { api.panic(any()) } throws IllegalStateException("boom")
        val vm = vm()

        vm.confirm(signer)
        advanceUntilIdle()

        assertTrue(vm.state.value is guard_panic_state.failed)
        vm.retry()
        assertTrue(vm.state.value is guard_panic_state.confirm)
    }

    @Test
    fun `held change notifications carry this was not me and sign in requests do not`() {
        assertTrue(guard_notification_spec_for(guard_event.change_held).panic_action)
        assertTrue(guard_notification_spec_for(guard_event.change_applied).panic_action)
        assertFalse(guard_notification_spec_for(guard_event.sign_in_request).panic_action)
    }

    @Test
    fun `a panic launch opens the secure account screen`() {
        val acct = "7f3c2a10-1111-4222-8333-944455556666"
        val request = "0d1e2f30-aaaa-4bbb-8ccc-000000000001"
        assertEquals(
            guard_launch_target.panic(acct),
            guard_launch_target_for(null, changes = true, settings = false, account_id = acct, panic = true),
        )
        assertEquals(
            guard_launch_target.request(request, acct),
            guard_launch_target_for(request, changes = false, settings = false, account_id = acct, panic = true),
        )
    }
}
