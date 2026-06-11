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

package org.astermail.android.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class StoredAccount(
    val id: String,
    val email: String,
    val display_name: String? = null,
    val profile_color: String? = null,
    val profile_picture: String? = null,
    val added_at: Long,
)

@Serializable
data class AccountsData(
    val accounts: List<StoredAccount> = emptyList(),
    val current_account_id: String? = null,
)

class AccountStore(context: Context? = null) {

    private val lock = Any()
    private val prefs: SharedPreferences? = context?.let { SecurePrefs.open(it, prefs_name) }
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var data: AccountsData = AccountsData()

    private val _current_account = MutableStateFlow<StoredAccount?>(null)
    val current_account: StateFlow<StoredAccount?> = _current_account.asStateFlow()

    init {
        val raw = runCatching { prefs?.getString(key_accounts, null) }.getOrNull()
        if (raw != null) {
            data = try {
                json.decodeFromString(AccountsData.serializer(), raw)
            } catch (_: Throwable) {
                AccountsData()
            }
        }
        _current_account.value = compute_current()
    }

    private fun compute_current(): StoredAccount? {
        val id = data.current_account_id ?: return null
        return data.accounts.firstOrNull { it.id == id }
    }

    private fun emit_current() {
        _current_account.value = compute_current()
    }

    fun get_all(): List<StoredAccount> = synchronized(lock) { data.accounts.toList() }

    fun get_current_id(): String? = synchronized(lock) { data.current_account_id }

    fun get_current(): StoredAccount? = synchronized(lock) {
        val id = data.current_account_id ?: return null
        data.accounts.firstOrNull { it.id == id }
    }

    fun get_others(): List<StoredAccount> = synchronized(lock) {
        val id = data.current_account_id
        data.accounts.filter { it.id != id }
    }

    fun count(): Int = synchronized(lock) { data.accounts.size }

    fun can_add(max_accounts: Int = max_accounts_default): Boolean =
        synchronized(lock) { data.accounts.size < max_accounts }

    fun account_exists(account_id: String): Boolean = synchronized(lock) {
        data.accounts.any { it.id == account_id }
    }

    fun add_or_update(account: StoredAccount, max_accounts: Int = max_accounts_default): AddResult {
        synchronized(lock) {
            val accounts = data.accounts.toMutableList()
            val existing_index = accounts.indexOfFirst { it.id == account.id }
            if (existing_index >= 0) {
                accounts[existing_index] = account.copy(added_at = accounts[existing_index].added_at)
                data = AccountsData(accounts, account.id)
                persist()
                emit_current()
                return AddResult.Success
            }
            if (accounts.size >= max_accounts) {
                return AddResult.LimitReached(max_accounts)
            }
            accounts.add(account)
            data = AccountsData(accounts, account.id)
            persist()
            emit_current()
            return AddResult.Success
        }
    }

    fun set_current(account_id: String): StoredAccount? {
        synchronized(lock) {
            val account = data.accounts.firstOrNull { it.id == account_id } ?: return null
            data = data.copy(current_account_id = account_id)
            persist()
            emit_current()
            return account
        }
    }

    fun remove(account_id: String): RemoveResult {
        synchronized(lock) {
            val accounts = data.accounts.toMutableList()
            val index = accounts.indexOfFirst { it.id == account_id }
            if (index < 0) return RemoveResult(removed = false, switched_to = null)
            accounts.removeAt(index)
            var switched_to: StoredAccount? = null
            val new_current = if (data.current_account_id == account_id) {
                if (accounts.isNotEmpty()) {
                    switched_to = accounts.first()
                    accounts.first().id
                } else {
                    null
                }
            } else {
                data.current_account_id
            }
            data = AccountsData(accounts, new_current)
            persist()
            emit_current()
            return RemoveResult(removed = true, switched_to = switched_to)
        }
    }

    fun clear_all() {
        synchronized(lock) {
            data = AccountsData()
            runCatching { prefs?.edit()?.remove(key_accounts)?.apply() }
            emit_current()
        }
    }

    private fun persist() {
        runCatching {
            val raw = json.encodeToString(AccountsData.serializer(), data)
            prefs?.edit()?.putString(key_accounts, raw)?.apply()
        }
    }

    sealed class AddResult {
        data object Success : AddResult()
        data class LimitReached(val max_accounts: Int) : AddResult()
    }

    data class RemoveResult(
        val removed: Boolean,
        val switched_to: StoredAccount?,
    )

    companion object {
        const val max_accounts_default = 3
        private const val prefs_name = "aster_accounts_v1"
        private const val key_accounts = "accounts_data"
    }
}
