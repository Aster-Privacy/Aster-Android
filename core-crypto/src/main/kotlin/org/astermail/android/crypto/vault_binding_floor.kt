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

package org.astermail.android.crypto

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

interface VaultBindingFloorStore {
    fun is_bound(account: String): Boolean?
    fun save(account: String, bound: Boolean): Boolean
    fun admitted(account: String): Set<String>? = emptySet()
    fun save_admitted(account: String, digests: Set<String>): Boolean = false
}

object VaultBindingFloor {
    const val MAX_ADMITTED_VAULTS = 4

    @Volatile
    private var account_source: () -> String? = { null }

    @Volatile
    private var store: VaultBindingFloorStore? = null

    private val bound_accounts: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val admitted_vaults: ConcurrentHashMap<String, List<String>> = ConcurrentHashMap()

    fun bind_account_source(source: () -> String?) {
        account_source = source
    }

    fun bind_store(floor_store: VaultBindingFloorStore?) {
        store = floor_store
    }

    fun allow_unbound(encrypted_vault: ByteArray? = null): Boolean {
        val floor_store = store
        val account = current_account() ?: return floor_store == null && bound_accounts.isEmpty()
        if (encrypted_vault != null && is_admitted(account, encrypted_vault)) return true
        if (account in bound_accounts) return false
        if (floor_store == null) return true
        val persisted = runCatching { floor_store.is_bound(account) }.getOrNull() ?: return false
        if (persisted) bound_accounts.add(account)
        return !persisted
    }

    fun note_opened(bound: Boolean) {
        if (!bound) return
        val account = current_account() ?: return
        raise(account)
    }

    fun note_written(bound: Boolean, account: String? = null, encrypted_vault: ByteArray? = null) {
        val target = account?.takeIf { it.isNotBlank() } ?: current_account() ?: return
        if (bound) raise(target) else if (encrypted_vault != null) admit(target, encrypted_vault)
    }

    fun admit_after_password_sign_in(encrypted_vault: ByteArray) {
        val account = current_account() ?: return
        admit(account, encrypted_vault)
    }

    fun forget(account: String) {
        bound_accounts.remove(account)
        admitted_vaults.remove(account)
        persist(account, false)
        runCatching { store?.save_admitted(account, emptySet()) }
    }

    fun reset() {
        bound_accounts.clear()
        admitted_vaults.clear()
        store = null
    }

    fun vault_digest(encrypted_vault: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(encrypted_vault).joinToString("") { "%02x".format(it) }

    private fun is_admitted(account: String, encrypted_vault: ByteArray): Boolean {
        val digest = vault_digest(encrypted_vault)
        if (admitted_vaults[account]?.contains(digest) == true) return true
        val persisted = runCatching { store?.admitted(account) }.getOrNull() ?: return false
        if (persisted.isNotEmpty()) admitted_vaults.merge(account, persisted.toList()) { old, new -> (old + new).distinct() }
        return digest in persisted
    }

    private fun admit(account: String, encrypted_vault: ByteArray) {
        val digest = vault_digest(encrypted_vault)
        val known = admitted_vaults[account].orEmpty() +
            runCatching { store?.admitted(account) }.getOrNull().orEmpty()
        val kept = (known.filter { it != digest } + digest).distinct().takeLast(MAX_ADMITTED_VAULTS)
        admitted_vaults[account] = kept
        runCatching { store?.save_admitted(account, kept.toSet()) }
    }

    private fun raise(account: String) {
        bound_accounts.add(account)
        persist(account, true)
    }

    private fun persist(account: String, bound: Boolean) {
        runCatching { store?.save(account, bound) }
    }

    private fun current_account(): String? =
        runCatching { account_source() }.getOrNull()?.takeIf { it.isNotBlank() }
}
