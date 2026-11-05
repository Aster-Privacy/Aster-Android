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

class inbox_skeleton_policy_test {

    private fun email(id: String, read: Boolean = false) = Email(
        id = id,
        sender_name = "Sender $id",
        sender_email = "sender$id@astermail.org",
        subject = "Subject $id",
        preview = "Preview $id",
        received_at = 1_700_000_000_000,
        is_read = read,
        is_starred = false,
        has_attachment = false,
    )

    private fun wanted(
        rows_deriving: Boolean = false,
        skeleton_showing: Boolean = false,
        cache_pending: Boolean = false,
        loading_without_rows: Boolean = false,
        waiting_for_rows: Boolean = false,
        showing_error: Boolean = false,
        refreshing_known_empty: Boolean = false,
        emptied_by_action: Boolean = false,
    ) = inbox_skeleton_wanted(
        rows_deriving = rows_deriving,
        skeleton_showing = skeleton_showing,
        cache_pending = cache_pending,
        loading_without_rows = loading_without_rows,
        waiting_for_rows = waiting_for_rows,
        showing_error = showing_error,
        refreshing_known_empty = refreshing_known_empty,
        emptied_by_action = emptied_by_action,
    )

    @Test
    fun loaded_items_still_turning_into_rows_count_as_deriving() {
        assertTrue(inbox_rows_deriving(has_rows = false, folder_items_loaded = true, derivation_pending = true, derived_from_loaded_items = false))
        assertTrue(inbox_rows_deriving(has_rows = false, folder_items_loaded = true, derivation_pending = false, derived_from_loaded_items = false))
        assertTrue(inbox_rows_deriving(has_rows = false, folder_items_loaded = true, derivation_pending = true, derived_from_loaded_items = true))
    }

    @Test
    fun visible_rows_or_missing_items_are_never_deriving() {
        assertFalse(inbox_rows_deriving(has_rows = true, folder_items_loaded = true, derivation_pending = true, derived_from_loaded_items = false))
        assertFalse(inbox_rows_deriving(has_rows = false, folder_items_loaded = false, derivation_pending = true, derived_from_loaded_items = false))
        assertFalse(inbox_rows_deriving(has_rows = false, folder_items_loaded = true, derivation_pending = false, derived_from_loaded_items = true))
    }

    @Test
    fun deriving_rows_from_memory_never_starts_a_skeleton() {
        assertFalse(
            wanted(
                rows_deriving = true,
                skeleton_showing = false,
                loading_without_rows = true,
                waiting_for_rows = true,
            ),
        )
    }

    @Test
    fun a_skeleton_already_on_screen_holds_while_rows_derive() {
        assertTrue(wanted(rows_deriving = true, skeleton_showing = true))
    }

    @Test
    fun a_cold_load_with_nothing_cached_shows_the_skeleton() {
        assertTrue(wanted(loading_without_rows = true))
        assertTrue(wanted(cache_pending = true))
    }

    @Test
    fun an_error_hides_the_waiting_skeleton_but_not_a_cold_load() {
        assertFalse(wanted(waiting_for_rows = true, showing_error = true))
        assertTrue(wanted(waiting_for_rows = true))
        assertTrue(wanted(loading_without_rows = true, showing_error = true))
    }

    @Test
    fun refreshing_a_known_empty_folder_or_an_emptied_folder_shows_no_skeleton() {
        assertFalse(wanted(loading_without_rows = true, refreshing_known_empty = true))
        assertFalse(wanted(cache_pending = true, emptied_by_action = true))
    }

    @Test
    fun nothing_to_wait_for_means_no_skeleton() {
        assertFalse(wanted())
    }

    @Test
    fun a_category_is_hidden_only_after_its_rows_finish_deriving() {
        assertFalse(inbox_hidden_by_category(has_rows = false, folder_items_loaded = true, rows_deriving = true))
        assertTrue(inbox_hidden_by_category(has_rows = false, folder_items_loaded = true, rows_deriving = false))
        assertFalse(inbox_hidden_by_category(has_rows = true, folder_items_loaded = true, rows_deriving = false))
        assertFalse(inbox_hidden_by_category(has_rows = false, folder_items_loaded = false, rows_deriving = false))
    }

    @Test
    fun ids_signature_ignores_order_and_field_changes() {
        val a = listOf(email("a"), email("b"), email("c"))
        val b = listOf(email("c", read = true), email("a"), email("b"))
        assertEquals(email_ids_signature(a), email_ids_signature(b))
    }

    @Test
    fun ids_signature_changes_when_the_id_set_changes() {
        val base = listOf(email("a"), email("b"))
        assertNotEquals(email_ids_signature(base), email_ids_signature(base + email("c")))
        assertNotEquals(email_ids_signature(base), email_ids_signature(listOf(email("a"), email("d"))))
        assertNotEquals(email_ids_signature(base), email_ids_signature(emptyList()))
    }

    @Test
    fun reconciled_rows_match_the_loaded_items_signature() {
        val rows = mutableListOf(email("x"), email("a"))
        val loaded = listOf(email("b"), email("a", read = true), email("c"))
        reconcile_email_rows(rows, loaded)
        assertEquals(email_ids_signature(loaded), email_ids_signature(rows))
    }
}
