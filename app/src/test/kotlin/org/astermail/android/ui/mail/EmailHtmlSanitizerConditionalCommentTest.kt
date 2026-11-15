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

class EmailHtmlSanitizerConditionalCommentTest {

    private val head_conditional_spaced_close =
        """<!--[if gte mso 9]>       <style type="text/css">         body, table, td { font-family: Arial; font-size: 16px; }       </style>       <xml>         <o:OfficeDocumentSettings>           <o:PixelsPerInch>96</o:PixelsPerInch>         </o:OfficeDocumentSettings>       </xml><!     [endif]-->"""

    private fun newsletter(head_conditional: String): String = """
        <!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
        <html xmlns:v="urn:schemas-microsoft-com:vml" xmlns:o="urn:schemas-microsoft-com:office:office">
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style type="text/css">
              .hide_small { display: block; }
              @media only screen and (max-width: 480px) {
                .wrapper { width: 360px !important; }
                .hide_small { display: none !important; }
              }
            </style>
            $head_conditional
          </head>
          <body style="margin:0;padding:0;"><style type="text/css">div.preheader { display: none !important; }</style>
            <div class="preheader" style="font-size:1px;display:none !important;">Preview line for the inbox</div>
            <table role="presentation" class="wrapper" style="width:600px;margin:auto;" cellpadding="0" cellspacing="0">
              <tr><td><h1>Welcome to the weekly update</h1></td></tr>
              <tr><td><p>Tell us what you like so every issue matches your interests.</p></td></tr>
              <tr><td><a href="https://example.com/account" class="button_cta">Open your account</a></td></tr>
              <tr><td>
                <p>Get the app:</p>
                <!--[if !mso]><!-->
                <p>Follow us on social media</p>
                <!--<![endif]-->
                <!--End of social part-->
              </td></tr>
              <tr><td><p>Example Company, 1 Example Street</p><a href="https://example.com/unsubscribe">Unsubscribe</a></td></tr>
            </table>
          </body>
        </html>
    """.trimIndent()

    private fun render(body: String): String =
        build_email_html(
            body = body,
            is_dark = false,
            fg_hex = "#111111",
            link_hex = "#3b82f6",
            forwarded_label = "Forwarded",
            image_failed_label = "Image failed",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )

    @Test
    fun spaced_endif_in_head_does_not_swallow_the_body() {
        val out = EmailHtmlSanitizer.sanitize(newsletter(head_conditional_spaced_close))

        assertTrue(out.contains("Welcome to the weekly update"))
        assertTrue(out.contains("Tell us what you like"))
        assertTrue(out.contains("https://example.com/account"))
        assertTrue(out.contains("Follow us on social media"))
        assertTrue(out.contains("Unsubscribe"))
        assertFalse(out.contains("PixelsPerInch"))
        assertFalse(out.contains("font-size: 16px"))
    }

    @Test
    fun spaced_endif_keeps_the_responsive_styles_and_preheader_hiding() {
        val out = EmailHtmlSanitizer.sanitize(newsletter(head_conditional_spaced_close))

        assertTrue(out.contains("max-width: 480px"))
        assertTrue(out.contains("div.preheader"))
        assertTrue(Regex("class=\"preheader\"[^>]*display:\\s*none").containsMatchIn(out))
    }

    @Test
    fun spaced_endif_newsletter_renders_its_main_content() {
        val built = render(EmailHtmlSanitizer.sanitize(newsletter(head_conditional_spaced_close)))

        assertTrue(built.contains("Welcome to the weekly update"))
        assertTrue(built.contains("Open your account"))
    }

    @Test
    fun well_formed_head_conditional_still_renders_the_same_body() {
        val well_formed = """<!--[if gte mso 9]><xml><o:OfficeDocumentSettings><o:PixelsPerInch>96</o:PixelsPerInch></o:OfficeDocumentSettings></xml><![endif]-->"""
        val out = EmailHtmlSanitizer.sanitize(newsletter(well_formed))

        assertTrue(out.contains("Welcome to the weekly update"))
        assertTrue(out.contains("Follow us on social media"))
        assertFalse(out.contains("PixelsPerInch"))
    }

    @Test
    fun hidden_block_ends_where_the_comment_ends() {
        val html = """<!--[if mso]><p>outlook only</p><! [endif]--><p>first</p><!--[if !mso]><!--><p>second</p><!--<![endif]--><p>third</p>"""
        val out = EmailHtmlSanitizer.sanitize(html)

        assertFalse(out.contains("outlook only"))
        assertTrue(out.contains("first"))
        assertTrue(out.contains("second"))
        assertTrue(out.contains("third"))
    }

    @Test
    fun hidden_block_closed_with_bang_comment_end_stays_contained() {
        val html = """<!--[if mso]><p>outlook only</p><![endif]--!><p>first</p><!--[if !mso]><!--><p>second</p><!--<![endif]-->"""
        val out = EmailHtmlSanitizer.sanitize(html)

        assertFalse(out.contains("outlook only"))
        assertTrue(out.contains("first"))
        assertTrue(out.contains("second"))
    }

    @Test
    fun repair_comment_markup_keeps_content_after_a_spaced_endif() {
        val repaired = EmailHtmlSanitizer.repair_comment_markup(
            "<!--[if mso]><p>drop</p><!  [endif]--><p>keep</p><!--[if !mso]><!--><p>also keep</p><!--<![endif]-->",
        )

        assertFalse(repaired.contains("drop"))
        assertTrue(repaired.contains("keep"))
        assertTrue(repaired.contains("also keep"))
    }
}
