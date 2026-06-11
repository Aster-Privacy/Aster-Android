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
import android.util.Base64

class SessionSnapshotStore(context: Context? = null) {

    private val lock = Any()
    private val prefs: SharedPreferences? = context?.let { SecurePrefs.open(it, prefs_name) }

    fun save(
        account_id: String,
        token_access: String?,
        token_refresh: String?,
        session_key: ByteArray?,
        passphrase: ByteArray?,
        identity_key: String?,
        encrypted_vault: String?,
        vault_nonce: String?,
        password_salt: ByteArray?,
        user_id: String?,
        user_email: String?,
        recovery_codes: List<String>?,
        previous_keys: List<String>?,
        legacy_keks: List<String>?,
    ) {
        synchronized(lock) {
            val p = prefs ?: return
            val e = p.edit()
            e.putString("${account_id}_$key_token_access", token_access)
            e.putString("${account_id}_$key_token_refresh", token_refresh)
            e.putString("${account_id}_$key_session_key", session_key?.let { encode_b64(it) })
            e.putString("${account_id}_$key_passphrase", passphrase?.let { encode_b64(it) })
            e.putString("${account_id}_$key_identity", identity_key)
            e.putString("${account_id}_$key_enc_vault", encrypted_vault)
            e.putString("${account_id}_$key_vault_nonce", vault_nonce)
            e.putString("${account_id}_$key_password_salt", password_salt?.let { encode_b64(it) })
            e.putString("${account_id}_$key_user_id", user_id)
            e.putString("${account_id}_$key_user_email", user_email)
            e.putString("${account_id}_$key_recovery_codes", recovery_codes?.joinToString("\n"))
            e.putString("${account_id}_$key_previous_keys", previous_keys?.joinToString("\n"))
            e.putString("${account_id}_$key_legacy_keks", legacy_keks?.joinToString("\n"))
            e.apply()
        }
    }

    fun has(account_id: String): Boolean = synchronized(lock) {
        val p = prefs ?: return false
        runCatching { p.getString("${account_id}_$key_token_access", null) != null }.getOrDefault(false)
    }

    fun load(account_id: String): SessionSnapshot? = synchronized(lock) {
        val p = prefs ?: return null
        val token_access = runCatching { p.getString("${account_id}_$key_token_access", null) }
            .getOrNull() ?: return null
        runCatching {
            SessionSnapshot(
                token_access = token_access,
                token_refresh = p.getString("${account_id}_$key_token_refresh", null) ?: token_access,
                session_key = p.getString("${account_id}_$key_session_key", null)?.let { decode_b64(it) },
                passphrase = p.getString("${account_id}_$key_passphrase", null)?.let { decode_b64(it) },
                identity_key = p.getString("${account_id}_$key_identity", null),
                encrypted_vault = p.getString("${account_id}_$key_enc_vault", null),
                vault_nonce = p.getString("${account_id}_$key_vault_nonce", null),
                password_salt = p.getString("${account_id}_$key_password_salt", null)?.let { decode_b64(it) },
                user_id = p.getString("${account_id}_$key_user_id", null),
                user_email = p.getString("${account_id}_$key_user_email", null),
                recovery_codes = p.getString("${account_id}_$key_recovery_codes", null)?.split("\n")?.filter { it.isNotBlank() },
                previous_keys = p.getString("${account_id}_$key_previous_keys", null)?.split("\n")?.filter { it.isNotBlank() },
                legacy_keks = p.getString("${account_id}_$key_legacy_keks", null)?.split("\n")?.filter { it.isNotBlank() },
            )
        }.getOrNull()
    }

    fun remove(account_id: String) {
        synchronized(lock) {
            val p = prefs ?: return
            val e = p.edit()
            listOf(
                key_token_access, key_token_refresh, key_session_key, key_passphrase,
                key_identity, key_enc_vault, key_vault_nonce, key_password_salt,
                key_user_id, key_user_email, key_recovery_codes, key_previous_keys, key_legacy_keks,
            ).forEach { e.remove("${account_id}_$it") }
            e.apply()
        }
    }

    private fun encode_b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode_b64(s: String): ByteArray =
        Base64.decode(s, Base64.DEFAULT)

    companion object {
        private const val prefs_name = "aster_session_snapshots_v1"
        private const val key_token_access = "token_access"
        private const val key_token_refresh = "token_refresh"
        private const val key_session_key = "session_key"
        private const val key_passphrase = "passphrase"
        private const val key_identity = "identity_key"
        private const val key_enc_vault = "encrypted_vault"
        private const val key_vault_nonce = "vault_nonce"
        private const val key_password_salt = "password_salt"
        private const val key_user_id = "user_id"
        private const val key_user_email = "user_email"
        private const val key_recovery_codes = "recovery_codes"
        private const val key_previous_keys = "previous_keys"
        private const val key_legacy_keks = "legacy_keks"
    }
}

data class SessionSnapshot(
    val token_access: String,
    val token_refresh: String,
    val session_key: ByteArray?,
    val passphrase: ByteArray?,
    val identity_key: String?,
    val encrypted_vault: String?,
    val vault_nonce: String?,
    val password_salt: ByteArray?,
    val user_id: String?,
    val user_email: String?,
    val recovery_codes: List<String>?,
    val previous_keys: List<String>?,
    val legacy_keks: List<String>?,
)
