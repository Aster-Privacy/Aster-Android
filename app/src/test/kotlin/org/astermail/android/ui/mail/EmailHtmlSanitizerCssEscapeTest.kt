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

package org.astermail.android.ui.mail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlSanitizerCssEscapeTest {

    private fun in_block(css: String): String =
        EmailHtmlSanitizer.sanitize("""<html><head><style>$css</style></head><body><p>hi</p></body></html>""")

    private fun in_attribute(css: String): String =
        EmailHtmlSanitizer.sanitize("""<html><body><div style="$css">hi</div></body></html>""")

    private val live_script = Regex("""javascript\s*(?::|\\0*3a|\\:)""", RegexOption.IGNORE_CASE)
    private val live_vbscript = Regex("""vbscript\s*(?::|\\0*3a|\\:)""", RegexOption.IGNORE_CASE)
    private val live_expression = Regex("""expression\s*(?:\(|\\0*28|\\\()""", RegexOption.IGNORE_CASE)

    @Test
    fun an_escaped_colon_after_javascript_is_blocked_in_a_style_block() {
        for (css in listOf(
            ".a{--x:javascript\\3a alert(1)}",
            ".a{--x:javascript\\00003a alert(1)}",
            ".a{--x:\\6a avascript\\:alert(1)}",
            ".a{--x:vbscript\\3a msgbox(1)}",
        )) {
            val out = in_block(css)
            assertFalse(out, live_script.containsMatchIn(out))
            assertFalse(out, live_vbscript.containsMatchIn(out))
        }
    }

    @Test
    fun an_escaped_paren_after_expression_is_blocked() {
        for (css in listOf(
            ".a{width:expression\\28 alert(1)}",
            ".a{width:\\65 xpression\\(alert(1)}",
        )) {
            val out = in_block(css)
            assertFalse(out, live_expression.containsMatchIn(out))
        }
        val inline = in_attribute("width:expression\\28 alert(1))")
        assertFalse(inline, live_expression.containsMatchIn(inline))
    }

    @Test
    fun an_escaped_colon_in_a_style_attribute_is_blocked() {
        val out = in_attribute("--x:javascript\\3a alert(1)")
        assertFalse(out, live_script.containsMatchIn(out))
    }

    @Test
    fun escaped_colons_in_class_selectors_still_render() {
        val out = in_block(".sm\\:w-full{width:100% !important}")
        assertTrue(out, out.contains(".sm\\:w-full"))
    }
}
