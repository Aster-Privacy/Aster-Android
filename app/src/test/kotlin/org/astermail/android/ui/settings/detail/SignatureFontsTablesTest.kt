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
package org.astermail.android.ui.settings.detail

import org.astermail.android.ui.compose.plain_signature_display_html
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureFontsTablesTest {
    @Test
    fun font_family_list_includes_monospace() {
        assertTrue(signature_font_families.any { it.is_monospace && it.stack.endsWith("monospace") })
        assertTrue(signature_font_family_allowed(signature_default_font_family))
        signature_font_families.forEach { assertTrue(signature_font_family_allowed(it.stack)) }
    }

    @Test
    fun font_family_rejects_values_outside_the_list() {
        assertFalse(signature_font_family_allowed("Arial; background: url(x)"))
        assertFalse(signature_font_family_allowed("expression(alert(1))"))
        assertFalse(signature_font_family_allowed(""))
    }

    @Test
    fun font_family_stacks_carry_no_css_breakouts() {
        signature_font_families.forEach { family ->
            assertFalse(family.stack.contains(';'))
            assertFalse(family.stack.contains('"'))
            assertFalse(family.stack.contains('('))
        }
    }

    @Test
    fun plain_signature_keeps_ascii_art_spacing() {
        assertEquals(
            "&nbsp; /\\_/\\<br>( o.o )<br>&nbsp;&gt; ^ &lt;",
            plain_signature_to_html("  /\\_/\\\n( o.o )\n > ^ <"),
        )
    }

    @Test
    fun plain_signature_keeps_runs_of_spaces_and_tabs() {
        assertEquals("a &nbsp;b &nbsp; &nbsp;c", plain_signature_to_html("a  b\tc"))
    }

    @Test
    fun plain_signature_display_matches_editor_conversion() {
        assertEquals(
            "Name &nbsp;Role<br>&nbsp;Ada &amp; Co",
            plain_signature_display_html("Name  Role\r\n Ada & Co"),
        )
    }

    @Test
    fun rich_signature_converts_to_plain_text_without_losing_spaces() {
        val html = "<div style=\"font-family: monospace\">&nbsp;/\\_/\\<br>(&nbsp;o.o&nbsp;)<br>" +
            "&nbsp;&gt;&nbsp;^&nbsp;&lt;</div>"

        assertEquals(" /\\_/\\\n( o.o )\n > ^ <", signature_html_to_plain_text(html))
    }

    @Test
    fun rich_signature_table_converts_to_aligned_plain_text() {
        val html = "<div>Ada</div><table border=\"1\"><tbody><tr><td>Phone</td><td>555 0100</td></tr>" +
            "<tr><td>Web</td><td>example.org</td></tr></tbody></table><div><br></div>"

        assertEquals("Ada\nPhone  555 0100\nWeb    example.org", signature_html_to_plain_text(html))
    }

    @Test
    fun rich_signature_plain_text_handles_blocks_and_breaks() {
        assertEquals("One\nTwo\n\nThree", signature_html_to_plain_text("<p>One</p><div>Two<br><br>Three</div>"))
        assertEquals("", signature_html_to_plain_text("<div><br></div>"))
    }

    @Test
    fun table_state_parses_editor_json() {
        val raw = org.json.JSONObject.quote("{\"in_table\":true,\"borders\":false,\"rows\":3,\"columns\":2}")

        assertEquals(signature_table_state(true, false, 3, 2), parse_signature_table_state(raw))
        assertEquals(signature_table_state(false), parse_signature_table_state("null"))
        assertEquals(signature_table_state(false), parse_signature_table_state("\"not json\""))
        assertEquals(signature_table_state(false), parse_signature_table_state(null))
    }

    @Test
    fun editor_script_defines_font_and_table_functions() {
        listOf(
            "window.aster_font_family=function(stack)",
            "window.aster_table_insert=function(rows,columns,borders)",
            "window.aster_table_state=function()",
            "window.aster_table_edit=function(action)",
        ).forEach { assertTrue(it, signature_editor_script.contains(it)) }
    }

    @Test
    fun editor_table_limits_match_the_web_client() {
        assertTrue(signature_editor_script.contains("var max_rows=$signature_table_max_rows;"))
        assertTrue(signature_editor_script.contains("var max_columns=$signature_table_max_columns;"))
        assertTrue(signature_editor_script.contains("1px solid #c4c4c4"))
    }

    @Test
    fun sanitizer_keeps_signature_tables_and_font_stacks() {
        val html = "<table border=\"0\" cellpadding=\"0\" cellspacing=\"0\" " +
            "style=\"border-collapse: collapse; border: none\"><tbody><tr>" +
            "<td style=\"padding: 4px 8px; vertical-align: top; border: none\">" +
            "<span style=\"font-family: 'Courier New', Courier, monospace\">a</span></td></tr></tbody></table>"
        val out = sanitize_signature_html(html)

        assertTrue(out, out.contains("<table"))
        assertTrue(out, out.contains("border=\"0\""))
        assertTrue(out, out.contains("font-family"))
        assertTrue(out, out.contains("monospace"))
    }

    @Test
    fun sanitizer_strips_dangerous_css_from_signature_cells() {
        val html = "<table><tr><td style=\"background: url(https://example.org/t.png); position: fixed\">a</td>" +
            "</tr></table>"
        val out = sanitize_signature_html(html)

        assertFalse(out, out.contains("example.org/t.png"))
        assertFalse(out, out.contains("position: fixed"))
    }

    @Test
    fun table_state_reports_merged_cells() {
        val raw = org.json.JSONObject.quote("{\"in_table\":true,\"borders\":true,\"rows\":2,\"columns\":2,\"merged\":true}")

        assertTrue(parse_signature_table_state(raw).merged)
    }

    @Test
    fun editor_script_sets_borders_explicitly_and_blocks_merged_structure_edits() {
        assertTrue(signature_editor_script.contains("action==='borders_on'||action==='borders_off'"))
        assertTrue(signature_editor_script.contains("structure_actions.indexOf(action)>=0&&has_merged(table)"))
        assertFalse(signature_editor_script.contains("toggle_borders"))
        assertTrue(signature_border_actions.containsAll(listOf("borders_on", "borders_off")))
    }

    @Test
    fun sanitizer_removes_leftover_font_markers() {
        val html = "<div><font face=\"aster_font_marker\">Ada</font> <font face=\"Georgia\">B</font></div>"
        val out = sanitize_signature_html(html)

        assertFalse(out, out.contains("aster_font_marker"))
        assertTrue(out, out.contains("Ada"))
    }

    @Test
    fun font_marker_strip_leaves_other_html_untouched() {
        val html = "<div>a&nbsp;&nbsp;b</div>"

        assertEquals(html, strip_signature_font_markers(html))
    }

    @Test
    fun style_scrub_keeps_editor_table_and_font_styles() {
        assertEquals(
            "padding: 4px 8px; vertical-align: top; border: 1px solid #c4c4c4",
            scrub_signature_style("padding: 4px 8px; vertical-align: top; border: 1px solid #c4c4c4"),
        )
        assertEquals(
            "font-family: \"Courier New\", Courier, monospace; font-size: 14px",
            scrub_signature_style("font-family: \"Courier New\", Courier, monospace; font-size: 14px"),
        )
        assertEquals(
            "border-collapse: collapse; border-spacing: 0; border: none",
            scrub_signature_style("border-collapse: collapse; border-spacing: 0; border: none"),
        )
    }

    @Test
    fun style_scrub_drops_unsafe_declarations() {
        assertEquals("color: #222222", scrub_signature_style("color: #222222; position: absolute"))
        assertEquals(null, scrub_signature_style("background: url(https://example.org/a.png)"))
        assertEquals(null, scrub_signature_style("background-color: u\\72l(x)"))
        assertEquals(null, scrub_signature_style("width: expression(alert(1))"))
        assertEquals(null, scrub_signature_style("behavior: url(x.htc)"))
        assertEquals(null, scrub_signature_style("color: red /* x */"))
        assertEquals(null, scrub_signature_style("display: flex"))
        assertEquals("display: table-cell", scrub_signature_style("display: table-cell; z-index: 9"))
    }

    @Test
    fun sanitizer_removes_style_attribute_when_nothing_is_left() {
        val out = sanitize_signature_html("<div style=\"position: fixed; top: 0\">a</div>")

        assertFalse(out, out.contains("style"))
        assertTrue(out, out.contains(">a<"))
    }

    @Test
    fun font_family_script_handles_a_collapsed_caret() {
        assertTrue(signature_editor_script.contains("caret_range.collapsed"))
        assertTrue(signature_editor_script.contains("String.fromCharCode(8203)"))
    }

    @Test
    fun caret_fillers_are_removed_on_save_and_in_plain_text() {
        val mono = signature_font_families.first { it.is_monospace }.stack
        val html = "<p>a<span style=\"font-family: $mono\">" + signature_filler_char + "b</span></p>"
        val saved = sanitize_signature_html(html)
        assertFalse(saved, saved.contains(signature_filler_char))
        assertTrue(saved, saved.contains("b</span>"))
        assertEquals("ab", signature_html_to_plain_text(html))
    }
}
