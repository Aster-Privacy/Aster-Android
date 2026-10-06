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

class BlockedRemoteCssTest {

    private val proxy = "https://app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Ftracker.example%2Fp.gif"

    private fun blocked(html: String): String =
        EmailHtmlSanitizer.neutralize_blocked_backgrounds(EmailHtmlSanitizer.sanitize(html))

    private fun assert_no_remote(html: String) {
        val out = blocked(html)
        assertFalse("remote reference survived in: $out", out.contains("tracker.example"))
        assertFalse("proxy reference survived in: $out", out.contains("api/images/v1/proxy"))
    }

    @Test
    fun an_escaped_url_function_is_removed() {
        assert_no_remote("<div style=\"background-image:\\75 rl($proxy)\">x</div>")
    }

    @Test
    fun a_quoted_url_with_a_trailing_space_is_removed() {
        assert_no_remote("<div style=\"background-image:url('$proxy ')\">x</div>")
    }

    @Test
    fun escaped_letters_without_hex_are_removed() {
        assert_no_remote("<div style=\"background:u\\rl($proxy)\">x</div>")
    }

    @Test
    fun six_digit_escapes_and_mixed_case_are_removed() {
        assert_no_remote("<div style=\"background:\\000055R\\00006c($proxy)\">x</div>")
    }

    @Test
    fun a_quoted_url_with_a_closing_parenthesis_inside_is_removed() {
        assert_no_remote("<div style=\"background:url('$proxy&a=)')\">x</div>")
    }

    @Test
    fun an_unterminated_quoted_url_is_removed() {
        assert_no_remote("<div style=\"background:url('$proxy\">x</div>")
    }

    @Test
    fun an_image_set_with_a_bare_string_is_removed() {
        assert_no_remote("<div style=\"background-image:image-set('$proxy' 1x)\">x</div>")
        assert_no_remote("<div style=\"background-image:-webkit-image-set(url($proxy) 1x)\">x</div>")
    }

    @Test
    fun a_style_block_with_an_escaped_url_is_removed() {
        assert_no_remote("<style>.a{background:\\75\\72\\6c($proxy)}</style><p class=\"a\">x</p>")
    }

    @Test
    fun an_escaped_font_face_is_removed() {
        assert_no_remote(
            "<style>@font-face{font-family:x;src:\\75 rl('$proxy ')}</style><p style=\"font-family:x\">x</p>",
        )
        assert_no_remote(
            "<style>@\\66ont-face{font-family:x;src:url($proxy)}</style><p style=\"font-family:x\">x</p>",
        )
    }

    @Test
    fun an_import_is_removed() {
        assert_no_remote("<style>@import '$proxy';</style><p>x</p>")
        assert_no_remote("<style>@\\69mport url($proxy);</style><p>x</p>")
    }

    @Test
    fun local_targets_are_kept() {
        val out = blocked("<div style=\"background-image:url(data:image/png;base64,AAAA)\">x</div>")
        assertTrue(out.contains("data:image/png;base64,AAAA"))
    }

    @Test
    fun escapes_that_are_not_letters_stay_escaped() {
        assertEquals("content:'\\25B6'", EmailHtmlSanitizer.decode_css_escapes("content:'\\25B6'"))
        assertEquals(".\\31 0u{}", EmailHtmlSanitizer.decode_css_escapes(".\\31 0u{}"))
        assertEquals("url(x)", EmailHtmlSanitizer.decode_css_escapes("\\75 r\\6C(x)"))
    }

    @Test
    fun a_blocked_message_never_reaches_the_image_proxy() {
        val action = mail_body_request_action(
            scheme = "https",
            host = "app.astermail.org",
            path = "/api/images/v1/proxy",
            proxied_url = "https://tracker.example/p.gif",
            allow_external = false,
        )
        assertEquals(MailBodyRequestAction.BLOCK, action)
    }

    @Test
    fun an_allowed_message_reaches_only_the_image_proxy() {
        fun action(scheme: String, host: String, path: String, url: String?) =
            mail_body_request_action(scheme, host, path, url, allow_external = true)

        assertEquals(
            MailBodyRequestAction.PROXY,
            action("https", "app.astermail.org", "/api/images/v1/proxy", "https://cdn.example/a.png"),
        )
        assertEquals(MailBodyRequestAction.BLOCK, action("https", "app.astermail.org", "/api/mail/v1/items", "x"))
        assertEquals(MailBodyRequestAction.BLOCK, action("https", "app.astermail.org", "/api/images/v1/proxy", null))
        assertEquals(MailBodyRequestAction.BLOCK, action("https", "tracker.example", "/p.gif", null))
        assertEquals(MailBodyRequestAction.BLOCK, action("http", "app.astermail.org", "/api/images/v1/proxy", "x"))
    }

    @Test
    fun local_content_is_served_whether_or_not_remote_content_is_allowed() {
        for (allow in listOf(true, false)) {
            assertEquals(
                MailBodyRequestAction.LOCAL,
                mail_body_request_action("https", "mail-content.invalid", "/__aster_font/opendyslexic.otf", null, allow),
            )
        }
    }

    @Test
    fun the_policy_names_no_font_host_but_the_local_one() {
        val document = build_email_html(
            body = "<p>hello</p>",
            is_dark = false,
            fg_hex = "#111111",
            link_hex = "#0b57d0",
            forwarded_label = "Forwarded",
            image_failed_label = "Image blocked",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        val font_src = Regex("font-src([^;]*);").find(document)?.groupValues?.get(1).orEmpty()
        val img_src = Regex("img-src([^;]*);").find(document)?.groupValues?.get(1).orEmpty()
        assertFalse(font_src.contains("app.astermail.org"))
        assertTrue(font_src.contains("mail-content.invalid"))
        assertTrue(img_src.contains("https://app.astermail.org/api/images/v1/proxy"))
        assertFalse(img_src.contains("https://app.astermail.org "))
    }
}
