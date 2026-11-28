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

package org.astermail.android.survey

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.api.ApiError
import org.astermail.android.api.found_us.FoundUsActionResponse
import org.astermail.android.api.found_us.FoundUsAnswer
import org.astermail.android.api.found_us.FoundUsApi
import org.astermail.android.api.found_us.FoundUsStatusResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private val zero_width = Char(0x200B).toString()

private val byte_order_mark = Char(0xFEFF).toString()

@OptIn(ExperimentalCoroutinesApi::class)
class found_us_view_model_test {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var api: FoundUsApi
    private lateinit var vm: FoundUsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        api = mockk(relaxed = true)
        coEvery { api.get_status() } returns FoundUsStatusResponse(eligible = true)
        coEvery { api.finish(any()) } returns FoundUsActionResponse(ok = true)
        coEvery { api.snooze() } returns FoundUsActionResponse(ok = true)
        vm = FoundUsViewModel(api)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun next_outcome(): FoundUsOutcome? = withTimeoutOrNull(1_000) { vm.outcomes.first() }

    @Test
    fun `validation asks for a pick when nothing is selected`() {
        assertEquals(FoundUsValidation.Invalid(FoundUsFormError.PICK), validate_found_us_answer(null, ""))
        assertEquals(FoundUsValidation.Invalid(FoundUsFormError.PICK), validate_found_us_answer("unknown", ""))
    }

    @Test
    fun `validation asks for text when other has only hidden characters`() {
        assertEquals(
            FoundUsValidation.Invalid(FoundUsFormError.DETAIL),
            validate_found_us_answer("other", " $zero_width\t$byte_order_mark "),
        )
    }

    @Test
    fun `validation drops the detail for a listed option and cleans it for other`() {
        assertEquals(FoundUsValidation.Valid(FoundUsAnswer("forum")), validate_found_us_answer("forum", "typed"))
        assertEquals(
            FoundUsValidation.Valid(FoundUsAnswer("other", "a podcast")),
            validate_found_us_answer("other", "  a$zero_width   podcast \n"),
        )
    }

