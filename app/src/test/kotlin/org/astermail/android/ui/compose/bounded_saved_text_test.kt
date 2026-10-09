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

package org.astermail.android.ui.compose

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class bounded_saved_text_test {
    @Test
    fun short_text_is_saved() {
        assertEquals("hello", bounded_saved_text("hello"))
    }

    @Test
    fun oversized_text_is_not_written_to_the_bundle() {
        assertNull(bounded_saved_text("a".repeat(max_saved_text_chars + 1)))
        assertEquals(max_saved_text_chars, bounded_saved_text("a".repeat(max_saved_text_chars))?.length)
    }
}
