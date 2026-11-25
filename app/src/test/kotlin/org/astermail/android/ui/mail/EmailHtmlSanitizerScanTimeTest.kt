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

import org.astermail.android.mail.strip_non_rendered_blocks
import org.astermail.android.mail.strip_script_like_blocks
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlSanitizerScanTimeTest {

    private fun image(src: String): Element = Element("img").attr("src", src)

    @Test(timeout = 2_000)
    fun an_unclosed_image_set_run_in_a_style_attribute_finishes_quickly() {
        val run = "image-set(".repeat(20_000)
        val out = EmailHtmlSanitizer.sanitize("<div style=\"background:$run\">Hello</div>")
        assertTrue(out.contains("Hello"))
        assertFalse(EmailHtmlSanitizer.has_remote_css_url(run))
        EmailHtmlSanitizer.strip_remote_css_urls("-webkit-cross-fade(".repeat(20_000))
    }

    @Test(timeout = 2_000)
    fun an_unclosed_image_set_run_in_a_style_sheet_finishes_quickly() {
        val out = EmailHtmlSanitizer.sanitize(
            "<html><head><style>p{background:" + "image-set(".repeat(20_000) +
                "}</style></head><body><p>Hello</p></body></html>",
        )
        assertTrue(out.contains("Hello"))
    }

    @Test(timeout = 2_000)
    fun a_moz_document_run_finishes_quickly() {
        val out = EmailHtmlSanitizer.sanitize(
            "<html><head><style>" + "-moz-document ".repeat(16_000) +
                "</style></head><body><p>Hello</p></body></html>",
        )
        assertTrue(out.contains("Hello"))
    }

    @Test(timeout = 2_000)
    fun an_unclosed_font_face_run_finishes_quickly() {
        val out = EmailHtmlSanitizer.sanitize(
            "<html><head><style>" + "@font-face{".repeat(20_000) +
                "</style></head><body><p>Hello</p></body></html>",
        )
        assertTrue(out.contains("Hello"))
    }

    @Test(timeout = 2_000)
    fun an_unclosed_base_tag_run_finishes_quickly() {
        val html = "<base ".repeat(20_000)
        EmailHtmlSanitizer.sanitize(html)
        assertNotNull(EmailHtmlSanitizer.analyze_trackers(html))
    }

    @Test(timeout = 2_000)
    fun unclosed_tag_runs_finish_quickly() {
        for (opener in listOf("<embed ", "<form ", "<meta ", "<link ", "<body ", "<html ", "<!doctype ")) {
            val out = EmailHtmlSanitizer.sanitize(opener.repeat(20_000) + "<p>Hello</p>")
            assertTrue(opener, out.contains("Hello"))
        }
    }

    @Test(timeout = 2_000)
    fun meta_and_link_tags_without_a_match_finish_quickly() {
        val metas = "<meta name=\"a\" content=\"b\">".repeat(10_000)
        val links = "<link rel=\"stylesheet\" href=\"a.css\">".repeat(10_000)
        assertTrue(EmailHtmlSanitizer.sanitize("$metas$links<p>Hello</p>").contains("Hello"))
    }

    @Test(timeout = 2_000)
    fun unclosed_block_runs_finish_quickly() {
        for (opener in listOf("<style>", "<style ", "<title>", "<head ", "<!--", "<?", "<!--[if mso]>")) {
            EmailHtmlSanitizer.sanitize(opener.repeat(20_000) + "<body><p>Hello</p></body>")
            EmailHtmlSanitizer.repair_comment_markup(opener.repeat(20_000))
        }
    }

    @Test(timeout = 2_000)
    fun unclosed_amp_style_runs_finish_quickly() {
        val run = "<style amp4email-boilerplate>".repeat(10_000)
        assertEquals(run, EmailHtmlSanitizer.neutralize_amp_markup(run))
        EmailHtmlSanitizer.sanitize("<style amp4email-boilerplate ".repeat(10_000) + "<p>Hello</p>")
    }

    @Test(timeout = 2_000)
    fun a_long_whitespace_run_finishes_quickly() {
        val out = EmailHtmlSanitizer.sanitize("<p>" + " ".repeat(200_000) + "Hello</p>")
        assertTrue(out.contains("Hello"))
    }

    @Test(timeout = 2_000)
    fun unclosed_script_blocks_before_body_tags_finish_quickly() {
        val run = "<iframe><body>".repeat(20_000)
        assertEquals(" <body>".repeat(20_000), strip_script_like_blocks(run))
        strip_non_rendered_blocks("<style><body>".repeat(20_000))
    }

    @Test(timeout = 2_000)
    fun repeated_tracker_hosts_finish_quickly() {
        for (host in listOf("mailchimp.com", "list-manage.com", "sendgrid.net", "amazonses.com/", "mailgun.org")) {
            assertFalse(host, EmailHtmlSanitizer.is_tracking_pixel(image("https://a." + host.repeat(20_000))))
        }
    }

    @Test(timeout = 2_000)
    fun repeated_background_properties_finish_quickly() {
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(
            "<div style=\"" + "background:".repeat(50_000) + "url(https://img.example/a.png)\">Hello</div>",
        )
        assertTrue(out.contains("Hello"))
    }

    @Test
    fun image_set_and_cross_fade_calls_are_replaced() {
        assertEquals(
            "color:red;background-image:none",
            EmailHtmlSanitizer.strip_remote_css_urls("color:red;background-image:image-set('a.png' 1x)"),
        )
        assertEquals(
            "background:none;color:red",
            EmailHtmlSanitizer.strip_remote_css_urls("background:-webkit-cross-fade('a.png', 'b.png', 50%);color:red"),
        )
        assertTrue(EmailHtmlSanitizer.has_remote_css_url("background:-webkit-image-set('a.png' 1x)"))
        assertFalse(EmailHtmlSanitizer.has_remote_css_url("background:image-set("))
        assertFalse(EmailHtmlSanitizer.has_remote_css_url("color:red"))
    }

    @Test
    fun image_set_in_a_style_sheet_is_replaced() {
        val out = EmailHtmlSanitizer.sanitize(
            "<html><head><style>.a{background:image-set('https://img.example/a.png' 1x)}</style></head>" +
                "<body><p class=\"a\">Hi</p></body></html>",
        )
        assertFalse(out.contains("img.example"))
        assertTrue(out.contains("background:none"))
    }

    @Test
    fun remote_font_faces_are_removed_and_local_ones_kept() {
        val remote = EmailHtmlSanitizer.sanitize(
            "<html><head><style>@font-face{font-family:x;src:url(https://fonts.example/x.woff)}p{color:red}</style>" +
                "</head><body><p>Hi</p></body></html>",
        )
        assertFalse(remote.contains("@font-face"))
        assertTrue(remote.contains("p{color:red}"))
        val local = EmailHtmlSanitizer.sanitize(
            "<html><head><style>@font-face{font-family:x;src:url(data:font/woff2;base64,AAAA)}</style>" +
                "</head><body><p>Hi</p></body></html>",
        )
        assertTrue(local.contains("@font-face"))
    }

    @Test
    fun moz_document_blocks_are_removed() {
        val out = EmailHtmlSanitizer.sanitize(
            "<html><head><style>@-moz-document url-prefix(){p{color:red}} .a{color:blue}</style></head>" +
                "<body><p>Hi</p></body></html>",
        )
        assertFalse(out.contains("moz-document"))
        assertTrue(out.contains(".a{color:blue}"))
    }

    @Test
    fun head_styles_are_extracted_and_commented_styles_skipped() {
        val out = EmailHtmlSanitizer.sanitize(
            "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Title</title>" +
                "<style>p{color:red}</style><!-- <style>q{color:blue}</style> --></head>" +
                "<body class=\"x\"><p>Hi</p></body></html>",
        )
        assertTrue(out.contains("p{color:red}"))
        assertFalse(out.contains("q{color:blue}"))
        assertFalse(out.contains("Title"))
        assertTrue(out.contains("Hi"))
    }

    @Test
    fun document_shell_tags_are_dropped() {
        val out = EmailHtmlSanitizer.sanitize(
            "<base href=\"https://other.example/\"><meta http-equiv=\"refresh\" content=\"0;url=https://other.example/\">" +
                "<link rel=\"preload\" href=\"https://other.example/a.css\"><embed src=\"https://other.example/a.swf\">" +
                "<form action=\"https://other.example/\"><p>Hi</p></form>",
        )
        assertFalse(out.contains("other.example"))
        assertTrue(out.contains("Hi"))
    }

    @Test
    fun conditional_comments_are_still_handled() {
        assertEquals(
            "<p>Hi</p>",
            EmailHtmlSanitizer.repair_comment_markup("<!--[if mso]><p>Desktop</p><![endif]--><p>Hi</p>"),
        )
        assertEquals(
            "<p>Hi</p>",
            EmailHtmlSanitizer.repair_comment_markup("<!--[if !mso]><!--><p>Hi</p>  <!--<![endif]-->"),
        )
        assertEquals(
            "<p>A</p><p>B</p>",
            EmailHtmlSanitizer.repair_comment_markup("<p>A</p><!--[if gte mso 9]><xml>x</xml><![endif]--><p>B</p>"),
        )
    }

    @Test
    fun amp_boilerplate_styles_are_removed() {
        assertEquals(
            "<style>p{}</style><img src=a>",
            EmailHtmlSanitizer.neutralize_amp_markup(
                "<style amp4email-boilerplate>body{visibility:hidden}</style><style>p{}</style><amp-img src=a></amp-img>",
            ),
        )
    }

    @Test
    fun unclosed_script_blocks_skip_to_the_body() {
        assertEquals(" <body>b <body>d", strip_script_like_blocks("<script>a<body>b<script>c<body>d"))
        assertEquals(" w", strip_script_like_blocks("<script>x<body>y<script>z</script>w"))
        assertEquals("<p>a</p> <p>b</p>", strip_script_like_blocks("<p>a</p><iframe src=x></iframe><p>b</p>"))
    }

    @Test
    fun known_tracker_urls_are_still_recognized() {
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(image("https://email.mailgun.org/o/abc")))
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(image("https://a.amazonses.com/abc/open?u=1")))
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(image("https://us1.mailchimp.com/x/track/1")))
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(image("https://u.sendgrid.net/x/wf/1")))
        assertFalse(EmailHtmlSanitizer.is_tracking_pixel(image("https://cdn.mailgun.org/images/logo.png")))
        assertFalse(EmailHtmlSanitizer.is_tracking_pixel(image("https://a.amazonses.com/img/logo.png?open")))
    }

    @Test
    fun solid_backgrounds_keep_their_color() {
        val solid = EmailHtmlSanitizer.neutralize_blocked_backgrounds(
            "<div style=\"background:#ffffff url(https://img.example/a.png)\">x</div>",
        )
        assertFalse(solid.contains("#6b7280"))
        val bare = EmailHtmlSanitizer.neutralize_blocked_backgrounds(
            "<div style=\"background:url(https://img.example/a.png)\">x</div>",
        )
        assertTrue(bare.contains("#6b7280"))
    }
}
