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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectionToolbarTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val dispatched = mutableListOf<String>()
    private val direct_calls = mutableListOf<String>()

    @Composable
    private fun bar_harness(
        custom_actions: List<String>,
        current_folder: String = "inbox",
    ) {
        var show_overflow by remember { mutableStateOf(false) }
        select_mode_bottom_bar(
            selected_count = 2,
            custom_actions = custom_actions,
            on_action = { dispatched.add(it) },
            on_more = { show_overflow = true },
            current_folder = current_folder,
        )
        if (show_overflow) {
            selection_overflow_sheet(
                on_close = { show_overflow = false },
                on_action = { id ->
                    show_overflow = false
                    dispatched.add(id)
                },
                on_customize = {
                    show_overflow = false
                    direct_calls.add("customize")
                },
                current_folder = current_folder,
            )
        }
    }

    private fun set_harness(actions: List<String>, folder: String = "inbox") {
        dispatched.clear()
        direct_calls.clear()
        compose_rule.setContent { AsterTheme { bar_harness(actions, folder) } }
        compose_rule.waitForIdle()
    }

    private val default_actions = parse_selection_toolbar_actions(null)

    @Test
    fun default_bar_shows_delete_folder_label_read_and_more() {
        set_harness(default_actions)

        compose_rule.onNodeWithTag("sel_action_trash").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_folder").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_label").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_read").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_more").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_archive").assertDoesNotExist()
        compose_rule.onNodeWithTag("sel_action_spam").assertDoesNotExist()
    }

    @Test
    fun custom_slots_render_instead_of_defaults() {
        set_harness(listOf("star", "archive", "snooze", "spam"))

        compose_rule.onNodeWithTag("sel_action_star").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_archive").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_snooze").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_spam").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_trash").assertDoesNotExist()
        compose_rule.onNodeWithTag("sel_action_more").assertIsDisplayed()
    }

    @Test
    fun bar_actions_dispatch_their_ids() {
        set_harness(default_actions)

        compose_rule.onNodeWithTag("sel_action_trash").performClick()
        compose_rule.onNodeWithTag("sel_action_folder").performClick()
        compose_rule.onNodeWithTag("sel_action_read").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("trash", "folder", "read"), dispatched)
        assertTrue(direct_calls.isEmpty())
    }

    @Test
    fun more_opens_overflow_with_full_catalog_in_order() {
        set_harness(default_actions)

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()

        listOf("star", "read", "unread", "archive", "snooze", "folder", "label", "trash", "spam", "customize")
            .forEach { compose_rule.onNodeWithTag("sel_overflow_$it").assertIsDisplayed() }
    }

    @Test
    fun overflow_action_dispatches_and_closes() {
        set_harness(default_actions)

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("sel_overflow_unread").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("unread"), dispatched)
        compose_rule.onNodeWithTag("sel_overflow_star").assertDoesNotExist()
    }

    @Test
    fun overflow_customize_row_invokes_customize() {
        set_harness(default_actions)

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("sel_overflow_customize").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("customize"), direct_calls)
    }

    @Test
    fun trash_folder_keeps_contextual_bar_and_offers_more() {
        set_harness(default_actions, folder = "trash")

        compose_rule.onNodeWithTag("mark_read").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_more").assertIsDisplayed()
        compose_rule.onNodeWithTag("sel_action_trash").assertDoesNotExist()
    }

    @Test
    fun trash_overflow_offers_restore_and_hides_contradictory_rows() {
        set_harness(default_actions, folder = "trash")

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()

        listOf("star", "read", "unread", "restore", "folder", "label", "delete_permanent", "customize")
            .forEach { compose_rule.onNodeWithTag("sel_overflow_$it").assertIsDisplayed() }
        listOf("archive", "snooze", "trash", "spam")
            .forEach { compose_rule.onNodeWithTag("sel_overflow_$it").assertDoesNotExist() }
    }

    @Test
    fun spam_overflow_offers_not_spam_and_hides_contradictory_rows() {
        set_harness(default_actions, folder = "spam")

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()

        listOf("star", "read", "unread", "not_spam", "folder", "label", "trash", "customize")
            .forEach { compose_rule.onNodeWithTag("sel_overflow_$it").assertIsDisplayed() }
        listOf("archive", "snooze", "spam")
            .forEach { compose_rule.onNodeWithTag("sel_overflow_$it").assertDoesNotExist() }
    }

    @Test
    fun archive_overflow_offers_unarchive_and_hides_archive() {
        set_harness(default_actions, folder = "archive")

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()

        listOf("star", "read", "unread", "unarchive", "snooze", "folder", "label", "trash", "spam", "customize")
            .forEach { compose_rule.onNodeWithTag("sel_overflow_$it").assertIsDisplayed() }
        compose_rule.onNodeWithTag("sel_overflow_archive").assertDoesNotExist()
    }

    @Test
    fun trash_overflow_restore_dispatches_its_id() {
        set_harness(default_actions, folder = "trash")

        compose_rule.onNodeWithTag("sel_action_more").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("sel_overflow_restore").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("restore"), dispatched)
    }

    @Test
    fun trash_folder_actions_route_through_the_selection_dispatcher() {
        set_harness(default_actions, folder = "trash")

        compose_rule.onNodeWithTag("mark_read").performClick()
        compose_rule.onNodeWithTag("sel_action_restore").performClick()
        compose_rule.onNodeWithTag("sel_action_delete_permanent").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("read", "restore", "delete_permanent"), dispatched)
        assertTrue(direct_calls.isEmpty())
    }

    @Test
    fun spam_folder_actions_route_through_the_selection_dispatcher() {
        set_harness(default_actions, folder = "spam")

        compose_rule.onNodeWithTag("sel_action_not_spam").performClick()
        compose_rule.onNodeWithTag("sel_action_trash").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("not_spam", "trash"), dispatched)
    }

    @Test
    fun archive_folder_actions_route_through_the_selection_dispatcher() {
        set_harness(default_actions, folder = "archive")

        compose_rule.onNodeWithTag("sel_action_unarchive").performClick()
        compose_rule.onNodeWithTag("sel_action_spam").performClick()
        compose_rule.onNodeWithTag("sel_action_trash").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("unarchive", "spam", "trash"), dispatched)
    }
}
