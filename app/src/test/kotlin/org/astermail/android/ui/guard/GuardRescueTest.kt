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
import org.astermail.android.api.GUARD_VETO_INVALID_CODE
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardRescueStarted
import org.astermail.android.api.guard.GuardRescueStatus
import org.astermail.android.api.guard.GuardSignInBeginResponse
import org.astermail.android.api.guard.GuardSignInComplete
import org.astermail.android.api.guard.GuardSignInStatus
import org.astermail.android.api.guard.GuardVetoResult
import org.astermail.android.auth.GuardLoginCredentials
import org.astermail.android.auth.GuardSignInChallenge
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.generate_box_keys
import org.astermail.android.guard.guard_box_keys
import org.astermail.android.guard.guard_saved_rescue
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.InMemoryPrefs
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardRescueTest {
    private val dispatcher = StandardTestDispatcher()
    private val base_ms = 1_800_000_000_000L
    private val request_id = "6f1e2d3c-4b5a-4978-8796-a5b4c3d2e1f0"
    private val rescue_id = "9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"
    private val token = "guard-token"
    private val expires_at = Instant.ofEpochMilli(base_ms + 300_000).toString()
    private val apply_after = Instant.ofEpochMilli(base_ms + 72 * 3_600_000L).toString()
    private val keys = generate_box_keys()
    private val no_signer = guard_signer { guard_sign_result.canceled }

    private class fake_deps(val keys: guard_box_keys, val now: () -> Long) : guard_sign_in_deps {
        override fun new_box_keys() = keys
        override fun new_action_alias(): String = "rescue_alias"
        override fun create_action_key(alias: String): ByteArray = ByteArray(65).also { it[0] = 4 }
        override fun delete_action_key(alias: String) = Unit
        override fun action_public_key(alias: String): ByteArray? = null
        override fun current_user_id(): UUID? = null
        override fun device_name() = "Pixel"
        override fun now_ms() = now()
        override fun pick_number() = 42
        override fun open_envelope(envelope: ByteArray, keys: guard_box_keys) = ByteArray(0)
        override suspend fun complete(challenge: GuardSignInChallenge, complete: GuardSignInComplete, passphrase: ByteArray?) = Unit
    }

    @Before
    fun set_up() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tear_down() {
        Dispatchers.resetMain()
    }

    private fun challenge() = GuardSignInChallenge(
        request_id = request_id,
        guard_token = token,
        expires_at = expires_at,
        methods = listOf("number", "code"),
        email = "a@astermail.org",
        user_id = null,
        credentials = GuardLoginCredentials(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), "h"),
    )

    private fun api(): GuardApi = mockk<GuardApi>().also {
        coEvery { it.sign_in_begin(any(), any()) } returns GuardSignInBeginResponse("pending", expires_at)
        coEvery { it.sign_in_status(any()) } returns GuardSignInStatus("pending", expires_at)
        coEvery { it.sign_in_rescue(any(), any()) } returns GuardRescueStarted(rescue_id, apply_after)
        coEvery { it.rescue_status(any()) } returns GuardRescueStatus("pending", apply_after)
    }

    private fun TestScope.deps() = fake_deps(keys) { base_ms + testScheduler.currentTime }

    private fun TestScope.waiting(api: GuardApi, store: GuardDeviceStore): GuardSignInViewModel {
        val vm = GuardSignInViewModel(api, store, deps())
        vm.start(challenge(), no_signer)
        runCurrent()
        assertTrue(vm.state.value is guard_wait_state.waiting)
        return vm
    }

    @Test
    fun rescue_code_is_normalized_before_it_is_sent() = runTest(dispatcher) {
        val api = api()
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = waiting(api, store)
        vm.open_rescue()
        assertEquals(guard_wait_state.rescue_entry(), vm.state.value)
        vm.submit_rescue("bcdfg-hjkmn-pqrtv-wxy23-45678")
        runCurrent()
        coVerify(exactly = 1) { api.sign_in_rescue(token, "BCDFGHJKMNPQRTVWXY2345678") }
        vm.cancel()
    }

    @Test
    fun rescue_code_outside_the_alphabet_makes_no_call() = runTest(dispatcher) {
        val api = api()
        val vm = waiting(api, GuardDeviceStore(InMemoryPrefs()))
        vm.open_rescue()
        vm.submit_rescue("BCDFG-HJKMN-PQRTV-WXY23-4567O")
        runCurrent()
        coVerify(exactly = 0) { api.sign_in_rescue(any(), any()) }
        assertEquals(guard_wait_state.rescue_entry(guard_rescue_notice.invalid), vm.state.value)
        vm.cancel()
    }

    @Test
    fun accepted_rescue_stores_the_id_and_shows_the_wait() = runTest(dispatcher) {
        val api = api()
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = waiting(api, store)
        vm.open_rescue()
        vm.submit_rescue("BCDFGHJKMNPQRTVWXY2345678")
        runCurrent()
        assertEquals(guard_wait_state.rescue_waiting(apply_after), vm.state.value)
        assertEquals(guard_saved_rescue(rescue_id, apply_after, "a@astermail.org"), store.rescue())
        assertNull(store.pending_sign_in())
        coVerify(exactly = 1) { api.rescue_status(rescue_id) }
        vm.cancel()
        assertEquals(rescue_id, store.rescue()?.rescue_id)
    }

    @Test
    fun rejected_rescue_shows_invalid_and_keeps_the_sign_in() = runTest(dispatcher) {
        val api = api()
        coEvery { api.sign_in_rescue(any(), any()) } throws ApiError.ForbiddenError("no", "GUARD_RESCUE_INVALID")
        val store = GuardDeviceStore(InMemoryPrefs())
        val vm = waiting(api, store)
        vm.open_rescue()
        vm.submit_rescue("BCDFGHJKMNPQRTVWXY2345678")
        runCurrent()
        assertEquals(guard_wait_state.rescue_entry(guard_rescue_notice.invalid), vm.state.value)
        assertNull(store.rescue())
        assertTrue(store.pending_sign_in() != null)
        vm.close_rescue()
        assertTrue(vm.state.value is guard_wait_state.waiting)
        vm.cancel()
    }

    @Test
    fun restored_rescue_polls_status() = runTest(dispatcher) {
        val api = api()
        val store = GuardDeviceStore(InMemoryPrefs())
        store.save_rescue(guard_saved_rescue(rescue_id, apply_after, "a@astermail.org"))
        val vm = GuardSignInViewModel(api, store, deps())
        assertTrue(vm.restore_pending())
        runCurrent()
        assertEquals(guard_wait_state.rescue_waiting(apply_after), vm.state.value)
        coVerify(exactly = 1) { api.rescue_status(rescue_id) }
        advanceTimeBy(GUARD_RESCUE_POLL_INTERVAL_MS)
        runCurrent()
        coVerify(exactly = 2) { api.rescue_status(rescue_id) }
        vm.cancel()
    }

    @Test
    fun applied_rescue_routes_to_sign_in_and_sets_up_guard_after() = runTest(dispatcher) {
        val api = api()
        coEvery { api.rescue_status(rescue_id) } returns GuardRescueStatus("applied", apply_after)
        val store = GuardDeviceStore(InMemoryPrefs())
        store.save_rescue(guard_saved_rescue(rescue_id, apply_after, "a@astermail.org"))
        val vm = GuardSignInViewModel(api, store, deps())
        vm.restore_pending()
        runCurrent()
        assertEquals(guard_wait_state.rescue_applied, vm.state.value)
        assertNull(store.rescue())
        assertTrue(store.take_setup_after_rescue())
        assertFalse(store.take_setup_after_rescue())
        vm.cancel()
        assertFalse(vm.restore_pending())
    }

    @Test
    fun vetoed_rescue_clears_the_id() = runTest(dispatcher) {
        val api = api()
        coEvery { api.rescue_status(rescue_id) } returns GuardRescueStatus("vetoed")
        val store = GuardDeviceStore(InMemoryPrefs())
        store.save_rescue(guard_saved_rescue(rescue_id, apply_after, "a@astermail.org"))
        val vm = GuardSignInViewModel(api, store, deps())
        vm.restore_pending()
        runCurrent()
        assertEquals(guard_wait_state.rescue_stopped, vm.state.value)
        assertNull(store.rescue())
        assertFalse(store.take_setup_after_rescue())
    }

    @Test
    fun veto_link_parses_id_and_token() {
        val link = parse_guard_veto("https://app.astermail.org/guard/veto?id=$rescue_id&t=tok_abc-1")
        assertEquals(guard_veto_link(rescue_id, "tok_abc-1"), link)
        assertNull(parse_guard_veto("https://app.astermail.org/guard/veto?id=$rescue_id"))
        assertNull(parse_guard_veto("http://app.astermail.org/guard/veto?id=a&t=b"))
        assertNull(parse_guard_veto("https://evil.example/guard/veto?id=a&t=b"))
        assertNull(parse_guard_veto("https://app.astermail.org/settings/billing?id=a&t=b"))
        assertNull(parse_guard_veto("https://app.astermail.org/guard/veto?id=a%20b&t=c"))
        assertFalse(link.toString().contains("tok_abc-1"))
    }

    @Test
    fun veto_waits_for_a_tap_then_posts_id_and_token() = runTest(dispatcher) {
        val api = mockk<GuardApi>()
        coEvery { api.veto_with_token(rescue_id, "tok") } returns GuardVetoResult("vetoed")
        val vm = GuardVetoViewModel(api)
        runCurrent()
        assertEquals(guard_veto_state.confirm, vm.state.value)
        coVerify(exactly = 0) { api.veto_with_token(any(), any()) }
        vm.stop(guard_veto_link(rescue_id, "tok"))
        runCurrent()
        assertEquals(guard_veto_state.done, vm.state.value)
        coVerify(exactly = 1) { api.veto_with_token(rescue_id, "tok") }
    }

    @Test
    fun veto_maps_invalid_and_other_errors() = runTest(dispatcher) {
        val invalid_api = mockk<GuardApi>()
        coEvery { invalid_api.veto_with_token(any(), any()) } throws ApiError.ForbiddenError("x", GUARD_VETO_INVALID_CODE)
        val invalid = GuardVetoViewModel(invalid_api)
        invalid.stop(guard_veto_link(rescue_id, "tok"))
        runCurrent()
        assertEquals(guard_veto_state.invalid, invalid.state.value)

        val gone_api = mockk<GuardApi>()
        coEvery { gone_api.veto_with_token(any(), any()) } throws ApiError.NotFoundError
        val gone = GuardVetoViewModel(gone_api)
        gone.stop(guard_veto_link(rescue_id, "tok"))
        runCurrent()
        assertEquals(guard_veto_state.invalid, gone.state.value)

        val error_api = mockk<GuardApi>()
        coEvery { error_api.veto_with_token(any(), any()) } throws ApiError.ServerError(500)
        val error = GuardVetoViewModel(error_api)
        error.stop(guard_veto_link(rescue_id, "tok"))
        runCurrent()
        assertEquals(guard_veto_state.error, error.state.value)
    }
}
