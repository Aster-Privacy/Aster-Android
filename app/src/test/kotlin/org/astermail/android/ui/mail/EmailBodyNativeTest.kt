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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmailBodyNativeTest {

    private fun prepare(
        body: String,
        is_newsletter: Boolean = false,
        simple_dark: Boolean = false,
    ): String = prepare_email_body(
        body = body,
        forwarded_label = "Forwarded message",
        image_failed_label = "Image unavailable",
        is_newsletter = is_newsletter,
        simple_dark = simple_dark,
    )

    @Test
    fun bare_urls_become_links_without_script() {
        val prepared = prepare("<p>Read https://example.org/docs today</p>")

        assertTrue(prepared.contains("<a href=\"https://example.org/docs\">https://example.org/docs</a>"))
    }

    @Test
    fun an_address_inside_an_anchor_is_left_alone() {
        val prepared = prepare("<p><a href=\"mailto:a@b.com\">a@b.com</a></p>")

        assertEquals(1, prepared.split("<a ").size - 1)
    }

    @Test
    fun quoted_replies_collapse_into_a_native_disclosure() {
        val body = "<div>Thanks</div><div>On Monday, someone wrote:</div>" +
            "<blockquote>the original message body</blockquote>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("<details class=\"aster-quoted-wrapper\">"))
        assertTrue(prepared.contains("<summary class=\"aster-quote-toggle\">"))
        assertTrue(prepared.contains("aster-quoted-content"))
        assertFalse(prepared.contains("<button"))
        assertFalse(prepared.contains("onclick"))
    }

    @Test
    fun an_image_gets_a_tap_target_the_app_can_route() {
        val prepared = prepare("<p><img src=\"https://example.org/a.png\"></p>")

        assertTrue(prepared.contains("class=\"aster-image-zoom\""))
        assertTrue(prepared.contains("href=\"asterimg:https%3A%2F%2Fexample.org%2Fa.png\""))
    }

    @Test
    fun a_remote_image_carries_its_failure_label_as_data() {
        val prepared = prepare("<p><img src=\"https://example.org/a.png\"></p>")

        assertTrue(prepared.contains("$FAILED_IMAGE_LABEL_ATTRIBUTE=\"Image unavailable\""))
    }

    @Test
    fun a_fixed_height_cell_can_still_grow() {
        val prepared = prepare("<table><tr><td style=\"height:20px\">a long line of text</td></tr></table>")

        assertTrue(prepared.contains("height:auto"))
        assertTrue(prepared.contains("min-height:20px"))
    }

    @Test
    fun dark_rendering_lifts_unreadable_inline_text() {
        val prepared = prepare("<p style=\"color:#111111\">hello</p>", simple_dark = true)

        assertTrue(prepared.contains("#e8e8e8"))
    }

    @Test
    fun malformed_markup_falls_back_to_the_original_body() {
        val body = "plain text with no markup at all"

        assertEquals(body, prepare(body).trim())
    }

    @Test
    fun translatable_text_survives_a_round_trip() {
        val body = "<p>Bonjour tout le monde</p><p>Deuxieme phrase ici</p>"
        val segments = extract_translatable_segments(body)

        assertEquals(listOf("Bonjour tout le monde", "Deuxieme phrase ici"), segments)

        val applied = apply_translated_segments(body, listOf("Hello everyone", "Second sentence here"))

        assertTrue(applied.contains("Hello everyone"))
        assertTrue(applied.contains("Second sentence here"))
        assertFalse(applied.contains("Bonjour"))
    }

    @Test
    fun script_and_style_text_is_never_sent_for_translation() {
        val body = "<style>.a{color:red}</style><script>alert('hi')</script><p>Bonjour le monde</p>"

        assertEquals(listOf("Bonjour le monde"), extract_translatable_segments(body))
    }

    @Test
    fun a_count_mismatch_leaves_the_body_untouched() {
        val body = "<p>Bonjour tout le monde</p><p>Deuxieme phrase ici</p>"

        assertEquals(body, apply_translated_segments(body, listOf("only one")))
    }

    @Test
    fun a_declared_background_is_read_without_script() {
        assertEquals("#ffffff", detect_body_background("<table bgcolor=\"#ffffff\"><tr><td>a</td></tr></table>"))
        assertTrue(background_reads_light("#ffffff"))
        assertFalse(background_reads_light("#101010"))
        assertFalse(background_reads_light(null))
    }

    @Test
    fun an_unsafe_background_value_is_refused() {
        assertNull(detect_body_background("<div style=\"background-color:url(javascript:alert(1))\">a</div>"))
    }
    @Test
    fun a_fixed_width_layout_reports_the_width_the_viewport_must_fit() {
        val body = "<div style=\"width:900px\"><p style=\"width:900px\">Your statement is ready.</p></div>"

        assertEquals(900, declared_content_width(body))
    }

    @Test
    fun an_ordinary_email_asks_for_no_fitting() {
        assertNull(declared_content_width("<p>Are we still on for Thursday?</p>"))
    }

    @Test
    fun a_wide_image_alone_does_not_shrink_the_page() {
        assertNull(declared_content_width("<p>Photo</p><img src=\"https://example.org/a.png\" width=\"1200\">"))
    }

    @Test
    fun an_absurd_width_is_capped() {
        assertEquals(MAX_FIT_CONTENT_WIDTH, declared_content_width("<div style=\"width:9000px\">wide</div>"))
    }

    @Test
    fun a_max_width_declaration_is_not_a_fixed_width() {
        assertNull(declared_content_width("<div style=\"max-width:900px\">fluid</div>"))
    }

    @Test
    fun a_newsletter_table_width_attribute_is_read() {
        assertEquals(640, declared_content_width("<table width=\"640\"><tr><td>Hello</td></tr></table>"))
    }

    @Test
    fun a_fixed_width_capped_by_a_fluid_max_width_is_already_responsive() {
        val body = "<div style=\"width:100%\"><div style=\"width:640px;max-width:100%;margin:0 auto\"><p>Welcome</p></div></div>"

        assertNull(declared_content_width(body))
    }


    @Test
    fun a_forward_that_is_only_a_quote_stays_expanded() {
        val body = "<br><div class=\"aster_quote gmail_quote\">" +
            "<div class=\"aster_quote_attr gmail_attr\"><div><b>From:</b> a sender</div></div>" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertFalse(prepared.contains("aster-quoted-wrapper"))
        assertTrue(prepared.contains("aster-quoted-content"))
        assertTrue(prepared.contains("the original message body"))
    }

    @Test
    fun blank_lines_before_a_collapsed_quote_are_dropped() {
        val body = "<div>Reply number 8.</div><br><br><br>" +
            "<div class=\"aster_quote gmail_quote\">" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("aster-quoted-wrapper"))
        assertFalse(prepared.contains("<br>"))
    }

    @Test
    fun blank_lines_inside_the_last_block_before_a_quote_are_dropped() {
        val body = "<div>Reply number 8.<br><br></div>" +
            "<div class=\"aster_quote gmail_quote\">" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("aster-quoted-wrapper"))
        assertFalse(prepared.contains("<br>"))
    }

    @Test
    fun a_forward_with_its_own_text_still_collapses_the_quote() {
        val body = "<div>Passing this along.</div><div class=\"aster_quote gmail_quote\">" +
            "<blockquote class=\"gmail_quote\"><p>the original message body</p></blockquote></div>"
        val prepared = prepare(body)

        assertTrue(prepared.contains("aster-quoted-wrapper"))
    }
}
