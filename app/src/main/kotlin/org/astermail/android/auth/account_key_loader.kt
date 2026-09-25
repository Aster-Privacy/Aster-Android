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

import java.security.MessageDigest
import java.security.SecureRandom
import org.astermail.android.api.keys.AccountKeyTokenResponse
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.crypto.AccountKey
import org.astermail.android.storage.AccountKeyLoadTicket
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.util.passphrase_chars

class AccountKeyLoader(
    private val keys_api: KeysApi,
    private val session_key_store: SessionKeyStore,
) {

    suspend fun load(): Int {
        val ticket = session_key_store.begin_account_key_load()
        try {
            return load_keys(ticket)
        } finally {
            session_key_store.finish_account_key_load(ticket)
        }
    }

    private suspend fun load_keys(ticket: AccountKeyLoadTicket): Int {
        val generation = session_key_store.account_kek_generation()
        val identity_key = session_key_store.get_identity_key() ?: return 0
        val existing = keys_api.get_account_key_token()
        if (session_key_store.account_kek_generation() != generation) return 0
        val current = existing ?: create_if_absent(identity_key, generation) ?: return 0
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
        var write_keks: Map<String, ByteArray>? = null
        try {
            val keks = LinkedHashSet<String>()
            val seen = HashSet<String>()
            for (token in tokens) {
                val account_key = AccountKey.open_token(token, own_keys, chars) ?: continue
                try {
                    if (token == current.token && write_keks == null) {
                        write_keks = AccountKey.DATA_CONTEXTS.associateWith {
                            AccountKey.derive_context_key(account_key, it)
                        }
                    }
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
            write_keks?.let { session_key_store.put_account_write_keks(it, generation, ticket) }
            return seen.size
        } finally {
            write_keks?.values?.forEach { it.fill(0) }
            chars.fill(' ')
        }
    }

    private suspend fun create_if_absent(identity_key: String, generation: Long): AccountKeyTokenResponse? {
        val history = keys_api.get_account_key_token_history_or_null() ?: return null
        if (history.isNotEmpty()) return null
        val passphrase = session_key_store.get_passphrase() ?: return null
        val chars = passphrase_chars(passphrase)
        passphrase.fill(0)
        val account_key = ByteArray(AccountKey.LENGTH).also { SecureRandom().nextBytes(it) }
        try {
            val token = AccountKey.seal_token(account_key, identity_key, chars) ?: return null
            val reopened = AccountKey.open_token(token, listOf(identity_key), chars) ?: return null
            val matches = MessageDigest.isEqual(reopened, account_key)
            reopened.fill(0)
            if (!matches) return null
            val fingerprint = AccountKey.primary_fingerprint(identity_key) ?: return null
            if (session_key_store.account_kek_generation() != generation) return null
            return keys_api.put_account_key_token_if_absent(token, fingerprint)
        } finally {
            account_key.fill(0)
            chars.fill(' ')
        }
    }
}
