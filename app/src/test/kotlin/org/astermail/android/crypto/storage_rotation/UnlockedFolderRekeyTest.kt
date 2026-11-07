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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.mail.LevelTwoEnvelope
import org.astermail.android.mail.SentMailResealCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockedFolderRekeyTest {

    private fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(value: String): ByteArray = Base64.getDecoder().decode(value)

    private fun sha256_hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sealed(tag: Byte, text: String): String = b64(byteArrayOf(tag) + text.toByteArray(Charsets.UTF_8))

    private inner class TaggedCrypto : EnvelopeV2Crypto {
        override fun is_v2(sealed_b64: String): Boolean = unb64(sealed_b64).firstOrNull() == 2.toByte()

        override fun open_v1(sealed_b64: String): ByteArray? {
            val raw = unb64(sealed_b64)
            if (raw.firstOrNull() != 1.toByte()) return null
            return raw.copyOfRange(1, raw.size)
        }

        override fun seal_v2(plaintext: ByteArray): String? = b64(byteArrayOf(2) + plaintext)
    }

    private class Row(val type: String, var envelope: String, var nonce: String)

    private inner class FolderServer : ResealApi {
        val rows = LinkedHashMap<String, Row>()
        val gets = mutableListOf<String>()
        val puts = mutableListOf<String>()
        val expected_hashes = mutableListOf<String>()
        var put_status: Int? = null
        var after_put: (String) -> Unit = {}

        override suspend fun get(path: String): ApiReply {
            gets.add(path)
            return ApiReply(404)
        }

        override suspend fun post(path: String, body: JsonElement): ApiReply = ApiReply(404)

        override suspend fun put(path: String, body: JsonElement): ApiReply {
            val id = path.removePrefix("$SENT_MESSAGES_PATH/")
            val record = body as JsonObject
            put_status?.let { return ApiReply(it) }
            val row = rows[id] ?: return ApiReply(404)
            val expected = record["expected_envelope_sha256"]?.jsonPrimitive?.contentOrNull
                ?: return ApiReply(400)
            expected_hashes.add(expected)
            if (expected != sha256_hex(unb64(row.envelope))) return ApiReply(409, code = ENVELOPE_CHANGED_CODE)
            row.envelope = record.getValue("encrypted_envelope").jsonPrimitive.contentOrNull!!
            row.nonce = record.getValue("envelope_nonce").jsonPrimitive.contentOrNull!!
            puts.add(id)
            after_put(id)
            return ApiReply(200)
        }

        fun listing(): List<JsonObject> = rows.map { (id, row) ->
            json_object(
                "id" to id,
                "item_type" to row.type,
                "encrypted_envelope" to row.envelope,
                "envelope_nonce" to row.nonce,
                "has_attachments" to false,
                "attachment_count" to 0,
            )
        }
    }

    private fun keys(): ResealKeys = ResealKeys(ByteArray(32) { (it + 3).toByte() }, emptyList())

    private fun v1(): String = SentMailResealCrypto.sentinel_nonce_b64()

    private fun rekey(
        server: FolderServer,
        still_unlocked: () -> Boolean = { true },
        draft_in_use: suspend (String) -> Boolean = { false },
        max_items: Int = UNLOCKED_FOLDER_MAX_ITEMS_PER_UNLOCK,
    ) = runBlocking {
        run_unlocked_folder_rekey(
            keys_source = { keys() },
            api = server,
            crypto = TaggedCrypto(),
            items = server.listing(),
            still_unlocked = still_unlocked,
            draft_in_use = draft_in_use,
            sleep = {},
            max_items = max_items,
        )
    }

    @Test
    fun unlocked_folder_sent_and_draft_rows_move_to_level_two_with_their_stored_hash() {
        val server = FolderServer()
        val sent_stored = sealed(1, "sent body")
        val draft_stored = sealed(1, "draft body")
        server.rows["s1"] = Row(SENT_ITEM_TYPE, sent_stored, v1())
        server.rows["d1"] = Row(DRAFT_ITEM_TYPE, draft_stored, v1())

        val result = rekey(server)

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(listOf("s1", "d1"), server.puts)
        assertEquals(listOf(sha256_hex(unb64(sent_stored)), sha256_hex(unb64(draft_stored))), server.expected_hashes)
        assertEquals(LevelTwoEnvelope.marker_b64(), server.rows.getValue("s1").nonce)
        assertEquals(LevelTwoEnvelope.marker_b64(), server.rows.getValue("d1").nonce)
        assertEquals(sealed(2, "sent body"), server.rows.getValue("s1").envelope)
        assertEquals(2, result.counts.resealed)
        assertTrue(server.gets.isEmpty())
    }

    @Test
    fun received_and_current_rows_in_the_folder_are_left_untouched() {
        val server = FolderServer()
        val received = sealed(1, "received body")
        server.rows["r1"] = Row("received", received, v1())
        server.rows["s2"] = Row(SENT_ITEM_TYPE, sealed(2, "already"), LevelTwoEnvelope.marker_b64())
        server.rows["s3"] = Row(SENT_ITEM_TYPE, sealed(1, "sent body"), v1())
        assertEquals(listOf("s3"), unlocked_folder_candidates(server.listing()).map { it.str("id") })

        val result = rekey(server)

        assertEquals(ResealRunStatus.DONE, result.status)
        assertEquals(listOf("s3"), server.puts)
        assertEquals(received, server.rows.getValue("r1").envelope)
        assertEquals(v1(), server.rows.getValue("r1").nonce)
    }

    @Test
    fun folder_locking_mid_run_stops_before_the_next_row() {
        val server = FolderServer()
        server.rows["s1"] = Row(SENT_ITEM_TYPE, sealed(1, "one"), v1())
        server.rows["s2"] = Row(SENT_ITEM_TYPE, sealed(1, "two"), v1())
        server.rows["d1"] = Row(DRAFT_ITEM_TYPE, sealed(1, "three"), v1())
        var unlocked = true
        server.after_put = { unlocked = false }

        val result = rekey(server, still_unlocked = { unlocked })

        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals("folder_locked", result.reason)
        assertEquals(listOf("s1"), server.puts)
        assertEquals(v1(), server.rows.getValue("s2").nonce)
        assertEquals(v1(), server.rows.getValue("d1").nonce)
    }

    @Test
    fun a_locked_folder_sends_nothing() {
        val server = FolderServer()
        server.rows["s1"] = Row(SENT_ITEM_TYPE, sealed(1, "one"), v1())

        val result = rekey(server, still_unlocked = { false })

        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertTrue(server.puts.isEmpty())
        assertTrue(server.expected_hashes.isEmpty())
    }

    @Test
    fun a_draft_open_in_the_editor_is_skipped() {
        val server = FolderServer()
        server.rows["d1"] = Row(DRAFT_ITEM_TYPE, sealed(1, "editing"), v1())
        server.rows["d2"] = Row(DRAFT_ITEM_TYPE, sealed(1, "idle"), v1())

        val result = rekey(server, draft_in_use = { it == "d1" })

        assertEquals(listOf("d2"), server.puts)
        assertEquals(1, result.counts.skipped)
        assertTrue(result.incomplete.isNotEmpty())
    }

    @Test
    fun server_replies_map_like_the_sweep() {
        val conflict = FolderServer()
        conflict.rows["s1"] = Row(SENT_ITEM_TYPE, sealed(1, "one"), v1())
        val listing = conflict.listing()
        conflict.rows.getValue("s1").envelope = sealed(1, "changed")
        val conflict_result = runBlocking {
            run_unlocked_folder_rekey({ keys() }, conflict, TaggedCrypto(), listing, { true }, sleep = {})
        }
        assertEquals(ResealRunStatus.DONE, conflict_result.status)
        assertEquals(1, conflict_result.counts.conflicts)
        assertTrue(conflict.puts.isEmpty())

        val missing = FolderServer()
        missing.rows["s1"] = Row(SENT_ITEM_TYPE, sealed(1, "one"), v1())
        missing.put_status = 404
        val missing_result = rekey(missing)
        assertEquals(ResealRunStatus.DONE, missing_result.status)
        assertEquals(1, missing_result.counts.skipped)

        val busy = FolderServer()
        busy.rows["s1"] = Row(SENT_ITEM_TYPE, sealed(1, "one"), v1())
        busy.rows["s2"] = Row(SENT_ITEM_TYPE, sealed(1, "two"), v1())
        busy.put_status = 503
        val busy_result = rekey(busy)
        assertEquals(ResealRunStatus.INTERRUPTED, busy_result.status)
        assertEquals(0, busy_result.counts.resealed)
    }

    @Test
    fun the_budget_bounds_one_run() {
        val server = FolderServer()
        for (i in 1..4) server.rows["s$i"] = Row(SENT_ITEM_TYPE, sealed(1, "body $i"), v1())

        val result = rekey(server, max_items = 2)

        assertEquals(ResealRunStatus.INTERRUPTED, result.status)
        assertEquals(listOf("s1", "s2"), server.puts)
    }

    @Test
    fun the_queue_bounds_work_per_unlock_and_resets_on_a_new_unlock() = runBlocking {
        val server = FolderServer()
        for (i in 1..5) server.rows["s$i"] = Row(SENT_ITEM_TYPE, sealed(1, "body $i"), v1())
        var marker: Long? = 10L
        val batches = mutableListOf<List<String>>()
        val queue = UnlockedFolderRekeyQueue(
            scope = CoroutineScope(coroutineContext),
            session_marker = { marker },
            run_batch = { items, _, _ ->
                batches.add(items.mapNotNull { it.str("id") })
                ResealRunResult(ResealRunStatus.DONE, StepCounts())
            },
            max_per_unlock = 3,
        )

        queue.offer("f1", server.listing())?.join()
        queue.offer("f1", server.listing())?.join()
        assertNull(queue.offer("f1", server.listing()))
        marker = null
        assertNull(queue.offer("f1", server.listing()))
        marker = 20L
        queue.offer("f1", server.listing())?.join()

        assertEquals(listOf(listOf("s1", "s2", "s3"), listOf("s1", "s2", "s3")), batches)
    }

    @Test
    fun the_queue_stops_a_run_when_the_folder_locks() = runBlocking {
        val server = FolderServer()
        server.rows["s1"] = Row(SENT_ITEM_TYPE, sealed(1, "one"), v1())
        server.rows["s2"] = Row(SENT_ITEM_TYPE, sealed(1, "two"), v1())
        var marker: Long? = 10L
        server.after_put = { marker = null }
        var result: ResealRunResult? = null
        val queue = UnlockedFolderRekeyQueue(
            scope = CoroutineScope(coroutineContext),
            session_marker = { marker },
            run_batch = { items, still_unlocked, max_items ->
                run_unlocked_folder_rekey(
                    { keys() },
                    server,
                    TaggedCrypto(),
                    items,
                    still_unlocked,
                    sleep = {},
                    max_items = max_items,
                ).also { result = it }
            },
        )

        queue.offer("f1", server.listing())?.join()

        assertEquals(ResealRunStatus.INTERRUPTED, result?.status)
        assertEquals(listOf("s1"), server.puts)
    }

    private class RecordingCursorStore : FolderRekeyCursorStore {
        val saved = LinkedHashMap<String, FolderRekeyCursor>()

        override fun load(folder_id: String): FolderRekeyCursor? = saved[folder_id]

        override fun save(folder_id: String, cursor: FolderRekeyCursor) {
            saved[folder_id] = cursor
        }

        override fun clear() {
            saved.clear()
        }
    }

    private fun paged_queue(
        scope: CoroutineScope,
        server: FolderServer,
        store: FolderRekeyCursorStore,
        marker: () -> Long?,
        offsets: MutableList<Int>,
        max_per_unlock: Int,
    ) = UnlockedFolderRekeyQueue(
        scope = scope,
        session_marker = { marker() },
        run_batch = { items, still_unlocked, max_items ->
            run_unlocked_folder_rekey(
                { keys() },
                server,
                TaggedCrypto(),
                items,
                still_unlocked,
                draft_in_use = { false },
                sleep = {},
                max_items = max_items,
            )
        },
        fetch_page = { _, offset ->
            offsets.add(offset)
            val all = server.listing()
            FolderRekeyPage(all.drop(offset).take(2), offset + 2 < all.size)
        },
        cursors = store,
        max_per_unlock = max_per_unlock,
    )

    @Test
    fun later_unlocks_continue_from_the_saved_cursor_until_no_level_one_rows_remain() = runBlocking {
        val server = FolderServer()
        for (i in 1..6) server.rows["s$i"] = Row(SENT_ITEM_TYPE, sealed(1, "body $i"), v1())
        val store = RecordingCursorStore()
        val offsets = mutableListOf<Int>()
        var marker = 0L
        val queue = paged_queue(CoroutineScope(coroutineContext), server, store, { marker }, offsets, max_per_unlock = 2)

        for (unlock in 1..4) {
            marker = unlock.toLong()
            queue.offer("f1", emptyList())?.join()
        }

        assertEquals(listOf("s1", "s2", "s3", "s4", "s5", "s6"), server.puts)
        assertEquals(FolderRekeyCursor(done = true), store.saved["f1"])
        assertEquals(0, unlocked_folder_candidates(server.listing()).size)

        marker = 5L
        val before = offsets.size
        assertNull(queue.offer("f1", emptyList()))
        assertEquals(before, offsets.size)
    }

    @Test
    fun the_saved_cursor_holds_only_the_folder_id_and_position() = runBlocking {
        val server = FolderServer()
        for (i in 1..4) server.rows["s$i"] = Row(SENT_ITEM_TYPE, sealed(1, "body $i"), v1())
        val store = RecordingCursorStore()
        val queue = paged_queue(CoroutineScope(coroutineContext), server, store, { 7L }, mutableListOf(), max_per_unlock = 2)

        queue.offer("f1", emptyList())?.join()

        val text = encode_folder_cursors(store.saved)
        assertEquals(setOf("f1"), store.saved.keys)
        assertEquals(FolderRekeyCursor(offset = 2, clean = false), store.saved["f1"])
        assertTrue(!text.contains("token") && !text.contains("unlock"))
        assertEquals(store.saved, decode_folder_cursors(text))
    }

    @Test
    fun a_listed_level_one_row_reopens_a_finished_folder() = runBlocking {
        val server = FolderServer()
        server.rows["d1"] = Row(DRAFT_ITEM_TYPE, sealed(1, "draft"), v1())
        val store = RecordingCursorStore()
        store.saved["f1"] = FolderRekeyCursor(done = true)
        val queue = paged_queue(CoroutineScope(coroutineContext), server, store, { 3L }, mutableListOf(), max_per_unlock = 5)

        assertNull(queue.offer("f1", emptyList()))
        queue.offer("f1", server.listing())?.join()

        assertEquals(listOf("d1"), server.puts)
        assertEquals(FolderRekeyCursor(done = true), store.saved["f1"])
    }

    @Test
    fun locking_during_a_page_stops_and_keeps_the_cursor_in_place() = runBlocking {
        val server = FolderServer()
        for (i in 1..4) server.rows["s$i"] = Row(SENT_ITEM_TYPE, sealed(1, "body $i"), v1())
        val store = RecordingCursorStore()
        val offsets = mutableListOf<Int>()
        var marker: Long? = 9L
        server.after_put = { marker = null }
        val queue = paged_queue(CoroutineScope(coroutineContext), server, store, { marker }, offsets, max_per_unlock = 10)

        queue.offer("f1", emptyList())?.join()

        assertEquals(listOf("s1"), server.puts)
        assertEquals(listOf(0), offsets)
        assertEquals(FolderRekeyCursor(offset = 0, clean = false), store.saved["f1"])
        assertTrue(store.saved["f1"]?.done != true)
    }
}
