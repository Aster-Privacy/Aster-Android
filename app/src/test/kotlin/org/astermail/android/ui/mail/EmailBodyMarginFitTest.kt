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

class EmailBodyMarginFitTest {

    private val side_margins = "margin-left: 10px;margin-right: 10px;border-collapse: collapse;"

    private val reminder = """
        <table background="https://example.com/pattern.png" cellpadding="0" cellspacing="0" border="0" style="width: 100% !important;margin: 0;padding: 0;">
          <tbody><tr><td valign="top">
            <div align="center" style="height: 5px;margin-left: 10px;margin-right: 10px;">&nbsp;</div>
            <table width="550" style="$side_margins" cellpadding="0" cellspacing="0" border="0" align="center">
              <tbody><tr><td><a href="https://example.com/"><img src="https://example.com/banner.png" width="550" height="105" style="display: block;"></a></td></tr></tbody>
            </table>
            <table width="550" bgcolor="#FFFFFF" style="$side_margins" cellpadding="0" cellspacing="0" border="0" align="center">
              <tbody><tr><td valign="top" align="center">
                <table width="530" bgcolor="#FFFFFF" style="$side_margins" cellpadding="0" cellspacing="0" border="0" align="center">
                  <tbody><tr><td align="left">
                    <h1 style="display: inline;line-height: 32px;">&nbsp;New notification from Example</h1>
                    <p style="margin: 1em 0;">Hello, you have a new message waiting. Sign in to read it.</p>
                    <a href="https://example.com/" style="display: inline-block;width: 200px;">Sign in</a>
                  </td></tr></tbody>
                </table>
              </td></tr></tbody>
            </table>
            <table width="550"><tr><td style="text-align: center;">To change your notifications, sign in at example.com.</td></tr></table>
          </td></tr></tbody>
        </table>
    """.trimIndent()

    private fun render(body: String): String =
        build_email_html(
            body = body,
            is_dark = true,
            fg_hex = "#e5e5e5",
            link_hex = "#8ab4f8",
            forwarded_label = "Forwarded",
            image_failed_label = "Image failed",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )

    private fun content_of(document: String): org.jsoup.nodes.Element =
        org.jsoup.Jsoup.parse(document).getElementById("m")!!

    private fun last_width(style: String): String =
        style.split(';')
            .map { it.trim() }
            .last { it.substringBefore(':').trim().equals("width", ignoreCase = true) }
            .substringAfter(':')
            .trim()

    @Test
    fun fixed_width_tables_with_side_margins_fit_inside_their_container() {
        val tables = content_of(render(reminder)).select("table[bgcolor], table[align]")

        assertEquals(3, tables.size)
        for (table in tables) {
            assertEquals(
                "a fluid table must give back the room its side margins take",
                "calc(100% - 10px - 10px)!important",
                last_width(table.attr("style")),
            )
        }
    }

    @Test
    fun the_full_width_wrapper_and_plain_tables_keep_their_width() {
        val content = content_of(render(reminder))
        val wrapper = content.selectFirst("table[background]")!!
        val footer = content.select("table").last()!!

        assertFalse(wrapper.attr("style").contains("calc("))
        assertEquals("100%", footer.attr("width"))
        assertFalse(footer.attr("style").contains("calc("))
    }

    @Test
    fun margin_shorthand_is_read_for_both_sides() {
        val fitted = fit_full_width_margins("""<table width="100%" style="margin:0 12px 0 8px"><tr><td>x</td></tr></table>""")

        assertTrue(fitted, fitted.contains("width:calc(100% - 8px - 12px)!important"))
    }

    @Test
    fun one_sided_and_relative_margins_are_subtracted() {
        val fitted = fit_full_width_margins("""<div style="width:100%;margin-left:1.5em">x</div>""")

        assertTrue(fitted, fitted.contains("width:calc(100% - 1.5em)!important"))
    }

    @Test
    fun auto_zero_and_negative_margins_are_left_alone() {
        val bodies = listOf(
            """<table width="100%" style="margin:0 auto"><tr><td>x</td></tr></table>""",
            """<table width="100%" style="margin:0"><tr><td>x</td></tr></table>""",
            """<div style="width:100%;margin-left:-10px">x</div>""",
        )

        for (body in bodies) assertEquals(body, fit_full_width_margins(body))
    }

    @Test
    fun elements_that_are_not_full_width_are_left_alone() {
        val bodies = listOf(
            """<table width="320" style="margin:0 10px"><tr><td>x</td></tr></table>""",
            """<div style="width:50%;margin:0 10px">x</div>""",
            """<p style="margin:0 10px">x</p>""",
            """<table width="100%" style="width:300px;margin:0 10px"><tr><td>x</td></tr></table>""",
        )

        for (body in bodies) assertEquals(body, fit_full_width_margins(body))
    }

    @Test
    fun narrow_fixed_tables_with_margins_also_fit() {
        val body = """<table width="300" bgcolor="#FFFFFF" style="margin-left:10px;margin-right:10px"><tr><td>Example</td></tr></table><table><tr><td>a</td></tr></table>"""
        val table = content_of(render(body)).selectFirst("table[bgcolor]")!!

        assertEquals("calc(100% - 10px - 10px)!important", last_width(table.attr("style")))
        assertTrue(table.attr("style").contains("max-width:300px!important"))
    }

    @Test
    fun ordinary_mail_is_not_rewritten() {
        val body = """<div style="margin:0 10px"><p>Hello,</p><p>See you on Friday at example.com.</p></div>"""

        assertTrue(render(body).contains(body))
    }
}
