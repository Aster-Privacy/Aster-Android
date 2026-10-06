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

object VaultSaltMemory {
    const val vault_salt_bytes = 16
    const val max_entries = 32
    private const val separator = "\n"

    fun vault_salt_hex(encrypted_vault_b64: String): String? {
        val vault = runCatching { java.util.Base64.getMimeDecoder().decode(encrypted_vault_b64) }.getOrNull()
            ?: return null
        if (vault.size <= vault_salt_bytes) return null
        return vault.copyOfRange(0, vault_salt_bytes).joinToString("") { "%02x".format(it) }
    }

    fun remember(entries: List<String>, encrypted_vault_b64: String): List<String> {
        val salt = vault_salt_hex(encrypted_vault_b64) ?: return entries
        if (entries.lastOrNull() == salt) return entries
        return (entries.filter { it != salt } + salt).takeLast(max_entries)
    }

    fun decode(entries: List<String>): List<ByteArray> = entries.mapNotNull { entry ->
        if (entry.length != vault_salt_bytes * 2) return@mapNotNull null
        runCatching {
            ByteArray(vault_salt_bytes) { index -> entry.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    fun parse(stored: String?): List<String> =
        stored?.split(separator)?.filter { it.isNotEmpty() }?.takeLast(max_entries).orEmpty()

    fun serialize(entries: List<String>): String = entries.joinToString(separator)
}
