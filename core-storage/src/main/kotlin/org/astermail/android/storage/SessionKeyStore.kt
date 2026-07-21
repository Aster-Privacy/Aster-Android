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

class SessionKeyStore(context: Context? = null) {

    private val lock = Any()
    private val prefs: SharedPreferences? = context?.let { SecurePrefs.open(it, prefs_name) }

    @Volatile
    private var key_material: ByteArray? = null

    @Volatile
    private var passphrase: ByteArray? = null

    @Volatile
    private var identity_key: String? = null

    @Volatile
    private var encrypted_vault: String? = null

    @Volatile
    private var vault_nonce: String? = null

    @Volatile
    private var password_salt: ByteArray? = null

    @Volatile
    private var user_id: String? = null

    @Volatile
    private var user_email: String? = null

    @Volatile
    private var recovery_codes: List<String>? = null

    @Volatile
    private var previous_keys: List<String>? = null

    @Volatile
    private var legacy_keks: List<String>? = null

    @Volatile
    private var data_kek: ByteArray? = null

    @Volatile
    private var ratchet_identity_jwk: String? = null

    @Volatile
    private var ratchet_identity_public_b64: String? = null

    @Volatile
    private var ratchet_signed_prekey_jwk: String? = null

    @Volatile
    private var ratchet_signed_prekey_public_b64: String? = null

    @Volatile
    private var ratchet_previous_keys_json: String? = null

    init {
        prefs?.let { p ->
            runCatching {
                key_material = decode_b64_field(p, key_session_key)
                passphrase = decode_b64_field(p, key_passphrase)
                identity_key = p.getString(key_identity, null)
                encrypted_vault = p.getString(key_enc_vault, null)
                vault_nonce = p.getString(key_vault_nonce, null)
                password_salt = decode_b64_field(p, key_password_salt)
                user_id = p.getString(key_user_id, null)
                user_email = p.getString(key_user_email, null)
                val saved_codes = p.getString(key_recovery_codes, null)
                if (saved_codes != null && saved_codes.isNotBlank()) {
                    recovery_codes = saved_codes.split("\n").filter { it.isNotBlank() }
                }
                val saved_prev = p.getString(key_previous_keys, null)
                if (saved_prev != null && saved_prev.isNotBlank()) {
                    previous_keys = try {
                        val arr = org.json.JSONArray(saved_prev)
                        (0 until arr.length()).map { arr.getString(it) }.filter { it.isNotBlank() }
                    } catch (_: Throwable) {
                        p.edit()?.remove(key_previous_keys)?.apply()
                        emptyList()
                    }
                }
                val saved_keks = p.getString(key_legacy_keks, null)
                if (saved_keks != null && saved_keks.isNotBlank()) {
                    legacy_keks = saved_keks.split("\n").filter { it.isNotBlank() }
                }
                data_kek = decode_b64_field(p, key_data_kek)
                ratchet_identity_jwk = p.getString(key_ratchet_identity_jwk, null)
                ratchet_identity_public_b64 = p.getString(key_ratchet_identity_pub, null)
                ratchet_signed_prekey_jwk = p.getString(key_ratchet_spk_jwk, null)
                ratchet_signed_prekey_public_b64 = p.getString(key_ratchet_spk_pub, null)
                ratchet_previous_keys_json = p.getString(key_ratchet_previous_keys, null)
            }.onFailure { t ->
                if (org.astermail.android.storage.BuildConfig.DEBUG) {
                    android.util.Log.e("SessionKeyStore", "Failed to load session keys", t)
                }
            }
        }
    }

    fun put_ratchet_keys(
        identity_jwk: String,
        identity_public_b64: String,
        signed_prekey_jwk: String,
        signed_prekey_public_b64: String,
    ) {
        synchronized(lock) {
            ratchet_identity_jwk = identity_jwk
            ratchet_identity_public_b64 = identity_public_b64
            ratchet_signed_prekey_jwk = signed_prekey_jwk
            ratchet_signed_prekey_public_b64 = signed_prekey_public_b64
            prefs?.edit()
                ?.putString(key_ratchet_identity_jwk, identity_jwk)
                ?.putString(key_ratchet_identity_pub, identity_public_b64)
                ?.putString(key_ratchet_spk_jwk, signed_prekey_jwk)
                ?.putString(key_ratchet_spk_pub, signed_prekey_public_b64)
                ?.apply()
        }
    }

