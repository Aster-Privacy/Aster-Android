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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

package org.astermail.android.ui.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadCountRefreshingTest {
    @Test
    fun an_inbox_count_from_categories_dims_while_the_list_refreshes() {
        assertTrue(unread_count_refreshing("inbox", category_counts = true, stats_refreshing = false, list_refreshing = true))
        assertFalse(unread_count_refreshing("inbox", category_counts = true, stats_refreshing = true, list_refreshing = false))
    }

    @Test
    fun an_inbox_count_from_stats_dims_while_the_stats_refresh() {
        assertTrue(unread_count_refreshing("inbox", category_counts = false, stats_refreshing = true, list_refreshing = false))
        assertFalse(unread_count_refreshing("inbox", category_counts = false, stats_refreshing = false, list_refreshing = true))
    }

    @Test
    fun other_folders_follow_their_own_count_source() {
        assertTrue(unread_count_refreshing("drafts", category_counts = true, stats_refreshing = true, list_refreshing = false))
        assertFalse(unread_count_refreshing("archive", category_counts = false, stats_refreshing = true, list_refreshing = true))
    }
}
