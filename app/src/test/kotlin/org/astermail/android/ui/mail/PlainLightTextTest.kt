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

class PlainLightTextTest {

    @Test
    fun outlook_reply_with_white_paper_reads_plain() {
        val body = "<div style=\"background:white\"><p class=\"MsoNormal\" style=\"color:black\">Thank you for your quick action on this!</p>" +
            "<div style=\"border-top:solid #E1E1E1 1.0pt;background-color:#ffffff\"><b>From:</b> Aster Team</div></div>"
        assertTrue(reads_as_plain_light_text(body))
    }

    @Test
    fun rgb_white_and_transparent_read_plain() {
        assertTrue(reads_as_plain_light_text("<span style=\"background-color: rgb(255, 255, 255)\">Hi</span><p style=\"background:transparent\">there</p>"))
    }

    @Test
    fun highlight_color_keeps_designed_page() {
        assertFalse(reads_as_plain_light_text("<p style=\"background:white\">Hi <span style=\"background:#ffff00\">note</span></p>"))
    }

    @Test
    fun colored_block_keeps_designed_page() {
        assertFalse(reads_as_plain_light_text("<div style=\"background-color:#1a56db\">Sale</div><p style=\"background:#fff\">text</p>"))
    }

    @Test
    fun background_image_keeps_designed_page() {
        assertFalse(reads_as_plain_light_text("<div style=\"background:#fff url(https://x.invalid/a.png)\">hello</div>"))
    }

    @Test
    fun many_images_keep_designed_page() {
        assertFalse(reads_as_plain_light_text("<div style=\"background:#fff\"><img src=\"a\"><img src=\"b\"><img src=\"c\">hi</div>"))
    }

    @Test
    fun no_backgrounds_is_not_overridden() {
        assertFalse(reads_as_plain_light_text("<p>Hello</p>"))
    }

