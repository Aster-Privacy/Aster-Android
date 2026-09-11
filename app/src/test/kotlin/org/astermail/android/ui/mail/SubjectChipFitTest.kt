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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectChipFitTest {

    private val line_cells = 20
    private val collapsed_lines = 3
    private val spam_chip = listOf(8)

    private fun inline_lines(subject: String, chip_cells: List<Int>): Int {
        val plain = detail_subject_plain_text(subject, chip_cells.size)
        val starts = detail_subject_placeholder_starts(subject.length, chip_cells.size)
        var lines = 1
        var used = 0
        plain.indices.forEach { index ->
            val chip_index = starts.indexOf(index)
            val cells = if (chip_index >= 0) chip_cells[chip_index] else 1
            if (used + cells > line_cells && used > 0) {
                lines += 1
                used = 0
            }
            used += cells
        }
        return lines
    }

    private fun decide(
        subject: String,
        chip_cells: List<Int>,
        max_lines: Int = collapsed_lines,
        has_bounded_width: Boolean = true,
        on_measure: () -> Unit = {},
    ): Boolean = detail_subject_chips_below(
        chip_count = chip_cells.size,
        max_lines = max_lines,
        has_bounded_width = has_bounded_width,
    ) {
        on_measure()
        inline_lines(subject, chip_cells)
    }

    @Test
    fun short_subject_keeps_spam_chip_inline() {
        assertFalse(decide("Invoice", spam_chip))
    }

    @Test
    fun long_subject_moves_spam_chip_below() {
        val subject = "Your account statement for the quarter is ready to review now ".repeat(4).trim()
        assertTrue(decide(subject, spam_chip))
    }

    @Test
    fun subject_that_fills_the_line_limit_alone_still_moves_chips_below() {
        val subject = "x".repeat(line_cells * collapsed_lines)
        assertEquals(collapsed_lines, inline_lines(subject, emptyList()))
        assertTrue(decide(subject, spam_chip))
    }

    @Test
    fun inline_layout_is_only_chosen_when_every_chip_fits_in_the_line_limit() {
        val chip_sets = listOf(listOf(8), listOf(8, 8), listOf(12, 5, 9, 3))
        chip_sets.forEach { chips ->
            (0..120).forEach { length ->
                val subject = "a".repeat(length)
                if (!decide(subject, chips)) {
                    assertTrue(inline_lines(subject, chips) <= collapsed_lines)
                } else {
                    assertTrue(inline_lines(subject, chips) > collapsed_lines)
                }
            }
        }
    }

    @Test
    fun expanded_subject_stays_inline_without_measuring() {
        var measured = 0
        val below = decide("x".repeat(500), spam_chip, max_lines = Int.MAX_VALUE) { measured += 1 }
        assertFalse(below)
        assertEquals(0, measured)
    }

    @Test
    fun no_chips_stays_inline_without_measuring() {
        var measured = 0
        assertFalse(decide("x".repeat(500), emptyList()) { measured += 1 })
        assertEquals(0, measured)
    }

    @Test
    fun unbounded_width_stays_inline_without_measuring() {
        var measured = 0
        assertFalse(decide("x".repeat(500), spam_chip, has_bounded_width = false) { measured += 1 })
        assertEquals(0, measured)
    }

    @Test
    fun decision_measures_the_inline_text_once_and_is_repeatable() {
        val subject = "x".repeat(70)
        var measured = 0
        val first = decide(subject, spam_chip) { measured += 1 }
        assertEquals(1, measured)
        repeat(5) { assertEquals(first, decide(subject, spam_chip)) }
    }

    @Test
    fun placeholder_starts_point_at_placeholder_characters() {
        val subject = "Subject"
        val plain = detail_subject_plain_text(subject, 3)
        val starts = detail_subject_placeholder_starts(subject.length, 3)
        assertEquals(3, starts.size)
        starts.forEach { start ->
            assertEquals(detail_chip_placeholder_text, plain.substring(start, start + 1))
            assertEquals(detail_chip_placeholder_gap, plain.substring(start - 1, start))
        }
        assertEquals(plain.length, starts.last() + 1)
    }

    @Test
    fun copied_inline_text_never_contains_placeholders() {
        val plain = detail_subject_plain_text("Invoice", 2)
        assertEquals("Invoice", strip_subject_chip_placeholders(plain))
    }
}
