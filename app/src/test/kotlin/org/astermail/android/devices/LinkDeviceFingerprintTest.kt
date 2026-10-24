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
import org.astermail.android.api.devices.PendingDevice
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertFalse(vm.state.value.codes_match_confirmed)
    }

    @Test
    fun `linking is refused until the codes are confirmed to match`() = runTest(dispatcher) {
        val vm = run_flow(pending())

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.CODES_NOT_CONFIRMED, vm.state.value.error)
        verify(exactly = 0) { session_key_store.get_passphrase() }
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `confirmed codes proceed to sealing`() = runTest(dispatcher) {
        val vm = run_flow(pending())
        vm.set_codes_match_confirmed(true)

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
        vm.set_codes_match_confirmed(true)

        vm.cancel_confirm()

        assertEquals("", vm.state.value.fingerprint)
        assertFalse(vm.state.value.codes_match_confirmed)
        assertEquals(LinkDeviceStep.INPUT, vm.state.value.step)
    }
}
