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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
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
    private val write_lock = Any()
    private val persist_pending = AtomicBoolean(false)
    private val prefs: SharedPreferences? = context?.let { SecurePrefs.open(it, prefs_name) }
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var data: AccountsData = AccountsData()

    @Volatile
    private var plan_max_accounts: Int = max_accounts_default

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
        plan_max_accounts = runCatching {
            prefs?.getInt(key_plan_max_accounts, max_accounts_default) ?: max_accounts_default
        }.getOrDefault(max_accounts_default)
        _current_account.value = compute_current()
    }

    private fun compute_current(): StoredAccount? {
        val id = data.current_account_id ?: return null
        return data.accounts.firstOrNull { it.id == id }
    }

    private fun emit_current() {
        _current_account.value = compute_current()
    }

    fun get_all(): List<StoredAccount> = data.accounts.toList()

    fun get_current_id(): String? = data.current_account_id

    fun get_current(): StoredAccount? {
        val snapshot = data
        val id = snapshot.current_account_id ?: return null
        return snapshot.accounts.firstOrNull { it.id == id }
    }

    fun get_others(): List<StoredAccount> {
        val snapshot = data
        val id = snapshot.current_account_id
        return snapshot.accounts.filter { it.id != id }
    }

    fun count(): Int = data.accounts.size

    fun get_max_accounts(): Int = plan_max_accounts

    fun set_max_accounts(max_accounts: Int) {
        synchronized(lock) {
            val normalized = if (max_accounts == unlimited_accounts) {
                unlimited_accounts
            } else {
                max_accounts.coerceAtLeast(1)
            }
            if (normalized == plan_max_accounts) return
            plan_max_accounts = normalized
        }
        schedule_persist()
    }

    fun can_add(max_accounts: Int = get_max_accounts()): Boolean =
        max_accounts == unlimited_accounts || data.accounts.size < max_accounts

    fun account_exists(account_id: String): Boolean = data.accounts.any { it.id == account_id }

    fun add_or_update(account: StoredAccount, max_accounts: Int = get_max_accounts()): AddResult {
        synchronized(lock) {
            val accounts = data.accounts.toMutableList()
            val existing_index = accounts.indexOfFirst { it.id == account.id }
            if (existing_index >= 0) {
                val merged = account.copy(added_at = accounts[existing_index].added_at)
                if (merged == accounts[existing_index] && data.current_account_id == account.id) {
                    return AddResult.Success
                }
                accounts[existing_index] = merged
                data = AccountsData(accounts.toList(), account.id)
                emit_current()
            } else {
                if (max_accounts != unlimited_accounts && accounts.size >= max_accounts) {
                    return AddResult.LimitReached(max_accounts)
                }
                accounts.add(account)
                data = AccountsData(accounts.toList(), account.id)
                emit_current()
            }
        }
        schedule_persist()
        return AddResult.Success
    }

    fun set_current(account_id: String): StoredAccount? {
        val account = synchronized(lock) {
            val found = data.accounts.firstOrNull { it.id == account_id } ?: return null
            data = data.copy(current_account_id = account_id)
            emit_current()
            found
        }
        schedule_persist()
        return account
    }

    fun remove(account_id: String): RemoveResult {
        val result = synchronized(lock) {
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
            data = AccountsData(accounts.toList(), new_current)
            emit_current()
            RemoveResult(removed = true, switched_to = switched_to)
        }
        schedule_persist()
        return result
    }

    fun clear_all() {
        synchronized(lock) {
            data = AccountsData()
            emit_current()
        }
        schedule_persist()
    }

    private fun schedule_persist() {
        val target = prefs ?: return
        if (!persist_pending.compareAndSet(false, true)) return
        val submitted = runCatching {
            writer.execute {
                persist_pending.set(false)
                synchronized(write_lock) { write_snapshot(target) }
            }
        }.isSuccess
        if (!submitted) {
            persist_pending.set(false)
            synchronized(write_lock) { write_snapshot(target) }
        }
    }

    private fun write_snapshot(target: SharedPreferences) {
        runCatching {
            val snapshot = data
            val max = plan_max_accounts
            val editor = target.edit()
            if (snapshot.accounts.isEmpty() && snapshot.current_account_id == null) {
                editor.remove(key_accounts)
            } else {
                editor.putString(key_accounts, json.encodeToString(AccountsData.serializer(), snapshot))
            }
            editor.putInt(key_plan_max_accounts, max)
            editor.commit()
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
        const val unlimited_accounts = -1
        private const val prefs_name = "aster_accounts_v1"
        private const val key_accounts = "accounts_data"
        private const val key_plan_max_accounts = "plan_max_accounts"
        private val writer: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "aster-account-store").apply { isDaemon = true }
        }
    }
}
