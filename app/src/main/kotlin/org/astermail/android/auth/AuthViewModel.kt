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

    private val _recovery_mnemonic = MutableStateFlow<String?>(null)
    val recovery_mnemonic: StateFlow<String?> = _recovery_mnemonic.asStateFlow()

    val is_signed_in: StateFlow<Boolean> = repository.is_signed_in

    fun submit_login(email: String, password: String, captcha_token: String? = null) {
        if (_ui_state.value == AuthUiState.Loading) return
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
                onFailure = { AuthUiState.Error(map_error(it)) },
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
                onFailure = { AuthUiState.Error(map_error(it)) },
            )
        }
    }

    fun submit_register(email: String, password: String, confirm_password: String, captcha_token: String? = null) {
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
            val result = repository.register(trimmed, password, captcha_token)
            result.fold(
                onSuccess = { success ->
                    _recovery_mnemonic.value = success.recovery.mnemonic
                    success.recovery.bytes.fill(0)
                    _ui_state.value = AuthUiState.Success
                },
                onFailure = { t ->
                    _ui_state.value = AuthUiState.Error(map_error(t))
                },
            )
        }
    }

    fun consume_recovery_mnemonic() {
        _recovery_mnemonic.value = null
    }

    fun reset_state() {
        _ui_state.value = AuthUiState.Idle
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
        is ApiError.ValidationError -> t.messages.joinToString(", ").ifBlank { ctx.getString(R.string.error_invalid_request) }
        is ApiError.UnknownError -> t.detail
        is java.net.UnknownHostException -> ctx.getString(R.string.error_no_connection)
        is java.net.ConnectException -> ctx.getString(R.string.error_no_connection)
        is java.net.SocketTimeoutException -> ctx.getString(R.string.error_timeout)
        is javax.net.ssl.SSLException -> ctx.getString(R.string.error_ssl)
        else -> ctx.getString(R.string.error_generic)
    }
}
