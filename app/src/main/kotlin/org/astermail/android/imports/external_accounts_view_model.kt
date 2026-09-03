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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.astermail.android.api.external_accounts.ConnectionSettings
import org.astermail.android.api.external_accounts.CreateManualAccountRequest
import org.astermail.android.api.external_accounts.ExternalAccount
import org.astermail.android.api.external_accounts.ExternalAccountSendAttachment
import org.astermail.android.api.external_accounts.ExternalAccountSendRequest
import org.astermail.android.api.external_accounts.ExternalAccountsApi
import org.astermail.android.api.external_accounts.ManualImapCredentials
import org.astermail.android.api.external_accounts.OAuthAuthorizeRequest
import org.astermail.android.api.external_accounts.PurgeMailRequest
import org.astermail.android.api.external_accounts.ToggleAccountRequest
import org.astermail.android.api.external_accounts.TriggerSyncRequest
import org.astermail.android.api.external_accounts.UpdateAccountRequest
import org.astermail.android.storage.SessionKeyStore

enum class ExternalAccountsError {
    LOAD_FAILED,
    OAUTH_FAILED,
    MANUAL_FAILED,
    NO_SESSION_KEY,
    DELETE_FAILED,
    SYNC_FAILED,
    TOGGLE_FAILED,
    UPDATE_FAILED,
}

data class ExternalAccountsUiState(
    val accounts: List<ExternalAccount> = emptyList(),
    val decrypted: Map<String, ExternalAccountData> = emptyMap(),
    val loading: Boolean = false,
    val connecting_provider: String? = null,
    val authorize_url: String? = null,
    val error: ExternalAccountsError? = null,
    val manual_submitting: Boolean = false,
    val manual_success: Boolean = false,
    val syncing_tokens: Set<String> = emptySet(),
    val toggling_tokens: Set<String> = emptySet(),
    val updating_token: String? = null,
    val connection_settings: Map<String, ConnectionSettings> = emptyMap(),
)

private const val sync_watch_timeout_ms = 120_000L

private const val sync_poll_interval_ms = 3_000L