    @Test
    fun `shows the banner for an eligible account`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        assertTrue(vm.state.value.is_banner_visible)
        assertEquals("user-1", vm.state.value.eligible_account_id)
    }

    @Test
    fun `stays hidden when the server says not eligible`() = runTest(dispatcher) {
        coEvery { api.get_status() } returns FoundUsStatusResponse(eligible = false)
        vm.load("user-1")
        runCurrent()
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `stays hidden when the status request fails`() = runTest(dispatcher) {
        coEvery { api.get_status() } throws ApiError.NetworkError
        vm.load("user-1")
        runCurrent()
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `does nothing without a signed in account`() = runTest(dispatcher) {
        vm.load(null)
        runCurrent()
        assertFalse(vm.state.value.is_banner_visible)
        coVerify(exactly = 0) { api.get_status() }
    }

    @Test
    fun `ignores a late status response for a previous account`() = runTest(dispatcher) {
        val first = CompletableDeferred<FoundUsStatusResponse>()
        coEvery { api.get_status() } coAnswers { first.await() }
        vm.load("user-1")
        runCurrent()
        coEvery { api.get_status() } returns FoundUsStatusResponse(eligible = false)
        vm.load("user-2")
        runCurrent()
        first.complete(FoundUsStatusResponse(eligible = true))
        runCurrent()
        assertEquals("user-2", vm.state.value.account_id)
        assertNull(vm.state.value.eligible_account_id)
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `does not reload or reset for the same account`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        vm.remind_later()
        runCurrent()
        vm.load("user-1")
        runCurrent()
        assertTrue(vm.state.value.hidden)
        coVerify(exactly = 1) { api.get_status() }
    }

    @Test
    fun `switching accounts resets the hidden state`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        vm.dont_ask()
        runCurrent()
        vm.load("user-2")
        runCurrent()
        assertFalse(vm.state.value.hidden)
        assertTrue(vm.state.value.is_banner_visible)
    }

    @Test
    fun `dismiss switches the buttons without hiding the banner`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        vm.choose_dismissal()
        assertTrue(vm.state.value.choosing_dismissal)
        assertTrue(vm.state.value.is_banner_visible)
        coVerify(exactly = 0) { api.snooze() }
        coVerify(exactly = 0) { api.finish(any()) }
    }

    @Test
    fun `remind later hides at once and snoozes`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        vm.remind_later()
        assertFalse(vm.state.value.is_banner_visible)
        runCurrent()
        coVerify(exactly = 1) { api.snooze() }
    }

    @Test
    fun `dont ask hides at once and finishes with no answer`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        vm.dont_ask()
        assertFalse(vm.state.value.is_banner_visible)
        runCurrent()
        coVerify(exactly = 1) { api.finish(null) }
    }

    @Test
    fun `remind later stays hidden when the request fails`() = runTest(dispatcher) {
        coEvery { api.snooze() } throws ApiError.ServerError(500)
        vm.load("user-1")
        runCurrent()
        vm.remind_later()
        runCurrent()
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `submit with no pick returns the pick error and sends nothing`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        assertEquals(FoundUsFormError.PICK, vm.submit(null, ""))
        runCurrent()
        coVerify(exactly = 0) { api.finish(any()) }
    }

    @Test
    fun `submit with empty other text returns the detail error`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        assertEquals(FoundUsFormError.DETAIL, vm.submit("other", "   "))
        runCurrent()
        coVerify(exactly = 0) { api.finish(any()) }
    }

    @Test
    fun `submit sends the cleaned answer and thanks the user`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        assertNull(vm.submit("other", "  a ${zero_width}podcast "))
        assertTrue(vm.state.value.is_sending)
        runCurrent()
        coVerify(exactly = 1) { api.finish(FoundUsAnswer("other", "a podcast")) }
        assertEquals(FoundUsOutcome.THANKS, next_outcome())
        assertFalse(vm.state.value.is_sending)
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `a second tap while sending is ignored`() = runTest(dispatcher) {
        val pending = CompletableDeferred<FoundUsActionResponse>()
        coEvery { api.finish(any()) } coAnswers { pending.await() }
        vm.load("user-1")
        runCurrent()
        vm.submit("reddit", "")
        vm.submit("reddit", "")
        vm.skip()
        runCurrent()
        pending.complete(FoundUsActionResponse(ok = true))
        runCurrent()
        coVerify(exactly = 1) { api.finish(any()) }
    }

    @Test
    fun `a failed send keeps the window open and reports the failure`() = runTest(dispatcher) {
        coEvery { api.finish(any()) } throws ApiError.NetworkError
        vm.load("user-1")
        runCurrent()
        vm.submit("friend", "")
        runCurrent()
        assertEquals(FoundUsOutcome.SEND_FAILED, next_outcome())
        assertFalse(vm.state.value.is_sending)
        assertTrue(vm.state.value.is_banner_visible)
    }

    @Test
    fun `a forbidden send closes quietly`() = runTest(dispatcher) {
        coEvery { api.finish(any()) } throws ApiError.ForbiddenError()
        vm.load("user-1")
        runCurrent()
        vm.submit("friend", "")
        runCurrent()
        assertEquals(FoundUsOutcome.CLOSED, next_outcome())
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `skip finishes with no answer and closes`() = runTest(dispatcher) {
        vm.load("user-1")
        runCurrent()
        vm.skip()
        runCurrent()
        coVerify(exactly = 1) { api.finish(null) }
        assertEquals(FoundUsOutcome.CLOSED, next_outcome())
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `skip closes even when the request fails`() = runTest(dispatcher) {
        coEvery { api.finish(any()) } throws ApiError.ServerError(503)
        vm.load("user-1")
        runCurrent()
        vm.skip()
        runCurrent()
        assertEquals(FoundUsOutcome.CLOSED, next_outcome())
        assertFalse(vm.state.value.is_banner_visible)
    }

    @Test
    fun `a send that finishes after an account switch emits nothing`() = runTest(dispatcher) {
        val pending = CompletableDeferred<FoundUsActionResponse>()
        coEvery { api.finish(any()) } coAnswers { pending.await() }
        vm.load("user-1")
        runCurrent()
        vm.submit("reddit", "")
        runCurrent()
        vm.load("user-2")
        runCurrent()
        pending.complete(FoundUsActionResponse(ok = true))
        runCurrent()
        assertNull(next_outcome())
        assertFalse(vm.state.value.hidden)
    }

    @Test
    fun `submit does nothing when the account is not eligible`() = runTest(dispatcher) {
        coEvery { api.get_status() } returns FoundUsStatusResponse(eligible = false)
        vm.load("user-1")
        runCurrent()
        assertNull(vm.submit("reddit", ""))
        vm.skip()
        runCurrent()
        coVerify(exactly = 0) { api.finish(any()) }
    }
}
