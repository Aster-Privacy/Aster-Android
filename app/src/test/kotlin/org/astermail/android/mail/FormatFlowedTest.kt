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

package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatFlowedTest {

    @Test
    fun joins_soft_wrapped_lines() {
        val input = "This is a long paragraph that was \nsoft wrapped at the sending client."
        assertEquals(
            "This is a long paragraph that was soft wrapped at the sending client.",
            FormatFlowed.unflow(input),
        )
    }

    @Test
    fun keeps_hard_breaks() {
        val input = "line one\nline two\nline three"
        assertEquals("line one\nline two\nline three", FormatFlowed.unflow(input))
    }

    @Test
    fun preserves_paragraph_breaks() {
        val input = "first para soft \nwrapped\n\nsecond para"
        assertEquals("first para soft wrapped\n\nsecond para", FormatFlowed.unflow(input))
    }

    @Test
    fun honors_delsp() {
        val input = "hyphenated word bro \nken across a line"
        assertEquals(
            "hyphenated word broken across a line",
            FormatFlowed.unflow(input, delsp = true),
        )
    }

    @Test
    fun removes_space_stuffing() {
        val input = " From the top\n >quoted looking line\n normal"
        assertEquals("From the top\n>quoted looking line\nnormal", FormatFlowed.unflow(input))
    }

    @Test
    fun signature_separator_is_a_hard_break() {
        val input = "body text\n-- \nSignature line"
        assertEquals("body text\n-- \nSignature line", FormatFlowed.unflow(input))
    }

    @Test
    fun reflows_within_quote_depth_but_not_across() {
        assertEquals("> quoted soft wrapped reply", FormatFlowed.unflow("> quoted soft \n> wrapped reply"))
        assertEquals("> outer soft \n>> inner reply", FormatFlowed.unflow("> outer soft \n>> inner reply"))
    }

    @Test
    fun looks_flowed_detects_soft_wraps() {
        assertTrue(FormatFlowed.looks_flowed("wrapped line here \ncontinuation"))
        assertFalse(FormatFlowed.looks_flowed("line one\nline two"))
        assertFalse(FormatFlowed.looks_flowed("body\n-- \nSignature"))
    }

    private fun render(body_source: String): String {
        val raw = if (FormatFlowed.looks_flowed(body_source)) {
            FormatFlowed.unflow(body_source)
        } else {
            body_source
        }
        val escaped = raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        return "<div style=\"white-space:pre-wrap;overflow-wrap:break-word\">${escaped.replace("\n", "<br>")}</div>"
    }

    @Test
    fun render_pipeline_reflows_real_flowed_message() {
        val flowed = listOf(
            "Hi team,",
            "",
            "This paragraph was soft wrapped at seventy two ",
            "columns by the sending client and should reflow ",
            "into one line.",
        ).joinToString("\n")

        val html = render(flowed)

        assertTrue(
            html.contains(
                "This paragraph was soft wrapped at seventy two columns by the sending client and should reflow into one line.",
            ),
        )
        assertFalse(html.contains("seventy two <br>"))
    }

    @Test
    fun render_pipeline_leaves_fixed_code_untouched() {
        val code = listOf(
            "  fun flow(text: String) =",
            "      text.trim()",
        ).joinToString("\n")

        val html = render(code)

        assertTrue(html.contains("fun flow(text: String) =<br>"))
        assertFalse(html.contains("fun flow(text: String) =      text.trim()"))
    }
}
