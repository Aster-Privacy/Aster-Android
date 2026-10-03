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

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailHtmlSanitizerSourcelessImagesTest {

    private val png = "data:image/png;base64,iVBORw0KGgo="
    private val remote = "https://shop.example.com/products/lamp.jpg"

    private fun blocked(html: String): Element =
        Jsoup.parseBodyFragment(
            EmailHtmlSanitizer.replace_blocked_images(EmailHtmlSanitizer.sanitize(html)),
        ).body()

    private fun assert_dropped(img: String) {
        val body = blocked("""<table><tr><td>$img</td><td>Desk lamp</td></tr></table>""")
        assertNull(body.selectFirst("img"))
        assertFalse(body.html().contains("Image of"))
        assertTrue(body.text().contains("Desk lamp"))
    }

    @Test
    fun drops_an_image_with_an_empty_src() {
        assert_dropped("""<img src="" alt="Image of " width="66">""")
    }

    @Test
    fun drops_an_image_with_a_whitespace_src() {
        assert_dropped("<img src=\" \t\n \" alt=\"Image of \" width=\"66\">")
    }

    @Test
    fun drops_an_image_without_a_src() {
        assert_dropped("""<img alt="Image of " width="66">""")
    }

    @Test
    fun drops_an_image_whose_scheme_the_safelist_rejects() {
        assert_dropped("""<img src="javascript:alert(1)" alt="Image of " width="66">""")
    }

    @Test
    fun drops_an_image_whose_data_url_is_not_a_raster_image() {
        assert_dropped("""<img src="data:text/html,hi" alt="Image of " width="66">""")
        assert_dropped("""<img src="data:image/svg+xml;base64,PHN2Zz48L3N2Zz4=" alt="Image of " width="66">""")
    }

    @Test
    fun keeps_blocking_a_remote_image_next_to_a_sourceless_one() {
        val body = blocked(
            """<img src="$remote" alt="Image of " width="66"><img src="" alt="Image of " width="66">""",
        )
        val images = body.select("img")
        assertEquals(1, images.size)
        assertEquals("true", images[0].attr("data-blocked"))
        assertEquals(remote, images[0].attr("data-original-src"))
        assertTrue(images[0].attr("src").startsWith("data:image/svg+xml,"))
    }

    @Test
    fun keeps_inline_cid_and_data_images() {
        val body = blocked("""<img src="cid:logo@example.com" alt="Logo"><img src="$png" alt="Dot">""")
        val images = body.select("img")
        assertEquals(2, images.size)
        assertEquals("cid:logo@example.com", images[0].attr("src"))
        assertEquals(png, images[1].attr("src"))
        assertFalse(images.any { it.hasAttr("data-blocked") })
    }

    @Test
    fun keeps_an_image_whose_srcset_provides_the_source() {
        val body = blocked("""<img src="" srcset="$remote 2x" alt="Lamp" width="66">""")
        val img = body.selectFirst("img")!!
        assertEquals("true", img.attr("data-blocked"))
        assertEquals(remote, img.attr("data-original-src"))
    }
}
