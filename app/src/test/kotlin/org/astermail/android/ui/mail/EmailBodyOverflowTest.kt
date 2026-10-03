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

    private fun reset_email(label: String, font_size: String): String =
        """<table class="email-body_inner" width="570" style="width:570px;margin:0 auto;"><tr><td style="font-size:20px;padding:10px;">""" +
            """<table width="100%"><tr><td align="center">""" +
            """<a href="https://accounts.example.com/reset" style="color:#ffffff;background-color:#1DCBEB;width:100%;padding:10px;display:inline-block;font-size:$font_size;">$label</a>""" +
            """</td></tr></table></td></tr></table>"""

    private fun buttons(document: String) =
        org.jsoup.Jsoup.parse(document).select("a.aster-email-button")

    @Test
    fun a_button_label_too_wide_for_a_phone_may_wrap_between_words() {
        val document = render(EmailHtmlSanitizer.sanitize(reset_email("RESET YOUR PASSWORD", "1.5em")))
        val button = buttons(document).single()

        assertTrue(button.outerHtml(), button.hasAttr(WRAP_BUTTON_LABEL_ATTRIBUTE))
        assertEquals("RESET YOUR PASSWORD", button.text())
        assertTrue(
            style_block(document).contains("#m a.aster-email-button[$WRAP_BUTTON_LABEL_ATTRIBUTE]{white-space:normal!important}"),
        )
    }

    @Test
    fun a_button_label_that_fits_keeps_its_single_line() {
        val inherited = render(EmailHtmlSanitizer.sanitize(reset_email("Reset password", "1em")))
        val small = render(EmailHtmlSanitizer.sanitize(reset_email("Iniciar o inquérito", "13px")))
        val short_in_a_row = render(
            EmailHtmlSanitizer.sanitize(
                """<table width="100%"><tr><td>Steam</td><td>15,60€</td><td>""" +
                    """<a href="https://example.com/deal" style="display:inline-block;padding:12px 24px;font-size:14px;background-color:#279af1;color:#fff">Get deal</a>""" +
                    """</td></tr></table>""",
            ),
        )

        for (document in listOf(inherited, small, short_in_a_row)) {
            val button = buttons(document).single()
            assertFalse(button.outerHtml(), button.hasAttr(WRAP_BUTTON_LABEL_ATTRIBUTE))
        }
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

    private val long_link = "https://accounts.example.com/oauth/authorize?client_id=abc123&state=eyJ" + "aB3xYz9QwErTy".repeat(45)

    private val transactional = "<table><tr><td><p>Hi Ann,</p><p>Or copy and paste this link into your web browser: " +
        "<a href=\"$long_link\">$long_link</a></p></td></tr></table>"

    private fun marked_tokens(document: String): List<String> =
        org.jsoup.Jsoup.parse(document).select("[data-aster-long-token]").map { it.text() }

    @Test
    fun a_plain_html_message_lets_long_unbroken_tokens_wrap_anywhere() {
        val css = style_block(render(transactional))

        assertTrue(css.contains("#m [data-aster-long-token]{overflow-wrap:anywhere}"))
    }

    @Test
    fun a_long_link_in_a_plain_html_table_is_marked_so_it_cannot_widen_the_cell() {
        val document = render(transactional)

        assertEquals(listOf(long_link), marked_tokens(document))
        val anchor = org.jsoup.Jsoup.parse(document).selectFirst("a[href^=https://accounts.example.com]")!!
        assertEquals(long_link, anchor.attr("href"))
        assertEquals(long_link, anchor.text())
    }

    @Test
    fun ordinary_words_and_table_cells_keep_their_wrapping() {
        val body = "<table><tr><td>Description:</td><td>Your monthly subscription renewal</td></tr>" +
            "<tr><td>Reference:</td><td>INV-2026-000123</td></tr></table>"
        val document = render(body)
        val css = style_block(document)

        assertTrue(marked_tokens(document).isEmpty())
        assertTrue(css.contains("td,th{overflow-wrap:break-word}"))
        assertFalse(css.contains("td,th{overflow-wrap:anywhere"))
        assertFalse(css.contains("#m td{overflow-wrap:anywhere"))
    }

    @Test
    fun preformatted_and_code_tokens_are_left_alone() {
        val token = "a".repeat(80)
        val document = render("<table><tr><td><pre>$token</pre><code>$token</code></td></tr></table>")

        assertTrue(marked_tokens(document).isEmpty())
    }

    @Test
    fun newsletters_keep_their_own_wrapping_rules() {
        val document = render(newsletter.replace("VISIT EXAMPLE.COM", long_link))

        assertTrue(marked_tokens(document).isEmpty())
        assertFalse(style_block(document).contains("[data-aster-long-token]"))
    }
}
