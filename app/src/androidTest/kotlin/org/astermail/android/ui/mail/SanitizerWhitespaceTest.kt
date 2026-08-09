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

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.mail.MimeParser
import org.astermail.android.mail.build_plain_text_html
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SanitizerWhitespaceTest {

    private val raw =
        "Content-Transfer-Encoding: quoted-printable\r\n" +
            "Content-Type: text/plain; charset=UTF-8; format=flowed\r\n" +
            "\r\n" +
            "line one =\r\nwrapped\r\n" +
            "\r\n" +
            "line two\r\n"

    @Test
    fun sanitizer_preserves_pre_wrap_whitespace_exactly() {
        val text = MimeParser.parse(raw).text ?: ""
        val html = build_plain_text_html(text)
        val sanitized = EmailHtmlSanitizer.sanitize(html, EmailHtmlSanitizer.SanitizeOptions())

        assertEquals(html, sanitized)
    }

    @Test
    fun blocked_image_pass_preserves_pre_wrap_whitespace_exactly() {
        val text = MimeParser.parse(raw).text ?: ""
        val html = build_plain_text_html(text)
        val blocked = EmailHtmlSanitizer.replace_blocked_images(html, "Image could not be loaded")
        val neutralized = EmailHtmlSanitizer.neutralize_blocked_backgrounds(blocked)
        val proxied = EmailHtmlSanitizer.rewrite_img_through_proxy(
            neutralized,
            "https://mail-content.invalid/proxy",
            allow_external = false,
        )

        assertFalse("the sanitizer indented pre-wrap content", proxied.contains("\n "))
        assertEquals(html, proxied)
    }
}
