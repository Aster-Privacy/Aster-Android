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

package org.astermail.android.settings

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.api.domains.BIMI_MAX_LOGO_BYTES
import org.astermail.android.api.domains.BimiApi
import org.astermail.android.api.domains.BimiLogoInvalid
import org.astermail.android.api.domains.BimiState
import org.astermail.android.api.domains.BimiUploadResponse
import org.astermail.android.api.domains.BimiView
import org.astermail.android.api.domains.bimi_state_from
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BimiViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var bimi_api: BimiApi
    private lateinit var vm: BimiViewModel

    private val domain_id = "dom-1"
    private val svg = "<svg/>".toByteArray()

    private fun view(state: String, managed_dns: Boolean = false, domain_active: Boolean = true) = BimiView(
        state = state,
        domain_active = domain_active,
        managed_dns = managed_dns,
        preview_png = if (state == "off") null else "iVBORw0KGgo=",
    )

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        bimi_api = mockk(relaxed = true)
        vm = BimiViewModel(bimi_api)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `unknown states fall back to draft and null stays null`() {
        assertEquals(BimiState.draft, bimi_state_from("something_new"))
        assertEquals(BimiState.live, bimi_state_from("live"))
        assertNull(bimi_state_from(null))
        assertNull(BimiView(record_status = "weird", dmarc_status = "weird").known_record_status)
        assertNull(BimiView(dmarc_status = "weird").known_dmarc_status)
    }

    @Test
    fun `load picks the step from the state`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("off")
        vm.load(domain_id)
        runCurrent()
        assertEquals(BimiStep.logo, vm.state.value.step)

        val other = BimiViewModel(bimi_api)
        coEvery { bimi_api.get_bimi("dom-2") } returns view("draft")
        other.load("dom-2")
        runCurrent()
        assertEquals(BimiStep.publish, other.state.value.step)

        for ((index, raw) in listOf("pending", "attention", "external").withIndex()) {
            val managed = BimiViewModel(bimi_api)
            coEvery { bimi_api.get_bimi("dom-m$index") } returns view(raw)
            managed.load("dom-m$index")
            runCurrent()
            assertEquals(BimiStep.manage, managed.state.value.step)
        }

        val live = BimiViewModel(bimi_api)
        coEvery { bimi_api.get_bimi("dom-3") } returns view("live")
        live.load("dom-3")
        runCurrent()
        assertEquals(BimiStep.manage, live.state.value.step)
    }

    @Test
    fun `a failed load is reported`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } throws ApiError.NetworkError
        vm.load(domain_id)
        runCurrent()
        assertTrue(vm.state.value.load_failed)
    }

    @Test
    fun `a file over 64 KB is rejected without uploading`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("off")
        vm.load(domain_id)
        runCurrent()

        vm.upload_logo(ByteArray(BIMI_MAX_LOGO_BYTES + 1))
        runCurrent()

        assertEquals(BimiErrorKind.file_too_large, vm.state.value.error)
        coVerify(exactly = 0) { bimi_api.upload_logo(any(), any()) }
    }

    @Test
    fun `an upload stores the preview and the adjustments`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("off")
        coEvery { bimi_api.upload_logo(domain_id, svg) } returns
            BimiUploadResponse(bimi = view("draft"), adjustments = listOf("added_title"))
        vm.load(domain_id)
        runCurrent()

        vm.upload_logo(svg)
        runCurrent()

        val state = vm.state.value
        assertFalse(state.uploading)
        assertEquals(BimiState.draft, state.bimi_state)
        assertEquals(listOf("added_title"), state.adjustments)
        assertTrue(state.logo_errors.isEmpty())
    }

    @Test
    fun `a rejected upload lists every error code`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("off")
        coEvery { bimi_api.upload_logo(domain_id, svg) } throws
            BimiLogoInvalid(listOf("not_square", "script_content"))
        vm.load(domain_id)
        runCurrent()

        vm.upload_logo(svg)
        runCurrent()

        assertEquals(listOf("not_square", "script_content"), vm.state.value.logo_errors)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a throttled upload shows the wait message`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("off")
        coEvery { bimi_api.upload_logo(domain_id, svg) } throws ApiError.RateLimited(code = "BIMI_UPLOAD_THROTTLED")
        vm.load(domain_id)
        runCurrent()

        vm.upload_logo(svg)
        runCurrent()

        assertEquals(BimiErrorKind.throttled, vm.state.value.error)
    }

    @Test
    fun `publishing before the domain is active shows the setup message`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("draft")
        coEvery { bimi_api.publish(domain_id) } throws
            ApiError.ValidationError(listOf("not active"), "BIMI_DOMAIN_NOT_ACTIVE")
        vm.load(domain_id)
        runCurrent()

        vm.publish()
        runCurrent()

        assertEquals(BimiErrorKind.domain_not_active, vm.state.value.error)
        assertFalse(vm.state.value.publishing)
    }

    @Test
    fun `auto check runs every 20 seconds on the manage step while pending`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("draft")
        coEvery { bimi_api.publish(domain_id) } returns view("pending")
        coEvery { bimi_api.check(domain_id) } returns view("pending")
        vm.load(domain_id)
        runCurrent()
        vm.set_screen_visible(true)

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS + 1)
        runCurrent()
        coVerify(exactly = 0) { bimi_api.check(any()) }

        vm.publish()
        runCurrent()
        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(BimiStep.manage, vm.state.value.step)
        coVerify(exactly = 1) { bimi_api.check(domain_id) }

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS)
        runCurrent()
        coVerify(exactly = 2) { bimi_api.check(domain_id) }

        vm.set_screen_visible(false)
        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS * 3)
        runCurrent()
        coVerify(exactly = 2) { bimi_api.check(domain_id) }
    }

    @Test
    fun `auto check stops once the logo is live`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("pending")
        coEvery { bimi_api.check(domain_id) } returns view("live")
        vm.load(domain_id)
        runCurrent()
        vm.set_screen_visible(true)

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS + 1)
        runCurrent()
        assertEquals(BimiState.live, vm.state.value.bimi_state)

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS * 3)
        runCurrent()
        coVerify(exactly = 1) { bimi_api.check(domain_id) }
    }

    @Test
    fun `auto check ignores a throttled check silently`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("attention")
        coEvery { bimi_api.check(domain_id) } throws ApiError.RateLimited(code = "BIMI_ACTION_THROTTLED") andThen view("attention")
        vm.load(domain_id)
        runCurrent()
        vm.set_screen_visible(true)

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS + 1)
        runCurrent()
        assertNull(vm.state.value.error)

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS)
        runCurrent()
        coVerify(exactly = 2) { bimi_api.check(domain_id) }
        vm.set_screen_visible(false)
    }

    @Test
    fun `a manual check that is throttled shows the wait message`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("pending")
        coEvery { bimi_api.check(domain_id) } throws ApiError.RateLimited(code = "BIMI_ACTION_THROTTLED")
        vm.load(domain_id)
        runCurrent()

        vm.check()
        runCurrent()

        assertEquals(BimiErrorKind.throttled, vm.state.value.error)
        assertFalse(vm.state.value.checking)
    }

    @Test
    fun `turning off a domain you manage shows the remove record note`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("live")
        coEvery { bimi_api.turn_off(domain_id) } returns view("off")
        vm.load(domain_id)
        runCurrent()

        vm.turn_off()
        runCurrent()

        val state = vm.state.value
        assertEquals(BimiState.off, state.bimi_state)
        assertEquals(BimiStep.logo, state.step)
        assertTrue(state.show_remove_record_note)
    }

    @Test
    fun `turning off a purchased domain needs no record note`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("live", managed_dns = true)
        coEvery { bimi_api.turn_off(domain_id) } returns view("off", managed_dns = true)
        vm.load(domain_id)
        runCurrent()

        vm.turn_off()
        runCurrent()

        assertFalse(vm.state.value.show_remove_record_note)
    }

    @Test
    fun `replace logo returns to the logo step and done returns to manage`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("live")
        vm.load(domain_id)
        runCurrent()

        vm.replace_logo()
        assertEquals(BimiStep.logo, vm.state.value.step)
        assertTrue(vm.state.value.replacing)

        vm.go_to_manage()
        assertEquals(BimiStep.manage, vm.state.value.step)
        assertFalse(vm.state.value.replacing)
    }

    @Test
    fun `a failed load can be retried`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } throws ApiError.NetworkError andThen view("live")
        vm.load(domain_id)
        runCurrent()
        assertTrue(vm.state.value.load_failed)
        assertEquals(BimiStep.loading, vm.state.value.step)

        vm.retry_load()
        runCurrent()
        assertFalse(vm.state.value.load_failed)
        assertEquals(BimiStep.manage, vm.state.value.step)
    }

    @Test
    fun `publishing on an inactive domain is blocked without a request`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("draft", domain_active = false)
        vm.load(domain_id)
        runCurrent()

        vm.publish()
        runCurrent()

        assertEquals(BimiErrorKind.domain_not_active, vm.state.value.error)
        coVerify(exactly = 0) { bimi_api.publish(any()) }
    }

    @Test
    fun `a successful publish opens the manage view`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("draft")
        coEvery { bimi_api.publish(domain_id) } returns view("pending")
        vm.load(domain_id)
        runCurrent()

        vm.publish()
        runCurrent()

        assertEquals(BimiStep.manage, vm.state.value.step)
        assertEquals(BimiState.pending, vm.state.value.bimi_state)
        assertFalse(vm.state.value.publishing)
    }

    @Test
    fun `server error codes map to their messages`() {
        assertEquals(
            BimiErrorKind.logo_required,
            bimi_error_kind(ApiError.ValidationError(listOf("x"), "BIMI_LOGO_REQUIRED")),
        )
        assertEquals(
            BimiErrorKind.logo_required,
            bimi_error_kind(ApiError.Conflict("x", "BIMI_LOGO_REQUIRED")),
        )
        assertEquals(
            BimiErrorKind.domain_not_active,
            bimi_error_kind(ApiError.Conflict("x", "BIMI_DOMAIN_NOT_ACTIVE")),
        )
        assertEquals(BimiErrorKind.file_too_large, bimi_error_kind(ApiError.AttachmentTooLarge()))
        assertEquals(
            BimiErrorKind.file_too_large,
            bimi_error_kind(ApiError.ValidationError(listOf("x"), "PAYLOAD_TOO_LARGE")),
        )
        assertEquals(BimiErrorKind.throttled, bimi_error_kind(ApiError.RateLimited(code = "RATE_LIMIT_EXCEEDED")))
        assertEquals(
            BimiErrorKind.generic,
            bimi_error_kind(ApiError.Conflict("x", "BIMI_RECORD_CONFLICT")),
        )
        assertEquals(BimiErrorKind.generic, bimi_error_kind(ApiError.NetworkError))
    }

    @Test
    fun `auto check waits while the turn off confirmation is open`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("pending")
        coEvery { bimi_api.check(domain_id) } returns view("pending")
        vm.load(domain_id)
        runCurrent()
        vm.set_screen_visible(true)

        vm.request_turn_off()
        assertTrue(vm.state.value.confirm_turn_off)
        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS * 2 + 1)
        runCurrent()
        coVerify(exactly = 0) { bimi_api.check(any()) }

        vm.dismiss_turn_off()
        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS + 1)
        runCurrent()
        coVerify(exactly = 1) { bimi_api.check(domain_id) }
        vm.set_screen_visible(false)
    }

    @Test
    fun `auto check does not run outside the manage step`() = runTest {
        coEvery { bimi_api.get_bimi(domain_id) } returns view("pending")
        vm.load(domain_id)
        runCurrent()
        vm.set_screen_visible(true)

        vm.replace_logo()
        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS * 3)
        runCurrent()
        coVerify(exactly = 0) { bimi_api.check(any()) }
    }

    @Test
    fun `a stale auto check response is dropped`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { bimi_api.get_bimi(domain_id) } returns view("pending")
        coEvery { bimi_api.check(domain_id) } coAnswers {
            gate.await()
            view("attention")
        }
        coEvery { bimi_api.turn_off(domain_id) } returns view("off")
        vm.load(domain_id)
        runCurrent()
        vm.set_screen_visible(true)

        advanceTimeBy(BIMI_AUTO_CHECK_INTERVAL_MS + 1)
        runCurrent()
        vm.request_turn_off()
        vm.turn_off()
        runCurrent()
        assertEquals(BimiState.off, vm.state.value.bimi_state)

        gate.complete(Unit)
        runCurrent()
        assertEquals(BimiState.off, vm.state.value.bimi_state)
        assertEquals(BimiStep.logo, vm.state.value.step)
        vm.set_screen_visible(false)
    }
}
