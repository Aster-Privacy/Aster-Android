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

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.security.Security
import java.util.Date
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyPacket
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.generators.X25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.X25519KeyGenerationParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpEncryptionKeySelectionTest {

    private val day_ms = 24L * 60 * 60 * 1000

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private fun public_key(
        created: Date = Date(),
        master_valid_seconds: Long = 0L,
        sub_valid_seconds: Long = 0L,
        sub_flags: Int = KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE,
    ): String {
        val rng = SecureRandom()
        val master_gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(rng)) }
        val sub_gen = X25519KeyPairGenerator().apply { init(X25519KeyGenerationParameters(rng)) }
        val master = BcPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.EDDSA_LEGACY, master_gen.generateKeyPair(), created)
        val sub = BcPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.ECDH, sub_gen.generateKeyPair(), created)
        val master_packets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER)
            if (master_valid_seconds > 0) setKeyExpirationTime(false, master_valid_seconds)
        }
        val sub_packets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, sub_flags)
            if (sub_valid_seconds > 0) setKeyExpirationTime(false, sub_valid_seconds)
        }
        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            master,
            "Recipient <recipient@example.com>",
            BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            master_packets.generate(),
            null,
            BcPGPContentSignerBuilder(master.publicKey.algorithm, HashAlgorithmTags.SHA512),
            BcPBESecretKeyEncryptorBuilder(
                SymmetricKeyAlgorithmTags.AES_256,
                BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256),
            ).build("passphrase".toCharArray()),
        )
        generator.addSubKey(sub, sub_packets.generate(), null)
        val ring = PGPPublicKeyRing(generator.generateSecretKeyRing().publicKeys.asSequence().toList())
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { ring.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }

    private val long_ago = Date(System.currentTimeMillis() - 30 * day_ms)

    @Test
    fun current_key_is_used() {
        val key = public_key()
        assertTrue(PgpEncryptor.can_encrypt_to(key))
        assertNotNull(PgpEncryptor.encrypt_to_keys("hello", listOf(key)))
    }

    @Test
    fun expired_subkey_is_not_used() {
        val key = public_key(created = long_ago, sub_valid_seconds = 86_400L)
        assertFalse(PgpEncryptor.can_encrypt_to(key))
        assertNull(PgpEncryptor.encrypt_to_keys("hello", listOf(key)))
    }

    @Test
    fun expired_primary_disables_its_subkeys() {
        val key = public_key(created = long_ago, master_valid_seconds = 86_400L)
        assertFalse(PgpEncryptor.can_encrypt_to(key))
    }

    @Test
    fun subkey_without_encryption_flags_is_not_used() {
        val key = public_key(sub_flags = KeyFlags.AUTHENTICATION)
        assertFalse(PgpEncryptor.can_encrypt_to(key))
    }

    @Test
    fun unexpired_key_with_expiry_is_used() {
        val key = public_key(created = long_ago, master_valid_seconds = 365 * 86_400L, sub_valid_seconds = 365 * 86_400L)
        assertTrue(PgpEncryptor.can_encrypt_to(key))
    }

    @Test
    fun missing_flags_still_allow_encryption() {
        assertTrue(PgpKeyValidity.encrypts_messages(null))
        assertTrue(PgpKeyValidity.encrypts_messages(KeyFlags.ENCRYPT_STORAGE))
        assertFalse(PgpKeyValidity.encrypts_messages(KeyFlags.SIGN_DATA))
    }
}
