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

package org.astermail.android.ui.mail

import org.astermail.android.mail.CATEGORY_TABS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildThreadRowsTest {

    private fun email(
        id: String,
        received_at: Long,
        category: String = "primary",
        is_pinned: Boolean = false,
        is_read: Boolean = true,
    ) = Email(
        id = id,
        sender_name = "Sender $id",
        sender_email = "$id@example.com",
        subject = "Subject $id",
        preview = "Preview $id",
        received_at = received_at,
        is_read = is_read,
        is_starred = false,
        has_attachment = false,
        thread_id = id,
        is_pinned = is_pinned,
        category = category,
    )

    private fun build(
        emails: List<Email>,
        categories_enabled: Boolean = false,
        active_category: String = "primary",
        sort_mode: InboxSortMode = InboxSortMode.newest,
        cached: Map<String, List<Pair<String, String>>> = emptyMap(),
        sticky: Map<String, List<Pair<String, String>>> = emptyMap(),
        grouping_enabled: Boolean = true,
    ) = build_thread_rows(
        emails = emails,
        categories_enabled = categories_enabled,
        active_category = active_category,
        active_tabs = CATEGORY_TABS,
        sort_mode = sort_mode,
        cached_participants = cached,
        sticky_participants = sticky,
        grouping_enabled = grouping_enabled,
    )

    private fun threaded(id: String, thread: String, received_at: Long) =
        email(id, received_at).copy(thread_id = thread)

    @Test
    fun grouping_disabled_keeps_one_row_per_message() {
        val emails = listOf(
            threaded("a", "t1", 300),
            threaded("b", "t1", 200),
            threaded("c", "t2", 100),
        )

        val grouped = build(emails).rows
        assertEquals(listOf("t1", "t2"), grouped.map { it.thread_id })
        assertEquals(2, grouped.first().message_count)

        val flat = build(emails, grouping_enabled = false).rows
        assertEquals(listOf("a", "b", "c"), flat.map { it.thread_id })
        assertTrue(flat.all { it.message_count == 1 })
        assertEquals(listOf("a", "b", "c"), flat.map { thread_open_target_id(it) })
    }

    @Test
    fun newest_first_keeps_pinned_threads_on_top() {
        val result = build(
            listOf(
                email("old", 100),
                email("new", 300),
                email("pinned", 200, is_pinned = true),
            ),
        )

        assertEquals(listOf("pinned", "new", "old"), result.rows.map { it.thread_id })
    }

    @Test
    fun oldest_first_reverses_only_the_unpinned_tail() {
        val result = build(
            listOf(
                email("old", 100),
                email("new", 300),
                email("pinned", 200, is_pinned = true),
            ),
            sort_mode = InboxSortMode.oldest,
        )

        assertEquals(listOf("pinned", "old", "new"), result.rows.map { it.thread_id })
    }

    @Test
    fun unread_first_lifts_unread_threads_above_read_ones() {
        val result = build(
            listOf(
                email("read_new", 300),
                email("unread_old", 100, is_read = false),
            ),
            sort_mode = InboxSortMode.unread_first,
        )

        assertEquals(listOf("unread_old", "read_new"), result.rows.map { it.thread_id })
    }

    @Test
    fun categories_filter_the_source_only_when_enabled() {
        val emails = listOf(
            email("primary_one", 300, category = "primary"),
            email("promo_one", 200, category = "promotions"),
        )

        val filtered = build(emails, categories_enabled = true, active_category = "primary")
        assertEquals(listOf("primary_one"), filtered.rows.map { it.thread_id })

        val unfiltered = build(emails, categories_enabled = false)
        assertEquals(setOf("primary_one", "promo_one"), unfiltered.rows.map { it.thread_id }.toSet())
    }

    @Test
    fun the_richest_participant_list_wins_and_is_reported_back() {
        val cached = mapOf("a" to listOf("One" to "one@x.com", "Two" to "two@x.com"))
        val sticky = mapOf("a" to listOf("Only" to "only@x.com"))

        val result = build(listOf(email("a", 100)), cached = cached, sticky = sticky)

        assertEquals(cached["a"], result.rows.single().participants)
        assertEquals(cached["a"], result.participants["a"])
    }

    @Test
    fun participants_of_threads_that_disappeared_are_not_carried_forward() {
        val sticky = mapOf(
            "gone" to listOf("Ghost" to "ghost@x.com"),
            "here" to listOf("Here" to "here@x.com"),
        )

        val result = build(listOf(email("here", 100)), sticky = sticky)

        assertEquals(setOf("here"), result.participants.keys)
        assertTrue("a vanished thread must not keep a participant entry", "gone" !in result.participants)
    }
}
