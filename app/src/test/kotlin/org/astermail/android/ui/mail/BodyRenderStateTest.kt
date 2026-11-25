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

class BodyRenderStateTest {

    @Test
    fun a_reading_from_an_older_load_is_rejected() {
        val gate = body_generation_gate()
        val first = gate.next()
        val second = gate.next()

        assertFalse(gate.accepts(first))
        assertTrue(gate.accepts(second))
        assertEquals(
            body_reading_verdict.stale,
            judge_body_reading(gate.accepts(first), 900, 24, 24, false),
        )
    }

    @Test
    fun a_released_gate_rejects_every_generation() {
        val gate = body_generation_gate()
        val live = gate.next()
        gate.release()

        assertTrue(gate.is_released)
        assertFalse(gate.accepts(live))
        assertFalse(gate.accepts(gate.generation))
        assertFalse(gate.accepts(gate.next()))
    }

    @Test
    fun a_taller_document_grows_the_view() {
        assertEquals(body_reading_verdict.grow, judge_body_reading(true, 1200, 24, 24, false))
    }

    @Test
    fun a_reading_at_the_probe_height_is_exact() {
        assertEquals(body_reading_verdict.settle, judge_body_reading(true, 24, 24, 24, false))
    }

    @Test
    fun a_reading_that_only_matches_an_unknown_view_height_needs_a_probe() {
        assertEquals(body_reading_verdict.probe, judge_body_reading(true, 400, 400, 24, false))
    }

    @Test
    fun a_reading_that_matches_a_known_height_settles() {
        assertEquals(body_reading_verdict.settle, judge_body_reading(true, 400, 400, 24, true))
    }

    @Test
    fun zero_or_negative_readings_are_ignored() {
        assertEquals(body_reading_verdict.stale, judge_body_reading(true, 0, 24, 24, false))
        assertEquals(body_reading_verdict.stale, judge_body_reading(true, 300, 0, 24, false))
    }

    @Test
    fun nothing_is_cached_before_the_page_paints() {
        assertFalse(body_height_cacheable(true, false, body_reading_verdict.grow, 800f))
        assertTrue(body_height_cacheable(true, true, body_reading_verdict.grow, 800f))
    }

    @Test
    fun stale_probe_or_empty_readings_are_never_cached() {
        assertFalse(body_height_cacheable(false, true, body_reading_verdict.grow, 800f))
        assertFalse(body_height_cacheable(true, true, body_reading_verdict.probe, 800f))
        assertFalse(body_height_cacheable(true, true, body_reading_verdict.stale, 800f))
        assertFalse(body_height_cacheable(true, true, body_reading_verdict.settle, 0f))
    }

    @Test
    fun a_cache_hit_only_seeds_the_height_and_never_counts_as_measured() {
        val hit = seed_body_height(640f, 240f)
        val miss = seed_body_height(null, 240f)

        assertEquals(640f, hit.view_dp)
        assertEquals(640f, hit.reserve_dp, 0f)
        assertFalse(hit.measured)
        assertNull(miss.view_dp)
        assertEquals(240f, miss.reserve_dp, 0f)
        assertFalse(miss.measured)
        assertNull(seed_body_height(0f, 240f).view_dp)
    }

    @Test
    fun a_reading_is_clamped_to_the_layout_limit() {
        assertEquals(5000f, body_reading_height_dp(90000, 5000), 0f)
        assertEquals(1f, body_reading_height_dp(0, 5000), 0f)
    }

    @Test
    fun the_skeleton_waits_before_showing_and_never_covers_a_measured_body() {
        assertFalse(show_body_skeleton(false, 0L))
        assertFalse(show_body_skeleton(false, BODY_SKELETON_DELAY_MS - 1))
        assertTrue(show_body_skeleton(false, BODY_SKELETON_DELAY_MS))
        assertFalse(show_body_skeleton(true, 10_000L))
    }

    @Test
    fun collapsing_returns_to_the_first_settled_height() {
        val quote = quote_toggle_state()
        quote.on_exact_height(300f)
        quote.on_exact_height(900f)

        assertTrue(quote.is_expanded)
        assertEquals(300f, quote.collapsed_height_dp)
        assertEquals(600f, quote.on_collapse_requested(), 0f)
        assertFalse(quote.is_expanded)
        assertEquals(0f, quote.on_collapse_requested(), 0f)
    }

    @Test
    fun collapsing_before_any_measurement_does_nothing() {
        val quote = quote_toggle_state()

        assertNull(quote.collapsed_height_dp)
        assertEquals(0f, quote.on_collapse_requested(), 0f)
        assertFalse(quote.is_expanded)
    }

    @Test
    fun a_new_document_forgets_the_old_collapsed_height() {
        val quote = quote_toggle_state()
        quote.on_exact_height(300f)
        quote.reset()
        quote.on_exact_height(500f)

        assertEquals(500f, quote.collapsed_height_dp)
        assertFalse(quote.is_expanded)
    }

