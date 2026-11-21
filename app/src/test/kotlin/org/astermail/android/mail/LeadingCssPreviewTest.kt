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

package org.astermail.android.mail

import org.astermail.android.ui.mail.select_display_body
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeadingCssPreviewTest {

    @Test
    fun `strips a leading style rule from an outside provider body`() {
        assertEquals(
            "Hi, thank you for your order",
            strip_leading_css("body { background-color: #fff } Hi, thank you for your order"),
        )
    }

    @Test
    fun `strips a leading media block with nested rules`() {
        val text = "@media only screen and (max-width: 600px) { .mobile { width: 100% !important; } " +
            "table td { padding: 0 } } Your receipt is ready"
        assertEquals("Your receipt is ready", strip_leading_css(text))
    }

    @Test
    fun `strips several leading rules in a row`() {
        val text = "body { margin: 0; } table td { border-collapse: collapse; } #wrapper a { padding: 0; } " +
            ".OuterClass * { line-height: 100%; } a[data-link-style] { color: inherit !important; } " +
            "u + #body a { color: inherit } Welcome aboard"
        assertEquals("Welcome aboard", strip_leading_css(text))
    }

    @Test
    fun `strips leading imports font faces and comments`() {
        val text = "/* reset */ @import url(https://fonts.example.com/css); " +
            "@font-face { font-family: 'Brand'; src: url(https://example.com/f.woff2) format('woff2'); } " +
            "<!-- p { color: #333; } --> Hello there"
        assertEquals("Hello there", strip_leading_css(text))
    }

    @Test
    fun `keeps a value with a data url intact while stripping the rule`() {
        val text = ".logo { background: url(data:image/png;base64,AAAA) no-repeat; } Order shipped"
        assertEquals("Order shipped", strip_leading_css(text))
    }

    @Test
    fun `keeps ordinary text that contains braces`() {
        val kept = listOf(
            "Set {color: red} later",
            "{name}, your order shipped",
            "Hi {first_name}, welcome to the team",
            "a {name} placeholder in the template",
            "@media team, please review the deck before Friday",
            "@media team { kickoff at 3 } see you there",
            "@import the files; then call me",
            "To fix the layout, add body { margin: 0 } to the template",
            "/* note */ Hello there",
            "Plain text with no styling at all",
        )
        for (text in kept) {
            assertEquals(text, strip_leading_css(text))
            assertFalse(text, has_leading_css(text))
        }
    }

    @Test
    fun `text that is only style rules becomes empty`() {
        assertEquals("", strip_leading_css("body { margin: 0 } p { color: red }   "))
        assertEquals("", clean_body_preview("body { margin: 0 } p { color: red }", null))
    }

    @Test
    fun `text fallback preview drops leading style rules`() {
        assertEquals(
            "Hi, thank you for your order",
            clean_body_preview("body { background-color: #fff } Hi, thank you for your order", null),
        )
    }

    @Test
    fun `preview prefers the html when the text starts with style rules`() {
        val html = "<html><head><style>body { color: red }</style></head><body><p>Hi from the message</p></body></html>"
        assertEquals(
            "Hi from the message",
            clean_body_preview("body { color: red } Hi from the text copy", html),
        )
    }

    @Test
    fun `notification and search text drops leading style rules`() {
        assertEquals("Your code is 123456", safe_display_text("p { margin: 0 } Your code is 123456"))
    }

    @Test
    fun `thread card text fallback drops leading style rules`() {
        assertEquals("Plain body", thread_card_preview(null, "body { color: red } Plain body"))
    }

    @Test
    fun `display body uses the html when the text starts with style rules`() {
        val text = "body { color: red } Hello"
        assertTrue(has_leading_css(text))
        assertEquals("Hello from html", select_display_body(text, "<div>Hello from html</div>"))
    }
}
