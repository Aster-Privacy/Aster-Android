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

package org.astermail.android.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.api.external_accounts.CreateManualAccountRequest
import org.astermail.android.api.external_accounts.ExternalAccount
import org.astermail.android.api.external_accounts.ExternalAccountsApi
import org.astermail.android.api.external_accounts.ManualImapCredentials
import org.astermail.android.api.external_accounts.OAuthAuthorizeRequest
import org.astermail.android.api.external_accounts.TriggerSyncRequest
import org.astermail.android.storage.SessionKeyStore

enum class ExternalAccountsError { LOAD_FAILED, OAUTH_FAILED, MANUAL_FAILED, NO_SESSION_KEY }

data class ExternalAccountsUiState(
    val accounts: List<ExternalAccount> = emptyList(),
    val loading: Boolean = false,
    val connecting_provider: String? = null,
    val authorize_url: String? = null,
    val error: ExternalAccountsError? = null,
    val manual_submitting: Boolean = false,
    val manual_success: Boolean = false,
)

@HiltViewModel
class ExternalAccountsViewModel @Inject constructor(
    private val api: ExternalAccountsApi,
    private val session_keys: SessionKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ExternalAccountsUiState())
    val state: StateFlow<ExternalAccountsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { api.list_accounts() } }
                .onSuccess { res ->
                    _state.value = _state.value.copy(accounts = res.accounts, loading = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = ExternalAccountsError.LOAD_FAILED)
                }
        }
    }

    fun start_oauth(provider: String, account_email: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting_provider = provider, authorize_url = null, error = null)
            val master = session_keys.get() ?: session_keys.get_passphrase()
            if (master == null) {
                _state.value = _state.value.copy(connecting_provider = null, error = ExternalAccountsError.NO_SESSION_KEY)
                return@launch
            }
            try {
                val token = generate_account_token(account_email, master)
                val placeholder = ExternalAccountData(
                    email = account_email,
                    display_name = account_email,
                    created_at = java.time.Instant.now().toString(),
                )
                val encrypted = encrypt_account_data(placeholder, master)
                val response = withContext(Dispatchers.IO) {
                    api.start_oauth(OAuthAuthorizeRequest(
                        provider = provider,
                        account_token = token,
                        encrypted_account_data = encrypted.encrypted_account_data,
                        account_data_nonce = encrypted.account_data_nonce,
                        integrity_hash = encrypted.integrity_hash,
                    ))
                }
                _state.value = _state.value.copy(authorize_url = response.authorize_url)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(connecting_provider = null, error = ExternalAccountsError.OAUTH_FAILED)
            }
        }
    }

    fun consume_authorize_url() {
        _state.value = _state.value.copy(authorize_url = null)
        poll_for_new_account()
    }

    fun cancel_oauth() {
        _state.value = _state.value.copy(connecting_provider = null, authorize_url = null)
    }

    private fun poll_for_new_account() {
        viewModelScope.launch {
            val before_tokens = _state.value.accounts.map { it.account_token }.toSet()
            repeat(60) {
                delay(2000)
                val res = runCatching { withContext(Dispatchers.IO) { api.list_accounts() } }.getOrNull()
                if (res != null) {
                    val new_one = res.accounts.firstOrNull { it.account_token !in before_tokens }
                    if (new_one != null) {
                        _state.value = _state.value.copy(
                            accounts = res.accounts,
                            connecting_provider = null,
                        )
                        runCatching {
                            withContext(Dispatchers.IO) {
                                api.trigger_sync(TriggerSyncRequest(account_token = new_one.account_token))
                            }
                        }
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(connecting_provider = null)
        }
    }

    fun submit_manual_imap(
        email: String,
        host: String,
        port: Int,
        username: String,
        password: String,
        use_tls: Boolean,
        smtp_host: String,
        smtp_port: Int,
        smtp_username: String,
        smtp_password: String,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(manual_submitting = true, error = null, manual_success = false)
            val master = session_keys.get() ?: session_keys.get_passphrase()
            if (master == null) {
                _state.value = _state.value.copy(manual_submitting = false, error = ExternalAccountsError.NO_SESSION_KEY)
                return@launch
            }
            try {
                val token = generate_account_token(email, master)
                val data = ExternalAccountData(
                    email = email,
                    display_name = email,
                    created_at = java.time.Instant.now().toString(),
                )
                val encrypted = encrypt_account_data(data, master)
                withContext(Dispatchers.IO) {
                    api.create_manual(CreateManualAccountRequest(
                        account_token = token,
                        encrypted_account_data = encrypted.encrypted_account_data,
                        account_data_nonce = encrypted.account_data_nonce,
                        integrity_hash = encrypted.integrity_hash,
                        credentials = ManualImapCredentials(
                            host = host,
                            port = port,
                            username = username,
                            password = password,
                            use_tls = use_tls,
                            smtp_host = smtp_host,
                            smtp_port = smtp_port,
                            smtp_username = smtp_username,
                            smtp_password = smtp_password,
                        ),
                    ))
                }
                _state.value = _state.value.copy(manual_submitting = false, manual_success = true)
                load()
                runCatching {
                    withContext(Dispatchers.IO) {
                        api.trigger_sync(TriggerSyncRequest(account_token = token))
                    }
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(manual_submitting = false, error = ExternalAccountsError.MANUAL_FAILED)
            }
        }
    }

    fun delete_account(account_token: String) {
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { api.delete_account(account_token) } }
            load()
        }
    }

    fun trigger_sync(account_token: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    api.trigger_sync(TriggerSyncRequest(account_token = account_token))
                }
            }
        }
    }

    fun clear_manual_success() {
        _state.value = _state.value.copy(manual_success = false)
    }
}
