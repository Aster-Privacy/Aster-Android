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

import java.security.MessageDigest
import java.util.Base64

object RetiredStorageKeys {

    const val key_length = 32

    private const val domain = "aster-retired-storage-key-v1"

    fun hash(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(domain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest.digest(key))
    }

    fun is_retired(encoded: String, hashes: Collection<String>): Boolean {
        if (hashes.isEmpty()) return false
        val key = runCatching { Base64.getMimeDecoder().decode(encoded.trim()) }.getOrNull() ?: return false
        return try {
            key.size == key_length && hash(key) in hashes
        } finally {
            key.fill(0)
        }
    }

    fun not_retired(encoded: List<String>, hashes: Collection<String>): List<String> {
        if (hashes.isEmpty()) return encoded
        val set = hashes.toSet()
        return encoded.filterNot { is_retired(it, set) }
    }
}
