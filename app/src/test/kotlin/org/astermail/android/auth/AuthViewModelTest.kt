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

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.ApiError
import org.astermail.android.crypto.RecoveryKeyResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: AuthRepository
    private lateinit var vm: AuthViewModel

    private val fake_signed_in = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true) {
            io.mockk.every { is_signed_in } returns fake_signed_in
        }
        vm = AuthViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is idle`() {
        assertEquals(AuthUiState.Idle, vm.ui_state.value)
        assertNull(vm.recovery_mnemonic.value)
    }

    @Test
    fun `submit_login transitions to loading then success`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success(Unit)

        vm.submit_login("user@astermail.org", "password123!")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        advanceUntilIdle()

        assertEquals(AuthUiState.Success, vm.ui_state.value)
        coVerify { repository.login("user@astermail.org", "password123!", null) }
    }

    @Test
    fun `submit_login with captcha token passes it through`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success(Unit)

        vm.submit_login("user@astermail.org", "pass", "captcha_abc")
        advanceUntilIdle()

        coVerify { repository.login("user@astermail.org", "pass", "captcha_abc") }
    }

    @Test
    fun `submit_login unauthorized error maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.UnauthorizedError)

        vm.submit_login("user@astermail.org", "wrong")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Invalid username or password", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login network error maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.NetworkError)

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Could not connect to the server. Check your internet connection.",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `submit_login server error maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ServerError(500))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Server error. Please try again later.", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login not found error maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.NotFoundError)

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Account not found", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login forbidden captcha error maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ForbiddenError("captcha verification failed"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Captcha verification failed. Please try again.",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `submit_login forbidden non_captcha error maps to access denied`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ForbiddenError("ip blocked"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Access denied", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login validation error joins messages`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ValidationError(listOf("field1 bad", "field2 bad")))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("field1 bad, field2 bad", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login unknown host exception maps to connection error`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(java.net.UnknownHostException("no such host"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Could not connect to the server. Check your internet connection.",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `submit_login connect exception maps to connection error`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(java.net.ConnectException("refused"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Could not connect to the server. Check your internet connection.",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `submit_login socket timeout maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(java.net.SocketTimeoutException("timed out"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Connection timed out. Please try again.", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login ssl exception maps correctly`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(javax.net.ssl.SSLException("handshake failed"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Secure connection failed. Please try again.", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login unknown error maps to generic message`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(IllegalStateException("random"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Something went wrong. Please try again.", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login ignores duplicate call while loading`() = runTest {
        coEvery { repository.login(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(Unit)
        }

        vm.submit_login("user@astermail.org", "pass")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        vm.submit_login("user@astermail.org", "pass2")

        advanceUntilIdle()

        coVerify(exactly = 1) { repository.login(any(), any(), any()) }
    }

    @Test
    fun `submit_register transitions to loading then success with mnemonic`() = runTest {
        val recovery = RecoveryKeyResult("word1 word2 word3", ByteArray(16) { 0x01 })
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(recovery))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        advanceUntilIdle()

        assertEquals(AuthUiState.Success, vm.ui_state.value)
        assertEquals("word1 word2 word3", vm.recovery_mnemonic.value)
    }

    @Test
    fun `submit_register trims email before validation`() = runTest {
        val recovery = RecoveryKeyResult("mnemonic", ByteArray(8))
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(recovery))

        vm.submit_register("  test@astermail.org  ", "password12345!", "password12345!")
        advanceUntilIdle()

        coVerify { repository.register("test@astermail.org", "password12345!", null) }
    }

    @Test
    fun `submit_register rejects invalid email without at sign`() = runTest {
        vm.submit_register("noemailhere", "password12345!", "password12345!")

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("enter a valid email", (state as AuthUiState.Error).message)
        coVerify(exactly = 0) { repository.register(any(), any(), any()) }
    }

    @Test
    fun `submit_register rejects email with at at start`() = runTest {
        vm.submit_register("@domain.com", "password12345!", "password12345!")

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("enter a valid email", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_register rejects email with at at end`() = runTest {
        vm.submit_register("user@", "password12345!", "password12345!")

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("enter a valid email", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_register rejects email without dot in domain`() = runTest {
        vm.submit_register("user@localhost", "password12345!", "password12345!")

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("enter a valid email", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_register rejects password under 12 characters`() = runTest {
        vm.submit_register("test@astermail.org", "short", "short")

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("password must be at least 12 characters", (state as AuthUiState.Error).message)
        coVerify(exactly = 0) { repository.register(any(), any(), any()) }
    }

    @Test
    fun `submit_register accepts password exactly 12 characters`() = runTest {
        val recovery = RecoveryKeyResult("words", ByteArray(8))
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(recovery))

        vm.submit_register("test@astermail.org", "123456789012", "123456789012")
        advanceUntilIdle()

        assertEquals(AuthUiState.Success, vm.ui_state.value)
    }

    @Test
    fun `submit_register rejects mismatched passwords`() = runTest {
        vm.submit_register("test@astermail.org", "password12345!", "password12345?")

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("passwords do not match", (state as AuthUiState.Error).message)
        coVerify(exactly = 0) { repository.register(any(), any(), any()) }
    }

    @Test
    fun `submit_register api failure maps error`() = runTest {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.failure(ApiError.UnknownError("email taken"))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("email taken", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_register ignores duplicate call while loading`() = runTest {
        coEvery { repository.register(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(RegisterSuccess(RecoveryKeyResult("m", ByteArray(4))))
        }

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.register(any(), any(), any()) }
    }

    @Test
    fun `submit_register with captcha token passes it through`() = runTest {
        val recovery = RecoveryKeyResult("m", ByteArray(4))
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(recovery))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!", "tok123")
        advanceUntilIdle()

        coVerify { repository.register("test@astermail.org", "password12345!", "tok123") }
    }

    @Test
    fun `consume_recovery_mnemonic clears the mnemonic`() = runTest {
        val recovery = RecoveryKeyResult("secret words", ByteArray(8))
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(recovery))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        advanceUntilIdle()
        assertEquals("secret words", vm.recovery_mnemonic.value)

        vm.consume_recovery_mnemonic()

        assertNull(vm.recovery_mnemonic.value)
    }

    @Test
    fun `reset_state returns to idle`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success(Unit)

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()
        assertEquals(AuthUiState.Success, vm.ui_state.value)

        vm.reset_state()

        assertEquals(AuthUiState.Idle, vm.ui_state.value)
    }

    @Test
    fun `reset_state from error returns to idle`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.UnauthorizedError)

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()
        assertTrue(vm.ui_state.value is AuthUiState.Error)

        vm.reset_state()

        assertEquals(AuthUiState.Idle, vm.ui_state.value)
    }

    @Test
    fun `is_signed_in reflects repository state`() {
        assertEquals(false, vm.is_signed_in.value)
        fake_signed_in.value = true
        assertEquals(true, vm.is_signed_in.value)
    }

    @Test
    fun `submit_login unknown error detail is surfaced`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.UnknownError("custom detail"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("custom detail", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login empty validation messages gives fallback`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ValidationError(emptyList()))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Invalid request", (state as AuthUiState.Error).message)
    }
}
