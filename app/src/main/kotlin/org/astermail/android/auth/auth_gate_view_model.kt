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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.api.auth.AuthApi

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    val auth_repository: AuthRepository,
    private val auth_api: AuthApi,
) : ViewModel() {

    private val _is_ready = MutableStateFlow(false)
    val is_ready: StateFlow<Boolean> = _is_ready.asStateFlow()

    val is_signed_in: StateFlow<Boolean> = auth_repository.is_signed_in

    init {
        if (auth_repository.is_signed_in.value) {
            runCatching { auth_repository.try_recover_identity_key() }
        }
        _is_ready.value = true
        if (auth_repository.is_signed_in.value) {
            viewModelScope.launch {
                try {
                    withTimeoutOrNull(8_000L) { auth_api.me() }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (e is org.astermail.android.api.ApiError.UnauthorizedError) {
                        auth_repository.logout()
                    }
                }
            }
        }
    }
}
