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
package org.astermail.android.mail

import org.astermail.android.crypto.ProtectedMimeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test

class PlainTextTablesTest {
    private val two_by_two =
        "<table border=\"0\"><tbody><tr><td>Name</td><td>Role</td></tr>" +
            "<tr><td>Ada</td><td>Engineer</td></tr></tbody></table>"

    @Test
    fun aligns_a_simple_table_into_padded_columns() {
        assertEquals(
            "<div>Name&nbsp;&nbsp;Role<br>Ada&nbsp;&nbsp;&nbsp;Engineer</div>",
            align_simple_tables(two_by_two),
        )
    }

    @Test
    fun plain_text_part_keeps_table_columns_aligned() {
        assertEquals("Name  Role\r\nAda   Engineer", ProtectedMimeBuilder.html_to_plain_text(two_by_two))
    }

    @Test
    fun keeps_text_around_the_table() {
        val html = "<div>Before</div>$two_by_two<div>After</div>"

        assertEquals(
            "Before\r\nName  Role\r\nAda   Engineer\r\nAfter",
            ProtectedMimeBuilder.html_to_plain_text(html),
        )
    }

    @Test
    fun leaves_single_column_tables_alone() {
        val html = "<table><tr><td>Only</td></tr><tr><td>One</td></tr></table>"

        assertEquals(html, align_simple_tables(html))
    }

    @Test
    fun leaves_tables_with_a_caption_alone() {
        val html = "<table><caption>Hours</caption><tr><td>a</td><td>b</td></tr></table>"

        assertEquals(html, align_simple_tables(html))
    }

    @Test
    fun leaves_tables_wider_than_the_limit_alone() {
        val wide = "x".repeat(MAX_ALIGNED_TABLE_WIDTH)
        val html = "<table><tr><td>$wide</td><td>y</td></tr></table>"

        assertEquals(html, align_simple_tables(html))
    }

    @Test
    fun aligns_the_inner_table_of_a_layout_table() {
        val html = "<table><tr><td><table><tr><td>a</td><td>b</td></tr></table></td></tr></table>"

        assertEquals(
            "<table><tr><td><div>a&nbsp;&nbsp;b</div></td></tr></table>",
            align_simple_tables(html),
        )
    }

    @Test
    fun escapes_markup_inside_cells() {
        val html = "<table><tr><td>a &lt; b</td><td>&amp;</td></tr></table>"

        assertEquals("<div>a&nbsp;&lt;&nbsp;b&nbsp;&nbsp;&amp;</div>", align_simple_tables(html))
    }

    @Test
    fun skips_empty_columns_and_splits_cell_lines() {
        val html = "<table><tr><td>Ada<br>Lovelace</td><td></td><td>Math</td></tr></table>"

        assertEquals("Ada       Math\r\nLovelace", ProtectedMimeBuilder.html_to_plain_text(html))
    }

    @Test
    fun keeps_non_breaking_spaces_inside_cells() {
        val html = "<table><tr><td>&nbsp;/\\_/\\</td><td>cat</td></tr></table>"

        assertEquals(" /\\_/\\  cat", ProtectedMimeBuilder.html_to_plain_text(html))
    }

    @Test
    fun measures_cell_width_in_code_points() {
        val html = "<table><tr><td>😀😀</td><td>x</td></tr><tr><td>ab</td><td>y</td></tr></table>"

        assertEquals("😀😀  x\r\nab  y", ProtectedMimeBuilder.html_to_plain_text(html))
    }

    @Test
    fun collapses_source_whitespace_and_leading_blank_lines_in_cells() {
        val html = "<table>\n  <tr>\n    <td>\n      <div>Ada</div>\n    </td>\n    <td>  Math  </td>\n  </tr>\n</table>"

        assertEquals("Ada  Math", ProtectedMimeBuilder.html_to_plain_text(html))
    }

    @Test
    fun plain_text_part_keeps_leading_spaces_of_the_first_line() {
        assertEquals("  /|", ProtectedMimeBuilder.html_to_plain_text("<div>&nbsp;&nbsp;/|</div>"))
    }
}
