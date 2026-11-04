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

object RetiredLoginSalts {
    const val max_per_user = 8
    const val max_entries = 64
    const val max_salt_bytes = 64
    private const val separator = "\n"
    private const val field_separator = "\t"

    private fun hex(salt: ByteArray): String = salt.joinToString("") { "%02x".format(it) }

    private fun unhex(value: String): ByteArray? {
        if (value.isEmpty() || value.length % 2 != 0 || value.length > max_salt_bytes * 2) return null
        return runCatching {
            ByteArray(value.length / 2) { index -> value.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    private fun owner(entry: String): String? = entry.substringBefore(field_separator, "").ifEmpty { null }

    fun remember(entries: List<String>, user_id: String, salt: ByteArray): List<String> {
        if (user_id.isEmpty() || user_id.contains(field_separator) || user_id.contains(separator)) return entries
        if (salt.isEmpty() || salt.size > max_salt_bytes) return entries
        val entry = user_id + field_separator + hex(salt)
        if (entries.lastOrNull() == entry) return entries
        val without = entries.filter { it != entry } + entry
        val own = without.filter { owner(it) == user_id }
        val trimmed = if (own.size > max_per_user) {
            val drop = own.take(own.size - max_per_user).toSet()
            without.filter { it !in drop }
        } else {
            without
        }
        return trimmed.takeLast(max_entries)
    }

    fun remember_all(entries: List<String>, user_id: String, salts: List<ByteArray>): List<String> =
        salts.fold(entries) { acc, salt -> remember(acc, user_id, salt) }

    fun salts_for(entries: List<String>, user_id: String): List<ByteArray> =
        entries.asReversed()
            .filter { owner(it) == user_id }
            .mapNotNull { unhex(it.substringAfter(field_separator, "")) }

    fun decode_base64(encoded: List<String>): List<ByteArray> =
        encoded.takeLast(max_per_user).mapNotNull { entry ->
            runCatching { java.util.Base64.getDecoder().decode(entry) }.getOrNull()
                ?.takeIf { it.isNotEmpty() && it.size <= max_salt_bytes }
        }

    fun parse(stored: String?): List<String> =
        stored?.split(separator)?.filter { it.contains(field_separator) }?.takeLast(max_entries).orEmpty()

    fun serialize(entries: List<String>): String = entries.joinToString(separator)
}
