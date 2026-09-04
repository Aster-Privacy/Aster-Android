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
import org.astermail.android.crypto.DeviceLinkBinding
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LinkDeviceBindingTest {

    private val dispatcher = StandardTestDispatcher()
    private val code = "ABCD-2345"
    private val ed25519_pk = DeviceEnvelope.base64url_encode(ByteArray(32) { 1 })
    private val mlkem_pk = DeviceEnvelope.base64url_encode(ByteArray(1184) { 2 })
    private val x25519_pk = DeviceEnvelope.base64url_encode(ByteArray(32) { 3 })
    private val attacker_mlkem_pk = DeviceEnvelope.base64url_encode(ByteArray(1184) { 9 })

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

    private fun pending(mlkem: String, tag: String) = PendingDevice(
        ed25519_pk = ed25519_pk,
        mlkem_pk = mlkem,
        x25519_pk = x25519_pk,
        machine_name = "Laptop",
        device_type = "desktop",
        binding_tag = tag,
    )

    private fun honest_tag(): String =
        DeviceLinkBinding.compute_tag(code, ed25519_pk, mlkem_pk, x25519_pk)

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
    fun `substituted bundle keys block the transfer`() = runTest(dispatcher) {
        val vm = run_flow(pending(attacker_mlkem_pk, honest_tag()))
        assertEquals(LinkDeviceStep.CONFIRM, vm.state.value.step)

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.BINDING_MISMATCH, vm.state.value.error)
        assertEquals(LinkDeviceStep.CONFIRM, vm.state.value.step)
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `tag from a different code blocks the transfer`() = runTest(dispatcher) {
        val other_tag = DeviceLinkBinding.compute_tag("ZZZZ9999", ed25519_pk, mlkem_pk, x25519_pk)
        val vm = run_flow(pending(mlkem_pk, other_tag))

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.BINDING_MISMATCH, vm.state.value.error)
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `bundle bound to the code is accepted`() = runTest(dispatcher) {
        val vm = run_flow(pending(mlkem_pk, honest_tag()))

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.SESSION_EXPIRED, vm.state.value.error)
        coVerify(exactly = 0) { api.confirm_code(any(), any()) }
    }

    @Test
    fun `bundle without a tag keeps the legacy path`() = runTest(dispatcher) {
        val vm = run_flow(pending(mlkem_pk, ""))

        confirm_and_settle(vm)

        assertEquals(LinkDeviceError.SESSION_EXPIRED, vm.state.value.error)
    }

    @Test
    fun `verification does not read the passphrase before the bundle is checked`() = runTest(dispatcher) {
        val vm = run_flow(pending(attacker_mlkem_pk, honest_tag()))

        confirm_and_settle(vm)

        io.mockk.verify(exactly = 0) { session_key_store.get_passphrase() }
    }
}
