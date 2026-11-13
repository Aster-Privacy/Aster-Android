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

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

const val LOCK_KEY_BYTES = 32

private const val seal_prefix = "ls1:"
private const val seal_nonce_bytes = 12
private const val seal_tag_bits = 128
private const val seal_transformation = "AES/GCM/NoPadding"
private const val seal_domain = "aster-lock-seal-v1"
private const val check_domain = "aster-lock-key-check-v1"

object LockSeal {

    fun new_lock_key(random: SecureRandom = SecureRandom()): ByteArray =
        ByteArray(LOCK_KEY_BYTES).also { random.nextBytes(it) }

    fun is_sealed(value: String?): Boolean = value != null && value.startsWith(seal_prefix)

    fun seal(
        lock_key: ByteArray,
        store: String,
        name: String,
        plaintext: String,
        random: SecureRandom = SecureRandom(),
    ): String {
        require(lock_key.size == LOCK_KEY_BYTES)
        val nonce = ByteArray(seal_nonce_bytes).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance(seal_transformation)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(lock_key, "AES"), GCMParameterSpec(seal_tag_bits, nonce))
        cipher.updateAAD(associated_data(store, name))
        val sealed = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return seal_prefix + Base64.getEncoder().encodeToString(nonce + sealed)
    }

    fun open(lock_key: ByteArray, store: String, name: String, value: String): String? {
        if (!is_sealed(value) || lock_key.size != LOCK_KEY_BYTES) return null
        return try {
            val raw = Base64.getDecoder().decode(value.substring(seal_prefix.length))
            if (raw.size < seal_nonce_bytes + seal_tag_bits / 8) return null
            val cipher = Cipher.getInstance(seal_transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(lock_key, "AES"),
                GCMParameterSpec(seal_tag_bits, raw, 0, seal_nonce_bytes),
            )
            cipher.updateAAD(associated_data(store, name))
            String(cipher.doFinal(raw, seal_nonce_bytes, raw.size - seal_nonce_bytes), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun key_check(lock_key: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(lock_key, "HmacSHA256"))
        return Base64.getEncoder().encodeToString(mac.doFinal(check_domain.toByteArray(Charsets.UTF_8)))
    }

    fun matches_key_check(lock_key: ByteArray, expected: String?): Boolean {
        if (expected.isNullOrEmpty() || lock_key.size != LOCK_KEY_BYTES) return false
        val actual = key_check(lock_key).toByteArray(Charsets.UTF_8)
        return java.security.MessageDigest.isEqual(actual, expected.toByteArray(Charsets.UTF_8))
    }

    private fun associated_data(store: String, name: String): ByteArray =
        "$seal_domain\u0000$store\u0000$name".toByteArray(Charsets.UTF_8)
}
