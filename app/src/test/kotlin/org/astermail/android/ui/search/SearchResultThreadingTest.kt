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

package org.astermail.android.ui.search

import io.mockk.mockk
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultThreadingTest {

    private val thread_token = "AlF3kQ2m9tZ7pR1sV8xY4cB6nD0gH5jK2lM7qW9eT3U="

    private val older_id = "11111111-1111-4111-8111-111111111111"

    private val newer_id = "22222222-2222-4222-8222-222222222222"

    private fun item(id: String, token: String?, timestamp: String) = InboxItem(
        id = id, thread_token = token, thread_message_count = 1,
        sender_name = "Aster", sender_email = "noreply@astermail.org",
        subject = "Welcome", preview = "hi",
        timestamp = timestamp,
        is_read = false, is_starred = false, is_encrypted = true,
        has_attachments = false, is_trashed = false, is_archived = false,
        is_spam = false, labels = emptyList(), raw_item = mockk(relaxed = true),
    )

    private fun older(token: String? = thread_token) = item(older_id, token, "2026-07-31T10:00:00Z")

    private fun newer(token: String? = thread_token) = item(newer_id, token, "2026-07-31T18:00:00Z")

    @Test
    fun messages_sharing_a_thread_token_collapse_into_one_row() {
        val threads = search_result_threads(listOf(older(), newer()), grouping_enabled = true)

        assertEquals(1, threads.size)
        assertEquals(2, threads[0].message_count)
        assertEquals(newer_id, threads[0].newest.id)
    }

    @Test
    fun grouping_off_keeps_one_row_per_message() {
        val threads = search_result_threads(listOf(older(), newer()), grouping_enabled = false)

        assertEquals(2, threads.size)
    }

    @Test
    fun rows_are_ordered_newest_first() {
        val threads = search_result_threads(
            listOf(older(token = "token-a"), newer(token = "token-b")),
            grouping_enabled = true,
        )

        assertEquals(listOf("token-b", "token-a"), threads.map { it.thread_id })
    }

    @Test
    fun selecting_a_thread_row_expands_to_every_message_in_the_thread() {
        val corpus = listOf(older(), newer())
        val threads = search_result_threads(corpus, grouping_enabled = true)
        val member_ids = search_thread_member_ids(threads, corpus, grouping_enabled = true)

        val expanded = expand_thread_selection(listOf(newer_id), member_ids, grouping_enabled = true)

        assertEquals(setOf(older_id, newer_id), expanded.toSet())
    }

    @Test
    fun expansion_is_a_no_op_when_grouping_is_off() {
        val corpus = listOf(older(), newer())
        val threads = search_result_threads(corpus, grouping_enabled = false)
        val member_ids = search_thread_member_ids(threads, corpus, grouping_enabled = false)

        val expanded = expand_thread_selection(listOf(newer_id), member_ids, grouping_enabled = false)

        assertEquals(listOf(newer_id), expanded)
    }

    @Test
    fun a_message_the_corpus_does_not_cover_still_selects_itself() {
        val threads = search_result_threads(listOf(older()), grouping_enabled = true)
        val member_ids = search_thread_member_ids(threads, emptyList(), grouping_enabled = true)

        val expanded = expand_thread_selection(listOf(older_id), member_ids, grouping_enabled = true)

        assertEquals(listOf(older_id), expanded)
    }
}
