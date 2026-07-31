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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmailBodyThemeColorTest {

    private val sent_body = "<p>Hey, that works for me. Let's do Thursday at 10.</p>"

    private fun render(is_dark: Boolean, fg_hex: String, body: String = sent_body): String =
        build_email_html(
            body = body,
            is_dark = is_dark,
            fg_hex = fg_hex,
            link_hex = "#2563eb",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image unavailable",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )

    private fun body_style(html: String): String =
        html.substringAfter("<body style=\"").substringBefore("\"")

    @Test
    fun a_light_theme_uses_its_own_text_color_not_hardcoded_black() {
        val style = body_style(render(is_dark = false, fg_hex = "#3B3228"))
        assertTrue("the sepia theme text color must reach the email body: $style", style.contains("color:#3B3228"))
        assertTrue("the email body must not force near-black text: $style", !style.contains("color:#111827"))
    }

    @Test
    fun a_dark_theme_still_renders_light_text() {
        val style = body_style(render(is_dark = true, fg_hex = "#E8E8E8"))
        assertTrue("dark themes must keep light body text: $style", style.contains("color:#e5e5e5"))
    }

    @Test
    fun a_light_designed_email_on_a_dark_theme_keeps_dark_text() {
        val body = "<div style=\"background-color:#ffffff\"><p>Newsletter copy</p></div>"
        val style = body_style(render(is_dark = true, fg_hex = "#E8E8E8", body = body))
        assertTrue("an email forced onto a white page must keep dark text: $style", style.contains("color:#111827"))
    }
}
