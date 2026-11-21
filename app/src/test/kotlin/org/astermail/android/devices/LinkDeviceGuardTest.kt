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
package org.astermail.android.devices

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.devices.DeviceCodeApi
import org.astermail.android.api.devices.DeviceCodeConfirmResponse
import org.astermail.android.api.devices.PendingDevice
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.guard.GuardLinkApprover
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.ui.guard.guard_current_user
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkDeviceGuardTest {
    private val dispatcher = StandardTestDispatcher()
    private val user_id = UUID.fromString("0b6e2c1a-4f3d-4e5a-9b8c-7d6e5f4a3b2c")
    private val nonce = ByteArray(32) { 7 }
    private val ed = ByteArray(32) { 0x11 }
    private val x = ByteArray(32) { 0x33 }
    private lateinit var mlkem: ByteArray
    private lateinit var api: DeviceCodeApi
    private lateinit var guard_api: GuardApi
    private lateinit var store: SessionKeyStore
    private val captured = mutableListOf<ByteArray>()
    private val signer = guard_signer { message ->
        captured += message
        guard_sign_result.signed(byteArrayOf(1, 2, 3))
    }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        mlkem = RatchetCrypto.ml_kem_768_generate_keypair().public_key
        api = mockk(relaxed = true)
        guard_api = mockk(relaxed = true)
        store = mockk(relaxed = true)
        every { store.get_passphrase() } answers { "vault-passphrase".toByteArray() }
        coEvery { guard_api.challenge() } returns GuardChallenge(nonce = DeviceEnvelope.base64url_encode(nonce))
        coEvery { api.confirm_code(any(), any(), any()) } returns
            DeviceCodeConfirmResponse(device_id = "dev_1", machine_name = "Laptop")
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun digest(): ByteArray = GuardDigests.keys_digest(ed, mlkem, x, GuardDigests.ABSENT_ACTION_PK)

    private fun pending(claimed: ByteArray = digest()) = PendingDevice(
        ed25519_pk = DeviceEnvelope.base64url_encode(ed),
        mlkem_pk = DeviceEnvelope.base64url_encode(mlkem),
        x25519_pk = DeviceEnvelope.base64url_encode(x),
        machine_name = "Laptop",
        device_type = "desktop",
        action_pk = null,
        keys_digest = DeviceEnvelope.base64url_encode(claimed),
    )

    private fun vm_with(switches: Int): LinkDeviceViewModel {
        coEvery { guard_api.status() } returns GuardStatus(switches = switches)
        val approver = GuardLinkApprover(guard_api, mockk(relaxed = true), guard_current_user { user_id })
        return LinkDeviceViewModel(api, store, approver)
    }

    private suspend fun kotlinx.coroutines.test.TestScope.ready(vm: LinkDeviceViewModel, device: PendingDevice) {
        coEvery { api.verify_code(any()) } returns device
        vm.on_code_change("ABCD-2345")
        vm.verify_code()
        vm.state.first { !it.is_verifying }
        vm.on_fingerprint_change(vm.state.value.fingerprint)
    }

    @Test
    fun `with new device approval on the link carries a signed approve over the keys digest`() = runTest(dispatcher) {
        val vm = vm_with(GuardSwitch.NEW_DEVICE_APPROVAL)
        ready(vm, pending())
        val action = slot<SignedAction>()
        coEvery { api.confirm_code(any(), any(), capture(action)) } returns
            DeviceCodeConfirmResponse(device_id = "dev_1", machine_name = "Laptop")

        vm.confirm_link(signer)
        vm.state.first { !it.is_confirming }

        assertEquals(LinkDeviceStep.SUCCESS, vm.state.value.step)
        assertEquals(1, captured.size)
        val subject = GuardDigests.approve_subject(null, digest(), 0)
        assertArrayEquals(subject, captured[0].copyOfRange(captured[0].size - 32, captured[0].size))
        assertArrayEquals(GuardDigests.action_message(GuardActionCode.APPROVE, user_id, nonce, subject), captured[0])
        assertEquals(DeviceEnvelope.base64url_encode(byteArrayOf(1, 2, 3)), action.captured.signature)
        assertEquals(DeviceEnvelope.base64url_encode(nonce), action.captured.nonce)
    }

    @Test
    fun `a keys digest that differs from the local one makes no call`() = runTest(dispatcher) {
        val vm = vm_with(GuardSwitch.NEW_DEVICE_APPROVAL)
        ready(vm, pending(claimed = ByteArray(32) { 9 }))

        vm.confirm_link(signer)
        vm.state.first { !it.is_confirming }

        assertEquals(LinkDeviceError.KEYS_MISMATCH, vm.state.value.error)
        assertEquals(0, captured.size)
        coVerify(exactly = 0) { api.confirm_code(any(), any(), any()) }
    }

    @Test
    fun `a canceled prompt makes no call`() = runTest(dispatcher) {
        val vm = vm_with(GuardSwitch.NEW_DEVICE_APPROVAL)
        ready(vm, pending())

        vm.confirm_link(guard_signer { guard_sign_result.canceled })
        vm.state.first { !it.is_confirming }

        assertEquals(LinkDeviceStep.CONFIRM, vm.state.value.step)
        assertNull(vm.state.value.error)
        coVerify(exactly = 0) { api.confirm_code(any(), any(), any()) }
    }

    @Test
    fun `with guard off the link sends no action`() = runTest(dispatcher) {
        val vm = vm_with(0)
        ready(vm, pending())

        vm.confirm_link(signer)
        vm.state.first { !it.is_confirming }

        assertEquals(LinkDeviceStep.SUCCESS, vm.state.value.step)
        assertEquals(0, captured.size)
        coVerify(exactly = 1) { api.confirm_code(any(), any(), null) }
    }

    @Test
    fun `a link that waits for activation shows when it can be used`() = runTest(dispatcher) {
        val vm = vm_with(GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.APPROVE_MAIL_APPS)
        ready(vm, pending())
        coEvery { api.confirm_code(any(), any(), any()) } returns DeviceCodeConfirmResponse(
            device_id = "dev_1",
            machine_name = "Laptop",
            activates_at = "2026-11-14T12:00:00Z",
            change_id = "c1",
        )

        vm.confirm_link(signer)
        vm.state.first { !it.is_confirming }

        assertEquals(LinkDeviceStep.SUCCESS, vm.state.value.step)
        assertNotNull(vm.state.value.pending_activates_at)
    }

    @Test
    fun `a missing guard device asks for a phone with aster guard`() = runTest(dispatcher) {
        val vm = vm_with(GuardSwitch.NEW_DEVICE_APPROVAL)
        ready(vm, pending())

        vm.confirm_link(null)
        vm.state.first { !it.is_confirming }

        assertEquals(LinkDeviceError.GUARD_DEVICE_REQUIRED, vm.state.value.error)
        coVerify(exactly = 0) { api.confirm_code(any(), any(), any()) }
    }
}