    fun put_ratchet_previous_keys(previous_keys_json: String?) {
        synchronized(lock) {
            ratchet_previous_keys_json = previous_keys_json
            val editor = prefs?.edit() ?: return
            if (previous_keys_json.isNullOrBlank()) {
                editor.remove(key_ratchet_previous_keys).apply()
            } else {
                editor.putString(key_ratchet_previous_keys, previous_keys_json).apply()
            }
        }
    }

    fun get_ratchet_previous_keys_json(): String? = synchronized(lock) { ratchet_previous_keys_json }

    fun get_ratchet_identity_jwk(): String? = synchronized(lock) { ratchet_identity_jwk }
    fun get_ratchet_identity_public_b64(): String? = synchronized(lock) { ratchet_identity_public_b64 }
    fun get_ratchet_signed_prekey_jwk(): String? = synchronized(lock) { ratchet_signed_prekey_jwk }
    fun get_ratchet_signed_prekey_public_b64(): String? = synchronized(lock) { ratchet_signed_prekey_public_b64 }

    fun put_pq_secret(key_id: Int, secret: ByteArray) {
        synchronized(lock) {
            prefs?.edit()?.putString("$key_pq_secret_prefix$key_id", encode_b64(secret))?.apply()
        }
    }

    fun get_pq_secret(key_id: Int): ByteArray? {
        synchronized(lock) {
            val s = prefs?.getString("$key_pq_secret_prefix$key_id", null) ?: return null
            return runCatching { decode_b64(s) }.getOrNull()
        }
    }

    fun has_ratchet_keys(): Boolean = synchronized(lock) {
        ratchet_identity_jwk != null &&
            ratchet_signed_prekey_jwk != null &&
            ratchet_signed_prekey_public_b64 != null
    }

    fun put(bytes: ByteArray) {
        synchronized(lock) {
            key_material?.let { it.fill(0) }
            key_material = bytes.copyOf()
            prefs?.edit()?.putString(key_session_key, encode_b64(bytes))?.apply()
        }
    }

    fun put_passphrase(bytes: ByteArray) {
        synchronized(lock) {
            passphrase?.let { it.fill(0) }
            passphrase = bytes.copyOf()
            prefs?.edit()?.putString(key_passphrase, encode_b64(bytes))?.apply()
        }
    }

    fun put_identity_key(key: String) {
        synchronized(lock) {
            identity_key = key
            prefs?.edit()?.putString(key_identity, key)?.apply()
        }
    }

    fun put_encrypted_vault(encrypted_vault_b64: String, vault_nonce_b64: String) {
        synchronized(lock) {
            this.encrypted_vault = encrypted_vault_b64
            this.vault_nonce = vault_nonce_b64
            prefs?.edit()
                ?.putString(key_enc_vault, encrypted_vault_b64)
                ?.putString(key_vault_nonce, vault_nonce_b64)
                ?.apply()
        }
    }

    fun put_password_salt(salt: ByteArray) {
        synchronized(lock) {
            password_salt?.fill(0)
            password_salt = salt.copyOf()
            prefs?.edit()?.putString(key_password_salt, encode_b64(salt))?.apply()
        }
    }

    fun put_user_id(id: String) {
        synchronized(lock) {
            user_id = id
            prefs?.edit()?.putString(key_user_id, id)?.apply()
        }
    }

    fun put_user_email(email: String) {
        synchronized(lock) {
            user_email = email
            prefs?.edit()?.putString(key_user_email, email)?.apply()
        }
    }

    fun put_recovery_codes(codes: List<String>) {
        synchronized(lock) {
            recovery_codes = codes.toList()
            prefs?.edit()
                ?.putString(key_recovery_codes, codes.joinToString("\n"))
                ?.apply()
        }
    }

