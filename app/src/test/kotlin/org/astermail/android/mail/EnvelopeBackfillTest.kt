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

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvelopeBackfillTest {

    private val loading = "Loading..."

    private fun raw(
        id: String,
        envelope: String? = null,
        nonce: String? = "nonce-$id",
        item_type: String? = "received",
        is_reaction: Boolean? = null,
    ) = MailItem(
        id = id,
        item_type = item_type,
        encrypted_envelope = envelope,
        envelope_nonce = nonce,
        is_reaction = is_reaction,
        metadata = MailItemMetadata(),
    )

    private fun row(
        id: String,
        sender_name: String = "Sender",
        sender_email: String = "sender@example.com",
        subject: String = "Subject",
        preview: String = "Preview",
        is_undecryptable: Boolean = false,
        is_envelope_missing: Boolean = false,
        is_read: Boolean = false,
        is_starred: Boolean = false,
    ) = InboxItem(
        id = id,
        thread_token = "t-$id",
        thread_message_count = 1,
        sender_name = sender_name,
        sender_email = sender_email,
        subject = subject,
        preview = preview,
        timestamp = "2026-11-22T00:00:00Z",
        is_read = is_read,
        is_starred = is_starred,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        is_undecryptable = is_undecryptable,
        raw_item = raw(id),
        is_decrypt_pending = is_envelope_missing,
        is_envelope_missing = is_envelope_missing,
    )

    private fun pending_row(id: String, is_read: Boolean = false, is_starred: Boolean = false) = row(
        id,
        sender_name = loading,
        sender_email = "",
        subject = loading,
        preview = "",
        is_envelope_missing = true,
        is_read = is_read,
        is_starred = is_starred,
    )

    @Test
    fun only_mail_without_an_envelope_needs_a_backfill() {
        assertTrue(needs_envelope_backfill(raw("a")))
        assertTrue(needs_envelope_backfill(raw("a", envelope = "")))
        assertTrue(needs_envelope_backfill(raw("a", item_type = "sent")))
        assertTrue(needs_envelope_backfill(raw("a", item_type = null)))
        assertFalse(needs_envelope_backfill(raw("a", envelope = "sealed")))
        assertFalse(needs_envelope_backfill(raw("a", item_type = "draft")))
        assertFalse(needs_envelope_backfill(raw("a", is_reaction = true)))
        assertFalse(needs_envelope_backfill(raw("")))
    }

    @Test
    fun missing_envelopes_are_fetched_in_small_chunks() = runBlocking {
        val ids = (1..50).map { "m$it" }
        val sizes = mutableListOf<Int>()
        val found = fetch_missing_envelopes(ids, { chunk ->
            synchronized(sizes) { sizes.add(chunk.size) }
            chunk.map { raw(it, envelope = "env-$it") }
        }, backoff_ms = { 0L })
        assertEquals(ids.toSet(), found.keys)
        assertTrue(sizes.all { it <= envelope_backfill_chunk_size })
        assertEquals(50, sizes.sum())
    }

    @Test
    fun rows_still_missing_are_retried_and_failures_do_not_abort() = runBlocking {
        val calls = AtomicInteger()
        val requested = mutableListOf<List<String>>()
        val found = fetch_missing_envelopes(listOf("a", "b", "c"), { chunk ->
            synchronized(requested) { requested.add(chunk) }
            when (calls.incrementAndGet()) {
                1 -> throw java.io.IOException("storage slow")
                2 -> chunk.map { if (it == "a") raw(it, envelope = "env-a") else raw(it) }
                else -> chunk.map { raw(it, envelope = "env-$it") }
            }
        }, backoff_ms = { 0L })
        assertEquals(setOf("a", "b", "c"), found.keys)
        assertEquals(3, calls.get())
        assertEquals(listOf("b", "c"), requested[2])
    }

    @Test
    fun attempts_are_bounded_when_the_server_never_returns_the_envelope() = runBlocking {
        val calls = AtomicInteger()
        val found = fetch_missing_envelopes(listOf("a"), { chunk ->
            calls.incrementAndGet()
            chunk.map { raw(it) }
        }, attempts = 3, backoff_ms = { 0L })
        assertTrue(found.isEmpty())
        assertEquals(3, calls.get())
    }

    @Test
    fun the_budget_stops_a_slow_backfill() = runBlocking {
        val started = System.currentTimeMillis()
        val found = fetch_missing_envelopes(listOf("a"), { _ ->
            kotlinx.coroutines.delay(10_000)
            emptyList()
        }, budget_ms = 200L, backoff_ms = { 0L })
        assertTrue(found.isEmpty())
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    @Test
    fun list_pages_skip_the_inline_backfill_unless_asked() {
        assertEquals(0L, list_envelope_backfill_budget_ms(inline = false))
        assertEquals(envelope_inline_budget_ms, list_envelope_backfill_budget_ms(inline = true))
    }

    @Test
    fun a_zero_budget_never_calls_the_server() = runBlocking {
        val calls = AtomicInteger()
        val items = listOf(raw("a"), raw("b"))
        val result = backfill_missing_envelopes(items, { ids -> calls.incrementAndGet(); ids.map { raw(it, envelope = "env-$it") } }, budget_ms = 0L)
        assertEquals(items, result)
        assertEquals(0, calls.get())
        assertTrue(fetch_missing_envelopes(listOf("a"), { calls.incrementAndGet(); emptyList() }, budget_ms = 0L).isEmpty())
        assertEquals(0, calls.get())
    }

    @Test
    fun the_first_refill_starts_soon_and_opening_mail_waits_less_than_a_refill() {
        assertTrue(envelope_refill_delays_ms.first() <= 1_000L)
        assertTrue(envelope_refill_delays_ms.zipWithNext().all { (a, b) -> a < b })
        assertTrue(envelope_open_budget_ms < envelope_refill_budget_ms)
    }

    @Test
    fun backfill_fills_only_the_missing_rows_and_keeps_list_fields() = runBlocking {
        val items = listOf(
            raw("a").copy(thread_message_count = 4),
            raw("b", envelope = "kept"),
            raw("c"),
        )
        val requested = mutableListOf<String>()
        val filled = backfill_missing_envelopes(items, { chunk ->
            synchronized(requested) { requested.addAll(chunk) }
            chunk.map { raw(it, envelope = "env-$it", nonce = "fresh-$it") }
        })
        assertEquals(listOf("a", "c"), requested)
        assertEquals("env-a", filled[0].encrypted_envelope)
        assertEquals("fresh-a", filled[0].envelope_nonce)
        assertEquals(4, filled[0].thread_message_count)
        assertEquals("kept", filled[1].encrypted_envelope)
        assertEquals("env-c", filled[2].encrypted_envelope)
    }

    @Test
    fun backfill_with_a_failing_server_returns_the_original_items() = runBlocking {
        val items = listOf(raw("a"), raw("b", envelope = "kept"))
        val filled = backfill_missing_envelopes(items, { throw java.io.IOException("down") }, attempts = 2)
        assertEquals(items, filled)
    }

    @Test
    fun a_fetched_row_without_an_envelope_never_replaces_the_original() {
        val original = raw("a")
        assertEquals(original, merge_backfilled_envelope(original, raw("a")))
        assertEquals(original, merge_backfilled_envelope(original, raw("b", envelope = "env-b")))
    }

    @Test
    fun a_row_with_a_missing_envelope_reuses_earlier_decrypted_content() {
        val previous = row("a", sender_name = "Alice", sender_email = "alice@example.com", subject = "Hello")
        val carried = carry_decrypted_content(pending_row("a"), previous)
        assertEquals("Alice", carried.sender_name)
        assertEquals("Hello", carried.subject)
        assertFalse(carried.is_envelope_missing)
        assertFalse(carried.is_decrypt_pending)
    }

    @Test
    fun a_blank_cached_row_is_never_carried_forward() {
        val live = pending_row("a")
        val blank = row("a", sender_name = "", sender_email = "", subject = "", preview = "")
        assertEquals(live, carry_decrypted_content(live, blank))
        assertEquals(listOf(live), folder_cache_carry_decrypted(listOf(live), listOf(blank)))
    }

    @Test
    fun rows_with_a_missing_envelope_are_not_written_to_the_disk_cache() {
        val rows = folder_cache_rows(
            "inbox",
            listOf(row("a"), pending_row("b"), row("c")),
            cached_at = 1L,
        )
        assertEquals(listOf("a", "c"), rows.map { it.id })
        assertEquals(listOf(0, 1), rows.map { it.position })
    }

    @Test
    fun blank_rows_already_on_disk_load_as_pending_and_get_refilled() {
        val marked = folder_cache_mark_placeholders(
            listOf(
                row("a", sender_name = "", sender_email = "", subject = "", preview = ""),
                row("b"),
                row("c", sender_name = "Encrypted", subject = "Message could not be decrypted"),
            ),
            "Encrypted",
            "Message could not be decrypted",
            loading,
        )
        assertTrue(marked[0].is_envelope_missing)
        assertTrue(marked[0].is_decrypt_pending)
        assertEquals(loading, marked[0].sender_name)
        assertEquals(loading, marked[0].subject)
        assertTrue(needs_envelope_backfill(marked[0].raw_item))
        assertFalse(marked[1].is_envelope_missing)
        assertTrue(marked[2].is_undecryptable)
        assertFalse(marked[2].is_envelope_missing)
        assertEquals(setOf("a"), envelope_missing_ids(marked))
    }

    @Test
    fun refilled_rows_replace_only_missing_rows_and_keep_local_state() {
        val items = listOf(pending_row("a", is_read = true, is_starred = true), row("b", sender_name = "Bob"))
        val refilled = mapOf(
            "a" to row("a", sender_name = "Alice", subject = "Hello"),
            "b" to row("b", sender_name = "Changed"),
        )
        val result = replace_refilled_items(items, refilled)
        assertEquals("Alice", result[0].sender_name)
        assertEquals("Hello", result[0].subject)
        assertTrue(result[0].is_read)
        assertTrue(result[0].is_starred)
        assertFalse(result[0].is_envelope_missing)
        assertEquals("Bob", result[1].sender_name)
    }

    @Test
    fun a_pending_row_is_never_written_to_the_search_index() {
        assertTrue(is_index_poisoned(pending_row("a")))
        assertFalse(is_index_poisoned(row("b")))
    }
}
