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

package org.astermail.android.auth

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.recovery.InitiateEmailRecoveryResponse
import org.astermail.android.api.recovery.RecoveryApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var recovery_api: RecoveryApi
    private lateinit var vm: RecoveryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        recovery_api = mockk(relaxed = true)
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        vm = RecoveryViewModel(recovery_api)
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has correct defaults`() {
        val state = vm.state.value
        assertEquals(RecoveryStep.email, state.step)
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertEquals("", state.processing_status)
        assertTrue(state.new_codes.isEmpty())
    }

    @Test
    fun `send_recovery_email sets loading then transitions to email_sent`() = runTest {
        coEvery { recovery_api.initiate_email(any()) } returns
            InitiateEmailRecoveryResponse(success = true)

        vm.send_recovery_email("User@AsterMail.org")
        assertTrue(vm.state.value.is_loading)
        assertNull(vm.state.value.error)

        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(RecoveryStep.email_sent, state.step)
        assertFalse(state.is_loading)
        assertNull(state.error)
    }

    @Test
    fun `send_recovery_email trims and lowercases email`() = runTest {
        coEvery { recovery_api.initiate_email(any()) } returns
            InitiateEmailRecoveryResponse(success = true)

        vm.send_recovery_email("  User@AsterMail.org  ")
        advanceUntilIdle()

        coVerify {
            recovery_api.initiate_email(match { it.email == "user@astermail.org" })
        }
    }

    @Test
    fun `send_recovery_email api failure sets error`() = runTest {
        coEvery { recovery_api.initiate_email(any()) } throws RuntimeException("server down")

        vm.send_recovery_email("user@astermail.org")
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(RecoveryStep.email, state.step)
        assertFalse(state.is_loading)
        assertEquals("server down", state.error)
    }

    @Test
    fun `send_recovery_email null message gives fallback error`() = runTest {
        coEvery { recovery_api.initiate_email(any()) } throws object : Throwable(null as String?) {}

        vm.send_recovery_email("user@astermail.org")
        advanceUntilIdle()

        assertEquals("failed to send recovery email", vm.state.value.error)
    }

    @Test
    fun `go_to_code_step transitions to code and clears error`() {
        vm.go_to_code_step()

        val state = vm.state.value
        assertEquals(RecoveryStep.code, state.step)
        assertNull(state.error)
    }

    @Test
    fun `verify_code rejects code without aster prefix`() {
        vm.verify_code("WRONG-ABCDE-FGHI-JKL")

        val state = vm.state.value
        assertEquals("Invalid recovery code format", state.error)
        assertFalse(state.is_loading)
    }

    @Test
    fun `verify_code rejects code with wrong length too short`() {
        vm.verify_code("ASTER-AB")

        assertEquals("Invalid recovery code format", vm.state.value.error)
    }

    @Test
    fun `verify_code accepts valid 20 char format and sets loading`() = runTest {
        coEvery { recovery_api.initiate(any()) } throws RuntimeException("test")

        vm.verify_code("ASTER-ABCD-EFGH-IJKL")
        assertTrue(vm.state.value.is_loading)
        advanceUntilIdle()
    }

    @Test
    fun `verify_code trims and uppercases input`() = runTest {
        coEvery { recovery_api.initiate(any()) } throws RuntimeException("test")

        vm.verify_code("  aster-abcd-efgh-ijkl  ")
        assertTrue(vm.state.value.is_loading)
        advanceUntilIdle()
    }

    @Test
    fun `verify_code api failure sets error`() = runTest {
        coEvery { recovery_api.initiate(any()) } throws RuntimeException("invalid code")

        vm.verify_code("ASTER-ABCD-EFGH-IJKL")
        Thread.sleep(500)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.is_loading)
        assertTrue(state.error != null)
    }

    @Test
    fun `verify_code null throwable message gives fallback`() = runTest {
        coEvery { recovery_api.initiate(any()) } throws object : Throwable(null as String?) {}

        vm.verify_code("ASTER-ABCD-EFGH-IJKL")
        Thread.sleep(500)
        advanceUntilIdle()
        Thread.sleep(100)
        advanceUntilIdle()

        assertEquals("invalid recovery code", vm.state.value.error)
    }

    @Test
    fun `verify_code 19 chars rejected`() {
        vm.verify_code("ASTER-ABCD-EFG-IJKL")
        assertEquals("Invalid recovery code format", vm.state.value.error)
    }

    @Test
    fun `verify_code 21 chars rejected`() {
        vm.verify_code("ASTER-ABCDE-FGHI-JKLM")
        assertEquals("Invalid recovery code format", vm.state.value.error)
    }

    @Test
    fun `submit_new_password rejects short password`() {
        vm.submit_new_password("short", "short")
        assertEquals("Password must be at least 12 characters", vm.state.value.error)
    }

    @Test
    fun `submit_new_password rejects mismatched passwords`() {
        vm.submit_new_password("password12345!", "password12345?")
        assertEquals("Passwords do not match", vm.state.value.error)
    }

    @Test
    fun `submit_new_password does nothing without recovery_token`() {
        vm.submit_new_password("password12345!", "password12345!")
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.is_loading)
    }

    @Test
    fun `submit_new_password 11 chars rejected`() {
        vm.submit_new_password("12345678901", "12345678901")
        assertEquals("Password must be at least 12 characters", vm.state.value.error)
    }

    @Test
    fun `go_to_success transitions to success and clears codes`() {
        vm.go_to_success()

        val state = vm.state.value
        assertEquals(RecoveryStep.success, state.step)
        assertTrue(state.new_codes.isEmpty())
    }

    @Test
    fun `clear_error clears error while preserving step`() = runTest {
        coEvery { recovery_api.initiate_email(any()) } throws RuntimeException("boom")

        vm.send_recovery_email("test@test.com")
        advanceUntilIdle()
        assertEquals("boom", vm.state.value.error)

        vm.clear_error()

        assertNull(vm.state.value.error)
        assertEquals(RecoveryStep.email, vm.state.value.step)
    }

    @Test
    fun `go_back from code goes to email`() {
        vm.go_to_code_step()
        assertEquals(RecoveryStep.code, vm.state.value.step)

        vm.go_back()
        assertEquals(RecoveryStep.email, vm.state.value.step)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `go_back from email_sent goes to email`() = runTest {
        coEvery { recovery_api.initiate_email(any()) } returns
            InitiateEmailRecoveryResponse(success = true)

        vm.send_recovery_email("test@test.com")
        advanceUntilIdle()
        assertEquals(RecoveryStep.email_sent, vm.state.value.step)

        vm.go_back()
        assertEquals(RecoveryStep.email, vm.state.value.step)
    }

    @Test
    fun `go_back from email does nothing`() {
        assertEquals(RecoveryStep.email, vm.state.value.step)
        vm.go_back()
        assertEquals(RecoveryStep.email, vm.state.value.step)
    }

    @Test
    fun `go_back from success does nothing`() {
        vm.go_to_success()
        vm.go_back()
        assertEquals(RecoveryStep.success, vm.state.value.step)
    }

    @Test
    fun `recovery_step enum has all expected values`() {
        val steps = RecoveryStep.entries
        assertEquals(7, steps.size)
        assertTrue(steps.contains(RecoveryStep.email))
        assertTrue(steps.contains(RecoveryStep.email_sent))
        assertTrue(steps.contains(RecoveryStep.code))
        assertTrue(steps.contains(RecoveryStep.password))
        assertTrue(steps.contains(RecoveryStep.processing))
        assertTrue(steps.contains(RecoveryStep.new_codes))
        assertTrue(steps.contains(RecoveryStep.success))
    }
}
