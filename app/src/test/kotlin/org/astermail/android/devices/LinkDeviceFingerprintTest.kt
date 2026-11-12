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

package org.astermail.android.devices

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.DeviceKeyFingerprint
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkDeviceFingerprintTest {

    private val dispatcher = StandardTestDispatcher()
    private val code = "ABCD-2345"
    private val ed25519_pk = DeviceEnvelope.base64url_encode(ByteArray(32) { 1 })
    private val mlkem_pk = DeviceEnvelope.base64url_encode(ByteArray(1184) { 2 })
    private val x25519_pk = DeviceEnvelope.base64url_encode(ByteArray(32) { 3 })

    private lateinit var api: DeviceCodeApi
    private lateinit var session_key_store: SessionKeyStore

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_passphrase() } returns null
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun pending(
        ed: String = ed25519_pk,
        mlkem: String = mlkem_pk,
        x: String = x25519_pk,
    ) = PendingDevice(
        ed25519_pk = ed,
        mlkem_pk = mlkem,
        x25519_pk = x,
        machine_name = "Laptop",
        device_type = "desktop",
    )

    private suspend fun run_flow(device: PendingDevice): LinkDeviceViewModel {
        coEvery { api.verify_code(any()) } returns device
        val vm = LinkDeviceViewModel(api, session_key_store)
        vm.on_code_change(code)
        vm.verify_code()
        vm.state.first { !it.is_verifying }
        return vm
    }

    private suspend fun confirm_and_settle(vm: LinkDeviceViewModel) {
        vm.confirm_link()
        vm.state.first { !it.is_confirming }
    }

    @Test
    fun `confirm step shows the shared fingerprint of the device keys`() = runTest(dispatcher) {
        val vm = run_flow(pending())

        assertEquals(LinkDeviceStep.CONFIRM, vm.state.value.step)
        assertEquals("9B16 AF79 0A6A E2F2 55D3", vm.state.value.fingerprint)
        assertEquals("", vm.state.value.fingerprint_input)
        assertFalse(vm.state.value.fingerprint_matches)
    }

    @Test
    fun `linking is refused until the fingerprint is typed`() = runTest(dispatcher) {
        val vm = run_flow(pending())

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.FINGERPRINT_MISMATCH, vm.state.value.error)
        verify(exactly = 0) { session_key_store.get_passphrase() }
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `a mistyped fingerprint never seals`() = runTest(dispatcher) {
        val vm = run_flow(pending())
        vm.on_fingerprint_change("9B16 AF79 0A6A E2F2 55D4")

        assertTrue(vm.state.value.fingerprint_rejected)
        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.FINGERPRINT_MISMATCH, vm.state.value.error)
        verify(exactly = 0) { session_key_store.get_passphrase() }
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `a partial fingerprint is neither accepted nor rejected`() = runTest(dispatcher) {
        val vm = run_flow(pending())
        vm.on_fingerprint_change("9B16 AF79")

        assertFalse(vm.state.value.fingerprint_matches)
        assertFalse(vm.state.value.fingerprint_rejected)
        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.FINGERPRINT_MISMATCH, vm.state.value.error)
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `a matching fingerprint typed in lower case without spaces proceeds to sealing`() = runTest(dispatcher) {
        val vm = run_flow(pending())
        vm.on_fingerprint_change("9b16af790a6ae2f255d3")

        assertEquals("9B16 AF79 0A6A E2F2 55D3", vm.state.value.fingerprint_input)
        assertTrue(vm.state.value.fingerprint_matches)
        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.SESSION_EXPIRED, vm.state.value.error)
        verify(exactly = 1) { session_key_store.get_passphrase() }
    }

    @Test
    fun `a matching fingerprint seals and confirms the link`() = runTest(dispatcher) {
        every { session_key_store.get_passphrase() } answers { "vault-passphrase".toByteArray() }
        coEvery { api.confirm_code(any(), any()) } returns
            DeviceCodeConfirmResponse(device_id = "dev_1", machine_name = "Laptop")
        val real_mlkem = RatchetCrypto.ml_kem_768_generate_keypair().public_key
        val vm = run_flow(pending(mlkem = DeviceEnvelope.base64url_encode(real_mlkem)))
        vm.on_fingerprint_change(vm.state.value.fingerprint)

        confirm_and_settle(vm)

        assertEquals(LinkDeviceStep.SUCCESS, vm.state.value.step)
        assertNull(vm.state.value.error)
        coVerify(exactly = 1) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `fingerprint input keeps only hex digits in groups of four`() {
        assertEquals("ABCD EF01 2345 6789 ABCD", DeviceKeyFingerprint.format_input("ab-cd ef01 2345 6789 abcd ffff"))
        assertEquals("ABCD EF01 2345 6789 ABCD", DeviceKeyFingerprint.normalize("abcd-ef01-2345-6789-abcd"))
        assertNull(DeviceKeyFingerprint.normalize("ABCD EF01 2345 6789 ABCG"))
        assertNull(DeviceKeyFingerprint.normalize("ABCD EF01 2345 6789"))
    }

    @Test
    fun `the fingerprint shown on the device proceeds to sealing`() = runTest(dispatcher) {
        val vm = run_flow(pending())
        vm.on_fingerprint_change("9B16 AF79 0A6A E2F2 55D3")

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.SESSION_EXPIRED, vm.state.value.error)
        verify(exactly = 1) { session_key_store.get_passphrase() }
    }

    @Test
    fun `wrong size post quantum key never reaches the confirm step`() = runTest(dispatcher) {
        val vm = run_flow(pending(mlkem = DeviceEnvelope.base64url_encode(ByteArray(1183) { 2 })))

        assertEquals(LinkDeviceStep.INPUT, vm.state.value.step)
        assertEquals(LinkDeviceError.INVALID_DEVICE_KEYS, vm.state.value.error)
    }

    @Test
    fun `wrong size classical key never reaches the confirm step`() = runTest(dispatcher) {
        val vm = run_flow(pending(x = DeviceEnvelope.base64url_encode(ByteArray(16))))

        assertEquals(LinkDeviceStep.INPUT, vm.state.value.step)
        assertEquals(LinkDeviceError.INVALID_DEVICE_KEYS, vm.state.value.error)
    }

    @Test
    fun `undecodable keys never reach the confirm step`() = runTest(dispatcher) {
        val vm = run_flow(pending(ed = "not*base64"))

        assertEquals(LinkDeviceStep.INPUT, vm.state.value.step)
        assertEquals(LinkDeviceError.INVALID_DEVICE_KEYS, vm.state.value.error)
        verify(exactly = 0) { session_key_store.get_passphrase() }
    }

    @Test
    fun `cancel clears the fingerprint and confirmation`() = runTest(dispatcher) {
        val vm = run_flow(pending())
        vm.on_fingerprint_change("9B16 AF79 0A6A E2F2 55D3")

        vm.cancel_confirm()

        assertEquals("", vm.state.value.fingerprint)
        assertEquals("", vm.state.value.fingerprint_input)
        assertEquals(LinkDeviceStep.INPUT, vm.state.value.step)
    }
}
