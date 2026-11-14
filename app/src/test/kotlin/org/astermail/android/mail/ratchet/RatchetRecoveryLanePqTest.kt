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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.keys.PublicKeyResponse
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RatchetRecoveryLanePqTest {

    private val sender_email = "hello@astermail.org"
    private val recipient_email = "kchaos@aster.cx"
    private val pgp_passphrase = "recovery-lane-fixture"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var state_store: RatchetStateStore
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var ratchet_api: RatchetApi
    private lateinit var syncer: RatchetStateSyncer
    private lateinit var keys_api: KeysApi
    private lateinit var identity_pins: RatchetIdentityPinStore

    private val states = mutableMapOf<String, RatchetState>()
    private lateinit var conversation_id: String
    private lateinit var identity_b64: String
    private lateinit var spk_b64: String
    private lateinit var pq_identity_b64: String
    private lateinit var owner_key: PGPSecretKey

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            val bytes = firstArg<ByteArray>()
            if (secondArg<Int>() and Base64.URL_SAFE != 0) {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            } else {
                java.util.Base64.getEncoder().encodeToString(bytes)
            }
        }
        every { Base64.decode(any<String>(), any()) } answers {
            val value = firstArg<String>()
            if (secondArg<Int>() and Base64.URL_SAFE != 0) {
                val mod = value.length % 4
                java.util.Base64.getUrlDecoder().decode(if (mod == 0) value else value + "=".repeat(4 - mod))
            } else {
                java.util.Base64.getDecoder().decode(value)
            }
        }

        val sender_identity = RatchetCrypto.generate_p256_keypair()
        identity_b64 = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        spk_b64 = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        pq_identity_b64 = RatchetCrypto.b64_encode(RatchetCrypto.ml_kem_768_generate_keypair().public_key)
        conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)
        owner_key = generate_pgp_key()

        state_store = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        ratchet_api = mockk(relaxed = true)
        syncer = mockk(relaxed = true)
        keys_api = mockk(relaxed = true)
        identity_pins = mockk(relaxed = true)

        coEvery { state_store.load(any()) } answers { states[firstArg()] }
        coEvery { state_store.save(any()) } answers {
            val state = firstArg<RatchetState>()
            states[state.conversation_id] = state
        }
        every { session_key_store.get_ratchet_identity_public_b64() } returns
            RatchetCrypto.b64_encode(sender_identity.public_raw)
        every { session_key_store.get_ratchet_identity_jwk() } returns to_private_jwk(sender_identity)
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse("kchaos", armor { owner_key.publicKey.encode(it) })
        every { identity_pins.evaluate(conversation_id, identity_b64) } returns IdentityPinOutcome.UNCHANGED
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

    private fun generate_pgp_key(): PGPSecretKey {
        val generator = Ed25519KeyPairGenerator()
        generator.init(Ed25519KeyGenerationParameters(java.security.SecureRandom()))
        val key_pair = BcPGPKeyPair(PublicKeyAlgorithmTags.EDDSA_LEGACY, generator.generateKeyPair(), java.util.Date())
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

    private fun serve_bundle(post_quantum: Boolean) {
        val text = if (post_quantum) {
            PrekeyBindingSigner.canonical_binding_v2(identity_b64, spk_b64, pq_identity_b64)
        } else {
            PrekeyBindingSigner.canonical_binding(identity_b64, spk_b64)
        }
        val armored = PrekeyBindingSigner.sign_cleartext(
            armored_secret_key = armor { owner_key.encode(it) },
            passphrase = pgp_passphrase.toCharArray(),
            text = text,
        )
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns PrekeyBundleResponse(
            user_id = "u_1",
            kem_identity_key = identity_b64,
            signed_prekey = spk_b64,
            signed_prekey_signature = java.util.Base64.getEncoder().encodeToString(armored.toByteArray(Charsets.UTF_8)),
            pq_kem_public_key = if (post_quantum) pq_identity_b64 else null,
        )
    }

    private fun account_only_directory(): AddressIdentityDirectory {
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } coAnswers {
            ratchet_api.fetch_prekey_bundle(firstArg(), secondArg())
        }
        return AddressIdentityDirectory(session_key_store, ratchet_api, identity_pins)
    }

    private suspend fun send(): RatchetRecipientData {
        val envelope = RatchetEncryptor(
            state_store,
            session_key_store,
            ratchet_api,
            syncer,
            ConversationLocks(),
            keys_api,
            identity_pins,
            account_only_directory(),
        ).encrypt_envelope(sender_email, listOf(recipient_email), "hello", allow_non_post_quantum = true)
        return json.decodeFromString(RatchetEnvelope.serializer(), envelope!!).recipients.getValue(recipient_email)
    }

    private fun claims_post_quantum_with_classical_lane(data: RatchetRecipientData): Boolean =
        data.pq_ciphertext != null && data.recovery?.kem_ct == null

    @Test
    fun `first post-quantum message seals a post-quantum recovery lane`() = runTest {
        serve_bundle(post_quantum = true)

        val first = send()

        assertNotNull(first.pq_ciphertext)
        assertNotNull(first.recovery?.kem_ct)
    }

    @Test
    fun `later bundle without the pq key keeps the post-quantum lane`() = runTest {
        serve_bundle(post_quantum = true)
        send()
        serve_bundle(post_quantum = false)

        val second = send()

        assertNotNull(second.pq_ciphertext)
        assertNotNull(second.recovery?.kem_ct)
    }

    @Test
    fun `session that lost its pq key never pairs a pq body with a classical lane`() = runTest {
        serve_bundle(post_quantum = true)
        send()
        val state = states.getValue(conversation_id)
        state.bootstrap = state.bootstrap!!.copy(recipient_pq_identity_key = null)
        serve_bundle(post_quantum = false)

        val second = runCatching { send() }

        second.onSuccess { assertFalse(claims_post_quantum_with_classical_lane(it)) }
    }

    @Test
    fun `classical session restarts as post-quantum once the recipient advertises a pq key`() = runTest {
        serve_bundle(post_quantum = false)
        val first = send()
        assertNull(first.pq_ciphertext)
        serve_bundle(post_quantum = true)

        val second = send()
        val third = send()

        assertNotNull(second.pq_ciphertext)
        assertNotNull(second.recovery?.kem_ct)
        assertNotEquals(first.ephemeral_key, second.ephemeral_key)
        assertEquals(second.ephemeral_key, third.ephemeral_key)
        assertFalse(claims_post_quantum_with_classical_lane(third))
    }
}
