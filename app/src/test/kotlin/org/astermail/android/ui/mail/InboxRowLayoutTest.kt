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

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxRowLayoutTest {

    private val all_metrics = listOf(
        inbox_row_metrics("compact"),
        inbox_row_metrics("comfortable"),
        inbox_row_metrics("compact", compact_rows = true),
        inbox_row_metrics("comfortable", compact_rows = true),
    )

    @Test
    fun three_line_rows_are_taller_than_two_line_rows() {
        all_metrics.forEach { m ->
            assertTrue(m.three_line_min_height > m.min_height)
            assertEquals(m.min_height, m.min_height_for(false))
            assertEquals(m.three_line_min_height, m.min_height_for(true))
        }
    }

    @Test
    fun accessibility_compact_tightens_rows_for_every_density() {
        val compact = inbox_row_metrics("compact")
        val comfortable = inbox_row_metrics("comfortable")
        val tight = inbox_row_metrics("comfortable", compact_rows = true)
        assertTrue(tight.min_height < compact.min_height)
        assertTrue(tight.three_line_min_height < compact.three_line_min_height)
        assertTrue(tight.vertical_padding < compact.vertical_padding)
        assertTrue(compact.min_height < comfortable.min_height)
        assertEquals(tight, inbox_row_metrics("compact", compact_rows = true))
    }

    @Test
    fun skeleton_matches_the_line_count_for_each_metric() {
        all_metrics.forEach { m ->
            assertFalse(skeleton_row_shows_preview(true, m.min_height, m))
            assertTrue(skeleton_row_shows_preview(true, m.three_line_min_height, m))
            assertTrue(skeleton_row_shows_preview(true, 0.dp, m))
            assertFalse(skeleton_row_shows_preview(false, m.three_line_min_height, m))
        }
    }

    @Test
    fun previous_or_exit_closes_the_reader_on_the_first_message() {
        var exited = false
        val exit = { exited = true }
        val action = previous_or_exit(null, exit)
        action()
        assertTrue(exited)
    }

    @Test
    fun previous_or_exit_moves_back_when_a_previous_message_exists() {
        val previous = {}
        assertSame(previous, previous_or_exit(previous) {})
    }
}
