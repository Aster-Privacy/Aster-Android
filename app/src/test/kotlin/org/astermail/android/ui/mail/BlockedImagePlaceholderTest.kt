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

class BlockedImagePlaceholderTest {

    private val image = "https://images.example.test/logo.png"

    private fun blocked(html: String, labels: BlockedImageLabels = BlockedImageLabels.ENGLISH): Element =
        Jsoup.parseBodyFragment(EmailHtmlSanitizer.replace_blocked_images(html, labels)).body()

    private fun svg_of(img: Element): String {
        val src = img.attr("src")
        assertTrue("expected a generated SVG, got $src", src.startsWith("data:image/svg+xml,"))
        return java.net.URLDecoder.decode(src.substringAfter(','), "UTF-8")
    }

    @Test
    fun keeps_the_original_image_box_classes_margins_and_alignment() {
        val body = blocked(
            """<p>Photo</p><img src="$image" class="hero" width="640" height="180" align="center" alt="Coastal path" style="display:block;margin:12px auto;width:100%;height:auto">""",
        )
        val img = body.selectFirst("img")!!

        assertNull(body.selectFirst("span.blocked-image"))
        assertTrue(img.hasClass("hero"))
        assertTrue(img.hasClass("blocked-image"))
        assertEquals("640", img.attr("width"))
        assertEquals("180", img.attr("height"))
        assertEquals("center", img.attr("align"))
        assertEquals("display:block;margin:12px auto;width:100%;height:auto", img.attr("style"))
        assertEquals("Coastal path", img.attr("alt"))
        assertEquals("Image blocked: Coastal path", img.attr("aria-label"))
        assertEquals("true", img.attr("data-blocked"))
        assertEquals(image, img.attr("data-original-src"))
        val svg = svg_of(img)
        assertTrue(svg.contains("width=\"640\" height=\"180\""))
        assertTrue(svg.contains(">Image blocked</text>"))
        assertTrue(svg.contains("fill=\"#f5f5f5\""))
    }

