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

    private class Row(var envelope: String, var nonce: String, val trashed: Boolean = false, val spam: Boolean = false)

    companion object {
        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun sha256_hex(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }

    private class SentServer(val page_size: Int = 2) : ResealApi {
        val rows = LinkedHashMap<String, Row>()
        val drafts = LinkedHashMap<String, Row>()
        val received = LinkedHashMap<String, Row>()
        val listed_types = mutableListOf<String>()
        val puts = mutableListOf<String>()
        val posts = mutableListOf<String>()
        var mutate_before_put: String? = null
        var put_status: Int? = null
        var put_reply: ApiReply? = null
        val bodies = mutableListOf<JsonObject>()
        var admission: ApiReply = ApiReply(200, JsonObject(mapOf("level2_admitted" to JsonPrimitive(true))))

        override suspend fun get(path: String): ApiReply {
            if (path.startsWith("$SENT_MESSAGES_PATH?")) {
                val cursor = Regex("cursor=([^&]+)").find(path)?.groupValues?.get(1)?.toInt() ?: 0
                val item_type = Regex("item_type=([^&]+)").find(path)?.groupValues?.get(1)
                val everything = rows.map { Triple(it.key, it.value, SENT_ITEM_TYPE) } +
                    drafts.map { Triple(it.key, it.value, DRAFT_ITEM_TYPE) } +
                    received.map { Triple(it.key, it.value, "received") }
                val (label, ids) = when {
                    item_type == SENT_ITEM_TYPE ->
                        SENT_ITEM_TYPE to everything.filter { it.third == SENT_ITEM_TYPE && !it.second.trashed && !it.second.spam }
                    item_type == DRAFT_ITEM_TYPE ->
                        DRAFT_ITEM_TYPE to everything.filter { it.third == DRAFT_ITEM_TYPE && !it.second.trashed }
                    path.contains("is_trashed=true") -> "trash" to everything.filter { it.second.trashed }
                    path.contains("is_spam=true") -> "spam" to everything.filter { it.second.spam && !it.second.trashed }
                    else -> "unknown" to emptyList()
                }
                listed_types.add(label)
                val page = ids.drop(cursor).take(page_size)
                val next = cursor + page.size
                val items = page.map { (id, row, type) ->
                    JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(id),
                            "item_type" to JsonPrimitive(type),
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
            val row = rows[id] ?: drafts[id] ?: return ApiReply(404)
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
            put_reply?.let { return it }
            val row = rows[id] ?: drafts[id] ?: return ApiReply(404)
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

    private fun sweep(
        server: SentServer,
        store: ProgressStore,
        max_items: Int = ENVELOPE_V2_MAX_ITEMS_PER_RUN,
        draft_in_use: suspend (String) -> Boolean = { false },
        now: Long = 0L,
        on_not_admitted: () -> Unit = {},
    ) = runBlocking {
        run_envelope_v2_sweep(
            view(),
            server,
            store,
            TaggedCrypto(),
            sleep = {},
            max_items = max_items,
            draft_in_use = draft_in_use,
            now_ms = { now },
            on_not_admitted = on_not_admitted,
        )
    }

    @Test
    fun drafts_move_to_level_two_with_the_stored_hash() {
        val server = SentServer()
        val stored = sealed(v1_tag, "draft body")
        server.drafts["d1"] = Row(stored, SentMailResealCrypto.sentinel_nonce_b64())
        server.drafts["d2"] = Row(sealed(v2_tag, "already"), LevelTwoEnvelope.marker_b64())
        val store = MemoryProgressStore()

        val result = sweep(server, store)

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(listOf(SENT_ITEM_TYPE, "trash", "spam", DRAFT_ITEM_TYPE, "trash"), server.listed_types)
        assertEquals(listOf("d1"), server.puts)
        val row = server.drafts.getValue("d1")
        assertTrue(LevelTwoEnvelope.is_marker_b64(row.nonce))
        assertEquals(unb64(stored).size, unb64(row.envelope).size)
        assertEquals(
            sha256_hex(unb64(stored)),
            server.bodies.single().getValue("expected_envelope_sha256").jsonPrimitive.contentOrNull,
        )
        assertTrue(store.load()?.steps?.get(ENVELOPE_V2_DRAFT_STEP)?.done == true)
        assertEquals(1, result.counts.current)
    }

    @Test
    fun trashed_and_spam_items_move_to_level_two_with_their_stored_hash() {
        val server = SentServer()
        val nonce = SentMailResealCrypto.sentinel_nonce_b64()
        val sent_in_trash = sealed(v1_tag, "sent in trash")
        val sent_in_spam = sealed(v1_tag, "sent in spam")
        val draft_in_trash = sealed(v1_tag, "draft in trash")
        val received_in_trash = sealed(v1_tag, "received in trash")
        server.rows["s_trash"] = Row(sent_in_trash, nonce, trashed = true)
        server.rows["s_spam"] = Row(sent_in_spam, nonce, spam = true)
        server.rows["s_archived"] = Row(sealed(v1_tag, "archived"), nonce)
        server.drafts["d_trash"] = Row(draft_in_trash, nonce, trashed = true)
        server.received["r_trash"] = Row(received_in_trash, nonce, trashed = true)
        val store = MemoryProgressStore()

        val result = sweep(server, store)

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(setOf("s_trash", "s_spam", "s_archived", "d_trash"), server.puts.toSet())
        assertEquals(4, server.puts.size)
        val hashes = server.bodies.map { it.getValue("expected_envelope_sha256").jsonPrimitive.contentOrNull }
        for (stored in listOf(sent_in_trash, sent_in_spam, draft_in_trash)) {
            assertTrue(hashes.contains(sha256_hex(unb64(stored))))
        }
        for (id in listOf("s_trash", "s_spam", "s_archived")) {
            assertTrue(LevelTwoEnvelope.is_marker_b64(server.rows.getValue(id).nonce))
        }
        assertTrue(LevelTwoEnvelope.is_marker_b64(server.drafts.getValue("d_trash").nonce))
        assertEquals(received_in_trash, server.received.getValue("r_trash").envelope)
        assertEquals(unb64(draft_in_trash).size, unb64(server.drafts.getValue("d_trash").envelope).size)
        for (step in listOf(
            ENVELOPE_V2_STEP,
            ENVELOPE_V2_STEP + "_trash",
            ENVELOPE_V2_STEP + "_spam",
            ENVELOPE_V2_DRAFT_STEP,
            ENVELOPE_V2_DRAFT_STEP + "_trash",
        )) {
            assertTrue(step, store.load()?.steps?.get(step)?.done == true)
        }

        server.puts.clear()
        assertEquals(ResealRunStatus.DONE, sweep(server, MemoryProgressStore()).status)
        assertTrue(server.puts.isEmpty())
    }

    @Test
    fun a_draft_open_or_pending_on_this_device_is_left_for_a_later_pass() {
        val server = SentServer()
        server.drafts["open"] = Row(sealed(v1_tag, "being edited"), SentMailResealCrypto.sentinel_nonce_b64())
        server.drafts["idle"] = Row(sealed(v1_tag, "untouched"), SentMailResealCrypto.sentinel_nonce_b64())
        val store = MemoryProgressStore()

        val busy = sweep(server, store, draft_in_use = { it == "open" })

        assertEquals(ResealRunStatus.DONE, busy.status)
        assertEquals(listOf("idle"), server.puts)
        assertTrue(SentMailResealCrypto.is_sentinel_nonce(server.drafts.getValue("open").nonce))
        assertFalse(store.load()?.steps?.get(ENVELOPE_V2_DRAFT_STEP)?.done == true)
        assertEquals(1, busy.counts.skipped)

        val early = sweep(server, store, draft_in_use = { false }, now = ENVELOPE_V2_FIRST_RETRY_MS - 1)
        assertEquals(ENVELOPE_V2_NOT_DUE_REASON, early.reason)
        assertEquals(listOf("idle"), server.puts)

        val later = sweep(server, store, draft_in_use = { false }, now = ENVELOPE_V2_FIRST_RETRY_MS)

        assertEquals(ResealRunStatus.DONE, later.status)
        assertEquals(listOf("idle", "open"), server.puts)
        assertTrue(LevelTwoEnvelope.is_marker_b64(server.drafts.getValue("open").nonce))
        assertTrue(store.load()?.steps?.get(ENVELOPE_V2_DRAFT_STEP)?.done == true)
    }

    @Test
    fun an_unknown_draft_state_counts_as_in_use() {
        val server = SentServer()
        server.drafts["d1"] = Row(sealed(v1_tag, "draft"), SentMailResealCrypto.sentinel_nonce_b64())

        val result = sweep(server, MemoryProgressStore(), draft_in_use = { throw IllegalStateException("db") })

        assertEquals(ResealRunStatus.DONE, result.status)
        assertTrue(server.puts.isEmpty())
        assertTrue(server.bodies.isEmpty())
    }

    @Test
    fun a_changed_draft_is_skipped_and_retried_next_pass() {
        val server = SentServer()
        server.drafts["d1"] = Row(sealed(v1_tag, "draft"), SentMailResealCrypto.sentinel_nonce_b64())
        server.mutate_before_put = "d1"
        val store = MemoryProgressStore()

        val result = sweep(server, store)

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(1, result.counts.conflicts)
        assertTrue(server.puts.isEmpty())
        assertFalse(store.load()?.steps?.get(ENVELOPE_V2_DRAFT_STEP)?.done == true)

        server.mutate_before_put = null
        server.drafts.getValue("d1").envelope = sealed(v1_tag, "edited draft")
        sweep(server, store, now = ENVELOPE_V2_FIRST_RETRY_MS)

        assertEquals(listOf("d1"), server.puts)
    }

    @Test
    fun a_sent_interruption_stops_before_drafts() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.drafts["d1"] = Row(sealed(v1_tag, "draft"), SentMailResealCrypto.sentinel_nonce_b64())
        server.put_status = 503

        val result = sweep(server, MemoryProgressStore())

        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals(listOf(SENT_ITEM_TYPE), server.listed_types)
    }

    @Test
    fun the_draft_hook_fails_closed_until_the_mail_layer_attaches() = runBlocking {
        org.astermail.android.mail.LocalDraftActivity.detach()
        assertTrue(org.astermail.android.mail.LocalDraftActivity.is_in_use("any"))
        org.astermail.android.mail.LocalDraftActivity.attach { it == "held" }
        assertTrue(org.astermail.android.mail.LocalDraftActivity.is_in_use("held"))
        assertFalse(org.astermail.android.mail.LocalDraftActivity.is_in_use("free"))
        org.astermail.android.mail.LocalDraftActivity.detach()
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
        val retry = sweep(server, store, now = ENVELOPE_V2_FIRST_RETRY_MS)

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
    fun a_level_two_refusal_stops_the_pass_and_resets_admission() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.rows["b"] = Row(sealed(v1_tag, "second"), SentMailResealCrypto.sentinel_nonce_b64())
        server.drafts["d1"] = Row(sealed(v1_tag, "draft"), SentMailResealCrypto.sentinel_nonce_b64())
        server.put_reply = ApiReply(409, code = VAULT_LEVEL2_NOT_ADMITTED_CODE)
        val store = MemoryProgressStore()
        var refused = 0

        val result = sweep(server, store, on_not_admitted = { refused += 1 })

        assertEquals(ResealRunStatus.DEFERRED, result.status)
        assertEquals(ENVELOPE_V2_NOT_ADMITTED_REASON, result.reason)
        assertEquals(REKEY_NOT_ADMITTED_WAIT_SECS, result.retry_after_secs)
        assertEquals(1, refused)
        assertEquals(1, server.bodies.size)
        assertEquals(0, result.counts.conflicts)
        assertEquals(listOf(SENT_ITEM_TYPE), server.listed_types)
        assertFalse(store.load()?.steps?.get(ENVELOPE_V2_STEP)?.done == true)
        assertNull(store.load()?.steps?.get(ENVELOPE_V2_RESCAN))

        server.put_reply = null
        val admitted = sweep(server, store, on_not_admitted = { refused += 1 })

        assertEquals(ResealRunStatus.DONE, admitted.status)
        assertEquals(listOf("a", "b", "d1"), server.puts)
        assertEquals(1, refused)
    }

    @Test
    fun an_upgrade_required_reply_stops_the_pass_without_finishing_it() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.put_status = 426
        val store = MemoryProgressStore()
        var refused = 0

        val result = sweep(server, store, on_not_admitted = { refused += 1 })

        assertEquals(ResealRunStatus.DEFERRED, result.status)
        assertEquals(ENVELOPE_V2_UPGRADE_REASON, result.reason)
        assertEquals(0, refused)
        assertEquals(0, result.counts.rejected)
        assertFalse(store.load()?.steps?.get(ENVELOPE_V2_STEP)?.done == true)
        assertNull(store.load()?.steps?.get(ENVELOPE_V2_RESCAN))

        server.put_status = null
        server.put_reply = ApiReply(409, code = ENVELOPE_V2_UPGRADE_REQUIRED_CODE)
        assertEquals(ResealRunStatus.DEFERRED, sweep(server, store).status)

        server.put_reply = null
        assertEquals(ResealRunStatus.DONE, sweep(server, store).status)
        assertEquals(listOf("a"), server.puts)
        assertTrue(store.load()?.steps?.get(ENVELOPE_V2_STEP)?.done == true)
    }

    @Test
    fun an_incomplete_pass_retries_with_a_doubling_backoff_capped_at_seven_days() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        server.put_reply = ApiReply(409, code = ENVELOPE_CHANGED_CODE)
        val store = MemoryProgressStore()
        var now = 0L
        val waits = mutableListOf<Long>()

        repeat(10) {
            val result = sweep(server, store, now = now)
            assertEquals(ResealRunStatus.DONE, result.status)
            assertEquals(1, result.counts.conflicts)
            val marker = read_envelope_v2_rescan(store.load()!!)!!
            waits.add(marker.next_at_ms - now)
            assertEquals(ENVELOPE_V2_NOT_DUE_REASON, sweep(server, store, now = marker.next_at_ms - 1).reason)
            now = marker.next_at_ms
        }

        val hour = ENVELOPE_V2_FIRST_RETRY_MS
        assertEquals(
            listOf(hour, 2 * hour, 4 * hour, 8 * hour, 16 * hour, 32 * hour, 64 * hour, 128 * hour),
            waits.take(8),
        )
        assertEquals(listOf(ENVELOPE_V2_MAX_RETRY_MS, ENVELOPE_V2_MAX_RETRY_MS), waits.drop(8))
        assertEquals(10, server.bodies.size)
        assertTrue(server.puts.isEmpty())

        server.put_reply = null
        assertEquals(ResealRunStatus.DONE, sweep(server, store, now = now).status)
        assertEquals(listOf("a"), server.puts)
        assertEquals(now + ENVELOPE_V2_RESCAN_AFTER_MS, read_envelope_v2_rescan(store.load()!!)!!.next_at_ms)
    }

    @Test
    fun a_finished_pass_rescans_after_seven_days() {
        val server = SentServer()
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        val store = MemoryProgressStore()

        assertEquals(ResealRunStatus.DONE, sweep(server, store, now = 0L).status)
        assertEquals(listOf("a"), server.puts)
        server.rows["late"] = Row(sealed(v1_tag, "written by an old client"), SentMailResealCrypto.sentinel_nonce_b64())
        server.listed_types.clear()

        val early = sweep(server, store, now = ENVELOPE_V2_RESCAN_AFTER_MS - 1)
        assertEquals(ENVELOPE_V2_NOT_DUE_REASON, early.reason)
        assertTrue(server.listed_types.isEmpty())

        val due = sweep(server, store, now = ENVELOPE_V2_RESCAN_AFTER_MS)
        assertEquals(ResealRunStatus.DONE, due.status)
        assertEquals(listOf("a", "late"), server.puts)
        assertEquals(listOf(SENT_ITEM_TYPE, "trash", "spam", DRAFT_ITEM_TYPE, "trash"), server.listed_types)
        assertEquals(2 * ENVELOPE_V2_RESCAN_AFTER_MS, read_envelope_v2_rescan(store.load()!!)!!.next_at_ms)
    }

    @Test
    fun an_interrupted_rescan_resumes_without_starting_over() {
        val server = SentServer(page_size = 2)
        for (id in listOf("a", "b", "c")) {
            server.rows[id] = Row(sealed(v2_tag, "text $id"), LevelTwoEnvelope.marker_b64())
        }
        val store = MemoryProgressStore()
        sweep(server, store, now = 0L)
        for (id in listOf("a", "b", "c")) {
            server.rows[id] = Row(sealed(v1_tag, "text $id"), SentMailResealCrypto.sentinel_nonce_b64())
        }

        val first = sweep(server, store, max_items = 1, now = ENVELOPE_V2_RESCAN_AFTER_MS)
        assertEquals(ResealRunStatus.INTERRUPTED, first.status)
        assertEquals(listOf("a"), server.puts)

        val second = sweep(server, store, max_items = 1, now = ENVELOPE_V2_RESCAN_AFTER_MS + 1)
        assertEquals(ResealRunStatus.INTERRUPTED, second.status)
        assertEquals(listOf("a", "b"), server.puts)

        val third = sweep(server, store, max_items = 1, now = ENVELOPE_V2_RESCAN_AFTER_MS + 2)
        assertEquals(ResealRunStatus.DONE, third.status)
        assertEquals(listOf("a", "b", "c"), server.puts)
    }

    @Test
    fun a_progress_record_from_an_older_build_finishes_and_waits_for_the_rescan() {
        val server = SentServer()
        val probe = MemoryProgressStore()
        sweep(server, probe, now = 0L)
        server.listed_types.clear()
        val legacy = ResealProgress(probe.load()!!.key_id)
        for (step in listOf(
            ENVELOPE_V2_STEP,
            ENVELOPE_V2_STEP + "_trash",
            ENVELOPE_V2_STEP + "_spam",
            ENVELOPE_V2_DRAFT_STEP,
            ENVELOPE_V2_DRAFT_STEP + "_trash",
        )) {
            legacy.steps[step] = StepProgress(done = true)
        }
        server.rows["a"] = Row(sealed(v1_tag, "first"), SentMailResealCrypto.sentinel_nonce_b64())
        val store = MemoryProgressStore(legacy)

        assertEquals(ResealRunStatus.DONE, sweep(server, store, now = 5L).status)
        assertTrue(server.listed_types.isEmpty())
        assertTrue(server.puts.isEmpty())
        assertEquals(5L + ENVELOPE_V2_RESCAN_AFTER_MS, read_envelope_v2_rescan(store.load()!!)!!.next_at_ms)
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
