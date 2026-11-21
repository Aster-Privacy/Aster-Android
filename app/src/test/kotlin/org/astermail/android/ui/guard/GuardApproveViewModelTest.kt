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

import androidx.lifecycle.SavedStateHandle
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
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_ENVELOPE_REQUIRED_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_WRONG_NUMBER_CODE
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.GuardNewDevice
import org.astermail.android.api.guard.GuardRequestItem
import org.astermail.android.api.guard.GuardRequestList
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.generate_box_keys
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.InMemoryPrefs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardApproveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val device = UUID.fromString("a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6")
    private val request_id = "6f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0"
    private val nonce = ByteArray(32) { (it + 1).toByte() }
    private val own_keys = generate_box_keys()
    private val new_keys = generate_box_keys()
    private val new_action_pk = ByteArray(33) { 2 }

    private class fake_deps(var pass: ByteArray? = byteArrayOf(1, 2, 3)) : guard_approve_deps {
        val canceled = mutableListOf<String>()
        var sealed_version: Int? = null
        override fun user_id(): UUID = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
        override fun passphrase(): ByteArray? = pass?.copyOf()
        override fun seal(passphrase: ByteArray, keys: guard_new_device_keys, version: Int): ByteArray {
            sealed_version = version
            return byteArrayOf(9, 9)
        }
        override fun cancel_notification(request_id: String, account_id: String) {
            canceled += request_id
        }
        val approvals = mutableListOf<String>()
        override fun record_approval(request_id: String, account_id: String) {
            approvals += request_id
        }
        override fun forget_approval(request_id: String, account_id: String) {
            approvals -= request_id
        }
    }

    private class recording_signer(val result: guard_sign_result) : guard_signer {
        var message: ByteArray? = null
        override suspend fun sign(message: ByteArray): guard_sign_result {
            this.message = message
            return result
        }
    }

    @Before
    fun set_up() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tear_down() {
        Dispatchers.resetMain()
    }

    private fun b64(b: ByteArray) = DeviceEnvelope.base64url_encode(b)

    private fun new_device(digest_override: ByteArray? = null, with_action: Boolean = true): GuardNewDevice {
        val action = if (with_action) new_action_pk else GuardDigests.ABSENT_ACTION_PK
        val digest = digest_override
            ?: GuardDigests.keys_digest(new_keys.ed25519_pk, new_keys.mlkem_pk, new_keys.x25519_pk, action)
        return GuardNewDevice(
            device_type = "desktop",
            name = "Laptop",
            ed25519_pk = b64(new_keys.ed25519_pk),
            mlkem_pk = b64(new_keys.mlkem_pk),
            x25519_pk = b64(new_keys.x25519_pk),
            action_pk = if (with_action) b64(new_action_pk) else null,
            keys_digest = b64(digest),
        )
    }

    private fun item(kind: String = "sign_in", device: GuardNewDevice = new_device()): GuardRequestItem {
        val ctx = """{"device_type":"desktop","browser":"Firefox","os":"Linux","city":"Berlin","country":"Germany","requested_at":"2026-11-15T10:00:00Z"}"""
        val sealed = GuardContextSeal.seal(
            ctx.toByteArray(),
            GuardContextSeal.context_aad(UUID.fromString(request_id), this.device),
            own_keys.x25519_pk,
            own_keys.mlkem_pk,
        )
        return GuardRequestItem(
            id = request_id,
            kind = kind,
            created_at = "2026-11-15T10:00:00Z",
            context = b64(sealed),
            new_device = device,
            envelope_versions = listOf(1, 2, 7),
        )
    }

    private fun store(): GuardDeviceStore = GuardDeviceStore(InMemoryPrefs()).also {
        it.save_enrollment(
            user_id = user,
            device_id = device,
            code_secret = ByteArray(32),
            x25519_sk = own_keys.x25519_sk,
            mlkem_sk = own_keys.mlkem_sk,
            x25519_pk = own_keys.x25519_pk,
            mlkem_pk = own_keys.mlkem_pk,
            ed25519_pk = own_keys.ed25519_pk,
        )
    }

    private fun api(item: GuardRequestItem? = item()): GuardApi = mockk(relaxed = true) {
        coEvery { requests() } returns GuardRequestList(listOfNotNull(item))
        coEvery { challenge() } returns GuardChallenge(b64(nonce))
    }

    private fun vm(api: GuardApi, deps: fake_deps = fake_deps()) =
        GuardApproveViewModel(api, store(), deps, SavedStateHandle(mapOf(GuardApproveViewModel.ARG_ID to request_id)))

    private val ok = recording_signer(guard_sign_result.signed(byteArrayOf(0x30, 0x01)))

    @Test
    fun loads_context_into_ready() = runTest(dispatcher) {
        val model = vm(api())
        advanceUntilIdle()
        val state = model.state.value as guard_approve_state.ready
        assertEquals("Linux · Firefox", state.view?.device_summary())
        assertEquals("Berlin, Germany", state.view?.place())
        assertEquals("", state.number_input)
    }

    @Test
    fun one_digit_makes_no_call() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        model.on_number("4")
        model.approve(ok)
        advanceUntilIdle()
        assertNull(ok.message)
        coVerify(exactly = 0) { api.approve(any(), any(), any(), any()) }
    }

    @Test
    fun two_digits_approve_with_signed_subject() = runTest(dispatcher) {
        val api = api()
        val deps = fake_deps()
        val model = vm(api, deps)
        advanceUntilIdle()
        model.on_number("42")
        model.approve(ok)
        advanceUntilIdle()
        val action = slot<SignedAction>()
        coVerify { api.approve(request_id, 42, b64(byteArrayOf(9, 9)), capture(action)) }
        val message = ok.message!!
        assertEquals(2, message[21].toInt())
        val digest = GuardDigests.keys_digest(new_keys.ed25519_pk, new_keys.mlkem_pk, new_keys.x25519_pk, new_action_pk)
        val subject = GuardDigests.approve_subject(UUID.fromString(request_id), digest, 42)
        assertArrayEquals(GuardDigests.action_message(org.astermail.android.crypto.guard.GuardActionCode.APPROVE, user, nonce, subject), message)
        assertEquals(2, deps.sealed_version)
        assertEquals(guard_approve_state.approved, model.state.value)
        assertEquals(listOf(request_id), deps.canceled)
        assertEquals(listOf(request_id), deps.approvals)
    }

    @Test
    fun canceled_prompt_makes_no_call() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        model.on_number("42")
        model.approve(recording_signer(guard_sign_result.canceled))
        advanceUntilIdle()
        coVerify(exactly = 0) { api.approve(any(), any(), any(), any()) }
        assertTrue(model.state.value is guard_approve_state.ready)
    }

    @Test
    fun wrong_number_ends_the_request() = runTest(dispatcher) {
        val api = api()
        coEvery { api.approve(any(), any(), any(), any()) } throws ApiError.ForbiddenError("no", GUARD_WRONG_NUMBER_CODE)
        val deps = fake_deps()
        val model = vm(api, deps)
        advanceUntilIdle()
        model.on_number("42")
        model.approve(ok)
        advanceUntilIdle()
        assertTrue(deps.approvals.isEmpty())
        assertEquals(guard_approve_state.mismatched, model.state.value)
        assertEquals(listOf(request_id), deps.canceled)
        model.on_number("43")
        model.approve(ok)
        advanceUntilIdle()
        coVerify(exactly = 1) { api.approve(any(), any(), any(), any()) }
        assertEquals(guard_approve_state.mismatched, model.state.value)
    }

    @Test
    fun gone_cancels_notification() = runTest(dispatcher) {
        val api = api()
        coEvery { api.approve(any(), any(), any(), any()) } throws ApiError.Conflict("gone", GUARD_REQUEST_GONE_CODE)
        val deps = fake_deps()
        val model = vm(api, deps)
        advanceUntilIdle()
        model.on_number("42")
        model.approve(ok)
        advanceUntilIdle()
        assertEquals(guard_approve_state.gone, model.state.value)
        assertEquals(listOf(request_id), deps.canceled)
    }

    @Test
    fun missing_request_is_gone() = runTest(dispatcher) {
        val deps = fake_deps()
        val model = vm(api(item = null), deps)
        advanceUntilIdle()
        assertEquals(guard_approve_state.gone, model.state.value)
        assertEquals(listOf(request_id), deps.canceled)
    }

    @Test
    fun deny_signs_id_subject_with_code_3() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        model.deny(ok)
        advanceUntilIdle()
        coVerify { api.deny(request_id, any()) }
        val message = ok.message!!
        assertEquals(3, message[21].toInt())
        val expected = GuardDigests.action_message(
            org.astermail.android.crypto.guard.GuardActionCode.DENY,
            user,
            nonce,
            GuardDigests.id_subject(UUID.fromString(request_id)),
        )
        assertArrayEquals(expected, message)
        assertEquals(guard_approve_state.denied, model.state.value)
    }

    @Test
    fun locked_vault_asks_to_unlock_and_makes_no_call() = runTest(dispatcher) {
        val api = api()
        val deps = fake_deps(pass = null)
        val model = vm(api, deps)
        advanceUntilIdle()
        assertEquals(guard_approve_state.unlock_first, model.state.value)
        model.approve(ok)
        advanceUntilIdle()
        coVerify(exactly = 0) { api.approve(any(), any(), any(), any()) }
        deps.pass = byteArrayOf(1)
        model.retry_unlocked()
        assertTrue(model.state.value is guard_approve_state.ready)
    }

    @Test
    fun envelope_required_maps_to_unlock_first() = runTest(dispatcher) {
        val api = api()
        coEvery { api.approve(any(), any(), any(), any()) } throws
            ApiError.ValidationError(listOf("x"), GUARD_ENVELOPE_REQUIRED_CODE)
        val model = vm(api)
        advanceUntilIdle()
        model.on_number("42")
        model.approve(ok)
        advanceUntilIdle()
        assertEquals(guard_approve_state.unlock_first, model.state.value)
    }

    @Test
    fun mismatched_keys_digest_is_gone() = runTest(dispatcher) {
        val api = api(item(device = new_device(digest_override = ByteArray(32) { 5 })))
        val model = vm(api)
        advanceUntilIdle()
        assertEquals(guard_approve_state.gone, model.state.value)
    }

    @Test
    fun absent_action_key_uses_zero_placeholder() = runTest(dispatcher) {
        val api = api(item(device = new_device(with_action = false)))
        val model = vm(api)
        advanceUntilIdle()
        assertTrue(model.state.value is guard_approve_state.ready)
    }

    @Test
    fun join_request_approves_the_same_way() = runTest(dispatcher) {
        val api = api(item(kind = "join"))
        val model = vm(api)
        advanceUntilIdle()
        val state = model.state.value as guard_approve_state.ready
        assertTrue(state.is_join)
        model.on_number("17")
        model.approve(ok)
        advanceUntilIdle()
        coVerify { api.approve(request_id, 17, any(), any()) }
        assertNotNull(ok.message)
    }

    @Test
    fun number_parsing() {
        assertNull(guard_parse_number("4"))
        assertNull(guard_parse_number("09"))
        assertNull(guard_parse_number("4a"))
        assertNull(guard_parse_number("123"))
        assertEquals(42, guard_parse_number("42"))
        assertEquals(2, guard_supported_envelope_version(listOf(1, 2, 7)))
        assertEquals(1, guard_supported_envelope_version(listOf(1)))
        assertNull(guard_supported_envelope_version(listOf(5)))
    }
}
