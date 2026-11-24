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


package org.astermail.android.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchMatcherTest {

    private fun candidate(
        label: String,
        screen_id: String = label,
        keywords: List<String> = emptyList(),
        is_description: Boolean = false,
        parent: String = "Settings",
    ) = settings_search_candidate(
        screen_id = screen_id,
        label = label,
        parent = parent,
        keywords = keywords,
        is_screen_title = false,
        is_description = is_description,
    )

    private fun labels(query: String, vararg rows: settings_search_candidate): List<String> =
        rank_settings_search(query, prepare_settings_search(rows.toList())).map { it.candidate.label }

    private fun assert_finds(query: String, screen_id: String, label: String? = null, top: Int = 1) {
        val hits = settings_search_fixture.search(query).take(top)
        val found = hits.any { hit ->
            hit.candidate.screen_id == screen_id && (label == null || hit.candidate.label == label)
        }
        assertTrue("$query -> ${hits.map { it.candidate.screen_id + ":" + it.candidate.label }}", found)
    }

    @Test
    fun normalize_folds_case_accents_and_punctuation() {
        assertEquals("theme sombre", normalize_settings_search_text("  Thème-SOMBRE! "))
        assertEquals("dont show", normalize_settings_search_text("Don't show"))
        assertEquals("two factor", normalize_settings_search_text("Two-factor"))
        assertEquals("", normalize_settings_search_text(" ,. "))
    }

    @Test
    fun edit_distance_counts_transpositions_as_one() {
        assertEquals(0, settings_search_edit_distance("theme", "theme"))
        assertEquals(1, settings_search_edit_distance("thmee", "theme"))
        assertEquals(1, settings_search_edit_distance("them", "theme"))
        assertEquals(2, settings_search_edit_distance("tmehe", "theme"))
    }

    @Test
    fun keyword_lists_split_on_commas() {
        assertEquals(listOf("dark mode", "night"), split_settings_search_keywords(" dark mode, night ,, "))
        assertEquals(listOf("a", "b"), split_settings_search_keywords("a、b"))
    }

    @Test
    fun description_names_are_detected() {
        assertTrue(is_settings_description_name("glass_mode_subtitle"))
        assertTrue(is_settings_description_name("app_lock_pin_description"))
        assertFalse(is_settings_description_name("glass_mode"))
    }

    @Test
    fun matching_ignores_case_and_accents() {
        val rows = arrayOf(candidate("Thème"), candidate("Notifications"))
        assertEquals(listOf("Thème"), labels("THEME", *rows))
        assertEquals(listOf("Notifications"), labels("nötificatiöns", *rows))
    }

    @Test
    fun words_match_in_any_order_and_by_prefix() {
        val rows = arrayOf(candidate("Enable undo send"), candidate("Send read receipts"))
        assertEquals(listOf("Enable undo send"), labels("send undo", *rows))
        assertEquals(listOf("Enable undo send"), labels("und sen", *rows))
        assertTrue(labels("undo send receipts", *rows).isEmpty())
    }

    @Test
    fun one_typo_is_tolerated_for_words_of_four_or_more_letters() {
        val rows = arrayOf(candidate("Vacation reply"), candidate("Font"), candidate("Language"))
        assertEquals(listOf("Vacation reply"), labels("vacaton", *rows))
        assertEquals(listOf("Language"), labels("langauge", *rows))
        assertEquals(listOf("Font"), labels("fotn", *rows))
        assertTrue(labels("fnt", *rows).isEmpty())
        assertTrue(labels("vcaatoin", *rows).isEmpty())
    }

    @Test
    fun ranking_puts_exact_title_then_prefix_then_keyword_then_description() {
        val rows = arrayOf(
            candidate("Accent shades", screen_id = "d", is_description = true),
            candidate("Palette tools", screen_id = "c", keywords = listOf("accent")),
            candidate("Accent color picker", screen_id = "b"),
            candidate("Accent", screen_id = "a"),
        )
        assertEquals(
            listOf("Accent", "Accent color picker", "Palette tools", "Accent shades"),
            labels("accent", *rows),
        )
    }

    @Test
    fun exact_match_beats_typo_match() {
        val rows = arrayOf(candidate("Fonts", screen_id = "x"), candidate("Font", screen_id = "y"))
        assertEquals(listOf("Font", "Fonts"), labels("font", *rows))
    }

    @Test
    fun parent_screen_name_narrows_results() {
        val rows = arrayOf(
            candidate("Sound", screen_id = "notifications", parent = "Notifications"),
            candidate("Sound", screen_id = "accessibility", parent = "Accessibility"),
        )
        val hits = rank_settings_search("notifications sound", prepare_settings_search(rows.toList()))
        assertEquals(listOf("notifications"), hits.map { it.candidate.screen_id })
    }

    @Test
    fun results_are_capped_per_screen() {
        val rows = (1..6).map { candidate("Theme $it", screen_id = "appearance") }
        assertEquals(3, rank_settings_search("theme", prepare_settings_search(rows)).size)
    }

    @Test
    fun blank_queries_return_nothing() {
        assertTrue(settings_search_fixture.search("").isEmpty())
        assertTrue(settings_search_fixture.search("  -  ").isEmpty())
    }

    @Test
    fun sender_logo_synonyms_find_sender_pictures() {
        val label = settings_search_fixture.text(org.astermail.android.R.string.show_sender_pictures)
        listOf("company", "logo", "logos", "favicon", "brand", "company logo").forEach {
            assert_finds(it, "behavior", label, top = 3)
        }
    }

    @Test
    fun theme_synonyms_find_appearance() {
        listOf("dark", "dark mode", "night", "night mode", "theme").forEach { assert_finds(it, "appearance") }
    }

    @Test
    fun two_factor_synonyms_find_two_factor() {
        listOf("2fa", "two factor", "otp", "authenticator", "mfa").forEach { assert_finds(it, "two_factor") }
    }

    @Test
    fun vacation_synonyms_find_vacation_reply() {
        listOf("vacation", "auto reply", "autoreply", "out of office", "vacaton").forEach {
            assert_finds(it, "vacation_reply", top = 3)
        }
    }

    @Test
    fun filter_synonyms_find_mail_rules() {
        listOf("filters", "rules", "email filters").forEach { assert_finds(it, "mail_rules", top = 3) }
    }

    @Test
    fun common_settings_are_found_by_name() {
        assert_finds("undo send", "behavior", settings_search_fixture.text(org.astermail.android.R.string.undo_send))
        assert_finds("unsend", "behavior")
        assert_finds("block", "blocked", top = 5)
        assert_finds("blocklist", "blocked")
        assert_finds("font", "appearance")
        assert_finds("language", "language")
        assert_finds("notifications", "notifications")
        assert_finds("notifcations", "notifications")
    }

    @Test
    fun app_lock_synonyms_find_security() {
        listOf("pin", "biometric", "lock", "app lock", "fingerprint").forEach { assert_finds(it, "security", top = 3) }
    }

    @Test
    fun stop_words_are_ignored_when_other_words_match() {
        val rows = arrayOf(candidate("Confirm before exiting", keywords = listOf("double tap", "exit")), candidate("Theme"))
        assertEquals(listOf("Confirm before exiting"), labels("how to turn off double tap", *rows))
        assertEquals(listOf("Theme"), labels("change my theme", *rows))
        assertTrue(labels("turn off", *rows).isEmpty())
    }

    @Test
    fun plurals_and_singulars_match_exactly() {
        assertEquals("category", settings_search_stem("categories"))
        assertEquals("alias", settings_search_stem("aliases"))
        assertEquals("alias", settings_search_stem("alias"))
        assertEquals("address", settings_search_stem("addresses"))
        assertEquals("device", settings_search_stem("devices"))
        val rows = arrayOf(candidate("Inbox categories", screen_id = "a"), candidate("Category", screen_id = "b"))
        assertEquals(setOf("Inbox categories", "Category"), labels("category", *rows).toSet())
        assertEquals(listOf("Category", "Inbox categories"), labels("category", *rows))
    }

    @Test
    fun joined_words_match_spaced_labels() {
        val rows = arrayOf(candidate("Sign out everywhere"), candidate("Theme"))
        assertEquals(listOf("Sign out everywhere"), labels("signout", *rows))
    }

    @Test
    fun short_tokens_with_digits_need_an_exact_or_prefix_match() {
        val rows = arrayOf(candidate("Two-factor", keywords = listOf("2fa", "totp")), candidate("24 hour clock"))
        assertEquals(listOf("Two-factor"), labels("2fa", *rows))
        assertTrue(labels("3fa", *rows).isEmpty())
    }

    @Test
    fun multi_word_queries_combine_label_and_keywords() {
        val rows = arrayOf(candidate("Sound", keywords = listOf("ringtone")), candidate("Ringtone picker", screen_id = "x"))
        assertEquals(listOf("Sound"), labels("sound ringtone", *rows))
    }

    @Test
    fun exit_toggle_is_found_by_its_synonyms() {
        val label = settings_search_fixture.text(org.astermail.android.R.string.confirm_back_exit)
        listOf(
            "exit", "double tap", "press back twice", "close app", "quit", "turn off double tap exit",
            "disable double back",
        ).forEach { assert_finds(it, "behavior", label, top = 3) }
    }

    @Test
    fun synonyms_reach_their_settings_on_the_real_index() {
        assert_finds("log out everywhere", "sessions", top = 3)
        assert_finds("cancel subscription", "billing", top = 3)
        assert_finds("reduce animations", "accessibility", top = 3)
        assert_finds("quick actions", "behavior", top = 3)
        assert_finds("ringtone", "notifications", top = 3)
        assert_finds("how do i change my password", "password", top = 3)
        assert_finds("turn on 2fa", "two_factor", top = 3)
        assert_finds("categories", "behavior", top = 5)
        assert_finds("smtp", "smtp_tokens", top = 3)
        assert_finds("hide my email", "aliases", top = 3)
        assert_finds("dkim", "domains", top = 3)
    }

    @Test
    fun word_order_and_accents_work_on_the_real_index() {
        assert_finds("send undo", "behavior")
        assert_finds("REPLY VACATÍON", "vacation_reply")
    }
}
