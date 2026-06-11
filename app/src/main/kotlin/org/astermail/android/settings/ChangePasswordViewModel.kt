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
import org.astermail.android.auth.AuthRepository

data class ChangePasswordUiState(
    val current_password: String = "",
    val new_password: String = "",
    val confirm_password: String = "",
    val show_current: Boolean = false,
    val show_new: Boolean = false,
    val show_confirm: Boolean = false,
    val is_submitting: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val auth_repository: AuthRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun set_current(value: String) {
        _state.value = _state.value.copy(current_password = value, error = null, success = false)
    }

    fun set_new(value: String) {
        _state.value = _state.value.copy(new_password = value, error = null, success = false)
    }

    fun set_confirm(value: String) {
        _state.value = _state.value.copy(confirm_password = value, error = null, success = false)
    }

    fun toggle_show_current() {
        _state.value = _state.value.copy(show_current = !_state.value.show_current)
    }

    fun toggle_show_new() {
        _state.value = _state.value.copy(show_new = !_state.value.show_new)
    }

    fun toggle_show_confirm() {
        _state.value = _state.value.copy(show_confirm = !_state.value.show_confirm)
    }

    fun submit() {
        val s = _state.value
        if (s.is_submitting) return

        if (s.current_password.isBlank()) {
            _state.value = s.copy(error = context.getString(R.string.enter_current_password))
            return
        }
        if (s.new_password.length < 8) {
            _state.value = s.copy(error = context.getString(R.string.new_password_min_length))
            return
        }
        if (s.new_password.length > 128) {
            _state.value = s.copy(error = context.getString(R.string.new_password_max_length))
            return
        }
        if (s.new_password != s.confirm_password) {
            _state.value = s.copy(error = context.getString(R.string.new_password_mismatch))
            return
        }
        if (s.new_password == s.current_password) {
            _state.value = s.copy(error = context.getString(R.string.new_password_must_differ))
            return
        }

        _state.value = s.copy(is_submitting = true, error = null, success = false)
        viewModelScope.launch {
            val result = auth_repository.change_password(s.current_password, s.new_password)
            result.onSuccess {
                _state.value = ChangePasswordUiState(success = true)
            }.onFailure { t ->
                _state.value = _state.value.copy(
                    is_submitting = false,
                    error = t.message ?: context.getString(R.string.failed_change_password),
                )
            }
        }
    }

    fun reset() {
        _state.value = ChangePasswordUiState()
    }
}
