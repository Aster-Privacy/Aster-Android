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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreheaderTextTest {

    private val filler = "&#847; &shy; ".repeat(60)

    @Test
    fun `reads a real world preheader and drops the filler wall`() {
        val html = """
            <body>
            <span id="Preheader" class="preheader" style="display: none; max-height: 0px; font-size: 0px; overflow: hidden; mso-hide: all;">Get in on the savings and offers, Sami.$filler</span>
            <div>Real visible body copy starts here.</div>
            </body>
        """.trimIndent()

        assertEquals("Get in on the savings and offers, Sami.", extract_preheader_text(html))
    }

    @Test
    fun `keeps reading past a boilerplate hidden block`() {
        val html = """
            <body>
            <div style="display:none">View this email in your browser</div>
            <div style="display:none">Get 30% off your next 3 meals.</div>
            <h1>Weekly menu</h1>
            </body>
        """.trimIndent()

        assertEquals(
            "View this email in your browser Get 30% off your next 3 meals.",
            extract_preheader_text(html),
        )
    }

    @Test
    fun `reads a preheader hidden by a stylesheet rule`() {
        val html = """
            <html><head><style>.ph{display:none;max-height:0}</style></head>
            <body><div class="ph">Two seats left for the workshop.</div><h1>Workshop</h1></body></html>
        """.trimIndent()

        assertEquals("Two seats left for the workshop.", extract_preheader_text(html))
    }

    @Test
    fun `ignores a rule that only hides inside a media query`() {
        val html = """
            <html><head><style>@media (max-width:600px){.mobile{display:none}}</style></head>
            <body><div class="mobile">Visible on desktop</div><p>Body</p></body></html>
        """.trimIndent()

        assertEquals("", extract_preheader_text(html))
    }

    @Test
    fun `returns nothing when the first content is visible`() {
        assertEquals(
            "",
            extract_preheader_text("<body><p>Hi Sam, here are the notes from today.</p></body>"),
        )
    }

    @Test
    fun `ignores a hidden block that holds only filler`() {
        val html = "<body><div style=\"display:none\">$filler</div><p>Body</p></body>"

        assertEquals("", extract_preheader_text(html))
    }

    @Test
    fun `stops accumulating once the hidden run is long enough`() {
        val block = "<div style=\"display:none\">alpha beta gamma delta</div>"
        val html = "<body>${block.repeat(80)}<p>Body</p></body>"

        assertTrue(extract_preheader_text(html).length < 700)
    }

    @Test
    fun `list preview uses the preheader instead of the body`() {
        val html = """
            <body>
            <span class="preheader" style="display:none">Get in on the savings and offers, Sami.$filler</span>
            <div>Real visible body copy starts here.</div>
            </body>
        """.trimIndent()

        assertEquals(
            "Get in on the savings and offers, Sami.",
            clean_body_preview("", html),
        )
    }

    @Test
    fun `list preview falls back to visible text without a preheader`() {
        val html = "<body><h1>Weekly menu</h1><p>Pick your meals</p></body>"

        assertEquals("Weekly menu Pick your meals", clean_body_preview("", html))
    }
}
