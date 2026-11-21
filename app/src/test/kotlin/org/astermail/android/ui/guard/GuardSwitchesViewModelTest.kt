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
import org.astermail.android.api.ApiError
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardChallenge
import org.astermail.android.api.guard.GuardDeviceInfo
import org.astermail.android.api.guard.GuardHoldScope
import org.astermail.android.api.guard.GuardStatus
import org.astermail.android.api.guard.GuardSwitch
import org.astermail.android.api.guard.GuardThisDevice
import org.astermail.android.api.guard.SignedAction
import org.astermail.android.crypto.DeviceEnvelope
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.guard_hold_kind
import org.astermail.android.guard.guard_sign_result
import org.astermail.android.guard.guard_signer
import org.astermail.android.storage.InMemoryPrefs
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GuardSwitchesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val user = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0")
    private val this_id = "a1a2a3a4-b1b2-c1c2-d1d2-e1e2e3e4e5e6"
    private val other_id = "b1b2b3b4-c1c2-d1d2-e1e2-f1f2f3f4f5f6"
    private val nonce = ByteArray(32) { (it + 7).toByte() }
    private val all_scopes = GuardHoldScope.FORWARDING_RULES or GuardHoldScope.RECOVERY_PASSWORD or GuardHoldScope.SECURITY_EXPORT

    private class recording_signer(private val result: guard_sign_result = guard_sign_result.signed(byteArrayOf(1, 2, 3))) : guard_signer {
        val messages = mutableListOf<ByteArray>()
        override suspend fun sign(message: ByteArray): guard_sign_result {
            messages += message
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

    private fun on_status(
        switches: Int = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES,
        scopes: Int = all_scopes,
        this_device: GuardThisDevice? = GuardThisDevice(id = this_id),
    ) = GuardStatus(
        switches = switches,
        hold_scopes = scopes,
        enrolled_at = "2026-11-01T00:00:00Z",
        has_rescue_code = true,
        this_device = this_device,
        devices = listOf(GuardDeviceInfo(id = this_id, name = "Pixel"), GuardDeviceInfo(id = other_id, name = "Tablet")),
    )

    private fun api(status: GuardStatus = on_status()): GuardApi = mockk(relaxed = true) {
        coEvery { status() } returns status
        coEvery { challenge() } returns GuardChallenge(DeviceEnvelope.base64url_encode(nonce))
    }

    private fun vm(api: GuardApi) = GuardSwitchesViewModel(api, GuardDeviceStore(InMemoryPrefs())) { user }

    @Test
    fun loads_status_into_the_draft() = runTest(dispatcher) {
        val model = vm(api())
        advanceUntilIdle()
        val ui = model.state.value
        assertFalse(ui.loading)
        assertTrue(ui.guard_on)
        assertTrue(ui.is_guard_device)
        assertEquals(GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES, ui.draft_switches)
        assertEquals(all_scopes, ui.draft_scopes)
        assertFalse(ui.dirty)
    }

    @Test
    fun not_found_hides_the_screen() = runTest(dispatcher) {
        val api: GuardApi = mockk(relaxed = true) { coEvery { status() } throws ApiError.NotFoundError }
        val model = vm(api)
        advanceUntilIdle()
        assertTrue(model.state.value.hidden)
    }

    @Test
    fun other_errors_show_retry() = runTest(dispatcher) {
        val api: GuardApi = mockk(relaxed = true) { coEvery { status() } throws IllegalStateException("x") }
        val model = vm(api)
        advanceUntilIdle()
        assertTrue(model.state.value.load_failed)
        assertFalse(model.state.value.hidden)
    }

    @Test
    fun turning_on_signs_switches_subject_and_saves() = runTest(dispatcher) {
        val api = api()
        val after = on_status(switches = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES or GuardSwitch.RECOVERY_WAIT)
        val action = slot<SignedAction>()
        coEvery { api.put_switches(any(), any(), capture(action)) } returns after
        val model = vm(api)
        advanceUntilIdle()
        model.toggle(GuardSwitch.RECOVERY_WAIT)
        assertTrue(model.state.value.can_save)
        val signer = recording_signer()
        model.run_save(signer)
        val expected_switches = GuardSwitch.NEW_DEVICE_APPROVAL or GuardSwitch.HOLD_CHANGES or GuardSwitch.RECOVERY_WAIT
        coVerify { api.put_switches(expected_switches, all_scopes, any()) }
        val expected = GuardDigests.action_message(
            GuardActionCode.SWITCHES,
            user,
            nonce,
            GuardDigests.switches_subject(expected_switches, all_scopes),
        )
        assertArrayEquals(expected, signer.messages.single())
        assertEquals(DeviceEnvelope.base64url_encode(nonce), action.captured.nonce)
        assertFalse(model.state.value.dirty)
        assertNull(model.state.value.held_notice)
    }

    @Test
    fun turning_off_when_held_keeps_the_switch_on_and_shows_notice() = runTest(dispatcher) {
        val api = api()
        coEvery { api.put_switches(any(), any(), any()) } throws
            ApiError.GuardHeld("chg-1", "guard_switches_off", "2026-11-16T10:00:00Z")
        val model = vm(api)
        advanceUntilIdle()
        model.toggle(GuardSwitch.NEW_DEVICE_APPROVAL)
        model.run_save(recording_signer())
        val ui = model.state.value
        assertNotNull(ui.held_notice)
        assertEquals(guard_hold_kind.guard_switches_off, ui.held_notice?.kind)
        assertEquals("chg-1", ui.held_notice?.id)
        assertTrue(ui.draft_switches and GuardSwitch.NEW_DEVICE_APPROVAL != 0)
        assertEquals(GuardSwitch.NEW_DEVICE_APPROVAL, ui.pending_off_switches)
        assertFalse(ui.saving)
    }

    @Test
    fun canceled_prompt_saves_nothing() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        model.toggle(GuardSwitch.RECOVERY_WAIT)
        model.run_save(recording_signer(guard_sign_result.canceled))
        coVerify(exactly = 0) { api.put_switches(any(), any(), any()) }
        assertEquals(guard_action_notice.canceled, model.state.value.notice)
        assertTrue(model.state.value.dirty)
    }

    @Test
    fun key_lost_shows_notice() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        model.toggle(GuardSwitch.RECOVERY_WAIT)
        model.run_save(recording_signer(guard_sign_result.key_lost))
        assertEquals(guard_action_notice.key_lost, model.state.value.notice)
    }

    @Test
    fun not_a_guard_device_cannot_save() = runTest(dispatcher) {
        val api = api(on_status(this_device = null))
        val model = vm(api)
        advanceUntilIdle()
        model.toggle(GuardSwitch.RECOVERY_WAIT)
        assertFalse(model.state.value.can_save)
        model.run_save(recording_signer())
        coVerify(exactly = 0) { api.put_switches(any(), any(), any()) }
    }

    @Test
    fun turning_hold_off_clears_scopes_in_the_draft() = runTest(dispatcher) {
        val model = vm(api())
        advanceUntilIdle()
        model.toggle(GuardSwitch.HOLD_CHANGES)
        assertEquals(0, model.state.value.draft_switches and GuardSwitch.HOLD_CHANGES)
        assertTrue(valid_switches(model.state.value.draft_switches, model.state.value.draft_scopes))
        model.discard()
        assertFalse(model.state.value.dirty)
    }

    @Test
    fun removing_a_device_signs_its_id() = runTest(dispatcher) {
        val api = api()
        val model = vm(api)
        advanceUntilIdle()
        val signer = recording_signer()
        model.run_remove_device(signer, other_id)
        coVerify { api.remove_device(other_id, any()) }
        val expected = GuardDigests.action_message(
            GuardActionCode.REMOVE_DEVICE,
            user,
            nonce,
            GuardDigests.id_subject(UUID.fromString(other_id)),
        )
        assertArrayEquals(expected, signer.messages.single())
        assertNull(model.state.value.removing_id)
    }

    @Test
    fun held_device_removal_shows_notice() = runTest(dispatcher) {
        val api = api()
        coEvery { api.remove_device(any(), any()) } throws
            ApiError.GuardHeld("chg-2", "guard_device_remove", "not-a-time")
        val model = vm(api)
        advanceUntilIdle()
        model.run_remove_device(recording_signer(), other_id)
        val notice = model.state.value.held_notice
        assertEquals(guard_hold_kind.guard_device_remove, notice?.kind)
        assertNull(notice?.apply_after)
    }

    @Test
    fun hold_kind_parsing_falls_back_to_unknown() {
        assertEquals(guard_hold_kind.password_change, guard_hold_kind.from_wire("password_change"))
        assertEquals(guard_hold_kind.unknown, guard_hold_kind.from_wire("something_new"))
        assertEquals(guard_hold_kind.unknown, guard_hold_kind.from_wire(""))
        assertEquals(guard_hold_kind.unknown, guard_hold_kind.from_wire(null))
        assertTrue(guard_hold_kind.rescue.can_veto)
        assertFalse(guard_hold_kind.rescue.skippable)
        assertTrue(guard_hold_kind.forwarding_change.skippable)
    }

    @Test
    fun entry_hides_when_guard_is_unavailable() = runTest(dispatcher) {
        val api: GuardApi = mockk(relaxed = true) { coEvery { status() } throws ApiError.NotFoundError }
        val entry = GuardEntryViewModel(api)
        entry.load()
        assertFalse(entry.state.value.visible)
        val on = GuardEntryViewModel(api())
        on.load()
        assertTrue(on.state.value.visible)
        assertTrue(on.state.value.on)
    }
}