    @Test
    fun plain_reply_renders_dark_without_white_page() {
        val html = build_email_html(
            body = "<div style=\"background:white\"><p style=\"color:black\">Thanks</p></div>",
            is_dark = true,
            fg_hex = "#e5e5e5",
            link_hex = "#8ab4f8",
            forwarded_label = "Forwarded",
            image_failed_label = "Image",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        assertFalse(html.contains("data-white=\"1\""))
        assertTrue(html.contains("data-dark=\"1\""))
        assertFalse(html.contains("background:white"))
    }

    private val outlook_reply_with_signature_table =
        "<div class=\"WordSection1\"><p class=\"MsoNormal\">Thanks, I forwarded the phishing report to our IT team.</p>" +
            "<table class=\"MsoNormalTable\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\" width=\"624\" " +
            "style=\"width:468.0pt\"><tr><td style=\"padding:0in\"><p class=\"MsoNormal\"><b>Michael Example</b><br>" +
            "Example CPA</p></td><td><img src=\"cid:image001.png@01DA\" width=\"120\"></td></tr></table>" +
            "<div style=\"border:none;border-top:solid #E1E1E1 1.0pt;padding:3.0pt 0in 0in 0in\">" +
            "<p class=\"MsoNormal\"><b>From:</b> Aster Team<br><b>Subject:</b> Report</p></div></div>"

    private fun render_dark(body: String): String =
        build_email_html(
            body = body,
            is_dark = true,
            fg_hex = "#e5e5e5",
            link_hex = "#8ab4f8",
            forwarded_label = "Forwarded",
            image_failed_label = "Image",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )

    @Test
    fun outlook_reply_with_a_signature_table_reads_as_correspondence() {
        assertTrue(reads_as_plain_table_correspondence(outlook_reply_with_signature_table))
    }

    @Test
    fun outlook_reply_with_a_signature_table_renders_dark() {
        val html = render_dark(outlook_reply_with_signature_table)
        assertFalse(html.contains("data-white=\"1\""))
        assertTrue(html.contains("data-dark=\"1\""))
    }

    @Test
    fun outlook_reply_with_white_paper_spans_and_a_table_renders_dark() {
        val body = outlook_reply_with_signature_table.replace(
            "<p class=\"MsoNormal\">Thanks",
            "<p class=\"MsoNormal\"><span style=\"background:white\">Thanks</span>",
        )
        assertFalse(render_dark(body).contains("data-white=\"1\""))
    }

    @Test
    fun bgcolor_tables_keep_designed_page() {
        val body = "<table width=\"600\" bgcolor=\"#ffffff\"><tr><td>Monthly update</td></tr></table>"
        assertFalse(reads_as_plain_table_correspondence(body))
        assertTrue(render_dark(body).contains("data-white=\"1\""))
    }

    @Test
    fun body_bgcolor_keeps_designed_page() {
        val body = "<html><body bgcolor=\"#f4f4f4\"><table width=\"600\"><tr><td>Hello</td></tr></table></body></html>"
        assertFalse(reads_as_plain_table_correspondence(body))
    }

    @Test
    fun cell_backgrounds_keep_designed_page() {
        val body = "<table width=\"600\"><tr><td style=\"background-color:#ffffff\">Release notes</td></tr></table>"
        assertFalse(reads_as_plain_table_correspondence(body))
        assertTrue(render_dark(body).contains("data-white=\"1\""))
    }

    @Test
    fun stylesheet_backgrounds_keep_designed_page() {
        val body = "<style>.cell{background:#fff}</style><table width=\"600\"><tr><td class=\"cell\">Hi</td></tr></table>"
        assertFalse(reads_as_plain_table_correspondence(body))
    }

    @Test
    fun image_heavy_tables_keep_designed_page() {
        val body = "<table width=\"600\"><tr><td><img src=\"a\"><img src=\"b\"><img src=\"c\">Sale</td></tr></table>"
        assertFalse(reads_as_plain_table_correspondence(body))
    }

    @Test
    fun empty_tables_are_not_correspondence() {
        assertFalse(reads_as_plain_table_correspondence("<table width=\"600\"><tr><td><img src=\"a\"></td></tr></table>"))
    }

    @Test
    fun bgcolor_cells_are_not_plain_text() {
        assertFalse(reads_as_plain_light_text("<table><tr><td bgcolor=\"#f2f2f2\"><p>Plain line</p></td></tr></table>"))
    }

    private fun dark_page(body: String): String = build_email_html(
        body = body,
        is_dark = true,
        fg_hex = "#E8E8E8",
        link_hex = "#8ab4f8",
        forwarded_label = "Forwarded message",
        image_failed_label = "Image unavailable",
        force_dark_emails = false,
        dyslexia_font = false,
        translate_mode = "off",
    )

    @Test
    fun plain_reply_over_designed_quote_stays_dark() {
        val quoted = "<table width=\"600\" bgcolor=\"#ffffff\"><tr><td style=\"background-color:#1a56db\">" +
            "<img src=\"a\"><img src=\"b\"><img src=\"c\">Verify your account</td></tr></table>"
        val body = "<div class=\"WordSection1\"><p class=\"MsoNormal\">Thanks, we blocked the sender.</p>" +
            "<div style=\"border:none;border-top:solid #E1E1E1 1.0pt;padding:3.0pt 0in 0in 0in\">" +
            "<p class=\"MsoNormal\"><b>From:</b> Aster Team<br><b>Sent:</b> Tuesday<br><b>Subject:</b> Phishing scam</p></div>" +
            quoted + "</div>"
        assertFalse(dark_page(body).contains("data-white=\"1\""))
    }

    @Test
    fun designed_mail_without_quote_keeps_white_page() {
        val body = "<table width=\"600\" bgcolor=\"#ffffff\"><tr><td style=\"background-color:#1a56db\">" +
            "<img src=\"a\"><img src=\"b\"><img src=\"c\">Sale</td></tr></table>"
        assertTrue(dark_page(body).contains("data-white=\"1\""))
    }
}
