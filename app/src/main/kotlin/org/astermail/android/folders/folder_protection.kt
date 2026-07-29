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

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val pbkdf2_iterations_legacy = 100000
private const val pbkdf2_iterations = 310000
private const val salt_bytes_legacy = 16
private const val auth_key_context = "astermail-folder-auth-v1"

private fun hkdf_sha256(ikm: ByteArray, info: String, length: Int): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(ByteArray(32), "HmacSHA256"))
    val prk = mac.doFinal(ikm)

    mac.init(SecretKeySpec(prk, "HmacSHA256"))
    val info_bytes = info.toByteArray(Charsets.UTF_8)
    val out = ByteArray(length)
    var offset = 0
    var counter = 1
    var previous = ByteArray(0)
    while (offset < length) {
        mac.update(previous)
        mac.update(info_bytes)
        mac.update(counter.toByte())
        previous = mac.doFinal()
        val take = minOf(previous.size, length - offset)
        previous.copyInto(out, offset, 0, take)
        offset += take
        counter++
    }
    prk.fill(0)
    previous.fill(0)
    return out
}

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
