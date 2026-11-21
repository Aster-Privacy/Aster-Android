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

package org.astermail.android.mail

import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.crypto.storage_rotation.ApiReply
import org.astermail.android.crypto.storage_rotation.ResealApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerSealResealerTest {

    private fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    private val seal = b64("-----BEGIN PGP MESSAGE-----\n\nwcBMA\n-----END PGP MESSAGE-----\n")
    private val v2_marker = Base64.getEncoder().encodeToString(byteArrayOf(2))

    private class RecordingApi(private val replies: ArrayDeque<ApiReply> = ArrayDeque()) : ResealApi {
        val puts = mutableListOf<Pair<String, JsonObject>>()
        override suspend fun get(path: String): ApiReply = ApiReply(500)
        override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(500)
        override suspend fun put(path: String, body: JsonElement): ApiReply {
            puts.add(path to (body as JsonObject))
            return replies.removeFirstOrNull() ?: ApiReply(200)
        }
    }

    private inner class FakeCrypto(
        private val opened: () -> StoredRecord = { StoredRecord.Legacy("{\"subject\":\"s\"}".toByteArray()) },
        private val sealed_nonce: String = v2_marker,
        private val can_seal: Boolean = true,
    ) : MailLegacyCrypto {
        val handed: MutableList<ByteArray> = mutableListOf()
        override fun classify_envelope(encrypted: String, nonce: String): StoredRecord = StoredRecord.Current
        override fun reseal_envelope(plaintext: ByteArray): SealedField? {
            handed.add(plaintext)
            return if (can_seal) SealedField("c2VhbGVk", sealed_nonce) else null
        }
        override fun classify_metadata(encrypted: String, nonce: String): StoredRecord = StoredRecord.Current
        override fun reseal_metadata(plaintext: ByteArray): SealedField? = null
        override fun classify_attachment_meta(encrypted: String, nonce: String): StoredRecord = StoredRecord.Current
        override fun reseal_attachment_meta(plaintext: ByteArray): SealedField? = null
        override fun stored_sha256_hex(encrypted: String): String = "ab".repeat(32)
        override fun classify_server_seal(encrypted: String): StoredRecord = opened()
    }

    @Test
    fun recognizes_only_armored_messages_without_a_nonce() {
        assertTrue(is_server_pgp_seal(seal, null))
        assertTrue(is_server_pgp_seal(seal, ""))
        assertTrue(is_server_pgp_seal(b64("\n  -----BEGIN PGP MESSAGE-----\n"), null))
        assertFalse(is_server_pgp_seal(seal, v2_marker))
        assertFalse(is_server_pgp_seal(b64("-----BEGIN PGP SIGNED MESSAGE-----\n"), null))
        assertFalse(is_server_pgp_seal(b64("{\"subject\":\"s\"}"), null))
        assertFalse(is_server_pgp_seal(null, null))
        assertFalse(is_server_pgp_seal("!!!", null))
    }

    @Test
    fun rewraps_a_server_seal_bound_to_the_bytes_it_opened() = runTest {
        val api = RecordingApi()
        val crypto = FakeCrypto()

        val outcome = ServerSealResealer(api) { crypto }.reseal("item/1", seal)

        assertEquals(LegacyOutcome.WRITTEN, outcome)
        assertEquals(1, api.puts.size)
        val (path, body) = api.puts.single()
        assertEquals("/mail/v1/messages/item%2F1/envelope-rewrap", path)
        assertEquals("c2VhbGVk", body["encrypted_envelope"]!!.jsonPrimitive.content)
        assertEquals(v2_marker, body["envelope_nonce"]!!.jsonPrimitive.content)
        assertEquals("ab".repeat(32), body["expected_envelope_sha256"]!!.jsonPrimitive.content)
        assertArrayEquals(ByteArray(crypto.handed.single().size), crypto.handed.single())
    }

    @Test
    fun rewraps_each_item_once() = runTest {
        val api = RecordingApi()
        val resealer = ServerSealResealer(api) { FakeCrypto() }

        resealer.reseal("a", seal)
        resealer.reseal("a", seal)

        assertEquals(1, api.puts.size)
    }

    @Test
    fun leaves_a_seal_it_cannot_open_alone() = runTest {
        val api = RecordingApi()

        val outcome = ServerSealResealer(api) { FakeCrypto(opened = { StoredRecord.Unreadable }) }.reseal("a", seal)

        assertEquals(LegacyOutcome.SKIPPED, outcome)
        assertTrue(api.puts.isEmpty())
    }

    @Test
    fun never_sends_a_payload_that_is_not_a_server_seal() = runTest {
        val api = RecordingApi()

        ServerSealResealer(api) { FakeCrypto() }.reseal("a", b64("{\"subject\":\"s\"}"))

        assertTrue(api.puts.isEmpty())
    }

    @Test
    fun never_sends_an_unexpected_envelope_format() = runTest {
        val api = RecordingApi()
        val twelve = Base64.getEncoder().encodeToString(ByteArray(12) { 7 })
        val crypto = FakeCrypto(sealed_nonce = "AQ==")

        assertEquals(LegacyOutcome.SKIPPED, ServerSealResealer(api) { crypto }.reseal("a", seal))
        assertEquals(LegacyOutcome.WRITTEN, ServerSealResealer(api) { FakeCrypto(sealed_nonce = twelve) }.reseal("b", seal))
        assertEquals(1, api.puts.size)
    }

    @Test
    fun tries_again_after_a_pause_but_not_after_a_change() = runTest {
        val api = RecordingApi(ArrayDeque(listOf(ApiReply(503), ApiReply(200), ApiReply(409, code = "ENVELOPE_CHANGED"))))
        val resealer = ServerSealResealer(api) { FakeCrypto() }

        assertEquals(LegacyOutcome.PAUSED, resealer.reseal("a", seal))
        assertEquals(LegacyOutcome.WRITTEN, resealer.reseal("a", seal))
        assertEquals(LegacyOutcome.CHANGED, resealer.reseal("b", seal))
        assertEquals(LegacyOutcome.SKIPPED, resealer.reseal("b", seal))
        assertEquals(3, api.puts.size)
    }

    @Test
    fun waits_for_keys_before_sealing() = runTest {
        val api = RecordingApi()
        val resealer = ServerSealResealer(api) { FakeCrypto(can_seal = false) }

        assertEquals(LegacyOutcome.PAUSED, resealer.reseal("a", seal))
        assertTrue(api.puts.isEmpty())
    }
}
