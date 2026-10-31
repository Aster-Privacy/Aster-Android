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
import org.astermail.android.crypto.PgpDecryptor
import org.astermail.android.crypto.PgpEncryptor
import org.astermail.android.crypto.PgpKeyGenerator
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetEncryptorStrictBundleTest {

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
        every { identity_pins.owner_key_pin(any()) } returns null
        coEvery { identity_pins.pin_owner_key_if_absent(any(), any()) } returns true
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

    private fun new_encryptor(strict: Boolean = true): RatchetEncryptor = RatchetEncryptor(
        state_store,
        session_key_store,
        ratchet_api,
        syncer,
        ConversationLocks(),
        keys_api,
        identity_pins,
    ).also { it.require_verified_prekey_bundle = strict }

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

    private suspend fun send(strict: Boolean = true): Result<String?> = runCatching {
        new_encryptor(strict).encrypt_envelope(
            sender_email,
            listOf(recipient_email),
            "hello",
            allow_non_post_quantum = true,
        )
    }

    private fun pin_owner(key: PGPSecretKey) {
        every { identity_pins.owner_key_pin(recipient_email) } returns
            PrekeyBindingVerifier.owner_fingerprint(public_armor(key))
    }


    private val fallback_passphrase = "pgp-fallback-fixture"

    private fun is_ratchet_envelope(sealed: String?): Boolean =
        sealed != null && sealed.contains("double_ratchet_v2")

    @Test
    fun `encryptor follows the policy switch by default`() {
        val encryptor = RatchetEncryptor(
            state_store,
            session_key_store,
            ratchet_api,
            syncer,
            ConversationLocks(),
            keys_api,
            identity_pins,
        )
        assertEquals(PrekeyBundlePolicy.require_verified_prekey_bundle, encryptor.require_verified_prekey_bundle)
    }

    @Test
    fun `verified bundle uses the ratchet in strict mode`() = runTest {
        val key = generate_pgp_key()
        serve_bundle(sign_binding(key, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(key))

        assertTrue(is_ratchet_envelope(send().getOrThrow()))
        coVerify(exactly = 1) { identity_pins.record_prekey_binding_verified(recipient_email) }
    }

    @Test
    fun `unsigned bundle is refused in strict mode before any state changes`() = runTest {
        serve_bundle("")

        val thrown = send().exceptionOrNull()
        assertTrue(thrown is UnverifiedPrekeyBundleException)
        assertEquals(recipient_email, (thrown as UnverifiedPrekeyBundleException).recipient)
        coVerify(exactly = 0) { state_store.save(any()) }
        coVerify(exactly = 0) { identity_pins.pin_if_absent(any(), any()) }
        coVerify(exactly = 0) { identity_pins.pin_owner_key_if_absent(any(), any()) }
    }

    @Test
    fun `bundle signed by a key the recipient does not publish is refused in strict mode`() = runTest {
        val signer = generate_pgp_key()
        val published = generate_pgp_key()
        serve_bundle(sign_binding(signer, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(published))

        assertTrue(send().exceptionOrNull() is UnverifiedPrekeyBundleException)
        coVerify(exactly = 0) { state_store.save(any()) }
    }

    @Test
    fun `signed bundle whose owner key cannot be fetched is refused in strict mode`() = runTest {
        val key = generate_pgp_key()
        serve_bundle(sign_binding(key, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } throws RuntimeException("offline")

        assertTrue(send().exceptionOrNull() is UnverifiedPrekeyBundleException)
    }

    @Test
    fun `unsigned bundle for a pinned owner whose key still matches falls back in strict mode`() = runTest {
        val key = generate_pgp_key()
        pin_owner(key)
        serve_bundle("")
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(key))

        assertTrue(send().exceptionOrNull() is UnverifiedPrekeyBundleException)
        coVerify(exactly = 0) { identity_pins.flag_owner_key_change(any(), any(), any()) }
    }

    @Test
    fun `swapped owner key is still refused in strict mode`() = runTest {
        val pinned = generate_pgp_key()
        val swapped = generate_pgp_key()
        pin_owner(pinned)
        serve_bundle(sign_binding(swapped, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(swapped))

        val thrown = send().exceptionOrNull()
        assertTrue(thrown is RecipientKeyUntrustedException)
        coVerify(exactly = 1) { identity_pins.flag_owner_key_change(recipient_email, any(), any()) }
    }

    @Test
    fun `signature over a different prekey is still refused in strict mode`() = runTest {
        val key = generate_pgp_key()
        val other_spk = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        serve_bundle(sign_binding(key, bundle.kem_identity_key, other_spk))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(key))

        assertTrue(send().exceptionOrNull() is RecipientKeyUntrustedException)
    }

    @Test
    fun `switch off keeps sending unsigned bundles with the ratchet`() = runTest {
        serve_bundle("")

        assertTrue(is_ratchet_envelope(send(strict = false).getOrThrow()))
    }

    @Test
    fun `switch off keeps sending bundles signed by an unpublished key`() = runTest {
        val signer = generate_pgp_key()
        val published = generate_pgp_key()
        serve_bundle(sign_binding(signer, bundle.kem_identity_key, bundle.signed_prekey))
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(published))

        assertTrue(is_ratchet_envelope(send(strict = false).getOrThrow()))
    }

    @Test
    fun `switch off keeps refusing unsigned bundles for a pinned owner`() = runTest {
        val key = generate_pgp_key()
        pin_owner(key)
        serve_bundle("")
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", public_armor(key))

        assertTrue(send(strict = false).exceptionOrNull() is RecipientKeyUntrustedException)
    }

    @Test
    fun `strict send to an unsigned bundle is sealed with the recipient pgp key`() = runTest {
        serve_bundle("")
        val recipient_key = PgpKeyGenerator.generate("Recipient", recipient_email, fallback_passphrase.toCharArray())
        val sender_key = PgpKeyGenerator.generate("Sender", sender_email, fallback_passphrase.toCharArray())
        val encryptor = new_encryptor()

        val sealed = seal_internal_body(
            recipients = listOf(recipient_email),
            allow_non_post_quantum = true,
            seal_ratchet = {
                encryptor.encrypt_envelope(sender_email, listOf(recipient_email), "wrapped body", allow_non_post_quantum = true)
            },
            seal_pgp = {
                seal_pgp_for_recipients(
                    recipients = listOf(recipient_email),
                    own_public_key = sender_key.armored_public_key,
                    fetch_public_key = { recipient_key.armored_public_key },
                    is_owner_key_trusted = encryptor::owner_key_matches_pin,
                    can_encrypt_to = PgpEncryptor::can_encrypt_to,
                    encrypt_and_sign = { keys ->
                        PgpEncryptor.encrypt_and_sign(
                            "wrapped body",
                            keys,
                            sender_key.armored_private_key,
                            fallback_passphrase.toCharArray(),
                        )
                    },
                )
            },
        )

        assertNotNull(sealed)
        assertTrue(sealed!!.startsWith("-----BEGIN PGP"))
        assertEquals(
            "wrapped body",
            PgpDecryptor.decrypt(sealed, recipient_key.armored_private_key, fallback_passphrase.toCharArray()),
        )
        coVerify(exactly = 0) { state_store.save(any()) }
    }

    @Test
    fun `strict send with no recipient key fails with a missing key error`() = runTest {
        serve_bundle("")
        val sender_key = PgpKeyGenerator.generate("Sender", sender_email, fallback_passphrase.toCharArray())
        val encryptor = new_encryptor()

        val thrown = runCatching {
            seal_internal_body(
                recipients = listOf(recipient_email),
                allow_non_post_quantum = true,
                seal_ratchet = {
                    encryptor.encrypt_envelope(sender_email, listOf(recipient_email), "wrapped body", allow_non_post_quantum = true)
                },
                seal_pgp = {
                    seal_pgp_for_recipients(
                        recipients = listOf(recipient_email),
                        own_public_key = sender_key.armored_public_key,
                        fetch_public_key = { null },
                        is_owner_key_trusted = encryptor::owner_key_matches_pin,
                        can_encrypt_to = PgpEncryptor::can_encrypt_to,
                        encrypt_and_sign = { "unused" },
                    )
                },
            )
        }.exceptionOrNull()

        assertTrue(thrown is RecipientPublicKeyMissingException)
    }

    @Test
    fun `pgp fallback refuses a key that does not match the pinned owner`() {
        val pinned = PgpKeyGenerator.generate("Recipient", recipient_email, fallback_passphrase.toCharArray())
        val other = PgpKeyGenerator.generate("Other", recipient_email, fallback_passphrase.toCharArray())
        every { identity_pins.owner_key_pin(recipient_email) } returns
            PrekeyBindingVerifier.owner_fingerprint(pinned.armored_public_key)
        val encryptor = new_encryptor()

        assertTrue(encryptor.owner_key_matches_pin(recipient_email, pinned.armored_public_key))
        assertTrue(!encryptor.owner_key_matches_pin(recipient_email, other.armored_public_key))
    }
}
