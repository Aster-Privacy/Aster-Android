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

    @Test
    fun keeps_content_inside_form_wrapper() {
        val html = """<form action="https://sender.example/submit"><p>survey question</p><a href="https://sender.example/answer">Answer here</a></form>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("survey question"))
        assertTrue(out.contains("https://sender.example/answer"))
        assertFalse(out.contains("<form"))
        assertFalse(out.contains("sender.example/submit"))
    }

    @Test
    fun unwraps_unknown_tags_keeping_content() {
        val html = """<mj-text><p>mj inside</p></mj-text><o:p><span>office text</span></o:p>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("mj inside"))
        assertTrue(out.contains("office text"))
    }

    @Test
    fun strips_tracking_params_from_links() {
        val html = """<a href="https://x.example/p?utm_source=nl&utm_campaign=c&id=5&fbclid=abc">link</a>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("https://x.example/p?id=5"))
        assertFalse(out.contains("utm_source"))
        assertFalse(out.contains("fbclid"))
    }

    @Test
    fun keeps_links_without_tracking_params_unchanged() {
        val url = "https://x.example/p?id=5&page=2"
        val out = EmailHtmlSanitizer.sanitize("""<a href="$url">link</a>""")
        assertTrue(out.contains(url.replace("&", "&amp;")))
    }

    @Test
    fun autolinks_bare_urls_in_text() {
        val out = EmailHtmlSanitizer.sanitize("<p>visit https://example.com/page now</p>")
        assertTrue(out.contains("""<a href="https://example.com/page""""))
        assertTrue(out.contains("visit"))
        assertTrue(out.contains("now"))
    }

    @Test
    fun does_not_autolink_inside_existing_anchor() {
        val out = EmailHtmlSanitizer.sanitize("""<a href="https://a.example"><span>see https://b.example</span></a>""")
        assertFalse(out.contains("""href="https://b.example""""))
    }

    @Test
    fun replaces_blocked_remote_images_with_placeholder() {
        val html = """<p>text</p><img src="https://x.example/banner.png" alt="Banner" width="600" height="200">"""
        val out = EmailHtmlSanitizer.replace_blocked_images(html, "[Image blocked]")
        assertFalse(out.contains("<img"))
        assertTrue(out.contains("blocked-image"))
        assertTrue(out.contains("data-original-src=\"https://x.example/banner.png\""))
        assertTrue(out.contains(">Banner</span>"))
        assertTrue(out.contains("text"))
    }

    @Test
    fun blocked_image_without_alt_uses_placeholder_text() {
        val out = EmailHtmlSanitizer.replace_blocked_images(
            """<img src="https://x.example/hero.jpg" width="600" height="300" style="display:block">""",
            "[Image blocked]",
        )
        assertTrue(out.contains("[Image blocked]"))
    }

    @Test
    fun blocked_tracking_pixels_are_removed_entirely() {
        val out = EmailHtmlSanitizer.replace_blocked_images(
            """<p>hi</p><img src="https://track.example/o.gif" width="1" height="1">""",
            "[Image blocked]",
        )
        assertFalse(out.contains("blocked-image"))
        assertFalse(out.contains("track.example"))
        assertTrue(out.contains("hi"))
    }

    @Test
    fun replace_blocked_images_keeps_data_and_cid_images() {
        val html = """<img src="data:image/png;base64,AAAA" alt="inline"><img src="cid:part1" alt="attached">"""
        val out = EmailHtmlSanitizer.replace_blocked_images(html, "[Image blocked]")
        assertTrue(out.contains("data:image/png;base64,AAAA"))
        assertTrue(out.contains("cid:part1"))
        assertFalse(out.contains("blocked-image"))
    }

    @Test
    fun keeps_background_image_attribute_on_table_cells() {
        val html = """
            <table><tr><td background="https://cdn.example/btn.png" bgcolor="#000000">
            <a href="https://ex.com/c" style="color:#ffffff;padding:15px 30px;display:inline-block">Confirm email subscription button</a>
            </td></tr></table>
        """.trimIndent()
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("background=\"https://cdn.example/btn.png\""))
        assertTrue(out.contains("bgcolor=\"#000000\""))
        assertTrue(out.contains("Confirm email subscription button"))
    }

    @Test
    fun keeps_css_background_url_in_inline_style() {
        val html = """<a href="https://ex.com/c" style="background:url('https://cdn.example/btn.png') no-repeat;color:#ffffff">Go</a>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("cdn.example/btn.png"))
        assertTrue(out.contains("url("))
    }

    @Test
    fun keeps_css_background_url_in_style_block() {
        val html = """<html><head><style>.b{background:url(https://cdn.example/btn.png) center;color:#fff}</style></head><body><a class="b" href="https://ex.com/c">Go</a></body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertTrue(out.contains("cdn.example/btn.png"))
    }

    @Test
    fun blocked_background_image_button_gets_readable_placeholder() {
        val html = """<table><tr><td background="https://cdn.example/btn.png"><a href="https://ex.com/c" style="color:#ffffff;padding:15px 30px;display:inline-block">Confirm email subscription button</a></td></tr></table>"""
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("cdn.example/btn.png"))
        assertTrue(out.contains("background-color:#6b7280"))
        assertTrue(out.contains("Confirm email subscription button"))
    }

    @Test
    fun blocked_inline_style_background_gets_placeholder() {
        val html = """<a href="https://ex.com/c" style="background:url('https://cdn.example/btn.png') center;color:#ffffff;padding:15px 30px">Go</a>"""
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("cdn.example/btn.png"))
        assertTrue(out.contains("background-color:#6b7280"))
    }

    @Test
    fun blocked_background_keeps_existing_solid_color_no_placeholder() {
        val html = """<table><tr><td background="https://cdn.example/btn.png" bgcolor="#000000"><a style="color:#fff">x</a></td></tr></table>"""
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("cdn.example/btn.png"))
        assertTrue(out.contains("bgcolor=\"#000000\""))
        assertFalse(out.contains("#6b7280"))
    }

    @Test
    fun blocked_background_shorthand_with_color_keeps_color() {
        val html = """<a style="background:#000000 url('https://cdn.example/btn.png') center;color:#fff">x</a>"""
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("cdn.example/btn.png"))
        assertTrue(out.contains("#000000"))
        assertFalse(out.contains("#6b7280"))
    }

    @Test
    fun neutralize_leaves_non_background_content_alone() {
        val html = """<p style="color:#111">hello <a href="https://ex.com">link</a></p>"""
        val out = EmailHtmlSanitizer.neutralize_blocked_backgrounds(html)
        assertFalse(out.contains("#6b7280"))
        assertTrue(out.contains("hello"))
        assertTrue(out.contains("https://ex.com"))
    }

    @Test
    fun strips_dark_mode_media_from_style_blocks() {
        val html = """<html><head><style>p{color:#111}@media (prefers-color-scheme: dark){p{color:#eee;background:#000}}h1{margin:0}</style></head><body><p>hi</p></body></html>"""
        val out = EmailHtmlSanitizer.sanitize(html)
        assertFalse(out.contains("prefers-color-scheme"))
        assertTrue(out.contains("color:#111"))
        assertTrue(out.contains("h1{margin:0}"))
    }
}
