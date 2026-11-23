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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

data class AccountKeyLoadTicket(val seq: Long, val write_epoch: Long)

class SessionKeyStore(context: Context? = null) {

    private val lock = Any()

    private val clear_listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()

    fun add_clear_listener(listener: () -> Unit) {
        clear_listeners.addIfAbsent(listener)
    }
    private val prefs: SealedPrefs? = context?.let {
        SealedPrefs(SecurePrefs.open(it, prefs_name), prefs_name) { name -> name in plain_names }
    }

    fun has_sealed_values(): Boolean = prefs?.has_sealed_values() ?: false

    fun is_locked(): Boolean = !LockKeyHolder.is_open() && has_sealed_values()

    fun seal_persisted(): Boolean = synchronized(lock) { prefs?.seal_all() ?: true }

    fun unseal_persisted(lock_key: ByteArray? = null): Boolean =
        synchronized(lock) { prefs?.unseal_all(lock_key) ?: true }

    private val vault_salt_prefs: SharedPreferences? =
        context?.getSharedPreferences(vault_salts_prefs_name, Context.MODE_PRIVATE)

    @Volatile
    private var remembered_vault_salts: List<String> = emptyList()

    private val retired_login_salt_prefs: SharedPreferences? =
        context?.getSharedPreferences(retired_login_salts_prefs_name, Context.MODE_PRIVATE)

    @Volatile
    private var retired_login_salts: List<String> = emptyList()

    private val pin_context: Context? = context?.applicationContext ?: context

    private val mail_legacy_retired_file = KeyPinFile(pin_context, mail_legacy_retired_prefs_name)

    private val mail_legacy_retired_prefs: SharedPreferences?
        get() = mail_legacy_retired_file.prefs

    private val mail_legacy_retired_owners = HashSet<String>()

    private val folder_key_pin_file = KeyPinFile(pin_context, folder_key_pins_prefs_name)

    private val folder_key_pin_prefs: SharedPreferences?
        get() = folder_key_pin_file.prefs

    private val scheduled_untagged_file = KeyPinFile(pin_context, scheduled_untagged_prefs_name)

    private val scheduled_untagged_snapshots = HashMap<String, ScheduledUntaggedSnapshot>()

    private val legacy_folder_kdf_pin_prefs: SharedPreferences? =
        context?.getSharedPreferences(legacy_folder_kdf_pins_prefs_name, Context.MODE_PRIVATE)

    private val folder_key_pins = HashMap<String, Int>()

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
    private var vault_kdf_iterations: Int = default_vault_kdf_iterations

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
    private var account_keks: List<String>? = null

    @Volatile
    private var account_key_serial_floor = 0L

    @Volatile
    private var level2_admitted = false

    @Volatile
    private var account_kek_generation: Long = 0

    private val account_write_keks = HashMap<String, ByteArray>()

    @Volatile
    private var account_write_epoch: Long = 0

    @Volatile
    private var account_load_seq: Long = 0

    private val account_load_pending = MutableStateFlow(false)

    private var account_keys_ready_ticket: AccountKeyLoadTicket? = null

    private val account_keys_ready_count = MutableStateFlow(0L)

    private val identity_key_count = MutableStateFlow(0L)

    @Volatile
    private var data_kek: ByteArray? = null
    private var storage_kdf_version: Int? = null

    @Volatile
    private var legacy_storage_keys_retired = false

    @Volatile
    private var retired_storage_key_hashes: List<String> = emptyList()

    @Volatile
    private var prekey_seal_key: ByteArray? = null

    @Volatile
    private var prekey_seal_migrated = false

    @Volatile
    private var pending_reseal_passphrase: ByteArray? = null

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

    @Volatile
    private var ratchet_pq_identity_secret: String? = null

    @Volatile
    private var ratchet_pq_identity_public: String? = null

    @Volatile
    private var external_pgp_blob: String? = null

    init {
        runCatching {
            remembered_vault_salts = VaultSaltMemory.parse(vault_salt_prefs?.getString(key_vault_salts, null))
        }
        runCatching {
            retired_login_salts = RetiredLoginSalts.parse(
                retired_login_salt_prefs?.getString(key_retired_login_salts, null),
            )
        }
        load_persisted()
        if (prefs != null) {
            LockKeyHolder.add_open_listener { synchronized(lock) { load_persisted() } }
        }
    }

