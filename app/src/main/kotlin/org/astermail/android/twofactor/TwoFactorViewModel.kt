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

package org.astermail.android.twofactor

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.astermail.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.api.totp.TotpApi
import org.astermail.android.api.totp.TotpDisableRequest
import org.astermail.android.api.totp.TotpRegenerateBackupCodesRequest
import org.astermail.android.api.totp.TotpSetupVerifyRequest
import org.astermail.android.auth.AuthRepository

enum class TwoFactorMode {
    Idle,
    Setup,
    SetupComplete,
    Disable,
    Regenerate,
    RegenerateComplete,
}

data class TwoFactorUiState(
    val is_loading: Boolean = false,
    val is_busy: Boolean = false,
    val error: String? = null,
    val enabled: Boolean = false,
    val backup_codes_remaining: Int = 0,
    val verified_at: String? = null,
    val mode: TwoFactorMode = TwoFactorMode.Idle,
    val setup_secret: String? = null,
    val setup_otpauth_uri: String? = null,
    val setup_token: String? = null,
    val code_input: String = "",
    val password_input: String = "",
    val backup_codes: List<String> = emptyList(),
)

@HiltViewModel
class TwoFactorViewModel @Inject constructor(
    private val totp_api: TotpApi,
    private val auth_repository: AuthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(TwoFactorUiState())
    val state: StateFlow<TwoFactorUiState> = _state.asStateFlow()

    fun load_status() {
        viewModelScope.launch {
            _state.value = _state.value.copy(is_loading = true, error = null)
            val outcome = runCatching { totp_api.status() }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_loading = false,
                        enabled = it.enabled,
                        backup_codes_remaining = it.backup_codes_remaining,
                        verified_at = it.verified_at,
                    )
                },
                onFailure = { _state.value.copy(is_loading = false, error = readable(it)) },
            )
        }
    }

    fun start_setup() {
        if (_state.value.is_busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(is_busy = true, error = null)
            val outcome = runCatching { totp_api.initiate_setup() }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_busy = false,
                        mode = TwoFactorMode.Setup,
                        setup_secret = it.secret,
                        setup_otpauth_uri = it.otpauth_uri,
                        setup_token = it.setup_token,
                        code_input = "",
                    )
                },
                onFailure = { _state.value.copy(is_busy = false, error = readable(it)) },
            )
        }
    }

    fun verify_setup() {
        val s = _state.value
        if (s.is_busy) return
        val token = s.setup_token ?: return
        if (s.code_input.length < 6) {
            _state.value = s.copy(error = context.getString(R.string.enter_six_digit_code))
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(is_busy = true, error = null)
            val outcome = runCatching {
                totp_api.verify_setup(TotpSetupVerifyRequest(code = s.code_input.trim(), setup_token = token))
            }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_busy = false,
                        mode = TwoFactorMode.SetupComplete,
                        enabled = true,
                        backup_codes = it.backup_codes,
                        backup_codes_remaining = it.backup_codes.size,
                        setup_secret = null,
                        setup_otpauth_uri = null,
                        setup_token = null,
                        code_input = "",
                    )
                },
                onFailure = { _state.value.copy(is_busy = false, error = readable(it)) },
            )
        }
    }

    fun start_disable() {
        _state.value = _state.value.copy(
            mode = TwoFactorMode.Disable,
            code_input = "",
            password_input = "",
            error = null,
        )
    }

    fun confirm_disable() {
        val s = _state.value
        if (s.is_busy) return
        if (s.password_input.isBlank()) {
            _state.value = s.copy(error = context.getString(R.string.enter_account_password))
            return
        }
        if (s.code_input.length < 6) {
            _state.value = s.copy(error = context.getString(R.string.enter_six_digit_code))
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(is_busy = true, error = null)
            val derived = auth_repository.derive_password_hash_b64(s.password_input)
            if (derived == null) {
                _state.value = _state.value.copy(
                    is_busy = false,
                    error = context.getString(R.string.session_unavailable_sign_in_again),
                )
                return@launch
            }
            val outcome = runCatching {
                totp_api.disable(
                    TotpDisableRequest(code = s.code_input.trim(), password_hash = derived),
                )
            }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_busy = false,
                        enabled = false,
                        backup_codes_remaining = 0,
                        verified_at = null,
                        mode = TwoFactorMode.Idle,
                        code_input = "",
                        password_input = "",
                    )
                },
                onFailure = { _state.value.copy(is_busy = false, error = readable(it)) },
            )
        }
    }

    fun start_regenerate() {
        _state.value = _state.value.copy(
            mode = TwoFactorMode.Regenerate,
            code_input = "",
            error = null,
        )
    }

    fun confirm_regenerate() {
        val s = _state.value
        if (s.is_busy) return
        if (s.code_input.length < 6) {
            _state.value = s.copy(error = context.getString(R.string.enter_six_digit_code))
            return
        }
        viewModelScope.launch {
            _state.value = s.copy(is_busy = true, error = null)
            val outcome = runCatching {
                totp_api.regenerate_backup_codes(
                    TotpRegenerateBackupCodesRequest(code = s.code_input.trim()),
                )
            }
            _state.value = outcome.fold(
                onSuccess = {
                    _state.value.copy(
                        is_busy = false,
                        mode = TwoFactorMode.RegenerateComplete,
                        backup_codes = it.backup_codes,
                        backup_codes_remaining = it.backup_codes.size,
                        code_input = "",
                    )
                },
                onFailure = { _state.value.copy(is_busy = false, error = readable(it)) },
            )
        }
    }

    fun update_code(value: String) {
        val digits = value.filter { it.isDigit() }.take(8)
        _state.value = _state.value.copy(code_input = digits, error = null)
    }

    fun update_password(value: String) {
        _state.value = _state.value.copy(password_input = value, error = null)
    }

    fun cancel_action() {
        _state.value = _state.value.copy(
            mode = TwoFactorMode.Idle,
            error = null,
            setup_secret = null,
            setup_otpauth_uri = null,
            setup_token = null,
            code_input = "",
            password_input = "",
            backup_codes = emptyList(),
        )
    }

    fun acknowledge_backup_codes() {
        _state.value = _state.value.copy(
            mode = TwoFactorMode.Idle,
            backup_codes = emptyList(),
        )
        load_status()
    }

    private fun readable(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: context.getString(R.string.something_went_wrong)
}
