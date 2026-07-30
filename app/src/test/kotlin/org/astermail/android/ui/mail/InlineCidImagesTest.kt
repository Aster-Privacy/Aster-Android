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

class InlineCidImagesTest {

    @Test
    fun normalizes_angle_bracketed_content_id() {
        assertEquals("logo@aster", normalized_content_id("<logo@aster>"))
        assertEquals("logo@aster", normalized_content_id("  logo@aster  "))
        assertEquals("", normalized_content_id(null))
    }

    @Test
    fun resolves_known_cid_to_data_uri() {
        val html = """<img src="cid:logo@aster" alt="logo">"""
        val out = resolve_inline_cids(html, mapOf("logo@aster" to "data:image/png;base64,AAAB"))
        assertTrue(out.contains("""src="data:image/png;base64,AAAB""""))
        assertFalse(out.contains("cid:"))
    }

    @Test
    fun resolves_angle_bracketed_cid_in_markup() {
        val html = """<img src='cid:<pic1>'>"""
        val out = resolve_inline_cids(html, mapOf("pic1" to "data:image/jpeg;base64,BBBB"))
        assertTrue(out.contains("data:image/jpeg;base64,BBBB"))
    }

    @Test
    fun unknown_cid_falls_back_to_transparent_pixel() {
        val html = """<img src="cid:missing">"""
        val out = resolve_inline_cids(html, emptyMap())
        assertTrue(out.contains("data:image/gif;base64,"))
        assertFalse(out.contains("cid:missing"))
    }

    @Test
    fun quoted_image_from_an_earlier_message_is_resolved() {
        val html = """<p>reply</p><div class="aster_quote"><img src="cid:orig@aster"></div>"""
        val out = resolve_inline_cids(html, mapOf("orig@aster" to "data:image/png;base64,QQQQ"))
        assertTrue(out.contains("data:image/png;base64,QQQQ"))
        assertFalse(out.contains("data:image/gif;base64,"))
    }

    @Test
    fun leaves_remote_and_data_sources_untouched() {
        val html = """<img src="https://cdn.example/a.png"><img src="data:image/png;base64,CCCC">"""
        val out = resolve_inline_cids(html, mapOf("x" to "data:image/png;base64,DDDD"))
        assertEquals(html, out)
    }

    @Test
    fun resolves_multiple_distinct_cids() {
        val html = """<img src="cid:a"><img src="cid:b"><img src="cid:a">"""
        val out = resolve_inline_cids(
            html,
            mapOf("a" to "data:image/png;base64,AAAA", "b" to "data:image/png;base64,BBBB"),
        )
        assertEquals(2, Regex("base64,AAAA").findAll(out).count())
        assertEquals(1, Regex("base64,BBBB").findAll(out).count())
    }
}
