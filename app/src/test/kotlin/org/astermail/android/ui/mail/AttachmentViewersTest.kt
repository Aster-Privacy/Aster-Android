// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
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

package org.astermail.android.ui.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentViewersTest {
    @Test
    fun text_types_open_in_the_text_viewer() {
        assertTrue(is_text_attachment("text/plain; charset=utf-8", "notes"))
        assertTrue(is_text_attachment("application/json", "data.bin"))
        assertTrue(is_text_attachment("application/vnd.api+json", "data"))
        assertTrue(is_text_attachment("application/octet-stream", "server.LOG"))
        assertTrue(is_text_attachment("", "config.yaml"))
    }

    @Test
    fun binary_types_do_not_open_in_the_text_viewer() {
        assertFalse(is_text_attachment("application/zip", "notes.txt"))
        assertFalse(is_text_attachment("application/octet-stream", "photo.heic"))
        assertFalse(is_text_attachment("application/octet-stream", "README"))
        assertFalse(is_text_attachment("image/png", "a.png"))
    }

    @Test
    fun large_images_are_sampled_down_by_powers_of_two() {
        assertEquals(1, image_sample_size(800, 600, max_dimension = 4096))
        assertEquals(2, image_sample_size(8000, 600, max_dimension = 4096))
        assertEquals(4, image_sample_size(600, 12_000, max_dimension = 4096))
    }

    @Test
    fun text_is_split_on_every_line_ending_style() {
        assertEquals(listOf("a", "b", "c", "d"), text_viewer_lines("a\r\nb\nc\rd\n\n".toByteArray()))
    }

    @Test
    fun a_byte_order_mark_is_not_shown() {
        assertEquals(listOf("hello"), text_viewer_lines("\uFEFFhello".toByteArray()))
    }

    @Test
    fun very_long_lines_are_broken_into_pieces() {
        val lines = text_viewer_lines("x".repeat(25).toByteArray(), max_line_chars = 10)
        assertEquals(listOf(10, 10, 5), lines.map { it.length })
    }

    @Test
    fun an_empty_file_is_one_empty_line() {
        assertEquals(listOf(""), text_viewer_lines(ByteArray(0)))
    }

    @Test
    fun text_size_stays_readable_while_pinching() {
        assertEquals(TEXT_VIEWER_MAX_FONT_SP, text_viewer_font_size(30f, 2f), 0.01f)
        assertEquals(TEXT_VIEWER_MIN_FONT_SP, text_viewer_font_size(10f, 0.5f), 0.01f)
        assertEquals(19.5f, text_viewer_font_size(13f, 1.5f), 0.01f)
    }

    @Test
    fun zoomed_pages_render_sharper_within_a_limit() {
        assertEquals(1000, pdf_render_width(1000, zoomed = false))
        assertEquals(2000, pdf_render_width(1000, zoomed = true))
        assertEquals(2400, pdf_render_width(1500, zoomed = true))
        assertEquals(3000, pdf_render_width(3000, zoomed = true))
    }

    @Test
    fun the_page_under_the_middle_of_the_screen_is_the_current_page() {
        val spans = listOf(
            pdf_page_span(3, -900, 500),
            pdf_page_span(4, 512, 1912),
            pdf_page_span(5, 1924, 3324),
        )
        assertEquals(4, pdf_page_at(spans, 1000))
        assertEquals(3, pdf_page_at(spans, 100))
        assertEquals(3, pdf_page_at(spans, 504))
        assertEquals(4, pdf_page_at(spans, 510))
        assertNull(pdf_page_at(emptyList(), 1000))
    }
}
