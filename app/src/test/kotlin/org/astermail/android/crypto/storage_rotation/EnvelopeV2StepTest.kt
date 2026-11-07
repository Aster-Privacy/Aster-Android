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

package org.astermail.android.crypto.storage_rotation

import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.mail.LevelTwoEnvelope
import org.astermail.android.mail.SentMailResealCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeV2StepTest {

    private val v1_tag: Byte = 1
    private val v2_tag: Byte = 2

    private fun unb64(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun sealed(tag: Byte, text: String): String = b64(byteArrayOf(tag) + text.toByteArray(Charsets.UTF_8))

    private inner class TaggedCrypto : EnvelopeV2Crypto {
        override fun is_v2(sealed_b64: String): Boolean = unb64(sealed_b64).firstOrNull() == v2_tag

        override fun open_v1(sealed_b64: String): ByteArray? {
            val raw = unb64(sealed_b64)
            if (raw.firstOrNull() != v1_tag) return null
            return raw.copyOfRange(1, raw.size)
        }

        override fun seal_v2(plaintext: ByteArray): String? = b64(byteArrayOf(v2_tag) + plaintext)
    }

    private class Row(var envelope: String, var nonce: String)

    companion object {
        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun sha256_hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private class SentServer(val page_size: Int = 2) : ResealApi {
        val rows = LinkedHashMap<String, Row>()
        val puts = mutableListOf<String>()
        val posts = mutableListOf<String>()
        var mutate_before_put: String? = null
        var put_status: Int? = null
        val bodies = mutableListOf<JsonObject>()
        var admission: ApiReply = ApiReply(200, JsonObject(mapOf("level2_admitted" to JsonPrimitive(true))))

        override suspend fun get(path: String): ApiReply {
            if (path.startsWith("$SENT_MESSAGES_PATH?")) {
                val cursor = Regex("cursor=([^&]+)").find(path)?.groupValues?.get(1)?.toInt() ?: 0
                val ids = rows.keys.toList()
                val page = ids.drop(cursor).take(page_size)
                val next = cursor + page.size
                val items = page.map { id ->
                    val row = rows.getValue(id)
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(id),
                            "encrypted_envelope" to JsonPrimitive(row.envelope),
                            "envelope_nonce" to JsonPrimitive(row.nonce),
                            "has_attachments" to JsonPrimitive(false),
                        ),
                    )
                }
                return ApiReply(
                    200,
                    JsonObject(
                        mapOf(
                            "items" to JsonArray(items),
                            "has_more" to JsonPrimitive(next < ids.size),
                            "next_cursor" to JsonPrimitive(next.toString()),
                        ),
                    ),
                )
            }
            val id = path.removePrefix("$SENT_MESSAGES_PATH/")
            val row = rows[id] ?: return ApiReply(404)
            return ApiReply(
                200,
                JsonObject(
                    mapOf(
                        "encrypted_envelope" to JsonPrimitive(row.envelope),
                        "envelope_nonce" to JsonPrimitive(row.nonce),
                    ),
                ),
            )
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply {
            posts.add(path)
            if (path == ENVELOPE_V2_PATH) return admission
            return ApiReply(404)
        }

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val id = path.removePrefix("$SENT_MESSAGES_PATH/")
            val record = body as JsonObject
            bodies.add(record)
            put_status?.let { return ApiReply(it) }
            val row = rows[id] ?: return ApiReply(404)
            if (mutate_before_put == id) row.envelope = b64(byteArrayOf(1, 1, 1))
            val expected = record["expected_envelope_sha256"]?.jsonPrimitive?.contentOrNull
                ?: return ApiReply(400, code = "VALIDATION_ERROR")
            if (expected != sha256_hex(Base64.getDecoder().decode(row.envelope))) {
                return ApiReply(409, code = ENVELOPE_CHANGED_CODE)
            }
            row.envelope = record.getValue("encrypted_envelope").jsonPrimitive.contentOrNull!!
            row.nonce = record.getValue("envelope_nonce").jsonPrimitive.contentOrNull!!
            puts.add(id)
            return ApiReply(200)
        }
    }

    private fun view(): VaultKeyView = VaultKeyView(
        data_kek = b64(ByteArray(32) { (it + 7).toByte() }),
        data_kek_random = true,
        storage_rekey_state = null,
        prior_keys = emptyList(),
        legacy_keks = emptyList(),
    )

    private fun sweep(server: SentServer, store: ProgressStore, max_items: Int = ENVELOPE_V2_MAX_ITEMS_PER_RUN) =
        runBlocking {
            run_envelope_v2_sweep(view(), server, store, TaggedCrypto(), sleep = {}, max_items = max_items)
        }

    @Test
    fun password_envelopes_move_to_level_two_at_the_same_size() {
        val server = SentServer()
        val v1_nonce = SentMailResealCrypto.sentinel_nonce_b64()
        server.rows["a"] = Row(sealed(v1_tag, "first"), v1_nonce)
        server.rows["b"] = Row(sealed(v2_tag, "second"), LevelTwoEnvelope.marker_b64())
        server.rows["c"] = Row(sealed(v1_tag, "third"), b64(ByteArray(12) { 9 }))
        server.rows["d"] = Row(sealed(v1_tag, "fourth"), v1_nonce)
        val before = server.rows.mapValues { unb64(it.value.envelope).size }

        val result = sweep(server, MemoryProgressStore())

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(listOf("a", "d"), server.puts)
        assertEquals(2, result.counts.resealed)
        assertEquals(1, result.counts.current)
        for (id in listOf("a", "d")) {
            val row = server.rows.getValue(id)
            assertTrue(LevelTwoEnvelope.is_marker_b64(row.nonce))
            assertEquals(v2_tag, unb64(row.envelope)[0])
            assertEquals(before.getValue(id), unb64(row.envelope).size)
        }
        assertEquals(v1_tag, unb64(server.rows.getValue("c").envelope)[0])
        assertFalse(LevelTwoEnvelope.is_marker_b64(server.rows.getValue("c").nonce))
    }

    @Test
    fun a_second_sweep_writes_nothing() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        sweep(server, MemoryProgressStore())
        server.puts.clear()

        val again = sweep(server, MemoryProgressStore())

        assertEquals(ResealRunStatus.DONE, again.status)
        assertTrue(server.puts.isEmpty())
        assertEquals(1, again.counts.current)
    }

    @Test
    fun a_row_changed_by_another_device_is_left_alone() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.mutate_before_put = "a"
        val store = MemoryProgressStore()

        val result = sweep(server, store)

        assertEquals(ResealRunStatus.DONE, result.status)
        assertTrue(server.puts.isEmpty())
        assertEquals(1, result.counts.conflicts)
        assertTrue(SentMailResealCrypto.is_sentinel_nonce(server.rows.getValue("a").nonce))
        assertFalse(store.load()?.steps?.get(ENVELOPE_V2_STEP)?.done == true)

        server.mutate_before_put = null
        server.rows.getValue("a").envelope = sealed(v1_tag, "edited")
        val retry = sweep(server, store)

        assertEquals(ResealRunStatus.DONE, retry.status)
        assertEquals(listOf("a"), server.puts)
        assertTrue(LevelTwoEnvelope.is_marker_b64(server.rows.getValue("a").nonce))
    }

    @Test
    fun the_rekey_request_carries_the_hash_of_the_stored_bytes() {
        val server = SentServer()
        val stored = sealed(v1_tag, "first")
        server.rows["a"] = Row(stored, SentMailResealCrypto.sentinel_nonce_b64())

        sweep(server, MemoryProgressStore())

        val body = server.bodies.single()
        assertEquals(
            sha256_hex(Base64.getDecoder().decode(stored)),
            body.getValue("expected_envelope_sha256").jsonPrimitive.contentOrNull,
        )
        assertEquals(LevelTwoEnvelope.marker_b64(), body.getValue("envelope_nonce").jsonPrimitive.contentOrNull)
        assertEquals(64, body.getValue("expected_envelope_sha256").jsonPrimitive.contentOrNull!!.length)
        assertEquals(
            body.getValue("expected_envelope_sha256").jsonPrimitive.contentOrNull,
            LevelTwoEnvelope.stored_sha256_hex(stored),
        )
    }

    @Test
    fun a_missing_item_is_skipped() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.put_status = 404

        val result = sweep(server, MemoryProgressStore())

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(1, result.counts.skipped)
        assertEquals(0, result.counts.resealed)
    }

    @Test
    fun an_unavailable_server_stops_the_run_for_later() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.rows["b"] = Row(sealed(v1_tag, "second"), SentMailResealCrypto.sentinel_nonce_b64())
        server.put_status = 503
        val store = MemoryProgressStore()

        val result = sweep(server, store)

        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals(1, server.bodies.size)

        server.put_status = null
        assertEquals(ResealRunStatus.DONE, sweep(server, store).status)
        assertEquals(listOf("a", "b"), server.puts)
    }

    @Test
    fun a_bounded_sweep_stops_and_resumes_where_it_left_off() {
        val server = SentServer(page_size = 2)
        for (id in listOf("a", "b", "c")) {
            server.rows[id] = Row(sealed(v1_tag, "text $id"), SentMailResealCrypto.sentinel_nonce_b64())
        }
        val store = MemoryProgressStore()

        val first = sweep(server, store, max_items = 1)
        assertEquals(ResealRunStatus.INTERRUPTED, first.status)
        assertEquals(listOf("a"), server.puts)

        val second = sweep(server, store, max_items = 1)
        assertEquals(ResealRunStatus.INTERRUPTED, second.status)
        assertEquals(listOf("a", "b"), server.puts)
        assertEquals("2", store.load()?.steps?.get(ENVELOPE_V2_STEP)?.cursor)

        val third = sweep(server, store, max_items = 1)
        assertEquals(ResealRunStatus.DONE, third.status)
        assertEquals(listOf("a", "b", "c"), server.puts)
        assertTrue(store.load()?.steps?.get(ENVELOPE_V2_STEP)?.done == true)

        server.puts.clear()
        assertEquals(ResealRunStatus.DONE, sweep(server, store).status)
        assertTrue(server.puts.isEmpty())
    }

    @Test
    fun an_unreadable_envelope_is_counted_and_kept() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(9, "foreign"), SentMailResealCrypto.sentinel_nonce_b64())

        val result = sweep(server, MemoryProgressStore())

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(1, result.counts.unreadable)
        assertTrue(server.puts.isEmpty())
    }

    @Test
    fun admission_needs_an_explicit_yes_from_the_server() = runBlocking {
        val server = SentServer()
        assertTrue(request_level2_admission(server))
        assertEquals(listOf(ENVELOPE_V2_PATH), server.posts)

        server.admission = ApiReply(409, code = "VAULT_LEVEL2_NOT_ADMITTED")
        assertFalse(request_level2_admission(server))

        server.admission = ApiReply(200, JsonObject(mapOf("level2_admitted" to JsonPrimitive(false))))
        assertFalse(request_level2_admission(server))

        server.admission = ApiReply(null)
        assertFalse(request_level2_admission(server))
    }

    @Test
    fun level_two_envelopes_open_with_any_known_key() {
        val current = ByteArray(32) { 3 }
        val legacy = ByteArray(32) { 5 }
        val other = ByteArray(32) { 8 }
        val text = "subject and body".toByteArray(Charsets.UTF_8)
        val sealed_current = LevelTwoEnvelope.seal_b64(text, current)
        val sealed_legacy = LevelTwoEnvelope.seal_b64(text, legacy)

        assertArrayEquals(text, LevelTwoEnvelope.open_b64(sealed_current, listOf(current.copyOf(), legacy.copyOf())))
        assertArrayEquals(text, LevelTwoEnvelope.open_b64(sealed_legacy, listOf(current.copyOf(), legacy.copyOf())))
        assertNull(LevelTwoEnvelope.open_b64(sealed_current, listOf(other)))
        assertEquals(16 + 12 + text.size + 16, unb64(sealed_current).size)
        assertTrue(LevelTwoEnvelope.is_marker_b64("Ag=="))
        assertFalse(LevelTwoEnvelope.is_marker_b64(SentMailResealCrypto.sentinel_nonce_b64()))
    }
}
