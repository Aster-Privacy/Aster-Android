//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.ui.guard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.astermail.android.api.ApiError
import org.astermail.android.api.api_error_code
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.StepUpProof
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.GuardRepository
import org.astermail.android.guard.guard_held_notice
import org.astermail.android.guard.guard_signer
import org.astermail.android.guard.to_notice

enum class guard_rotate_notice {
    wrong_password,
    totp_required,
    wrong_group,
    canceled,
    key_lost,
    no_lock_screen,
    no_device,
    failed,
}

sealed interface guard_rotate_result {
    data object applied : guard_rotate_result
    data class held(val notice: guard_held_notice) : guard_rotate_result
}

data class guard_rotate_ui(
    val rescue_code: String = "",
    val confirm_input: String = "",
    val password: String = "",
    val totp: String = "",
    val show_totp: Boolean = false,
    val working: Boolean = false,
    val result: guard_rotate_result? = null,
    val notice: guard_rotate_notice? = null,
) {
    val can_submit: Boolean
        get() = !working && result == null && rescue_code.isNotEmpty() && confirm_input.isNotBlank() &&
            password.isNotEmpty() && (!show_totp || totp.isNotBlank())
}

fun guard_rotate_notice_for(t: Throwable): guard_rotate_notice {
    val code = api_error_code(t)
    return when {
        code == ROTATE_TOTP_REQUIRED_CODE -> guard_rotate_notice.totp_required
        t is ApiError.InvalidCredentials || t is ApiError.StepUpRequired ||
            code == GUARD_STEP_UP_REQUIRED_CODE || code == ROTATE_INVALID_CREDENTIALS_CODE ||
            code == ROTATE_INVALID_PASSWORD_CODE -> guard_rotate_notice.wrong_password
        else -> when (guard_action_notice_for(t)) {
            guard_action_notice.key_lost -> guard_rotate_notice.key_lost
            guard_action_notice.no_lock_screen -> guard_rotate_notice.no_lock_screen
            guard_action_notice.no_device -> guard_rotate_notice.no_device
            else -> guard_rotate_notice.failed
        }
    }
}

private const val ROTATE_TOTP_REQUIRED_CODE = "TOTP_REQUIRED"
private const val ROTATE_INVALID_CREDENTIALS_CODE = "INVALID_CREDENTIALS"
private const val ROTATE_INVALID_PASSWORD_CODE = "INVALID_PASSWORD"

@HiltViewModel
class GuardRotateRescueViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val user: guard_current_user,
    private val deps: guard_setup_deps,
) : ViewModel() {

    private val _state = MutableStateFlow(guard_rotate_ui(rescue_code = deps.rescue_code()))
    val state: StateFlow<guard_rotate_ui> = _state.asStateFlow()

    fun on_confirm_input(value: String) = _state.update { it.copy(confirm_input = value, notice = null) }

    fun on_password(value: String) = _state.update { it.copy(password = value, notice = null) }

    fun on_totp(value: String) = _state.update { it.copy(totp = value, notice = null) }

    fun submit(signer: guard_signer) {
        if (_state.value.working) return
        viewModelScope.launch { run_rotate(signer) }
    }

    suspend fun run_rotate(signer: guard_signer) {
        val ui = _state.value
        if (!ui.can_submit) return
        if (!guard_rescue_group_matches(ui.rescue_code, ui.confirm_input)) {
            _state.update { it.copy(notice = guard_rotate_notice.wrong_group) }
            return
        }
        val user_id = user.user_id()
        if (user_id == null) {
            _state.update { it.copy(notice = guard_rotate_notice.no_device) }
            return
        }
        _state.update { it.copy(working = true, notice = null) }
        try {
            val hash = deps.password_hash(ui.password)
            if (hash == null) {
                _state.update { it.copy(working = false, notice = guard_rotate_notice.failed) }
                return
            }
            val proof = StepUpProof.Password(hash, ui.totp.takeIf { ui.show_totp && it.isNotBlank() })
            val action = GuardRepository(api, store) { user_id }
                .signed_with(signer, GuardActionCode.ROTATE_RESCUE, GuardDigests.rescue_rotate_subject(ui.rescue_code), proof)
            if (action == null) {
                _state.update { it.copy(working = false, notice = guard_rotate_notice.canceled) }
                return
            }
            api.rotate_rescue(ui.rescue_code, action)
            _state.update {
                it.copy(working = false, result = guard_rotate_result.applied, password = "", totp = "", confirm_input = "")
            }
        } catch (t: CancellationException) {
            throw t
        } catch (held: ApiError.GuardHeld) {
            _state.update {
                it.copy(
                    working = false,
                    result = guard_rotate_result.held(held.to_notice()),
                    password = "",
                    totp = "",
                    confirm_input = "",
                )
            }
        } catch (t: Throwable) {
            val notice = guard_rotate_notice_for(t)
            _state.update {
                it.copy(working = false, notice = notice, show_totp = it.show_totp || notice == guard_rotate_notice.totp_required)
            }
        }
    }

    override fun onCleared() {
        _state.update { it.copy(password = "", totp = "", rescue_code = "") }
        super.onCleared()
    }
}
