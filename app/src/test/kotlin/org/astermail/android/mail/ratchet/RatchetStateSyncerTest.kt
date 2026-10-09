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
package org.astermail.android.mail.ratchet

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.api.ratchet.RatchetStateResponse
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RatchetStateSyncerTest {

    private val conversation_id = "conv-sync"
    private val device_key = ByteArray(32) { it.toByte() }
    private val foreign_key = ByteArray(32) { (it + 100).toByte() }

    private val state_store = mockk<RatchetStateStore>(relaxed = true)
    private val ratchet_api = mockk<RatchetApi>(relaxed = true)

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { state_store.derive_state_encryption_key() } answers { device_key.copyOf() }
        every { state_store.state_encryption_key_candidates() } answers { listOf(device_key.copyOf()) }
        coEvery { state_store.sync_floor(any()) } returns 0L
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun sample_state(id: String, root_key: String): RatchetState = RatchetState(
        conversation_id = id,
        dh_keypair = RatchetDhKeyPair(public_key = "pub", secret_key = "sec"),
        root_key = root_key,
        send_message_number = 4,
    )

    private fun server_state(key: ByteArray, bound_id: String, state: RatchetState, version: Int = 3): RatchetStateResponse {
        val nonce = RatchetCrypto.random_bytes(12)
        val plaintext = RatchetStateContainer.encode(bound_id, state, 10L).toByteArray(Charsets.UTF_8)
        val ciphertext = RatchetCrypto.aes_gcm_encrypt(plaintext, key, nonce, null)
        return RatchetStateResponse(
            id = "1",
            conversation_id = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8)),
            encrypted_state = RatchetCrypto.b64_encode(ciphertext),
            state_nonce = RatchetCrypto.b64_encode(nonce),
            state_version = version,
        )
    }

    private fun stored(version: Int): RatchetStateResponse = RatchetStateResponse(
        id = "1",
        conversation_id = "c",
        encrypted_state = "",
        state_nonce = "",
        state_version = version,
    )

    @Test
    fun `a server state no key on this device opens is never overwritten`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns
            server_state(foreign_key, conversation_id, sample_state(conversation_id, "remote"))

        val synced = RatchetStateSyncer(state_store, ratchet_api)
            .sync(conversation_id, sample_state(conversation_id, "local"))

        assertFalse(synced)
        coVerify(exactly = 0) { ratchet_api.put_state(any(), any(), any(), any()) }
        coVerify(exactly = 0) { ratchet_api.post_state(any(), any(), any()) }
    }

    @Test
    fun `a readable server state is merged before it is replaced`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns
            server_state(device_key, conversation_id, sample_state(conversation_id, "remote"))
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns PutStateOutcome.Success(stored(4))

        val synced = RatchetStateSyncer(state_store, ratchet_api)
            .sync(conversation_id, sample_state(conversation_id, "local"))

        assertTrue(synced)
        coVerify(exactly = 1) { state_store.save(any()) }
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), 3) }
    }

    @Test
    fun `a readable state bound to another conversation is replaced`() = runTest {
        coEvery { ratchet_api.fetch_state(any()) } returns
            server_state(device_key, "conv-other", sample_state("conv-other", "remote"))
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns PutStateOutcome.Success(stored(4))

        val synced = RatchetStateSyncer(state_store, ratchet_api)
            .sync(conversation_id, sample_state(conversation_id, "local"))

        assertTrue(synced)
        coVerify(exactly = 0) { state_store.save(any()) }
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), 3) }
    }

    @Test
    fun `a conflict whose newer state cannot be read stops the write`() = runTest {
        val readable = server_state(device_key, conversation_id, sample_state(conversation_id, "remote"))
        val unreadable = server_state(foreign_key, conversation_id, sample_state(conversation_id, "other"), 5)
        coEvery { ratchet_api.fetch_state(any()) } returnsMany listOf(readable, readable, unreadable)
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns PutStateOutcome.VersionConflict

        val synced = RatchetStateSyncer(state_store, ratchet_api)
            .sync(conversation_id, sample_state(conversation_id, "local"))

        assertFalse(synced)
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), any()) }
    }

    @Test
    fun `a failed fetch after a conflict stops the write`() = runTest {
        val readable = server_state(device_key, conversation_id, sample_state(conversation_id, "remote"))
        coEvery { ratchet_api.fetch_state(any()) } returnsMany listOf(readable, readable) andThenThrows RuntimeException("offline")
        coEvery { ratchet_api.put_state(any(), any(), any(), any()) } returns PutStateOutcome.VersionConflict

        val synced = RatchetStateSyncer(state_store, ratchet_api)
            .sync(conversation_id, sample_state(conversation_id, "local"))

        assertFalse(synced)
        coVerify(exactly = 1) { ratchet_api.put_state(any(), any(), any(), any()) }
    }
}
