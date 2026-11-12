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

import java.nio.ByteBuffer
import java.security.MessageDigest

object DeviceKeyFingerprint {

    private const val DOMAIN = "aster-bridge-device-fingerprint-v1"
    private const val FINGERPRINT_BYTES = 10
    private const val GROUP_CHARS = 4
    private const val FINGERPRINT_HEX_CHARS = FINGERPRINT_BYTES * 2
    private const val MAX_INPUT_CHARS = 64
    private val HEX_CHAR = Regex("[0-9a-fA-F]")
    private val ALLOWED_INPUT = Regex("[0-9a-fA-F\\s-]*")

    fun of(ed25519_pk: ByteArray, mlkem_pk: ByteArray, x25519_pk: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(DOMAIN.toByteArray(Charsets.US_ASCII))
        digest.update(0.toByte())
        for (key in listOf(ed25519_pk, mlkem_pk, x25519_pk)) {
            digest.update(ByteBuffer.allocate(4).putInt(key.size).array())
            digest.update(key)
        }
        val hex = digest.digest().copyOf(FINGERPRINT_BYTES)
            .joinToString("") { "%02X".format(it.toInt() and 0xff) }
        return hex.chunked(GROUP_CHARS).joinToString(" ")
    }

    fun of_encoded(ed25519_pk: String, mlkem_pk: String, x25519_pk: String): String? {
        val ed = runCatching { DeviceEnvelope.base64url_decode(ed25519_pk) }.getOrNull() ?: return null
        val mlkem = runCatching { DeviceEnvelope.base64url_decode(mlkem_pk) }.getOrNull() ?: return null
        val x = runCatching { DeviceEnvelope.base64url_decode(x25519_pk) }.getOrNull() ?: return null
        if (ed.size != DeviceEnvelope.ED25519_PK_BYTES ||
            mlkem.size != DeviceEnvelope.MLKEM_768_PK_BYTES ||
            x.size != DeviceEnvelope.X25519_PK_BYTES
        ) {
            return null
        }
        return of(ed, mlkem, x)
    }

    fun format_input(raw: String): String =
        raw.filter { HEX_CHAR.matches(it.toString()) }
            .uppercase()
            .take(FINGERPRINT_HEX_CHARS)
            .chunked(GROUP_CHARS)
            .joinToString(" ")

    fun normalize(raw: String): String? {
        if (raw.length > MAX_INPUT_CHARS || !ALLOWED_INPUT.matches(raw)) return null
        val hex = raw.filterNot { it.isWhitespace() || it == '-' }.uppercase()
        if (hex.length != FINGERPRINT_HEX_CHARS) return null
        return hex.chunked(GROUP_CHARS).joinToString(" ")
    }
}
