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

package org.astermail.android.crypto

import java.security.SecureRandom

object VaultKdf {
    const val DEFAULT_ITERATIONS = 310000
    const val LEVEL2_ITERATIONS = 600000

    fun is_allowed(iterations: Int): Boolean =
        iterations == DEFAULT_ITERATIONS || iterations == LEVEL2_ITERATIONS

    fun require_allowed(iterations: Int): Int {
        require(is_allowed(iterations)) { "unsupported vault work factor" }
        return iterations
    }

    fun from_server(value: Int?): Int = when (value) {
        null, DEFAULT_ITERATIONS -> DEFAULT_ITERATIONS
        LEVEL2_ITERATIONS -> LEVEL2_ITERATIONS
        else -> throw IllegalArgumentException("unsupported vault work factor")
    }

    fun to_server(iterations: Int): Int? =
        if (require_allowed(iterations) == LEVEL2_ITERATIONS) LEVEL2_ITERATIONS else null
}

object EnvelopeV2 {
    const val SALT_LENGTH = 16
    const val IV_LENGTH = 12
    private const val KEY_LENGTH = 32
    private val INFO = "aster-envelope-v2".toByteArray(Charsets.UTF_8)
    private val MARKER = byteArrayOf(2)
    private val random = SecureRandom()

    fun marker(): ByteArray = MARKER.copyOf()

    fun is_marker(nonce: ByteArray?): Boolean = nonce != null && nonce.contentEquals(MARKER)

    fun derive_key(data_kek: ByteArray, salt: ByteArray): ByteArray =
        hkdf_sha256(data_kek, salt, INFO, KEY_LENGTH)

    fun seal(plaintext: ByteArray, data_kek: ByteArray): ByteArray {
        require(data_kek.size == KEY_LENGTH) { "data key must be 32 bytes" }
        val salt = ByteArray(SALT_LENGTH).also { random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { random.nextBytes(it) }
        val key = derive_key(data_kek, salt)
        return try {
            salt + iv + AesGcm.encrypt(key, iv, plaintext)
        } finally {
            key.fill(0)
        }
    }

    fun open_with(blob: ByteArray, data_kek: ByteArray): ByteArray? {
        if (blob.size <= SALT_LENGTH + IV_LENGTH) return null
        if (data_kek.size != KEY_LENGTH) return null
        val salt = blob.copyOfRange(0, SALT_LENGTH)
        val iv = blob.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = blob.copyOfRange(SALT_LENGTH + IV_LENGTH, blob.size)
        val key = derive_key(data_kek, salt)
        return try {
            AesGcm.decrypt(key, iv, ciphertext)
        } catch (_: Exception) {
            null
        } finally {
            key.fill(0)
        }
    }

    fun open(blob: ByteArray, keys: List<ByteArray>): ByteArray? {
        for (key in keys) {
            open_with(blob, key)?.let { return it }
        }
        return null
    }
}
