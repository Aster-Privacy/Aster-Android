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

package org.astermail.android.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.OffsetDateTime
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.api.ACCOUNT_SUSPENDED_CODE
import org.astermail.android.api.ApiError
import org.astermail.android.api.account.AccountApi
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.api.auth.UserInfo
import org.astermail.android.auth.AuthRepository

@HiltViewModel
class SuspendedAccountViewModel @Inject constructor(
    private val account_api: AccountApi,
    private val auth_api: AuthApi,
    private val auth_repository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val visible: Boolean = false,
        val suspended_at: Instant? = null,
        val deletion_eligible_at: Instant? = null,
        val is_signing_out: Boolean = false,
        val profile: UserInfo? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun check() {
        if (_state.value.is_signing_out) return
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = runCatching { account_api.get_status() }
            if (_state.value.is_signing_out) return@launch
            outcome.fold(
                onSuccess = { status ->
                    _state.value = if (status.status == "suspended") {
                        _state.value.copy(
                            visible = true,
                            suspended_at = parse_instant(status.suspended_at),
                            deletion_eligible_at = parse_instant(status.deletion_eligible_at),
                            profile = load_profile(),
                        )
                    } else {
                        UiState()
                    }
                },
                onFailure = { failure ->
                    if (is_suspended_error(failure)) {
                        _state.value = _state.value.copy(visible = true, profile = load_profile())
                    }
                },
            )
        }
    }

    fun sign_out(on_done: (Boolean) -> Unit) {
        if (_state.value.is_signing_out) return
        _state.value = _state.value.copy(is_signing_out = true)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { auth_repository.logout() }
            val switched_account = auth_repository.is_signed_in.value
            _state.value = UiState()
            withContext(Dispatchers.Main) { on_done(switched_account) }
        }
    }

    fun reset() {
        _state.value = UiState()
    }

    private suspend fun load_profile(): UserInfo? = runCatching { auth_api.me() }.getOrNull()

    private fun parse_instant(raw: String?): Instant? =
        raw?.let { value -> runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull() }

    private fun is_suspended_error(t: Throwable): Boolean =
        t is ApiError.ForbiddenError && t.code == ACCOUNT_SUSPENDED_CODE
}
