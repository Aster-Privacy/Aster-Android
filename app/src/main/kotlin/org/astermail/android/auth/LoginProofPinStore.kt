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

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

fun login_v3_seen(pins_readable: Boolean, pinned: Boolean, offered: Boolean): Boolean =
    pinned || (!pins_readable && offered)

@Singleton
class LoginProofPinStore internal constructor(
    private val prefs: SharedPreferences?,
    private val secret_source: () -> ByteArray?,
) : LoginPinSource, LoginSaltRecordSource {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        runCatching { context.getSharedPreferences(prefs_name, Context.MODE_PRIVATE) }.getOrNull(),
        { LoginKeyBlind.device_secret(context) },
    )

    private val lock = Any()

    private val blind: LoginKeyBlind? by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { secret_source() }.getOrNull()?.let(::LoginKeyBlind)
    }

    @Volatile
    private var migrated = false

    @Volatile
    private var entries: List<String> = read_entries()

    @Volatile
    private var v3_readable: Boolean = true

    @Volatile
    private var v3_entries: List<String> = read_v3_entries()

    @Volatile
    private var v3_owners: List<String> = read_v3_owners()

    @Volatile
    private var salt_records: List<LoginSaltRecord> = read_salt_records()

    private fun ready(): LoginKeyBlind? {
        val active = blind ?: return null
        if (migrated) return active
        synchronized(lock) {
            if (migrated) return active
            val next_entries = active.pins(entries)
            val next_v3 = active.pins(v3_entries)
            val next_owners = active.owners(v3_owners)
            val next_records = active.salt_records(salt_records)
            val changed = next_entries != entries || next_v3 != v3_entries ||
                next_owners != v3_owners || next_records != salt_records
            val saved = !changed || runCatching {
                prefs?.edit()
                    ?.let(::stamped)
                    ?.putString(key_entries, LoginProof.serialize_pins(next_entries))
                    ?.putString(key_v3_entries, LoginProof.serialize_pins(next_v3))
                    ?.putString(key_v3_owners, LoginProof.serialize_pins(next_owners))
                    ?.putString(key_salt_records, LoginSaltRule.serialize_records(next_records))
                    ?.commit() ?: true
            }.getOrDefault(false)
            entries = next_entries
            v3_entries = next_v3
            v3_owners = next_owners
            salt_records = next_records
            migrated = saved
        }
        return active
    }

    private fun pin_keys(keys: List<String>): List<String> = ready()?.pins(keys) ?: keys

    private fun record_keys(keys: List<String>): List<String> = ready()?.records(keys) ?: keys

    private fun blinded_pin_keys(keys: List<String>): List<String>? = ready()?.pins(keys)

    private fun blinded_record_keys(keys: List<String>): List<String>? = ready()?.records(keys)

    private fun blind_matches_entries(): Boolean {
        val active = ready() ?: return false
        val stored = runCatching { prefs?.getString(key_blind_check, null) }.getOrElse { return false }
        if (stored != null) return stored == active.check()
        return v3_entries.none(LoginKeyBlind::is_blinded_pin)
    }

    private fun stamped(editor: SharedPreferences.Editor): SharedPreferences.Editor {
        val active = blind ?: return editor
        val stored = runCatching { prefs?.getString(key_blind_check, null) }.getOrNull()
        return if (stored == null) editor.putString(key_blind_check, active.check()) else editor
    }

    private fun write_entries(updated: List<String>) {
        entries = updated
        runCatching { prefs?.edit()?.let(::stamped)?.putString(key_entries, LoginProof.serialize_pins(updated))?.commit() }
    }

    private fun write_v3_entries(updated: List<String>) {
        v3_entries = updated
        runCatching { prefs?.edit()?.let(::stamped)?.putString(key_v3_entries, LoginProof.serialize_pins(updated))?.commit() }
    }

    private fun write_v3_owners(updated: List<String>) {
        v3_owners = updated
        runCatching { prefs?.edit()?.let(::stamped)?.putString(key_v3_owners, LoginProof.serialize_pins(updated))?.commit() }
    }

    private fun write_salt_records(updated: List<LoginSaltRecord>) {
        salt_records = updated
        runCatching { prefs?.edit()?.let(::stamped)?.putString(key_salt_records, LoginSaltRule.serialize_records(updated))?.commit() }
    }

    override fun is_pinned(keys: List<String>): Boolean {
        val blinded = pin_keys(keys)
        return LoginProof.is_pinned(entries, blinded)
    }

    override fun pin(keys: List<String>) {
        val blinded = blinded_pin_keys(keys) ?: return
        synchronized(lock) {
            val updated = LoginProof.add_pins(entries, blinded)
            if (updated != entries) write_entries(updated)
        }
    }

    override fun unpin(keys: List<String>) {
        val blinded = pin_keys(keys)
        synchronized(lock) {
            val updated = LoginProof.remove_pins(entries, blinded)
            if (updated != entries) write_entries(updated)
        }
    }

    fun is_v3_pinned(keys: List<String>): Boolean {
        val blinded = pin_keys(keys)
        return LoginProof.is_pinned(v3_entries, blinded)
    }

    fun is_v3_seen(keys: List<String>, offered: Boolean): Boolean =
        login_v3_seen(v3_readable && blind_matches_entries(), is_v3_pinned(keys), offered)

    fun pin_v3(keys: List<String>) {
        val blinded = blinded_pin_keys(keys) ?: return
        synchronized(lock) {
            val updated = LoginProof.add_pins(v3_entries, blinded)
            if (updated != v3_entries) write_v3_entries(updated)
        }
    }

    fun unpin_v3(keys: List<String>) {
        val blinded = pin_keys(keys)
        synchronized(lock) {
            val updated = LoginProof.remove_pins(v3_entries, blinded)
            if (updated != v3_entries) write_v3_entries(updated)
        }
    }

    fun remember_v3_owner(user_id: String, keys: List<String>) {
        val blinded = blinded_pin_keys(keys) ?: return
        synchronized(lock) {
            val owned = blinded.filter { it in v3_entries }
            val updated = LoginProof.add_owner(v3_owners, user_id, owned)
            if (updated != v3_owners) write_v3_owners(updated)
        }
    }

    fun unpin_v3_for_account(user_id: String?, address_keys: List<String>) {
        val blinded = pin_keys(address_keys)
        synchronized(lock) {
            val removed = LoginProof.remove_pins(v3_entries, LoginProof.owned_keys(v3_owners, user_id) + blinded)
            if (removed != v3_entries) write_v3_entries(removed)
            val updated = LoginProof.remove_owner(v3_owners, user_id)
            if (updated != v3_owners) write_v3_owners(updated)
        }
    }

    override fun find(keys: List<String>): ByteArray? {
        val blinded = record_keys(keys)
        return LoginSaltRule.find_record(salt_records, blinded)
    }

    override fun put(keys: List<String>, salt: ByteArray) {
        val blinded = blinded_record_keys(keys) ?: return
        synchronized(lock) {
            val updated = LoginSaltRule.put_records(salt_records, blinded, salt)
            if (updated != salt_records) write_salt_records(updated)
        }
    }

    override fun remove(keys: List<String>) {
        val blinded = record_keys(keys)
        synchronized(lock) {
            val updated = LoginSaltRule.remove_records(salt_records, blinded)
            if (updated.size != salt_records.size) write_salt_records(updated)
        }
    }

    private fun read_salt_records(): List<LoginSaltRecord> =
        runCatching { LoginSaltRule.parse_records(prefs?.getString(key_salt_records, null)) }.getOrDefault(emptyList())

    private fun read_v3_owners(): List<String> =
        runCatching { LoginProof.parse_owners(prefs?.getString(key_v3_owners, null)) }.getOrDefault(emptyList())

    private fun read_entries(): List<String> =
        runCatching { LoginProof.parse_pins(prefs?.getString(key_entries, null)) }.getOrDefault(emptyList())

    private fun read_v3_entries(): List<String> {
        val store = prefs
        if (store == null) {
            v3_readable = false
            return emptyList()
        }
        return runCatching { LoginProof.parse_pins(store.getString(key_v3_entries, null)) }
            .getOrElse {
                v3_readable = false
                emptyList()
            }
    }

    companion object {
        private const val prefs_name = "aster_login_proof_pins_v1"
        private const val key_entries = "pinned_accounts"
        private const val key_salt_records = "login_salt_records"
        private const val key_v3_entries = "login_v3_accounts"
        private const val key_v3_owners = "login_v3_owners"
        private const val key_blind_check = "login_blind_check"
    }
}
