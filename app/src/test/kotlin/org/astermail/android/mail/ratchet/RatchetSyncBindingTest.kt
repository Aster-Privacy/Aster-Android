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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetSyncBindingTest {

    private val conversation_id = "conv-binding"
    private val current_master = ByteArray(32) { (it + 1).toByte() }
    private val retired_master = ByteArray(32) { (it + 42).toByte() }

    private val ratchet_api = mockk<RatchetApi>(relaxed = true)
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var state_store: RatchetStateStore

    private class FakeVault(val floors: RatchetVaultFloors) : RatchetSyncVault {
        val recorded = mutableListOf<Triple<String, Long, Boolean>>()
        val intents = HashMap<String, Long>()
        val dropped = mutableListOf<Pair<String, Long>>()
        override suspend fun read(): RatchetVaultFloors = floors
        override suspend fun record(conversation_id: String, floor: Long, state_v2: Boolean) {
            recorded.add(Triple(conversation_id, floor, state_v2))
            if ((intents[conversation_id] ?: Long.MAX_VALUE) <= floor) intents.remove(conversation_id)
        }
        override suspend fun intend(conversation_id: String, floor: Long) {
            if (floor > (intents[conversation_id] ?: 0L)) intents[conversation_id] = floor
        }
        override suspend fun unconfirmed(): Map<String, Long> = HashMap(intents)
        override suspend fun drop_intent(conversation_id: String, floor: Long) {
            dropped.add(conversation_id to floor)
            if ((intents[conversation_id] ?: Long.MAX_VALUE) <= floor) intents.remove(conversation_id)
        }
    }

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

    private fun compat_key(master: ByteArray): ByteArray =
        RatchetCrypto.hkdf_sha256(
            ikm = master,
            salt = "Aster Mail_Ratchet_State_Encryption".toByteArray(Charsets.UTF_8),
            info = "ratchet_state_key".toByteArray(Charsets.UTF_8),
            length = 32,
        )

    private fun state(root_key: String, epoch: Int, send: Int = 0, conversation: String = conversation_id) = RatchetState(
        conversation_id = conversation,
        dh_keypair = RatchetDhKeyPair(public_key = "pub", secret_key = "sec"),
        root_key = root_key,
        chain_key_send = "send",
        chain_key_recv = "recv",
        send_message_number = send,
        epoch = epoch,
    )

    private fun response(
        ciphertext: ByteArray,
        nonce: ByteArray,
        version: Int = 3,
        conversation: String = conversation_id,
    ) = RatchetStateResponse(
        id = "1",
        conversation_id = RatchetCrypto.b64_encode(conversation.toByteArray(Charsets.UTF_8)),
        encrypted_state = RatchetCrypto.b64_encode(ciphertext),
        state_nonce = RatchetCrypto.b64_encode(nonce),
        state_version = version,
    )

    private fun old_format(
        master: ByteArray,
        sealed: RatchetState,
        sync_version: Long,
        conversation: String = conversation_id,
    ): RatchetStateResponse {
        val nonce = RatchetCrypto.random_bytes(12)
        val plaintext = RatchetStateContainer.encode(conversation, sealed, sync_version).toByteArray(Charsets.UTF_8)
        return response(
            RatchetCrypto.aes_gcm_encrypt(plaintext, compat_key(master), nonce, RatchetStateContainer.bound_aad(conversation)),
            nonce,
            conversation = conversation,
        )
    }

    private fun new_format(sealed: RatchetState, sync_version: Long): RatchetStateResponse {
        val keys = state_store.derive_sync_keys()!!
        val nonce = RatchetCrypto.random_bytes(12)
        val header = RatchetStateContainer.encode_header(RatchetStateHeader(sealed.epoch.toLong(), sync_version))
        val plaintext = RatchetStateContainer.encode(conversation_id, sealed, sync_version).toByteArray(Charsets.UTF_8)
        val ciphertext = RatchetCrypto.aes_gcm_encrypt(
            plaintext,
            keys.state_key,
            nonce,
            RatchetStateContainer.sync_aad(keys.user_id, conversation_id, header),
        )
        return response(header + ciphertext, nonce)
    }

    private class Posted {
        lateinit var ciphertext: String
        lateinit var nonce: String
    }

    private fun capture_post(): Posted {
        val posted = Posted()
        val ciphertext = slot<String>()
        val nonce = slot<String>()
        coEvery { ratchet_api.fetch_state(any()) } returns null
        coEvery { ratchet_api.post_state(any(), capture(ciphertext), capture(nonce)) } answers {
            posted.ciphertext = ciphertext.captured
            posted.nonce = nonce.captured
            PostStateOutcome.Success(RatchetStateResponse("1", "c", ciphertext.captured, nonce.captured, 1))
        }
        return posted
    }

    private fun syncer(vault: RatchetSyncVault, gate: SyncFormatGate) =
        RatchetStateSyncer(state_store, ratchet_api, vault, gate)

    @Test
    fun `an old format state is refused once the vault records the new format`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns old_format(current_master, state("replayed", 4), 7L)

        val before = syncer(FakeVault(RatchetVaultFloors.empty), SyncFormatGate.never).fetch_from_server(conversation_id)
        val after = syncer(FakeVault(RatchetVaultFloors(true, emptyMap())), SyncFormatGate.never)
            .fetch_from_server(conversation_id)

        assertEquals("replayed", before?.root_key)
        assertNull(after)
    }

    @Test
    fun `a fresh device keeps the floor the vault carries`() = runTest {
        val vault = FakeVault(RatchetVaultFloors(true, mapOf(conversation_id to 10L)))

        coEvery { ratchet_api.fetch_state(any()) } returns new_format(state("stale", 4), 7L)
        assertNull(syncer(vault, SyncFormatGate.never).fetch_from_server(conversation_id))

        coEvery { ratchet_api.fetch_state(any()) } returns new_format(state("current", 5), 11L)
        assertEquals("current", syncer(vault, SyncFormatGate.never).fetch_from_server(conversation_id)?.root_key)
    }

    @Test
    fun `an older epoch is refused on a fresh device before the new format is ready`() = runTest {
        val vault = FakeVault(RatchetVaultFloors(false, mapOf(conversation_id to 10L)))

        coEvery { ratchet_api.fetch_state(any()) } returns old_format(current_master, state("rolled-back", 1), 5L)
        assertNull(syncer(vault, SyncFormatGate.never).fetch_from_server(conversation_id))

        coEvery { ratchet_api.fetch_state(any()) } returns old_format(current_master, state("latest", 3), 12L)
        assertEquals("latest", syncer(vault, SyncFormatGate.never).fetch_from_server(conversation_id)?.root_key)
    }

    @Test
    fun `the write version counts up from the vault floor and ignores the clock`() = runTest {
        val posted = capture_post()
        val vault = FakeVault(RatchetVaultFloors(true, mapOf(conversation_id to 5L)))

        assertTrue(syncer(vault, SyncFormatGate.ready).sync(conversation_id, state("local", 2)))

        val payload = RatchetStateContainer.split_payload(RatchetCrypto.b64_decode(posted.ciphertext))
        assertEquals(6L, payload?.header?.sync_version)
        assertEquals(listOf(Triple(conversation_id, 6L, true)), vault.recorded)
    }

    @Test
    fun `a device with a fast clock does not push the version ahead`() = runTest {
        coEvery { state_store.sync_floor(any()) } returns 41L
        val posted = capture_post()

        syncer(FakeVault(RatchetVaultFloors.empty), SyncFormatGate.ready).sync(conversation_id, state("local", 2))

        val payload = RatchetStateContainer.split_payload(RatchetCrypto.b64_decode(posted.ciphertext))
        assertEquals(42L, payload?.header?.sync_version)
    }

    @Test
    fun `before readiness the state is written in the old format`() = runTest {
        val posted = capture_post()
        val vault = FakeVault(RatchetVaultFloors.empty)

        assertTrue(syncer(vault, SyncFormatGate.never).sync(conversation_id, state("local", 2)))

        val bytes = RatchetCrypto.b64_decode(posted.ciphertext)
        assertNull(RatchetStateContainer.split_payload(bytes))
        assertEquals(listOf(Triple(conversation_id, 1L, false)), vault.recorded)
        val plain = RatchetCrypto.aes_gcm_decrypt(
            bytes,
            compat_key(current_master),
            RatchetCrypto.b64_decode(posted.nonce),
            RatchetStateContainer.bound_aad(conversation_id),
        )
        assertNotNull(plain)
    }

    @Test
    fun `an old client state still merges before readiness`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns
            old_format(current_master, state("old-client", 3), 1_700_000_000_000L)

        val restored = syncer(FakeVault(RatchetVaultFloors.empty), SyncFormatGate.never)
            .fetch_from_server(conversation_id)

        assertEquals("old-client", restored?.root_key)
    }

    @Test
    fun `a peer writing under the previous key is merged instead of overwritten`() = runTest {
        val peer = state("shared", 2, send = 9)
        coEvery { ratchet_api.fetch_state(any()) } returns old_format(retired_master, peer, 3L)
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns
            PutStateOutcome.Success(old_format(current_master, peer, 4L).copy(state_version = 4))
        val saved = mutableListOf<RatchetState>()
        coEvery { state_store.save(capture(saved)) } returns Unit

        syncer(FakeVault(RatchetVaultFloors.empty), SyncFormatGate.never)
            .sync(conversation_id, state("shared", 2, send = 4))

        assertEquals(listOf(9), saved.map { it.send_message_number })
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), 3) }
    }

    private class Written {
        val ciphertexts = mutableListOf<String>()
        val conversations = mutableListOf<String>()
        val intents_at_write = mutableListOf<Long?>()
    }

    private fun capture_put(vault: FakeVault, written: Written = Written()): Written {
        val conv = slot<String>()
        val ciphertext = slot<String>()
        coEvery { ratchet_api.put_state(capture(conv), capture(ciphertext), any(), any()) } answers {
            val conversation = String(RatchetCrypto.b64_decode(conv.captured), Charsets.UTF_8)
            written.conversations.add(conversation)
            written.ciphertexts.add(ciphertext.captured)
            written.intents_at_write.add(vault.intents[conversation])
            PutStateOutcome.Success(RatchetStateResponse("1", conv.captured, ciphertext.captured, "n", 4))
        }
        coEvery { ratchet_api.post_state(any(), any(), any()) } returns PostStateOutcome.Failure(500)
        return written
    }

    private fun header_version(ciphertext: String): Long? =
        RatchetStateContainer.split_payload(RatchetCrypto.b64_decode(ciphertext))?.header?.sync_version

    private fun expect_overwritten(remote: RatchetStateResponse) = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns remote
        val saved = mutableListOf<RatchetState>()
        coEvery { state_store.save(capture(saved)) } returns Unit
        val vault = FakeVault(RatchetVaultFloors(true, mapOf(conversation_id to 8L)))
        val written = capture_put(vault)

        val synced = syncer(vault, SyncFormatGate.ready).sync(conversation_id, state("local", 2))

        assertEquals(true, synced)
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), 3) }
        coVerify(exactly = 0) { ratchet_api.post_state(any(), any(), any()) }
        coVerify(exactly = 0) { ratchet_api.delete_state(any()) }
        assertEquals(listOf(9L), written.ciphertexts.map { header_version(it) })
        assertTrue(saved.none { it.root_key == "old-client" })
        assertEquals(listOf(Triple(conversation_id, 9L, true)), vault.recorded)
    }

    @Test
    fun `a latched device overwrites an older format copy with its own state`() =
        expect_overwritten(old_format(current_master, state("old-client", 2), 3L))

    @Test
    fun `a latched device overwrites an older format copy under the previous key with its own state`() =
        expect_overwritten(old_format(retired_master, state("old-client", 2), 3L))

    @Test
    fun `a latched device without local state leaves an older format copy alone and starts fresh`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns old_format(current_master, state("old-client", 2), 3L)
        val saved = mutableListOf<RatchetState>()
        coEvery { state_store.save(capture(saved)) } returns Unit
        val vault = FakeVault(RatchetVaultFloors(true, emptyMap()))
        val written = capture_put(vault)
        val syncer = syncer(vault, SyncFormatGate.ready)

        assertNull(syncer.fetch_from_server(conversation_id))
        assertNull(syncer.fetch_from_server(conversation_id))

        coVerify(exactly = 1) { ratchet_api.fetch_state(any()) }
        coVerify(exactly = 0) { ratchet_api.put_state(any(), any(), any(), any()) }
        coVerify(exactly = 0) { ratchet_api.post_state(any(), any(), any()) }
        coVerify(exactly = 0) { ratchet_api.delete_state(any()) }
        assertTrue(saved.isEmpty())
        assertTrue(vault.recorded.isEmpty())

        assertTrue(syncer.sync(conversation_id, state("fresh", 1)))
        assertEquals(listOf(1L), written.ciphertexts.map { header_version(it) })
        assertEquals(listOf(Triple(conversation_id, 1L, true)), vault.recorded)
    }

    @Test
    fun `every conversation on an older format copy upgrades after the first new format write`() = runTest {
        val conversations = listOf("conv-a", "conv-b", "conv-c")
        coEvery { ratchet_api.fetch_state(any()) } answers {
            val conversation = String(RatchetCrypto.b64_decode(firstArg<String>()), Charsets.UTF_8)
            old_format(current_master, state("old-client", 2, conversation = conversation), 3L, conversation)
        }
        val vault = FakeVault(RatchetVaultFloors(true, mapOf("conv-a" to 2L, "conv-b" to 4L, "conv-c" to 6L)))
        val written = capture_put(vault)
        val syncer = syncer(vault, SyncFormatGate.ready)

        val synced = conversations.map { syncer.sync(it, state("local-$it", 2, conversation = it)) }

        assertEquals(listOf(true, true, true), synced)
        assertEquals(conversations, written.conversations)
        assertEquals(listOf(3L, 5L, 7L), written.ciphertexts.map { header_version(it) })
        assertEquals(
            listOf(Triple("conv-a", 3L, true), Triple("conv-b", 5L, true), Triple("conv-c", 7L, true)),
            vault.recorded,
        )
    }

    @Test
    fun `the write floor is journaled before the server write`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns new_format(state("remote", 2), 8L)
        val vault = FakeVault(RatchetVaultFloors(true, mapOf(conversation_id to 8L)))
        val written = capture_put(vault)

        assertTrue(syncer(vault, SyncFormatGate.ready).sync(conversation_id, state("remote", 2, send = 3)))

        assertEquals(listOf<Long?>(9L), written.intents_at_write)
    }

    @Test
    fun `an unconfirmed write the server holds is recorded in the vault`() = runTest {
        val vault = FakeVault(RatchetVaultFloors(true, emptyMap()))
        vault.intents[conversation_id] = 9L
        coEvery { ratchet_api.fetch_state(any()) } returns new_format(state("landed", 2), 9L)

        syncer(vault, SyncFormatGate.ready).settle_intents()

        assertEquals(listOf(Triple(conversation_id, 9L, false)), vault.recorded)
    }

    @Test
    fun `an unconfirmed write the server does not hold is dropped without a floor`() = runTest {
        val vault = FakeVault(RatchetVaultFloors(true, emptyMap()))
        vault.intents[conversation_id] = 9L
        coEvery { ratchet_api.fetch_state(any()) } returns new_format(state("older", 2), 8L)

        syncer(vault, SyncFormatGate.ready).settle_intents()

        assertTrue(vault.recorded.isEmpty())
        assertEquals(listOf(conversation_id to 9L), vault.dropped)
    }

    @Test
    fun `an unconfirmed write is kept while the server cannot be reached`() = runTest {
        val vault = FakeVault(RatchetVaultFloors(true, emptyMap()))
        vault.intents[conversation_id] = 9L
        coEvery { ratchet_api.fetch_state(any()) } throws java.io.IOException("offline")

        syncer(vault, SyncFormatGate.ready).settle_intents()

        assertTrue(vault.recorded.isEmpty())
        assertTrue(vault.dropped.isEmpty())
        assertEquals(mapOf(conversation_id to 9L), vault.intents)
    }
}
