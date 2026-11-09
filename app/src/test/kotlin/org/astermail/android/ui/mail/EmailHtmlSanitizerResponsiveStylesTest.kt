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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlSanitizerResponsiveStylesTest {

    private val greeting = "Hallo Max, hier sind deine Angebote der Woche"
    private val footer = "Impressum Beispiel Markt GmbH Abmelden"
    private val reveal_rule = "display:block!important"
    private val preheader =
        """<div style="display:none;font-size:1px;max-height:0px;max-width:0px;opacity:0;overflow:hidden;mso-hide:all">Deine Wochenangebote &zwnj; &nbsp;</div>"""

    private fun mobile_block(): String =
        """<div class="m" style="display:none;max-height:0;overflow:hidden"><h1>$greeting</h1><p>Kaffee 1kg nur 9,99</p></div>"""

    private fun footer_table(): String =
        """<table width="600" bgcolor="#e5e5e5"><tr><td>$footer</td></tr></table>"""

    private val mobile_query =
        "@media only screen and (max-width:600px){ .m{display:block!important;max-height:none!important;overflow:visible!important} .d{display:none!important} }"

    private fun assert_reveals_mobile_content(out: String) {
        assertTrue(out.contains(greeting))
        assertTrue(out.contains(footer))
        assertTrue(out.contains("max-width:600px"))
        assertTrue(out.contains(reveal_rule))
        assertTrue(out.indexOf("<style>") < out.indexOf(greeting))
    }

    @Test
    fun keeps_a_style_block_between_html_and_body_without_a_head() {
        val html = """<html><style type="text/css">$mobile_query</style><body>$preheader<table width="600"><tr><td><div class="d" style="display:none;max-height:0;overflow:hidden"></div>${mobile_block()}${footer_table()}</td></tr></table></body></html>"""
        assert_reveals_mobile_content(EmailHtmlSanitizer.sanitize(html))
    }

    @Test
    fun keeps_a_style_block_placed_after_the_closing_body_tag() {
        val html = """<html><head></head><body><table width="600"><tr><td>${mobile_block()}${footer_table()}</td></tr></table></body><style>$mobile_query</style></html>"""
        assert_reveals_mobile_content(EmailHtmlSanitizer.sanitize(html))
    }

    @Test
    fun keeps_a_style_block_placed_before_the_html_tag() {
        val html = """<style>$mobile_query</style><html><head><meta charset="utf-8"></head><body><table width="640"><tr><td>${mobile_block()}${footer_table()}</td></tr></table></body></html>"""
        assert_reveals_mobile_content(EmailHtmlSanitizer.sanitize(html))
    }

    @Test
    fun keeps_a_head_style_once_when_the_document_is_split() {
        val html = """<!doctype html><html><head><meta charset="utf-8"><style>$mobile_query</style></head><body>$preheader${mobile_block()}${footer_table()}</body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assert_reveals_mobile_content(out)
        assertEquals(1, Regex("max-width:600px").findAll(out).count())
    }

    @Test
    fun keeps_head_and_trailing_styles_in_document_order() {
        val html = """<html><head><style>.m{color:#111111}</style></head><body>${mobile_block()}</body><style>.m{color:#222222}</style></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.indexOf("#111111") in 0 until out.indexOf("#222222"))
    }

    @Test
    fun keeps_head_styles_behind_office_only_settings() {
        val html = """<html xmlns:o="urn:schemas-microsoft-com:office:office"><head><!--[if gte mso 9]><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml><![endif]--><style>$mobile_query</style></head><body>${mobile_block()}${footer_table()}</body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assert_reveals_mobile_content(out)
        assertFalse(out.contains("OfficeDocumentSettings"))
    }

    @Test
    fun keeps_the_mobile_first_desktop_swap_and_drops_office_conditionals() {
        val html = """<!doctype html><html lang="de"><head><!--[if mso]><style>.mob{display:none!important}</style><![endif]--><style type="text/css">.desk{display:none;max-height:0;overflow:hidden;mso-hide:all} @media only screen and (min-width:620px){ .desk{display:block!important;max-height:none!important} .mob{display:none!important} }</style></head><body>$preheader<!--[if mso]><table width="600"><tr><td><![endif]--><!--[if !mso]><!--><div class="mob"><h1>$greeting</h1></div><!--<![endif]--><div class="desk" style="display:none;max-height:0;overflow:hidden"><h1>$greeting</h1></div>${footer_table()}<!--[if mso]></td></tr></table><![endif]--></body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("""class="mob""""))
        assertTrue(out.contains("min-width:620px"))
        assertFalse(out.contains("[if mso]"))
        assertTrue(out.contains(footer))
    }

    @Test
    fun still_sanitizes_a_style_block_outside_the_head() {
        val html = """<html><style>@import url(https://tracker.invalid/a.css); .m{background:url(https://tracker.invalid/p.gif);width:expression(alert(1))} $mobile_query</style><body>${mobile_block()}</body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("@import", ignoreCase = true))
        assertFalse(out.contains("expression(", ignoreCase = true))
        assertFalse(out.contains("tracker.invalid/a.css"))
        assertTrue(out.contains(reveal_rule))
    }

    @Test
    fun does_not_keep_markup_after_the_body_as_a_style() {
        val html = """<html><body>${mobile_block()}</body><script>alert(1)</script><style>.m{display:block!important}</style></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("<script", ignoreCase = true))
        assertFalse(out.contains("alert(1)"))
        assertTrue(out.contains(reveal_rule))
    }

    @Test
    fun leaves_a_fragment_without_a_body_tag_unchanged() {
        val html = """<style>.m{display:block!important}</style>${mobile_block()}"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains(greeting))
        assertEquals(1, Regex("display:block!important").findAll(out).count())
    }

    @Test
    fun ignores_a_style_block_that_is_commented_out() {
        val html = """<!-- <style>.m{color:#abcdef}</style> --><html><head><!-- <style>.m{color:#fedcba}</style> --></head><body>${mobile_block()}</body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("#abcdef"))
        assertFalse(out.contains("#fedcba"))
        assertTrue(out.contains(greeting))
    }

    @Test
    fun keeps_a_legacy_style_wrapped_in_comment_markers() {
        val html = """<html><head><style type="text/css"><!-- $mobile_query --></style></head><body>${mobile_block()}${footer_table()}</body></html>"""
        assert_reveals_mobile_content(EmailHtmlSanitizer.sanitize(html))
    }

    @Test
    fun keeps_a_style_revealed_to_every_client_except_office() {
        val html = """<html><head><!--[if !mso]><!--><style>$mobile_query</style><!--<![endif]--></head><body>${mobile_block()}${footer_table()}</body></html>"""
        assert_reveals_mobile_content(EmailHtmlSanitizer.sanitize(html))
    }
}
