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

import org.astermail.android.mail.IndexProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchStatusTest {

    private fun progress(indexed: Int, total: Int, waiting: Boolean = false) =
        IndexProgress(indexed = indexed, total = total, started_at_ms = 0L, waiting = waiting)

    private fun status(
        has_query: Boolean = true,
        metadata_indexing: Boolean = false,
        paused: Boolean = false,
        metadata: IndexProgress? = null,
        body: IndexProgress? = null,
        partial: Boolean = false,
    ) = search_status_of(has_query, metadata_indexing, paused, metadata, body, partial)

    @Test
    fun nothing_running_shows_no_status() {
        assertNull(status())
    }

    @Test
    fun finished_body_progress_shows_no_status() {
        assertNull(status(body = progress(10, 10)))
    }

    @Test
    fun metadata_indexing_wins_over_body_progress() {
        val shown = status(metadata_indexing = true, metadata = progress(3, 9), body = progress(1, 50))
        assertEquals(SearchStatusKind.indexing_mail, shown?.kind)
        assertEquals(3, shown?.done)
        assertEquals(9, shown?.total)
        assertEquals(SearchStatusAction.pause, shown?.action)
    }

    @Test
    fun metadata_indexing_without_counts_has_no_fraction() {
        val shown = status(metadata_indexing = true)
        assertEquals(SearchStatusKind.indexing_mail, shown?.kind)
        assertNull(shown?.fraction)
    }

    @Test
    fun body_progress_shows_text_indexing() {
        val shown = status(body = progress(25, 100))
        assertEquals(SearchStatusKind.indexing_text, shown?.kind)
        assertEquals(0.25f, shown?.fraction ?: -1f, 0.0001f)
    }

    @Test
    fun metered_network_shows_waiting_without_action() {
        val shown = status(body = progress(25, 100, waiting = true))
        assertEquals(SearchStatusKind.waiting_for_wifi, shown?.kind)
        assertNull(shown?.action)
    }

    @Test
    fun paused_keeps_body_counts_and_offers_resume() {
        val shown = status(paused = true, body = progress(40, 80))
        assertEquals(SearchStatusKind.paused, shown?.kind)
        assertEquals(40, shown?.done)
        assertEquals(SearchStatusAction.resume, shown?.action)
    }

    @Test
    fun paused_during_metadata_uses_metadata_counts() {
        val shown = status(paused = true, metadata_indexing = true, metadata = progress(2, 4), body = progress(1, 80))
        assertEquals(SearchStatusKind.paused, shown?.kind)
        assertEquals(4, shown?.total)
    }

    @Test
    fun partial_needs_a_query() {
        assertNull(status(has_query = false, partial = true))
        val shown = status(partial = true)
        assertEquals(SearchStatusKind.partial, shown?.kind)
        assertEquals(SearchStatusAction.retry, shown?.action)
        assertNull(shown?.fraction)
    }

    @Test
    fun indexing_shows_without_a_query_so_the_layout_does_not_shift() {
        assertEquals(SearchStatusKind.indexing_text, status(has_query = false, body = progress(1, 2))?.kind)
    }
}
