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

import org.astermail.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionSelectionTest {
    private val visible = setOf("a", "b", "c")

    @Test
    fun deselect_clears_the_selection_and_ends_selection_mode_on_both_tabs() {
        for (unsubscribed_tab in listOf(true, false)) {
            val action = subscription_bar_actions(unsubscribed_tab).first()
            assertEquals(subscription_bar_action.deselect, action)
            val result = reduce_subscription_selection(setOf("a"), visible, action)
            assertTrue(result.selected.isEmpty())
            assertFalse(is_subscription_selection_active(result.selected))
            assertTrue(result.delete_request.isEmpty())
            assertTrue(result.reactivate_request.isEmpty())
            assertTrue(result.unsubscribe_request.isEmpty())
        }
    }

    @Test
    fun the_first_action_is_labeled_deselect_not_clear() {
        assertEquals(R.string.deselect_all, subscription_bar_action.deselect.label_res)
        subscription_bar_actions(true).forEach { assertTrue(it.label_res != R.string.clear) }
        subscription_bar_actions(false).forEach { assertTrue(it.label_res != R.string.clear) }
    }

    @Test
    fun both_tabs_offer_delete_next_to_their_own_action() {
        assertEquals(
            listOf(
                subscription_bar_action.deselect,
                subscription_bar_action.select_all,
                subscription_bar_action.reactivate,
                subscription_bar_action.delete,
            ),
            subscription_bar_actions(true),
        )
        assertEquals(
            listOf(
                subscription_bar_action.deselect,
                subscription_bar_action.select_all,
                subscription_bar_action.unsubscribe,
                subscription_bar_action.delete,
            ),
            subscription_bar_actions(false),
        )
    }

    @Test
    fun delete_asks_for_confirmation_of_only_visible_selected_entries() {
        val result = reduce_subscription_selection(setOf("a", "gone"), visible, subscription_bar_action.delete)
        assertEquals(listOf("a"), result.delete_request)
        assertEquals(setOf("a"), result.selected)
    }

    @Test
    fun reactivate_runs_on_the_selection_and_ends_selection_mode() {
        val result = reduce_subscription_selection(setOf("a", "b"), visible, subscription_bar_action.reactivate)
        assertEquals(setOf("a", "b"), result.reactivate_request.toSet())
        assertTrue(result.selected.isEmpty())
    }

    @Test
    fun select_all_selects_every_visible_entry_and_toggle_flips_one() {
        assertEquals(visible, reduce_subscription_selection(setOf("a"), visible, subscription_bar_action.select_all).selected)
        assertEquals(setOf("a", "b"), toggle_subscription_selection(setOf("a"), "b"))
        assertEquals(emptySet<String>(), toggle_subscription_selection(setOf("a"), "a"))
    }
}
