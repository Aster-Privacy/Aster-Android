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

import org.astermail.android.api.keys.KeysApi
import org.astermail.android.crypto.AccountKey
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.util.passphrase_chars

class AccountKeyLoader(
    private val keys_api: KeysApi,
    private val session_key_store: SessionKeyStore,
) {

    suspend fun load(): Int {
        val generation = session_key_store.account_kek_generation()
        val identity_key = session_key_store.get_identity_key() ?: return 0
        val current = keys_api.get_account_key_token() ?: return 0
        if (session_key_store.account_kek_generation() != generation) return 0
        val history = keys_api.get_account_key_token_history()
        val own_keys = buildList {
            add(identity_key)
            session_key_store.get_previous_keys()?.let { addAll(it) }
        }
        val tokens = (listOf(current.token) + history.map { it.token }).distinct()
        val passphrase = session_key_store.get_passphrase() ?: return 0
        val chars = passphrase_chars(passphrase)
        passphrase.fill(0)
        try {
            val keks = LinkedHashSet<String>()
            val seen = HashSet<String>()
            for (token in tokens) {
                val account_key = AccountKey.open_token(token, own_keys, chars) ?: continue
                try {
                    val id = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(account_key)
                        .joinToString("") { "%02x".format(it) }
                    if (!seen.add(id)) continue
                    keks.addAll(AccountKey.derive_context_keks(account_key))
                } finally {
                    account_key.fill(0)
                }
            }
            if (seen.isEmpty()) return 0
            if (!session_key_store.put_account_keks(keks.toList(), generation)) return 0
            return seen.size
        } finally {
            chars.fill(' ')
        }
    }
}
