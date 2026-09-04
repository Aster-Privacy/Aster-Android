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

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object DeviceLinkBinding {

    const val TAG_BYTES = 32

    private const val BINDING_SALT = "astermail-device-link-bind-v1"
    private const val BINDING_INFO = "astermail-device-link-bundle-v1"
    private const val MAC_ALGORITHM = "HmacSHA256"

    class BindingMismatchException(message: String) : IllegalArgumentException(message)

    fun normalize_code(raw: String): String =
        raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }.uppercase()

    fun compute_tag(
        code: String,
        ed25519_pk: String,
        mlkem_pk: String,
        x25519_pk: String,
    ): String {
        val normalized = normalize_code(code)
        require(normalized.isNotEmpty()) { "device code must not be empty" }
        val key = derive_binding_key(normalized)
        try {
            val mac = Mac.getInstance(MAC_ALGORITHM)
            mac.init(SecretKeySpec(key, MAC_ALGORITHM))
            mac.update(framed(normalized, ed25519_pk, mlkem_pk, x25519_pk))
            return DeviceEnvelope.base64url_encode(mac.doFinal())
        } finally {
            zeroize(key)
        }
    }

    fun matches(
        code: String,
        ed25519_pk: String,
        mlkem_pk: String,
        x25519_pk: String,
        offered_tag: String,
    ): Boolean {
        if (offered_tag.isBlank()) return false
        val offered = runCatching { DeviceEnvelope.base64url_decode(offered_tag) }.getOrNull() ?: return false
        if (offered.size != TAG_BYTES) return false
        val expected = runCatching {
            DeviceEnvelope.base64url_decode(compute_tag(code, ed25519_pk, mlkem_pk, x25519_pk))
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(expected, offered)
    }

    fun require_match(
        code: String,
        ed25519_pk: String,
        mlkem_pk: String,
        x25519_pk: String,
        offered_tag: String,
    ) {
        if (!matches(code, ed25519_pk, mlkem_pk, x25519_pk, offered_tag)) {
            throw BindingMismatchException("device bundle is not bound to this device code")
        }
    }

    private fun derive_binding_key(normalized_code: String): ByteArray = hkdf_sha256(
        normalized_code.toByteArray(Charsets.UTF_8),
        BINDING_SALT.toByteArray(Charsets.UTF_8),
        BINDING_INFO.toByteArray(Charsets.UTF_8),
        32,
    )

    private fun framed(vararg parts: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            out.write((bytes.size ushr 24) and 0xff)
            out.write((bytes.size ushr 16) and 0xff)
            out.write((bytes.size ushr 8) and 0xff)
            out.write(bytes.size and 0xff)
            out.write(bytes)
        }
        return out.toByteArray()
    }
}
