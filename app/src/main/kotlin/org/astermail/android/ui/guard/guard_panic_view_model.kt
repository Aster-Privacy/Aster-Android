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
import kotlinx.coroutines.launch
import org.astermail.android.api.guard.GuardApi
import org.astermail.android.api.guard.GuardPanicResult
import org.astermail.android.crypto.guard.GuardActionCode
import org.astermail.android.crypto.guard.GuardDigests
import org.astermail.android.guard.GuardDeviceStore
import org.astermail.android.guard.GuardRepository
import org.astermail.android.guard.guard_signer

sealed interface guard_panic_state {
    data class confirm(val notice: guard_action_notice? = null) : guard_panic_state
    data object working : guard_panic_state
    data class done(val result: GuardPanicResult) : guard_panic_state {
        val offers_password_change: Boolean get() = result.can_reset_password_now
    }
    data class failed(
        val notice: guard_action_notice,
        val error: org.astermail.android.guard.guard_error_message? = null,
    ) : guard_panic_state
}

@HiltViewModel
class GuardPanicViewModel @Inject constructor(
    private val api: GuardApi,
    private val store: GuardDeviceStore,
    private val user: guard_current_user,
) : ViewModel() {

    private val _state = MutableStateFlow<guard_panic_state>(guard_panic_state.confirm())
    val state: StateFlow<guard_panic_state> = _state.asStateFlow()

    fun confirm(signer: guard_signer) {
        if (_state.value is guard_panic_state.working) return
        viewModelScope.launch { run_confirm(signer) }
    }

    fun retry() {
        _state.value = guard_panic_state.confirm()
    }

    private suspend fun run_confirm(signer: guard_signer) {
        val user_id = user.user_id()
        if (user_id == null) {
            _state.value = guard_panic_state.failed(guard_action_notice.no_device)
            return
        }
        _state.value = guard_panic_state.working
        try {
            val action = GuardRepository(api, store) { user_id }
                .signed_with(signer, GuardActionCode.PANIC, GuardDigests.PANIC_SUBJECT)
            if (action == null) {
                _state.value = guard_panic_state.confirm(guard_action_notice.canceled)
                return
            }
            _state.value = guard_panic_state.done(api.panic(action))
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            _state.value = guard_panic_state.failed(guard_action_notice_for(t), org.astermail.android.guard.guard_error_message_for(t))
        }
    }
}
