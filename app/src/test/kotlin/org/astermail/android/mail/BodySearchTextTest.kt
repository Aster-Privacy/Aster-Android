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

import org.astermail.android.storage.search.BodyBackfillRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodySearchTextTest {

    @Test
    fun folds_case_and_diacritics_in_indexed_text() {
        assertEquals("cafe creme brulee", build_body_index_text("Café Crème BRÛLÉE", null))
    }

    @Test
    fun caps_the_indexed_body_at_thirty_two_kilobytes() {
        val body = "a".repeat(body_search_max_chars) + " quetzal"
        val indexed = build_body_index_text(body, null)

        assertEquals(body_search_max_chars, indexed.length)
        assertFalse(indexed.contains("quetzal"))
    }

    @Test
    fun indexes_text_past_the_old_preview_length() {
        val body = "x ".repeat(4_000) + "quetzal"

        assertTrue(build_body_index_text(body, null).contains("quetzal"))
    }

    @Test
    fun appends_attachment_file_names() {
        val indexed = build_body_index_text("Hello", null, listOf("Résumé.pdf", " "))

        assertEquals("hello\nresume.pdf", indexed)
    }

    @Test
    fun reads_text_from_html_when_plain_text_is_missing() {
        val indexed = build_body_index_text("", "<p>Hidden <b>orchid</b> note</p>")

        assertTrue(indexed.contains("orchid"))
        assertFalse(indexed.contains("<b>"))
    }

    @Test
    fun builds_a_prefix_match_for_one_word() {
        assertEquals("quetz*", body_match_expression("Quetz"))
    }

    @Test
    fun builds_a_prefix_phrase_for_several_words() {
        assertEquals("\"blue* wha*\"", body_match_expression("Blue wha"))
    }

    @Test
    fun strips_full_text_operators_from_the_query() {
        assertEquals("\"foo* or* bar*\"", body_match_expression("foo\" OR bar*"))
        assertNull(body_match_expression("*\"-"))
    }

    @Test
    fun matches_accented_query_words_against_folded_text() {
        assertEquals("creme*", body_match_expression("Crème"))
    }

    @Test
    fun backfills_only_on_unmetered_networks() {
        assertTrue(body_backfill_allowed(on_wifi = true, on_ethernet = false))
        assertTrue(body_backfill_allowed(on_wifi = false, on_ethernet = true))
        assertFalse(body_backfill_allowed(on_wifi = false, on_ethernet = false))
    }

    @Test
    fun backfill_groups_rows_by_thread_in_order() {
        val rows = listOf(
            BodyBackfillRow("m1", "t1", "2026-11-03"),
            BodyBackfillRow("m2", "t2", "2026-11-02"),
            BodyBackfillRow("m3", "t1", "2026-11-01"),
        )
        val groups = body_backfill_groups(rows, emptySet())
        assertEquals(listOf("t1", "t2"), groups.keys.toList())
        assertEquals(listOf("m1", "m3"), groups["t1"])
        assertEquals(listOf("m2"), groups["t2"])
    }

    @Test
    fun backfill_groups_skip_failed_ids_and_blank_threads() {
        val rows = listOf(
            BodyBackfillRow("m1", "t1", "2026-11-03"),
            BodyBackfillRow("m2", "", "2026-11-02"),
            BodyBackfillRow("m3", null, "2026-11-01"),
            BodyBackfillRow("m4", "t2", "2026-11-01"),
        )
        val groups = body_backfill_groups(rows, setOf("m4"))
        assertEquals(mapOf("t1" to listOf("m1")), groups)
    }

    @Test
    fun progress_publishes_first_then_throttles() {
        assertTrue(should_publish_body_progress(last_published_ms = 0L, now_ms = 1_000L, finished = false))
        assertFalse(should_publish_body_progress(last_published_ms = 1_000L, now_ms = 1_100L, finished = false))
        assertTrue(should_publish_body_progress(last_published_ms = 1_000L, now_ms = 1_250L, finished = false))
    }

    @Test
    fun progress_always_publishes_when_finished() {
        assertTrue(should_publish_body_progress(last_published_ms = 1_000L, now_ms = 1_001L, finished = true))
    }

    @Test
    fun snapshot_covers_only_when_every_expected_id_is_ready() {
        assertTrue(snapshot_covers(setOf("a", "b", "c"), listOf("a", "c")))
        assertFalse(snapshot_covers(setOf("a"), listOf("a", "b")))
        assertFalse(snapshot_covers(setOf("a"), emptyList()))
    }

    @Test
    fun thread_rows_mark_missing_expected_ids_once() {
        val rows = body_index_thread_rows(emptyList(), listOf("x", "y", "x"), { emptyList() }, now = 7L)
        assertEquals(listOf("x", "y"), rows.map { it.message_id })
        assertTrue(rows.all { it.body.isEmpty() && it.indexed_at == 7L })
    }
}
