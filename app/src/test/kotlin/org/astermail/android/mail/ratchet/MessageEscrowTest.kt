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
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.ratchet.PlaintextEscrowEntry
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.StorageWriteKey
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MessageEscrowTest {

    private val base_key_b64 = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA="
    private val escrow_key_b64 = "3Ola430IlLF43/ftx0FE6MT4ZsUe/m+X4ISq/It5sQQ="
    private val dedupe_key = "73e9433f-67d5-4b28-9b2f-51bc53819c5d:BAbCdEf0123456789xyz=:7"
    private val plaintext = "Hello from the escrow vector. Ünïcödé ✓"
    private val nonce_b64 = "oKGio6Slpqeoqaqr"
    private val ciphertext_b64 =
        "y8cIU6Ssg4bk6eKQohqECUVi3WGoxw1aVg90oJANtQtVDCIb47SoqbnYgDjlvPXMubLJmBc6RYV2jDDKDg=="

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun teardown() {
        org.astermail.android.api.keys.BoundWriteSwitches.clear()
        unmockkStatic(Base64::class)
    }

    private fun b64(value: String): ByteArray = java.util.Base64.getDecoder().decode(value)

    @Test
    fun `the escrow key matches the interop vector`() {
        assertArrayEquals(b64(escrow_key_b64), MessageEscrow.derive_escrow_key(b64(base_key_b64)))
    }

    @Test
    fun `the vector ciphertext opens to the vector plaintext`() {
        val opened = MessageEscrow.open(b64(ciphertext_b64), b64(escrow_key_b64), b64(nonce_b64), dedupe_key)
        assertEquals(plaintext, opened)
    }

    @Test
    fun `sealing with the vector nonce reproduces the vector ciphertext`() {
        val sealed = MessageEscrow.seal(
            plaintext.toByteArray(Charsets.UTF_8),
            b64(escrow_key_b64),
            b64(nonce_b64),
            dedupe_key,
        )
        assertEquals(ciphertext_b64, java.util.Base64.getEncoder().encodeToString(sealed))
    }

    @Test
    fun `a different dedupe key fails to open`() {
        val opened = MessageEscrow.open(
            b64(ciphertext_b64),
            b64(escrow_key_b64),
            b64(nonce_b64),
            "73e9433f-67d5-4b28-9b2f-51bc53819c5d:BAbCdEf0123456789xyz=:8",
        )
        assertNull(opened)
    }

    @Test
    fun `the dedupe key joins the message id and the header`() {
        val header = MessageHeader(dh_public = "BAbCdEf0123456789xyz=", previous_chain_length = 0, message_number = 7)
        assertEquals(dedupe_key, MessageEscrow.dedupe_key("73e9433f-67d5-4b28-9b2f-51bc53819c5d", header))
    }

    @Test
    fun `fetch opens the vector with any master key candidate`() = runTest {
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers {
            listOf(ByteArray(32) { 9 }, b64(base_key_b64))
        }
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(dedupe_key) } returns PlaintextEscrowEntry(
            message_id = dedupe_key,
            encrypted_plaintext = ciphertext_b64,
            plaintext_nonce = nonce_b64,
        )

        assertEquals(plaintext, MessageEscrow(state_store, api).fetch(dedupe_key))
    }

    private fun constant_store(): RatchetStateStore {
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(constant.copyOf(), b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        return state_store
    }

    @Test
    fun `fetch refuses a row sealed under a public mail constant`() = runTest {
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val nonce = ByteArray(12) { 3 }
        val sealed = org.astermail.android.crypto.ratchet.RatchetCrypto.aes_gcm_encrypt(
            "planted".toByteArray(Charsets.UTF_8),
            MessageEscrow.derive_escrow_key(constant),
            nonce,
            MessageEscrow.aad_for(dedupe_key),
        )
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(dedupe_key) } returns PlaintextEscrowEntry(
            message_id = dedupe_key,
            encrypted_plaintext = java.util.Base64.getEncoder().encodeToString(sealed),
            plaintext_nonce = java.util.Base64.getEncoder().encodeToString(nonce),
        )

        assertNull(MessageEscrow(constant_store(), api).fetch(dedupe_key))
    }

    @Test
    fun `fetch_bound refuses a bound row sealed under a public mail constant`() = runTest {
        val constant = StorageWriteKey.public_mail_kek_raws().first()
        val nonce = ByteArray(12) { 4 }
        val sealed = org.astermail.android.crypto.ratchet.RatchetCrypto.aes_gcm_encrypt(
            MessageEscrow.encode_bound_payload("peer@astermail.org", "conv_1", "planted", false),
            MessageEscrow.derive_escrow_key(constant),
            nonce,
            MessageEscrow.bound_aad_for(dedupe_key),
        )
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(MessageEscrow.bound_key(dedupe_key)) } returns PlaintextEscrowEntry(
            message_id = MessageEscrow.bound_key(dedupe_key),
            encrypted_plaintext = java.util.Base64.getEncoder().encodeToString(sealed),
            plaintext_nonce = java.util.Base64.getEncoder().encodeToString(nonce),
        )
        coEvery { api.fetch_plaintext_escrow(dedupe_key) } returns null

        assertNull(MessageEscrow(constant_store(), api).fetch_bound(dedupe_key, "peer@astermail.org", "conv_1"))
    }

    @Test
    fun `fetch returns null when the server has no entry`() = runTest {
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(any()) } returns null

        assertNull(MessageEscrow(state_store, api).fetch(dedupe_key))
    }

    @Test
    fun `upload skips plaintext above the size limit`() = runTest {
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        coEvery { api.upload_plaintext_escrow(any()) } returns true

        val oversized = "a".repeat(MessageEscrow.max_plaintext_bytes + 1)
        val escrow = MessageEscrow(state_store, api)
        assertFalse(escrow.upload_bound(dedupe_key, oversized, "peer@astermail.org", "conv_1", false))
        coVerify(exactly = 0) { api.upload_plaintext_escrow(any()) }
    }

    @Test
    fun `a confirmed bound header has no unconfirmed field and round trips`() {
        val bytes = MessageEscrow.encode_bound_payload("Peer@Astermail.org", "conv_1", "body\nline two", false)
        assertEquals("{\"s\":\"peer@astermail.org\",\"c\":\"conv_1\"}\nbody\nline two", String(bytes, Charsets.UTF_8))
        val decoded = MessageEscrow.decode_bound_payload(bytes)
        assertEquals(BoundEscrowPayload("peer@astermail.org", "conv_1", "body\nline two", false), decoded)
    }

    @Test
    fun `an unconfirmed bound header carries u and round trips`() {
        val bytes = MessageEscrow.encode_bound_payload("peer@astermail.org", "conv_1", "body", true)
        assertEquals("{\"s\":\"peer@astermail.org\",\"c\":\"conv_1\",\"u\":1}\nbody", String(bytes, Charsets.UTF_8))
        val decoded = MessageEscrow.decode_bound_payload(bytes)
        assertEquals(BoundEscrowPayload("peer@astermail.org", "conv_1", "body", true), decoded)
    }

    @Test
    fun `only a numeric u of one marks the sender unconfirmed`() {
        fun decode(header: String) =
            MessageEscrow.decode_bound_payload("$header\nbody".toByteArray(Charsets.UTF_8))
        assertEquals(false, decode("{\"s\":\"a\",\"c\":\"b\",\"u\":\"1\"}")?.unconfirmed_sender)
        assertEquals(false, decode("{\"s\":\"a\",\"c\":\"b\",\"u\":0}")?.unconfirmed_sender)
        assertEquals(true, decode("{\"s\":\"a\",\"c\":\"b\",\"u\":1}")?.unconfirmed_sender)
        assertEquals(false, decode("{\"s\":\"a\",\"c\":\"b\"}")?.unconfirmed_sender)
        assertEquals(false, decode("{\"s\":\"a\",\"c\":\"b\",\"k\":1}")?.unconfirmed_sender)
        assertEquals(true, decode("{\"s\":\"a\",\"c\":\"b\",\"k\":1,\"u\":1}")?.unconfirmed_sender)
        assertNull(decode("{\"s\":\"a\"}"))
        assertNull(MessageEscrow.decode_bound_payload("no header".toByteArray(Charsets.UTF_8)))
    }

    private fun bound_entry(
        sender: String,
        conversation_id: String,
        body: String,
        unconfirmed_sender: Boolean,
    ): PlaintextEscrowEntry {
        val nonce = ByteArray(12) { 7 }
        val sealed = org.astermail.android.crypto.ratchet.RatchetCrypto.aes_gcm_encrypt(
            MessageEscrow.encode_bound_payload(sender, conversation_id, body, unconfirmed_sender),
            b64(escrow_key_b64),
            nonce,
            MessageEscrow.bound_aad_for(dedupe_key),
        )
        return PlaintextEscrowEntry(
            message_id = MessageEscrow.bound_key(dedupe_key),
            encrypted_plaintext = java.util.Base64.getEncoder().encodeToString(sealed),
            plaintext_nonce = java.util.Base64.getEncoder().encodeToString(nonce),
        )
    }

    @Test
    fun `fetch_bound returns the copy flag when the binding matches`() = runTest {
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(MessageEscrow.bound_key(dedupe_key)) } returns
            bound_entry("peer@astermail.org", "conv_1", "bound body", true)

        val fetched = MessageEscrow(state_store, api).fetch_bound(dedupe_key, "Peer@astermail.org", "conv_1")

        assertEquals(EscrowedPlaintext("bound body", verified = true, unconfirmed_sender = true), fetched)
    }

    @Test
    fun `fetch_bound rejects a copy bound to another conversation`() = runTest {
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(MessageEscrow.bound_key(dedupe_key)) } returns
            bound_entry("peer@astermail.org", "conv_other", "bound body", false)

        assertNull(MessageEscrow(state_store, api).fetch_bound(dedupe_key, "peer@astermail.org", "conv_1"))
    }

    @Test
    fun `fetch_bound falls back to the legacy row as unconfirmed`() = runTest {
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        coEvery { api.fetch_plaintext_escrow(MessageEscrow.bound_key(dedupe_key)) } returns null
        coEvery { api.fetch_plaintext_escrow(dedupe_key) } returns PlaintextEscrowEntry(
            message_id = dedupe_key,
            encrypted_plaintext = ciphertext_b64,
            plaintext_nonce = nonce_b64,
        )

        val fetched = MessageEscrow(state_store, api).fetch_bound(dedupe_key, "peer@astermail.org", "conv_1")

        assertEquals(EscrowedPlaintext(plaintext, verified = false, unconfirmed_sender = true), fetched)
    }

    @Test
    fun `upload_bound keeps writing the unbound row until the server retires it`() = runTest {
        org.astermail.android.api.keys.BoundWriteSwitches.record("{\"bound_escrow_writes\":false}")
        val state_store = mockk<RatchetStateStore>()
        every { state_store.note_master_key_use(any()) } returns Unit
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        val uploaded = mutableListOf<PlaintextEscrowEntry>()
        coEvery { api.upload_plaintext_escrow(capture(uploaded)) } returns true

        assertTrue(MessageEscrow(state_store, api).upload_bound(dedupe_key, "body", "peer@astermail.org", "conv_1", true))

        assertEquals(
            listOf(MessageEscrow.bound_key(dedupe_key), dedupe_key),
            uploaded.map { it.message_id },
        )
        val unbound = uploaded[1]
        assertEquals(
            "body",
            MessageEscrow.open(
                b64(unbound.encrypted_plaintext),
                b64(escrow_key_b64),
                b64(unbound.plaintext_nonce),
                dedupe_key,
            ),
        )
    }

    @Test
    fun `upload_bound writes only the sender bound row`() = runTest {
        org.astermail.android.api.keys.BoundWriteSwitches.record("{\"bound_escrow_writes\":true}")
        val state_store = mockk<RatchetStateStore>()
        every { state_store.master_key_candidates() } answers { listOf(b64(base_key_b64)) }
        every { state_store.write_master_key() } answers { b64(base_key_b64) }
        val api = mockk<RatchetApi>()
        val uploaded = mutableListOf<PlaintextEscrowEntry>()
        coEvery { api.upload_plaintext_escrow(capture(uploaded)) } returns true

        assertTrue(MessageEscrow(state_store, api).upload_bound(dedupe_key, "body", "peer@astermail.org", "conv_1", true))

        assertEquals(listOf(MessageEscrow.bound_key(dedupe_key)), uploaded.map { it.message_id })
        val bound = uploaded.first()
        val opened = org.astermail.android.crypto.ratchet.RatchetCrypto.aes_gcm_decrypt(
            b64(bound.encrypted_plaintext),
            b64(escrow_key_b64),
            b64(bound.plaintext_nonce),
            MessageEscrow.bound_aad_for(dedupe_key),
        )
        assertEquals(
            BoundEscrowPayload("peer@astermail.org", "conv_1", "body", true),
            MessageEscrow.decode_bound_payload(opened),
        )
    }
}
