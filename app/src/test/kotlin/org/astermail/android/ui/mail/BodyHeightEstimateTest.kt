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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyHeightEstimateTest {

    private val width_dp = 392

    @Test
    fun style_and_head_text_do_not_count() {
        val plain = "<p>Hello there</p>"
        val styled = "<html><head><title>Big title text</title><style>" + ".a{color:red}\n".repeat(400) +
            "</style></head><body><!-- " + "comment ".repeat(500) + " --><p>Hello there</p></body></html>"
        assertEquals(estimated_body_height_dp(plain, width_dp), estimated_body_height_dp(styled, width_dp))
    }

    @Test
    fun quoted_history_is_excluded_and_toggle_added() {
        val reply = "<p>Thanks, sounds good.</p>"
        val history = "<div class=\"gmail_quote\">" + "<p>Earlier message line that is quoted.</p>".repeat(200) + "</div>"
        val with_quote = estimated_body_height_dp(reply + history, width_dp)
        val without_quote = estimated_body_height_dp(reply, width_dp)
        assertEquals(without_quote + ESTIMATE_QUOTE_TOGGLE_DP, with_quote)
        assertTrue(estimate_body_parts(reply + history, width_dp).has_quote)
        assertTrue(estimate_body_parts(reply + "<blockquote>old</blockquote>", width_dp).has_quote)
    }

    @Test
    fun image_heights_scale_to_width() {
        val content = width_dp - ESTIMATE_SIDE_PADDING_DP
        assertEquals(200, estimated_image_dp("<img src=\"a\" width=\"300\" height=\"200\">", content))
        assertEquals(150, estimated_image_dp("<img src=\"a\" width=\"${content * 2}\" height=\"300\">", content))
        assertEquals(ESTIMATE_UNKNOWN_IMAGE_DP, estimated_image_dp("<img src=\"a\">", content))
        assertEquals(80, estimated_image_dp("<img src=\"a\" height=\"80px\">", content))
    }

    @Test
    fun tracking_pixels_add_nothing() {
        val content = width_dp - ESTIMATE_SIDE_PADDING_DP
        assertEquals(0, estimated_image_dp("<img src=\"t\" width=\"1\" height=\"1\">", content))
        assertEquals(0, estimated_image_dp("<img src=\"t\" height=\"0\">", content))
        val base = estimated_body_height_dp("<p>Hi</p>", width_dp)
        assertEquals(base, estimated_body_height_dp("<p>Hi</p><img src=\"t\" width=\"1\" height=\"1\">", width_dp))
    }

    @Test
    fun estimate_is_clamped() {
        assertTrue(estimated_body_height_dp("", width_dp) >= ESTIMATE_MIN_DP)
        assertEquals(ESTIMATE_MAX_DP, estimated_body_height_dp("<p>line</p>".repeat(5000), width_dp))
    }

    @Test
    fun long_lines_wrap() {
        val short = estimated_body_height_dp("<p>word</p>", width_dp)
        val long = estimated_body_height_dp("<p>" + "word ".repeat(200) + "</p>", width_dp)
        assertTrue(long > short + ESTIMATE_LINE_DP * 5)
    }

    @Test
    fun height_key_tracks_dark_mode() {
        val light = body_height_key(42L, false, null, false, is_dark = false, force_dark = false)
        val dark = body_height_key(42L, false, null, false, is_dark = true, force_dark = false)
        val forced = body_height_key(42L, false, null, false, is_dark = true, force_dark = true)
        assertNotEquals(light, dark)
        assertNotEquals(dark, forced)
        assertEquals(dark, body_height_key(42L, false, null, false, is_dark = true, force_dark = false))
        assertNotEquals(light, body_height_key(42L, true, null, false, is_dark = false, force_dark = false))
        assertNotEquals(light, body_height_key(42L, false, "serif", false, is_dark = false, force_dark = false))
    }

    @Test
    fun exact_readings() {
        assertTrue(body_reading_exact(body_reading_verdict.grow, 600, 24))
        assertTrue(body_reading_exact(body_reading_verdict.settle, 24, 24))
        assertTrue(body_reading_exact(body_reading_verdict.settle, 24 + BODY_HEIGHT_SLACK_CSS, 24))
        assertFalse(body_reading_exact(body_reading_verdict.settle, 600, 24))
        assertFalse(body_reading_exact(body_reading_verdict.probe, 24, 24))
        assertFalse(body_reading_exact(body_reading_verdict.stale, 24, 24))
    }

    @Test
    fun only_finished_exact_heights_are_trusted() {
        assertTrue(body_height_trustworthy(exact = true, load_finished = true, height_dp = 300f, exact_dp = 300f))
        assertFalse(body_height_trustworthy(exact = true, load_finished = false, height_dp = 300f, exact_dp = 300f))
        assertFalse(body_height_trustworthy(exact = true, load_finished = true, height_dp = 0f, exact_dp = 0f))
        assertTrue(body_height_trustworthy(exact = false, load_finished = true, height_dp = 300.2f, exact_dp = 300f))
        assertFalse(body_height_trustworthy(exact = false, load_finished = true, height_dp = 640f, exact_dp = 300f))
    }

    @Test
    fun confirm_probe_only_for_unproven_heights() {
        assertTrue(needs_confirm_probe(settled = false, trusted = false, measured = true, probing = false, last_exact = false))
        assertFalse(needs_confirm_probe(settled = true, trusted = false, measured = true, probing = false, last_exact = false))
        assertFalse(needs_confirm_probe(settled = false, trusted = true, measured = true, probing = false, last_exact = false))
        assertFalse(needs_confirm_probe(settled = false, trusted = false, measured = false, probing = false, last_exact = false))
        assertFalse(needs_confirm_probe(settled = false, trusted = false, measured = true, probing = true, last_exact = false))
        assertFalse(needs_confirm_probe(settled = false, trusted = false, measured = true, probing = false, last_exact = true))
    }

    @Test
    fun settle_wait_skipped_when_known() {
        assertEquals(0L, body_settle_wait_ms(trusted = true, load_finished = false))
        assertEquals(0L, body_settle_wait_ms(trusted = false, load_finished = true))
        assertEquals(BODY_SETTLE_WAIT_MS, body_settle_wait_ms(trusted = false, load_finished = false))
    }

    @Test
    fun quote_anchor_returns_delta() {
        assertEquals(-120f, quote_anchor_correction(400f, 280f), 0.001f)
        assertEquals(50f, quote_anchor_correction(400f, 450f), 0.001f)
        assertEquals(0f, quote_anchor_correction(400f, 400.5f), 0.001f)
        assertEquals(0f, quote_anchor_correction(Float.NaN, 300f), 0.001f)
        assertEquals(0f, quote_anchor_correction(300f, Float.NaN), 0.001f)
    }

    @Test
    fun collapsed_spacer_images_render_no_height() {
        val page = build_email_html(
            body = "<p>Hi</p>",
            is_dark = false,
            fg_hex = "#111111",
            link_hex = "#1a56db",
            forwarded_label = "Forwarded message",
            image_failed_label = "Image unavailable",
            force_dark_emails = false,
            dyslexia_font = false,
            translate_mode = "off",
        )
        assertTrue(page.contains("#m img[src^=\"data:image/gif;base64,R0lGODlhAQABAAAAACH5\"]:not([height]){height:0!important}"))
    }
}