    private fun load_persisted() {
        prefs?.let { p ->
            runCatching {
                key_material = decode_b64_field(p, key_session_key)
                passphrase = decode_b64_field(p, key_passphrase)
                set_identity_key_locked(p.getString(key_identity, null))
                encrypted_vault = p.getString(key_enc_vault, null)
                encrypted_vault?.let { remember_vault_salt_locked(it) }
                vault_nonce = p.getString(key_vault_nonce, null)
                vault_kdf_iterations = sanitize_vault_kdf_iterations(
                    p.getInt(key_vault_kdf_iterations, default_vault_kdf_iterations),
                )
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
                val saved_account_keks = p.getString(key_account_keks, null)
                if (!saved_account_keks.isNullOrBlank()) {
                    account_keks = saved_account_keks.split("\n").filter { it.isNotBlank() }
                }
                account_key_serial_floor = p.getString(key_account_key_serial_floor, null)?.toLongOrNull() ?: 0L
                data_kek = decode_b64_field(p, key_data_kek)
                storage_kdf_version = if (p.contains(key_storage_kdf_version)) p.getInt(key_storage_kdf_version, 1) else null
                legacy_storage_keys_retired = p.getBoolean(key_legacy_storage_keys_retired, false)
                retired_storage_key_hashes = p.getString(key_retired_storage_key_hashes, null)
                    ?.split("\n")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
                prekey_seal_key = decode_b64_field(p, key_prekey_seal_key)?.takeIf { it.size == 32 }
                prekey_seal_migrated = p.getBoolean(key_prekey_seal_migrated, false)
                pending_reseal_passphrase = decode_b64_field(p, key_pending_reseal_pass)
                ratchet_identity_jwk = p.getString(key_ratchet_identity_jwk, null)
                ratchet_identity_public_b64 = p.getString(key_ratchet_identity_pub, null)
                ratchet_signed_prekey_jwk = p.getString(key_ratchet_spk_jwk, null)
                ratchet_signed_prekey_public_b64 = p.getString(key_ratchet_spk_pub, null)
                ratchet_previous_keys_json = p.getString(key_ratchet_previous_keys, null)
                ratchet_pq_identity_secret = p.getString(key_ratchet_pq_identity_priv, null)
                ratchet_pq_identity_public = p.getString(key_ratchet_pq_identity_pub, null)
                external_pgp_blob = p.getString(key_external_pgp_blob, null)
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
        signed_prekey_jwk: String? = null,
        signed_prekey_public_b64: String? = null,
    ) {
        synchronized(lock) {
            ratchet_identity_jwk = identity_jwk
            ratchet_identity_public_b64 = identity_public_b64.takeIf { it.isNotBlank() }
            val editor = prefs?.edit()
            editor?.putString(key_ratchet_identity_jwk, identity_jwk)
            if (ratchet_identity_public_b64 == null) {
                editor?.remove(key_ratchet_identity_pub)
            } else {
                editor?.putString(key_ratchet_identity_pub, ratchet_identity_public_b64)
            }
            if (!signed_prekey_jwk.isNullOrBlank()) {
                ratchet_signed_prekey_jwk = signed_prekey_jwk
                editor?.putString(key_ratchet_spk_jwk, signed_prekey_jwk)
            }
            if (!signed_prekey_public_b64.isNullOrBlank()) {
                ratchet_signed_prekey_public_b64 = signed_prekey_public_b64
                editor?.putString(key_ratchet_spk_pub, signed_prekey_public_b64)
            }
            editor?.commit()
        }
    }

    fun put_ratchet_previous_keys(previous_keys_json: String?) {
        synchronized(lock) {
            ratchet_previous_keys_json = previous_keys_json
            val editor = prefs?.edit() ?: return
            if (previous_keys_json.isNullOrBlank()) {
                editor.remove(key_ratchet_previous_keys).commit()
            } else {
                editor.putString(key_ratchet_previous_keys, previous_keys_json).commit()
            }
        }
    }

    fun put_ratchet_pq_identity(secret_b64: String?, public_b64: String?) {
        synchronized(lock) {
            ratchet_pq_identity_secret = secret_b64?.takeIf { it.isNotBlank() }
            ratchet_pq_identity_public = public_b64?.takeIf { it.isNotBlank() }
            val editor = prefs?.edit() ?: return
            if (ratchet_pq_identity_secret == null) {
                editor.remove(key_ratchet_pq_identity_priv)
            } else {
                editor.putString(key_ratchet_pq_identity_priv, ratchet_pq_identity_secret)
            }
            if (ratchet_pq_identity_public == null) {
                editor.remove(key_ratchet_pq_identity_pub)
            } else {
                editor.putString(key_ratchet_pq_identity_pub, ratchet_pq_identity_public)
            }
            editor.commit()
        }
    }

    fun get_ratchet_pq_identity_secret(): String? = synchronized(lock) { ratchet_pq_identity_secret }
    fun get_ratchet_pq_identity_public(): String? = synchronized(lock) { ratchet_pq_identity_public }

    fun get_ratchet_previous_keys_json(): String? = synchronized(lock) { ratchet_previous_keys_json }

    fun get_ratchet_identity_jwk(): String? = synchronized(lock) { ratchet_identity_jwk }
    fun get_ratchet_identity_public_b64(): String? = synchronized(lock) { ratchet_identity_public_b64 }
    fun get_ratchet_signed_prekey_jwk(): String? = synchronized(lock) { ratchet_signed_prekey_jwk }
    fun get_ratchet_signed_prekey_public_b64(): String? = synchronized(lock) { ratchet_signed_prekey_public_b64 }

    fun put_pq_secret(key_id: Int, secret: ByteArray) {
        synchronized(lock) {
            prefs?.edit()?.putString("$key_pq_secret_prefix$key_id", encode_b64(secret))?.commit()
        }
    }

    fun get_pq_secret(key_id: Int): ByteArray? {
        synchronized(lock) {
            val s = prefs?.getString("$key_pq_secret_prefix$key_id", null) ?: return null
            return runCatching { decode_b64(s) }.getOrNull()
        }
    }

    fun remove_pq_secret(key_id: Int) {
        synchronized(lock) {
            prefs?.edit()?.remove("$key_pq_secret_prefix$key_id")?.commit()
        }
    }

    fun ec_one_time_storage_available(): Boolean = synchronized(lock) { prefs != null } && !is_locked()

    fun put_ec_one_time_secret(key_id: Int, scalar: ByteArray): Boolean {
        synchronized(lock) {
            return prefs?.edit()?.putString("$key_ec_one_time_secret_prefix$key_id", encode_b64(scalar))?.commit()
                ?: false
        }
    }

    fun get_ec_one_time_secret(key_id: Int): ByteArray? {
        synchronized(lock) {
            val s = prefs?.getString("$key_ec_one_time_secret_prefix$key_id", null) ?: return null
            return runCatching { decode_b64(s) }.getOrNull()
        }
    }

    fun remove_ec_one_time_secret(key_id: Int) {
        synchronized(lock) {
            prefs?.edit()?.remove("$key_ec_one_time_secret_prefix$key_id")?.commit()
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
            prefs?.edit()?.putString(key_session_key, encode_b64(bytes))?.commit()
        }
    }

    fun put_passphrase(bytes: ByteArray) {
        synchronized(lock) {
            passphrase?.let { it.fill(0) }
            passphrase = bytes.copyOf()
            prefs?.edit()?.putString(key_passphrase, encode_b64(bytes))?.commit()
        }
    }

    fun put_pending_reseal_passphrase(bytes: ByteArray) {
        synchronized(lock) {
            pending_reseal_passphrase?.let { it.fill(0) }
            pending_reseal_passphrase = bytes.copyOf()
            prefs?.edit()?.putString(key_pending_reseal_pass, encode_b64(bytes))?.commit()
        }
    }

    fun get_pending_reseal_passphrase(): ByteArray? {
        synchronized(lock) {
            return pending_reseal_passphrase?.copyOf()
        }
    }

    fun clear_pending_reseal_passphrase() {
        synchronized(lock) {
            pending_reseal_passphrase?.fill(0)
            pending_reseal_passphrase = null
            prefs?.edit()?.remove(key_pending_reseal_pass)?.commit()
        }
    }

    fun put_identity_key(key: String) {
        synchronized(lock) {
            if (identity_key != key) drop_account_write_keks_locked()
            set_identity_key_locked(key)
            prefs?.edit()?.putString(key_identity, key)?.commit()
        }
    }

    fun put_encrypted_vault(
        encrypted_vault_b64: String,
        vault_nonce_b64: String,
        kdf_iterations: Int = default_vault_kdf_iterations,
    ) {
        synchronized(lock) {
            if (prefs?.drops_write(key_enc_vault) == true) return
            val iterations = sanitize_vault_kdf_iterations(kdf_iterations)
            this.encrypted_vault = encrypted_vault_b64
            this.vault_nonce = vault_nonce_b64
            this.vault_kdf_iterations = iterations
            remember_vault_salt_locked(encrypted_vault_b64)
            prefs?.edit()
                ?.putString(key_enc_vault, encrypted_vault_b64)
                ?.putString(key_vault_nonce, vault_nonce_b64)
                ?.putInt(key_vault_kdf_iterations, iterations)
                ?.commit()
        }
    }

    fun get_vault_kdf_iterations(): Int {
        synchronized(lock) {
            return vault_kdf_iterations
        }
    }

    fun get_remembered_vault_salts(): List<ByteArray> {
        synchronized(lock) {
            return VaultSaltMemory.decode(remembered_vault_salts)
        }
    }

    private fun remember_vault_salt_locked(encrypted_vault_b64: String) {
        val updated = VaultSaltMemory.remember(remembered_vault_salts, encrypted_vault_b64)
        if (updated == remembered_vault_salts) return
        remembered_vault_salts = updated
        vault_salt_prefs?.edit()?.putString(key_vault_salts, VaultSaltMemory.serialize(updated))?.commit()
    }

    fun retire_password_salts(owner_id: String, salts: List<ByteArray>) {
        synchronized(lock) {
            val updated = RetiredLoginSalts.remember_all(retired_login_salts, owner_id, salts)
            if (updated == retired_login_salts) return
            retired_login_salts = updated
            retired_login_salt_prefs?.edit()
                ?.putString(key_retired_login_salts, RetiredLoginSalts.serialize(updated))
                ?.commit()
        }
    }

    fun pin_mail_legacy_retired(owner_id: String) {
        if (owner_id.isBlank()) return
        synchronized(lock) {
            if (!mail_legacy_retired_owners.add(owner_id)) return
            mail_legacy_retired_file.readable()
            val saved = runCatching { mail_legacy_retired_prefs?.edit()?.putBoolean(owner_id, true)?.commit() == true }
                .getOrDefault(false)
            if (!saved && pin_context != null) android.util.Log.w("SessionKeyStore", "retirement pin kept in memory only")
        }
    }

    fun mail_legacy_retired(): Boolean {
        synchronized(lock) {
            if (!mail_legacy_retired_file.readable()) return true
            val owner_id = user_id?.takeIf { it.isNotBlank() } ?: return any_mail_legacy_retired()
            if (owner_id in mail_legacy_retired_owners) return true
            val pinned = runCatching { mail_legacy_retired_prefs?.getBoolean(owner_id, false) == true }
                .getOrDefault(false)
            if (pinned) mail_legacy_retired_owners.add(owner_id)
            return pinned
        }
    }

    private fun any_mail_legacy_retired(): Boolean {
        if (mail_legacy_retired_owners.isNotEmpty()) return true
        return runCatching { mail_legacy_retired_prefs?.all?.values?.any { it == true } == true }
            .getOrDefault(false)
    }

    fun key_pins_readable(): Boolean = synchronized(lock) {
        mail_legacy_retired_file.readable() && folder_key_pin_file.readable() && scheduled_untagged_file.readable()
    }

    fun mark_key_pins_restored_from_vault() {
        synchronized(lock) {
            mail_legacy_retired_file.restore()
            folder_key_pin_file.restore()
            scheduled_untagged_file.restore()
        }
    }

    fun folder_key_pin_known(folder_id: String): Int? {
        synchronized(lock) {
            if (!folder_key_pin_file.readable()) return null
            return recorded_folder_key_pin(folder_id)
        }
    }

    fun folder_key_pin(folder_id: String): Int = folder_key_pin_known(folder_id) ?: max_folder_key_pin

    private fun recorded_folder_key_pin(folder_id: String): Int {
        val key = folder_id.lowercase(java.util.Locale.ROOT)
        val stored = runCatching { folder_key_pin_prefs?.getInt(key, 0) ?: 0 }.getOrDefault(0)
        val legacy = runCatching {
            val p = legacy_folder_kdf_pin_prefs
            p != null && (p.getBoolean(folder_id, false) || p.getBoolean(key, false))
        }.getOrDefault(false)
        return maxOf(folder_key_pins[key] ?: 0, stored, if (legacy) 1 else 0)
    }

    fun raise_folder_key_pin(folder_id: String, level: Int): Boolean {
        if (folder_id.isBlank() || level !in 1..max_folder_key_pin) return false
        val key = folder_id.lowercase(java.util.Locale.ROOT)
        synchronized(lock) {
            if (recorded_folder_key_pin(folder_id) >= level) return false
            folder_key_pins[key] = level
            if (pin_context == null) return true
            folder_key_pin_file.readable()
            val saved = runCatching { folder_key_pin_prefs?.edit()?.putInt(key, level)?.commit() == true }
                .getOrDefault(false)
            if (!saved) android.util.Log.w("SessionKeyStore", "folder pin kept in memory only")
            return saved
        }
    }

    fun scheduled_untagged_snapshot(): ScheduledUntaggedSnapshot? {
        synchronized(lock) {
            val owner_id = user_id?.takeIf { it.isNotBlank() } ?: return ScheduledUntaggedSnapshot.EMPTY
            if (!scheduled_untagged_file.readable()) return ScheduledUntaggedSnapshot.EMPTY
            return recorded_scheduled_untagged(owner_id)
        }
    }

    fun put_scheduled_untagged_snapshot(snapshot: ScheduledUntaggedSnapshot): ScheduledUntaggedSnapshot {
        synchronized(lock) {
            val owner_id = user_id?.takeIf { it.isNotBlank() } ?: return ScheduledUntaggedSnapshot.EMPTY
            if (!scheduled_untagged_file.readable()) return ScheduledUntaggedSnapshot.EMPTY
            recorded_scheduled_untagged(owner_id)?.let { return it }
            store_scheduled_untagged(owner_id, snapshot)
            return snapshot
        }
    }

    fun absorb_scheduled_untagged_snapshot(snapshot: ScheduledUntaggedSnapshot?, replace: Boolean) {
        if (snapshot == null) return
        synchronized(lock) {
            val owner_id = user_id?.takeIf { it.isNotBlank() } ?: return
            if (!replace && recorded_scheduled_untagged(owner_id) != null) return
            store_scheduled_untagged(owner_id, snapshot)
        }
    }

    private fun recorded_scheduled_untagged(owner_id: String): ScheduledUntaggedSnapshot? {
        scheduled_untagged_snapshots[owner_id]?.let { return it }
        val raw = runCatching { scheduled_untagged_file.prefs?.getString(owner_id, null) }.getOrNull() ?: return null
        val shared = runCatching { ScheduledUntaggedSnapshot.from_json(org.json.JSONObject(raw)) }.getOrNull()
        if (shared != null) {
            scheduled_untagged_snapshots[owner_id] = shared
            return shared
        }
        val listed = runCatching { snapshot_id_list(org.json.JSONArray(raw)) }.getOrNull()
        if (listed == null) {
            scheduled_untagged_snapshots[owner_id] = ScheduledUntaggedSnapshot.EMPTY
            return ScheduledUntaggedSnapshot.EMPTY
        }
        val migrated = ScheduledUntaggedSnapshot.taken_at(listed, System.currentTimeMillis())
        store_scheduled_untagged(owner_id, migrated)
        return migrated
    }

    private fun store_scheduled_untagged(owner_id: String, snapshot: ScheduledUntaggedSnapshot) {
        scheduled_untagged_snapshots[owner_id] = snapshot
        if (pin_context == null) return
        scheduled_untagged_file.readable()
        val saved = runCatching {
            scheduled_untagged_file.prefs?.edit()
                ?.putString(owner_id, snapshot.to_json().toString())
                ?.commit() == true
        }.getOrDefault(false)
        if (!saved) android.util.Log.w("SessionKeyStore", "scheduled snapshot kept in memory only")
    }

    fun get_retired_password_salts(): List<ByteArray> {
        synchronized(lock) {
            val owner_id = user_id ?: return emptyList()
            return RetiredLoginSalts.salts_for(retired_login_salts, owner_id)
        }
    }

    fun is_login_salt(salt: ByteArray): Boolean {
        if (salt.isEmpty()) return false
        synchronized(lock) {
            if (password_salt?.let { java.security.MessageDigest.isEqual(it, salt) } == true) return true
            val owner_id = user_id ?: return false
            val retired = RetiredLoginSalts.salts_for(retired_login_salts, owner_id)
            try {
                return retired.any { java.security.MessageDigest.isEqual(it, salt) }
            } finally {
                retired.forEach { it.fill(0) }
            }
        }
    }

    fun put_password_salt(salt: ByteArray) {
        synchronized(lock) {
            password_salt?.fill(0)
            password_salt = salt.copyOf()
            prefs?.edit()?.putString(key_password_salt, encode_b64(salt))?.commit()
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
                ?.commit()
        }
    }

    fun put_previous_keys(keys: List<String>) {
        synchronized(lock) {
            if (previous_keys != keys) drop_account_write_keks_locked()
            previous_keys = keys.toList()
            val json_arr = org.json.JSONArray()
            keys.forEach { json_arr.put(it) }
            prefs?.edit()
                ?.putString(key_previous_keys, json_arr.toString())
                ?.commit()
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
                ?.commit()
        }
    }

    fun get_legacy_keks(): List<String>? {
        synchronized(lock) {
            return legacy_keks?.let { RetiredStorageKeys.not_retired(it, retired_storage_key_hashes) }
        }
    }

    fun account_kek_generation(): Long {
        synchronized(lock) {
            return account_kek_generation
        }
    }

    fun put_account_keks(keys: List<String>, generation: Long): Boolean {
        synchronized(lock) {
            if (generation != account_kek_generation) return false
            account_keks = keys.toList()
            prefs?.edit()
                ?.putString(key_account_keks, keys.joinToString("\n"))
                ?.commit()
            return true
        }
    }

    fun account_key_serial_floor(): Long {
        synchronized(lock) {
            return account_key_serial_floor
        }
    }

    fun raise_account_key_serial_floor(serial: Long, generation: Long): Boolean {
        synchronized(lock) {
            if (generation != account_kek_generation) return false
            if (serial <= account_key_serial_floor) return true
            account_key_serial_floor = serial
            prefs?.edit()
                ?.putString(key_account_key_serial_floor, serial.toString())
                ?.commit()
            return true
        }
    }

    fun get_account_keks(): List<String>? {
        synchronized(lock) {
            return account_keks?.toList()
        }
    }

    fun begin_account_key_load(): AccountKeyLoadTicket {
        synchronized(lock) {
            drop_account_write_keks_locked()
            account_load_seq += 1
            account_load_pending.value = true
            return AccountKeyLoadTicket(account_load_seq, account_write_epoch)
        }
    }

    fun finish_account_key_load(ticket: AccountKeyLoadTicket) {
        synchronized(lock) {
            if (ticket.seq == account_load_seq) account_load_pending.value = false
            if (ticket.seq == account_load_seq && ticket.write_epoch == account_write_epoch && account_keys_ready_ticket == ticket) {
                account_keys_ready_count.value = account_keys_ready_count.value + 1
            }
        }
    }

    fun account_keys_ready_events(): StateFlow<Long> = account_keys_ready_count

    fun identity_key_events(): StateFlow<Long> = identity_key_count

    private fun set_identity_key_locked(key: String?) {
        if (identity_key == key) return
        identity_key = key
        identity_key_count.value = identity_key_count.value + 1
    }

    fun account_key_load_pending(): Boolean = account_load_pending.value

    fun complete_account_key_load(ticket: AccountKeyLoadTicket, generation: Long): Boolean {
        synchronized(lock) {
            if (generation != account_kek_generation) return false
            if (ticket.seq != account_load_seq || ticket.write_epoch != account_write_epoch) return false
            account_keys_ready_ticket = ticket
            return true
        }
    }

    fun account_keys_ready(): Boolean {
        synchronized(lock) {
            val ticket = account_keys_ready_ticket ?: return false
            return !account_load_pending.value &&
                ticket.seq == account_load_seq &&
                ticket.write_epoch == account_write_epoch
        }
    }

    private val constant_use_prefs: SharedPreferences? =
        context?.getSharedPreferences(constant_use_prefs_name, Context.MODE_PRIVATE)

    private val constant_use_pending = HashSet<String>()

    @Volatile
    private var constant_use_seq: Long = 0

    fun note_public_constant_use() {
        synchronized(lock) {
            val id = user_id ?: return
            constant_use_seq += 1
            if (constant_use_pending.add(id)) {
                constant_use_prefs?.edit()?.putBoolean(constant_use_prefix + id, true)?.apply()
            }
        }
    }

    fun public_constant_use_pending(): Boolean {
        synchronized(lock) {
            val id = user_id ?: return false
            return id in constant_use_pending ||
                runCatching { constant_use_prefs?.getBoolean(constant_use_prefix + id, false) }.getOrNull() == true
        }
    }

    fun public_constant_use_seq(): Long = constant_use_seq

    fun clear_public_constant_use(seen_seq: Long) {
        synchronized(lock) {
            if (seen_seq != constant_use_seq) return
            val id = user_id ?: return
            constant_use_pending.remove(id)
            constant_use_prefs?.edit()?.remove(constant_use_prefix + id)?.apply()
        }
    }

    suspend fun await_account_key_load(timeout_ms: Long) {
        withTimeoutOrNull(timeout_ms) { account_load_pending.first { !it } }
    }

    fun put_account_write_keks(
        keys: Map<String, ByteArray>,
        generation: Long,
        ticket: AccountKeyLoadTicket,
    ): Boolean {
        synchronized(lock) {
            if (generation != account_kek_generation) return false
            if (ticket.seq != account_load_seq || ticket.write_epoch != account_write_epoch) return false
            account_write_keks.values.forEach { it.fill(0) }
            account_write_keks.clear()
            keys.forEach { (context, key) -> account_write_keks[context] = key.copyOf() }
            return true
        }
    }

    fun has_account_write_kek(context: String): Boolean {
        synchronized(lock) {
            return account_write_keks.containsKey(context)
        }
    }

    fun get_account_write_kek(context: String): ByteArray? {
        synchronized(lock) {
            return account_write_keks[context]?.copyOf()
        }
    }

    private fun drop_account_write_keks_locked() {
        account_write_keks.values.forEach { it.fill(0) }
        account_write_keks.clear()
        account_write_epoch += 1
    }

    fun get_decrypt_keks(): List<String> {
        synchronized(lock) {
            return RetiredStorageKeys.not_retired(
                (legacy_keks.orEmpty() + account_keks.orEmpty()).distinct(),
                retired_storage_key_hashes,
            )
        }
    }

    fun put_data_kek(kek: ByteArray) {
        synchronized(lock) {
            data_kek?.fill(0)
            data_kek = kek.copyOf()
            prefs?.edit()?.putString(key_data_kek, encode_b64(kek))?.commit()
        }
    }

    fun put_external_pgp_blob(json: String?) {
        synchronized(lock) {
            external_pgp_blob = json
            prefs?.edit()?.apply {
                if (json == null) remove(key_external_pgp_blob) else putString(key_external_pgp_blob, json)
            }?.commit()
        }
    }

    fun get_external_pgp_blob(): String? = synchronized(lock) { external_pgp_blob }

    fun put_storage_kdf_version(version: Int) {
        synchronized(lock) {
            storage_kdf_version = version
            prefs?.edit()?.putInt(key_storage_kdf_version, version)?.commit()
        }
    }

    fun get_storage_kdf_version(): Int? = synchronized(lock) { storage_kdf_version }

    fun retire_legacy_storage_keys(key_hashes: List<String>): Boolean {
        synchronized(lock) {
            val merged = (retired_storage_key_hashes + key_hashes.filter { it.isNotBlank() }).distinct()
            val committed = prefs?.let { store ->
                runCatching {
                    store.edit()
                        .putString(key_retired_storage_key_hashes, merged.joinToString("\n"))
                        .putBoolean(key_legacy_storage_keys_retired, true)
                        .commit()
                }.getOrDefault(false)
            } ?: true
            if (!committed) return false
            retired_storage_key_hashes = merged
            legacy_storage_keys_retired = true
            return true
        }
    }

    fun legacy_storage_keys_retired(): Boolean = legacy_storage_keys_retired

    fun retired_storage_key_hashes(): List<String> = retired_storage_key_hashes

    fun put_prekey_seal(key: ByteArray?, migrated: Boolean) {
        synchronized(lock) {
            val editor = prefs?.edit()
            if (key != null && key.size == 32) {
                prekey_seal_key?.fill(0)
                prekey_seal_key = key.copyOf()
                editor?.putString(key_prekey_seal_key, encode_b64(key))
            }
            if (migrated && !prekey_seal_migrated) {
                prekey_seal_migrated = true
                editor?.putBoolean(key_prekey_seal_migrated, true)
            }
            editor?.commit()
        }
    }

    fun get_prekey_seal_key(): ByteArray? = synchronized(lock) { prekey_seal_key?.copyOf() }

    fun prekey_seal_migrated(): Boolean = prekey_seal_migrated

    fun get_data_kek(): ByteArray? {
        synchronized(lock) {
            return data_kek?.copyOf()
        }
    }

    fun set_level2_admitted(value: Boolean) {
        level2_admitted = value
    }

    fun is_level2_admitted(): Boolean = level2_admitted

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
            set_identity_key_locked(null)
            encrypted_vault = null
            vault_nonce = null
            vault_kdf_iterations = default_vault_kdf_iterations
            password_salt?.fill(0)
            password_salt = null
            user_id = null
            user_email = null
            recovery_codes = null
            previous_keys = null
            legacy_keks = null
            account_keks = null
            account_key_serial_floor = 0L
            account_kek_generation += 1
            drop_account_write_keks_locked()
            account_load_seq += 1
            account_load_pending.value = false
            account_keys_ready_ticket = null
            data_kek?.fill(0)
            data_kek = null
            level2_admitted = false
            storage_kdf_version = null
            legacy_storage_keys_retired = false
            retired_storage_key_hashes = emptyList()
            prekey_seal_key?.fill(0)
            prekey_seal_key = null
            prekey_seal_migrated = false
            pending_reseal_passphrase?.fill(0)
            pending_reseal_passphrase = null
            ratchet_identity_jwk = null
            ratchet_identity_public_b64 = null
            ratchet_signed_prekey_jwk = null
            ratchet_signed_prekey_public_b64 = null
            ratchet_previous_keys_json = null
            ratchet_pq_identity_secret = null
            ratchet_pq_identity_public = null
            external_pgp_blob = null
            prefs?.edit()?.clear()?.commit()
        }
        for (listener in clear_listeners) runCatching { listener() }
    }

