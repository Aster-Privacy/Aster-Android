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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Date
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.CompressionAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator

object PgpEncryptor {

    init {
        java.security.Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        java.security.Security.addProvider(BouncyCastleProvider())
    }

    fun encrypt_to_keys(plaintext: String, armored_public_keys: List<String>): String? {
        val recipients = armored_public_keys.mapNotNull { select_encryption_key(it) }
        if (recipients.isEmpty()) return null

        return try {
            val literal = ByteArrayOutputStream()
            val compressor = PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP)
            val compressed_stream = compressor.open(literal)
            val literal_generator = PGPLiteralDataGenerator()
            val plaintext_bytes = plaintext.toByteArray(Charsets.UTF_8)
            literal_generator.open(
                compressed_stream,
                PGPLiteralData.UTF8,
                PGPLiteralData.CONSOLE,
                plaintext_bytes.size.toLong(),
                Date(),
            ).use { it.write(plaintext_bytes) }
            literal_generator.close()
            compressor.close()

            val encryptor_builder = JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(SecureRandom())
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            val encrypted_generator = PGPEncryptedDataGenerator(encryptor_builder)
            for (key in recipients) {
                encrypted_generator.addMethod(
                    JcePublicKeyKeyEncryptionMethodGenerator(key)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME),
                )
            }

            val payload = literal.toByteArray()
            val armored_output = ByteArrayOutputStream()
            ArmoredOutputStream(armored_output).use { armored ->
                encrypted_generator.open(armored, payload.size.toLong()).use { it.write(payload) }
            }
            armored_output.toString(Charsets.UTF_8.name())
        } catch (_: Throwable) {
            null
        }
    }

    private fun select_encryption_key(armored_public_key: String): PGPPublicKey? {
        return try {
            val stream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8)),
            )
            val rings = PGPPublicKeyRingCollection(stream, JcaKeyFingerprintCalculator())
            var fallback: PGPPublicKey? = null
            for (ring in rings) {
                for (key in (ring as PGPPublicKeyRing)) {
                    if (!key.isEncryptionKey) continue
                    if (!key.isMasterKey) return key
                    if (fallback == null) fallback = key
                }
            }
            fallback
        } catch (_: Throwable) {
            null
        }
    }
}
