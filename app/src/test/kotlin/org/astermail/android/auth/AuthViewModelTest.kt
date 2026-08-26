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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var application: android.app.Application
    private lateinit var repository: AuthRepository
    private lateinit var vm: AuthViewModel

    private val fake_signed_in = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        io.mockk.mockkStatic(Dispatchers::class)
        io.mockk.every { Dispatchers.IO } returns dispatcher
        application = mockk(relaxed = true)
        stub_error_strings(application)
        repository = mockk(relaxed = true) {
            io.mockk.every { is_signed_in } returns fake_signed_in
        }
        vm = AuthViewModel(application, repository)
    }

    @After
    fun teardown() {
        io.mockk.unmockkStatic(Dispatchers::class)
        Dispatchers.resetMain()
    }

    private fun clear_dispatcher_records() {
        io.mockk.clearStaticMockk(
            Dispatchers::class,
            answers = false,
            recordedCalls = true,
            childMocks = false,
        )
    }

    private fun stub_error_strings(app: android.app.Application) {
        io.mockk.every { app.getString(org.astermail.android.R.string.error_invalid_email) } returns
            "enter a valid email"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_password_min_length) } returns
            "password must be at least 12 characters"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_passwords_no_match) } returns
            "passwords do not match"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_invalid_credentials) } returns
            "Invalid username or password"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_captcha_failed) } returns
            "Captcha verification failed. Please try again."
        io.mockk.every { app.getString(org.astermail.android.R.string.error_access_denied) } returns
            "Access denied"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_account_not_found) } returns
            "Account not found"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_no_connection) } returns
            "Could not connect to the server. Check your internet connection."
        io.mockk.every { app.getString(org.astermail.android.R.string.error_server) } returns
            "Server error. Please try again later."
        io.mockk.every { app.getString(org.astermail.android.R.string.error_invalid_request) } returns
            "Invalid request"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_timeout) } returns
            "Connection timed out. Please try again."
        io.mockk.every { app.getString(org.astermail.android.R.string.error_ssl) } returns
            "Secure connection failed. Please try again."
        io.mockk.every { app.getString(org.astermail.android.R.string.error_generic) } returns
            "Something went wrong. Please try again."
        io.mockk.every { app.getString(org.astermail.android.R.string.recovery_step_up_description) } returns
            "confirm your password first"
        io.mockk.every { app.getString(org.astermail.android.R.string.totp_code_required_error) } returns
            "enter your authentication code"
        io.mockk.every { app.getString(org.astermail.android.R.string.error_send_recovery) } returns
            "could not send the recovery email"
        io.mockk.every { app.getString(org.astermail.android.R.string.recovery_email_already_in_use) } returns
            "that email is already in use"
        io.mockk.every { app.getString(org.astermail.android.R.string.recovery_email_resend_cooldown) } returns
            "wait before requesting another email"
    }

    private fun fake_totp_challenge(): org.astermail.android.auth.TotpChallenge =
        org.astermail.android.auth.TotpChallenge(
            pending_login_token = "pending",
            available_methods = listOf("totp"),
            password_hash_bytes = ByteArray(4) { 7 },
            password_bytes = ByteArray(4) { 9 },
            salt_bytes = ByteArray(4) { 3 },
            email = "user@astermail.org",
            remember_me = true,
        )

    @Test
    fun `initial state is idle`() {
        assertEquals(AuthUiState.Idle, vm.ui_state.value)
        assertNull(vm.recovery_codes.value)
    }

    @Test
    fun `submit_login transitions to loading then success`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success(LoginOutcome.Success)

        vm.submit_login("user@astermail.org", "password123!")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        advanceUntilIdle()

        assertEquals(AuthUiState.Success, vm.ui_state.value)
        coVerify { repository.login("user@astermail.org", "password123!", null) }
    }

    @Test
    fun `submit_login with captcha token passes it through`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success(LoginOutcome.Success)

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
    fun `submit_login validation error hides field level detail`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ValidationError(listOf("field1 bad", "field2 bad")))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Invalid request", (state as AuthUiState.Error).message)
    }

    @Test
    fun `submit_login validation error keeps a readable server message`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.ValidationError(listOf("Your account is not ready yet.")))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals("Your account is not ready yet.", (state as AuthUiState.Error).message)
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
            Result.success(LoginOutcome.Success)
        }

        vm.submit_login("user@astermail.org", "pass")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        vm.submit_login("user@astermail.org", "pass2")

        advanceUntilIdle()

        clear_dispatcher_records()
        coVerify(exactly = 1) { repository.login(any(), any(), any()) }
    }

    @Test
    fun `submit_register transitions to loading then success with codes`() = runTest {
        val codes = listOf("ASTER-AAAA-BBBB-CCCC", "ASTER-DDDD-EEEE-FFFF")
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(codes))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        advanceUntilIdle()

        assertEquals(AuthUiState.Success, vm.ui_state.value)
        assertEquals(codes, vm.recovery_codes.value)
    }

    @Test
    fun `submit_register trims email before validation`() = runTest {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(listOf("ASTER-AAAA-BBBB-CCCC")))

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
        io.mockk.unmockkStatic(Dispatchers::class)
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
        io.mockk.unmockkStatic(Dispatchers::class)
        coVerify(exactly = 0) { repository.register(any(), any(), any()) }
    }

    @Test
    fun `submit_register accepts password exactly 12 characters`() = runTest {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(listOf("ASTER-AAAA-BBBB-CCCC")))

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
        io.mockk.unmockkStatic(Dispatchers::class)
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
        assertEquals(
            "Something went wrong. Please try again.",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `submit_register ignores duplicate call while loading`() = runTest {
        coEvery { repository.register(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(RegisterSuccess(listOf("ASTER-AAAA-BBBB-CCCC")))
        }

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        advanceUntilIdle()

        clear_dispatcher_records()
        coVerify(exactly = 1) { repository.register(any(), any(), any()) }
    }

    @Test
    fun `submit_register with captcha token passes it through`() = runTest {
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(listOf("ASTER-AAAA-BBBB-CCCC")))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!", "tok123")
        advanceUntilIdle()

        coVerify { repository.register("test@astermail.org", "password12345!", "tok123") }
    }

    @Test
    fun `consume_recovery_codes clears the codes`() = runTest {
        val codes = listOf("ASTER-AAAA-BBBB-CCCC", "ASTER-DDDD-EEEE-FFFF")
        coEvery { repository.register(any(), any(), any()) } returns
            Result.success(RegisterSuccess(codes))

        vm.submit_register("test@astermail.org", "password12345!", "password12345!")
        advanceUntilIdle()
        assertEquals(codes, vm.recovery_codes.value)

        vm.consume_recovery_codes()

        assertNull(vm.recovery_codes.value)
    }

    @Test
    fun `reset_state returns to idle`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns Result.success(LoginOutcome.Success)

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
    fun `submit_login unknown error shows generic message not raw detail`() = runTest {
        coEvery { repository.login(any(), any(), any()) } returns
            Result.failure(ApiError.UnknownError("custom detail"))

        vm.submit_login("user@astermail.org", "pass")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Something went wrong. Please try again.",
            (state as AuthUiState.Error).message,
        )
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

    @Test
    fun `submit_login timeout maps to the timeout message`() = runTest {
        coEvery { repository.login(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(60_000L)
            Result.success(LoginOutcome.Success)
        }

        vm.submit_login("user@astermail.org", "password123!")
        advanceUntilIdle()

        val state = vm.ui_state.value
        assertTrue(state is AuthUiState.Error)
        assertEquals(
            "Connection timed out. Please try again.",
            (state as AuthUiState.Error).message,
        )
    }

    @Test
    fun `cancel_totp clears the challenge buffers when idle`() {
        val challenge = fake_totp_challenge()

        assertTrue(vm.cancel_totp(challenge))

        assertTrue(challenge.password_bytes.all { it == 0.toByte() })
        assertTrue(challenge.password_hash_bytes.all { it == 0.toByte() })
        assertEquals(AuthUiState.Idle, vm.ui_state.value)
    }

    @Test
    fun `cancel_totp refuses to wipe buffers while a verification is in flight`() = runTest {
        val challenge = fake_totp_challenge()
        coEvery { repository.verify_totp(any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5_000L)
            Result.success(Unit)
        }

        vm.submit_totp("123456", challenge)

        assertEquals(AuthUiState.Loading, vm.ui_state.value)
        assertEquals(false, vm.cancel_totp(challenge))
        assertTrue(challenge.password_bytes.any { it != 0.toByte() })
        assertTrue(challenge.password_hash_bytes.any { it != 0.toByte() })
        assertEquals(AuthUiState.Loading, vm.ui_state.value)

        advanceUntilIdle()
    }

    @Test
    fun `save_recovery_email maps step up required to a localized message`() = runTest {
        coEvery { repository.save_recovery_email(any()) } returns Result.failure(
            org.astermail.android.api.recovery_email.RecoveryEmailError(
                code = org.astermail.android.api.recovery_email.RecoveryEmailApiImpl.STEP_UP_REQUIRED,
                user_message = "Invalid request",
            ),
        )

        vm.save_recovery_email("backup@example.com") {}
        advanceUntilIdle()

        assertEquals("confirm your password first", vm.recovery_email_error.value)
        assertEquals(false, vm.is_saving_recovery_email.value)
    }

    @Test
    fun `save_recovery_email maps totp required to a localized message`() = runTest {
        coEvery { repository.save_recovery_email(any()) } returns Result.failure(
            org.astermail.android.api.recovery_email.RecoveryEmailError(
                code = org.astermail.android.api.recovery_email.RecoveryEmailApiImpl.TOTP_REQUIRED,
            ),
        )

        vm.save_recovery_email("backup@example.com") {}
        advanceUntilIdle()

        assertEquals("enter your authentication code", vm.recovery_email_error.value)
    }

    @Test
    fun `save_recovery_email maps the in use code to a localized message`() = runTest {
        coEvery { repository.save_recovery_email(any()) } returns Result.failure(
            ApiError.UnknownError(
                org.astermail.android.api.recovery_email.RecoveryEmailApiImpl.RECOVERY_EMAIL_IN_USE,
            ),
        )

        vm.save_recovery_email("backup@example.com") {}
        advanceUntilIdle()

        assertEquals("that email is already in use", vm.recovery_email_error.value)
    }

    @Test
    fun `save_recovery_email maps the cooldown code to a localized message`() = runTest {
        coEvery { repository.save_recovery_email(any()) } returns Result.failure(
            ApiError.UnknownError(
                org.astermail.android.api.recovery_email.RecoveryEmailApiImpl.RECOVERY_EMAIL_COOLDOWN,
            ),
        )

        vm.save_recovery_email("backup@example.com") {}
        advanceUntilIdle()

        assertEquals("wait before requesting another email", vm.recovery_email_error.value)
    }

    @Test
    fun `save_recovery_email never renders raw server text`() = runTest {
        coEvery { repository.save_recovery_email(any()) } returns Result.failure(
            org.astermail.android.api.recovery_email.RecoveryEmailError(
                code = "some_unmapped_code",
                user_message = "raw backend detail",
            ),
        )

        vm.save_recovery_email("backup@example.com") {}
        advanceUntilIdle()

        assertEquals("Something went wrong. Please try again.", vm.recovery_email_error.value)
    }
}
