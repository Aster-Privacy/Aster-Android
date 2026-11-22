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
import io.mockk.every
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
import org.astermail.android.api.ApiError
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.api.auth.WebAuthnAssertionOptions
import org.astermail.android.guard.guard_passkey_failure
import org.astermail.android.guard.guard_passkey_sheet
import org.astermail.android.guard.guard_hold_kind
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardRotateRescueViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user_id = UUID.fromString("0b6e2c1a-4f3d-4e5a-9b8c-7d6e5f4a3b2c")
    private val nonce = ByteArray(32) { 5 }
    private val rescue = "BCDFGHJKMNPQRTVWXY2345678"
    private lateinit var api: GuardApi
    private lateinit var deps: guard_setup_deps
    private val captured = mutableListOf<ByteArray>()
    private val signer = guard_signer { message ->
        captured += message
        guard_sign_result.signed(byteArrayOf(9, 8, 7))
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
        deps = mockk(relaxed = true)
        every { deps.rescue_code() } returns rescue
        coEvery { deps.password_hash(any()) } returns "hash"
        coEvery { api.challenge() } returns GuardChallenge(nonce = DeviceEnvelope.base64url_encode(nonce))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun vm() = GuardRotateRescueViewModel(api, mockk(relaxed = true), guard_current_user { user_id }, deps)

    private fun GuardRotateRescueViewModel.fill(password: String = "pw", confirm: String = "45678") {
        on_confirm_input(confirm)
        on_password(password)
    }

    @Test
    fun `submit signs the rotate action and sends the code with a password proof`() = runTest(dispatcher) {
        val action = slot<SignedAction>()
        coEvery { api.rotate_rescue(any(), capture(action)) } returns Unit
        val vm = vm()
        vm.fill()

        vm.submit(signer)
        advanceUntilIdle()

        assertEquals(12, GuardActionCode.ROTATE_RESCUE.wire)
        assertEquals(1, captured.size)
        assertArrayEquals(
            GuardDigests.action_message(GuardActionCode.ROTATE_RESCUE, user_id, nonce, GuardDigests.rescue_rotate_subject(rescue)),
            captured[0],
        )
        assertEquals(StepUpProof.Password("hash", null), action.captured.proof)
        coVerify { api.rotate_rescue(rescue, any()) }
        assertEquals(guard_rotate_result.applied, vm.state.value.result)
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun `a wrong confirmation group never reaches the server`() = runTest(dispatcher) {
        val vm = vm()
        vm.fill(confirm = "AAAAA")

        vm.submit(signer)
        advanceUntilIdle()

        assertEquals(guard_rotate_notice.wrong_group, vm.state.value.notice)
        assertNull(vm.state.value.result)
        coVerify(exactly = 0) { api.rotate_rescue(any(), any()) }
        assertTrue(captured.isEmpty())
    }

    @Test
    fun `a held response shows the hold and keeps no secrets`() = runTest(dispatcher) {
        coEvery { api.rotate_rescue(any(), any()) } throws
            ApiError.GuardHeld("c9", "rescue_code_rotation", "2026-11-22T10:00:00Z")
        val vm = vm()
        vm.fill()

        vm.submit(signer)
        advanceUntilIdle()

        val result = vm.state.value.result
        assertTrue(result is guard_rotate_result.held)
        result as guard_rotate_result.held
        assertEquals("c9", result.notice.id)
        assertEquals(guard_hold_kind.rescue_code_rotation, result.notice.kind)
        assertEquals("", vm.state.value.password)
        assertEquals("", vm.state.value.confirm_input)
    }

    @Test
    fun `a rejected password returns to the form with a notice`() = runTest(dispatcher) {
        coEvery { api.rotate_rescue(any(), any()) } throws ApiError.InvalidCredentials
        val vm = vm()
        vm.fill()

        vm.submit(signer)
        advanceUntilIdle()

        assertEquals(guard_rotate_notice.wrong_password, vm.state.value.notice)
        assertNull(vm.state.value.result)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun `a canceled prompt sends nothing`() = runTest(dispatcher) {
        val vm = vm()
        vm.fill()

        vm.submit(guard_signer { guard_sign_result.canceled })
        advanceUntilIdle()

        assertEquals(guard_rotate_notice.canceled, vm.state.value.notice)
        coVerify(exactly = 0) { api.rotate_rescue(any(), any()) }
    }

    @Test
    fun `submit stays disabled until the group and password are filled`() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.state.value.can_submit)
        vm.on_confirm_input("45678")
        assertFalse(vm.state.value.can_submit)
        vm.on_password("pw")
        assertTrue(vm.state.value.can_submit)
    }

    private val passkey_sheet = guard_passkey_sheet {
        """{"id":"AQID","rawId":"AQID","type":"public-key","response":{"authenticatorData":"YXV0aA","clientDataJSON":"Y2xpZW50","signature":"c2ln"}}"""
    }

    private fun stub_passkey_options() {
        coEvery { api.step_up_passkey_options() } returns
            WebAuthnAssertionOptions(challenge = "Y2hhbGxlbmdl", challenge_token = "token-1", rpId = "app.astermail.org")
    }

    @Test
    fun `passkey rotates without a password`() = runTest(dispatcher) {
        stub_passkey_options()
        val action = slot<SignedAction>()
        coEvery { api.rotate_rescue(any(), capture(action)) } returns Unit
        val vm = vm()
        vm.on_confirm_input("45678")

        assertTrue(vm.state.value.can_use_passkey)
        assertFalse(vm.state.value.can_submit)
        vm.submit_passkey(signer, passkey_sheet)
        advanceUntilIdle()

        val proof = action.captured.proof
        assertTrue(proof is StepUpProof.Passkey)
        assertEquals("token-1", (proof as StepUpProof.Passkey).assertion.challenge_token)
        coVerify(exactly = 0) { deps.password_hash(any()) }
        assertEquals(guard_rotate_result.applied, vm.state.value.result)
    }

    @Test
    fun `a passkey from another account shows the wrong account error`() = runTest(dispatcher) {
        stub_passkey_options()
        coEvery { api.rotate_rescue(any(), any()) } throws ApiError.ValidationError(listOf("Unknown credential"))
        val vm = vm()
        vm.on_confirm_input("45678")

        vm.submit_passkey(signer, passkey_sheet)
        advanceUntilIdle()

        assertEquals(guard_passkey_failure.wrong_account, vm.state.value.passkey_failure)
        assertNull(vm.state.value.notice)
        assertNull(vm.state.value.result)
        assertFalse(vm.state.value.working)
    }

    @Test
    fun `switching accounts during the passkey sheet stops the rotation`() = runTest(dispatcher) {
        stub_passkey_options()
        var current: UUID? = user_id
        val vm = GuardRotateRescueViewModel(api, mockk(relaxed = true), guard_current_user { current }, deps)
        vm.on_confirm_input("45678")

        vm.submit_passkey(signer) {
            current = UUID.randomUUID()
            """{"id":"AQID","rawId":"AQID","response":{"authenticatorData":"YXV0aA","clientDataJSON":"Y2xpZW50","signature":"c2ln"}}"""
        }
        advanceUntilIdle()

        assertEquals(guard_passkey_failure.account_changed, vm.state.value.passkey_failure)
        coVerify(exactly = 0) { api.rotate_rescue(any(), any()) }
        assertTrue(captured.isEmpty())
    }
}
