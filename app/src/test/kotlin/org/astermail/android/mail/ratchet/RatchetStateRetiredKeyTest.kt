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
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.PostStateOutcome
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.RatchetStateResponse
import org.astermail.android.crypto.StorageWriteKey
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SecurePrefs
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class RatchetStateRetiredKeyTest {

    private val conversation_id = "conv-retired"
    private val current_master = ByteArray(32) { (it + 1).toByte() }
    private val retired_master = ByteArray(32) { (it + 42).toByte() }

    private val ratchet_api = mockk<RatchetApi>(relaxed = true)
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var state_store: RatchetStateStore

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        mockkObject(SecurePrefs)
        every { SecurePrefs.open(any(), any()) } returns mockk(relaxed = true)
        mockkObject(StorageWriteKey)
        every { StorageWriteKey.write_key(any()) } answers { current_master.copyOf() }
        every { StorageWriteKey.read_keys(any()) } answers {
            listOf(current_master.copyOf(), retired_master.copyOf())
        }
        session_key_store = SessionKeyStore(null)
        session_key_store.put_user_id("user-1")
        state_store = spyk(RatchetStateStore(mockk(relaxed = true), session_key_store))
        coEvery { state_store.load(any()) } returns null
        coEvery { state_store.save(any()) } returns Unit
        coEvery { state_store.sync_floor(any()) } returns 0L
        coEvery { state_store.raise_sync_floor(any(), any()) } returns Unit
    }

    @After
    fun teardown() {
        unmockkObject(StorageWriteKey)
        unmockkObject(SecurePrefs)
        unmockkStatic(Base64::class)
    }

    private fun legacy_state_key(master: ByteArray): ByteArray =
        RatchetCrypto.hkdf_sha256(
            ikm = master,
            salt = "Aster Mail_Ratchet_State_Encryption".toByteArray(Charsets.UTF_8),
            info = "ratchet_state_key".toByteArray(Charsets.UTF_8),
            length = 32,
        )

    private fun state(root_key: String, epoch: Int): RatchetState = RatchetState(
        conversation_id = conversation_id,
        dh_keypair = RatchetDhKeyPair(public_key = "pub", secret_key = "sec"),
        root_key = root_key,
        epoch = epoch,
    )

    private fun response(ciphertext: ByteArray, nonce: ByteArray, version: Int = 3) = RatchetStateResponse(
        id = "1",
        conversation_id = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8)),
        encrypted_state = RatchetCrypto.b64_encode(ciphertext),
        state_nonce = RatchetCrypto.b64_encode(nonce),
        state_version = version,
    )

    private fun sealed_v2(master: ByteArray, sealed: RatchetState, sync_version: Long, bound: Boolean): RatchetStateResponse {
        val nonce = RatchetCrypto.random_bytes(12)
        val plaintext = RatchetStateContainer.encode(conversation_id, sealed, sync_version).toByteArray(Charsets.UTF_8)
        val aad = if (bound) RatchetStateContainer.bound_aad(conversation_id) else null
        return response(RatchetCrypto.aes_gcm_encrypt(plaintext, legacy_state_key(master), nonce, aad), nonce)
    }

    private fun planted(bound: Boolean) =
        sealed_v2(retired_master, state("planted", 1_000_000_000), 9_000_000_000_000L, bound)

    @Test
    fun `a state sealed under a retired key is never restored`() = runTest {
        for (bound in listOf(false, true)) {
            coEvery { ratchet_api.fetch_state(any()) } returns planted(bound)

            val restored = RatchetStateSyncer(state_store, ratchet_api).fetch_from_server(conversation_id)

            assertNull(restored)
        }
    }

    @Test
    fun `a state sealed under a retired key never wins the merge`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns planted(true)
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns
            PutStateOutcome.Success(planted(true).copy(state_version = 4))
        val saved = mutableListOf<RatchetState>()
        coEvery { state_store.save(capture(saved)) } returns Unit

        RatchetStateSyncer(state_store, ratchet_api).sync(conversation_id, state("local", 2))

        assertEquals(emptyList<String>(), saved.map { it.root_key }.filter { it == "planted" })
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), 3) }
    }

    @Test
    fun `a retired copy at the same epoch never replaces the local chain keys`() = runTest {
        val local_chain = RatchetCrypto.b64_encode(ByteArray(32) { (it + 3).toByte() })
        val remote_chain = RatchetCrypto.b64_encode(ByteArray(32) { 9 })
        val local = state("shared-root", 2).copy(
            chain_key_send = local_chain,
            chain_key_recv = "local-recv",
            send_message_number = 1,
            recv_message_number = 1,
        )
        val racing = state("shared-root", 2).copy(
            chain_key_send = remote_chain,
            chain_key_recv = "remote-recv",
            send_message_number = 9,
            recv_message_number = 4,
        )
        val sealed = sealed_v2(retired_master, racing, 4L, true)
        coEvery { ratchet_api.fetch_state(any()) } returns sealed
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns
            PutStateOutcome.Success(sealed.copy(state_version = 4))
        val saved = mutableListOf<RatchetState>()
        coEvery { state_store.save(capture(saved)) } returns Unit
        val expected = local.copy()
        DoubleRatchet.advance_send_chain(expected, 9)

        RatchetStateSyncer(state_store, ratchet_api).sync(conversation_id, local.copy())

        assertEquals(emptyList<String>(), saved.mapNotNull { it.chain_key_send }.filter { it == remote_chain })
        assertEquals(emptyList<String>(), saved.mapNotNull { it.chain_key_recv }.filter { it == "remote-recv" })
        val merged = saved.first()
        assertEquals(expected.chain_key_send, merged.chain_key_send)
        assertEquals(9, merged.send_message_number)
        assertEquals("local-recv", merged.chain_key_recv)
        assertEquals(1, merged.recv_message_number)
    }

    @Test
    fun `a state sealed by the current key is still restored`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns sealed_v2(current_master, state("from-phone", 4), 7L, true)

        val restored = RatchetStateSyncer(state_store, ratchet_api).fetch_from_server(conversation_id)

        assertEquals("from-phone", restored?.root_key)
    }

    @Test
    fun `a written state reads back only for the same account`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns null
        val ciphertext = slot<String>()
        val nonce = slot<String>()
        coEvery { ratchet_api.post_state(any(), capture(ciphertext), capture(nonce)) } answers {
            PostStateOutcome.Success(
                RatchetStateResponse("1", "c", ciphertext.captured, nonce.captured, 1),
            )
        }
        RatchetStateSyncer(state_store, ratchet_api, RatchetSyncVault.none, SyncFormatGate.ready)
            .sync(conversation_id, state("written", 6))
        val written = RatchetStateResponse("1", "c", ciphertext.captured, nonce.captured, 1)

        coEvery { ratchet_api.fetch_state(any()) } returns written
        assertEquals("written", RatchetStateSyncer(state_store, ratchet_api).fetch_from_server(conversation_id)?.root_key)

        session_key_store.put_user_id("user-2")
        assertNull(RatchetStateSyncer(state_store, ratchet_api).fetch_from_server(conversation_id))

        session_key_store.put_user_id("user-1")
        val bytes = RatchetCrypto.b64_decode(ciphertext.captured)
        bytes[11] = (bytes[11].toInt() xor 1).toByte()
        coEvery { ratchet_api.fetch_state(any()) } returns written.copy(encrypted_state = RatchetCrypto.b64_encode(bytes))
        assertNull(RatchetStateSyncer(state_store, ratchet_api).fetch_from_server(conversation_id))
        assertNotEquals(ciphertext.captured, RatchetCrypto.b64_encode(bytes))
    }
}
