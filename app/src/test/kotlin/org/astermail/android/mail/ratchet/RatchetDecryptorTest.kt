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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetDecryptorTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val sender_email = "hello@astermail.org"
    private val recipient_email = "kchaos@aster.cx"

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
            val s = firstArg<String>()
            val flags = secondArg<Int>()
            if (flags and Base64.URL_SAFE != 0) {
                var padded = s
                val mod = padded.length % 4
                if (mod != 0) padded += "=".repeat(4 - mod)
                java.util.Base64.getUrlDecoder().decode(padded)
            } else {
                java.util.Base64.getDecoder().decode(s)
            }
        }
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

    private fun deep_copy(state: RatchetState): RatchetState =
        state.copy(
            dh_keypair = state.dh_keypair.copy(),
            skipped_message_keys = state.skipped_message_keys.toMutableList(),
        )

    private fun envelope_json(sender_identity_raw: ByteArray, recipient_data: RatchetRecipientData): String {
        val envelope = RatchetEnvelope(
            type = "double_ratchet_v2",
            sender_identity_key = RatchetCrypto.b64_encode(sender_identity_raw),
            recipients = mapOf(recipient_email to recipient_data),
        )
        return json.encodeToString(envelope)
    }

    private fun new_decryptor(
        state_store: RatchetStateStore,
        session_key_store: SessionKeyStore,
        ratchet_api: RatchetApi,
        syncer: RatchetStateSyncer,
        auth_repo: AuthRepository,
    ): RatchetDecryptor = RatchetDecryptor(
        state_store,
        session_key_store,
        ratchet_api,
        syncer,
        ConversationLocks(),
        dagger.Lazy { auth_repo },
    )

    private data class ReceiverKeys(
        val identity_jwk: String,
        val identity_public_b64: String,
        val spk_jwk: String,
        val spk_public_b64: String,
    )

    private class Fixture(
        val conversation_id: String,
        val sender_identity_raw: ByteArray,
        val sender_state: RatchetState,
        val sender_ephemeral_raw: ByteArray,
        val receiver_keys: ReceiverKeys,
    )

    private fun build_fixture(): Fixture {
        val sender_identity_kp = RatchetCrypto.generate_p256_keypair()
        val receiver_identity_kp = RatchetCrypto.generate_p256_keypair()
        val receiver_spk_kp = RatchetCrypto.generate_p256_keypair()

        val sender_identity_jwk = to_private_jwk(sender_identity_kp)
        val receiver_identity_jwk = to_private_jwk(receiver_identity_kp)
        val receiver_spk_jwk = to_private_jwk(receiver_spk_kp)
        val receiver_spk_public_b64 = RatchetCrypto.b64_encode(receiver_spk_kp.public_raw)
        val receiver_identity_public_b64 = RatchetCrypto.b64_encode(receiver_identity_kp.public_raw)

        val conversation_id = X3dh.derive_conversation_id(recipient_email, sender_email)

        val sender_result = X3dh.perform_sender(
            sender_identity_jwk = sender_identity_jwk,
            recipient_identity_raw = receiver_identity_kp.public_raw,
            recipient_signed_prekey_raw = receiver_spk_kp.public_raw,
        )

        val sender_state = DoubleRatchet.init_sender(
            conversation_id = conversation_id,
            shared_secret = sender_result.shared_secret,
            remote_signed_prekey_raw_b64 = receiver_spk_public_b64,
        )

        return Fixture(
            conversation_id = conversation_id,
            sender_identity_raw = sender_identity_kp.public_raw,
            sender_state = sender_state,
            sender_ephemeral_raw = sender_result.ephemeral_public_raw,
            receiver_keys = ReceiverKeys(
                receiver_identity_jwk,
                receiver_identity_public_b64,
                receiver_spk_jwk,
                receiver_spk_public_b64,
            ),
        )
    }

    private fun receiver_state_from(fixture: Fixture): RatchetState {
        val shared = X3dh.perform_receiver(
            receiver_identity_jwk = fixture.receiver_keys.identity_jwk,
            receiver_signed_prekey_jwk = fixture.receiver_keys.spk_jwk,
            sender_identity_raw = fixture.sender_identity_raw,
            sender_ephemeral_raw = fixture.sender_ephemeral_raw,
        )
        return X3dh.init_receiver_state(
            conversation_id = fixture.conversation_id,
            shared_secret = shared,
            own_signed_prekey_jwk = fixture.receiver_keys.spk_jwk,
            own_signed_prekey_public_b64 = fixture.receiver_keys.spk_public_b64,
        )
    }

    private fun recipient_data_for(fixture: Fixture, enc: DoubleRatchet.EncryptResult): RatchetRecipientData =
        RatchetRecipientData(
            ephemeral_key = RatchetCrypto.b64_encode(fixture.sender_ephemeral_raw),
            header = enc.header,
            ciphertext = RatchetCrypto.b64_encode(enc.ciphertext),
            nonce = RatchetCrypto.b64_encode(enc.nonce),
        )

    private fun seed_session_key_store(session_key_store: SessionKeyStore, keys: ReceiverKeys) {
        session_key_store.put_ratchet_keys(
            keys.identity_jwk,
            keys.identity_public_b64,
            keys.spk_jwk,
            keys.spk_public_b64,
        )
    }

    @Test
    fun `fresh bootstrap decrypts successfully with correct recipient keys`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)
        val auth_repo = mockk<AuthRepository>(relaxed = true)

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("hello kchaos", result)
    }

    @Test
    fun `fresh bootstrap recovers via vault refresh when local keys are stale`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello again")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)

        val session_key_store = SessionKeyStore(null)
        val stale_identity_kp = RatchetCrypto.generate_p256_keypair()
        val stale_spk_kp = RatchetCrypto.generate_p256_keypair()
        session_key_store.put_ratchet_keys(
            to_private_jwk(stale_identity_kp),
            RatchetCrypto.b64_encode(stale_identity_kp.public_raw),
            to_private_jwk(stale_spk_kp),
            RatchetCrypto.b64_encode(stale_spk_kp.public_raw),
        )

        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } coAnswers {
            seed_session_key_store(session_key_store, fixture.receiver_keys)
            true
        }

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("hello again", result)
        coVerify(exactly = 1) { auth_repo.try_refresh_vault_keys() }
    }

    @Test
    fun `continuation message with corrupted local ratchet state recovers via server refetch`() = runTest {
        val fixture = build_fixture()

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        val plaintext0 = DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0))
        assertEquals("first message", plaintext0)

        val server_state = deep_copy(receiver_state)
        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        val plaintext_reply = DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))
        assertEquals("reply from kchaos", plaintext_reply)

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        assertTrue(enc2.header.dh_public != stale_state.dh_remote_public)
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc2))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns stale_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(fixture.conversation_id) } returns server_state
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)
        val auth_repo = mockk<AuthRepository>(relaxed = true)

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("third message", result)
        coVerify(exactly = 1) { syncer.fetch_from_server(fixture.conversation_id) }
    }

    @Test
    fun `continuation message stays undecryptable when server has no recovery state`() = runTest {
        val fixture = build_fixture()

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        val plaintext0 = DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0))
        assertEquals("first message", plaintext0)

        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        val plaintext_reply = DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))
        assertEquals("reply from kchaos", plaintext_reply)

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        assertTrue(enc2.header.dh_public != stale_state.dh_remote_public)
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc2))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns stale_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(fixture.conversation_id) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)
        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } returns false

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals(RATCHET_UNDECRYPTABLE_SENTINEL, result)
    }
}