    @Test
    fun declared_size_sets_the_intrinsic_size_and_wide_images_keep_a_readable_label() {
        val svg = svg_of(blocked("""<img src="$image" width="640" height="180" alt="Hero">""").selectFirst("img")!!)

        assertTrue(svg.contains("""width="640" height="180" viewBox="0 0 360 101.25""""))
    }

    @Test
    fun width_only_logo_gets_a_one_line_placeholder_rather_than_a_square() {
        val svg = svg_of(blocked("""<img src="$image" width="162" alt="Sample logo">""").selectFirst("img")!!)

        assertTrue(svg.contains("""width="162" height="24""""))
    }

    @Test
    fun image_without_dimensions_falls_back_to_one_line() {
        val img = blocked("""<img src="$image" alt="Logo">""").selectFirst("img")!!
        val svg = svg_of(img)

        assertTrue(svg.contains("""width="120" height="24""""))
        assertTrue(svg.contains(">Image blocked</text>"))
    }

    @Test
    fun aspect_ratio_is_preserved() {
        val svg = svg_of(
            blocked("""<img src="$image" alt="Wide" style="width:300px;aspect-ratio:16 / 9">""").selectFirst("img")!!,
        )

        assertTrue(svg.contains("""width="300" height="168.75""""))
    }

    @Test
    fun tracking_pixels_keep_their_declared_footprint_and_get_no_drawn_label() {
        val body = blocked("""<p>End</p><img src="https://t.example.test/open.gif" width="1" height="1">""")
        val img = body.selectFirst("img")!!

        assertEquals("1", img.attr("width"))
        assertEquals("1", img.attr("height"))
        assertEquals("true", img.attr("data-tracking-pixel"))
        assertEquals("Tracking pixel blocked", img.attr("title"))
        assertEquals("Tracking pixel blocked", img.attr("aria-label"))
        val svg = svg_of(img)
        assertTrue(svg.contains("""width="1" height="1""""))
        assertFalse(svg.contains("<text"))
        assertNull(body.selectFirst("span"))
    }

    @Test
    fun decorative_images_stay_unnamed_for_screen_readers() {
        val img = blocked("""<img src="$image" width="600" height="20" alt="">""").selectFirst("img")!!

        assertEquals("", img.attr("alt"))
        assertFalse(img.hasAttr("aria-label"))
        assertFalse(img.hasAttr("title"))
        assertTrue(img.attr("src").startsWith("data:image/svg+xml,"))
    }

    @Test
    fun sender_alt_text_never_enters_the_generated_svg() {
        val img = blocked("""<img src="$image" width="300" height="100" alt="&lt;script&gt;alert(1)&lt;/script&gt;">""")
            .selectFirst("img")!!
        val svg = svg_of(img)

        assertFalse(svg.contains("script"))
        assertFalse(svg.contains("alert"))
    }

    @Test
    fun labels_are_escaped_inside_the_svg() {
        val img = blocked(
            """<img src="$image" width="640" height="180">""",
            BlockedImageLabels(image = "<script>\"x\"&'y'</script>", tracking_pixel = "t"),
        ).selectFirst("img")!!
        val svg = svg_of(img)

        assertFalse(svg.contains("<script>"))
        assertTrue(svg.contains("&lt;script&gt;&quot;x&quot;&amp;&apos;y&apos;&lt;/script&gt;"))
    }

    @Test
    fun translated_labels_are_drawn_and_announced() {
        val body = blocked(
            """<img src="$image" width="640" height="180" alt="Farol"><img src="$image" width="1" height="1">""",
            BlockedImageLabels(image = "Imagem bloqueada", tracking_pixel = "Píxel de rastreio bloqueado"),
        )
        val (photo, pixel) = body.select("img")

        assertEquals("Imagem bloqueada: Farol", photo.attr("aria-label"))
        assertTrue(svg_of(photo).contains(">Imagem bloqueada</text>"))
        assertEquals("Píxel de rastreio bloqueado", pixel.attr("aria-label"))
        assertFalse(body.html().contains("Image blocked"))
    }

    @Test
    fun dark_email_bodies_repaint_the_placeholder_with_the_dark_surface() {
        val body = EmailHtmlSanitizer.replace_blocked_images("""<p>Hi</p><img src="$image" width="300" height="100" alt="Logo">""")
        fun rendered(is_dark: Boolean): String {
            val html = build_email_html(
                body = body,
                is_dark = is_dark,
                fg_hex = "#111111",
                link_hex = "#3b82f6",
                forwarded_label = "Forwarded",
                image_failed_label = "Image could not be loaded",
                force_dark_emails = false,
                dyslexia_font = false,
                translate_mode = "off",
            )
            return svg_of(Jsoup.parse(html).selectFirst("img")!!)
        }

        assertTrue(rendered(is_dark = true).contains("fill=\"#0a0a0a\""))
        assertTrue(rendered(is_dark = true).contains(">Image blocked</text>"))
        assertTrue(rendered(is_dark = false).contains("fill=\"#f5f5f5\""))
    }

    @Test
    fun blocked_placeholders_are_not_zoomable_or_marked_as_failed() {
        val body = EmailHtmlSanitizer.replace_blocked_images("""<p>Hi</p><img src="$image" width="300" height="100" alt="Logo">""")
        val html = build_email_html(
            body = body,
            is_dark = false,
            fg_hex = "#111111",
            link_hex = "#3b82f6",
            forwarded_label = "Forwarded",
            image_failed_label = "Image could not be loaded",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        val img = Jsoup.parse(html).selectFirst("#m img")!!

        assertNull(img.closest("a"))
        assertFalse(img.hasAttr(FAILED_IMAGE_LABEL_ATTRIBUTE))
    }

    @Test
    fun loading_images_restores_the_sender_image_through_the_proxy() {
        val sanitized = EmailHtmlSanitizer.sanitize(
            """<p>Logo</p><img src="$image" width="162" height="32" alt="Sample logo" title="Original title" style="border:none">""",
        )
        val loaded = Jsoup.parseBodyFragment(proxy_external_urls(sanitized, REMOTE_IMAGE_PROXY_BASE)).selectFirst("img")!!

        assertEquals(REMOTE_IMAGE_PROXY_BASE + java.net.URLEncoder.encode(image, "UTF-8"), loaded.attr("src"))
        assertEquals("162", loaded.attr("width"))
        assertEquals("32", loaded.attr("height"))
        assertEquals("Sample logo", loaded.attr("alt"))
        assertEquals("Original title", loaded.attr("title"))
        assertFalse(loaded.hasAttr("aria-label"))
        assertFalse(loaded.hasAttr("data-blocked"))
        assertFalse(loaded.hasClass("blocked-image"))
    }

    private val remote = Regex("""(?:^|[\s,])(?:[a-z][a-z0-9+.-]*:)?//""", RegexOption.IGNORE_CASE)

    private val newsletter = """
        <picture>
          <source srcset="https://cdn.example.test/a.webp 1x, //cdn.example.test/a2.webp 2x" type="image/webp">
          <source media="(min-width: 600px)" srcset="http://cdn.example.test/wide.jpg">
          <img src="$image" srcset="$image 1x, https://cdn.example.test/b.png 2x" sizes="100vw" width="320" height="200" alt="Hero">
        </picture>
        <img src="http://cdn.example.test/plain.png" srcset="https://cdn.example.test/plain2.png 2x" alt="Plain">
        <img src="//cdn.example.test/protocol.png" alt="Protocol relative">
        <img src=" HTTPS://CDN.EXAMPLE.TEST/UPPER.PNG " alt="Upper">
        <img src="data:image/png;base64,AAAA" srcset="https://cdn.example.test/data-srcset.png 2x" alt="Inline">
        <img srcset="https://cdn.example.test/srcset-only.png 1x" alt="Srcset only">
        <img src="https://t.example.test/open.gif" width="1" height="1">
        <img src="https://t.example.test/track/open" alt="">
        <video src="https://cdn.example.test/v.mp4"></video>
    """

    private fun assert_no_remote_sources(html: String) {
        val doc = Jsoup.parse(html)
        for (element in doc.select("[src], [srcset]")) {
            for (name in listOf("src", "srcset")) {
                assertFalse(
                    "remote $name survived on <${element.normalName()}>: ${element.attr(name)}",
                    remote.containsMatchIn(element.attr(name)),
                )
            }
        }
        for (img in doc.select("img[data-blocked=true]")) {
            assertTrue(img.attr("src").startsWith("data:image/svg+xml,"))
            assertFalse(img.hasAttr("srcset"))
            assertFalse(img.hasAttr("sizes"))
        }
    }

    @Test
    fun blocked_mode_never_renders_a_remote_src_or_srcset() {
        for (remove_tracking_pixels in listOf(true, false)) {
            for (is_dark in listOf(false, true)) {
                val sanitized = EmailHtmlSanitizer.sanitize(
                    newsletter,
                    EmailHtmlSanitizer.SanitizeOptions(remove_tracking_pixels = remove_tracking_pixels),
                )
                val body = EmailHtmlSanitizer.neutralize_blocked_backgrounds(
                    EmailHtmlSanitizer.replace_blocked_images(sanitized),
                )
                val document = build_email_html(
                    body = body,
                    is_dark = is_dark,
                    fg_hex = "#111111",
                    link_hex = "#3b82f6",
                    forwarded_label = "Forwarded",
                    image_failed_label = "Image could not be loaded",
                    force_dark_emails = false,
                    dyslexia_font = false,
                    translate_mode = "off",
                )

                assert_no_remote_sources(document)
                val images = Jsoup.parse(document).select("#m img")
                assertTrue(images.count { it.attr("data-blocked") == "true" } >= 5)
                if (!remove_tracking_pixels) {
                    assertTrue(images.any { it.attr("data-tracking-pixel") == "true" && it.attr("width") == "1" })
                }
            }
        }
    }

    @Test
    fun the_blocker_strips_remote_sources_even_from_unsanitized_markup() {
        val out = EmailHtmlSanitizer.replace_blocked_images(newsletter)

        assert_no_remote_sources(out)
        val doc = Jsoup.parseBodyFragment(out)
        assertEquals(2, doc.select("picture source").size)
        assertTrue(doc.select("picture source").none { it.hasAttr("srcset") })
        assertTrue(doc.select("img").filter { it.attr("data-blocked") == "true" }.size >= 7)
        assertEquals("data:image/png;base64,AAAA", doc.selectFirst("img[alt=Inline]")!!.attr("src"))
    }

    @Test
    fun print_documents_never_carry_a_remote_src_or_srcset() {
        val body = EmailHtmlSanitizer.neutralize_blocked_backgrounds(
            EmailHtmlSanitizer.replace_blocked_images(
                EmailHtmlSanitizer.sanitize(newsletter, EmailHtmlSanitizer.SanitizeOptions(remove_tracking_pixels = false)),
            ),
        )

        assert_no_remote_sources(body)
    }
}
