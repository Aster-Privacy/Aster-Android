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

package org.astermail.android.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.astermail.android.BuildConfig
import org.astermail.android.api.billing.BillingApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.StoredAccount

data class AccountsUiState(
    val accounts: List<StoredAccount> = emptyList(),
    val current_account_id: String? = null,
    val can_add_more: Boolean = true,
    val max_accounts: Int = AccountStore.max_accounts_default,
) {
    val is_unlimited: Boolean get() = max_accounts == AccountStore.unlimited_accounts
}

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val account_store: AccountStore,
    private val auth_repository: AuthRepository,
    private val billing_api: BillingApi,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    private var is_switching = false

    init {
        refresh()
        viewModelScope.launch {
            auth_repository.refresh_profile()
            refresh()
            sync_account_limit()
        }
    }

    fun refresh_with_profile() {
        viewModelScope.launch {
            auth_repository.refresh_profile()
            refresh()
            sync_account_limit()
        }
    }

    fun sync_account_limit() {
        viewModelScope.launch { load_account_limit() }
    }

    private suspend fun load_account_limit() {
        val limit = fetch_account_limit() ?: return
        account_store.set_max_accounts(limit)
        refresh()
    }

    private suspend fun fetch_account_limit(): Int? {
        val from_endpoint = try {
            billing_api.get_account_limit().max_accounts
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (BuildConfig.DEBUG) android.util.Log.w("AccountsVM", "get_account_limit failed", t)
            null
        }
        if (from_endpoint != null && from_endpoint != 0) return from_endpoint
        return try {
            billing_api.get_plan_limits()
                .limits[limit_key_max_multi_accounts]
                ?.limit
                ?.takeIf { it != 0 }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            if (BuildConfig.DEBUG) android.util.Log.w("AccountsVM", "get_plan_limits failed", t)
            null
        }
    }

    fun refresh() {
        val all = account_store.get_all()
        _state.value = AccountsUiState(
            accounts = all,
            current_account_id = account_store.get_current_id(),
            can_add_more = account_store.can_add(),
            max_accounts = account_store.get_max_accounts(),
        )
    }

    fun switch_account(account_id: String, on_result: (Boolean) -> Unit = {}) {
        if (!account_store.account_exists(account_id)) {
            on_result(false)
            return
        }
        if (is_switching) return
        is_switching = true
        auth_repository.store_current_session_tokens()
        account_store.set_current(account_id)
        org.astermail.android.billing.AttachmentLimits.reset()
        org.astermail.android.billing.AvailablePlansCache.reset()
        org.astermail.android.billing.PlanLimitsCache.reset()
        refresh()
        viewModelScope.launch {
            val restored = try {
                auth_repository.try_restore_session(account_id)
            } finally {
                is_switching = false
            }
            if (account_store.get_current_id() == account_id) {
                on_result(restored)
                if (restored) load_account_limit()
            }
        }
    }

    fun has_stored_session(account_id: String): Boolean =
        auth_repository.has_stored_session(account_id)

    private companion object {
        const val limit_key_max_multi_accounts = "max_multi_accounts"
    }
}
