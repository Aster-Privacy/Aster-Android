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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyImageLoadingTest {

    private fun img(html: String): Element = Jsoup.parseBodyFragment(html).body().selectFirst("img")!!

    @After
    fun clear_store() {
        InlineImageStore.clear()
    }

    @Test
    fun a_bare_remote_image_is_not_a_tracking_pixel() {
        assertFalse(EmailHtmlSanitizer.is_tracking_pixel(img("""<img src="https://picsum.photos/600/400">""")))
    }

    @Test
    fun a_bare_remote_image_survives_sanitizing() {
        val out = EmailHtmlSanitizer.sanitize("""<p>Hi</p><img src="https://picsum.photos/600/400">""")
        assertTrue(out.contains("picsum.photos/600/400"))
    }

    @Test
    fun a_border_width_and_line_height_of_one_do_not_mark_a_pixel() {
        val element = img("""<img src="https://cdn.example.com/hero.png" style="border-width:0;line-height:1;display:block">""")
        assertFalse(EmailHtmlSanitizer.is_tracking_pixel(element))
    }

    @Test
    fun a_logo_on_a_tracking_assets_path_with_a_real_width_is_kept() {
        val element = img("""<img src="https://cdn.example.com/tracking-assets/logo.png" width="200">""")
        assertFalse(EmailHtmlSanitizer.is_tracking_pixel(element))
    }

    @Test
    fun a_one_by_one_pixel_is_removed() {
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(img("""<img src="https://cdn.example.com/a.gif" width="1" height="1">""")))
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(img("""<img src="https://cdn.example.com/a.gif" style="width:1px;height:1px">""")))
    }

    @Test
    fun a_mail_service_open_beacon_is_removed() {
        val element = img("""<img src="https://abc.r.us-east-1.awstrack.me.amazonses.com/open?id=1">""")
        assertTrue(EmailHtmlSanitizer.is_tracking_pixel(element))
    }

    @Test
    fun a_mail_service_hosted_image_is_kept() {
        val element = img("""<img src="https://images.amazonses.com/brand/banner.png">""")
        assertFalse(EmailHtmlSanitizer.is_tracking_pixel(element))
    }

    @Test
    fun a_redirect_to_another_proxy_url_is_followed() {
        val current = "https://app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fa.example%2Fx.png"
        assertEquals(
            "https://app.astermail.org/api/images/v1/proxy?url=https%3A%2F%2Fb.example%2Fy.png",
            proxy_redirect_target(current, "/api/images/v1/proxy?url=https%3A%2F%2Fb.example%2Fy.png"),
        )
    }

    @Test
    fun a_redirect_off_the_proxy_is_refused() {
        val current = "https://app.astermail.org/api/images/v1/proxy?url=x"
        assertNull(proxy_redirect_target(current, "https://evil.example/api/images/v1/proxy?url=x"))
        assertNull(proxy_redirect_target(current, "http://app.astermail.org/api/images/v1/proxy?url=x"))
        assertNull(proxy_redirect_target(current, "https://user@app.astermail.org/api/images/v1/proxy?url=x"))
        assertNull(proxy_redirect_target(current, "https://app.astermail.org:8443/api/images/v1/proxy?url=x"))
        assertNull(proxy_redirect_target(current, "https://app.astermail.org/api/other?url=x"))
        assertNull(proxy_redirect_target(current, "https://app.astermail.org/api/images/v1/proxy?url="))
        assertNull(proxy_redirect_target(current, null))
    }

    @Test
    fun only_transient_proxy_failures_are_retried() {
        listOf(408, 502, 503, 504).forEach { assertTrue(proxy_status_is_retryable(it)) }
        listOf(200, 400, 401, 403, 404, 424, 429, 500).forEach { assertFalse(proxy_status_is_retryable(it)) }
    }

    @Test
    fun an_evicted_inline_image_is_reloaded() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val key = InlineImageStore.content_key("logo@mail", bytes)
        InlineImageStore.register_reloader(key, InlineImageReloader("logo@mail", "image/png") { bytes.copyOf() })
        val entry = InlineImageStore.entry_or_reload(key)
        assertNotNull(entry)
        assertEquals("image/png", entry!!.content_type)
        assertTrue(bytes.contentEquals(entry.bytes))
        assertNotNull(InlineImageStore.entry_for_source(InlineImageStore.url_for(key)))
    }

    @Test
    fun a_reload_with_different_bytes_is_rejected() {
        val key = InlineImageStore.content_key("logo@mail", byteArrayOf(1, 2, 3))
        InlineImageStore.register_reloader(key, InlineImageReloader("logo@mail", "image/png") { byteArrayOf(9, 9) })
        assertNull(InlineImageStore.entry_or_reload(key))
    }

    @Test
    fun clearing_the_store_drops_reloaders() {
        val bytes = byteArrayOf(5, 6)
        val key = InlineImageStore.content_key("a@b", bytes)
        InlineImageStore.register_reloader(key, InlineImageReloader("a@b", "image/gif") { bytes.copyOf() })
        InlineImageStore.clear()
        assertNull(InlineImageStore.entry_or_reload(key))
    }
}
