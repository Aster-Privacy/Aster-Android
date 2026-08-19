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

package org.astermail.android.compose

import org.astermail.android.ui.compose.inline_style_state
import org.astermail.android.ui.compose.render_inline_style_html
import org.astermail.android.ui.compose.serializable_font_color
import org.astermail.android.ui.compose.serializable_font_size_px
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class inline_style_html_test {

    private fun render(text: String, style_of: (Int) -> inline_style_state): String =
        render_inline_style_html(
            length = text.length,
            style_at = style_of,
            char_at = { text[it].toString() },
        )

    private fun render_uniform(text: String, style: inline_style_state): String =
        render(text) { style }

    @Test
    fun plain_text_without_styling_is_returned_unwrapped() {
        assertEquals("hello", render_uniform("hello", inline_style_state()))
    }

    @Test
    fun an_empty_range_renders_nothing() {
        assertEquals("", render_uniform("", inline_style_state()))
        assertEquals("", render_inline_style_html(0, { inline_style_state() }, { "" }))
    }

    @Test
    fun a_font_size_becomes_a_pixel_span_matching_the_web_client() {
        assertEquals(
            "<span style=\"font-size:14px\">hi</span>",
            render_uniform("hi", inline_style_state(font_size_px = 14)),
        )
        assertEquals(
            "<span style=\"font-size:24px\">hi</span>",
            render_uniform("hi", inline_style_state(font_size_px = 24)),
        )
    }

    @Test
    fun a_font_color_becomes_a_color_span_matching_the_web_client() {
        assertEquals(
            "<span style=\"color:#1a73e8\">hi</span>",
            render_uniform("hi", inline_style_state(font_color = "#1a73e8")),
        )
    }

    @Test
    fun size_wraps_color_which_wraps_the_character_styles() {
        assertEquals(
            "<span style=\"font-size:18px\"><span style=\"color:#1a73e8\"><b><i>hi</i></b></span></span>",
            render_uniform(
                "hi",
                inline_style_state(font_size_px = 18, font_color = "#1a73e8", bold = true, italic = true),
            ),
        )
    }

    @Test
    fun an_out_of_range_font_size_is_dropped_rather_than_serialized() {
        assertEquals("hi", render_uniform("hi", inline_style_state(font_size_px = 0)))
        assertEquals("hi", render_uniform("hi", inline_style_state(font_size_px = -14)))
        assertEquals("hi", render_uniform("hi", inline_style_state(font_size_px = 4000)))
    }

    @Test
    fun a_malformed_color_is_dropped_rather_than_injected_into_the_style_attribute() {
        val hostile = listOf(
            "red;background:url(x)",
            "#1a73e8;background:url(x)",
            "red",
            "#1A73E8",
            "#abc",
            "\"onload=\"alert(1)",
            "",
        )
        for (value in hostile) {
            assertEquals(value, "hi", render_uniform("hi", inline_style_state(font_color = value)))
        }
    }

    @Test
    fun tags_close_in_reverse_order_when_an_outer_style_ends() {
        val styled = inline_style_state(font_size_px = 12, bold = true, italic = true)
        val result = render("ab") { if (it == 0) styled else inline_style_state(bold = true) }

        assertEquals("<span style=\"font-size:12px\"><b><i>a</i></b></span><b>b</b>", result)
    }

    @Test
    fun a_nested_style_ending_early_does_not_break_the_outer_span() {
        val result = render("abc") {
            inline_style_state(font_size_px = 14, bold = it == 1)
        }

        assertEquals("<span style=\"font-size:14px\">a<b>b</b>c</span>", result)
    }

    @Test
    fun overlapping_bold_and_italic_stay_properly_nested() {
        val result = render("abcd") {
            inline_style_state(bold = it <= 2, italic = it >= 1)
        }

        assertEquals("<b>a<i>bc</i></b><i>d</i>", result)
    }

    @Test
    fun a_size_change_mid_run_closes_and_reopens_the_span() {
        val result = render("ab") {
            inline_style_state(font_size_px = if (it == 0) 12 else 24)
        }

        assertEquals(
            "<span style=\"font-size:12px\">a</span><span style=\"font-size:24px\">b</span>",
            result,
        )
    }

    @Test
    fun a_link_sits_inside_the_size_and_color_spans() {
        val result = render_uniform(
            "hi",
            inline_style_state(font_size_px = 14, font_color = "#0a0b0c", href = "https://example.com"),
        )

        assertEquals(
            "<span style=\"font-size:14px\"><span style=\"color:#0a0b0c\">" +
                "<a href=\"https://example.com\">hi</a></span></span>",
            result,
        )
    }

    @Test
    fun a_link_change_reopens_the_character_styles_inside_the_new_link() {
        val result = render("ab") {
            inline_style_state(href = if (it == 0) "https://a.test" else "https://b.test", bold = true)
        }

        assertEquals(
            "<a href=\"https://a.test\"><b>a</b></a><a href=\"https://b.test\"><b>b</b></a>",
            result,
        )
    }

    @Test
    fun underline_and_strikethrough_still_serialize() {
        assertEquals(
            "<u><s>hi</s></u>",
            render_uniform("hi", inline_style_state(underline = true, strike = true)),
        )
    }

    @Test
    fun a_span_color_is_serialized_from_an_argb_value_as_lowercase_hex() {
        assertEquals("#1a73e8", serializable_font_color(0xFF1A73E8.toInt()))
        assertEquals("#000000", serializable_font_color(0xFF000000.toInt()))
        assertEquals("#ffffff", serializable_font_color(0xFFFFFFFF.toInt()))
        assertEquals("#0a0b0c", serializable_font_color(0x000A0B0C))
    }

    @Test
    fun only_plausible_pixel_sizes_are_serializable() {
        assertEquals(12, serializable_font_size_px(12))
        assertEquals(24, serializable_font_size_px(24))
        assertNull(serializable_font_size_px(0))
        assertNull(serializable_font_size_px(7))
        assertNull(serializable_font_size_px(97))
        assertNull(serializable_font_size_px(Int.MIN_VALUE))
    }
}
