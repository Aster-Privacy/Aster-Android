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

package org.astermail.android.ui.mail

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingPixelMarkerTest {

    private val marker_selector = "span[data-tracking-pixel-marker]"

    private val email =
        """<p>Hello there</p>""" +
            """<table><tr><td>Footer<img src="https://open.mailmetrics.example/o/1.gif" width="1" height="1">end</td></tr></table>""" +
            """<img src="https://hidden.example/open?id=1" width="1" height="1" style="display:none">""" +
            """<img src="https://zero.example/open?id=2" width="0" height="0">""" +
            """<img src="https://collapsed.example/pixel.gif" style="width:0;height:0;border:0">"""

    private fun reader_body(html: String, remove_tracking_pixels: Boolean): Element {
        val sanitized = EmailHtmlSanitizer.sanitize(
            html,
            EmailHtmlSanitizer.SanitizeOptions(
                remove_tracking_pixels = remove_tracking_pixels,
                mark_tracking_pixels = true,
            ),
        )
        val blocked = EmailHtmlSanitizer.replace_blocked_images(sanitized, mark_tracking_pixels = true)
        return Jsoup.parseBodyFragment(blocked).body()
    }

    private fun assert_nothing_loads_remotely(body: Element) {
        for (element in body.select("[src], [srcset], [background]")) {
            val src = element.attr("src").trim().lowercase()
            assertFalse("remote source left on ${element.outerHtml()}", src.startsWith("http") || src.startsWith("//"))
            assertFalse(element.hasAttr("srcset"))
            assertFalse(element.hasAttr("background"))
        }
        assertFalse(body.html().contains("url(", ignoreCase = true))
    }

    @Test
    fun a_removed_tracking_pixel_leaves_a_labelled_marker_where_it_sat() {
        val body = reader_body(email, remove_tracking_pixels = true)
        val markers = body.select(marker_selector)

        assertEquals(1, markers.size)
        val marker = markers.single()
        assertEquals("img", marker.attr("role"))
        assertEquals("Tracking pixel blocked", marker.attr("aria-label"))
        assertEquals("Tracking pixel blocked", marker.attr("title"))
        assertTrue(marker.children().isEmpty())
        assertEquals("td", marker.parent()!!.normalName())
        assertEquals("Footer", (marker.previousSibling() as org.jsoup.nodes.TextNode).text())
        assertEquals("end", (marker.nextSibling() as org.jsoup.nodes.TextNode).text())
        assertTrue(body.select("img").isEmpty())
        assertNull(body.selectFirst("[data-tracking-pixel-slot]"))
        assert_nothing_loads_remotely(body)
    }

    @Test
    fun a_kept_tracking_pixel_gets_a_marker_in_front_of_its_placeholder() {
        val body = reader_body(email, remove_tracking_pixels = false)
        val markers = body.select(marker_selector)

        assertEquals(1, markers.size)
        val pixel = markers.single().nextElementSibling()!!
        assertEquals("img", pixel.normalName())
        assertEquals("true", pixel.attr("data-tracking-pixel"))
        assertEquals("true", pixel.attr("aria-hidden"))
        assertEquals("https://open.mailmetrics.example/o/1.gif", pixel.attr("data-original-src"))
        assertEquals(4, body.select("img[data-tracking-pixel=true]").size)
        assertEquals(1, body.select("img[aria-hidden=true]").size)
        assert_nothing_loads_remotely(body)
    }

    @Test
    fun hidden_tracking_pixels_get_no_marker_but_stay_counted() {
        val report = EmailHtmlSanitizer.analyze_trackers(email)

        assertEquals(4, report.pixel_count)
        assertEquals(
            listOf("open.mailmetrics.example", "hidden.example", "zero.example", "collapsed.example"),
            report.pixel_domains.map { it.first },
        )
        for (remove in listOf(true, false)) {
            assertEquals(1, reader_body(email, remove_tracking_pixels = remove).select(marker_selector).size)
        }
    }

    @Test
    fun the_banner_the_dialog_and_the_markers_count_the_same_pixels() {
        val mixed =
            """<p>Hi<img src="https://track.example.com/o/1.gif" width="1" height="1"></p>""" +
                """<p>Later<img src="https://track.example.com/o/2.gif" width="1" height="1"></p>""" +
                """<img src="https://cdn.example.org/spacer.gif" width="2" height="2" alt="">""" +
                """<img src="https://hidden.example/open?id=1" width="1" height="1" style="display:none">""" +
                """<p>Bye<img src="//px.example/p.gif" width="1" height="1"></p>"""
        val report = EmailHtmlSanitizer.analyze_trackers(mixed)
        val banner = count_external_content(mixed, report)

        assertEquals(4, report.pixel_count)
        assertEquals(1, report.hidden_pixel_count)
        assertEquals(
            listOf("track.example.com" to 2, "hidden.example" to 1, "px.example" to 1),
            report.pixel_domains,
        )
        assertEquals(report.pixel_count, banner.tracker_count)
        assertEquals(report.pixel_count, report.pixel_domains.sumOf { it.second })
        assertEquals(1, banner.image_count)
        assertEquals(
            listOf("https://cdn.example.org/spacer.gif"),
            banner.items.filter { it.type == ExternalContentType.image }.map { it.url },
        )
        assertEquals(4, banner.items.count { it.type == ExternalContentType.tracker })
        for (remove in listOf(true, false)) {
            assertEquals(3, report.marked_pixel_count)
            assertEquals(
                report.marked_pixel_count,
                reader_body(mixed, remove_tracking_pixels = remove).select(marker_selector).size,
            )
        }
    }

    @Test
    fun ordinary_blocked_images_get_no_marker() {
        val body = reader_body(
            """<img src="https://images.example.test/hero.png" width="600" height="200" alt="Hero">""",
            remove_tracking_pixels = true,
        )

        assertNull(body.selectFirst(marker_selector))
        assertEquals("false", body.selectFirst("img")!!.attr("data-tracking-pixel"))
    }

    @Test
    fun no_marker_or_slot_without_the_marker_option() {
        val sanitized = EmailHtmlSanitizer.sanitize(email)
        val blocked = EmailHtmlSanitizer.replace_blocked_images(sanitized)

        assertFalse(sanitized.contains("data-tracking-pixel-slot"))
        assertFalse(blocked.contains("data-tracking-pixel-marker"))
        assertFalse(
            EmailHtmlSanitizer.replace_blocked_images(
                EmailHtmlSanitizer.sanitize(email, EmailHtmlSanitizer.SanitizeOptions(mark_tracking_pixels = true)),
            ).contains("data-tracking-pixel"),
        )
    }

    @Test
    fun the_print_body_carries_no_marker() {
        val msg = ThreadMessage(
            id = "m1",
            sender_name = "News",
            sender_email = "news@example.com",
            to_label = "",
            body = "Hello",
            body_html = email,
            timestamp = 0L,
        )
        val printed = build_email_print_body(
            msg,
            allow_external = false,
            sanitize_options = EmailHtmlSanitizer.SanitizeOptions(),
            blocked_image_labels = BlockedImageLabels.ENGLISH,
        )

        assertFalse(printed.contains("data-tracking-pixel-marker"))
        assertFalse(printed.contains("data-tracking-pixel-slot"))
    }

    @Test
    fun a_marker_never_takes_layout_space_and_does_not_print() {
        for (dark in listOf(false, true)) {
            val css = BlockedImagePlaceholder.tracking_marker_css(dark)

            assertTrue(css.contains("$marker_selector{position:relative!important;display:inline-block!important;width:0!important;height:0!important"))
            assertTrue(css.contains("$marker_selector::before{content:''!important;position:absolute!important"))
            assertTrue(css.contains("width:11px!important;height:11px!important"))
            assertTrue(css.contains("@media print{$marker_selector{display:none!important}}"))
            val urls = Regex("""url\("([^"]+)"\)""").findAll(css).map { it.groupValues[1] }.toList()
            assertEquals(1, urls.size)
            assertTrue(urls.single().startsWith("data:image/svg+xml,"))
            val svg = java.net.URLDecoder.decode(urls.single().substringAfter(','), "UTF-8")
            assertTrue(svg.contains(if (dark) "fill=\"#10b981\"" else "fill=\"#059669\""))
            assertTrue(svg.contains(if (dark) "stroke=\"#0a0a0a\"" else "stroke=\"#ffffff\""))
        }
    }

    @Test
    fun the_reader_document_keeps_a_trailing_marker_and_styles_it() {
        val body = reader_body(
            """<p>Thanks for reading</p><div><img src="https://open.example/t.gif" width="1" height="1"></div>""",
            remove_tracking_pixels = true,
        ).html()
        val document = build_email_html(
            body = body,
            is_dark = false,
            fg_hex = "#111827",
            link_hex = "#2563eb",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image could not be loaded",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )

        assertTrue(document.contains("data-tracking-pixel-marker"))
        assertTrue(document.contains(BlockedImagePlaceholder.tracking_marker_css(false)))
    }
}
