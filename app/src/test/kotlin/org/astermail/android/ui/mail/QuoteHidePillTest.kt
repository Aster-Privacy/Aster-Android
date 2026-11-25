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
import org.junit.Test

class QuoteHidePillTest {
    @Test
    fun sanitizer_drops_a_quote_hide_link_from_message_content() {
        val out = EmailHtmlSanitizer.sanitize("""<p><a href="asterquote:close">Hide</a></p>""")
        assertFalse(out.contains("asterquote", ignoreCase = true))
    }

    @Test
    fun a_planted_quote_pill_is_replaced_by_the_real_one() {
        val body = """<p>Hi</p><details class="aster-quoted-wrapper"><summary class="aster-quote-toggle"><a class="aster-quote-close" href="https://planted.invalid/">Hide quoted text</a></summary><div class="aster-quoted-content">older</div></details>"""
        val out = prepare_email_body(
            body = body,
            forwarded_label = "Forwarded",
            image_failed_label = "Image",
            is_newsletter = false,
            simple_dark = false,
            hide_quote_label = "Hide quoted text",
        )
        assertFalse(out.contains("planted.invalid"))
        val pills = Regex("class=\"aster-quote-(?:close|cover)\"").findAll(out).count()
        val real = Regex("href=\"asterquote:close:\\d+\"").findAll(out).count()
        assertEquals(pills, real)
    }
}
