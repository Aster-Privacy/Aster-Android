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

package org.astermail.android.account

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.R
import org.astermail.android.api.ApiError
import org.astermail.android.api.account.AccountApi
import org.astermail.android.auth.AuthRepository

@HiltViewModel
class PendingDeletionViewModel @Inject constructor(
    application: Application,
    private val account_api: AccountApi,
    private val auth_repository: AuthRepository,
) : AndroidViewModel(application) {

    data class UiState(
        val visible: Boolean = false,
        val days_remaining: Long? = null,
        val is_cancelling: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val ctx get() = getApplication<Application>()

    fun check() {
        if (_state.value.is_cancelling) return
        viewModelScope.launch(Dispatchers.IO) {
            val days = runCatching { account_api.get_status() }.fold(
                onSuccess = { status ->
                    if (status.status == "pending_deletion") status.days_until_deletion ?: -1L else null
                },
                onFailure = { t -> if (is_pending_deletion_error(t)) -1L else null },
            )
            if (_state.value.is_cancelling) return@launch
            _state.value = if (days != null) {
                _state.value.copy(visible = true, days_remaining = days.takeIf { it >= 0L })
            } else {
                _state.value.copy(visible = false)
            }
        }
    }

    fun keep_account(on_done: () -> Unit) {
        if (_state.value.is_cancelling) return
        _state.value = _state.value.copy(is_cancelling = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { account_api.cancel_deletion() }
            result.fold(
                onSuccess = { resp ->
                    if (resp.success) {
                        _state.value = UiState(visible = false)
                        withContext(Dispatchers.Main) { on_done() }
                    } else {
                        _state.value = _state.value.copy(
                            is_cancelling = false,
                            error = ctx.getString(R.string.pending_deletion_error),
                        )
                    }
                },
                onFailure = {
                    _state.value = _state.value.copy(
                        is_cancelling = false,
                        error = ctx.getString(R.string.pending_deletion_error),
                    )
                },
            )
        }
    }

    fun sign_out(on_done: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { auth_repository.logout() }
            val switched_account = auth_repository.is_signed_in.value
            _state.value = UiState(visible = false)
            withContext(Dispatchers.Main) { on_done(switched_account) }
        }
    }

    private fun is_pending_deletion_error(t: Throwable): Boolean =
        t is ApiError.ForbiddenError && t.detail.contains("scheduled for deletion", ignoreCase = true)
}
