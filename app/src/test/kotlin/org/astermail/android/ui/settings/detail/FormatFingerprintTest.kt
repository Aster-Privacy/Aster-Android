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

package org.astermail.android.ui.settings.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatFingerprintTest {

    private val sha256_hex = "60F3CB7A6FE251F2FAF93F1B7FA9A8983F8048C231EB58D3CD5CDE9ACDF7FE68"

    @Test
    fun groups_a_sha256_fingerprint_into_four_lines_of_eight_bytes() {
        val out = format_fingerprint(sha256_hex)
        val lines = out.split("\n")
        assertEquals(4, lines.size)
        assertEquals("60 F3 CB 7A 6F E2 51 F2", lines[0])
        assertEquals("CD 5C DE 9A CD F7 FE 68", lines[3])
    }

    @Test
    fun ignores_existing_separators() {
        val spaced = sha256_hex.chunked(4).joinToString(" ")
        assertEquals(format_fingerprint(sha256_hex), format_fingerprint(spaced))
    }

    @Test
    fun keeps_a_trailing_partial_group() {
        assertEquals("AA BB CC", format_fingerprint("AABBCC"))
    }

    @Test
    fun returns_the_input_when_there_is_nothing_to_group() {
        assertEquals("", format_fingerprint(""))
    }
}
