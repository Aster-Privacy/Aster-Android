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

import android.content.SharedPreferences
import android.util.Base64
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.keys.PublicKeyResponse
import org.astermail.android.api.ratchet.AddressBundleUpload
import org.astermail.android.api.ratchet.PqPrekeyInfo
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs
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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OneTimePqReplyTest {

    private val me = "reader@astermail.org"
    private val replier = "replier@astermail.org"
    private val onetime_key_id = 77
    private val local_states = mutableMapOf<String, RatchetState>()
    private val server_states = mutableMapOf<String, RatchetState>()
    private val pin_values = mutableMapOf<String, Any?>()
    private val pq_secrets = mutableMapOf<Int, ByteArray>()

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var ratchet_api: RatchetApi
    private lateinit var encryptor: RatchetEncryptor
    private lateinit var decryptor: RatchetDecryptor

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
            val s = firstArg<String>()
            if (secondArg<Int>() and Base64.URL_SAFE != 0) {
                var padded = s
                val mod = padded.length % 4
                if (mod != 0) padded += "=".repeat(4 - mod)
                java.util.Base64.getUrlDecoder().decode(padded)
            } else {
                java.util.Base64.getDecoder().decode(s)
            }
        }
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns fake_prefs()
        build()
    }

    @After
    fun teardown() {
        unmockkObject(SecurePrefs)
        unmockkStatic(Base64::class)
    }

    private fun build() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        session_key_store = spyk(SessionKeyStore(null))
        every { session_key_store.get_pq_secret(any()) } answers { pq_secrets[firstArg()]?.copyOf() }
        every { session_key_store.put_pq_secret(any(), any()) } answers {
            pq_secrets[firstArg()] = secondArg<ByteArray>().copyOf()
        }
        every { session_key_store.remove_pq_secret(any()) } answers { pq_secrets.remove(firstArg<Int>()) }
        session_key_store.put_ratchet_keys(
            to_private_jwk(identity),
            RatchetCrypto.b64_encode(identity.public_raw),
            to_private_jwk(spk),
            RatchetCrypto.b64_encode(spk.public_raw),
        )
        val onetime = RatchetCrypto.ml_kem_768_generate_keypair()
        pq_secrets[onetime_key_id] = onetime.secret_key.copyOf()

        val ik_b64 = RatchetCrypto.b64_encode(identity.public_raw)
        val spk_b64 = RatchetCrypto.b64_encode(spk.public_raw)
        val pgp = generate_pgp_key()
        val signature = java.util.Base64.getEncoder().encodeToString(
            PrekeyBindingSigner.sign_cleartext(
                armored_secret_key = armor { pgp.encode(it) },
                passphrase = pgp_passphrase.toCharArray(),
                text = PrekeyBindingSigner.canonical_binding(ik_b64, spk_b64),
            ).toByteArray(Charsets.UTF_8),
        )
        val keys_api = mockk<KeysApi>(relaxed = true)
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            PublicKeyResponse(username = "reader", public_key = armor { pgp.publicKey.encode(it) })
        val bundle = PrekeyBundleResponse(
            user_id = "u_reader",
            kem_identity_key = ik_b64,
            signed_prekey = spk_b64,
            signed_prekey_signature = signature,
            pq_prekey = PqPrekeyInfo(onetime_key_id, RatchetCrypto.b64_encode(onetime.public_key)),
            pq_kem_public_key = RatchetCrypto.b64_encode(RatchetCrypto.ml_kem_768_generate_keypair().public_key),
            x3dh_max_version = null,
        )
        ratchet_api = mockk(relaxed = true)
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns bundle
        coEvery { ratchet_api.fetch_address_prekey_bundle(any(), any()) } returns bundle
        coEvery { ratchet_api.upload_address_bundle(any()) } returns AddressBundleUpload.Unsupported
        coEvery { ratchet_api.fetch_pq_secret(any()) } returns null

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } answers { local_states[firstArg()]?.let { copy(it) } }
        coEvery { state_store.save(any()) } answers {
            val s = firstArg<RatchetState>()
            local_states[s.conversation_id] = copy(s)
        }
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.sync(any(), any()) } answers {
            server_states[firstArg()] = copy(secondArg())
            true
        }
        coEvery { syncer.fetch_from_server(any()) } answers { server_states[firstArg()]?.let { copy(it) } }

        val pins = RatchetIdentityPinStore(mockk(relaxed = true), session_key_store)
        val locks = ConversationLocks()
        val address_directory = AddressIdentityDirectory(session_key_store, ratchet_api, pins)

        val sender_identity = RatchetCrypto.generate_p256_keypair()
        val sender_spk = RatchetCrypto.generate_p256_keypair()
        val sender_keys = SessionKeyStore(null)
        sender_keys.put_ratchet_keys(
            to_private_jwk(sender_identity),
            RatchetCrypto.b64_encode(sender_identity.public_raw),
            to_private_jwk(sender_spk),
            RatchetCrypto.b64_encode(sender_spk.public_raw),
        )
        val sender_pins = RatchetIdentityPinStore(mockk(relaxed = true), sender_keys)
        encryptor = RatchetEncryptor(
            mockk<RatchetStateStore>(relaxed = true),
            sender_keys,
            ratchet_api,
            mockk<RatchetStateSyncer>(relaxed = true),
            ConversationLocks(),
            keys_api,
            sender_pins,
            AddressIdentityDirectory(sender_keys, ratchet_api, sender_pins),
            mockk<RatchetSenderKeyHeal>(relaxed = true),
        )
        val escrow = mockk<MessageEscrow>(relaxed = true)
        coEvery { escrow.fetch(any()) } returns null
        coEvery { escrow.fetch_bound(any(), any(), any()) } returns null
        decryptor = RatchetDecryptor(
            state_store,
            session_key_store,
            ratchet_api,
            syncer,
            locks,
            dagger.Lazy { mockk<AuthRepository>(relaxed = true) },
            pins,
            mockk(relaxed = true),
            escrow,
            address_directory,
            keys_api,
            MemoryLaneReplayVault(),
        )
    }

    private suspend fun one_time_reply(body: String): String {
        val sealed = JSONObject(encryptor.encrypt_envelope(replier, listOf(me), body, allow_non_post_quantum = true)!!)
        val recipient = sealed.getJSONObject("recipients").getJSONObject(me)
        recipient.remove("x3dh_v")
        recipient.remove("recovery")
        local_states.clear()
        server_states.clear()
        return sealed.toString()
    }

    private fun sealed_for_me(envelope: String): JSONObject =
        JSONObject(envelope).getJSONObject("recipients").getJSONObject(me)

    private suspend fun open(envelope: String, id: String): String =
        decryptor.try_decrypt(envelope, listOf(me), replier, id)

    @Test
    fun `a reply bootstrapped on a one-time post-quantum prekey opens`() = runTest {
        val reply = one_time_reply("thanks, see you then")
        val recipient = sealed_for_me(reply)
        assertEquals(onetime_key_id, recipient.getInt("pq_key_id"))
        assertTrue(recipient.has("pq_ciphertext"))
        assertTrue(!recipient.has("x3dh_v"))
        assertTrue(!recipient.has("recovery"))
        assertEquals("thanks, see you then", open(reply, "ios-reply-1"))
        assertNull(pq_secrets[onetime_key_id])
    }

    @Test
    fun `a canceled first open keeps the one-time secret so the retry opens`() = runTest {
        val reply = one_time_reply("thanks, see you then")
        val reached = CompletableDeferred<Unit>()
        coEvery { ratchet_api.fetch_own_ratchet_identity(any(), any()) } coAnswers {
            reached.complete(Unit)
            awaitCancellation()
        }
        val first = launch { runCatching { open(reply, "ios-reply-2") } }
        reached.await()
        first.cancel()
        first.join()
        assertNotNull(pq_secrets[onetime_key_id])

        coEvery { ratchet_api.fetch_own_ratchet_identity(any(), any()) } throws java.io.IOException("offline")
        assertEquals("thanks, see you then", open(reply, "ios-reply-2"))
        assertNull(pq_secrets[onetime_key_id])
    }

    @Test
    fun `a network error fetching the one-time secret does not block the next try`() = runTest {
        val reply = one_time_reply("thanks, see you then")
        val secret = pq_secrets.remove(onetime_key_id)!!
        var server_asks = 0
        coEvery { ratchet_api.fetch_pq_secret(onetime_key_id) } answers {
            server_asks += 1
            throw java.io.IOException("offline")
        }
        assertEquals(RATCHET_UNDECRYPTABLE_SENTINEL, runCatching { open(reply, "ios-reply-3") }.getOrDefault(RATCHET_UNDECRYPTABLE_SENTINEL))
        val after_first = server_asks
        assertTrue(after_first > 0)

        assertEquals(RATCHET_UNDECRYPTABLE_SENTINEL, runCatching { open(reply, "ios-reply-3") }.getOrDefault(RATCHET_UNDECRYPTABLE_SENTINEL))
        assertTrue(server_asks > after_first)

        pq_secrets[onetime_key_id] = secret
        assertEquals("thanks, see you then", open(reply, "ios-reply-3"))
    }

    private val pgp_passphrase = "one-time-reply-fixture"

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
            "Fixture <reader@astermail.org>",
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

    private fun copy(state: RatchetState): RatchetState = state.copy(
        dh_keypair = state.dh_keypair.copy(),
        skipped_message_keys = state.skipped_message_keys.toMutableList(),
    )

    private fun to_private_jwk(kp: RatchetCrypto.EcKeyPair): String {
        val d = RatchetCrypto.private_to_raw_d(kp.private_key)
        val x = kp.public_raw.copyOfRange(1, 33)
        val y = kp.public_raw.copyOfRange(33, 65)
        return "{\"kty\":\"EC\",\"crv\":\"P-256\",\"x\":\"${RatchetCrypto.b64url_encode(x)}\",\"y\":\"${RatchetCrypto.b64url_encode(y)}\",\"d\":\"${RatchetCrypto.b64url_encode(d)}\"}"
    }

    private fun fake_prefs(): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers { pin_values[firstArg()] = secondArg<String?>(); editor }
        every { editor.putBoolean(any(), any()) } answers { pin_values[firstArg()] = secondArg<Boolean>(); editor }
        every { editor.putInt(any(), any()) } answers { pin_values[firstArg()] = secondArg<Int>(); editor }
        every { editor.putLong(any(), any()) } answers { pin_values[firstArg()] = secondArg<Long>(); editor }
        every { editor.remove(any()) } answers { pin_values.remove(firstArg<String>()); editor }
        every { editor.clear() } answers { pin_values.clear(); editor }
        every { editor.commit() } returns true
        every { editor.apply() } returns Unit
        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers { pin_values[firstArg()] as? String ?: secondArg<String?>() }
        every { prefs.getBoolean(any(), any()) } answers { pin_values[firstArg()] as? Boolean ?: secondArg<Boolean>() }
        every { prefs.getInt(any(), any()) } answers { pin_values[firstArg()] as? Int ?: secondArg<Int>() }
        every { prefs.getLong(any(), any()) } answers { pin_values[firstArg()] as? Long ?: secondArg<Long>() }
        every { prefs.contains(any()) } answers { pin_values.containsKey(firstArg<String>()) }
        every { prefs.all } answers { pin_values.toMap() }
        return prefs
    }
}
