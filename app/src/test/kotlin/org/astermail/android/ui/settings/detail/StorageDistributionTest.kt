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

package org.astermail.android.ui.settings.detail

import org.astermail.android.api.mail.MailUserStatsResponse
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageDistributionTest {

    @Test
    fun `inbox uses item count not thread count`() {
        val stats = MailUserStatsResponse(
            total_items = 500,
            inbox = 83,
            notifiable = 95,
            archived = 284,
            sent = 204,
        )

        assertEquals(95, compute_distribution(stats).inbox)
    }

    @Test
    fun `sent excludes messages already counted as archived`() {
        val stats = MailUserStatsResponse(
            total_items = 500,
            inbox = 83,
            notifiable = 95,
            archived = 284,
            sent = 204,
        )

        assertEquals(121, compute_distribution(stats).sent)
    }

    @Test
    fun `segments sum to every message the mailbox holds`() {
        val stats = MailUserStatsResponse(
            total_items = 500,
            inbox = 83,
            notifiable = 95,
            archived = 284,
            sent = 204,
            drafts = 7,
            spam = 12,
            trash = 30,
        )
        val distribution = compute_distribution(stats)

        assertEquals(109, distribution.sent)
        assertEquals(
            distribution.inbox + distribution.archived + distribution.sent +
                distribution.drafts + distribution.spam + distribution.trash,
            distribution.total,
        )
        assertEquals(stats.total_items + stats.drafts + stats.trash, distribution.total)
    }

    @Test
    fun `sent never exceeds the reported sent count`() {
        val stats = MailUserStatsResponse(
            total_items = 900,
            notifiable = 10,
            archived = 10,
            sent = 40,
        )

        assertEquals(40, compute_distribution(stats).sent)
    }

    @Test
    fun `sent never goes negative when spam and archive overlap`() {
        val stats = MailUserStatsResponse(
            total_items = 100,
            notifiable = 60,
            archived = 50,
            spam = 40,
            sent = 5,
        )

        assertEquals(0, compute_distribution(stats).sent)
    }

    @Test
    fun `falls back to thread count when notifiable is absent`() {
        val stats = MailUserStatsResponse(
            total_items = 200,
            inbox = 83,
            notifiable = null,
            archived = 50,
            sent = 70,
        )

        assertEquals(83, compute_distribution(stats).inbox)
        assertEquals(67, compute_distribution(stats).sent)
    }

    @Test
    fun `empty mailbox produces an empty distribution`() {
        val distribution = compute_distribution(MailUserStatsResponse())

        assertEquals(0, distribution.total)
    }
}
