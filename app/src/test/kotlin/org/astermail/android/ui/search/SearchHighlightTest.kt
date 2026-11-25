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
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchHighlightTest {

    private val accent = 0xFF3B82F6.toInt()

    private fun page(body: String): String =
        "<!DOCTYPE html><html><head><style>p{color:red}</style></head><body><div id=\"m\">$body</div></body></html>"

    private fun hits_text(html: String): List<String> =
        Jsoup.parse(html).select("mark.$SEARCH_HIT_CLASS").map { it.text() }

    @Test
    fun query_terms_drop_operators_and_negations() {
        val terms = search_highlight_terms_for(parse_query("from:alice \"quarterly report\" Café -spam has:attachment"))
        assertTrue(terms.body.contains("quarterly report"))
        assertTrue(terms.body.contains("cafe"))
        assertFalse(terms.body.any { it.contains("spam") })
        assertFalse(terms.body.any { it.contains("alice") || it.contains("attachment") || it.contains(":") })
        assertTrue(terms.sender.contains("alice"))
        assertFalse(terms.subject.contains("alice"))
    }

    @Test
    fun subject_operator_only_reaches_subject() {
        val terms = search_highlight_terms_for(parse_query("subject:invoice"))
        assertEquals(listOf("invoice"), terms.subject)
        assertTrue(terms.body.isEmpty())
    }

    @Test
    fun single_latin_letters_are_skipped_but_cjk_is_kept() {
        assertEquals(emptyList<String>(), clean_search_terms(listOf("a", " ")))
        assertEquals(listOf("猫"), clean_search_terms(listOf("猫")))
    }

    @Test
    fun matching_ignores_case_and_diacritics() {
        val text = "Résumé from CAFÉ and resume"
        val ranges = find_search_hit_ranges(text, listOf("resume", "cafe"))
        assertEquals(listOf("Résumé", "CAFÉ", "resume"), ranges.map { text.substring(it.start, it.end) })
    }

    @Test
    fun combining_marks_stay_inside_the_hit() {
        val text = "café time"
        val range = find_search_hit_ranges(text, listOf("cafe")).single()
        assertEquals("café", text.substring(range.start, range.end))
    }

    @Test
    fun each_term_matches_separately_and_overlaps_merge() {
        val text = "project plan and planning"
        val ranges = find_search_hit_ranges(text, listOf("plan", "planning", "project"))
        assertEquals(listOf("project", "plan", "planning"), ranges.map { text.substring(it.start, it.end) })
        val merged = merge_search_hit_ranges(listOf(search_hit_range(0, 4), search_hit_range(2, 6), search_hit_range(8, 9)))
        assertEquals(listOf(search_hit_range(0, 6), search_hit_range(8, 9)), merged)
    }

    @Test
    fun regex_metacharacters_are_literal() {
        val text = "price is \$5.00 (approx) a+b"
        assertEquals(1, count_search_hits(text, listOf("(approx)")))
        assertEquals(1, count_search_hits(text, listOf("a+b")))
        assertEquals(0, count_search_hits(text, listOf(".*")))
    }

    @Test
    fun annotated_highlight_styles_every_hit() {
        val style = SpanStyle(background = Color.Yellow)
        val current = SpanStyle(background = Color.Red)
        val result = highlight_annotated(AnnotatedString("Budget and budget"), listOf("budget"), style, current, 1)
        assertEquals(2, result.spanStyles.size)
        assertEquals(style, result.spanStyles[0].item)
        assertEquals(current, result.spanStyles[1].item)
        assertEquals(11, result.spanStyles[1].start)
    }

    @Test
    fun snippet_window_shifts_to_a_late_match() {
        val text = "Hello there, this is a long introduction before we get to the invoice details"
        val window = search_snippet_window(text, listOf("invoice"))
        assertTrue(window.shifted)
        assertTrue(window.text.startsWith("…"))
        assertTrue(window.text.contains("invoice"))
        assertFalse(search_snippet_window("invoice first", listOf("invoice")).shifted)
    }

    @Test
    fun marking_touches_text_nodes_only() {
        val html = page(
            "<p title=\"invoice\">Your invoice is ready</p>" +
                "<a href=\"https://example.com/invoice\">open invoice</a>" +
                "<style>.invoice{color:blue}</style><script>var invoice=1</script>",
        )
        val marked = mark_search_hits(html, listOf("invoice"), accent, dark = false, reduce_motion = false)
        assertEquals(2, marked.hits)
        val doc = Jsoup.parse(marked.html)
        assertEquals("invoice", doc.selectFirst("p")!!.attr("title"))
        assertEquals("https://example.com/invoice", doc.selectFirst("a")!!.attr("href"))
        assertEquals("open invoice", doc.selectFirst("a")!!.text())
        assertTrue(doc.select("script").first()!!.data().contains("var invoice=1"))
        assertEquals(listOf("invoice", "invoice"), hits_text(marked.html))
        assertEquals(listOf("aster_hit_0", "aster_hit_1"), doc.select("mark.$SEARCH_HIT_CLASS").map { it.id() })
    }

    @Test
    fun marking_escapes_hostile_text_and_terms() {
        val html = page("Look: &lt;script&gt;alert(1)&lt;/script&gt; done")
        val marked = mark_search_hits(html, listOf("<script>"), accent, dark = true, reduce_motion = true)
        assertEquals(1, marked.hits)
        val doc = Jsoup.parse(marked.html)
        assertTrue(doc.select("div#m script").isEmpty())
        assertEquals("<script>", doc.selectFirst("mark.$SEARCH_HIT_CLASS")!!.text())
        assertFalse(marked.html.contains("<script>alert"))
    }

    @Test
    fun marking_counts_hits_inside_collapsed_quotes() {
        val html = page("Top note<details><summary>Show quoted text</summary><blockquote>older note</blockquote></details>")
        val marked = mark_search_hits(html, listOf("note"), accent, dark = false, reduce_motion = false)
        assertEquals(2, marked.hits)
        assertNotNull(Jsoup.parse(marked.html).selectFirst("details mark.$SEARCH_HIT_CLASS"))
    }

    @Test
    fun marking_without_hits_returns_the_input() {
        val html = page("nothing here")
        val marked = mark_search_hits(html, listOf("invoice"), accent, dark = false, reduce_motion = false)
        assertEquals(0, marked.hits)
        assertEquals(html, marked.html)
    }

    @Test
    fun marking_opens_only_quotes_that_hold_a_hit() {
        val html = page(
            "Top note<details><summary>Show quoted text</summary><blockquote>older note</blockquote></details>" +
                "<details><summary>Show quoted text</summary><blockquote>unrelated</blockquote></details>",
        )
        val marked = mark_search_hits(html, listOf("note"), accent, dark = false, reduce_motion = false)
        val doc = Jsoup.parse(marked.html)
        val quotes = doc.select("details")
        assertTrue(quotes[0].hasAttr("open"))
        assertFalse(quotes[1].hasAttr("open"))
        assertEquals(1, marked.opened_quotes)
    }

    @Test
    fun marking_adds_no_script_and_orders_hit_positions() {
        val html = page("alpha " + "filler ".repeat(40) + "alpha " + "filler ".repeat(40) + "alpha")
        val marked = mark_search_hits(html, listOf("alpha"), accent, dark = true, reduce_motion = false)
        assertEquals(3, marked.hits)
        assertEquals(Jsoup.parse(html).select("script").size, Jsoup.parse(marked.html).select("script").size)
        assertFalse(marked.html.contains("javascript:", ignoreCase = true))
        assertEquals(0f, marked.fraction_of(0))
        assertTrue(marked.fraction_of(1) in 0.4f..0.6f)
        assertTrue(marked.fraction_of(2) > 0.9f)
        assertEquals(0f, marked.fraction_of(7))
    }

    @Test
    fun palette_keeps_text_readable() {
        val light = search_hit_palette_for(accent, dark = false)
        val dark = search_hit_palette_for(accent, dark = true)
        assertTrue(luminance(light.background) > luminance(light.foreground) + 0.4)
        assertTrue(luminance(dark.foreground) > luminance(dark.background) + 0.4)
    }

    @Test
    fun session_locates_and_steps_across_messages() {
        val session = search_highlight_session(search_highlight_terms(listOf("x1"), listOf("x1"), emptyList()), "b")
        session.order = listOf("a", "b", "c")
        session.subject_count = 1
        session.report_count("a", 2)
        session.report_count("c", 3)
        assertEquals(6, session.total)
        assertEquals(search_hit_location(null, 0), session.locate(0))
        assertEquals(search_hit_location("a", 1), session.locate(2))
        assertEquals(search_hit_location("c", 0), session.locate(3))
        assertNull(session.locate(6))
        assertEquals(-1, session.first_index_in("b"))
        assertEquals(3, session.first_index_in("c"))
        assertEquals(0, session.step(1))
        assertEquals(5, session.step(-1))
        assertEquals(0, session.step(1))
        session.seed_count("a", 9)
        assertEquals(2, session.counts["a"])
        session.seed_count("b", 4)
        assertEquals(4, session.counts["b"])
        session.dismiss()
        assertEquals(0, session.total)
        assertFalse(session.active)
    }

    @Test
    fun handoff_is_single_use_and_expires() {
        val terms = search_highlight_terms(listOf("hello"), listOf("hello"), emptyList())
        search_highlight_handoff.offer("m1", terms, now_ms = 1_000L)
        assertNull(search_highlight_handoff.take("m2", now_ms = 1_100L))
        assertEquals(terms, search_highlight_handoff.take("m1", now_ms = 1_200L))
        assertNull(search_highlight_handoff.take("m1", now_ms = 1_300L))
        search_highlight_handoff.offer("m1", terms, now_ms = 0L)
        assertNull(search_highlight_handoff.take("m1", now_ms = 60_000L))
    }

    private fun luminance(argb: Int): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
