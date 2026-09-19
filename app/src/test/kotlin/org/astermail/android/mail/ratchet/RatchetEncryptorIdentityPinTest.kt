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

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.keys.PublicKeyResponse
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
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetEncryptorIdentityPinTest {

    private val sender_email = "hello@astermail.org"
    private val recipient_email = "kchaos@aster.cx"

    private lateinit var state_store: RatchetStateStore
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var ratchet_api: RatchetApi
    private lateinit var syncer: RatchetStateSyncer
    private lateinit var keys_api: KeysApi
    private lateinit var identity_pins: RatchetIdentityPinStore

    private lateinit var recipient_identity_b64: String
    private lateinit var bundle: PrekeyBundleResponse
    private lateinit var conversation_id: String

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }
        every { Base64.decode(any<String>(), any()) } answers {
            val value = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                var padded = value
                val mod = padded.length % 4
                if (mod != 0) padded += "=".repeat(4 - mod)
                java.util.Base64.getUrlDecoder().decode(padded)
            } else {
                java.util.Base64.getDecoder().decode(value)
            }
        }

        val sender_identity_kp = RatchetCrypto.generate_p256_keypair()
        val recipient_identity_kp = RatchetCrypto.generate_p256_keypair()
        val recipient_spk_kp = RatchetCrypto.generate_p256_keypair()

        recipient_identity_b64 = RatchetCrypto.b64_encode(recipient_identity_kp.public_raw)
        conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)

        bundle = PrekeyBundleResponse(
            user_id = "u_1",
            kem_identity_key = recipient_identity_b64,
            signed_prekey = RatchetCrypto.b64_encode(recipient_spk_kp.public_raw),
            signed_prekey_signature = "",
        )

        state_store = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        ratchet_api = mockk(relaxed = true)
        syncer = mockk(relaxed = true)
        keys_api = mockk(relaxed = true)
        identity_pins = mockk(relaxed = true)

        coEvery { state_store.load(any()) } returns null
        every { session_key_store.get_ratchet_identity_public_b64() } returns
            RatchetCrypto.b64_encode(sender_identity_kp.public_raw)
        every { session_key_store.get_ratchet_identity_jwk() } returns to_private_jwk(sender_identity_kp)
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns bundle
        every { identity_pins.is_prekey_binding_verified(any()) } returns false
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun to_private_jwk(kp: RatchetCrypto.EcKeyPair): String {
        val d = RatchetCrypto.private_to_raw_d(kp.private_key)
        val x = kp.public_raw.copyOfRange(1, 33)
        val y = kp.public_raw.copyOfRange(33, 65)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${RatchetCrypto.b64url_encode(x)}\",\"y\":\"${RatchetCrypto.b64url_encode(y)}\",\"d\":\"${RatchetCrypto.b64url_encode(d)}\"}"
    }

    private fun new_encryptor(): RatchetEncryptor = RatchetEncryptor(
        state_store,
        session_key_store,
        ratchet_api,
        syncer,
        ConversationLocks(),
        keys_api,
        identity_pins,
    )

    @Test
    fun `swapped prekey bundle identity blocks the send`() = runTest {
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.CHANGED

        var thrown: Throwable? = null
        try {
            new_encryptor().encrypt_envelope(
                sender_email,
                listOf(recipient_email),
                "hello",
                allow_non_post_quantum = true,
            )
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue(thrown is RatchetIdentityPinException)
        assertEquals(recipient_email, (thrown as RatchetIdentityPinException).recipient)
        coVerify(exactly = 0) { state_store.save(any()) }
        coVerify(exactly = 1) {
            identity_pins.flag_identity_change(conversation_id, recipient_email, recipient_identity_b64, any())
        }
    }

    @Test
    fun `first contact bundle is pinned and sent`() = runTest {
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.FIRST_CONTACT

        val envelope = new_encryptor().encrypt_envelope(
            sender_email,
            listOf(recipient_email),
            "hello",
            allow_non_post_quantum = true,
        )

        assertNotNull(envelope)
        coVerify(exactly = 1) { identity_pins.pin_if_absent(conversation_id, recipient_identity_b64) }
    }

    @Test
    fun `pinned identity that still matches is sent`() = runTest {
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.UNCHANGED

        val envelope = new_encryptor().encrypt_envelope(
            sender_email,
            listOf(recipient_email),
            "hello",
            allow_non_post_quantum = true,
        )

        assertNotNull(envelope)
        coVerify(exactly = 0) { identity_pins.flag_identity_change(any(), any(), any(), any()) }
    }

    private val pgp_passphrase = "prekey-binding-fixture"

    private fun generate_pgp_key(): PGPSecretKey {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(java.security.SecureRandom()))
        val key_pair = BcPGPKeyPair(
            PublicKeyAlgorithmTags.EDDSA_LEGACY,
            generator.generateKeyPair(),
            java.util.Date(),
        )
        val digests = BcPGPDigestCalculatorProvider()
        val subpackets = PGPSignatureSubpacketGenerator()
        subpackets.setKeyFlags(false, KeyFlags.CERTIFY_OTHER or KeyFlags.SIGN_DATA)
        return PGPSecretKey(
            PGPSignature.DEFAULT_CERTIFICATION,
            key_pair,
            "Fixture <fixture@astermail.org>",
            digests.get(HashAlgorithmTags.SHA1),
            subpackets.generate(),
            null,
            BcPGPContentSignerBuilder(key_pair.publicKey.algorithm, HashAlgorithmTags.SHA256),
            BcPBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, digests.get(HashAlgorithmTags.SHA256))
                .build(pgp_passphrase.toCharArray()),
        )
    }

    private fun armor(write: (ArmoredOutputStream) -> Unit): String {
        val out = java.io.ByteArrayOutputStream()
        ArmoredOutputStream(out).use(write)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun sign_binding(key: PGPSecretKey, ik: String, spk: String): String {
        val armored = PrekeyBindingSigner.sign_cleartext(
            armored_secret_key = armor { key.encode(it) },
            passphrase = pgp_passphrase.toCharArray(),
            text = PrekeyBindingSigner.canonical_binding(ik, spk),
        )
        return java.util.Base64.getEncoder().encodeToString(armored.toByteArray(Charsets.UTF_8))
    }

    private fun public_armor(key: PGPSecretKey): String = armor { key.publicKey.encode(it) }

    private fun serve_bundle(signature: String) {
        bundle = bundle.copy(signed_prekey_signature = signature)
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns bundle
        every { identity_pins.evaluate(conversation_id, recipient_identity_b64) } returns
            IdentityPinOutcome.UNCHANGED
    }

    private suspend fun send(): Result<String?> = runCatching {
        new_encryptor().encrypt_envelope(
            sender_email,
            listOf(recipient_email),
            "hello",
            allow_non_post_quantum = true,
        )
    }

    @Test
    fun `valid prekey signature is verified and sent`() = runTest {
        val key = generate_pgp_key()
        serve_bundle(sign_binding(key, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(key))

        assertNotNull(send().getOrThrow())
        coVerify(exactly = 1) { identity_pins.record_prekey_binding_verified(recipient_email) }
    }

    @Test
    fun `prekey signature over a different signed prekey blocks the send`() = runTest {
        val key = generate_pgp_key()
        val other_spk = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        serve_bundle(sign_binding(key, bundle.kem_identity_key, other_spk))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(key))

        val thrown = send().exceptionOrNull()
        assertTrue(thrown is RatchetEncryptionException)
        coVerify(exactly = 0) { state_store.save(any()) }
    }

    @Test
    fun `signed bundle is still sent when the owner key cannot be fetched`() = runTest {
        val key = generate_pgp_key()
        serve_bundle(sign_binding(key, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } throws RuntimeException("offline")

        assertNotNull(send().getOrThrow())
    }

    @Test
    fun `signature from a key the recipient does not publish is still sent`() = runTest {
        val signer = generate_pgp_key()
        val published = generate_pgp_key()
        serve_bundle(sign_binding(signer, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(published))

        assertNotNull(send().getOrThrow())
    }

    @Test
    fun `unsigned bundle is still sent after a verified one was seen`() = runTest {
        serve_bundle("")
        every { identity_pins.is_prekey_binding_verified(any()) } returns true

        assertNotNull(send().getOrThrow())
    }
}
