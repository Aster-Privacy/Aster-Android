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

package org.astermail.android.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LatestCodeInputTest {
    @Test
    fun keeps_short_input_unchanged() {
        assertEquals("123456", latest_code_input(ascii_digits("123456"), 6))
    }

    @Test
    fun keeps_pasted_code_when_field_already_full() {
        assertEquals("654321", latest_code_input(ascii_digits("123456" + "654321"), 6))
    }

    @Test
    fun keeps_pasted_code_after_partial_typing() {
        assertEquals("654321", latest_code_input(ascii_digits("12" + "654321"), 6))
    }

    @Test
    fun keeps_backup_code_when_field_already_full() {
        assertEquals(
            "MNOP-QRST-UVWX",
            latest_code_input("ABCD-EFGH-IJKL" + "MNOP-QRST-UVWX", 14),
        )
    }
}
