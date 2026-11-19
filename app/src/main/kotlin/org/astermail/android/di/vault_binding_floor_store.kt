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

package org.astermail.android.di

import android.content.SharedPreferences
import java.security.MessageDigest
import org.astermail.android.crypto.VaultBindingFloorStore

class SecureVaultBindingFloorStore(
    private val prefs: () -> SharedPreferences?,
    private val backup: () -> SharedPreferences? = { null },
) : VaultBindingFloorStore {

    override fun is_bound(account: String): Boolean? {
        val secure = runCatching { prefs()?.getBoolean(entry_key(account), false) }.getOrNull()
        val mirrored = runCatching { backup()?.getBoolean(backup_key(account), false) }.getOrNull()
        if (secure == true && mirrored == false) write(backup, backup_key(account)) { key -> putBoolean(key, true) }
        if (secure == true || mirrored == true) return true
        return secure ?: mirrored
    }

    override fun save(account: String, bound: Boolean): Boolean {
        val secure = write(prefs, entry_key(account)) { key -> if (bound) putBoolean(key, true) else remove(key) }
        val mirrored = write(backup, backup_key(account)) { key -> if (bound) putBoolean(key, true) else remove(key) }
        return secure || mirrored
    }

    override fun admitted(account: String): Set<String>? {
        val secure = read_string(prefs, admitted_key(account))
        val mirrored = read_string(backup, admitted_backup_key(account))
        if (secure == null && mirrored == null) return null
        val mirrored_digests = split(mirrored?.getOrNull())
        val merged = (split(secure?.getOrNull()) + mirrored_digests).toCollection(LinkedHashSet())
        if (mirrored?.isSuccess == true && !mirrored_digests.containsAll(merged)) {
            write(backup, admitted_backup_key(account)) { key -> putString(key, merged.joinToString(",")) }
        }
        return merged
    }

    private fun read_string(source: () -> SharedPreferences?, key: String): Result<String?>? =
        runCatching { source() }.getOrNull()?.let { opened -> runCatching { opened.getString(key, null) }.takeIf { it.isSuccess } }

    override fun save_admitted(account: String, digests: Set<String>): Boolean {
        val joined = digests.joinToString(",")
        val secure = write(prefs, admitted_key(account)) { key ->
            if (digests.isEmpty()) remove(key) else putString(key, joined)
        }
        val mirrored = write(backup, admitted_backup_key(account)) { key ->
            if (digests.isEmpty()) remove(key) else putString(key, joined)
        }
        return secure || mirrored
    }

    private fun write(
        source: () -> SharedPreferences?,
        key: String,
        change: SharedPreferences.Editor.(String) -> SharedPreferences.Editor,
    ): Boolean = runCatching {
        val editor = source()?.edit() ?: return@runCatching false
        editor.change(key).commit()
    }.getOrDefault(false)

    private fun split(value: String?): List<String> =
        value?.split(",")?.filter { it.isNotBlank() }.orEmpty()

    companion object {
        const val PREFS_NAME = "aster_vault_binding_floor_v1"

        const val BACKUP_PREFS_NAME = "aster_vault_binding_floor_mirror_v1"

        fun entry_key(account: String): String = "bound_$account"

        fun admitted_key(account: String): String = "admitted_$account"

        fun backup_key(account: String): String = "bound_" + account_digest(account)

        fun admitted_backup_key(account: String): String = "admitted_" + account_digest(account)

        private fun account_digest(account: String): String =
            MessageDigest.getInstance("SHA-256").digest(account.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
