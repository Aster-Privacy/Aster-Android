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
import org.junit.Test

class UnsubscribeScanTimeTest {

    private fun body_link(html: String? = null, text: String? = null): String? =
        detect_unsubscribe_info(html_content = html, text_content = text).unsubscribe_link

    @Test(timeout = 2_000)
    fun long_link_values_finish_quickly() {
        body_link(html = "href='" + "remove".repeat(30_000))
        body_link(html = "href='" + "remove".repeat(30_000) + "'")
        body_link(html = "href=\"" + "manage email subscription ".repeat(10_000) + "\"")
    }

    @Test(timeout = 2_000)
    fun unclosed_anchors_finish_quickly() {
        assertEquals(null, body_link(html = "<a href='x' ".repeat(30_000)))
        assertEquals(null, body_link(html = "<a".repeat(60_000) + ">"))
        assertEquals(null, body_link(html = "<a href='https://x.example/'>" + "unsubscribe ".repeat(20_000)))
    }

    @Test(timeout = 2_000)
    fun long_text_runs_finish_quickly() {
        assertEquals(null, body_link(text = "http://".repeat(30_000)))
        assertEquals(null, body_link(text = "https://a" + "x".repeat(100_000)))
        detect_unsubscribe_info(list_unsubscribe = "<http://a".repeat(30_000))
    }

    @Test
    fun unsubscribe_anchors_are_still_found() {
        assertEquals(
            "https://news.example.com/u?a=1&b=2",
            body_link(
                html = "<p><a href='https://news.example.com/'>Home</a> " +
                    "<A class=\"x\" HREF=\"https://news.example.com/u?a=1&amp;b=2\">Unsubscribe</A></p>",
            ),
        )
        assertEquals(
            "https://news.example.com/o",
            body_link(html = "<a href=\"https://news.example.com/o\">Opt out</a>"),
        )
    }

    @Test
    fun unsubscribe_href_values_are_still_found() {
        assertEquals(
            "https://news.example.com/list/remove-from-list",
            body_link(html = "<a href=\"https://news.example.com/\">x</a><a href=\"https://news.example.com/list/remove-from-list\">y</a>"),
        )
        assertEquals(
            "https://news.example.com/opt-out",
            body_link(html = "<a href='https://news.example.com/manage/preferences'>a</a><a href='https://news.example.com/opt-out'>b</a>"),
        )
        assertEquals(
            "https://news.example.com/subscription/settings",
            body_link(html = "<div><a href='https://news.example.com/subscription/settings'>Settings</a></div>"),
        )
        assertEquals(null, body_link(html = "<a href='https://news.example.com/settings/subscription'>s</a>"))
    }

    @Test
    fun unsubscribe_urls_in_text_are_still_found() {
        assertEquals(
            "https://news.example.com/unsubscribe?id=7",
            body_link(text = "Thanks.\nTo stop: https://news.example.com/unsubscribe?id=7\nBye"),
        )
        assertEquals("http://e.example/x/optout", body_link(text = "go http://e.example/x/optout now"))
        assertEquals(null, body_link(text = "https://unsubscribe.example"))
        assertEquals(null, body_link(text = "see https:// unsubscribe"))
    }

    @Test
    fun bracketed_header_links_are_still_read() {
        val info = detect_unsubscribe_info(list_unsubscribe = "< https://list.example.com/u/1 >, <mailto:u@list.example.com>")
        assertEquals("u@list.example.com", info.unsubscribe_mailto)
        assertEquals("https://list.example.com/u/1", info.unsubscribe_page_url)
    }
}
