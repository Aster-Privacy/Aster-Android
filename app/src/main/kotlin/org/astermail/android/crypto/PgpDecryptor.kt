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
import java.io.InputStream
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder

object PgpDecryptor {

    init {
        java.security.Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        java.security.Security.addProvider(BouncyCastleProvider())
    }

    fun decrypt(
        armored_ciphertext: String,
        armored_private_key: String,
        passphrase: CharArray,
    ): String? {
        return try {
            val key_stream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8)),
            )
            val key_rings = PGPSecretKeyRingCollection(key_stream, JcaKeyFingerprintCalculator())

            val msg_stream = PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_ciphertext.toByteArray(Charsets.UTF_8)),
            )
            val pgp_factory = PGPObjectFactory(msg_stream, JcaKeyFingerprintCalculator())
            val enc_data_list = find_encrypted_data_list(pgp_factory) ?: return null

            @Suppress("UNCHECKED_CAST")
            val iterator = enc_data_list.encryptedDataObjects as Iterator<Any>
            while (iterator.hasNext()) {
                val pbe = iterator.next()
                if (pbe !is PGPPublicKeyEncryptedData) continue

                val secret_key = key_rings.getSecretKey(pbe.keyID) ?: continue

                val decryptor = JcePBESecretKeyDecryptorBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(passphrase)
                val private_key = secret_key.extractPrivateKey(decryptor)

                val decryptor_factory = JcePublicKeyDataDecryptorFactoryBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(private_key)

                val clear_stream = pbe.getDataStream(decryptor_factory)
                val plaintext = extract_literal_data(clear_stream)
                if (plaintext != null) return plaintext
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun find_encrypted_data_list(factory: PGPObjectFactory): PGPEncryptedDataList? {
        var obj = factory.nextObject()
        while (obj != null) {
            if (obj is PGPEncryptedDataList) return obj
            obj = factory.nextObject()
        }
        return null
    }

    private fun extract_literal_data(stream: InputStream): String? {
        val factory = PGPObjectFactory(stream, JcaKeyFingerprintCalculator())
        var obj = factory.nextObject()
        while (obj != null) {
            when (obj) {
                is PGPCompressedData -> {
                    val inner = PGPObjectFactory(obj.dataStream, JcaKeyFingerprintCalculator())
                    val result = extract_from_factory(inner)
                    if (result != null) return result
                }
                is PGPLiteralData -> {
                    return read_literal(obj)
                }
            }
            obj = factory.nextObject()
        }
        return null
    }

    private fun extract_from_factory(factory: PGPObjectFactory): String? {
        var obj = factory.nextObject()
        while (obj != null) {
            if (obj is PGPLiteralData) return read_literal(obj)
            if (obj is PGPCompressedData) {
                val inner = PGPObjectFactory(obj.dataStream, JcaKeyFingerprintCalculator())
                val result = extract_from_factory(inner)
                if (result != null) return result
            }
            obj = factory.nextObject()
        }
        return null
    }

    private fun read_literal(literal: PGPLiteralData): String {
        val out = ByteArrayOutputStream()
        literal.inputStream.use { it.copyTo(out) }
        return out.toString(Charsets.UTF_8.name())
    }
}
