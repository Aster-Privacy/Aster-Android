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
import android.content.Context
import android.widget.Toast
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.astermail.android.BuildConfig
import org.astermail.android.R
import org.astermail.android.api.auth.AuthApi
import org.astermail.android.security.LockdownStore

@HiltViewModel
class AuthGateViewModel @Inject constructor(
    val auth_repository: AuthRepository,
    private val auth_api: AuthApi,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _is_ready = MutableStateFlow(false)
    val is_ready: StateFlow<Boolean> = _is_ready.asStateFlow()

    val is_signed_in: StateFlow<Boolean> = auth_repository.is_signed_in

    val session_expired: StateFlow<Boolean> = auth_repository.session_expired

    fun consume_session_expired() {
        auth_repository.consume_session_expired()
    }

    fun recheck_session() {
        if (!auth_repository.is_signed_in.value) return
        viewModelScope.launch {
            runCatching { auth_repository.handle_unauthorized_signal() }
        }
    }

    init {
        if (auth_repository.is_signed_in.value) {
            viewModelScope.launch {
                val recovery = launch(Dispatchers.Default) {
                    runCatching { auth_repository.try_recover_identity_key() }
                }
                withTimeoutOrNull(IDENTITY_RECOVERY_TIMEOUT_MS) { recovery.join() }
                _is_ready.value = true
            }
        } else {
            _is_ready.value = true
        }
        if (auth_repository.is_signed_in.value) {
            viewModelScope.launch {
                val csrf_ok = auth_repository.ensure_csrf_ready()
                if (!csrf_ok) {
                    if (BuildConfig.DEBUG) {
                        android.util.Log.w("AuthGate", "CSRF token fetch failed on launch")
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.csrf_unavailable),
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            }
            viewModelScope.launch {
                try {
                    val info = withTimeoutOrNull(8_000L) { auth_api.me() }
                    if (info != null) {
                        LockdownStore.set_enabled(context, info.lockdown_mode_enabled)
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (e is org.astermail.android.api.ApiError.UnauthorizedError) {
                        auth_repository.handle_unauthorized_signal(force = true)
                    }
                }
            }
        }
    }

    private companion object {
        const val IDENTITY_RECOVERY_TIMEOUT_MS = 12_000L
    }
}
