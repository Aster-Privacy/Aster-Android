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
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedDataGenerator
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpInboundIndicationTest {

    private val passphrase = "correct horse battery staple".toCharArray()

    private val recipient by lazy {
        PgpKeyGenerator.generate("Recipient", "recipient@astermail.org", passphrase)
    }

    private val sender by lazy {
        PgpKeyGenerator.generate("Sender", "sender@example.com", passphrase)
    }

    @Test
    fun unsigned_encrypted_message_decrypts_and_reports_no_signature() {
        val armored = PgpEncryptor.encrypt_to_keys(
            "hello from outside",
            listOf(recipient.armored_public_key),
        )
        assertNotNull(armored)
        assertTrue(armored!!.startsWith("-----BEGIN PGP MESSAGE-----"))

        val result = PgpDecryptor.decrypt_with_status(
            armored,
            recipient.armored_private_key,
            passphrase,
            null,
        )

        assertEquals("hello from outside", result.plaintext)
        assertEquals(PgpSignatureStatus.NONE, result.signature)
    }

    @Test
    fun signed_encrypted_message_without_sender_key_reports_unverified() {
        val armored = sign_and_encrypt("signed hello")

        val result = PgpDecryptor.decrypt_with_status(
            armored,
            recipient.armored_private_key,
            passphrase,
            null,
        )

        assertEquals("signed hello", result.plaintext)
        assertEquals(PgpSignatureStatus.UNVERIFIED, result.signature)
    }

    @Test
    fun signed_encrypted_message_with_sender_key_reports_valid() {
        val armored = sign_and_encrypt("signed hello")

        val result = PgpDecryptor.decrypt_with_status(
            armored,
            recipient.armored_private_key,
            passphrase,
            sender.armored_public_key,
        )

        assertEquals("signed hello", result.plaintext)
        assertEquals(PgpSignatureStatus.VALID, result.signature)
    }

    @Test
    fun signed_encrypted_message_with_wrong_key_is_not_reported_valid() {
        val armored = sign_and_encrypt("signed hello")

        val result = PgpDecryptor.decrypt_with_status(
            armored,
            recipient.armored_private_key,
            passphrase,
            recipient.armored_public_key,
        )

        assertEquals("signed hello", result.plaintext)
        assertEquals(PgpSignatureStatus.UNVERIFIED, result.signature)
    }

    private fun sign_and_encrypt(plaintext: String): String {
        java.security.Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        java.security.Security.addProvider(BouncyCastleProvider())

        val encryption_key = select_encryption_key(recipient.armored_public_key)
        val signing_key = select_signing_key(sender.armored_private_key)
        val private_key = signing_key.extractPrivateKey(
            JcePBESecretKeyDecryptorBuilder()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(passphrase),
        )

        val signature_generator = PGPSignatureGenerator(
            JcaPGPContentSignerBuilder(
                signing_key.publicKey.algorithm,
                HashAlgorithmTags.SHA512,
            ).setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )
        signature_generator.init(PGPSignature.BINARY_DOCUMENT, private_key)

        val encrypted_generator = PGPEncryptedDataGenerator(
            JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(SecureRandom())
                .setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )
        encrypted_generator.addMethod(
            JcePublicKeyKeyEncryptionMethodGenerator(encryption_key)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )

        val out = ByteArrayOutputStream()
        val armored_stream = ArmoredOutputStream(out)
        val encrypted_stream = encrypted_generator.open(armored_stream, ByteArray(1 shl 16))
        val compressor = PGPCompressedDataGenerator(CompressionAlgorithmTags.ZIP)
        val compressed_stream = compressor.open(encrypted_stream)
        signature_generator.generateOnePassVersion(false).encode(compressed_stream)

        val literal_generator = PGPLiteralDataGenerator()
        val bytes = plaintext.toByteArray(Charsets.UTF_8)
        val literal_stream = literal_generator.open(
            compressed_stream,
            PGPLiteralData.UTF8,
            PGPLiteralData.CONSOLE,
            Date(),
            ByteArray(1 shl 16),
        )
        literal_stream.write(bytes)
        signature_generator.update(bytes)
        literal_generator.close()
        signature_generator.generate().encode(compressed_stream)
        compressor.close()
        encrypted_stream.close()
        armored_stream.close()

        return out.toString(Charsets.UTF_8.name())
    }

    private fun select_encryption_key(armored_public_key: String): PGPPublicKey {
        val rings = PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8)),
            ),
            JcaKeyFingerprintCalculator(),
        )
        return rings.keyRings.asSequence()
            .flatMap { it.publicKeys.asSequence() }
            .first { it.isEncryptionKey && !it.isMasterKey }
    }

    private fun select_signing_key(armored_private_key: String): PGPSecretKey {
        val rings = PGPSecretKeyRingCollection(
            PGPUtil.getDecoderStream(
                ByteArrayInputStream(armored_private_key.toByteArray(Charsets.UTF_8)),
            ),
            JcaKeyFingerprintCalculator(),
        )
        return rings.keyRings.asSequence()
            .flatMap { it.secretKeys.asSequence() }
            .first { it.isSigningKey && it.publicKey.isMasterKey }
    }
}
