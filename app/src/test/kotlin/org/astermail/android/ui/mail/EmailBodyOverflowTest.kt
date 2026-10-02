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

class EmailBodyOverflowTest {

    private val promo_cell = """
        <td align="center" style="width:33%;padding:22px 8px;" width="33%">
          <div style="font-size:28px;font-weight:900;white-space:nowrap;">3 FOR 2</div>
          <div style="font-size:11px;letter-spacing:0.16em;white-space:nowrap;">THREE ITEMS, PAY FOR TWO</div>
        </td>
    """.trimIndent()

    private val newsletter = """
        <div style="background:#000000;width:100%;">
        <table style="width:100%;background:#000000;" width="100%"><tr><td align="center" style="padding:0 12px;">
        <table style="width:600px;max-width:600px;" width="600">
          <tr><td align="center"><h1>Example Weekly</h1></td></tr>
          <tr><td><table style="width:100%;" width="100%"><tr>$promo_cell$promo_cell$promo_cell</tr></table></td></tr>
          <tr><td align="center"><a href="https://example.com/shop" style="white-space:nowrap;">VISIT EXAMPLE.COM</a></td></tr>
        </table>
        </td></tr></table>
        </div>
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

    private fun style_block(document: String): String =
        document.substringAfter("<style>").substringBefore("</style>")

    @Test
    fun a_newsletter_wider_than_the_screen_is_never_clipped() {
        val css = style_block(render(newsletter))

        assertFalse(
            "the content column must not hide what does not fit",
            css.contains("#m{max-width:100%!important;overflow-x:hidden"),
        )
        assertTrue(
            "content that still does not fit must stay reachable",
            css.contains("#m{max-width:100%!important;overflow-x:auto!important"),
        )
    }

    @Test
    fun nowrap_text_may_wrap_so_a_reflowed_row_fits_the_screen() {
        val css = style_block(render(newsletter))

        assertTrue(
            "inline nowrap must not set the row's minimum width",
            css.contains("#m [style*=\"nowrap\" i],#m [nowrap]{white-space:normal!important}"),
        )
        assertTrue(
            "buttons keep their single line",
            css.contains("#m a.aster-email-button{white-space:nowrap!important"),
        )
    }

    @Test
    fun a_reflowed_newsletter_keeps_the_device_width_viewport() {
        val document = render(newsletter)

        assertTrue(document.contains("data-nl=\"1\""))
        assertTrue(document.contains("content=\"width=device-width,initial-scale=1"))
        assertTrue(document.contains("style=\"width:100%;max-width:600px;\""))
    }

    @Test
    fun narrow_fixed_width_tables_shrink_instead_of_widening_the_row() {
        val body = """<table width="476"><tr>""" +
            """<td><table style="width:222px;background:#353347;"><tr><td>Tile one</td></tr></table></td>""" +
            """<td><table width="222"><tr><td>Tile two</td></tr></table></td>""" +
            """</tr></table>"""

        val out = fluid_narrow_fixed_tables(body)

        assertTrue(out, out.contains("style=\"width:100%;background:#353347;width:100%;max-width:222px!important\""))
        assertTrue(out, out.contains("width=\"100%\" style=\"width:100%;max-width:222px!important\""))
        assertTrue("wide tables are left to the existing rewrite", out.contains("<table width=\"476\">"))
    }

    @Test
    fun small_and_fluid_tables_are_left_alone() {
        val body = """<table style="width:64px;"><tr><td>-</td></tr></table>""" +
            """<table width="100%"><tr><td>full</td></tr></table>"""

        assertEquals(body, fluid_narrow_fixed_tables(body))
    }

    @Test
    fun a_plain_html_message_does_not_get_the_newsletter_wrapping_rules() {
        val css = style_block(render("<p style=\"white-space:nowrap\">Short note</p>"))

        assertFalse(css.contains("#m [style*=\"nowrap\" i]"))
    }
}
