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

class EmailHtmlSanitizerAmpTest {
    private val amp_email = """<!doctype html><html ⚡4email data-css-strict><head><meta charset="utf-8"><style amp4email-boilerplate>body{visibility:hidden}</style><script async src="https://cdn.ampproject.org/v0.js"></script><style amp-custom>.es-wrapper { width:100%; } h1 { font-size:30px; color:#023047; }</style></head>
<body><div class="es-wrapper-color"><table class="es-wrapper" width="100%"><tr><td><h1>Verify your email</h1><p style="font-size: 20px;color: #666666"><strong>482915</strong><br></p><amp-img src="cid:pic1" alt="Banner" width="370" height="255" layout="responsive"></amp-img></td></tr></table></div></body></html>"""

    @Test
    fun does_not_hide_the_body_of_an_amp_email() {
        val out = EmailHtmlSanitizer.sanitize(amp_email)
        assertFalse(out.contains("visibility"))
        assertTrue(out.contains("482915"))
        assertTrue(out.contains(".es-wrapper"))
        assertFalse(out.contains("cdn.ampproject.org"))
    }

    @Test
    fun keeps_an_amp_image_as_a_plain_image() {
        val out = EmailHtmlSanitizer.sanitize(amp_email)
        assertTrue(Regex("<img[^>]*alt=\"Banner\"").containsMatchIn(out))
    }

    @Test
    fun removes_every_amp_boilerplate_style_variant() {
        val out = EmailHtmlSanitizer.neutralize_amp_markup(
            "<style amp4email-boilerplate>body{visibility:hidden}</style>" +
                "<style amp-boilerplate>body{-webkit-animation:-amp-start 8s steps(1,end) 0s 1 normal both}</style>" +
                "<noscript><style amp-boilerplate>body{-webkit-animation:none}</style></noscript>" +
                "<style amp4ads-boilerplate>body{visibility:hidden}</style>" +
                "<STYLE AMP4EMAIL-BOILERPLATE=\"\">body{visibility:hidden}</STYLE>",
        )
        assertFalse(out.contains("visibility"))
        assertFalse(out.contains("-amp-start"))
        assertFalse(out.contains("<style", ignoreCase = true))
    }

    @Test
    fun leaves_ordinary_markup_untouched() {
        val plain = "<style>body{color:red}</style><p>amp4email-boilerplate is a style name</p>"
        assertEquals(plain, EmailHtmlSanitizer.neutralize_amp_markup(plain))
    }

    @Test
    fun removes_the_boilerplate_from_quoted_replies() {
        val out = EmailHtmlSanitizer.repair_comment_markup(amp_email)
        assertFalse(out.contains("visibility:hidden"))
        assertTrue(out.contains("<style amp-custom>"))
    }
}
