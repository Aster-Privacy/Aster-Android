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
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PgpRevocationTest {

    private val day_ms = 24L * 60 * 60 * 1000
    private val rng = SecureRandom()
    private val created = Date(System.currentTimeMillis() - 60 * day_ms)
    private val revoked_on = Date(System.currentTimeMillis() - 10 * day_ms)

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private class built(val master: BcPGPKeyPair, val ring: PGPPublicKeyRing, val sub_id: Long)

    private fun signing_pair(): BcPGPKeyPair {
        val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(rng)) }
        return BcPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.EDDSA_LEGACY, gen.generateKeyPair(), created)
    }

    private fun build(): built {
        val master = signing_pair()
        val sub_gen = X25519KeyPairGenerator().apply { init(X25519KeyGenerationParameters(rng)) }
        val sub = BcPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.ECDH, sub_gen.generateKeyPair(), created)
        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            master,
            "Recipient <recipient@example.com>",
            BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            PGPSignatureSubpacketGenerator().apply { setKeyFlags(false, KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER) }.generate(),
            null,
            BcPGPContentSignerBuilder(master.publicKey.algorithm, HashAlgorithmTags.SHA512),
            BcPBESecretKeyEncryptorBuilder(
                SymmetricKeyAlgorithmTags.AES_256,
                BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256),
            ).build("passphrase".toCharArray()),
        )
        generator.addSubKey(
            sub,
            PGPSignatureSubpacketGenerator().apply { setKeyFlags(false, KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE) }.generate(),
            null,
        )
        val ring = PGPPublicKeyRing(generator.generateSecretKeyRing().publicKeys.asSequence().toList())
        return built(master, ring, sub.keyID)
    }

    private fun revoke_subkey(key: built, reason: Int, signer: BcPGPKeyPair = key.master): PGPPublicKeyRing {
        val primary = key.ring.publicKey
        val sub = key.ring.getPublicKey(key.sub_id)
        val generator = PGPSignatureGenerator(
            BcPGPContentSignerBuilder(signer.publicKey.algorithm, HashAlgorithmTags.SHA512),
            signer.publicKey,
        )
        generator.init(PGPSignature.SUBKEY_REVOCATION, signer.privateKey)
        generator.setHashedSubpackets(
            PGPSignatureSubpacketGenerator().apply {
                setSignatureCreationTime(false, revoked_on)
                setRevocationReason(false, reason.toByte(), "")
            }.generate(),
        )
        val signature = generator.generateCertification(primary, sub)
        return PGPPublicKeyRing.insertPublicKey(key.ring, PGPPublicKey.addCertification(sub, signature))
    }

    private fun armored(ring: PGPPublicKeyRing): String {
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { ring.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun sub_revoked_at(ring: PGPPublicKeyRing, sub_id: Long, at: Date): Boolean =
        PgpKeyValidity.key_revoked_at(ring.publicKey, ring.getPublicKey(sub_id), at.time)

    @Test
    fun soft_revocations_end_use_only_after_they_were_made() {
        val rule = listOf(PgpRevocation(1, 1_000L))
        assertFalse(PgpKeyValidity.revoked_at(rule, 999L))
        assertFalse(PgpKeyValidity.revoked_at(rule, 1_000L))
        assertTrue(PgpKeyValidity.revoked_at(rule, 1_001L))
        assertFalse(PgpKeyValidity.revoked_at(listOf(PgpRevocation(3, 1_000L)), 500L))
    }

    @Test
    fun hard_revocations_cover_every_signature() {
        assertTrue(PgpKeyValidity.revoked_at(listOf(PgpRevocation(2, 1_000L)), 500L))
        assertTrue(PgpKeyValidity.revoked_at(listOf(PgpRevocation(0, 1_000L)), 500L))
        assertTrue(PgpKeyValidity.revoked_at(listOf(PgpRevocation(null, 1_000L)), 500L))
        assertFalse(PgpKeyValidity.revoked_at(emptyList(), 500L))
    }

    @Test
    fun a_superseded_subkey_keeps_its_older_signatures() {
        val key = build()
        val ring = revoke_subkey(key, reason = 1)
        assertFalse(sub_revoked_at(ring, key.sub_id, Date(revoked_on.time - day_ms)))
        assertTrue(sub_revoked_at(ring, key.sub_id, Date(revoked_on.time + day_ms)))
        assertFalse(PgpEncryptor.can_encrypt_to(armored(ring)))
    }

    @Test
    fun a_compromised_subkey_loses_every_signature() {
        val key = build()
        val ring = revoke_subkey(key, reason = 2)
        assertTrue(sub_revoked_at(ring, key.sub_id, Date(revoked_on.time - day_ms)))
    }

    @Test
    fun a_revocation_from_another_key_is_ignored() {
        val key = build()
        val ring = revoke_subkey(key, reason = 2, signer = signing_pair())
        assertFalse(sub_revoked_at(ring, key.sub_id, Date()))
        assertTrue(PgpEncryptor.can_encrypt_to(armored(ring)))
    }
}
