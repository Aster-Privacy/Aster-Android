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
import java.io.File
import java.security.SecureRandom
import java.util.Date
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.bcpg.sig.KeyFlags
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPSignatureSubpacketGenerator
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.bc.BcPBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPDigestCalculatorProvider
import org.bouncycastle.openpgp.operator.bc.BcPGPKeyPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrekeyBindingSignerTest {

    private val passphrase = "orbit-lantern-9-quartz"
    private val kem_b64 = "BASE64KEMIDENTITYKEYxxxxxxxxxxxxxxxxxxxxxxx="
    private val spk_b64 = "BASE64SIGNEDPREKEYyyyyyyyyyyyyyyyyyyyyyyyyy="

    @Test
    fun canonical_binding_matches_the_web_client_format() {
        assertEquals(
            "aster-ratchet-prekey-v1:$kem_b64.$spk_b64",
            PrekeyBindingSigner.canonical_binding(kem_b64, spk_b64),
        )
    }

    @Test
    fun signing_produces_a_cleartext_signature_that_verifies() {
        val secret_key = generate_test_secret_key()
        val armored_secret = armor_secret_key(secret_key)
        val text = PrekeyBindingSigner.canonical_binding(kem_b64, spk_b64)

        val armored = PrekeyBindingSigner.sign_cleartext(
            armored_secret_key = armored_secret,
            passphrase = passphrase.toCharArray(),
            text = text,
        )

        assertTrue(armored.startsWith("-----BEGIN PGP SIGNED MESSAGE-----"))
        assertTrue(armored.contains(text))
        assertTrue(armored.contains("-----BEGIN PGP SIGNATURE-----"))

        val (valid, signed_text) = verify_cleartext(armored, secret_key.publicKey)
        assertTrue(valid)
        assertEquals(text, signed_text)

        write_interop_vector(secret_key, text, armored)
    }

    @Test
    fun a_wrong_passphrase_throws_instead_of_producing_a_broken_signature() {
        val secret_key = generate_test_secret_key()
        val armored_secret = armor_secret_key(secret_key)

        val result = runCatching {
            PrekeyBindingSigner.sign_cleartext(
                armored_secret_key = armored_secret,
                passphrase = "not-the-passphrase".toCharArray(),
                text = PrekeyBindingSigner.canonical_binding(kem_b64, spk_b64),
            )
        }

        assertTrue(result.isFailure)
    }

    @Test
    fun armored_private_key_detection_rejects_legacy_material() {
        assertTrue(
            PrekeyBindingSigner.looks_like_armored_private_key(
                "-----BEGIN PGP PRIVATE KEY BLOCK-----\n...",
            ),
        )
        assertTrue(!PrekeyBindingSigner.looks_like_armored_private_key("q4fzXm0="))
        assertTrue(
            !PrekeyBindingSigner.looks_like_armored_private_key(
                "-----BEGIN PGP PUBLIC KEY BLOCK-----\n...",
            ),
        )
    }

    @Test
    fun signs_with_an_external_fixture_key_when_provided() {
        val key_path = System.getenv("ASTER_PREKEY_FIXTURE_KEY") ?: return
        val fixture_passphrase = System.getenv("ASTER_PREKEY_FIXTURE_PASSPHRASE") ?: return
        val out_path = System.getenv("ASTER_PREKEY_FIXTURE_OUT") ?: return

        val armored_secret = File(key_path).readText()
        val text = PrekeyBindingSigner.canonical_binding(kem_b64, spk_b64)

        val armored = PrekeyBindingSigner.sign_cleartext(
            armored_secret_key = armored_secret,
            passphrase = fixture_passphrase.toCharArray(),
            text = text,
        )

        val ring = PGPSecretKeyRing(
            PGPUtil.getDecoderStream(armored_secret.byteInputStream(Charsets.UTF_8)),
            BcKeyFingerprintCalculator(),
        )
        val (valid, signed_text) = verify_cleartext(armored, ring.secretKey.publicKey)
        assertTrue(valid)
        assertEquals(text, signed_text)

        File(out_path).writeText(armored)

        val public_out = ByteArrayOutputStream()
        ArmoredOutputStream(public_out).use { ring.secretKey.publicKey.encode(it) }
        File("$out_path.pub").writeText(public_out.toString(Charsets.UTF_8.name()))
        File("$out_path.txt").writeText(text)
    }

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

    private fun verify_cleartext(armored: String, public_key: PGPPublicKey): Pair<Boolean, String> {
        val armor_in = ArmoredInputStream(armored.byteInputStream(Charsets.UTF_8))
        val text_out = ByteArrayOutputStream()
        var ch = armor_in.read()
        while (ch >= 0 && armor_in.isClearText) {
            text_out.write(ch)
            ch = armor_in.read()
        }
        val signed_text = text_out.toString(Charsets.UTF_8.name()).trimEnd('\r', '\n')

        val factory = PGPObjectFactory(armor_in, BcKeyFingerprintCalculator())
        val signatures = factory.nextObject() as PGPSignatureList
        val signature = signatures.get(0)
        signature.init(BcPGPContentVerifierBuilderProvider(), public_key)
        signature.update(signed_text.toByteArray(Charsets.UTF_8))
        return signature.verify() to signed_text
    }

    private fun write_interop_vector(secret_key: PGPSecretKey, text: String, armored: String) {
        val dir = File("build/prekey_binding_interop")
        dir.mkdirs()

        val public_out = ByteArrayOutputStream()
        ArmoredOutputStream(public_out).use { secret_key.publicKey.encode(it) }

        File(dir, "public_key.asc").writeText(public_out.toString(Charsets.UTF_8.name()))
        File(dir, "signature.asc").writeText(armored)
        File(dir, "text.txt").writeText(text)
    }
}
