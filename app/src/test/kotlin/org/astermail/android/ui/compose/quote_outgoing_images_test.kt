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

package org.astermail.android.ui.compose

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class quote_outgoing_images_test {
    private val pixel =
        "data:image/gif;base64,R0lGODlhAQABAAAAACH5BAEKAAEALAAAAAABAAEAAAICTAEAOw=="

    @Test
    fun unresolved_cid_images_are_dropped() {
        val out = remove_unsendable_quote_images(
            "<p>Hi</p><img src=\"cid:img_25da@ios.example\"><img alt='a>b' src='CID:b@x' width=\"20\"><img src=cid:c@x>",
        )
        assertFalse(out.contains("cid:", ignoreCase = true))
        assertFalse(out.contains("<img"))
        assertTrue(out.contains("<p>Hi</p>"))
    }

    @Test
    fun placeholder_pixels_from_earlier_replies_are_dropped() {
        val out = remove_unsendable_quote_images(
            "<p>Earlier</p><img src=\"$pixel\"><div style=\"background:url($pixel)\">x</div>",
        )
        assertFalse(out.contains("<img"))
        assertFalse(out.contains("R0lGODlh"))
        assertTrue(out.contains("Earlier"))
    }

    @Test
    fun blob_images_are_dropped() {
        val out = remove_unsendable_quote_images("<img src=\"blob:https://mail.example/abc\">t")
        assertFalse(out.contains("blob:"))
    }

    @Test
    fun loadable_images_are_kept() {
        val html = "<img src=\"https://example.com/logo.png\"><img data-src=\"cid:x\" src=\"data:image/png;base64,iVBORw0KGgo=\">"
        assertTrue(remove_unsendable_quote_images(html) == html)
    }

    @Test
    fun slash_separated_sources_are_dropped() {
        val out = remove_unsendable_quote_images("<img/src=\"cid:a@x\">t")
        assertFalse(out.contains("<img"))
    }

    @Test
    fun unterminated_tags_finish_quickly() {
        val html = "<img src=cid:" + "a".repeat(400_000) + "<img a".repeat(60_000)
        val started = System.nanoTime()
        remove_unsendable_quote_images(html)
        assertTrue(System.nanoTime() - started < 2_000_000_000L)
    }
}
