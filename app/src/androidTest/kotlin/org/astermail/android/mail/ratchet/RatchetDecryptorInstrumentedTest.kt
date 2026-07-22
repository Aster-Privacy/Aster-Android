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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.ratchet.PostStateOutcome
import org.astermail.android.api.ratchet.PqSecretResponse
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.RatchetStateResponse
import org.astermail.android.auth.AuthRepository
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the exact production RatchetDecryptor + RatchetStateStore + RatchetStateSyncer
 * pipeline on the real Android runtime (real android.util.Base64, real Keystore-backed
 * encrypted storage), with only the network layer (RatchetApi) faked out. This is the
 * on-device counterpart to RatchetDecryptorTest's JVM-hosted coverage.
 */
@RunWith(AndroidJUnit4::class)
class RatchetDecryptorInstrumentedTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val sender_email = "ratchet-instrumented-test-sender@test.invalid"
    private val recipient_email = "ratchet-instrumented-test-receiver@test.invalid"
    private val test_passphrase = "instrumented-test-passphrase-do-not-use".toByteArray(Charsets.UTF_8)

    private val used_conversation_ids = mutableListOf<Pair<RatchetStateStore, String>>()

    @After
    fun cleanup() = runTest {
        for ((store, conversation_id) in used_conversation_ids) {
            store.delete(conversation_id)
        }
    }

    private class FakeRatchetApi : RatchetApi {
        var stored: RatchetStateResponse? = null

        override suspend fun fetch_state(conversation_id_b64: String): RatchetStateResponse? = stored

        override suspend fun post_state(
            conversation_id_b64: String,
            encrypted_state: String,
            state_nonce: String,
        ): PostStateOutcome {
            if (stored != null) return PostStateOutcome.AlreadyExists
            val response = RatchetStateResponse(
                id = "test",
                conversation_id = conversation_id_b64,
                encrypted_state = encrypted_state,
                state_nonce = state_nonce,
                state_version = 1,
            )
            stored = response
            return PostStateOutcome.Success(response)
        }

        override suspend fun put_state(
            conversation_id_b64: String,
            encrypted_state: String,
            state_nonce: String,
            expected_version: Int,
        ): PutStateOutcome {
            val current = stored
            if (current == null) return PutStateOutcome.NotFound
            if (current.state_version != expected_version) return PutStateOutcome.VersionConflict
            val response = current.copy(encrypted_state = encrypted_state, state_nonce = state_nonce, state_version = expected_version + 1)
            stored = response
            return PutStateOutcome.Success(response)
        }

        override suspend fun fetch_pq_secret(key_id: Int): PqSecretResponse? = null
        override suspend fun fetch_prekey_bundle(username: String, email: String): PrekeyBundleResponse? = null
        override suspend fun delete_state(conversation_id_b64: String): Boolean {
            stored = null
            return true
        }
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

    private class ReceiverKeys(
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

    private fun envelope_json(sender_identity_raw: ByteArray, recipient_data: RatchetRecipientData): String {
        val envelope = RatchetEnvelope(
            type = "double_ratchet_v2",
            sender_identity_key = RatchetCrypto.b64_encode(sender_identity_raw),
            recipients = mapOf(recipient_email to recipient_data),
        )
        return json.encodeToString(envelope)
    }

    @Test
    fun continuation_message_with_corrupted_local_state_recovers_via_real_server_refetch() = runTest {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storage_session_key_store = SessionKeyStore(null)
        storage_session_key_store.put_passphrase(test_passphrase)
        val real_state_store = RatchetStateStore(context, storage_session_key_store)
        val fake_ratchet_api = FakeRatchetApi()
        val real_syncer = RatchetStateSyncer(real_state_store, fake_ratchet_api)

        val fixture = build_fixture()
        used_conversation_ids.add(real_state_store to fixture.conversation_id)

        val enc0 = DoubleRatchet.encrypt(fixture.sender_state, "first message")
        val receiver_state = receiver_state_from(fixture)
        val plaintext0 = DoubleRatchet.decrypt(receiver_state, recipient_data_for(fixture, enc0))
        assertEquals("first message", plaintext0)

        val server_state = deep_copy(receiver_state)
        assertTrue(real_syncer.sync(fixture.conversation_id, server_state))

        val stale_state = deep_copy(receiver_state)
        val corrupt_kp = RatchetCrypto.generate_p256_keypair()
        stale_state.dh_keypair = stale_state.dh_keypair.copy(
            secret_key = RatchetCrypto.b64_encode(RatchetCrypto.private_to_raw_d(corrupt_kp.private_key)),
        )
        real_state_store.save(stale_state)

        val reply_state = deep_copy(receiver_state)
        val enc_reply = DoubleRatchet.encrypt(reply_state, "reply from test receiver")
        val plaintext_reply = DoubleRatchet.decrypt(fixture.sender_state, recipient_data_for(fixture, enc_reply))
        assertEquals("reply from test receiver", plaintext_reply)

        val enc2 = DoubleRatchet.encrypt(fixture.sender_state, "third message")
        assertTrue(enc2.header.dh_public != stale_state.dh_remote_public)
        val body = envelope_json(fixture.sender_identity_raw, recipient_data_for(fixture, enc2))

        val decryptor_session_key_store = SessionKeyStore(null)
        decryptor_session_key_store.put_ratchet_keys(
            fixture.receiver_keys.identity_jwk,
            fixture.receiver_keys.identity_public_b64,
            fixture.receiver_keys.spk_jwk,
            fixture.receiver_keys.spk_public_b64,
        )

        val decryptor = RatchetDecryptor(
            real_state_store,
            decryptor_session_key_store,
            fake_ratchet_api,
            real_syncer,
            ConversationLocks(),
            dagger.Lazy<AuthRepository> { throw IllegalStateException("vault refresh should not be needed for this recovery path") },
        )

        val result = decryptor.try_decrypt(body, listOf(recipient_email), sender_email)

        assertEquals("third message", result)

        val loaded_after = real_state_store.load(fixture.conversation_id)
        assertTrue(loaded_after != null && loaded_after.dh_remote_public == enc2.header.dh_public)
    }
}