    @Test
    fun a_width_change_while_expanded_forgets_the_collapsed_height() {
        val quote = quote_toggle_state()
        quote.on_exact_height(300f)
        quote.on_exact_height(900f)
        quote.on_layout_changed()
        quote.on_exact_height(700f)

        assertNull(quote.collapsed_height_dp)
        assertTrue(quote.is_expanded)
        assertEquals(0f, quote.on_collapse_requested(), 0f)
    }

    @Test
    fun a_fresh_document_after_a_width_change_measures_again() {
        val quote = quote_toggle_state()
        quote.on_exact_height(300f)
        quote.on_layout_changed()
        quote.reset()
        quote.on_exact_height(260f)

        assertEquals(260f, quote.collapsed_height_dp)
        assertFalse(quote.is_expanded)
    }

    @Test
    fun the_least_recently_used_detached_view_is_released_first() {
        val released = mutableListOf<String>()
        val lru = live_view_lru<String, String>(2) { released.add(it) }
        lru.put("a", "view_a")
        lru.put("b", "view_b")
        lru.get("a")
        lru.put("c", "view_c")

        assertEquals(listOf("view_b"), released)
        assertTrue(lru.contains("a"))
        assertTrue(lru.contains("c"))
        assertEquals(2, lru.size)
    }

    @Test
    fun attached_views_are_never_evicted_by_the_cap() {
        val released = mutableListOf<String>()
        val lru = live_view_lru<String, String>(1) { released.add(it) }
        lru.put("a", "view_a")
        lru.attach("a")
        lru.put("b", "view_b")
        lru.attach("b")

        assertTrue(released.isEmpty())
        lru.detach("a")
        assertEquals(listOf("view_a"), released)
    }

    @Test
    fun a_view_put_as_attached_survives_its_own_insertion() {
        val released = mutableListOf<String>()
        val lru = live_view_lru<String, String>(1) { released.add(it) }
        lru.put("a", "view_a", attach = true)
        lru.put("b", "view_b", attach = true)

        assertTrue(released.isEmpty())
        assertTrue(lru.is_attached("b"))
        lru.detach("a")
        assertEquals(listOf("view_a"), released)
        assertTrue(lru.contains("b"))
    }

    @Test
    fun a_stale_generation_is_rejected_after_rapid_reloads() {
        val gate = body_generation_gate()
        val readings = (1..50).map { gate.next() }

        assertEquals(1, readings.count { gate.accepts(it) })
        assertTrue(gate.accepts(readings.last()))
    }

    @Test
    fun low_memory_releases_only_detached_views() {
        val released = mutableListOf<String>()
        val lru = live_view_lru<String, String>(6) { released.add(it) }
        lru.put("a", "view_a")
        lru.put("b", "view_b")
        lru.attach("b")
        lru.evict_detached()

        assertEquals(listOf("view_a"), released)
        assertTrue(lru.contains("b"))
    }

    @Test
    fun replacing_or_removing_a_view_releases_the_old_one() {
        val released = mutableListOf<String>()
        val lru = live_view_lru<String, String>(6) { released.add(it) }
        lru.put("a", "view_a")
        lru.put("a", "view_a2")
        lru.remove("a")

        assertEquals(listOf("view_a", "view_a2"), released)
        assertFalse(lru.contains("a"))
    }

    @Test
    fun release_all_releases_everything_even_if_one_release_throws() {
        val released = mutableListOf<String>()
        val lru = live_view_lru<String, String>(6) {
            if (it == "view_a") error("boom")
            released.add(it)
        }
        lru.put("a", "view_a")
        lru.put("b", "view_b")
        lru.attach("b")
        lru.release_all()

        assertEquals(listOf("view_b"), released)
        assertEquals(0, lru.size)
        assertFalse(lru.is_attached("b"))
    }

    @Test
    fun viewport_rounding_does_not_count_as_growth() {
        assertEquals(body_reading_verdict.settle, judge_body_reading(true, 180, 178, 24, true))
        assertEquals(body_reading_verdict.probe, judge_body_reading(true, 180, 178, 24, false))
        assertEquals(body_reading_verdict.grow, judge_body_reading(true, 182, 178, 24, true))
    }

    @Test
    fun open_quotes_mark_only_the_requested_details() {
        val html = "<details class=\"aster-quoted-wrapper aster-quote-managed\" data-aster-quote=\"0\"><summary></summary></details>" +
            "<details class=\"aster-quoted-wrapper aster-quote-managed\" data-aster-quote=\"1\"><summary></summary></details>"
        val rendered = with_open_quotes(html, setOf(1))
        assertEquals(1, Regex(" open>").findAll(rendered).count())
        assertEquals(true, rendered.contains("data-aster-quote=\"1\" open>"))
        assertEquals(html, with_open_quotes(html, emptySet()))
    }
}
