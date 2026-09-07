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
import org.junit.Test

class SubjectCopyTest {

    @Test
    fun copied_subject_drops_inline_chip_placeholders() {
        val copied = "Quarterly report" + detail_chip_placeholder_gap + detail_chip_placeholder_text +
            detail_chip_placeholder_gap + detail_chip_placeholder_text
        assertEquals("Quarterly report", strip_subject_chip_placeholders(copied))
    }

    @Test
    fun partial_selection_ending_inside_subject_is_untouched() {
        assertEquals("Quarterly rep", strip_subject_chip_placeholders("Quarterly rep"))
    }

    @Test
    fun inner_no_break_spaces_survive() {
        val subject = "Re:" + detail_chip_placeholder_gap + "hello"
        assertEquals(subject, strip_subject_chip_placeholders(subject))
    }

    @Test
    fun placeholder_constants_match_the_layout_glyphs() {
        assertEquals("\u00A0", detail_chip_placeholder_gap)
        assertEquals("\u200B", detail_chip_placeholder_text)
    }
}