    private fun encode_b64(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode_b64(s: String): ByteArray =
        Base64.decode(s, Base64.DEFAULT)

    private fun decode_b64_field(p: SharedPreferences, key: String): ByteArray? =
        runCatching { p.getString(key, null)?.let { decode_b64(it) } }.getOrNull()

    companion object {
        private const val prefs_name = "aster_session_keys_v1"
        private val plain_names = setOf("user_id", "user_email")
        private const val vault_salts_prefs_name = "aster_vault_salts_v1"
        private const val constant_use_prefs_name = "aster_constant_use_v1"
        private const val constant_use_prefix = "pending_"
        private const val key_vault_salts = "vault_salts"
        private const val retired_login_salts_prefs_name = "aster_retired_login_salts_v1"
        private const val key_retired_login_salts = "retired_login_salts"
        const val mail_legacy_retired_prefs_name = "aster_mail_legacy_retired_secure"
        const val folder_key_pins_prefs_name = "aster_folder_key_pins_secure"
        const val scheduled_untagged_prefs_name = "aster_scheduled_untagged_secure"
        private const val legacy_folder_kdf_pins_prefs_name = "folder_kdf_pins"
        const val max_folder_key_pin = 2
        private const val key_session_key = "session_key"
        private const val key_passphrase = "passphrase"
        private const val key_identity = "identity_key"
        private const val key_enc_vault = "encrypted_vault"
        private const val key_vault_nonce = "vault_nonce"
        private const val key_vault_kdf_iterations = "vault_kdf_iterations"
        const val default_vault_kdf_iterations = 310000
        private const val level2_vault_kdf_iterations = 600000

        fun sanitize_vault_kdf_iterations(value: Int): Int =
            if (value == level2_vault_kdf_iterations) level2_vault_kdf_iterations else default_vault_kdf_iterations
        private const val key_password_salt = "password_salt"
        private const val key_user_id = "user_id"
        private const val key_user_email = "user_email"
        private const val key_recovery_codes = "recovery_codes"
        private const val key_previous_keys = "previous_keys"
        private const val key_legacy_keks = "legacy_keks"
        private const val key_account_keks = "account_keks"
        private const val key_account_key_serial_floor = "account_key_serial_floor"
        private const val key_data_kek = "data_kek"
        private const val key_external_pgp_blob = "external_pgp_blob"
        private const val key_storage_kdf_version = "storage_kdf_version"
        private const val key_legacy_storage_keys_retired = "legacy_storage_keys_retired"
        private const val key_retired_storage_key_hashes = "retired_storage_key_hashes"
        private const val key_prekey_seal_key = "prekey_seal_key"
        private const val key_prekey_seal_migrated = "prekey_seal_migrated"
        private const val key_pending_reseal_pass = "pending_reseal_pass"
        private const val key_ratchet_identity_jwk = "ratchet_identity_jwk"
        private const val key_ratchet_identity_pub = "ratchet_identity_pub"
        private const val key_ratchet_spk_jwk = "ratchet_spk_jwk"
        private const val key_ratchet_spk_pub = "ratchet_spk_pub"
        private const val key_ratchet_previous_keys = "ratchet_previous_keys"
        private const val key_ratchet_pq_identity_priv = "ratchet_pq_identity_secret"
        private const val key_ratchet_pq_identity_pub = "ratchet_pq_identity_pub"
        private const val key_pq_secret_prefix = "pq_secret_"
        private const val key_ec_one_time_secret_prefix = "ec_one_time_secret_"
    }
}

private class KeyPinFile(private val context: Context?, private val name: String) {
    val prefs: SharedPreferences? by lazy {
        context?.let { runCatching { SecurePrefs.open(it, name) }.getOrNull() }
    }

