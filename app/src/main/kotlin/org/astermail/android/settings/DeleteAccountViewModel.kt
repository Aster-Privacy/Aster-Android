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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.R
import org.astermail.android.api.totp.TotpApi
import org.astermail.android.auth.AuthRepository

data class DeleteAccountUiState(
    val password: String = "",
    val confirm_phrase: String = "",
    val totp_code: String = "",
    val totp_enabled: Boolean = false,
    val show_password: Boolean = false,
    val is_submitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

fun is_valid_totp_or_backup_code(code: String): Boolean {
    if (code.length == 6 && code.all { it.isDigit() }) return true
    val normalized = code.filter { it.isLetterOrDigit() }
    return normalized.length == 8 || normalized.length == 12
}

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val auth_repository: AuthRepository,
    private val totp_api: TotpApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(DeleteAccountUiState())
    val state: StateFlow<DeleteAccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { totp_api.status() }.onSuccess { status ->
                _state.value = _state.value.copy(totp_enabled = status.enabled)
            }
        }
    }

    fun set_password(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    fun set_confirm_phrase(value: String) {
        _state.value = _state.value.copy(confirm_phrase = value, error = null)
    }

    fun set_totp_code(value: String) {
        _state.value = _state.value.copy(
            totp_code = value.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(14),
            error = null,
        )
    }

    fun toggle_show_password() {
        _state.value = _state.value.copy(show_password = !_state.value.show_password)
    }

    fun submit() {
        val s = _state.value
        if (s.is_submitting) return

        val required_phrase = context.getString(R.string.delete_account_confirm_word)
        if (s.password.isBlank()) {
            _state.value = s.copy(error = context.getString(R.string.enter_current_password))
            return
        }
        if (s.confirm_phrase.trim() != required_phrase) {
            _state.value = s.copy(error = context.getString(R.string.delete_account_confirm_mismatch))
            return
        }
        val code = s.totp_code.trim()
        if (s.totp_enabled && !is_valid_totp_or_backup_code(code)) {
            _state.value = s.copy(error = context.getString(R.string.totp_code_required_error))
            return
        }

        _state.value = s.copy(is_submitting = true, error = null)
        viewModelScope.launch {
            val result = auth_repository.delete_account(s.password, code.takeIf { it.isNotBlank() })
            result.onSuccess {
                _state.value = DeleteAccountUiState(success = true)
            }.onFailure { t ->
                _state.value = _state.value.copy(
                    is_submitting = false,
                    error = org.astermail.android.api.user_facing_error(t, context.getString(R.string.failed_delete_account)),
                )
            }
        }
    }

    fun reset() {
        _state.value = DeleteAccountUiState()
    }
}
