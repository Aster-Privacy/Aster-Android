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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.recovery_email.RecoveryEmailApiImpl
import org.astermail.android.api.recovery_email.RecoveryEmailError

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data class Error(val message: String) : AuthUiState
    data object Success : AuthUiState
    data class TotpChallenge(val challenge: org.astermail.android.auth.TotpChallenge) : AuthUiState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    application: Application,
    private val repository: AuthRepository,
) : AndroidViewModel(application) {

    private val ctx get() = getApplication<Application>()

    private val _ui_state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val ui_state: StateFlow<AuthUiState> = _ui_state.asStateFlow()

    private val _recovery_codes = MutableStateFlow<List<String>?>(null)
    val recovery_codes: StateFlow<List<String>?> = _recovery_codes.asStateFlow()

    private val _recovery_backup_failed = MutableStateFlow(false)
    val recovery_backup_failed: StateFlow<Boolean> = _recovery_backup_failed.asStateFlow()

    private val _is_retrying_recovery_backup = MutableStateFlow(false)
    val is_retrying_recovery_backup: StateFlow<Boolean> = _is_retrying_recovery_backup.asStateFlow()

    private val _recovery_email_error = MutableStateFlow<String?>(null)
    val recovery_email_error: StateFlow<String?> = _recovery_email_error.asStateFlow()

    private val _is_saving_recovery_email = MutableStateFlow(false)
    val is_saving_recovery_email: StateFlow<Boolean> = _is_saving_recovery_email.asStateFlow()

    val is_signed_in: StateFlow<Boolean> = repository.is_signed_in

    fun submit_login(email: String, password: String, captcha_token: String? = null) {
        if (_ui_state.value == AuthUiState.Loading) return
        if (!is_supported_sign_in_domain(email)) {
            _ui_state.value = AuthUiState.Error(
                ctx.getString(R.string.error_sign_in_domain_unsupported),
            )
            return
        }
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(25_000L) {
                    repository.login(email, password, captcha_token).getOrThrow()
                }
            }
            _ui_state.value = result.fold(
                onSuccess = { outcome ->
                    when (outcome) {
                        is LoginOutcome.Success -> AuthUiState.Success
                        is LoginOutcome.NeedsTotp -> AuthUiState.TotpChallenge(outcome.challenge)
                    }
                },
                onFailure = { failure_state(it) },
            )
        }
    }

    fun submit_totp(code: String, challenge: org.astermail.android.auth.TotpChallenge, trust_device: Boolean = false) {
        if (_ui_state.value == AuthUiState.Loading) return
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                kotlinx.coroutines.withTimeout(25_000L) {
                    repository.verify_totp(code, challenge, trust_device).getOrThrow()
                }
            }
            _ui_state.value = result.fold(
                onSuccess = { AuthUiState.Success },
                onFailure = { failure_state(it) },
            )
        }
    }

    private fun failure_state(cause: Throwable): AuthUiState {
        if (cause is kotlinx.coroutines.TimeoutCancellationException && repository.is_signed_in.value) {
            return AuthUiState.Success
        }
        return AuthUiState.Error(map_error(cause))
    }

    fun submit_register(
        email: String,
        password: String,
        confirm_password: String,
        captcha_token: String? = null,
        remember_me: Boolean = true,
    ) {
        if (_ui_state.value == AuthUiState.Loading) return
        val trimmed = email.trim()
        if (!is_valid_email(trimmed)) {
            _ui_state.value = AuthUiState.Error(ctx.getString(R.string.error_invalid_email))
            return
        }
        if (password.length < 12) {
            _ui_state.value = AuthUiState.Error(ctx.getString(R.string.error_password_min_length))
            return
        }
        if (password != confirm_password) {
            _ui_state.value = AuthUiState.Error(ctx.getString(R.string.error_passwords_no_match))
            return
        }
        _ui_state.value = AuthUiState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.register(trimmed, password, captcha_token, remember_me)
            result.fold(
                onSuccess = { success ->
                    _recovery_codes.value = success.recovery_codes
                    _recovery_backup_failed.value = !success.recovery_backup_saved
                    _ui_state.value = AuthUiState.Success
                },
                onFailure = { t ->
                    _ui_state.value = AuthUiState.Error(map_error(t))
                },
            )
        }
    }

    fun consume_recovery_codes() {
        _recovery_codes.value = null
    }

    fun retry_recovery_backup() {
        if (_is_retrying_recovery_backup.value) return
        _is_retrying_recovery_backup.value = true
        viewModelScope.launch {
            val saved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { repository.retry_recovery_backup() }.getOrDefault(false)
            }
            _recovery_backup_failed.value = !saved
            _is_retrying_recovery_backup.value = false
        }
    }

    fun save_recovery_email(email: String, on_saved: () -> Unit) {
        if (_is_saving_recovery_email.value) return
        _recovery_email_error.value = null
        _is_saving_recovery_email.value = true
        viewModelScope.launch {
            val result = kotlinx.coroutines.withContext(Dispatchers.IO) {
                repository.save_recovery_email(email)
            }
            _is_saving_recovery_email.value = false
            result.fold(
                onSuccess = { on_saved() },
                onFailure = { t -> _recovery_email_error.value = recovery_email_error_message(t) },
            )
        }
    }

    fun clear_recovery_email_error() {
        _recovery_email_error.value = null
    }

    private fun recovery_email_error_message(t: Throwable): String {
        (t as? RecoveryEmailError)?.let { error ->
            return when (error.code) {
                RecoveryEmailApiImpl.STEP_UP_REQUIRED ->
                    ctx.getString(R.string.recovery_step_up_description)
                RecoveryEmailApiImpl.TOTP_REQUIRED ->
                    ctx.getString(R.string.totp_code_required_error)
                RecoveryEmailApiImpl.INVALID_INPUT ->
                    ctx.getString(R.string.error_invalid_email)
                RecoveryEmailApiImpl.EMAIL_SEND_FAILED ->
                    ctx.getString(R.string.error_send_recovery)
                else -> map_error(t)
            }
        }
        return when ((t as? ApiError.UnknownError)?.detail) {
            RecoveryEmailApiImpl.RECOVERY_EMAIL_IN_USE ->
                ctx.getString(R.string.recovery_email_already_in_use)
            RecoveryEmailApiImpl.RECOVERY_EMAIL_COOLDOWN ->
                ctx.getString(R.string.recovery_email_resend_cooldown)
            else -> map_error(t)
        }
    }

    fun reset_state() {
        _ui_state.value = AuthUiState.Idle
    }

    fun cancel_totp(challenge: org.astermail.android.auth.TotpChallenge): Boolean {
        if (_ui_state.value == AuthUiState.Loading) return false
        challenge.password_bytes.fill(0)
        challenge.password_hash_bytes.fill(0)
        _ui_state.value = AuthUiState.Idle
        return true
    }

    private fun is_supported_sign_in_domain(email: String): Boolean {
        val at_index = email.indexOf('@')

        if (at_index == -1) return true

        val domain = email.substring(at_index + 1).lowercase(java.util.Locale.ROOT).trimEnd('.')

        return domain == "astermail.org" || domain.endsWith(".astermail.org") ||
            domain == "aster.cx" || domain.endsWith(".aster.cx")
    }
    private fun is_valid_email(email: String): Boolean {
        val at = email.indexOf('@')
        if (at <= 0 || at == email.length - 1) return false
        val local = email.substring(0, at)
        val domain = email.substring(at + 1)
        if (local.isBlank() || domain.isBlank()) return false
        return domain.contains('.')
    }

    private fun map_error(t: Throwable): String = when (t) {
        is ApiError.UnauthorizedError -> ctx.getString(R.string.error_invalid_credentials)
        is ApiError.ForbiddenError -> if (t.detail.contains("captcha", ignoreCase = true)) {
            ctx.getString(R.string.error_captcha_failed)
        } else {
            ctx.getString(R.string.error_access_denied)
        }
        is ApiError.NotFoundError -> ctx.getString(R.string.error_account_not_found)
        is ApiError.NetworkError -> ctx.getString(R.string.error_no_connection)
        is ApiError.ServerError -> ctx.getString(R.string.error_server)
        is ApiError.ValidationError ->
            org.astermail.android.localized_api_error(ctx, t, ctx.getString(R.string.error_invalid_request))
        is ApiError.RateLimited -> ctx.getString(R.string.error_too_many_attempts)
        is ApiError.UnknownError -> ctx.getString(R.string.error_generic)
        is java.net.UnknownHostException -> ctx.getString(R.string.error_no_connection)
        is java.net.ConnectException -> ctx.getString(R.string.error_no_connection)
        is java.net.SocketTimeoutException -> ctx.getString(R.string.error_timeout)
        is kotlinx.coroutines.TimeoutCancellationException -> ctx.getString(R.string.error_timeout)
        is javax.net.ssl.SSLException -> ctx.getString(R.string.error_ssl)
        else -> ctx.getString(R.string.error_generic)
    }
}
