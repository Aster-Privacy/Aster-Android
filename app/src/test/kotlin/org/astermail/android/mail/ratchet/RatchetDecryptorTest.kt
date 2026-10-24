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
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        unmockkObject(SecurePrefs)
        unmockkStatic(Base64::class)
    }

    private val pin_values = mutableMapOf<String, Any?>()

    private fun fake_pin_prefs(unreadable_prefix: String? = null): SharedPreferences {
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            pin_values[firstArg()] = secondArg<String?>()
            editor
        }
        every { editor.putBoolean(any(), any()) } answers {
            pin_values[firstArg()] = secondArg<Boolean>()
            editor
        }
        every { editor.putInt(any(), any()) } answers {
            pin_values[firstArg()] = secondArg<Int>()
            editor
        }
        every { editor.remove(any()) } answers {
            pin_values.remove(firstArg<String>())
            editor
        }
        every { editor.clear() } answers {
            pin_values.clear()
            editor
        }
        every { editor.commit() } returns true
        every { editor.apply() } returns Unit
        val prefs = mockk<SharedPreferences>()
        every { prefs.edit() } returns editor
        every { prefs.getString(any(), any()) } answers {
            if (unreadable_prefix != null && firstArg<String>().startsWith(unreadable_prefix)) {
                throw SecurityException("unreadable")
            }
            pin_values[firstArg()] as? String ?: secondArg<String?>()
        }
        every { prefs.getBoolean(any(), any()) } answers {
            pin_values[firstArg()] as? Boolean ?: secondArg<Boolean>()
        }
        every { prefs.getInt(any(), any()) } answers {
            pin_values[firstArg()] as? Int ?: secondArg<Int>()
        }
        every { prefs.contains(any()) } answers { pin_values.containsKey(firstArg<String>()) }
        every { prefs.all } answers { pin_values.toMap() }
        return prefs
    }

    private fun real_pin_store(unreadable_prefix: String? = null): RatchetIdentityPinStore {
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns fake_pin_prefs(unreadable_prefix)
        return RatchetIdentityPinStore(mockk(relaxed = true), SessionKeyStore(null))
    }

    private fun stateful_store(): RatchetStateStore {
        var saved: RatchetState? = null
        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } answers { saved?.let { deep_copy(it) } }
        coEvery { state_store.save(any()) } answers { saved = deep_copy(firstArg()) }
        return state_store
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

    private fun envelope_json(
        sender_identity_raw: ByteArray,
        recipient_data: RatchetRecipientData,
        address: String = recipient_email,
    ): String {
        val envelope = RatchetEnvelope(
            type = "double_ratchet_v2",
            sender_identity_key = RatchetCrypto.b64_encode(sender_identity_raw),
            recipients = mapOf(address to recipient_data),
        )
        return json.encodeToString(envelope)
    }

    private fun new_decryptor(
        state_store: RatchetStateStore,
        session_key_store: SessionKeyStore,
        ratchet_api: RatchetApi,
        syncer: RatchetStateSyncer,
        auth_repo: AuthRepository,
        plaintext_cache: RatchetPlaintextCache = mockk(relaxed = true),
        identity_pins: RatchetIdentityPinStore = pin_store(),
        escrow: MessageEscrow = escrow_returning(null),
    ): RatchetDecryptor = RatchetDecryptor(
        state_store,
        session_key_store,
        ratchet_api,
        syncer,
        ConversationLocks(),
        dagger.Lazy { auth_repo },
        identity_pins,
        plaintext_cache,
        escrow,
    )

    private fun escrow_returning(
        plaintext: String?,
        unconfirmed_sender: Boolean = false,
        verified: Boolean = true,
    ): MessageEscrow {
        val escrow = mockk<MessageEscrow>(relaxed = true)
        coEvery { escrow.fetch(any()) } returns plaintext
        coEvery { escrow.fetch_bound(any(), any(), any()) } returns plaintext?.let {
            EscrowedPlaintext(plaintext = it, verified = verified, unconfirmed_sender = unconfirmed_sender || !verified)
        }
        return escrow
    }

    private fun pin_store(
        replayed_bootstrap: Boolean = false,
        continuity: SenderIdentityContinuity = SenderIdentityContinuity.NEW,
    ): RatchetIdentityPinStore {
        val identity_pins = mockk<RatchetIdentityPinStore>(relaxed = true)
        every { identity_pins.sender_continuity(any(), any(), any()) } returns continuity
        coEvery { identity_pins.is_replayed_bootstrap(any(), any()) } returns replayed_bootstrap
        coEvery { identity_pins.record(any(), any(), any(), any()) } returns IdentityPinOutcome.UNCHANGED
        return identity_pins
    }

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

    private fun build_fixture(address: String = recipient_email): Fixture {
        val sender_identity_kp = RatchetCrypto.generate_p256_keypair()
        val receiver_identity_kp = RatchetCrypto.generate_p256_keypair()
        val receiver_spk_kp = RatchetCrypto.generate_p256_keypair()

        val sender_identity_jwk = to_private_jwk(sender_identity_kp)
        val receiver_identity_jwk = to_private_jwk(receiver_identity_kp)
        val receiver_spk_jwk = to_private_jwk(receiver_spk_kp)
        val receiver_spk_public_b64 = RatchetCrypto.b64_encode(receiver_spk_kp.public_raw)
        val receiver_identity_public_b64 = RatchetCrypto.b64_encode(receiver_identity_kp.public_raw)

        val conversation_id = X3dh.derive_conversation_id(address, sender_email)

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

    @Test
    fun `server state that cannot decrypt the message is never persisted locally`() = runTest {
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
        val bogus_server_state = deep_copy(stale_state)
        val bogus_kp = RatchetCrypto.generate_p256_keypair()
        bogus_server_state.dh_keypair = bogus_server_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(bogus_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        val plaintext_reply = DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))
        assertEquals("reply from kchaos", plaintext_reply)

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc2))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns stale_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(fixture.conversation_id) } returns bogus_server_state
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)
        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } returns false

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals(RATCHET_UNDECRYPTABLE_SENTINEL, result)
        coVerify(exactly = 0) { state_store.save(bogus_server_state) }
    }

    private fun lane_only_body(fixture: Fixture): Pair<String, RatchetState> {
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        assertEquals("first message", DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0)))

        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        val body = envelope_json(
            fixture.sender_identity_raw,
            recipient_data_for(fixture, enc2).copy(recovery = lane_for(fixture, enc2)),
        )
        return body to stale_state
    }

    private fun lane_decryptor(
        fixture: Fixture,
        stale_state: RatchetState,
        plaintext_cache: RatchetPlaintextCache,
        escrow: MessageEscrow,
    ): RatchetDecryptor {
        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns stale_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(fixture.conversation_id) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } returns false
        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)
        return new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            auth_repo,
            plaintext_cache,
            escrow = escrow,
        )
    }

    @Test
    fun `an escrow hit is used before the recovery lane and stays authenticated`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning("third message from escrow")

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message from escrow", result)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message from escrow", false, false, any()) }
        coVerify(exactly = 0) { plaintext_cache.put(any(), any(), true, any(), any()) }
        verify(exactly = 0) { escrow.upload_in_background(any(), any()) }
        verify(exactly = 0) { escrow.upload_bound_in_background(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `recovery lane plaintext is never uploaded to the escrow`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning(null)

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message", result)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message", true, false, any()) }
        coVerify(atLeast = 1) {
            escrow.fetch_bound(match { it.startsWith("msg_lane:") }, sender_email, fixture.conversation_id)
        }
        verify(exactly = 0) { escrow.upload_in_background(any(), any()) }
        verify(exactly = 0) { escrow.upload_bound_in_background(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an authenticated decrypt uploads the plaintext to the escrow`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)
        val escrow = escrow_returning(null)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            escrow = escrow,
        )
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_chain")

        assertEquals("hello kchaos", result)
        val expected_key = "msg_chain:${enc0.header.dh_public}:${enc0.header.message_number}"
        verify(exactly = 1) {
            escrow.upload_bound_in_background(expected_key, "hello kchaos", sender_email, fixture.conversation_id, false)
        }
    }

    @Test
    fun `an escrow entry without a sender binding is shown as unverified`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning("third message from escrow", verified = false)

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message from escrow", result)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message from escrow", true, true, any()) }
        coVerify(exactly = 0) { plaintext_cache.put(any(), any(), false, any(), any()) }
    }

    @Test
    fun `the escrow is queried and cached with the sender and conversation binding`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning("third message from escrow")

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_lane")

        coVerify(atLeast = 1) { escrow.fetch_bound(any(), sender_email, fixture.conversation_id) }
        coVerify(exactly = 1) {
            plaintext_cache.put(
                "msg_lane",
                "third message from escrow",
                false,
                false,
                PlaintextBinding(sender_email, fixture.conversation_id),
            )
        }
    }

    @Test
    fun `an unbound escrow entry never upgrades an unverified message`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        every { plaintext_cache.is_sender_unauthenticated("msg_lane") } returns true
        val escrow = escrow_returning("swapped text", verified = false)

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val result = decryptor.upgrade_from_escrow(body, listOf(recipient_email), sender_email, "msg_lane")

        assertNull(result)
        coVerify(exactly = 0) { plaintext_cache.put(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an unauthenticated cache entry is upgraded from the escrow once`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        every { plaintext_cache.is_sender_unauthenticated("msg_lane") } returns true
        val escrow = escrow_returning("third message")

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val first = decryptor.upgrade_from_escrow(body, listOf(recipient_email), sender_email, "msg_lane")
        val second = decryptor.upgrade_from_escrow(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message", first)
        assertNull(second)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message", false, false, any()) }
        coVerify(exactly = 1) { escrow.fetch_bound(any(), sender_email, fixture.conversation_id) }
        verify(exactly = 0) { escrow.upload_bound_in_background(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an authenticated cache entry never queries the escrow`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        every { plaintext_cache.is_sender_unauthenticated(any()) } returns false
        val escrow = escrow_returning("third message")

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val result = decryptor.upgrade_from_escrow(body, listOf(recipient_email), sender_email, "msg_lane")

        assertNull(result)
        coVerify(exactly = 0) { escrow.fetch(any()) }
        coVerify(exactly = 0) { escrow.fetch_bound(any(), any(), any()) }
    }

    @Test
    fun `recovery lane decrypts a continuation message the ratchet state cannot`() = runTest {
        val fixture = build_fixture()

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        assertEquals("first message", DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0)))

        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        val body = envelope_json(
            fixture.sender_identity_raw,
            recipient_data_for(fixture, enc2).copy(recovery = lane_for(fixture, enc2)),
        )

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

        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo, plaintext_cache)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message", result)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message", true, any(), any()) }
    }

    @Test
    fun `recovery lane decrypts a continuation message that carries no bootstrap key`() = runTest {
        val fixture = build_fixture()

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        assertEquals("first message", DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0)))

        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        val body = envelope_json(
            fixture.sender_identity_raw,
            recipient_data_for(fixture, enc2).copy(ephemeral_key = null, recovery = lane_for(fixture, enc2)),
        )

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

        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo, plaintext_cache)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message", result)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message", true, any(), any()) }
    }

    @Test
    fun `message addressed to an alias decrypts for the primary address`() = runTest {
        val alias_email = "support@aster.cx"
        val fixture = build_fixture(alias_email)
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "sent to the alias")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0), alias_email)

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)
        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } returns false

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("sent to the alias", result)
    }

    @Test
    fun `envelope with no openable entry stays undecryptable`() = runTest {
        val fixture = build_fixture("stranger@aster.cx")
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "not for you")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0), "stranger@aster.cx")

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val ratchet_api = mockk<RatchetApi>(relaxed = true)
        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } returns false

        val session_key_store = SessionKeyStore(null)
        val other_identity_kp = RatchetCrypto.generate_p256_keypair()
        val other_spk_kp = RatchetCrypto.generate_p256_keypair()
        session_key_store.put_ratchet_keys(
            to_private_jwk(other_identity_kp),
            RatchetCrypto.b64_encode(other_identity_kp.public_raw),
            to_private_jwk(other_spk_kp),
            RatchetCrypto.b64_encode(other_spk_kp.public_raw),
        )

        val decryptor = new_decryptor(state_store, session_key_store, ratchet_api, syncer, auth_repo)
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals(RATCHET_UNDECRYPTABLE_SENTINEL, result)
    }

    @Test
    fun `recovery lane size does not grow with the message body`() {
        val fixture = build_fixture()

        val small = DoubleRatchet.encrypt(fixture.sender_state, "hi")
        val large = DoubleRatchet.encrypt(fixture.sender_state, "x".repeat(100_000))

        val small_lane = json.encodeToString(lane_for(fixture, small))
        val large_lane = json.encodeToString(lane_for(fixture, large))

        assertEquals(small_lane.length, large_lane.length)
    }

    private fun lane_for(fixture: Fixture, enc: DoubleRatchet.EncryptResult): RecoveryLaneData {
        val sealed = RecoveryLane.seal(
            RatchetCrypto.b64_encode(enc.message_key),
            fixture.conversation_id,
            RatchetCrypto.b64_encode(fixture.sender_identity_raw),
            RecoveryLane.RecipientKeys(fixture.receiver_keys.identity_public_b64, null),
        )
        return RecoveryLaneData(
            v = sealed.v,
            epk = sealed.epk,
            kem_ct = sealed.kem_ct,
            ciphertext = sealed.ciphertext,
            nonce = sealed.nonce,
            rid = sealed.rid,
        )
    }

    @Test
    fun `plaintext is cached before the advanced ratchet state is persisted`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            plaintext_cache,
        )
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_1")

        assertEquals("hello kchaos", result)
        coVerifyOrder {
            plaintext_cache.put("msg_1", "hello kchaos", any(), any(), any())
            state_store.save(any())
            syncer.sync(any(), any())
        }
    }

    @Test
    fun `plaintext survives a failure while persisting the advanced ratchet state`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "durable body")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        coEvery { state_store.save(any()) } throws RuntimeException("process died")
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            plaintext_cache,
        )
        decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_2")

        coVerify(exactly = 1) { plaintext_cache.put("msg_2", "durable body", any(), any(), any()) }
    }

    @Test
    fun `recovery lane decrypt never touches the identity pin`() = runTest {
        val fixture = build_fixture()

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        assertEquals("first message", DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0)))

        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from kchaos")
        DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        val unrelated_identity = RatchetCrypto.generate_p256_keypair().public_raw
        val body = envelope_json(
            unrelated_identity,
            recipient_data_for(fixture, enc2).copy(ephemeral_key = null, recovery = lane_for_sender(fixture, enc2, unrelated_identity)),
        )

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns stale_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(fixture.conversation_id) } returns null
        val auth_repo = mockk<AuthRepository>(relaxed = true)
        coEvery { auth_repo.try_refresh_vault_keys() } returns false
        val identity_pins = pin_store()

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            auth_repo,
            identity_pins = identity_pins,
        )
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("third message", result)
        coVerify(exactly = 0) { identity_pins.record(any(), any(), any(), any()) }
    }

    @Test
    fun `chained decrypt never records the unauthenticated envelope identity`() = runTest {
        val fixture = build_fixture()

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        assertEquals("first message", DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0)))

        val enc1 = DoubleRatchet.encrypt(fixture.sender_state, "second message")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc1))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns receiver_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.sync(any(), any()) } returns true
        val identity_pins = pin_store(replayed_bootstrap = true)

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            identity_pins = identity_pins,
        )
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("second message", result)
        coVerify(exactly = 0) { identity_pins.record(any(), any(), any(), any()) }
    }

    @Test
    fun `new bootstrap records the sender identity and remembers the ephemeral key`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(any()) } returns null
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        val identity_pins = pin_store()

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            identity_pins = identity_pins,
        )
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("hello kchaos", result)
        coVerify(exactly = 1) {
            identity_pins.record(fixture.conversation_id, sender_email, any(), any())
        }
        val ephemeral_b64 = RatchetCrypto.b64_encode(fixture.sender_ephemeral_raw)
        coVerify(exactly = 1) { identity_pins.record_bootstrap(fixture.conversation_id, ephemeral_b64) }
    }

    @Test
    fun `reopening an already accepted bootstrap does not flag the sender identity`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val receiver_state = receiver_state_from(fixture)
        assertEquals("hello kchaos", DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0)))
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))

        val state_store = mockk<RatchetStateStore>(relaxed = true)
        coEvery { state_store.load(fixture.conversation_id) } returns receiver_state
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.sync(any(), any()) } returns true
        val identity_pins = pin_store(replayed_bootstrap = true)

        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)

        val decryptor = new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            identity_pins = identity_pins,
        )
        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("hello kchaos", result)
        coVerify(exactly = 0) { identity_pins.record(any(), any(), any(), any()) }
    }

    private fun lane_for_sender(
        fixture: Fixture,
        enc: DoubleRatchet.EncryptResult,
        sender_identity_raw: ByteArray,
    ): RecoveryLaneData {
        val sealed = RecoveryLane.seal(
            RatchetCrypto.b64_encode(enc.message_key),
            fixture.conversation_id,
            RatchetCrypto.b64_encode(sender_identity_raw),
            RecoveryLane.RecipientKeys(fixture.receiver_keys.identity_public_b64, null),
        )
        return RecoveryLaneData(
            v = sealed.v,
            epk = sealed.epk,
            kem_ct = sealed.kem_ct,
            ciphertext = sealed.ciphertext,
            nonce = sealed.nonce,
            rid = sealed.rid,
        )
    }

    private fun chain_decryptor(
        fixture: Fixture,
        state_store: RatchetStateStore,
        identity_pins: RatchetIdentityPinStore,
        plaintext_cache: RatchetPlaintextCache,
        escrow: MessageEscrow,
        session_key_store: SessionKeyStore = SessionKeyStore(null).also {
            seed_session_key_store(it, fixture.receiver_keys)
        },
    ): RatchetDecryptor {
        val syncer = mockk<RatchetStateSyncer>(relaxed = true)
        coEvery { syncer.fetch_from_server(any()) } returns null
        coEvery { syncer.sync(any(), any()) } returns true
        return new_decryptor(
            state_store,
            session_key_store,
            mockk(relaxed = true),
            syncer,
            mockk(relaxed = true),
            plaintext_cache,
            identity_pins,
            escrow,
        )
    }

    @Test
    fun `a known sender key is verified on the second message`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val enc1 = DoubleRatchet.encrypt(fixture.sender_state, "second message")
        val first_body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))
        val second_body = envelope_json(
            fixture.sender_identity_raw,
            recipient_data_for(fixture, enc1).copy(ephemeral_key = null),
        )
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning(null)
        val decryptor = chain_decryptor(fixture, stateful_store(), real_pin_store(), plaintext_cache, escrow)

        assertEquals(
            "first message",
            decryptor.try_decrypt(first_body, listOf(recipient_email), sender_email, "msg_first"),
        )
        assertEquals(
            "second message",
            decryptor.try_decrypt(second_body, listOf(recipient_email), sender_email, "msg_second"),
        )

        coVerify(exactly = 1) { plaintext_cache.put("msg_first", "first message", false, false, any()) }
        coVerify(exactly = 1) { plaintext_cache.put("msg_second", "second message", false, false, any()) }
        val first_key = "msg_first:${enc0.header.dh_public}:${enc0.header.message_number}"
        val second_key = "msg_second:${enc1.header.dh_public}:${enc1.header.message_number}"
        verify(exactly = 1) {
            escrow.upload_bound_in_background(first_key, "first message", sender_email, fixture.conversation_id, false)
        }
        verify(exactly = 1) {
            escrow.upload_bound_in_background(second_key, "second message", sender_email, fixture.conversation_id, false)
        }
    }

    @Test
    fun `a first contact with no stored sender key is treated as confirmed`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning(null)
        val identity_pins = real_pin_store()
        val decryptor = chain_decryptor(fixture, stateful_store(), identity_pins, plaintext_cache, escrow)

        assertEquals("hello kchaos", decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_new"))

        coVerify(exactly = 1) { plaintext_cache.put("msg_new", "hello kchaos", false, false, any()) }
        verify(exactly = 1) {
            escrow.upload_bound_in_background(any(), "hello kchaos", sender_email, fixture.conversation_id, false)
        }
        assertTrue(identity_pins.unacknowledged_changes.value.isEmpty())
    }

    @Test
    fun `a changed sender key is unverified and records a rotation notice`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "hello kchaos")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning(null)
        val identity_pins = real_pin_store()
        val earlier_identity = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        identity_pins.remember_sender_identity(sender_email, earlier_identity)
        val decryptor = chain_decryptor(fixture, stateful_store(), identity_pins, plaintext_cache, escrow)

        assertEquals("hello kchaos", decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_changed"))

        coVerify(exactly = 1) { plaintext_cache.put("msg_changed", "hello kchaos", false, true, any()) }
        verify(exactly = 1) {
            escrow.upload_bound_in_background(any(), "hello kchaos", sender_email, fixture.conversation_id, true)
        }
        val change = identity_pins.unacknowledged_changes.value.single()
        assertEquals(fixture.conversation_id, change.conversation_id)
        assertEquals(sender_email, change.sender_email)
        assertTrue(change.previous_fingerprint != change.current_fingerprint)
        assertEquals(
            SenderIdentityContinuity.CHANGED,
            identity_pins.sender_continuity(
                fixture.conversation_id,
                sender_email,
                RatchetCrypto.b64_encode(fixture.sender_identity_raw),
            ),
        )
    }

    @Test
    fun `the own vault identity key of the account is verified`() = runTest {
        val fixture = build_fixture()
        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "note to self")
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc0))
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        val escrow = escrow_returning(null)
        val session_key_store = SessionKeyStore(null)
        seed_session_key_store(session_key_store, fixture.receiver_keys)
        val own_previous = org.json.JSONArray().put(
            org.json.JSONObject().put("ratchet_identity_public", RatchetCrypto.b64_encode(fixture.sender_identity_raw)),
        )
        session_key_store.put_ratchet_previous_keys(own_previous.toString())
        val decryptor = chain_decryptor(
            fixture,
            stateful_store(),
            real_pin_store(),
            plaintext_cache,
            escrow,
            session_key_store,
        )

        assertEquals("note to self", decryptor.try_decrypt(body, listOf(recipient_email), sender_email, "msg_self"))

        coVerify(exactly = 1) { plaintext_cache.put("msg_self", "note to self", false, false, any()) }
        verify(exactly = 1) {
            escrow.upload_bound_in_background(any(), "note to self", sender_email, fixture.conversation_id, false)
        }
    }

    @Test
    fun `a lane upgrade keeps the escrowed unconfirmed flag and does not upload again`() = runTest {
        val fixture = build_fixture()
        val (body, stale_state) = lane_only_body(fixture)
        val plaintext_cache = mockk<RatchetPlaintextCache>(relaxed = true)
        every { plaintext_cache.is_sender_unauthenticated("msg_lane") } returns true
        val escrow = escrow_returning("third message", unconfirmed_sender = true)

        val decryptor = lane_decryptor(fixture, stale_state, plaintext_cache, escrow)
        val upgraded = decryptor.upgrade_from_escrow(body, listOf(recipient_email), sender_email, "msg_lane")

        assertEquals("third message", upgraded)
        coVerify(exactly = 1) { plaintext_cache.put("msg_lane", "third message", false, true, any()) }
        verify(exactly = 0) { escrow.upload_in_background(any(), any()) }
        verify(exactly = 0) { escrow.upload_bound_in_background(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an unreadable sender history still consults the pinned key`() = runTest {
        val identity_pins = real_pin_store(unreadable_prefix = "hist_")
        val conversation_id = X3dh.derive_conversation_id(recipient_email, sender_email)
        val pinned_identity = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        val other_identity = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)
        identity_pins.pin_if_absent(conversation_id, pinned_identity)

        assertEquals(
            SenderIdentityContinuity.KNOWN,
            identity_pins.sender_continuity(conversation_id, sender_email, pinned_identity),
        )
        assertEquals(
            SenderIdentityContinuity.CHANGED,
            identity_pins.sender_continuity(conversation_id, sender_email, other_identity),
        )
    }

    @Test
    fun `an unreadable sender history is never overwritten`() = runTest {
        val identity_pins = real_pin_store(unreadable_prefix = "hist_")
        val identity = RatchetCrypto.b64_encode(RatchetCrypto.generate_p256_keypair().public_raw)

        identity_pins.remember_sender_identity(sender_email, identity)

        assertTrue(pin_values.keys.none { it.startsWith("hist_") })
    }
}
