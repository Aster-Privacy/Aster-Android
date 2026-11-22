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
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
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
import org.astermail.android.api.guard.GuardHoldScope
import org.astermail.android.api.guard.GuardJoinComplete
import org.astermail.android.api.guard.GuardJoinRequest
import org.astermail.android.api.guard.GuardJoinStarted
import org.astermail.android.api.guard.GuardJoinStatus
import org.astermail.android.api.guard.GuardSetupNonce
import org.astermail.android.api.guard.GuardSetupRequest
import org.astermail.android.api.guard.GuardSetupResponse
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.GuardThisDevice
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardCode
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.generate_box_keys
import org.astermail.android.guard.guard_box_keys
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.InMemoryPrefs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val device = UUID.fromString("a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6")
    private val join_id = UUID.fromString("11111111-2222-4333-8444-555555555555")
    private val nonce = ByteArray(32) { (it + 3).toByte() }
    private val keys: guard_box_keys = generate_box_keys()
    private val pair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    private val code_secret = ByteArray(20) { (it + 1).toByte() }
    private val rescue = "BCDFGHJKMNPQRTVWXY2345678"

    private inner class fake_deps : guard_setup_deps {
        val created = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        private var counter = 0
        override fun user_id(): UUID = user
        override fun new_box_keys(): guard_box_keys = keys
        override fun new_action_alias(): String = "setup_alias_${++counter}"
        override fun create_action_key(alias: String): ByteArray {
            created += alias
            return uncompressed(pair.public as ECPublicKey)
        }
        override fun delete_action_key(alias: String) {
            deleted += alias
        }
        override fun device_name(): String = "Pixel"
        override fun pick_number(): Int = 42
        override fun new_request_id(): UUID = join_id
        override fun rescue_code(): String = rescue
        override suspend fun password_hash(password: String): String? = "hash-of-" + password.length
    }

    private inner class soft_signer : guard_signer {
        var messages = mutableListOf<ByteArray>()
        override suspend fun sign(message: ByteArray): guard_sign_result {
            messages += message
            val s = Signature.getInstance("SHA256withECDSA")
            s.initSign(pair.private)
            s.update(message)
            return guard_sign_result.signed(s.sign())
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

    private fun uncompressed(pub: ECPublicKey): ByteArray {
        fun fixed(v: BigInteger): ByteArray {
            val raw = v.toByteArray()
            val out = ByteArray(32)
            val src = if (raw.size > 32) raw.copyOfRange(raw.size - 32, raw.size) else raw
            System.arraycopy(src, 0, out, 32 - src.size, src.size)
            return out
        }
        return byteArrayOf(4) + fixed(pub.w.affineX) + fixed(pub.w.affineY)
    }

    private fun verifies(message: ByteArray, der: ByteArray): Boolean {
        val s = Signature.getInstance("SHA256withECDSA")
        s.initVerify(pair.public)
        s.update(message)
        return s.verify(der)
    }

    private fun sealed_secret(): String = DeviceEnvelope.base64url_encode(
        GuardContextSeal.seal(code_secret, GuardContextSeal.code_secret_aad(device), keys.x25519_pk, keys.mlkem_pk),
    )

    private fun api(status: GuardStatus = GuardStatus()): GuardApi = mockk(relaxed = true) {
        coEvery { status() } returns status
        coEvery { setup_nonce() } returns GuardSetupNonce(DeviceEnvelope.base64url_encode(nonce))
        coEvery { setup(any()) } returns GuardSetupResponse(device.toString(), sealed_secret())
        coEvery { join(any()) } returns GuardJoinStarted("req-1", "2026-11-15T10:05:00Z")
        coEvery { join_status("req-1") } returns GuardJoinStatus("approved")
        coEvery { join_complete("req-1") } returns GuardJoinComplete(device.toString(), sealed_secret())
    }

    private fun on_status() = GuardStatus(
        switches = GuardSwitch.NEW_DEVICE_APPROVAL,
        enrolled_at = "2026-11-01T00:00:00Z",
        this_device = null,
    )

    private fun walk_to_password(vm: GuardSetupViewModel) {
        vm.to_choose()
        vm.to_rescue()
        vm.to_confirm_rescue()
        vm.on_confirm_input(rescue.takeLast(5).lowercase())
        assertTrue(vm.confirm_rescue())
        vm.on_password("correct horse")
    }

    @Test
    fun rescue_code_has_25_alphabet_characters() {
        repeat(20) {
            val code = generate_rescue_code(SecureRandom())
            assertEquals(25, code.length)
            assertTrue(code.all { it in GuardCode.ALPHABET })
        }
    }

    @Test
    fun switch_combinations_are_validated() {
        assertFalse(valid_switches(4, 0))
        assertTrue(valid_switches(5, 0))
        assertFalse(valid_switches(1, 1))
        assertTrue(valid_switches(GUARD_DEFAULT_SWITCHES, GUARD_ALL_SCOPES))
        assertFalse(valid_switches(0, 0))
    }

    @Test
    fun mail_apps_turns_on_new_device_approval_and_hold_off_clears_scopes() {
        val start = guard_selection(GuardSwitch.HOLD_CHANGES, GUARD_ALL_SCOPES)
        val with_apps = guard_toggle_switch(start, GuardSwitch.APPROVE_MAIL_APPS)
        assertEquals(GuardSwitch.NEW_DEVICE_APPROVAL, with_apps.switches and GuardSwitch.NEW_DEVICE_APPROVAL)
        val no_hold = guard_toggle_switch(with_apps, GuardSwitch.HOLD_CHANGES)
        assertEquals(0, no_hold.scopes)
        assertEquals(no_hold, guard_toggle_scope(no_hold, GuardHoldScope.FORWARDING_RULES))
    }

    @Test
    fun default_selection_is_approval_and_hold_with_all_scopes() = runTest(dispatcher) {
        val vm = GuardSetupViewModel(api(), GuardDeviceStore(InMemoryPrefs()), fake_deps())
        advanceUntilIdle()
        assertEquals(guard_setup_step.intro, vm.state.value.step)
        assertEquals(GUARD_DEFAULT_SWITCHES, vm.state.value.switches)
        assertEquals(GUARD_ALL_SCOPES, vm.state.value.scopes)
    }

    @Test
    fun confirm_rejects_a_wrong_last_group() = runTest(dispatcher) {
        val vm = GuardSetupViewModel(api(), GuardDeviceStore(InMemoryPrefs()), fake_deps())
        advanceUntilIdle()
        vm.to_choose()
        vm.to_rescue()
        vm.to_confirm_rescue()
        vm.on_confirm_input("BCDFG")
        assertFalse(vm.confirm_rescue())
        assertEquals(guard_setup_step.confirm_rescue, vm.state.value.step)
        assertEquals(guard_setup_notice.wrong_group, vm.state.value.notice)
    }

    @Test
    fun setup_sends_plain_rescue_code_and_enroll_signature() = runTest(dispatcher) {
        val api = api()
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSetupViewModel(api, store, fake_deps())
        advanceUntilIdle()
        walk_to_password(vm)
        val signer = soft_signer()
        vm.run_setup(signer)
        val sent = slot<GuardSetupRequest>()
        coVerify { api.setup(capture(sent)) }
        assertEquals(rescue, sent.captured.rescue_code)
        assertFalse(sent.captured.rescue_code.contains('-'))
        assertEquals("mobile", sent.captured.device.device_type)
        val action_pk = GuardDigests.compress_p256(uncompressed(pair.public as ECPublicKey))
        assertArrayEquals(action_pk, DeviceEnvelope.base64url_decode(sent.captured.device.action_pk))
        val digest = GuardDigests.keys_digest(keys.ed25519_pk, keys.mlkem_pk, keys.x25519_pk, action_pk)
        val der = DeviceEnvelope.base64url_decode(sent.captured.possession_signature)
        assertTrue(verifies(GuardDigests.action_message(GuardActionCode.ENROLL, user, nonce, digest), der))
        assertFalse(verifies(GuardDigests.action_message(GuardActionCode.JOIN, user, nonce, digest), der))
        assertTrue(sent.captured.proof is StepUpProof.Password)
        assertEquals(GUARD_DEFAULT_SWITCHES, sent.captured.switches)
        assertEquals(GUARD_ALL_SCOPES, sent.captured.hold_scopes)
        assertArrayEquals(code_secret, store.code_secret(user))
        assertEquals(device, store.device_id(user))
        assertEquals(guard_setup_step.done, vm.state.value.step)
        assertEquals("", vm.state.value.password)
    }

    @Test
    fun setup_saves_its_alias_and_keeps_the_key() = runTest(dispatcher) {
        val store = GuardDeviceStore(InMemoryPrefs())
        val deps = fake_deps()
        val vm = GuardSetupViewModel(api(), store, deps)
        advanceUntilIdle()
        walk_to_password(vm)
        vm.run_setup(soft_signer())
        assertEquals("setup_alias_1", store.stored_action_alias(user))
        assertTrue(deps.deleted.isEmpty())
    }

    @Test
    fun failed_setup_deletes_its_new_key() = runTest(dispatcher) {
        val api = api()
        coEvery { api.setup(any()) } throws java.io.IOException("offline")
        val store = GuardDeviceStore(InMemoryPrefs())
        val deps = fake_deps()
        val vm = GuardSetupViewModel(api, store, deps)
        advanceUntilIdle()
        walk_to_password(vm)
        vm.run_setup(soft_signer())
        assertEquals(listOf("setup_alias_1"), deps.deleted)
        assertNull(store.stored_action_alias(user))
    }

    @Test
    fun denied_join_deletes_its_new_key() = runTest(dispatcher) {
        val api = api(on_status())
        coEvery { api.join_status("req-1") } returns GuardJoinStatus("denied")
        val deps = fake_deps()
        val vm = GuardSetupViewModel(api, GuardDeviceStore(InMemoryPrefs()), deps)
        advanceUntilIdle()
        vm.run_join(soft_signer())
        vm.poll_once()
        assertEquals(listOf("setup_alias_1"), deps.deleted)
    }

    @Test
    fun canceled_prompt_makes_no_setup_call() = runTest(dispatcher) {
        val api = api()
        val deps = fake_deps()
        val vm = GuardSetupViewModel(api, GuardDeviceStore(InMemoryPrefs()), deps)
        advanceUntilIdle()
        walk_to_password(vm)
        vm.run_setup(guard_signer { guard_sign_result.canceled })
        coVerify(exactly = 0) { api.setup(any()) }
        assertEquals(deps.created, deps.deleted)
        assertEquals(guard_setup_step.password, vm.state.value.step)
        assertEquals(guard_setup_notice.canceled, vm.state.value.notice)
    }

    @Test
    fun totp_required_shows_the_code_field() = runTest(dispatcher) {
        val api = api()
        coEvery { api.setup(any()) } throws ApiError.ForbiddenError("totp", "TOTP_REQUIRED")
        val vm = GuardSetupViewModel(api, GuardDeviceStore(InMemoryPrefs()), fake_deps())
        advanceUntilIdle()
        walk_to_password(vm)
        vm.run_setup(soft_signer())
        assertTrue(vm.state.value.show_totp)
        assertEquals(guard_setup_notice.totp_required, vm.state.value.notice)
        assertFalse(vm.state.value.can_submit)
    }

    @Test
    fun guard_disabled_hides_the_wizard() = runTest(dispatcher) {
        val api = api()
        coEvery { api.status() } throws ApiError.NotFoundError
        val vm = GuardSetupViewModel(api, GuardDeviceStore(InMemoryPrefs()), fake_deps())
        advanceUntilIdle()
        assertEquals(guard_setup_step.hidden, vm.state.value.step)
    }

    @Test
    fun guard_on_with_this_device_shows_status() = runTest(dispatcher) {
        val api = api(on_status().copy(this_device = GuardThisDevice(device.toString())))
        val vm = GuardSetupViewModel(api, GuardDeviceStore(InMemoryPrefs()), fake_deps())
        advanceUntilIdle()
        assertEquals(guard_setup_step.already_on, vm.state.value.step)
    }

    @Test
    fun guard_on_without_this_device_joins_with_code_10() = runTest(dispatcher) {
        val api = api(on_status())
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSetupViewModel(api, store, fake_deps())
        advanceUntilIdle()
        assertEquals(guard_setup_step.join_intro, vm.state.value.step)
        vm.run_join(soft_signer())
        assertEquals(guard_setup_step.join_waiting, vm.state.value.step)
        assertEquals(42, vm.state.value.join_number)
        val sent = slot<GuardJoinRequest>()
        coVerify { api.join(capture(sent)) }
        coVerify(exactly = 0) { api.setup(any()) }
        val action_pk = GuardDigests.compress_p256(uncompressed(pair.public as ECPublicKey))
        val digest = GuardDigests.keys_digest(keys.ed25519_pk, keys.mlkem_pk, keys.x25519_pk, action_pk)
        val der = DeviceEnvelope.base64url_decode(sent.captured.action.signature)
        assertTrue(verifies(GuardDigests.action_message(GuardActionCode.JOIN, user, nonce, digest), der))
        assertEquals(listOf(2), sent.captured.envelope_versions)
        assertEquals(join_id.toString(), sent.captured.request_id)
        assertArrayEquals(
            GuardDigests.number_hash(join_id, 42),
            DeviceEnvelope.base64url_decode(sent.captured.number_hash),
        )
        vm.poll_once()
        coVerify { api.join_complete("req-1") }
        assertEquals(guard_setup_step.join_done, vm.state.value.step)
        assertArrayEquals(code_secret, store.code_secret(user))
        assertNull(vm.state.value.join_number)
    }

    @Test
    fun denied_join_ends_without_saving() = runTest(dispatcher) {
        val api = api(on_status())
        coEvery { api.join_status("req-1") } returns GuardJoinStatus("denied")
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSetupViewModel(api, store, fake_deps())
        advanceUntilIdle()
        vm.run_join(soft_signer())
        vm.poll_once()
        assertEquals(guard_setup_step.join_denied, vm.state.value.step)
        assertNull(store.code_secret(user))
        coVerify(exactly = 0) { api.join_complete(any()) }
    }
}
