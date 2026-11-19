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
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpKeyValidityTest {

    private val day_ms = 24L * 60 * 60 * 1000
    private val created = 1_700_000_000_000L

    @Test
    fun key_without_expiry_is_usable_after_creation() {
        assertNull(PgpKeyValidity.expiry_ms(created, 0L))
        assertTrue(PgpKeyValidity.usable_at(created, 0L, false, null, created + 1000 * day_ms))
    }

    @Test
    fun revoked_key_is_never_usable() {
        assertFalse(PgpKeyValidity.usable_at(created, 0L, true, null, created + day_ms))
    }

    @Test
    fun key_is_unusable_before_creation() {
        assertFalse(PgpKeyValidity.usable_at(created, 0L, false, null, created - 1))
    }

    @Test
    fun key_is_unusable_from_its_expiry() {
        val valid_seconds = 86_400L
        assertEquals(created + day_ms, PgpKeyValidity.expiry_ms(created, valid_seconds))
        assertTrue(PgpKeyValidity.usable_at(created, valid_seconds, false, null, created + day_ms - 1))
        assertFalse(PgpKeyValidity.usable_at(created, valid_seconds, false, null, created + day_ms))
    }

    @Test
    fun expired_primary_makes_subkey_unusable() {
        assertFalse(PgpKeyValidity.usable_at(created, 0L, false, created + day_ms, created + 2 * day_ms))
        assertTrue(PgpKeyValidity.usable_at(created, 0L, false, created + day_ms, created + day_ms / 2))
    }

    @Test
    fun message_with_two_literal_packets_is_rejected() {
        val passphrase = "correct horse battery staple".toCharArray()
        val recipient = PgpKeyGenerator.generate("Recipient", "recipient@astermail.org", passphrase)
        val single = encrypt_literals(recipient.armored_public_key, listOf("first"))
        assertEquals("first", PgpDecryptor.decrypt(single, recipient.armored_private_key, passphrase))
        val doubled = encrypt_literals(recipient.armored_public_key, listOf("first", "second"))
        val result = PgpDecryptor.decrypt_with_status(doubled, recipient.armored_private_key, passphrase, null)
        assertNull(result.plaintext)
        assertEquals(PgpSignatureStatus.NONE, result.signature)
    }

    private fun encryption_key(armored_public_key: String): PGPPublicKey {
        val rings = PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        )
        return rings.keyRings.asSequence()
            .flatMap { it.publicKeys.asSequence() }
            .first { it.isEncryptionKey && !it.isMasterKey }
    }

    private fun encrypt_literals(armored_public_key: String, parts: List<String>): String {
        val payload = ByteArrayOutputStream()
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            PGPLiteralDataGenerator().open(payload, PGPLiteralData.UTF8, PGPLiteralData.CONSOLE, bytes.size.toLong(), Date())
                .use { it.write(bytes) }
        }
        val generator = PGPEncryptedDataGenerator(
            JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(SecureRandom())
                .setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )
        generator.addMethod(
            JcePublicKeyKeyEncryptionMethodGenerator(encryption_key(armored_public_key))
                .setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )
        val bytes = payload.toByteArray()
        val armored = ByteArrayOutputStream()
        ArmoredOutputStream(armored).use { out ->
            generator.open(out, bytes.size.toLong()).use { it.write(bytes) }
        }
        return armored.toString(Charsets.UTF_8.name())
    }
}
