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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlSanitizerTest {

    @Test
    fun keeps_downlevel_revealed_button_and_drops_mso_fallback() {
        val html = """
            <html><body>
            <p>please confirm your email by clicking the button below:</p>
            <!--[if mso]>
            <v:roundrect xmlns:v="urn:schemas-microsoft-com:vml" href="https://example.com/confirm" style="height:40px;width:220px;">
            <center>Confirm email subscription</center>
            </v:roundrect>
            <![endif]-->
            <!--[if !mso]><!-->
            <a href="https://example.com/confirm" style="background-color:#000000;color:#ffffff;padding:12px 24px;">Confirm email subscription button</a>
            <!--<![endif]-->
            <p>Rest assured, we respect your privacy.</p>
            </body></html>
        """.trimIndent()
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("Confirm email subscription button"))
        assertTrue(out.contains("https://example.com/confirm"))
        assertFalse(out.contains("roundrect"))
        assertFalse(out.contains("urn:schemas-microsoft-com"))
        assertTrue(out.contains("respect your privacy"))
    }

    @Test
    fun keeps_revealed_content_with_spaced_marker_variant() {
        val html = """<!--[if !mso]> <!-- --><a href="https://example.com/go">Go now</a><!-- <![endif]-->"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("Go now"))
        assertTrue(out.contains("https://example.com/go"))
    }

    @Test
    fun drops_hidden_mso_xml_block() {
        val html = """<!--[if gte mso 9]><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml><![endif]--><p>Hello</p>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("PixelsPerInch"))
        assertTrue(out.contains("Hello"))
    }

    @Test
    fun drops_non_mso_conditional_comment_content() {
        val html = """<!--[if IE]><p>ie only</p><![endif]--><p>everyone</p>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("ie only"))
        assertTrue(out.contains("everyone"))
    }

    @Test
    fun revealed_button_survives_when_hidden_block_follows() {
        val html = """
            <!--[if !mso]><!--><a href="https://example.com/a">Button A</a><!--<![endif]-->
            <!--[if mso]><p>outlook only</p><![endif]-->
        """.trimIndent()
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("Button A"))
        assertFalse(out.contains("outlook only"))
    }

    @Test
    fun still_strips_scripts_and_forms() {
        val html = """<p>hi</p><script>alert(1)</script><form action="https://evil.example"><input name="x"></form>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("alert(1)"))
        assertFalse(out.contains("evil.example"))
        assertTrue(out.contains("hi"))
    }
}
