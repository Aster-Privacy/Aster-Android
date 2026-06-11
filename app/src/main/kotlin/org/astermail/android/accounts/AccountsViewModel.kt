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
import org.astermail.android.auth.AuthRepository
import org.astermail.android.storage.AccountStore
import org.astermail.android.storage.StoredAccount

data class AccountsUiState(
    val accounts: List<StoredAccount> = emptyList(),
    val current_account_id: String? = null,
    val can_add_more: Boolean = true,
    val max_accounts: Int = AccountStore.max_accounts_default,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val account_store: AccountStore,
    private val auth_repository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            auth_repository.refresh_profile()
            refresh()
        }
    }

    fun refresh_with_profile() {
        viewModelScope.launch {
            auth_repository.refresh_profile()
            refresh()
        }
    }

    fun refresh() {
        val all = account_store.get_all()
        _state.value = AccountsUiState(
            accounts = all,
            current_account_id = account_store.get_current_id(),
            can_add_more = account_store.can_add(),
            max_accounts = AccountStore.max_accounts_default,
        )
    }

    fun switch_account(account_id: String, on_result: (Boolean) -> Unit = {}) {
        if (!account_store.account_exists(account_id)) {
            on_result(false)
            return
        }
        account_store.set_current(account_id)
        refresh()
        viewModelScope.launch {
            val restored = auth_repository.try_restore_session(account_id)
            on_result(restored)
        }
    }

    fun has_stored_session(account_id: String): Boolean =
        auth_repository.has_stored_session(account_id)
}
