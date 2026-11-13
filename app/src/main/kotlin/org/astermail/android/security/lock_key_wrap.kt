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

package org.astermail.android.security

import java.security.SecureRandom
import java.util.Base64
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.astermail.android.storage.LOCK_KEY_BYTES

private const val wrap_prefix = "lw1:"
private const val wrap_salt_bytes = 16
private const val wrap_nonce_bytes = 12
private const val wrap_key_bits = 256
private val wrap_aad = "aster-lock-key-wrap-v1".toByteArray(Charsets.UTF_8)

object LockKeyWrap {

    fun wrap(pin: String, lock_key: ByteArray, iterations: Int, random: SecureRandom = SecureRandom()): String {
        require(lock_key.size == LOCK_KEY_BYTES)
        val salt = ByteArray(wrap_salt_bytes).also { random.nextBytes(it) }
        val nonce = ByteArray(wrap_nonce_bytes).also { random.nextBytes(it) }
        val wrapping_key = PasswordKdf.derive_aes_key(pin, salt, iterations, wrap_key_bits)
        try {
            val sealed = AesGcm.encrypt(wrapping_key, nonce, lock_key, wrap_aad)
            return wrap_prefix + Base64.getEncoder().encodeToString(salt + nonce + sealed)
        } finally {
            wrapping_key.fill(0)
        }
    }

    fun unwrap(pin: String, wrapped: String?, iterations: Int): ByteArray? {
        if (wrapped == null || !wrapped.startsWith(wrap_prefix)) return null
        return try {
            val raw = Base64.getDecoder().decode(wrapped.substring(wrap_prefix.length))
            val header = wrap_salt_bytes + wrap_nonce_bytes
            if (raw.size <= header) return null
            val wrapping_key = PasswordKdf.derive_aes_key(pin, raw.copyOfRange(0, wrap_salt_bytes), iterations, wrap_key_bits)
            try {
                AesGcm.decrypt(
                    wrapping_key,
                    raw.copyOfRange(wrap_salt_bytes, header),
                    raw.copyOfRange(header, raw.size),
                    wrap_aad,
                ).takeIf { it.size == LOCK_KEY_BYTES }
            } finally {
                wrapping_key.fill(0)
            }
        } catch (_: Exception) {
            null
        }
    }
}
