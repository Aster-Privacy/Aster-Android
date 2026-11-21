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
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.GUARD_PAUSED_CODE
import org.astermail.android.api.GUARD_REQUEST_GONE_CODE
import org.astermail.android.api.GUARD_WRONG_NUMBER_CODE
import org.astermail.android.api.auth.LoginResponse
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardResumeNonce
import org.astermail.android.api.guard.GuardSignInBegin
import org.astermail.android.api.guard.GuardSignInBeginResponse
import org.astermail.android.api.guard.GuardSignInComplete
import org.astermail.android.api.guard.GuardSignInGuard
import org.astermail.android.api.guard.GuardSignInStatus
import org.astermail.android.auth.GuardLoginCredentials
import org.astermail.android.auth.GuardSignInChallenge
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardContextSeal
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.generate_box_keys
import org.astermail.android.guard.guard_box_keys
import org.astermail.android.guard.guard_pending_sign_in
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
class GuardSignInViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val base_ms = 1_800_000_000_000L
    private val request_id = "6f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0"
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val device = UUID.fromString("a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6")
    private val token = "guard-token"
    private val expires_at = Instant.ofEpochMilli(base_ms + 300_000).toString()
    private val action_pub = p256_public()
    private val fresh_keys = generate_box_keys()

    private class fake_deps(
        val keys: guard_box_keys,
        val action_pub: ByteArray,
        val now: () -> Long,
        var stored_action_pub: ByteArray? = null,
        var current_user: UUID? = null,
    ) : guard_sign_in_deps {
        var action_keys_created = 0
        var completes = 0
        var passphrase_seen: ByteArray? = null
        var opened: ByteArray? = null
        val created_aliases = mutableListOf<String>()
        val deleted_aliases = mutableListOf<String>()
        val looked_up_aliases = mutableListOf<String>()
        private var alias_counter = 0
        override fun new_box_keys() = keys
        override fun new_action_alias(): String = "fresh_alias_${++alias_counter}"
        override fun create_action_key(alias: String): ByteArray {
            action_keys_created++
            created_aliases += alias
            return action_pub
        }
        override fun delete_action_key(alias: String) {
            deleted_aliases += alias
        }
        override fun action_public_key(alias: String): ByteArray? {
            looked_up_aliases += alias
            return stored_action_pub
        }
        override fun current_user_id(): UUID? = current_user
        override fun device_name() = "Pixel"
        override fun now_ms() = now()
        override fun pick_number() = 42
        override fun open_envelope(envelope: ByteArray, keys: guard_box_keys): ByteArray {
            opened = envelope
            return byteArrayOf(7, 7, 7)
        }
        override suspend fun complete(challenge: GuardSignInChallenge, complete: GuardSignInComplete, passphrase: ByteArray?) {
            completes++
            passphrase_seen = passphrase
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

    private fun p256_public(): ByteArray {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec("secp256r1"))
        val pub = gen.generateKeyPair().public as ECPublicKey
        val out = ByteArray(65)
        out[0] = 4
        fun put(v: java.math.BigInteger, off: Int) {
            val raw = v.toByteArray().let { if (it.size > 32) it.copyOfRange(it.size - 32, it.size) else it }
            System.arraycopy(raw, 0, out, off + 32 - raw.size, raw.size)
        }
        put(pub.w.affineX, 1)
        put(pub.w.affineY, 33)
        return out
    }

    private fun challenge(with_credentials: Boolean = true, user_id: String? = null) = GuardSignInChallenge(
        request_id = request_id,
        guard_token = token,
        expires_at = expires_at,
        methods = listOf("number", "code"),
        email = "a@astermail.org",
        user_id = user_id,
        credentials = if (with_credentials) GuardLoginCredentials(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), "h") else null,
    )

    private fun login() = LoginResponse(
        user_id = user.toString(),
        username = "a",
        email = "a@astermail.org",
        csrf_token = "c",
        encrypted_vault = "v",
        vault_nonce = "n",
    )

    private fun api(status: String = "pending"): GuardApi = mockk<GuardApi>().also {
        coEvery { it.sign_in_begin(any(), any()) } returns GuardSignInBeginResponse("pending", expires_at)
        coEvery { it.sign_in_status(any()) } returns GuardSignInStatus(status, expires_at)
    }

    private fun TestScope.deps(stored_action_pub: ByteArray? = null, current_user: UUID? = null) =
        fake_deps(fresh_keys, action_pub, { base_ms + testScheduler.currentTime }, stored_action_pub, current_user)

    private val no_signer = guard_signer { guard_sign_result.canceled }

    @Test
    fun number_is_two_digits() {
        val random = SecureRandom()
        repeat(2_000) { assertTrue(guard_pick_number(random) in 10..99) }
    }

    @Test
    fun begin_sends_number_hash_mobile_v2_and_compressed_action_key() = runTest(dispatcher) {
        val api = api()
        val sent = slot<GuardSignInBegin>()
        coEvery { api.sign_in_begin(token, capture(sent)) } returns GuardSignInBeginResponse("pending", expires_at)
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSignInViewModel(api, store, deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        val state = vm.state.value as guard_wait_state.waiting
        assertEquals(42, state.number)
        assertEquals(
            DeviceEnvelope.base64url_encode(GuardDigests.number_hash(UUID.fromString(request_id), 42)),
            sent.captured.number_hash,
        )
        assertEquals("mobile", sent.captured.device.device_type)
        assertEquals(listOf(2), sent.captured.envelope_versions)
        assertEquals(33, DeviceEnvelope.base64url_decode(sent.captured.device.action_pk).size)
        assertNull(sent.captured.resume)
        assertEquals(42, store.pending_sign_in()!!.number)
        vm.cancel()
    }

    @Test
    fun polls_no_faster_than_every_two_seconds() = runTest(dispatcher) {
        val api = api()
        val vm = GuardSignInViewModel(api, GuardDeviceStore(InMemoryPrefs()), deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        advanceTimeBy(10_001)
        coVerify(atMost = 5, atLeast = 4) { api.sign_in_status(token) }
        vm.cancel()
    }

    @Test
    fun approved_completes_once_and_saves_enrollment() = runTest(dispatcher) {
        val api = api("approved")
        val code_secret = ByteArray(20) { (it + 1).toByte() }
        val sealed = GuardContextSeal.seal(
            code_secret,
            GuardContextSeal.code_secret_aad(device),
            fresh_keys.x25519_pk,
            fresh_keys.mlkem_pk,
        )
        coEvery { api.sign_in_complete(token) } returns GuardSignInComplete(
            login(),
            GuardSignInGuard(device.toString(), DeviceEnvelope.base64url_encode(sealed), DeviceEnvelope.base64url_encode(byteArrayOf(9))),
        )
        val store = GuardDeviceStore(InMemoryPrefs())
        val d = deps()
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(), no_signer)
        runCurrent()
        advanceTimeBy(6_001)
        coVerify(exactly = 1) { api.sign_in_complete(token) }
        assertEquals(1, d.completes)
        assertArrayEquals(byteArrayOf(7, 7, 7), d.passphrase_seen)
        assertArrayEquals(byteArrayOf(9), d.opened)
        assertEquals(guard_wait_state.done, vm.state.value)
        assertEquals(device, store.device_id(user))
        assertArrayEquals(code_secret, store.code_secret(user))
        assertNull(store.pending_sign_in())
    }

    @Test
    fun null_envelope_unlocks_with_the_typed_password() = runTest(dispatcher) {
        val api = api("approved")
        coEvery { api.sign_in_complete(token) } returns GuardSignInComplete(login(), null)
        val d = deps()
        val vm = GuardSignInViewModel(api, GuardDeviceStore(InMemoryPrefs()), d)
        vm.start(challenge(), no_signer)
        runCurrent()
        advanceTimeBy(2_001)
        assertEquals(1, d.completes)
        assertNull(d.passphrase_seen)
        assertEquals(guard_wait_state.done, vm.state.value)
    }

    @Test
    fun expired_clears_pending() = runTest(dispatcher) {
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSignInViewModel(api("expired"), store, deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        assertNotNull(store.pending_sign_in())
        advanceTimeBy(2_001)
        assertEquals(guard_wait_state.expired, vm.state.value)
        assertNull(store.pending_sign_in())
    }

    @Test
    fun request_gone_maps_to_expired() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_status(token) } throws ApiError.Conflict("gone", GUARD_REQUEST_GONE_CODE)
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSignInViewModel(api, store, deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        advanceTimeBy(2_001)
        assertEquals(guard_wait_state.expired, vm.state.value)
        assertNull(store.pending_sign_in())
    }

    @Test
    fun restored_pending_shows_the_same_number() = runTest(dispatcher) {
        val store = GuardDeviceStore(InMemoryPrefs())
        store.save_pending_sign_in(guard_pending_sign_in(request_id, token, 37, expires_at, fresh_keys, "a@astermail.org"))
        val vm = GuardSignInViewModel(api(), store, deps())
        assertTrue(vm.restore_pending())
        runCurrent()
        assertEquals(37, (vm.state.value as guard_wait_state.waiting).number)
        vm.cancel()
    }

    @Test
    fun restored_pending_without_password_asks_for_it() = runTest(dispatcher) {
        val api = api("approved")
        val store = GuardDeviceStore(InMemoryPrefs())
        store.save_pending_sign_in(guard_pending_sign_in(request_id, token, 37, expires_at, fresh_keys, "a@astermail.org"))
        val vm = GuardSignInViewModel(api, store, deps())
        vm.restore_pending()
        advanceTimeBy(2_001)
        assertEquals(guard_wait_state.needs_password, vm.state.value)
        coVerify(exactly = 0) { api.sign_in_complete(any()) }
    }

    @Test
    fun submit_code_normalizes_input() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_code(token, "BCDFG") } returns GuardSignInStatus("pending", expires_at)
        val vm = GuardSignInViewModel(api, GuardDeviceStore(InMemoryPrefs()), deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        vm.submit_code("bcd fg")
        runCurrent()
        coVerify(exactly = 1) { api.sign_in_code(token, "BCDFG") }
        vm.cancel()
    }

    @Test
    fun wrong_code_shows_notice() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_code(token, any()) } throws ApiError.ForbiddenError("x", GUARD_WRONG_NUMBER_CODE)
        val vm = GuardSignInViewModel(api, GuardDeviceStore(InMemoryPrefs()), deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        vm.submit_code("ABCDE")
        runCurrent()
        assertEquals(guard_wait_notice.wrong_code, (vm.state.value as guard_wait_state.waiting).notice)
        vm.cancel()
    }

    @Test
    fun paused_shows_retry_time() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_begin(any(), any()) } throws ApiError.GuardRetry(GUARD_PAUSED_CODE, 120)
        val vm = GuardSignInViewModel(api, GuardDeviceStore(InMemoryPrefs()), deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        assertEquals(guard_wait_state.retry(120), vm.state.value)
    }

    @Test
    fun stored_enrollment_resumes_with_resume_code() = runTest(dispatcher) {
        val api = api()
        val nonce = ByteArray(32) { it.toByte() }
        coEvery { api.sign_in_resume_nonce(token, device.toString()) } returns
            GuardResumeNonce(DeviceEnvelope.base64url_encode(nonce), expires_at)
        val sent = slot<GuardSignInBegin>()
        coEvery { api.sign_in_begin(token, capture(sent)) } returns GuardSignInBeginResponse("approved", expires_at)
        coEvery { api.sign_in_complete(token) } returns GuardSignInComplete(login(), null)
        val store = GuardDeviceStore(InMemoryPrefs())
        val stored = generate_box_keys()
        store.save_enrollment(
            user, device, ByteArray(20), stored.x25519_sk, stored.mlkem_sk, stored.x25519_pk, stored.mlkem_pk, stored.ed25519_pk, stored.ed25519_sk,
        )
        val d = deps(stored_action_pub = action_pub)
        var seen: ByteArray? = null
        val signer = guard_signer { msg ->
            seen = msg
            guard_sign_result.signed(byteArrayOf(5))
        }
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(user_id = user.toString()), signer)
        runCurrent()
        assertEquals(GuardActionCode.RESUME.wire.toByte(), seen!![21])
        val digest = GuardDigests.keys_digest(
            stored.ed25519_pk, stored.mlkem_pk, stored.x25519_pk, GuardDigests.compress_p256(action_pub),
        )
        assertArrayEquals(GuardDigests.action_message(GuardActionCode.RESUME, user, nonce, digest), seen)
        assertEquals(device.toString(), sent.captured.resume!!.device_id)
        assertEquals(DeviceEnvelope.base64url_encode(stored.x25519_pk), sent.captured.device.x25519_pk)
        assertEquals(0, d.action_keys_created)
        assertEquals(1, d.completes)
        assertEquals(guard_wait_state.done, vm.state.value)
        coVerify(exactly = 0) { api.sign_in_status(any()) }
    }

    private fun enrolled_store(alias: String? = null): GuardDeviceStore {
        val store = GuardDeviceStore(InMemoryPrefs())
        val stored = generate_box_keys()
        store.save_enrollment(
            user, device, ByteArray(20), stored.x25519_sk, stored.mlkem_sk, stored.x25519_pk, stored.mlkem_pk, stored.ed25519_pk, stored.ed25519_sk,
            alias,
        )
        return store
    }

    private fun nonce_api(): GuardApi = api().also {
        coEvery { it.sign_in_resume_nonce(token, device.toString()) } returns
            GuardResumeNonce(DeviceEnvelope.base64url_encode(ByteArray(32)), expires_at)
    }

    @Test
    fun canceled_resume_keeps_the_enrolled_key_and_offers_retry() = runTest(dispatcher) {
        val api = nonce_api()
        val store = enrolled_store("enrolled_alias")
        val d = deps(stored_action_pub = action_pub)
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(user_id = user.toString()), no_signer)
        runCurrent()
        assertEquals(guard_wait_state.resume_blocked(guard_resume_block.unlock), vm.state.value)
        assertEquals(0, d.action_keys_created)
        assertTrue(d.deleted_aliases.isEmpty())
        assertEquals("enrolled_alias", store.stored_action_alias(user))
        assertEquals(device, store.device_id(user))
        coVerify(exactly = 0) { api.sign_in_begin(any(), any()) }
        vm.cancel()
        assertTrue(d.deleted_aliases.isEmpty())
    }

    @Test
    fun failed_resume_nonce_keeps_the_key_and_offers_retry() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_resume_nonce(token, device.toString()) } throws java.io.IOException("offline")
        val store = enrolled_store("enrolled_alias")
        val d = deps(stored_action_pub = action_pub)
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(user_id = user.toString()), no_signer)
        runCurrent()
        assertEquals(guard_wait_state.resume_blocked(guard_resume_block.network), vm.state.value)
        assertEquals(0, d.action_keys_created)
        assertTrue(d.deleted_aliases.isEmpty())
        coVerify(exactly = 0) { api.sign_in_begin(any(), any()) }
        vm.cancel()
    }

    @Test
    fun retry_after_a_blocked_resume_signs_with_the_enrolled_key() = runTest(dispatcher) {
        val api = nonce_api()
        val sent = slot<GuardSignInBegin>()
        coEvery { api.sign_in_begin(token, capture(sent)) } returns GuardSignInBeginResponse("pending", expires_at)
        val store = enrolled_store("enrolled_alias")
        val d = deps(stored_action_pub = action_pub)
        var attempts = 0
        val signer = guard_signer {
            attempts++
            if (attempts == 1) guard_sign_result.canceled else guard_sign_result.signed(byteArrayOf(5))
        }
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(user_id = user.toString()), signer)
        runCurrent()
        assertTrue(vm.state.value is guard_wait_state.resume_blocked)
        vm.retry_resume()
        runCurrent()
        assertTrue(vm.state.value is guard_wait_state.waiting)
        assertEquals(device.toString(), sent.captured.resume!!.device_id)
        assertEquals(0, d.action_keys_created)
        assertEquals(listOf("enrolled_alias", "enrolled_alias"), d.looked_up_aliases)
        assertEquals("enrolled_alias", store.pending_sign_in()!!.action_alias)
        vm.cancel()
        assertTrue(d.deleted_aliases.isEmpty())
    }

    @Test
    fun revoked_device_falls_back_to_a_fresh_alias() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_resume_nonce(token, device.toString()) } throws ApiError.NotFoundError
        val sent = slot<GuardSignInBegin>()
        coEvery { api.sign_in_begin(token, capture(sent)) } returns GuardSignInBeginResponse("pending", expires_at)
        val store = enrolled_store("enrolled_alias")
        val d = deps(stored_action_pub = action_pub)
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(user_id = user.toString()), no_signer)
        runCurrent()
        assertNull(sent.captured.resume)
        assertEquals(listOf("fresh_alias_1"), d.created_aliases)
        assertEquals("enrolled_alias", store.stored_action_alias(user))
        vm.cancel()
        assertEquals(listOf("fresh_alias_1"), d.deleted_aliases)
    }

    @Test
    fun resume_uses_the_legacy_alias_for_the_current_account_only() = runTest(dispatcher) {
        val api = nonce_api()
        val store = enrolled_store(null)
        val d = deps(stored_action_pub = action_pub, current_user = user)
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(user_id = user.toString()), no_signer)
        runCurrent()
        assertEquals(listOf(org.astermail.android.guard.GuardActionKey.LEGACY_ALIAS), d.looked_up_aliases)
        vm.cancel()

        val other_store = enrolled_store(null)
        val other = deps(stored_action_pub = action_pub, current_user = UUID.randomUUID())
        val other_vm = GuardSignInViewModel(nonce_api(), other_store, other)
        other_vm.start(challenge(user_id = user.toString()), no_signer)
        runCurrent()
        assertEquals(listOf(org.astermail.android.guard.GuardActionKey.default_alias(user)), other.looked_up_aliases)
        other_vm.cancel()
    }

    @Test
    fun fresh_enrollment_saves_its_alias_and_drops_the_old_one() = runTest(dispatcher) {
        val api = api("approved")
        val code_secret = ByteArray(20) { (it + 1).toByte() }
        val sealed = GuardContextSeal.seal(
            code_secret,
            GuardContextSeal.code_secret_aad(device),
            fresh_keys.x25519_pk,
            fresh_keys.mlkem_pk,
        )
        coEvery { api.sign_in_complete(token) } returns GuardSignInComplete(
            login(),
            GuardSignInGuard(device.toString(), DeviceEnvelope.base64url_encode(sealed), DeviceEnvelope.base64url_encode(byteArrayOf(9))),
        )
        val store = enrolled_store("old_alias")
        val d = deps()
        val vm = GuardSignInViewModel(api, store, d)
        vm.start(challenge(), no_signer)
        runCurrent()
        advanceTimeBy(2_001)
        assertEquals(guard_wait_state.done, vm.state.value)
        assertEquals("fresh_alias_1", store.stored_action_alias(user))
        assertEquals(listOf("old_alias"), d.deleted_aliases)
    }

    @Test
    fun cancelled_fresh_sign_in_deletes_its_new_key() = runTest(dispatcher) {
        val d = deps()
        val vm = GuardSignInViewModel(api(), GuardDeviceStore(InMemoryPrefs()), d)
        vm.start(challenge(), no_signer)
        runCurrent()
        vm.cancel()
        assertEquals(listOf("fresh_alias_1"), d.deleted_aliases)
    }

    @Test
    fun refreshed_expiry_is_saved_for_restore() = runTest(dispatcher) {
        val later = Instant.ofEpochMilli(base_ms + 600_000).toString()
        val api = api()
        coEvery { api.sign_in_begin(any(), any()) } returns GuardSignInBeginResponse("pending", later)
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = GuardSignInViewModel(api, store, deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        assertEquals(later, store.pending_sign_in()!!.expires_at)
        val restored = GuardSignInViewModel(api, store, deps())
        assertTrue(restored.restore_pending())
        runCurrent()
        assertTrue((restored.state.value as guard_wait_state.waiting).seconds_left > 300)
        restored.cancel()
    }
}