    fun put_previous_keys(keys: List<String>) {
        synchronized(lock) {
            previous_keys = keys.toList()
            val json_arr = org.json.JSONArray()
            keys.forEach { json_arr.put(it) }
            prefs?.edit()
                ?.putString(key_previous_keys, json_arr.toString())
                ?.apply()
        }
    }

    fun get_previous_keys(): List<String>? {
        synchronized(lock) {
            return previous_keys?.toList()
        }
    }

    fun put_legacy_keks(keys: List<String>) {
        synchronized(lock) {
            legacy_keks = keys.toList()
            prefs?.edit()
                ?.putString(key_legacy_keks, keys.joinToString("\n"))
                ?.apply()
        }
    }

    fun get_legacy_keks(): List<String>? {
        synchronized(lock) {
            return legacy_keks?.toList()
        }
    }

    fun put_data_kek(kek: ByteArray) {
        synchronized(lock) {
            data_kek?.fill(0)
            data_kek = kek.copyOf()
            prefs?.edit()?.putString(key_data_kek, encode_b64(kek))?.apply()
        }
    }

    fun get_data_kek(): ByteArray? {
        synchronized(lock) {
            return data_kek?.copyOf()
        }
    }

    fun get(): ByteArray? {
        synchronized(lock) {
            return key_material?.copyOf()
        }
    }

    fun get_passphrase(): ByteArray? {
        synchronized(lock) {
            return passphrase?.copyOf()
        }
    }

    fun get_identity_key(): String? {
        synchronized(lock) {
            return identity_key
        }
    }

    fun get_encrypted_vault(): Pair<String, String>? {
        synchronized(lock) {
            val v = encrypted_vault ?: return null
            val n = vault_nonce ?: return null
            return v to n
        }
    }

    fun get_password_salt(): ByteArray? {
        synchronized(lock) {
            return password_salt?.copyOf()
        }
    }

    fun get_user_id(): String? {
        synchronized(lock) {
            return user_id
        }
    }

    fun get_user_email(): String? {
        synchronized(lock) {
            return user_email
        }
    }

    fun get_recovery_codes(): List<String>? {
        synchronized(lock) {
            return recovery_codes?.toList()
        }
    }

    fun has(): Boolean {
        synchronized(lock) {
            return key_material != null
        }
    }

    fun clear() {
        synchronized(lock) {
            key_material?.fill(0)
            key_material = null
            passphrase?.fill(0)
            passphrase = null
            identity_key = null
            encrypted_vault = null
            vault_nonce = null
            password_salt?.fill(0)
            password_salt = null
            user_id = null
            user_email = null
            recovery_codes = null
            previous_keys = null
            legacy_keks = null
            data_kek?.fill(0)
            data_kek = null
            ratchet_identity_jwk = null
            ratchet_identity_public_b64 = null
            ratchet_signed_prekey_jwk = null
            ratchet_signed_prekey_public_b64 = null
            ratchet_previous_keys_json = null
            prefs?.edit()?.clear()?.apply()
        }
    }

    private fun encode_b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode_b64(s: String): ByteArray =
        Base64.decode(s, Base64.DEFAULT)

    private fun decode_b64_field(p: SharedPreferences, key: String): ByteArray? =
        runCatching { p.getString(key, null)?.let { decode_b64(it) } }.getOrNull()

    companion object {
        private const val prefs_name = "aster_session_keys_v1"
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
        private const val key_data_kek = "data_kek"
        private const val key_ratchet_identity_jwk = "ratchet_identity_jwk"
        private const val key_ratchet_identity_pub = "ratchet_identity_pub"
        private const val key_ratchet_spk_jwk = "ratchet_spk_jwk"
        private const val key_ratchet_spk_pub = "ratchet_spk_pub"
        private const val key_ratchet_previous_keys = "ratchet_previous_keys"
        private const val key_pq_secret_prefix = "pq_secret_"
    }
}
