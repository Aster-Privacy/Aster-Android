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

package org.astermail.android.api.preferences

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SidebarLayoutTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val default_order = listOf("mail", "folders", "labels", "aliases")

    @Test
    fun sanitize_order_appends_missing_sections_in_default_order() {
        assertEquals(default_order, sanitize_sidebar_section_order(null))
        assertEquals(default_order, sanitize_sidebar_section_order(emptyList()))
        assertEquals(
            listOf("aliases", "mail", "folders", "labels"),
            sanitize_sidebar_section_order(listOf("aliases")),
        )
    }

    @Test
    fun sanitize_order_drops_invalid_and_duplicate_ids_and_keeps_unknown_ones() {
        assertEquals(
            listOf("calendar", "aliases", "mail", "folders", "labels"),
            sanitize_sidebar_section_order(
                listOf("calendar", "aliases", "aliases", "<b>", "", "Mail", "1abc", "a".repeat(33)),
            ),
        )
    }

    @Test
    fun sanitize_lists_are_capped() {
        val many = (0 until 100).map { "item_$it" }

        assertEquals(MAX_SIDEBAR_LAYOUT_IDS, sanitize_sidebar_hidden_items(many).size)
        val order = sanitize_sidebar_section_order(many)
        assertEquals(MAX_SIDEBAR_LAYOUT_IDS + default_order.size, order.size)
        assertEquals(default_order, order.takeLast(default_order.size))
    }

    @Test
    fun resolve_order_renders_only_known_sections() {
        assertEquals(
            listOf("labels", "mail", "folders", "aliases"),
            resolve_sidebar_section_order(listOf("calendar", "labels", "notes", "mail")),
        )
    }

    @Test
    fun inbox_and_mail_can_never_be_hidden() {
        assertFalse(is_sidebar_item_hideable("inbox"))
        assertFalse(is_sidebar_item_hideable("mail"))
        assertFalse(is_sidebar_item_hidden(listOf("inbox", "mail"), "inbox"))
        assertFalse(is_sidebar_item_hidden(listOf("inbox", "mail"), "mail"))
        assertEquals(emptyList<String>(), set_sidebar_item_hidden(emptyList(), "inbox", true))
        assertEquals(emptyList<String>(), set_sidebar_item_hidden(emptyList(), "mail", true))
    }

    @Test
    fun hiding_and_showing_keeps_unknown_ids() {
        val hidden = set_sidebar_item_hidden(listOf("future_item"), "trash", true)
        assertEquals(listOf("future_item", "trash"), hidden)
        assertTrue(is_sidebar_item_hidden(hidden, "trash"))

        assertEquals(listOf("future_item", "trash"), set_sidebar_item_hidden(hidden, "trash", true))
        assertEquals(listOf("future_item"), set_sidebar_item_hidden(hidden, "trash", false))
        assertEquals(listOf("future_item"), set_sidebar_item_hidden(listOf("future_item"), "spam", false))
    }

    @Test
    fun hiding_at_the_cap_evicts_the_oldest_unknown_id() {
        val full = (0 until MAX_SIDEBAR_LAYOUT_IDS).map { "item_$it" }
        val next = set_sidebar_item_hidden(full, "trash", true)

        assertEquals(MAX_SIDEBAR_LAYOUT_IDS, next.size)
        assertEquals(full.drop(1) + "trash", next)
    }

    @Test
    fun hiding_at_the_cap_keeps_known_hidden_items() {
        val full = listOf("spam") + (0 until MAX_SIDEBAR_LAYOUT_IDS - 1).map { "item_$it" }
        val next = set_sidebar_item_hidden(full, "trash", true)

        assertEquals(MAX_SIDEBAR_LAYOUT_IDS, next.size)
        assertEquals(listOf("spam") + full.drop(2) + "trash", next)
    }

    @Test
    fun hiding_an_already_hidden_item_at_the_cap_moves_it_to_the_end() {
        val full = listOf("spam") + (0 until MAX_SIDEBAR_LAYOUT_IDS - 1).map { "item_$it" }

        assertEquals(full.drop(1) + "spam", set_sidebar_item_hidden(full, "spam", true))
    }

    @Test
    fun reset_order_restores_known_sections_and_keeps_unknown_ids_in_place() {
        assertEquals(default_order, reset_sidebar_section_order(listOf("aliases", "labels")))
        assertEquals(default_order, reset_sidebar_section_order(null))
        assertEquals(
            listOf("mail", "calendar", "folders", "labels", "aliases"),
            reset_sidebar_section_order(listOf("aliases", "calendar", "labels", "mail", "folders")),
        )
    }

    @Test
    fun reset_hidden_items_drops_known_ids_and_keeps_unknown_ids() {
        assertEquals(
            listOf("calendar_tasks"),
            reset_sidebar_hidden_items(listOf("spam", "calendar_tasks", "folders", "Bad Id")),
        )
        assertEquals(emptyList<String>(), reset_sidebar_hidden_items(null))
    }

    @Test
    fun move_swaps_neighbors_and_stops_at_the_ends() {
        assertEquals(
            listOf("mail", "labels", "folders", "aliases"),
            move_sidebar_section(default_order, "labels", -1),
        )
        assertEquals(
            listOf("folders", "mail", "labels", "aliases"),
            move_sidebar_section(default_order, "mail", 1),
        )
        assertEquals(default_order, move_sidebar_section(default_order, "mail", -1))
        assertEquals(default_order, move_sidebar_section(default_order, "aliases", 1))
        assertEquals(default_order, move_sidebar_section(default_order, "folders", 2))
        assertEquals(default_order, move_sidebar_section(default_order, "calendar", 1))
    }

    @Test
    fun move_skips_unknown_ids_and_keeps_them_in_place() {
        val order = listOf("mail", "calendar", "folders", "labels", "aliases")

        assertEquals(
            listOf("folders", "calendar", "mail", "labels", "aliases"),
            move_sidebar_section(order, "folders", -1),
        )
        val leading_unknown = listOf("calendar") + default_order
        assertEquals(leading_unknown, move_sidebar_section(leading_unknown, "mail", -1))
    }

    @Test
    fun can_move_matches_the_rendered_order() {
        val order = listOf("calendar", "mail", "folders", "labels", "aliases", "notes")

        assertFalse(can_move_sidebar_section(order, "mail", -1))
        assertTrue(can_move_sidebar_section(order, "mail", 1))
        assertFalse(can_move_sidebar_section(order, "aliases", 1))
        assertTrue(can_move_sidebar_section(order, "aliases", -1))
        assertFalse(can_move_sidebar_section(order, "calendar", 1))
        assertFalse(can_move_sidebar_section(order, "folders", 0))
        assertEquals(order, move_sidebar_section(order, "mail", -1))
        assertEquals(order, move_sidebar_section(order, "aliases", 1))
    }

    @Test
    fun default_layout_ignores_unknown_ids() {
        assertTrue(is_default_sidebar_layout(null, null))
        assertTrue(is_default_sidebar_layout(default_order, emptyList()))
        assertTrue(is_default_sidebar_layout(listOf("mail", "calendar"), listOf("future_item", "inbox")))
        assertFalse(is_default_sidebar_layout(listOf("aliases"), emptyList()))
        assertFalse(is_default_sidebar_layout(default_order, listOf("sent")))
    }

    @Test
    fun visible_sections_filter_hidden_sections() {
        val prefs = UserPreferences(
            sidebar_section_order = listOf("aliases", "mail", "labels", "folders"),
            sidebar_hidden_items = listOf("labels", "mail", "trash"),
        )

        assertEquals(listOf("aliases", "mail", "folders"), visible_sidebar_sections(prefs))
        assertEquals(default_order, visible_sidebar_sections(null))
    }

    @Test
    fun merge_defaults_when_the_keys_are_missing() {
        val merged = merge_decrypted_preferences(json, """{"show_aster_branding":true}""", null)

        assertEquals(default_order, merged.sidebar_section_order)
        assertEquals(emptyList<String>(), merged.sidebar_hidden_items)
    }

    @Test
    fun merge_reads_a_layout_saved_by_another_client() {
        val merged = merge_decrypted_preferences(
            json,
            """{"sidebar_section_order":["labels","mail"],"sidebar_hidden_items":["trash","aliases"]}""",
            null,
        )

        assertEquals(listOf("labels", "mail", "folders", "aliases"), merged.sidebar_section_order)
        assertEquals(listOf("trash", "aliases"), merged.sidebar_hidden_items)
    }

    @Test
    fun merge_keeps_the_cached_layout_when_a_stale_client_dropped_the_keys() {
        val previous = UserPreferences(
            sidebar_section_order = listOf("aliases", "mail", "folders", "labels"),
            sidebar_hidden_items = listOf("spam"),
        )
        val merged = merge_decrypted_preferences(json, """{"show_aster_branding":true}""", previous)

        assertEquals(previous.sidebar_section_order, merged.sidebar_section_order)
        assertEquals(previous.sidebar_hidden_items, merged.sidebar_hidden_items)
    }

    @Test
    fun merge_survives_malformed_values() {
        listOf(
            """{"sidebar_section_order":"aliases","sidebar_hidden_items":{"0":"trash"}}""",
            """{"sidebar_section_order":null,"sidebar_hidden_items":null}""",
            """{"sidebar_section_order":7,"sidebar_hidden_items":true}""",
        ).forEach { blob ->
            val merged = merge_decrypted_preferences(json, blob, null)

            assertEquals(blob, default_order, merged.sidebar_section_order)
            assertEquals(blob, emptyList<String>(), merged.sidebar_hidden_items)
        }
    }

    @Test
    fun merge_drops_non_string_entries_without_failing() {
        val merged = merge_decrypted_preferences(
            json,
            """{"sidebar_section_order":["calendar","aliases","aliases",7,"<b>",null,{"a":1},["x"]],""" +
                """"sidebar_hidden_items":["trash",false,1.5,"trash","future_item"]}""",
            null,
        )

        assertEquals(listOf("calendar", "aliases", "mail", "folders", "labels"), merged.sidebar_section_order)
        assertEquals(listOf("trash", "future_item"), merged.sidebar_hidden_items)
    }

    @Test
    fun merge_caps_oversized_lists() {
        val many = (0 until 100).joinToString(",") { "\"item_$it\"" }
        val merged = merge_decrypted_preferences(
            json,
            """{"sidebar_section_order":[$many],"sidebar_hidden_items":[$many]}""",
            null,
        )

        assertEquals(MAX_SIDEBAR_LAYOUT_IDS, merged.sidebar_hidden_items.size)
        assertEquals(MAX_SIDEBAR_LAYOUT_IDS + default_order.size, merged.sidebar_section_order.size)
        assertEquals(default_order, merged.sidebar_section_order.takeLast(default_order.size))
    }

    @Test
    fun encode_writes_the_layout_and_keeps_unknown_keys() {
        val prefs = UserPreferences(
            sidebar_section_order = listOf("calendar", "aliases", "mail", "folders", "labels"),
            sidebar_hidden_items = listOf("future_item", "spam"),
        )
        val encoded = encode_preferences_preserving_unknown(json, prefs, """{"unknown_key":1}""")
        val obj = json.parseToJsonElement(encoded).jsonObject

        assertEquals(
            listOf("calendar", "aliases", "mail", "folders", "labels"),
            obj.getValue(SIDEBAR_SECTION_ORDER_KEY).jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals(
            listOf("future_item", "spam"),
            obj.getValue(SIDEBAR_HIDDEN_ITEMS_KEY).jsonArray.map { it.jsonPrimitive.content },
        )
        assertEquals("1", obj.getValue("unknown_key").jsonPrimitive.content)
    }

    @Test
    fun a_round_trip_keeps_the_layout() {
        val prefs = UserPreferences(
            sidebar_section_order = listOf("labels", "aliases", "mail", "folders"),
            sidebar_hidden_items = listOf("aliases", "snoozed"),
        )
        val encoded = encode_preferences_preserving_unknown(json, prefs, null)
        val merged = merge_decrypted_preferences(json, encoded, null)

        assertEquals(prefs.sidebar_section_order, merged.sidebar_section_order)
        assertEquals(prefs.sidebar_hidden_items, merged.sidebar_hidden_items)
    }
}
