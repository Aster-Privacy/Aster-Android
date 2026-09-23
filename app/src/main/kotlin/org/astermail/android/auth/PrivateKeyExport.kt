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

package org.astermail.android.auth

import java.math.BigInteger
import java.util.Base64
import java.util.Locale
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.PasswordKdf
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider

internal object PrivateKeyExport {

    private const val armored_private_key_header = "-----BEGIN PGP PRIVATE KEY"
    private const val blob_salt_length = 16

    fun select(
        candidates: List<String>,
        fingerprint: String,
        password: CharArray,
        encrypted_blob_b64: String?,
        nonce_b64: String?,
        pbkdf2_iterations: Int,
    ): String? {
        if (fingerprint.isBlank()) return null

        candidates
            .filter { is_armored_private_key(it) }
            .firstOrNull { matches(it, fingerprint) && unlocks_with(it, password) }
            ?.let { return it }

        if (encrypted_blob_b64.isNullOrBlank() || nonce_b64.isNullOrBlank()) return null

        val opened = try {
            open_blob(encrypted_blob_b64, nonce_b64, password, pbkdf2_iterations)
        } catch (_: Throwable) {
            return null
        }
        return opened.takeIf { is_armored_private_key(it) && matches(it, fingerprint) }
    }

    fun fingerprint(armored_private_key: String): String? = try {
        val secret_ring = read_ring(armored_private_key)
        String.format(Locale.US, "%040X", BigInteger(1, secret_ring.publicKey.fingerprint))
    } catch (_: Throwable) {
        null
    }

    private fun matches(armored_private_key: String, fingerprint: String): Boolean =
        fingerprint(armored_private_key)?.equals(fingerprint.trim(), ignoreCase = true) == true

    private fun is_armored_private_key(value: String): Boolean =
        value.trimStart().startsWith(armored_private_key_header)

    private fun unlocks_with(armored_private_key: String, password: CharArray): Boolean = try {
        read_ring(armored_private_key).secretKey.extractPrivateKey(
            BcPBESecretKeyDecryptorBuilder(BcPGPDigestCalculatorProvider()).build(password),
        ) != null
    } catch (_: Throwable) {
        false
    }

    private fun open_blob(
        encrypted_blob_b64: String,
        nonce_b64: String,
        password: CharArray,
        pbkdf2_iterations: Int,
    ): String {
        val combined = Base64.getMimeDecoder().decode(encrypted_blob_b64)
        val nonce = Base64.getMimeDecoder().decode(nonce_b64)
        val salt = combined.copyOfRange(0, blob_salt_length)
        val ciphertext = combined.copyOfRange(blob_salt_length, combined.size)

        val derived = PasswordKdf.derive_aes_key(password, salt, pbkdf2_iterations)
        try {
            return AesGcm.decrypt(derived, nonce, ciphertext).toString(Charsets.UTF_8)
        } finally {
            derived.fill(0)
        }
    }

    private fun read_ring(armored_private_key: String): PGPSecretKeyRing =
        PGPSecretKeyRing(
            PGPUtil.getDecoderStream(armored_private_key.byteInputStream()),
            BcKeyFingerprintCalculator(),
        )
}
