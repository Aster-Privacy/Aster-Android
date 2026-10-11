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

package org.astermail.android.mail.ratchet

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.SecureRandom
import java.util.Date
import java.util.Locale
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublishedSigningKeyTest {

    private val passphrase = "orbit-lantern-9-quartz"
    private val kem_b64 = "BASE64KEMIDENTITYKEYxxxxxxxxxxxxxxxxxxxxxxx="
    private val spk_b64 = "BASE64SIGNEDPREKEYyyyyyyyyyyyyyyyyyyyyyyyyy="
    private val pq_b64 = "BASE64PQIDENTITYKEYzzzzzzzzzzzzzzzzzzzzzzzz="

    @Test
    fun a_rotated_account_signs_with_the_published_key_held_in_previous_keys() {
        val original = generate_test_secret_key()
        val rotated = generate_test_secret_key()
        val candidates = PublishedSigningKey.candidates(
            identity_key = armor_secret_key(original),
            previous_keys = listOf(armor_secret_key(rotated)),
        )

        val choice = PublishedSigningKey.choose(candidates, fingerprint_hex(rotated))

        assertEquals(PublishedSigningKeyChoice.Selected(armor_secret_key(rotated)), choice)
    }

    @Test
    fun the_bundle_signature_verifies_against_the_published_key_after_a_rotation() {
        val original = generate_test_secret_key()
        val rotated = generate_test_secret_key()
        val candidates = PublishedSigningKey.candidates(
            identity_key = armor_secret_key(original),
            previous_keys = listOf(armor_secret_key(rotated)),
        )
        val selected = PublishedSigningKey.choose(candidates, fingerprint_hex(rotated))
            as PublishedSigningKeyChoice.Selected

        val block = PrekeyBindingSigner.sign_cleartext(
            selected.armored_secret_key,
            passphrase.toCharArray(),
            PrekeyBindingSigner.binding_for_bundle(kem_b64, spk_b64, pq_b64),
        )

        assertEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(block, armor_public_key(rotated), kem_b64, spk_b64, pq_b64),
        )
        assertNotEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(block, armor_public_key(original), kem_b64, spk_b64, pq_b64),
        )
    }

    @Test
    fun an_account_that_never_rotated_signs_with_its_identity_key() {
        val original = generate_test_secret_key()
        val candidates = PublishedSigningKey.candidates(armor_secret_key(original), null)

        val choice = PublishedSigningKey.choose(candidates, fingerprint_hex(original).lowercase(Locale.US))

        assertEquals(PublishedSigningKeyChoice.Selected(armor_secret_key(original)), choice)
    }

    @Test
    fun a_published_key_the_vault_does_not_hold_is_never_substituted() {
        val original = generate_test_secret_key()
        val foreign = generate_test_secret_key()
        val candidates = PublishedSigningKey.candidates(armor_secret_key(original), emptyList())

        val choice = PublishedSigningKey.choose(candidates, fingerprint_hex(foreign))

        assertEquals(PublishedSigningKeyChoice.NotHeld, choice)
    }

    @Test
    fun an_account_with_no_published_key_signs_with_its_identity_key() {
        val original = generate_test_secret_key()
        val candidates = PublishedSigningKey.candidates(armor_secret_key(original), null)

        assertEquals(
            PublishedSigningKeyChoice.Selected(armor_secret_key(original)),
            PublishedSigningKey.choose(candidates, ""),
        )
    }

    @Test
    fun candidates_skip_values_that_are_not_armored_private_keys() {
        val original = generate_test_secret_key()
        val candidates = PublishedSigningKey.candidates(
            identity_key = "not-a-key",
            previous_keys = listOf("", armor_secret_key(original), armor_secret_key(original)),
        )

        assertEquals(listOf(armor_secret_key(original)), candidates)
        assertEquals(
            PublishedSigningKeyChoice.NoCandidates,
            PublishedSigningKey.choose(PublishedSigningKey.candidates("not-a-key", null), "AB"),
        )
        assertNull(PublishedSigningKey.fingerprint_of("not-a-key"))
    }

    @Test
    fun fingerprints_compare_without_spacing_or_case() {
        assertEquals("0C75B52F", PublishedSigningKey.normalize_fingerprint("0c75 b5:2f"))
    }

    private fun fingerprint_hex(secret_key: PGPSecretKey): String =
        String.format(Locale.US, "%040X", BigInteger(1, secret_key.publicKey.fingerprint))

    private fun generate_test_secret_key(): PGPSecretKey {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
        val key_pair = BcPGPKeyPair(
            PublicKeyAlgorithmTags.EDDSA_LEGACY,
            generator.generateKeyPair(),
            Date(),
        )

        val digests = BcPGPDigestCalculatorProvider()
        val subpackets = PGPSignatureSubpacketGenerator()
        subpackets.setKeyFlags(false, KeyFlags.CERTIFY_OTHER or KeyFlags.SIGN_DATA)

        return PGPSecretKey(
            PGPSignature.DEFAULT_CERTIFICATION,
            key_pair,
            "Vector <vector@astermail.org>",
            digests.get(HashAlgorithmTags.SHA1),
            subpackets.generate(),
            null,
            BcPGPContentSignerBuilder(key_pair.publicKey.algorithm, HashAlgorithmTags.SHA256),
            BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, digests.get(HashAlgorithmTags.SHA256))
                .build(passphrase.toCharArray()),
        )
    }

    private fun armor_secret_key(secret_key: PGPSecretKey): String {
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { secret_key.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }

    private fun armor_public_key(secret_key: PGPSecretKey): String {
        val out = ByteArrayOutputStream()
        ArmoredOutputStream(out).use { secret_key.publicKey.encode(it) }
        return out.toString(Charsets.UTF_8.name())
    }
}
