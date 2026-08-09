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

package org.astermail.android.folders

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256 as hkdf_expand
import javax.crypto.spec.SecretKeySpec

private const val pbkdf2_iterations_legacy = 100000
private const val pbkdf2_iterations = 310000
private const val salt_bytes_legacy = 16
private const val salt_bytes = 32
private const val auth_key_context = "astermail-folder-auth-v1"
private const val encrypt_key_context = "astermail-folder-encrypt-v1"

private fun hkdf_sha256(ikm: ByteArray, info: String, length: Int): ByteArray =
    hkdf_expand(ikm, ByteArray(32), info.toByteArray(Charsets.UTF_8), length)

private fun pbkdf2_hmac_sha256(password: ByteArray, salt: ByteArray, iterations: Int, length: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(password, "HmacSHA256"))
    val block_size = mac.macLength
    val out = ByteArray(length)
    var offset = 0
    var block = 1
    while (offset < length) {
        mac.update(salt)
        mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
        var u = mac.doFinal()
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = mac.doFinal(u)
            for (j in 0 until block_size) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        val take = minOf(block_size, length - offset)
        t.copyInto(out, offset, 0, take)
        offset += take
        block++
    }
    return out
}

data class folder_password_material(
    val password_hash: String,
    val password_salt: String,
    val encrypted_folder_key: String,
    val folder_key_nonce: String,
)

fun prepare_folder_password(password: String): folder_password_material {
    val random = SecureRandom()
    val salt = ByteArray(salt_bytes).also { random.nextBytes(it) }
    val password_bytes = password.toByteArray(Charsets.UTF_8)
    val derived = pbkdf2_hmac_sha256(password_bytes, salt, pbkdf2_iterations, 32)
    password_bytes.fill(0)

    val auth_key = hkdf_sha256(derived, auth_key_context, 32)
    val encryption_key = hkdf_sha256(derived, encrypt_key_context, 32)
    derived.fill(0)

    val folder_key = ByteArray(32).also { random.nextBytes(it) }
    val nonce = ByteArray(12).also { random.nextBytes(it) }
    val encrypted = AesGcm.encrypt(encryption_key, nonce, folder_key)
    folder_key.fill(0)
    encryption_key.fill(0)

    val encoder = Base64.getEncoder()
    val material = folder_password_material(
        password_hash = encoder.encodeToString(auth_key),
        password_salt = encoder.encodeToString(salt),
        encrypted_folder_key = encoder.encodeToString(encrypted),
        folder_key_nonce = encoder.encodeToString(nonce),
    )
    auth_key.fill(0)
    return material
}

fun derive_folder_auth_hash(password: String, salt_base64: String): String {
    val salt = Base64.getMimeDecoder().decode(salt_base64)
    val iterations = if (salt.size <= salt_bytes_legacy) pbkdf2_iterations_legacy else pbkdf2_iterations
    val password_bytes = password.toByteArray(Charsets.UTF_8)
    val derived = pbkdf2_hmac_sha256(password_bytes, salt, iterations, 32)
    password_bytes.fill(0)

    val auth_key = hkdf_sha256(derived, auth_key_context, 32)
    derived.fill(0)

    val encoded = Base64.getEncoder().encodeToString(auth_key)
    auth_key.fill(0)
    return encoded
}
