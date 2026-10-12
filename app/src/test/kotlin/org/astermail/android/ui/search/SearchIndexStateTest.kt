//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//


package org.astermail.android.ui.search

import java.time.ZoneId
import java.time.ZonedDateTime
import org.astermail.android.api.mail.MailItem
import org.astermail.android.mail.InboxItem
import org.astermail.android.mail.SearchUiState
import org.astermail.android.mail.search_state_after_index_failure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexStateTest {

    private fun item(id: String) = InboxItem(
        id = id,
        thread_token = null,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@example.com",
        subject = "Quarterly report",
        preview = "Preview",
        timestamp = "2026-08-01T00:00:00Z",
        is_read = true,
        is_starred = false,
        is_encrypted = false,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(id = id, item_type = "received"),
    )

    @Test
    fun failure_with_no_items_reports_the_error() {
        val next = search_state_after_index_failure(SearchUiState(is_indexing = true), "failed")
        assertEquals("failed", next.error)
        assertFalse(next.is_indexing)
        assertFalse(next.is_indexed)
        assertFalse(next.is_partial)
    }

    @Test
    fun failure_with_items_keeps_them_and_marks_the_index_partial() {
        val state = SearchUiState(all_items = listOf(item("a")), is_indexing = true)
        val next = search_state_after_index_failure(state, "failed")
        assertNull(next.error)
        assertFalse(next.is_indexing)
        assertTrue(next.is_indexed)
        assertTrue(next.is_partial)
        assertEquals(listOf("a"), next.all_items.map { it.id })
    }

    @Test
    fun a_fresh_state_is_not_partial() {
        assertFalse(SearchUiState(all_items = listOf(item("a")), is_indexed = true).is_partial)
    }

    @Test
    fun local_day_changes_at_local_midnight() {
        val zone = ZoneId.of("America/New_York")
        val before = ZonedDateTime.of(2026, 10, 12, 23, 59, 59, 0, zone).toInstant().toEpochMilli()
        val after = ZonedDateTime.of(2026, 10, 13, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertNotEquals(search_local_day(before, zone), search_local_day(after, zone))
        assertEquals(search_local_day(after, zone), search_local_day(after + 3_600_000L, zone))
    }

    @Test
    fun local_day_follows_the_zone_not_utc() {
        val instant = ZonedDateTime.of(2026, 10, 12, 23, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()
        assertEquals(
            search_local_day(instant, ZoneId.of("UTC")) + 1,
            search_local_day(instant, ZoneId.of("Asia/Tokyo")),
        )
    }
}
