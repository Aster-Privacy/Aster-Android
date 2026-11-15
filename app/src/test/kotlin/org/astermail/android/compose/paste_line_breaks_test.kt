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

package org.astermail.android.compose

import org.astermail.android.ui.compose.align_styled_to_plain
import org.astermail.android.ui.compose.map_styled_range
import org.astermail.android.ui.compose.normalize_paste_line_breaks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class paste_line_breaks_test {

    private fun mapped_text(styled: String, plain: String, word: String): String {
        val map = align_styled_to_plain(styled, plain)
        assertNotNull(map)
        val start = styled.indexOf(word)
        val range = map_styled_range(styled, map!!, start, start + word.length)
        assertNotNull(range)
        return plain.substring(range!!.first, range.second)
    }

    @Test
    fun collapsed_html_text_aligns_with_the_plain_lines() {
        val styled = "Hi Sam, Thanks for writing in. Best regards, Aster Support"
        val plain = "Hi Sam,\nThanks for writing in.\n\nBest regards,\nAster Support"

        assertNotNull(align_styled_to_plain(styled, plain))
        assertEquals("Thanks for writing in.", mapped_text(styled, plain, "Thanks for writing in."))
        assertEquals("regards,\nAster", mapped_text(styled, plain, "regards, Aster"))
    }

    @Test
    fun legacy_paragraph_gaps_align_with_single_plain_breaks() {
        val styled = "one\n\ntwo\n\nthree\n\n"
        val plain = "one\ntwo\nthree"

        assertNotNull(align_styled_to_plain(styled, plain))
        assertEquals("two", mapped_text(styled, plain, "two"))
    }

    @Test
    fun image_placeholders_are_ignored() {
        val styled = "before \uFFFC after"
        val plain = "before\nafter"

        assertEquals("after", mapped_text(styled, plain, "after"))
    }

    @Test
    fun different_text_does_not_align() {
        val styled = "completely different words in this html part of the clip"
        val plain = "nothing in common here at all"

        assertNull(align_styled_to_plain(styled, plain))
    }

    @Test
    fun many_extra_plain_characters_do_not_align() {
        val styled = "short"
        val plain = "short" + "x".repeat(100)

        assertNull(align_styled_to_plain(styled, plain))
    }

    @Test
    fun whitespace_only_ranges_are_skipped() {
        val styled = "a   b"
        val map = align_styled_to_plain(styled, "a\nb")

        assertNull(map_styled_range(styled, map!!, 1, 4))
    }

    @Test
    fun carriage_returns_become_line_feeds() {
        assertEquals("a\nb\nc", normalize_paste_line_breaks("a\r\nb\rc"))
    }
}
