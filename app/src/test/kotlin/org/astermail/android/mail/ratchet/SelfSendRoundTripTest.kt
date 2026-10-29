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
import io.mockk.unmockkObject
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
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.json.JSONObject
import org.junit.Test

class SelfSendRoundTripTest {

    private val me = "newuser@astermail.org"
    private val local_states = mutableMapOf<String, RatchetState>()
    private val server_states = mutableMapOf<String, RatchetState>()
    private val pin_values = mutableMapOf<String, Any?>()

    private lateinit var session_key_store: SessionKeyStore
    private lateinit var ratchet_api: RatchetApi
    private lateinit var encryptor: RatchetEncryptor
    private lateinit var decryptor: RatchetDecryptor
    private var pq_mode = false

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
    }

    private fun build(signed_pq: Boolean) {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        session_key_store = SessionKeyStore(null)
        session_key_store.put_ratchet_keys(
            to_private_jwk(identity),
            RatchetCrypto.b64_encode(identity.public_raw),
            to_private_jwk(spk),
            RatchetCrypto.b64_encode(spk.public_raw),
        )

        val ik_b64 = RatchetCrypto.b64_encode(identity.public_raw)
        val spk_b64 = RatchetCrypto.b64_encode(spk.public_raw)
        val keys_api = mockk<KeysApi>(relaxed = true)
        var pq_public_b64: String? = null
        var signature = ""
        if (signed_pq) {
            val pq = RatchetCrypto.ml_kem_768_generate_keypair()
            pq_public_b64 = RatchetCrypto.b64_encode(pq.public_key)
            session_key_store.put_ratchet_pq_identity(RatchetCrypto.b64_encode(pq.secret_key), pq_public_b64)
            val pgp = generate_pgp_key()
            val armored = PrekeyBindingSigner.sign_cleartext(
                armored_secret_key = armor { pgp.encode(it) },
                passphrase = pgp_passphrase.toCharArray(),
                text = PrekeyBindingSigner.canonical_binding_v2(ik_b64, spk_b64, pq_public_b64),
            )
            signature = java.util.Base64.getEncoder().encodeToString(armored.toByteArray(Charsets.UTF_8))
            coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
                PublicKeyResponse(username = "newuser", public_key = armor { pgp.publicKey.encode(it) })
        }
        ratchet_api = mockk(relaxed = true)
        coEvery { ratchet_api.fetch_prekey_bundle(any(), any()) } returns PrekeyBundleResponse(
            user_id = "u_me",
            kem_identity_key = ik_b64,
            signed_prekey = spk_b64,
            signed_prekey_signature = signature,
            pq_kem_public_key = pq_public_b64,
            x3dh_max_version = if (signed_pq) X3dh.VERSION_TRANSCRIPT_BOUND else null,
        )

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
        encryptor = RatchetEncryptor(
            state_store,
            session_key_store,
            ratchet_api,
            syncer,
            locks,
            keys_api,
            pins,
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
        )
    }

    @After
    fun teardown() {
        unmockkObject(SecurePrefs)
        unmockkStatic(Base64::class)
    }

    private val pgp_passphrase = "self-send-fixture"

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
            "Fixture <newuser@astermail.org>",
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

    private suspend fun send(body: String): String =
        encryptor.encrypt_envelope(me, listOf(me), body, allow_non_post_quantum = !pq_mode)!!

    private suspend fun open(envelope: String, id: String): String =
        decryptor.try_decrypt(envelope, listOf(me), me, id)

    @Test
    fun `send to self then read`() = runTest {
        build(signed_pq = false)
        assertEquals("one", open(send("one"), "m1"))
    }

    @Test
    fun `two sends to self then read both in order`() = runTest {
        build(signed_pq = false)
        val a = send("one")
        val b = send("two")
        assertEquals("one", open(a, "m1"))
        assertEquals("two", open(b, "m2"))
    }

    @Test
    fun `two sends to self read newest first`() = runTest {
        build(signed_pq = false)
        val a = send("one")
        val b = send("two")
        assertEquals("two", open(b, "m2"))
        assertEquals("one", open(a, "m1"))
    }

    @Test
    fun `send read send read`() = runTest {
        build(signed_pq = false)
        assertEquals("one", open(send("one"), "m1"))
        assertEquals("two", open(send("two"), "m2"))
        assertEquals("three", open(send("three"), "m3"))
    }

    @Test
    fun `read then send again then reread the first`() = runTest {
        build(signed_pq = false)
        val a = send("one")
        assertEquals("one", open(a, "m1"))
        val b = send("two")
        assertEquals("two", open(b, "m2"))
        assertEquals("one", open(a, "m1-again"))
    }

    @Test
    fun `pq send to self then read`() = runTest {
        pq_mode = true
        build(signed_pq = true)
        assertEquals("one", open(send("one"), "m1"))
    }

    @Test
    fun `pq two sends to self then read both in order`() = runTest {
        pq_mode = true
        build(signed_pq = true)
        val a = send("one")
        val b = send("two")
        assertEquals("one", open(a, "m1"))
        assertEquals("two", open(b, "m2"))
    }

    @Test
    fun `pq two sends to self read newest first`() = runTest {
        pq_mode = true
        build(signed_pq = true)
        val a = send("one")
        val b = send("two")
        assertEquals("two", open(b, "m2"))
        assertEquals("one", open(a, "m1"))
    }

    @Test
    fun `pq send read send read`() = runTest {
        pq_mode = true
        build(signed_pq = true)
        assertEquals("one", open(send("one"), "m1"))
        assertEquals("two", open(send("two"), "m2"))
        assertEquals("three", open(send("three"), "m3"))
    }

    @Test
    fun `pq read then send again then reread the first`() = runTest {
        pq_mode = true
        build(signed_pq = true)
        val a = send("one")
        assertEquals("one", open(a, "m1"))
        val b = send("two")
        assertEquals("two", open(b, "m2"))
        assertEquals("one", open(a, "m1-again"))
    }

    private fun replace_keys_from_vault() {
        val identity = RatchetCrypto.generate_p256_keypair()
        val spk = RatchetCrypto.generate_p256_keypair()
        val vault = JSONObject()
            .put("ratchet_identity_key", to_private_jwk(identity))
            .put("ratchet_identity_public", RatchetCrypto.b64_encode(identity.public_raw))
            .put("ratchet_signed_prekey", to_private_jwk(spk))
            .put("ratchet_signed_prekey_public", RatchetCrypto.b64_encode(spk.public_raw))
        if (pq_mode) {
            val pq = RatchetCrypto.ml_kem_768_generate_keypair()
            vault.put("ratchet_pq_identity_key", RatchetCrypto.b64_encode(pq.secret_key))
            vault.put("ratchet_pq_identity_public", RatchetCrypto.b64_encode(pq.public_key))
        }
        apply_vault_ratchet_keys(parse_vault_ratchet_keys(vault), session_key_store)
        local_states.clear()
        server_states.clear()
    }

    @Test
    fun `mail sealed before the vault replaced the keys still opens`() = runTest {
        build(signed_pq = false)
        val sealed = send("one")
        replace_keys_from_vault()
        assertEquals("one", open(sealed, "m1"))
    }

    @Test
    fun `pq mail sealed before the vault replaced the keys still opens`() = runTest {
        pq_mode = true
        build(signed_pq = true)
        val sealed = send("one")
        replace_keys_from_vault()
        assertEquals("one", open(sealed, "m1"))
    }
}
