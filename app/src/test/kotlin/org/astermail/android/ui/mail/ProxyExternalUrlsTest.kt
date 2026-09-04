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

class ProxyExternalUrlsTest {

    private val base = "https://app.astermail.org/api/images/v1/proxy?url="

    private fun proxied(url: String) = base + java.net.URLEncoder.encode(url, "UTF-8")

    @Test
    fun proxies_img_src() {
        val out = proxy_external_urls("""<img src="https://cdn.example/hero.png">""", base)
        assertTrue(out.contains("src=\"${proxied("https://cdn.example/hero.png")}\""))
    }

    @Test
    fun proxies_td_background_attribute() {
        val out = proxy_external_urls("""<table><tr><td background="https://cdn.example/btn.png" bgcolor="#000000">x</td></tr></table>""", base)
        assertTrue(out.contains("background=\"${proxied("https://cdn.example/btn.png")}\""))
        assertTrue(out.contains("bgcolor=\"#000000\""))
    }

    @Test
    fun proxies_protocol_relative_background_attribute() {
        val out = proxy_external_urls("""<table><tr><td background="//cdn.example/btn.png">x</td></tr></table>""", base)
        assertTrue(out.contains("background=\"${proxied("https://cdn.example/btn.png")}\""))
    }

    @Test
    fun proxies_css_background_url_in_inline_style() {
        val out = proxy_external_urls("""<a style="background:url('https://cdn.example/btn.png') center">x</a>""", base)
        assertTrue(out.contains("url('${proxied("https://cdn.example/btn.png")}')"))
    }

    @Test
    fun proxies_protocol_relative_css_url() {
        val out = proxy_external_urls("""<a style="background:url(//cdn.example/btn.png)">x</a>""", base)
        assertTrue(out.contains("url(${proxied("https://cdn.example/btn.png")})"))
    }

    @Test
    fun proxies_css_background_url_in_style_block() {
        val out = proxy_external_urls("""<style>.b{background:url(https://cdn.example/btn.png) center}</style>""", base)
        assertTrue(out.contains("url(${proxied("https://cdn.example/btn.png")})"))
    }

    @Test
    fun decodes_amp_entities_in_url() {
        val out = proxy_external_urls("""<table><tr><td background="https://cdn.example/b.png?a=1&amp;b=2">x</td></tr></table>""", base)
        assertTrue(out.contains(proxied("https://cdn.example/b.png?a=1&b=2")))
    }

    @Test
    fun leaves_data_and_cid_untouched() {
        val html = """<img src="data:image/png;base64,AAAA"><table><tr><td background="cid:part1">x</td></tr></table>"""
        val out = proxy_external_urls(html, base)
        assertTrue(out.contains("data:image/png;base64,AAAA"))
        assertTrue(out.contains("background=\"cid:part1\""))
        assertFalse(out.contains("proxy?url="))
    }

    @Test
    fun proxies_unquoted_img_src() {
        val out = proxy_external_urls("""<img src=https://cdn.example/hero.png width=40>""", base)
        assertTrue(out.contains("src=\"${proxied("https://cdn.example/hero.png")}\""))
        assertTrue(out.contains("width=\"40\""))
    }

    @Test
    fun proxies_unquoted_protocol_relative_src() {
        val out = proxy_external_urls("""<img src=//cdn.example/hero.png>""", base)
        assertTrue(out.contains("src=\"${proxied("https://cdn.example/hero.png")}\""))
    }

    @Test
    fun decodes_numeric_amp_entities_in_url() {
        val out = proxy_external_urls("""<img src="https://cdn.example/b.png?a=1&#38;b=2">""", base)
        assertTrue(out.contains(proxied("https://cdn.example/b.png?a=1&b=2")))
    }

    @Test
    fun srcset_drops_non_http_entries_without_stray_commas() {
        val out = proxy_external_urls(
            """<img srcset="https://cdn.example/a.png 1x, cid:part1 2x, https://cdn.example/b.png 3x">""",
            base,
        )
        assertEquals(
            """<img srcset="${proxied("https://cdn.example/a.png")} 1x, ${proxied("https://cdn.example/b.png")} 3x">""",
            out,
        )
    }

    @Test
    fun does_not_double_proxy_an_already_proxied_url() {
        val already = proxied("https://cdn.example/hero.png")
        val out = proxy_external_urls("""<img src="$already">""", base)
        assertEquals("""<img src="$already">""", out)
    }

    @Test
    fun does_not_reproxy_already_proxied_or_touch_css_property_name() {
        val out = proxy_external_urls("""<div style="background-color:#000000">x</div>""", base)
        assertEquals("""<div style="background-color:#000000">x</div>""", out)
    }
}
