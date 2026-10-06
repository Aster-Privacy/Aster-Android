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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactBannerLayoutTest {

    private val gap = 6
    private val icon_column = 27
    private val min_text_width = icon_column + 120

    private fun lines(text_width: Int, slot: Int): Int {
        val room = slot - icon_column
        return (text_width + room - 1) / room
    }

    private fun inline(
        available_width: Int,
        actions_width: Int,
        text_width: Int,
        text_may_wrap: Boolean = true,
        on_measure: (Int) -> Unit = {},
    ) = compact_banner_actions_inline(
        available_width = available_width,
        gap = gap,
        actions_width = actions_width,
        text_single_line_width = text_width,
        min_text_width = min_text_width,
        max_text_lines = 3,
        text_may_wrap = text_may_wrap,
    ) { slot ->
        on_measure(slot)
        lines(text_width - icon_column, slot)
    }

    @Test
    fun `text and pill that fit on one line stay inline`() {
        assertTrue(inline(available_width = 375, actions_width = 80, text_width = 200))
    }

    @Test
    fun `a long text wraps beside the pill at phone width`() {
        assertTrue(inline(available_width = 375, actions_width = 150, text_width = 27 + 380))
    }

    @Test
    fun `the text gets the width left beside the pill`() {
        var measured_slot = -1
        inline(available_width = 375, actions_width = 150, text_width = 27 + 380) { measured_slot = it }
        assertEquals(375 - gap - 150, measured_slot)
    }

    @Test
    fun `a narrow screen stacks the pill under the text`() {
        assertFalse(inline(available_width = 280, actions_width = 150, text_width = 27 + 380))
    }

    @Test
    fun `a large font scale stacks the pill under the text`() {
        assertFalse(inline(available_width = 375, actions_width = 300, text_width = 27 + 760))
    }

    @Test
    fun `text that would need more than three lines beside the pill stacks`() {
        assertFalse(inline(available_width = 375, actions_width = 150, text_width = 27 + 4 * 192))
    }

    @Test
    fun `text that may not wrap keeps the single line rule`() {
        assertFalse(inline(available_width = 375, actions_width = 150, text_width = 27 + 380, text_may_wrap = false))
        assertTrue(inline(available_width = 375, actions_width = 80, text_width = 200, text_may_wrap = false))
    }

    @Test
    fun `the text is not measured when it already fits on one line`() {
        var measured = false
        assertTrue(inline(available_width = 375, actions_width = 80, text_width = 200) { measured = true })
        assertFalse(measured)
    }

    @Test
    fun `a summary that fits on one line keeps the actions beside it`() {
        assertTrue(compact_banner_summary_inline(available_width = 347, gap = gap, actions_width = 130, one_line_width = 205, two_row_width = 132))
    }

    @Test
    fun `a summary on two rows keeps the actions beside it when one line is too wide`() {
        assertTrue(compact_banner_summary_inline(available_width = 347, gap = gap, actions_width = 147, one_line_width = 262, two_row_width = 167))
    }

    @Test
    fun `a summary too wide even on two rows moves the actions under it`() {
        assertFalse(compact_banner_summary_inline(available_width = 347, gap = gap, actions_width = 200, one_line_width = 330, two_row_width = 230))
    }
}
