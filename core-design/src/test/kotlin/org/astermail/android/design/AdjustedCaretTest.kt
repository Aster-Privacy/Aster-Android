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

package org.astermail.android.design

import org.astermail.android.design.components.adjusted_caret
import org.junit.Assert.assertEquals
import org.junit.Test

class AdjustedCaretTest {
    @Test
    fun unchanged_text_keeps_caret() {
        assertEquals(2, adjusted_caret("abc", "abc", 2))
    }

    @Test
    fun case_change_keeps_caret() {
        assertEquals(2, adjusted_caret("abc", "ABC", 2))
    }

    @Test
    fun rejected_character_moves_caret_back_one() {
        assertEquals(2, adjusted_caret("ab@c", "abc", 3))
    }

    @Test
    fun truncation_at_end_clamps_caret() {
        assertEquals(3, adjusted_caret("abcd", "abc", 4))
    }

    @Test
    fun truncation_after_caret_keeps_caret() {
        assertEquals(2, adjusted_caret("aXbc", "aXb", 2))
    }

    @Test
    fun leading_trim_shifts_caret_back() {
        assertEquals(2, adjusted_caret(" ab", "ab", 3))
    }

    @Test
    fun trailing_trim_keeps_caret() {
        assertEquals(2, adjusted_caret("ab ", "ab", 2))
    }

    @Test
    fun caret_never_exceeds_next_length() {
        assertEquals(0, adjusted_caret("abc", "", 3))
    }
}
