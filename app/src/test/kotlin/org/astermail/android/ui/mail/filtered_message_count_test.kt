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

import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.ui.settings.detail.compute_distribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class filtered_message_count_test {
    private val stats = MailUserStatsResponse(
        total_items = 900,
        inbox = 500,
        sent = 250,
        drafts = 12,
        archived = 120,
        spam = 30,
        trash = 44,
    )

    @Test
    fun `uses the storage numbers for system folders`() {
        val distribution = compute_distribution(stats)
        val expected = mapOf(
            "inbox" to distribution.inbox,
            "archive" to distribution.archived,
            "sent" to distribution.sent,
            "drafts" to distribution.drafts,
            "spam" to distribution.spam,
            "trash" to distribution.trash,
        )
        expected.forEach { (folder, count) ->
            assertEquals(
                count,
                filtered_message_count(FilterType.folder, folder, stats, 7, 25, has_more = true),
            )
        }
    }

    @Test
    fun `stays the same while more pages load`() {
        val counts = listOf(25, 50, 75, 100).map { rows ->
            filtered_message_count(FilterType.folder, "archive", stats, null, rows, has_more = true)
        }
        assertEquals(listOf(120, 120, 120, 120), counts)
    }

    @Test
    fun `uses the server total for labels and aliases while paging`() {
        listOf(25, 50, 75).forEach { rows ->
            assertEquals(210, filtered_message_count(FilterType.label, "tok", stats, 210, rows, has_more = true))
            assertEquals(64, filtered_message_count(FilterType.alias, "tok", stats, 64, rows, has_more = true))
        }
    }

    @Test
    fun `uses the server total for custom folders`() {
        assertEquals(88, filtered_message_count(FilterType.folder, "custom_token", stats, 88, 25, has_more = true))
    }

    @Test
    fun `hides the count while paging without a total`() {
        assertNull(filtered_message_count(FilterType.label, "tok", null, null, 25, has_more = true))
        assertNull(filtered_message_count(FilterType.folder, "inbox", null, -1, 25, has_more = true))
    }

    @Test
    fun `counts loaded rows once the folder is fully loaded`() {
        assertEquals(9, filtered_message_count(FilterType.label, "tok", null, null, 9, has_more = false))
    }

    @Test
    fun `shows zero for an empty folder`() {
        assertEquals(0, filtered_message_count(FilterType.label, "tok", null, 0, 0, has_more = false))
    }
}