    private val meta: SharedPreferences? by lazy {
        context?.let { runCatching { it.getSharedPreferences(key_pin_meta_prefs_name, Context.MODE_PRIVATE) }.getOrNull() }
    }

    @Volatile private var trusted: Boolean? = null

    @Volatile private var restored_in_memory = false

    fun readable(): Boolean {
        if (context == null || restored_in_memory) return true
        trusted?.let { return it }
        val result = runCatching { inspect() }.getOrDefault(false)
        if (result) trusted = true
        return result
    }

    fun restore() {
        val sealed = runCatching { seal() }.getOrDefault(false)
        if (sealed) trusted = true else restored_in_memory = true
    }

    private fun writable(): SharedPreferences? = prefs?.takeIf { it !is InMemoryPrefs }

    private fun inspect(): Boolean {
        val p = writable() ?: return false
        if (p.contains(key_pin_sentinel)) return true
        if (meta?.getBoolean(name, false) == true) return false
        return seal()
    }

    private fun seal(): Boolean {
        val p = writable() ?: return false
        if (!p.edit().putString(key_pin_sentinel, "1").commit()) return false
        meta?.edit()?.putBoolean(name, true)?.commit()
        return true
    }

    private companion object {
        const val key_pin_meta_prefs_name = "aster_key_pin_files_v1"
        const val key_pin_sentinel = "__key_pin_file"
    }
}
