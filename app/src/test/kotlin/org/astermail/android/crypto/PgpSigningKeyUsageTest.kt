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
import java.security.Security
import java.util.Date
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyPacket
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.bcpg.sig.RevocationReasonTags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPLiteralDataGenerator
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcePGPDataEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyKeyEncryptionMethodGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PgpSigningKeyUsageTest {

    private val rng = SecureRandom()
    private val created = Date(System.currentTimeMillis() - 24L * 60 * 60 * 1000)
    private val passphrase = "correct horse battery staple".toCharArray()
    private val sign_flags = KeyFlags.SIGN_DATA or KeyFlags.CERTIFY_OTHER
    private val encrypt_flags = KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    private val recipient = PgpKeyGenerator.generate("Recipient", "recipient@astermail.org", passphrase)

    private class sender(val ring: PGPPublicKeyRing, val primary: BcPGPKeyPair, val sub: BcPGPKeyPair)

    private fun ed25519_pair(): BcPGPKeyPair {
        val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(rng)) }
        return BcPGPKeyPair(PublicKeyPacket.VERSION_4, PublicKeyAlgorithmTags.EDDSA_LEGACY, gen.generateKeyPair(), created)
    }

    private fun flags(value: Int, at: Date = created) = PGPSignatureSubpacketGenerator().apply {
        setSignatureCreationTime(false, at)
        setKeyFlags(false, value)
    }.generate()

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.use { it.readBytes().toString(Charsets.UTF_8) }

    private fun with_subkey_signature(key: sender, signature: PGPSignature): sender {
        val updated = PGPPublicKey.addCertification(key.ring.getPublicKey(key.sub.keyID), signature)
        return sender(PGPPublicKeyRing.insertPublicKey(key.ring, updated), key.primary, key.sub)
    }

    private fun rebind(key: sender, sub_flags: Int, back_signed: Boolean): sender {
        val at = Date(System.currentTimeMillis() - 60L * 60 * 1000)
        val hashed = PGPSignatureSubpacketGenerator().apply {
            setSignatureCreationTime(false, at)
            setKeyFlags(false, sub_flags)
        }
        val unhashed = PGPSignatureSubpacketGenerator()
        if (back_signed) {
            val back = PGPSignatureGenerator(
                BcPGPContentSignerBuilder(key.sub.publicKey.algorithm, HashAlgorithmTags.SHA512),
                key.sub.publicKey,
            )
            back.init(PGPSignature.PRIMARYKEY_BINDING, key.sub.privateKey)
            back.setHashedSubpackets(PGPSignatureSubpacketGenerator().apply { setSignatureCreationTime(false, at) }.generate())
            unhashed.setEmbeddedSignature(false, back.generateCertification(key.primary.publicKey, key.sub.publicKey))
        }
        val binder = PGPSignatureGenerator(
            BcPGPContentSignerBuilder(key.primary.publicKey.algorithm, HashAlgorithmTags.SHA512),
            key.primary.publicKey,
        )
        binder.init(PGPSignature.SUBKEY_BINDING, key.primary.privateKey)
        binder.setHashedSubpackets(hashed.generate())
        binder.setUnhashedSubpackets(unhashed.generate())
        return with_subkey_signature(key, binder.generateCertification(key.primary.publicKey, key.sub.publicKey))
    }

    private fun revoke_subkey(key: sender): sender {
        val revoker = PGPSignatureGenerator(
            BcPGPContentSignerBuilder(key.primary.publicKey.algorithm, HashAlgorithmTags.SHA512),
            key.primary.publicKey,
        )
        revoker.init(PGPSignature.SUBKEY_REVOCATION, key.primary.privateKey)
        revoker.setHashedSubpackets(
            PGPSignatureSubpacketGenerator().apply {
                setSignatureCreationTime(false, Date(System.currentTimeMillis() - 60L * 60 * 1000))
                setRevocationReason(false, RevocationReasonTags.KEY_COMPROMISED, "")
            }.generate(),
        )
        return with_subkey_signature(key, revoker.generateCertification(key.primary.publicKey, key.sub.publicKey))
    }

    private fun build_sender(primary_flags: Int, sub_flags: Int, back_signed: Boolean): sender {
        val primary = ed25519_pair()
        val sub = ed25519_pair()
        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            primary,
            "Sender <sender@example.com>",
            BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA1),
            flags(primary_flags),
            null,
            BcPGPContentSignerBuilder(primary.publicKey.algorithm, HashAlgorithmTags.SHA512),
            BcPBESecretKeyEncryptorBuilder(
                SymmetricKeyAlgorithmTags.AES_256,
                BcPGPDigestCalculatorProvider().get(HashAlgorithmTags.SHA256),
            ).build("sender".toCharArray()),
        )
        if (back_signed) {
            generator.addSubKey(
                sub,
                flags(sub_flags),
                null,
                BcPGPContentSignerBuilder(sub.publicKey.algorithm, HashAlgorithmTags.SHA512),
            )
        } else {
            generator.addSubKey(sub, flags(sub_flags), null)
        }
        val ring = PGPPublicKeyRing(generator.generateSecretKeyRing().publicKeys.asSequence().toList())
        return sender(ring, primary, sub)
    }

    private fun armored(ring: PGPPublicKeyRing): String {
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { ring.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun encryption_key(armored_public_key: String): PGPPublicKey =
        PGPPublicKeyRingCollection(
            PGPUtil.getDecoderStream(ByteArrayInputStream(armored_public_key.toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        ).keyRings.asSequence()
            .flatMap { it.publicKeys.asSequence() }
            .first { it.isEncryptionKey && !it.isMasterKey }

    private fun signed_message(signer: BcPGPKeyPair, text: String): String = encrypted(signed_payload(signer, text))

    private fun signed_payload(signer: BcPGPKeyPair, text: String): ByteArray {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArrayOutputStream()
        val packets = BCPGOutputStream(payload)
        val signature = PGPSignatureGenerator(
            BcPGPContentSignerBuilder(signer.publicKey.algorithm, HashAlgorithmTags.SHA256),
            signer.publicKey,
        )
        signature.init(PGPSignature.BINARY_DOCUMENT, signer.privateKey)
        signature.generateOnePassVersion(false).encode(packets)
        PGPLiteralDataGenerator().open(packets, PGPLiteralData.BINARY, PGPLiteralData.CONSOLE, bytes.size.toLong(), Date())
            .use { it.write(bytes) }
        signature.update(bytes)
        signature.generate().encode(packets)
        packets.close()
        return payload.toByteArray()
    }

    private fun encrypted(body: ByteArray): String {
        val generator = PGPEncryptedDataGenerator(
            JcePGPDataEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256)
                .setWithIntegrityPacket(true)
                .setSecureRandom(rng)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )
        generator.addMethod(
            JcePublicKeyKeyEncryptionMethodGenerator(encryption_key(recipient.armored_public_key))
                .setProvider(BouncyCastleProvider.PROVIDER_NAME),
        )
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { armor -> generator.open(armor, body.size.toLong()).use { it.write(body) } }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun status_of(key: sender, signer: BcPGPKeyPair): PgpSignatureStatus {
        val result = PgpDecryptor.decrypt_with_status(
            signed_message(signer, "hello"),
            recipient.armored_private_key,
            passphrase,
            armored(key.ring),
        )
        assertEquals("hello", result.plaintext)
        return result.signature
    }

    @Test
    fun a_signature_from_an_encryption_only_subkey_is_not_valid() {
        val key = build_sender(sign_flags, encrypt_flags, back_signed = false)
        assertNotEquals(PgpSignatureStatus.VALID, status_of(key, key.sub))
    }

    @Test
    fun a_signing_subkey_without_a_back_signature_is_not_valid() {
        val key = build_sender(sign_flags, KeyFlags.SIGN_DATA, back_signed = false)
        assertNotEquals(PgpSignatureStatus.VALID, status_of(key, key.sub))
    }

    @Test
    fun a_primary_key_without_the_sign_flag_is_not_valid() {
        val key = build_sender(KeyFlags.CERTIFY_OTHER, KeyFlags.SIGN_DATA, back_signed = true)
        assertNotEquals(PgpSignatureStatus.VALID, status_of(key, key.primary))
    }

    @Test
    fun a_bound_signing_subkey_is_valid() {
        val key = build_sender(sign_flags, KeyFlags.SIGN_DATA, back_signed = true)
        assertEquals(PgpSignatureStatus.VALID, status_of(key, key.sub))
    }

    @Test
    fun a_signing_primary_key_is_valid() {
        val key = build_sender(sign_flags, encrypt_flags, back_signed = false)
        assertEquals(PgpSignatureStatus.VALID, status_of(key, key.primary))
    }

    @Test
    fun a_signing_subkey_rebound_without_a_back_signature_stays_valid() {
        val key = rebind(build_sender(sign_flags, KeyFlags.SIGN_DATA, back_signed = true), KeyFlags.SIGN_DATA, back_signed = false)
        assertEquals(PgpSignatureStatus.VALID, status_of(key, key.sub))
    }

    @Test
    fun a_newer_binding_without_the_sign_flag_wins_over_an_older_one() {
        val key = rebind(build_sender(sign_flags, KeyFlags.SIGN_DATA, back_signed = true), encrypt_flags, back_signed = false)
        assertNotEquals(PgpSignatureStatus.VALID, status_of(key, key.sub))
    }

    @Test
    fun a_revoked_signing_subkey_is_not_valid() {
        val key = revoke_subkey(build_sender(sign_flags, KeyFlags.SIGN_DATA, back_signed = true))
        assertNotEquals(PgpSignatureStatus.VALID, status_of(key, key.sub))
    }

    @Test
    fun a_gnupg_rsa_signing_subkey_with_a_sha1_binding_is_valid() {
        val signed = PGPUtil.getDecoderStream(
            ByteArrayInputStream(resource("gnupg_rsa_signed_message.asc").toByteArray(Charsets.UTF_8)),
        ).use { it.readBytes() }
        val result = PgpDecryptor.decrypt_with_status(
            encrypted(signed),
            recipient.armored_private_key,
            passphrase,
            resource("gnupg_rsa_signing_subkey.asc"),
        )
        assertEquals("hello from gnupg", result.plaintext)
        assertEquals(PgpSignatureStatus.VALID, result.signature)
    }
}
