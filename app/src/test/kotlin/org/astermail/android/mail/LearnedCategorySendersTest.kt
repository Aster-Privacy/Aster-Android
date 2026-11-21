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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.preferences.LearnedCategorySender
import org.astermail.android.api.preferences.MAX_LEARNED_CATEGORY_SENDERS
import org.astermail.android.api.preferences.UserPreferences
import org.astermail.android.api.preferences.encode_preferences_preserving_unknown
import org.astermail.android.api.preferences.merge_decrypted_preferences
import org.astermail.android.api.preferences.merge_learned_category_senders
import org.astermail.android.api.preferences.normalize_learned_sender
import org.astermail.android.api.preferences.preferred_learned_entry
import org.astermail.android.api.preferences.rebase_learned_category_senders
import org.astermail.android.api.preferences.rebase_preferences_changes
import org.astermail.android.api.preferences.sanitize_learned_category_senders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedCategorySendersTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private fun entry(sender: String, category: String, at: Long) = LearnedCategorySender(sender, category, at)

    @Test
    fun sender_is_trimmed_and_lowercased() {
        assertEquals("alice@example.com", normalize_learned_sender("  Alice@Example.COM "))
    }

    @Test
    fun malformed_senders_are_rejected() {
        assertNull(normalize_learned_sender(""))
        assertNull(normalize_learned_sender("no-at-sign"))
        assertNull(normalize_learned_sender("a@b@c.com"))
        assertNull(normalize_learned_sender("a b@c.com"))
        assertNull(normalize_learned_sender("@example.com"))
        assertNull(normalize_learned_sender("alice@"))
        assertNull(normalize_learned_sender("a".repeat(316) + "@x.com"))
    }

    @Test
    fun sanitize_keeps_newest_per_sender_and_sorts_newest_first() {
        val result = sanitize_learned_category_senders(
            listOf(
                entry("a@x.com", "social", 10),
                entry("B@x.com", "updates", 30),
                entry("A@X.com", "promotions", 20),
            ),
        )
        assertEquals(
            listOf(entry("b@x.com", "updates", 30), entry("a@x.com", "promotions", 20)),
            result,
        )
    }

    @Test
    fun sanitize_drops_invalid_entries_and_keeps_custom_ids() {
        val result = sanitize_learned_category_senders(
            listOf(
                entry("a@x.com", "custom:politics", 5),
                entry("b@x.com", "not_a_category", 6),
                entry("c@x.com", "custom:", 7),
                entry("bad", "social", 8),
                entry("d@x.com", "social", -1),
            ),
        )
        assertEquals(listOf(entry("a@x.com", "custom:politics", 5)), result)
    }

    @Test
    fun sanitize_caps_the_list() {
        val many = (1..MAX_LEARNED_CATEGORY_SENDERS + 20).map { entry("user$it@x.com", "social", it.toLong()) }
        val result = sanitize_learned_category_senders(many)
        assertEquals(MAX_LEARNED_CATEGORY_SENDERS, result.size)
        assertEquals("user${MAX_LEARNED_CATEGORY_SENDERS + 20}@x.com", result.first().sender)
        assertEquals("user21@x.com", result.last().sender)
    }

    @Test
    fun merge_keeps_the_newest_entry_per_sender() {
        val merged = merge_learned_category_senders(
            listOf(entry("a@x.com", "social", 10), entry("b@x.com", "updates", 50)),
            listOf(entry("a@x.com", "promotions", 40)),
        )
        assertEquals(
            listOf(entry("b@x.com", "updates", 50), entry("a@x.com", "promotions", 40)),
            merged,
        )
    }

    @Test
    fun rebase_keeps_server_additions_and_local_changes() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val server = listOf(entry("a@x.com", "social", 10), entry("s@x.com", "updates", 20))
        val local = listOf(entry("a@x.com", "finance", 30))
        val result = rebase_learned_category_senders(server, baseline, local)
        assertEquals(
            listOf(entry("a@x.com", "finance", 30), entry("s@x.com", "updates", 20)),
            result,
        )
    }

    @Test
    fun rebase_applies_a_local_deletion() {
        val baseline = listOf(entry("a@x.com", "social", 10), entry("b@x.com", "updates", 11))
        val server = baseline
        val local = listOf(entry("b@x.com", "updates", 11))
        assertEquals(local, rebase_learned_category_senders(server, baseline, local))
    }

    @Test
    fun rebase_clears_the_list_when_forgotten_locally() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        assertEquals(emptyList<LearnedCategorySender>(), rebase_learned_category_senders(baseline, baseline, emptyList()))
    }

    @Test
    fun rebase_prefers_the_newer_entry_on_conflict() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val server = listOf(entry("a@x.com", "updates", 50))
        val local = listOf(entry("a@x.com", "finance", 30))
        assertEquals(server, rebase_learned_category_senders(server, baseline, local))
    }

    @Test
    fun a_tie_keeps_the_smaller_category_in_any_order() {
        val first = entry("a@x.com", "updates", 10)
        val second = entry("a@x.com", "social", 10)
        val expected = listOf(entry("a@x.com", "social", 10))
        assertEquals(expected, sanitize_learned_category_senders(listOf(first, second)))
        assertEquals(expected, sanitize_learned_category_senders(listOf(second, first)))
        assertEquals(expected, merge_learned_category_senders(listOf(first), listOf(second)))
        assertEquals(expected, merge_learned_category_senders(listOf(second), listOf(first)))
        assertEquals(second, preferred_learned_entry(first, second))
        assertEquals(second, preferred_learned_entry(second, first))
    }

    @Test
    fun a_tie_in_rebase_keeps_the_smaller_category_on_both_sides() {
        val baseline = listOf(entry("a@x.com", "finance", 5))
        val server = listOf(entry("a@x.com", "updates", 10))
        val local = listOf(entry("a@x.com", "social", 10))
        val expected = listOf(entry("a@x.com", "social", 10))
        assertEquals(expected, rebase_learned_category_senders(server, baseline, local))
        assertEquals(expected, rebase_learned_category_senders(local, baseline, server))
        assertEquals(expected, rebase_learned_category_senders(server, emptyList(), local))
        assertEquals(expected, rebase_learned_category_senders(local, emptyList(), server))
    }

    @Test
    fun rebase_with_no_changes_keeps_everything() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        assertEquals(baseline, rebase_learned_category_senders(baseline, baseline, baseline))
    }

    @Test
    fun rebase_takes_a_server_only_change() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val server = listOf(entry("a@x.com", "updates", 5))
        assertEquals(server, rebase_learned_category_senders(server, baseline, baseline))
    }

    @Test
    fun rebase_takes_a_local_only_change_even_when_older() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val local = listOf(entry("a@x.com", "updates", 5))
        assertEquals(local, rebase_learned_category_senders(baseline, baseline, local))
    }

    @Test
    fun rebase_applies_a_server_deletion() {
        val baseline = listOf(entry("a@x.com", "social", 10), entry("b@x.com", "updates", 11))
        val server = listOf(entry("b@x.com", "updates", 11))
        assertEquals(server, rebase_learned_category_senders(server, baseline, baseline))
    }

    @Test
    fun rebase_drops_an_entry_deleted_on_both_sides() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        assertEquals(
            emptyList<LearnedCategorySender>(),
            rebase_learned_category_senders(emptyList(), baseline, emptyList()),
        )
    }

    @Test
    fun rebase_keeps_a_change_over_a_deletion_on_the_other_side() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val changed = listOf(entry("a@x.com", "travel", 20))
        assertEquals(changed, rebase_learned_category_senders(changed, baseline, emptyList()))
        assertEquals(changed, rebase_learned_category_senders(emptyList(), baseline, changed))
    }

    @Test
    fun rebase_keeps_additions_from_both_sides() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val server = baseline + entry("s@x.com", "updates", 20)
        val local = baseline + entry("l@x.com", "travel", 30)
        assertEquals(
            listOf(entry("l@x.com", "travel", 30), entry("s@x.com", "updates", 20), entry("a@x.com", "social", 10)),
            rebase_learned_category_senders(server, baseline, local),
        )
    }

    @Test
    fun rebase_picks_the_newer_entry_when_both_sides_add_the_same_sender() {
        val server = listOf(entry("n@x.com", "updates", 20))
        val local = listOf(entry("n@x.com", "travel", 30))
        assertEquals(local, rebase_learned_category_senders(server, emptyList(), local))
        assertEquals(local, rebase_learned_category_senders(local, emptyList(), server))
    }

    @Test
    fun rebase_prefers_the_newer_local_entry_on_conflict() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val server = listOf(entry("a@x.com", "updates", 30))
        val local = listOf(entry("a@x.com", "finance", 50))
        assertEquals(local, rebase_learned_category_senders(server, baseline, local))
    }

    @Test
    fun forget_does_not_come_back_from_an_unchanged_server() {
        val baseline = listOf(entry("a@x.com", "social", 10), entry("b@x.com", "updates", 20))
        assertEquals(
            emptyList<LearnedCategorySender>(),
            rebase_learned_category_senders(baseline, baseline, emptyList()),
        )
        val prefs_baseline = UserPreferences(learned_category_senders = baseline)
        val forgotten = prefs_baseline.copy(learned_category_senders = emptyList())
        val result = rebase_preferences_changes(json, prefs_baseline, prefs_baseline, forgotten)
        assertEquals(emptyList<LearnedCategorySender>(), result.learned_category_senders)
    }

    @Test
    fun forget_keeps_senders_the_server_added_afterward() {
        val baseline = listOf(entry("a@x.com", "social", 10))
        val server = baseline + entry("s@x.com", "updates", 20)
        assertEquals(
            listOf(entry("s@x.com", "updates", 20)),
            rebase_learned_category_senders(server, baseline, emptyList()),
        )
    }

    @Test
    fun an_unknown_baseline_takes_the_union() {
        val server = listOf(entry("s@x.com", "updates", 20), entry("a@x.com", "social", 10))
        val local = listOf(entry("l@x.com", "travel", 30), entry("a@x.com", "finance", 40))
        assertEquals(
            listOf(entry("a@x.com", "finance", 40), entry("l@x.com", "travel", 30), entry("s@x.com", "updates", 20)),
            rebase_learned_category_senders(server, null, local),
        )
    }

    @Test
    fun preference_rebase_with_an_unknown_baseline_takes_the_union() {
        val server = UserPreferences(learned_category_senders = listOf(entry("s@x.com", "updates", 20)))
        val updated = UserPreferences(learned_category_senders = listOf(entry("l@x.com", "travel", 30)))
        val result = rebase_preferences_changes(json, server, null, updated)
        assertEquals(
            listOf(entry("l@x.com", "travel", 30), entry("s@x.com", "updates", 20)),
            result.learned_category_senders,
        )
    }

    @Test
    fun malformed_json_entries_are_dropped_when_decoding() {
        val payload = """
            {"learn_from_category_moves": false,
             "learned_category_senders": [
               {"sender": "Alice@Example.com", "category": "social", "updated_at": 100},
               {"sender": "bob@example.com", "category": "social", "updated_at": "100"},
               {"sender": 5, "category": "social", "updated_at": 100},
               "junk",
               {"sender": "carol@example.com", "category": "custom:club", "updated_at": 50.7},
               {"sender": "dave@example.com", "category": "nope", "updated_at": 10}
             ]}
        """.trimIndent()
        val prefs = merge_decrypted_preferences(json, payload, null)
        assertFalse(prefs.learn_from_category_moves)
        assertEquals(
            listOf(entry("alice@example.com", "social", 100), entry("carol@example.com", "custom:club", 50)),
            prefs.learned_category_senders,
        )
    }

    @Test
    fun missing_keys_use_defaults() {
        val prefs = merge_decrypted_preferences(json, "{}", null)
        assertTrue(prefs.learn_from_category_moves)
        assertTrue(prefs.learned_category_senders.isEmpty())
    }

    @Test
    fun wrong_type_keeps_previous_value() {
        val previous = UserPreferences(learned_category_senders = listOf(entry("a@x.com", "social", 1)))
        val prefs = merge_decrypted_preferences(json, """{"learned_category_senders": "oops"}""", previous)
        assertEquals(previous.learned_category_senders, prefs.learned_category_senders)
    }

    @Test
    fun encoding_preserves_unknown_keys() {
        val original = """{"future_setting": {"x": 1}, "learned_category_senders": []}"""
        val prefs = UserPreferences(learned_category_senders = listOf(entry("a@x.com", "travel", 9)))
        val encoded = json.parseToJsonElement(encode_preferences_preserving_unknown(json, prefs, original)).jsonObject
        assertTrue(encoded.containsKey("future_setting"))
        val stored = encoded["learned_category_senders"]!!.jsonArray
        assertEquals(1, stored.size)
        val first = stored[0].jsonObject
        assertEquals("a@x.com", first["sender"]!!.jsonPrimitive.content)
        assertEquals("travel", first["category"]!!.jsonPrimitive.content)
        assertEquals("9", first["updated_at"]!!.jsonPrimitive.content)
        assertEquals("true", encoded["learn_from_category_moves"]!!.jsonPrimitive.content)
    }

    @Test
    fun preference_rebase_merges_learned_senders_per_entry() {
        val baseline = UserPreferences(learned_category_senders = listOf(entry("a@x.com", "social", 10)))
        val server = baseline.copy(
            learned_category_senders = baseline.learned_category_senders + entry("s@x.com", "updates", 20),
        )
        val updated = baseline.copy(learned_category_senders = listOf(entry("a@x.com", "finance", 30)))
        val result = rebase_preferences_changes(json, server, baseline, updated)
        assertEquals(
            listOf(entry("a@x.com", "finance", 30), entry("s@x.com", "updates", 20)),
            result.learned_category_senders,
        )
    }
}
