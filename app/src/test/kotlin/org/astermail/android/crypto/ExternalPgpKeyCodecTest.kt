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
import java.util.Base64
import org.astermail.android.api.encryption.ExternalPgpKeyInfo
import java.security.KeyPairGenerator
import java.util.Date
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExternalPgpKeyCodecTest {

    private lateinit var vector: JSONObject

    @Before
    fun set_up() {
        val stream = checkNotNull(
            javaClass.classLoader?.getResourceAsStream("external_pgp_key_vectors.json"),
        )
        vector = JSONObject(stream.bufferedReader().use { it.readText() })
    }

    private fun hex(value: String): ByteArray =
        ByteArray(value.length / 2) { value.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun to_hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private val kek get() = hex(vector.getString("data_kek_hex"))
    private val user_id get() = vector.getString("user_id")
    private val fingerprint get() = vector.getString("fingerprint")
    private val secret get() = Base64.getDecoder().decode(vector.getString("secret_key_b64"))
    private val wrapped get() = vector.getString("wrapped_private_key")

    @Test
    fun vector_matches_the_contract_version_and_label() {
        assertEquals(1, vector.getInt("version"))
        assertEquals(ExternalPgpKeyCodec.LABEL, vector.getString("label"))
    }

    @Test
    fun derives_the_vector_wrap_key_and_aad() {
        assertEquals(vector.getString("wrap_key_hex"), to_hex(ExternalPgpKeyCodec.derive_wrap_key(kek, user_id)))
        assertEquals(vector.getString("aad"), String(ExternalPgpKeyCodec.aad(user_id, fingerprint), Charsets.UTF_8))
    }

    @Test
    fun wrap_reproduces_the_vector_blob() {
        val produced = ExternalPgpKeyCodec.wrap(secret, kek, user_id, fingerprint, hex(vector.getString("nonce_hex")))
        assertEquals(wrapped, produced)
    }

    @Test
    fun unwrap_opens_the_vector_blob() {
        assertArrayEquals(secret, ExternalPgpKeyCodec.unwrap(wrapped, kek, user_id, fingerprint))
    }

    @Test
    fun user_id_and_fingerprint_are_normalized_before_use() {
        val shouty_user_id = user_id.uppercase()
        val grouped_fingerprint = fingerprint.uppercase().chunked(4).joinToString(" ")
        assertArrayEquals(secret, ExternalPgpKeyCodec.unwrap(wrapped, kek, shouty_user_id, grouped_fingerprint))
    }

    @Test
    fun wrap_and_unwrap_round_trip_with_a_random_kek() {
        val random_kek = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val nonce = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val blob = ExternalPgpKeyCodec.wrap(secret, random_kek, user_id, fingerprint, nonce)
        val decoded = Base64.getDecoder().decode(blob)
        assertEquals(ExternalPgpKeyCodec.BLOB_VERSION, decoded[0])
        assertEquals(1 + 12 + secret.size + 16, decoded.size)
        assertArrayEquals(secret, ExternalPgpKeyCodec.unwrap(blob, random_kek, user_id, fingerprint))
    }

    @Test
    fun a_different_user_id_fails_the_aad() {
        assertNull(ExternalPgpKeyCodec.unwrap(wrapped, kek, "00000000-0000-0000-0000-000000000002", fingerprint))
    }

    @Test
    fun a_different_fingerprint_fails_the_aad() {
        assertNull(ExternalPgpKeyCodec.unwrap(wrapped, kek, user_id, "ab".repeat(20)))
    }

    @Test
    fun a_wrong_kek_fails() {
        val other = kek.also { it[0] = (it[0].toInt() xor 1).toByte() }
        assertNull(ExternalPgpKeyCodec.unwrap(wrapped, other, user_id, fingerprint))
    }

    @Test
    fun a_flipped_ciphertext_byte_fails() {
        val bytes = Base64.getDecoder().decode(wrapped)
        bytes[bytes.size - 20] = (bytes[bytes.size - 20].toInt() xor 0x01).toByte()
        assertNull(ExternalPgpKeyCodec.unwrap(Base64.getEncoder().encodeToString(bytes), kek, user_id, fingerprint))
    }

    @Test
    fun an_unknown_blob_version_fails() {
        val bytes = Base64.getDecoder().decode(wrapped)
        bytes[0] = 0x02
        assertNull(ExternalPgpKeyCodec.unwrap(Base64.getEncoder().encodeToString(bytes), kek, user_id, fingerprint))
    }

    @Test
    fun malformed_input_fails_quietly() {
        assertNull(ExternalPgpKeyCodec.unwrap("not base64 !!", kek, user_id, fingerprint))
        assertNull(ExternalPgpKeyCodec.unwrap("AQ==", kek, user_id, fingerprint))
        assertNull(ExternalPgpKeyCodec.unwrap(wrapped, ByteArray(16), user_id, fingerprint))
        assertNull(ExternalPgpKeyCodec.unwrap(wrapped, kek, user_id, "abcd"))
    }

    @Test
    fun parses_the_vector_key_into_decryption_and_signing_keys() {
        val unlocked = require_present(ExternalPgpKeyCodec.parse_unlocked(secret, fingerprint))
        assertEquals(fingerprint, unlocked.fingerprint)
        assertEquals(1, unlocked.decryption_keys.size)
        assertNotNull(unlocked.signing_key)
        assertEquals(
            java.lang.Long.parseUnsignedLong(vector.getString("key_id"), 16),
            unlocked.signing_key!!.keyID,
        )
        assertNull(unlocked.expires_at_ms)
        assertFalse(unlocked.revoked)
        assertTrue(unlocked.can_sign(System.currentTimeMillis()))
    }

    @Test
    fun a_fingerprint_that_does_not_match_the_key_is_rejected() {
        val wrong = "ab".repeat(20)
        assertNull(ExternalPgpKeyCodec.parse_unlocked(secret, wrong))
        val sealed_under_wrong = ExternalPgpKeyCodec.wrap(secret, kek, user_id, wrong, ByteArray(12) { 9 })
        val info = ExternalPgpKeyInfo(fingerprint = wrong, wrapped_private_key = sealed_under_wrong, published = true)
        assertNull(ExternalPgpKeyCodec.open(info, kek, user_id))
    }

    @Test
    fun open_unlocks_the_vector_row() {
        val info = ExternalPgpKeyInfo(fingerprint = fingerprint.uppercase(), wrapped_private_key = wrapped, published = true)
        val unlocked = ExternalPgpKeyCodec.open(info, kek, user_id)
        assertNotNull(unlocked)
        assertEquals(fingerprint, unlocked!!.fingerprint)
    }

    @Test
    fun garbage_secret_key_bytes_are_rejected() {
        assertNull(ExternalPgpKeyCodec.parse_unlocked(byteArrayOf(1, 2, 3), fingerprint))
    }

    @Test
    fun expired_or_revoked_keys_cannot_sign() {
        val unlocked = ExternalPgpKeyCodec.parse_unlocked(secret, fingerprint)!!
        val expired = UnlockedExternalPgpKey(unlocked.fingerprint, unlocked.decryption_keys, unlocked.signing_key, 1_000L, false)
        assertFalse(expired.can_sign(2_000L))
        assertTrue(expired.can_sign(500L))
        val revoked = UnlockedExternalPgpKey(unlocked.fingerprint, unlocked.decryption_keys, unlocked.signing_key, null, true)
        assertFalse(revoked.can_sign(0L))
        val no_signer = UnlockedExternalPgpKey(unlocked.fingerprint, unlocked.decryption_keys, null, null, false)
        assertFalse(no_signer.can_sign(0L))
    }

    @Test
    fun decrypts_a_message_encrypted_to_the_external_key() {
        val unlocked = ExternalPgpKeyCodec.parse_unlocked(secret, fingerprint)!!
        val result = PgpDecryptor.decrypt_with_unlocked_keys(
            vector.getString("message_armored"),
            unlocked.decryption_keys,
            null,
        )
        assertEquals(vector.getString("message_plaintext"), result.plaintext)
    }

    @Test
    fun decryption_without_the_external_key_yields_nothing() {
        val result = PgpDecryptor.decrypt_with_unlocked_keys(vector.getString("message_armored"), emptyMap(), null)
        assertNull(result.plaintext)
    }

    @Test
    fun signs_with_the_external_key_and_the_public_key_verifies() {
        val unlocked = ExternalPgpKeyCodec.parse_unlocked(secret, fingerprint)!!
        val data = "Content-Type: text/plain\r\n\r\nSigned body\r\n".toByteArray(Charsets.UTF_8)
        val detached = PgpSigner.sign_detached_unlocked(data, unlocked.signing_key!!)
        assertNotNull(detached)
        assertEquals("pgp-sha512", detached!!.micalg)

        val signature_stream = PGPUtil.getDecoderStream(ByteArrayInputStream(detached.signature.toByteArray(Charsets.UTF_8)))
        val signature = (JcaPGPObjectFactory(signature_stream).nextObject() as PGPSignatureList)[0]
        val public_ring = PGPPublicKeyRing(
            PGPUtil.getDecoderStream(ByteArrayInputStream(vector.getString("public_key_armored").toByteArray(Charsets.UTF_8))),
            JcaKeyFingerprintCalculator(),
        )
        val signer_public = public_ring.getPublicKey(signature.keyID)
        assertNotNull(signer_public)
        signature.init(JcaPGPContentVerifierBuilderProvider().setProvider("BC"), signer_public)
        signature.update(data)
        assertTrue(signature.verify())
    }

    private fun rsa_pair(): JcaPGPKeyPair {
        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        generator.initialize(2048)
        return JcaPGPKeyPair(PublicKeyAlgorithmTags.RSA_GENERAL, generator.generateKeyPair(), Date())
    }

    private fun flags(value: Int) =
        PGPSignatureSubpacketGenerator().apply { setKeyFlags(false, value) }.generate()

    private fun signer_builder(algorithm: Int) =
        JcaPGPContentSignerBuilder(algorithm, HashAlgorithmTags.SHA256).setProvider(BouncyCastleProvider.PROVIDER_NAME)

    private fun certify_only_ring(with_back_signature: Boolean): Triple<ByteArray, Long, Long> {
        java.security.Security.addProvider(BouncyCastleProvider())
        val primary = rsa_pair()
        val encrypt = rsa_pair()
        val sign = rsa_pair()
        val checksum = JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            primary,
            "Outside Owner <owner@astermail.org>",
            checksum,
            flags(KeyFlags.CERTIFY_OTHER),
            null,
            signer_builder(primary.publicKey.algorithm),
            null,
        )
        generator.addSubKey(encrypt, flags(KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE), null)
        if (with_back_signature) {
            generator.addSubKey(sign, flags(KeyFlags.SIGN_DATA), null, signer_builder(sign.publicKey.algorithm))
        } else {
            generator.addSubKey(sign, flags(KeyFlags.SIGN_DATA), null)
        }
        return Triple(generator.generateSecretKeyRing().encoded, encrypt.keyID, sign.keyID)
    }

    private fun fingerprint_of(ring_bytes: ByteArray): String =
        to_hex(
            org.bouncycastle.openpgp.PGPSecretKeyRing(ring_bytes, JcaKeyFingerprintCalculator()).publicKey.fingerprint,
        )

    @Test
    fun a_certify_only_primary_signs_with_its_back_signed_subkey() {
        val (ring, encrypt_id, sign_id) = certify_only_ring(with_back_signature = true)
        val unlocked = require_present(ExternalPgpKeyCodec.parse_unlocked(ring, fingerprint_of(ring)))
        assertEquals(sign_id, unlocked.signing_key!!.keyID)
        assertTrue(encrypt_id in unlocked.decryption_keys.keys)
    }

    @Test
    fun a_signing_subkey_without_a_back_signature_is_not_used() {
        val (ring, encrypt_id, _) = certify_only_ring(with_back_signature = false)
        val unlocked = require_present(ExternalPgpKeyCodec.parse_unlocked(ring, fingerprint_of(ring)))
        assertNull(unlocked.signing_key)
        assertTrue(encrypt_id in unlocked.decryption_keys.keys)
    }

    private fun ring_with_subkey_state(revoke_subkeys: Boolean, sign_expiry_seconds: Long): Triple<ByteArray, Long, Long> {
        java.security.Security.addProvider(BouncyCastleProvider())
        val primary = rsa_pair()
        val encrypt = rsa_pair()
        val sign = rsa_pair()
        val checksum = JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)
        val generator = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            primary,
            "Outside Owner <owner@astermail.org>",
            checksum,
            flags(KeyFlags.CERTIFY_OTHER),
            null,
            signer_builder(primary.publicKey.algorithm),
            null,
        )
        generator.addSubKey(encrypt, flags(KeyFlags.ENCRYPT_COMMS or KeyFlags.ENCRYPT_STORAGE), null)
        val sign_subpackets = PGPSignatureSubpacketGenerator().apply {
            setKeyFlags(false, KeyFlags.SIGN_DATA)
            if (sign_expiry_seconds > 0) setKeyExpirationTime(false, sign_expiry_seconds)
        }.generate()
        generator.addSubKey(sign, sign_subpackets, null, signer_builder(sign.publicKey.algorithm))
        var ring = generator.generateSecretKeyRing()
        if (revoke_subkeys) {
            for (subkey in listOf(encrypt, sign)) {
                val revoker = org.bouncycastle.openpgp.PGPSignatureGenerator(
                    signer_builder(primary.publicKey.algorithm),
                    primary.publicKey,
                )
                revoker.init(PGPSignature.SUBKEY_REVOCATION, primary.privateKey)
                val existing = ring.getSecretKey(subkey.keyID)
                val revocation = revoker.generateCertification(primary.publicKey, existing.publicKey)
                val revoked_public = org.bouncycastle.openpgp.PGPPublicKey.addCertification(existing.publicKey, revocation)
                val replaced = org.bouncycastle.openpgp.PGPSecretKey.replacePublicKey(existing, revoked_public)
                ring = org.bouncycastle.openpgp.PGPSecretKeyRing.insertSecretKey(ring, replaced)
            }
        }
        return Triple(ring.encoded, encrypt.keyID, sign.keyID)
    }

    @Test
    fun revoked_subkeys_still_decrypt_but_never_sign() {
        val (ring, encrypt_id, _) = ring_with_subkey_state(revoke_subkeys = true, sign_expiry_seconds = 0)
        val unlocked = require_present(ExternalPgpKeyCodec.parse_unlocked(ring, fingerprint_of(ring)))
        assertTrue(encrypt_id in unlocked.decryption_keys.keys)
        assertNull(unlocked.signing_key)
    }

    @Test
    fun an_expired_signing_subkey_is_not_chosen_as_the_signer() {
        val (ring, encrypt_id, sign_id) = ring_with_subkey_state(revoke_subkeys = false, sign_expiry_seconds = 60)
        val fingerprint = fingerprint_of(ring)
        val now = System.currentTimeMillis()
        val fresh = require_present(ExternalPgpKeyCodec.parse_unlocked(ring, fingerprint, now))
        assertEquals(sign_id, fresh.signing_key!!.keyID)
        val later = require_present(ExternalPgpKeyCodec.parse_unlocked(ring, fingerprint, now + 86_400_000L))
        assertNull(later.signing_key)
        assertTrue(encrypt_id in later.decryption_keys.keys)
    }

    @Test
    fun the_external_key_is_limited_to_inbound_item_types() {
        assertTrue(ExternalPgpKeyCodec.allows_item_type("received"))
        assertTrue(ExternalPgpKeyCodec.allows_item_type(null))
        assertTrue(ExternalPgpKeyCodec.allows_item_type(""))
        assertFalse(ExternalPgpKeyCodec.allows_item_type("sent"))
        assertFalse(ExternalPgpKeyCodec.allows_item_type(" Sent "))
        assertFalse(ExternalPgpKeyCodec.allows_item_type("draft"))
        assertFalse(ExternalPgpKeyCodec.allows_item_type("drafts"))
        assertFalse(ExternalPgpKeyCodec.allows_item_type("scheduled"))
    }

    private fun <T> require_present(value: T?): T {
        assertNotNull(value)
        return value!!
    }

    @Test
    fun only_the_primary_address_counts_as_the_imported_key_sender() {
        assertTrue(ExternalPgpKeyCodec.is_primary_sender("alice@astermail.org", "alice@astermail.org"))
        assertTrue(ExternalPgpKeyCodec.is_primary_sender("Alice <Alice@AsterMail.org>", " alice@astermail.org "))
        assertFalse(ExternalPgpKeyCodec.is_primary_sender("alias@astermail.org", "alice@astermail.org"))
        assertFalse(ExternalPgpKeyCodec.is_primary_sender("alice@example.com", "alice@astermail.org"))
        assertTrue(ExternalPgpKeyCodec.is_primary_sender("alice@astermail.org", null))
        assertTrue(ExternalPgpKeyCodec.is_primary_sender("", ""))
        assertTrue(ExternalPgpKeyCodec.is_primary_sender("a.lice+news@astermail.org", "alice@astermail.org"))
    }
}