@HiltViewModel
class ExternalAccountsViewModel @Inject constructor(
    private val api: ExternalAccountsApi,
    private val session_keys: SessionKeyStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ExternalAccountsUiState())
    private val sync_watchers = mutableMapOf<String, Job>()
    val state: StateFlow<ExternalAccountsUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { withContext(Dispatchers.IO) { api.list_accounts() } }
                .onSuccess { res ->
                    val master = session_keys.get() ?: session_keys.get_passphrase()
                    val decrypted = if (master != null) {
                        res.accounts.mapNotNull { acct ->
                            runCatching {
                                decrypt_account_data(
                                    encrypted_account_data = acct.encrypted_account_data,
                                    account_data_nonce = acct.account_data_nonce,
                                    master_key = master,
                                    integrity_hash = acct.integrity_hash,
                                )
                            }.getOrNull()?.let { acct.account_token to it }
                        }.toMap()
                    } else {
                        emptyMap()
                    }
                    _state.value = _state.value.copy(accounts = res.accounts, decrypted = decrypted, loading = false)
                }
                .onFailure {
                    _state.value = _state.value.copy(loading = false, error = ExternalAccountsError.LOAD_FAILED)
                }
        }
    }

    fun start_oauth(provider: String) {
        if (_state.value.connecting_provider != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(connecting_provider = provider, authorize_url = null, error = null)
            val master = session_keys.get() ?: session_keys.get_passphrase()
            if (master == null) {
                _state.value = _state.value.copy(connecting_provider = null, error = ExternalAccountsError.NO_SESSION_KEY)
                return@launch
            }
            try {
                val placeholder_email = "oauth-$provider-${java.time.Instant.now().toEpochMilli()}@import"
                val token = generate_account_token(placeholder_email, master)
                val placeholder = ExternalAccountData(
                    email = placeholder_email,
                    display_name = provider,
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
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
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
        poll_job?.cancel()
        _state.value = _state.value.copy(connecting_provider = null, authorize_url = null)
    }

    private var poll_job: Job? = null

    private fun poll_for_new_account() {
        poll_job?.cancel()
        poll_job = viewModelScope.launch {
            val before_tokens = _state.value.accounts.map { it.account_token }.toSet()
            repeat(60) {
                delay(2000)
                val res = runCatching { withContext(Dispatchers.IO) { api.list_accounts() } }
                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                    .getOrNull()
                if (res != null) {
                    val new_one = res.accounts.firstOrNull { it.account_token !in before_tokens }
                    if (new_one != null) {
                        _state.value = _state.value.copy(
                            accounts = res.accounts,
                            connecting_provider = null,
                        )
                        trigger_sync(new_one.account_token)
                        return@launch
                    }
                }
            }
            _state.value = _state.value.copy(
                connecting_provider = null,
                error = ExternalAccountsError.OAUTH_FAILED,
            )
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
                trigger_sync(token)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(manual_submitting = false, error = ExternalAccountsError.MANUAL_FAILED)
            }
        }
    }

    fun delete_account(
        account_token: String,
        delete_messages: Boolean = false,
        on_result: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            sync_watchers.remove(account_token)?.cancel()
            _state.value = _state.value.copy(
                syncing_tokens = _state.value.syncing_tokens - account_token,
            )
            if (delete_messages) {
                val purged = runCatching {
                    withContext(Dispatchers.IO) {
                        api.purge_mail(PurgeMailRequest(account_token = account_token))
                    }
                }
                if (purged.isFailure || purged.getOrNull()?.success == false) {
                    _state.value = _state.value.copy(error = ExternalAccountsError.DELETE_FAILED)
                    on_result?.invoke(false)
                    return@launch
                }
            }
            val result = runCatching { withContext(Dispatchers.IO) { api.delete_account(account_token) } }
            if (result.isFailure) {
                _state.value = _state.value.copy(error = ExternalAccountsError.DELETE_FAILED)
                on_result?.invoke(false)
            } else {
                load()
                on_result?.invoke(true)
            }
        }
    }

    fun trigger_sync(account_token: String, on_result: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                syncing_tokens = _state.value.syncing_tokens + account_token,
                error = null,
            )
            val previous = _state.value.accounts.firstOrNull { it.account_token == account_token }
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.trigger_sync(TriggerSyncRequest(account_token = account_token))
                }
            }
            val failed = result.isFailure || result.getOrNull()?.success == false
            if (failed) {
                _state.value = _state.value.copy(
                    syncing_tokens = _state.value.syncing_tokens - account_token,
                    error = ExternalAccountsError.SYNC_FAILED,
                )
            } else {
                watch_sync(account_token, previous)
            }
            on_result?.invoke(!failed)
        }
    }

    private fun watch_sync(account_token: String, previous: ExternalAccount?) {
        sync_watchers.remove(account_token)?.cancel()
        sync_watchers[account_token] = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + sync_watch_timeout_ms
            var settled = false
            while (!settled && System.currentTimeMillis() < deadline) {
                delay(sync_poll_interval_ms)
                val listed = runCatching {
                    withContext(Dispatchers.IO) { api.list_accounts() }
                }.getOrNull() ?: continue
                _state.value = _state.value.copy(accounts = listed.accounts)
                val current = listed.accounts.firstOrNull { it.account_token == account_token }
                settled = current == null || sync_has_settled(previous, current)
            }
            sync_watchers.remove(account_token)
            _state.value = _state.value.copy(
                syncing_tokens = _state.value.syncing_tokens - account_token,
            )
            load()
        }
    }

    private fun sync_has_settled(previous: ExternalAccount?, current: ExternalAccount): Boolean {
        val in_progress = current.last_sync_status.equals("syncing", ignoreCase = true) ||
            current.last_sync_status.equals("pending", ignoreCase = true) ||
            current.last_sync_status.equals("running", ignoreCase = true)
        if (in_progress) return false
        return current.needs_reauth ||
            current.last_sync_status.equals("error", ignoreCase = true) ||
            current.last_sync_at != previous?.last_sync_at ||
            current.email_count != previous?.email_count
    }

    fun toggle_account(account_token: String, is_enabled: Boolean) {
        if (account_token in _state.value.toggling_tokens) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                toggling_tokens = _state.value.toggling_tokens + account_token,
                accounts = _state.value.accounts.map { account ->
                    if (account.account_token == account_token) {
                        account.copy(is_enabled = is_enabled)
                    } else {
                        account
                    }
                },
                error = null,
            )
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    api.toggle_account(ToggleAccountRequest(account_token, is_enabled))
                }
            }
            _state.value = _state.value.copy(toggling_tokens = _state.value.toggling_tokens - account_token)
            if (result.isFailure) {
                _state.value = _state.value.copy(
                    error = ExternalAccountsError.TOGGLE_FAILED,
                    accounts = _state.value.accounts.map { account ->
                        if (account.account_token == account_token) {
                            account.copy(is_enabled = !is_enabled)
                        } else {
                            account
                        }
                    },
                )
            } else {
                load()
            }
        }
    }

    fun load_connection_settings(account_token: String) {
        if (_state.value.connection_settings.containsKey(account_token)) return
        viewModelScope.launch {
            val settings = runCatching {
                withContext(Dispatchers.IO) { api.get_connection_settings(account_token) }
            }.getOrNull() ?: return@launch
            _state.value = _state.value.copy(
                connection_settings = _state.value.connection_settings + (account_token to settings),
            )
        }
    }

    fun submit_update(
        account_token: String,
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
        on_result: ((Boolean) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(updating_token = account_token, error = null)
            val master = session_keys.get() ?: session_keys.get_passphrase()
            if (master == null) {
                _state.value = _state.value.copy(updating_token = null, error = ExternalAccountsError.NO_SESSION_KEY)
                on_result?.invoke(false)
                return@launch
            }
            try {
                val existing = _state.value.decrypted[account_token]
                val data = ExternalAccountData(
                    email = email,
                    display_name = existing?.display_name?.takeIf { it.isNotBlank() } ?: email,
                    label_name = existing?.label_name,
                    label_color = existing?.label_color,
                    created_at = existing?.created_at ?: java.time.Instant.now().toString(),
                )
                val encrypted = encrypt_account_data(data, master)
                withContext(Dispatchers.IO) {
                    api.update_account(
                        UpdateAccountRequest(
                            account_token = account_token,
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
                        ),
                    )
                }
                _state.value = _state.value.copy(
                    updating_token = null,
                    connection_settings = _state.value.connection_settings - account_token,
                )
                load()
                on_result?.invoke(true)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                _state.value = _state.value.copy(updating_token = null, error = ExternalAccountsError.UPDATE_FAILED)
                on_result?.invoke(false)
            }
        }
    }

    fun clear_manual_success() {
        _state.value = _state.value.copy(manual_success = false)
    }

    suspend fun send_via_account(
        account_token: String,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        body: String,
        attachments: List<ExternalAccountSendAttachment>,
    ): Result<Unit> = runCatching {
        val response = withContext(Dispatchers.IO) {
            api.send_via_account(
                ExternalAccountSendRequest(
                    account_token = account_token,
                    to = to,
                    cc = cc,
                    bcc = bcc,
                    subject = subject,
                    body = body,
                    attachments = attachments.takeIf { it.isNotEmpty() },
                ),
            )
        }
        if (!response.success) throw IllegalStateException(response.message)
    }
}

fun external_sender_map(state: ExternalAccountsUiState): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    state.accounts.forEach { account ->
        if (!account.is_enabled || account.oauth_provider != null) return@forEach
        val email = state.decrypted[account.account_token]?.email?.trim().orEmpty()
        if (email.isBlank() || email.endsWith("@import") || !email.contains('@')) return@forEach
        result.putIfAbsent(email, account.account_token)
    }
    return result
}
