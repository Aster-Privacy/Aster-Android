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

package org.astermail.android.ui.mail

import org.astermail.android.mail.CATEGORY_TABS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantCategorySwitchTest {

    private fun email(id: String, received_at: Long, category: String, thread: String = id) = Email(
        id = id,
        sender_name = "Sender $id",
        sender_email = "$id@example.com",
        subject = "Subject $id",
        preview = "Preview $id",
        received_at = received_at,
        is_read = true,
        is_starred = false,
        has_attachment = false,
        thread_id = thread,
        is_pinned = false,
        category = category,
    )

    private val mail = listOf(
        email("p1", 500, "primary"),
        email("u1", 400, "updates"),
        email("s1", 300, "social"),
        email("p2", 200, "primary"),
        email("m1", 100, "promotions"),
        email("u2", 50, "updates", thread = "t-u"),
        email("u3", 40, "updates", thread = "t-u"),
    )

    private fun tabs(grouping: Boolean = true) = build_tab_thread_rows(
        emails = mail,
        categories_enabled = true,
        active_tabs = CATEGORY_TABS,
        sort_mode = InboxSortMode.newest,
        cached_participants = emptyMap(),
        sticky_participants = emptyMap(),
        grouping_enabled = grouping,
    )

    private fun single(tab: String, grouping: Boolean = true) = build_thread_rows(
        emails = mail,
        categories_enabled = true,
        active_category = tab,
        active_tabs = CATEGORY_TABS,
        sort_mode = InboxSortMode.newest,
        cached_participants = emptyMap(),
        sticky_participants = emptyMap(),
        grouping_enabled = grouping,
    ).rows

    @Test
    fun one_pass_builds_the_same_rows_as_each_tab_on_its_own() {
        for (grouping in listOf(true, false)) {
            val all = tabs(grouping)
            for (tab in CATEGORY_TABS) {
                assertEquals("$tab grouping=$grouping", single(tab, grouping), tab_rows_for(all, true, tab))
            }
        }
    }

    @Test
    fun every_enabled_tab_is_ready_before_it_is_opened() {
        val all = tabs()
        assertTrue(CATEGORY_TABS.all { it in all.by_tab })
        assertEquals(listOf("p1", "p2"), tab_rows_for(all, true, "primary").map { it.thread_id })
        assertEquals(listOf("u1", "t-u"), tab_rows_for(all, true, "updates").map { it.thread_id })
        assertEquals(listOf("m1"), tab_rows_for(all, true, "promotions").map { it.thread_id })
    }

    @Test
    fun categories_off_uses_the_whole_inbox() {
        val all = build_tab_thread_rows(
            emails = mail,
            categories_enabled = false,
            active_tabs = CATEGORY_TABS,
            sort_mode = InboxSortMode.newest,
            cached_participants = emptyMap(),
            sticky_participants = emptyMap(),
        )
        assertEquals(6, tab_rows_for(all, false, "updates").size)
    }

    @Test
    fun switching_tabs_on_unchanged_mail_is_a_category_only_change() {
        val gate = InboxThreadGate()
        assertFalse(gate.observe("inbox", "primary", 7))
        assertTrue(gate.observe("inbox", "updates", 7))
        assertTrue(gate.observe("inbox", "updates", 7))
        assertFalse(gate.observe("inbox", "updates", 8))
        assertFalse(gate.observe("archive", "social", 8))
    }
}
