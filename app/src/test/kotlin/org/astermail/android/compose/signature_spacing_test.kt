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

import org.astermail.android.ui.compose.append_signature
import org.astermail.android.ui.compose.caret_starts_above_signature
import org.astermail.android.ui.compose.insert_template_body
import org.astermail.android.ui.compose.plain_signature_with_separator
import org.astermail.android.ui.compose.seeded_body_with_signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class signature_spacing_test {
    private val signature = plain_signature_with_separator("Adam\nAster", true)
    private val watermark = "\n\nSecured by Aster"

    private fun blank_lines_before_signature(body: String): Int {
        val lines = body.split("\n")
        val first = lines.indexOfFirst { it == signature.lines().first() }
        return lines.take(first).count { it.isBlank() }
    }

    @Test
    fun `new message leaves one empty line above the signature`() {
        val body = seeded_body_with_signature("", signature, "")
        assertEquals(1, blank_lines_before_signature(body))
        assertEquals("\n--\nAdam\nAster", body)
    }

    @Test
    fun `watermark does not add lines above the signature`() {
        val body = seeded_body_with_signature("", signature, watermark)
        assertEquals(1, blank_lines_before_signature(body))
        assertTrue(body.endsWith(watermark))
    }

    @Test
    fun `caret starts on the empty line above the signature`() {
        val body = seeded_body_with_signature("", signature, watermark)
        assertTrue(caret_starts_above_signature(body))
        assertEquals("", body.substring(0, body.indexOf('\n')))
    }

    @Test
    fun `signature content with leading newlines is trimmed`() {
        val padded = plain_signature_with_separator("\n\n\nAdam\n", true)
        assertEquals("--\nAdam", padded)
        assertEquals(1, seeded_body_with_signature("", padded, "").takeWhile { it == '\n' }.length)
    }

    @Test
    fun `shared text keeps one blank line before the signature`() {
        val body = seeded_body_with_signature("Shared link\n\n", signature, "")
        assertEquals("Shared link\n\n--\nAdam\nAster", body)
    }

    @Test
    fun `body without signature is unchanged`() {
        assertEquals("", seeded_body_with_signature("", "", ""))
        assertEquals(watermark, seeded_body_with_signature("", "", watermark))
        assertFalse(caret_starts_above_signature(""))
    }

    @Test
    fun `switching sender on an empty body adds one empty line`() {
        assertEquals("\n--\nAdam\nAster", append_signature("\n", signature))
        assertEquals("\n--\nAdam\nAster", append_signature("", signature))
    }

    @Test
    fun `switching sender after typed text keeps a blank line`() {
        assertEquals("Hello\n\n--\nAdam\nAster", append_signature("Hello", signature))
    }

    @Test
    fun `template keeps the signature after the inserted text`() {
        val body = seeded_body_with_signature("", signature, watermark)
        val merged = insert_template_body(body, "Template", signature, "Secured by Aster")
        assertEquals("Template\n\n--\nAdam\nAster\n\nSecured by Aster", merged)
    }
}
