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

package org.astermail.android.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.api.mail.MailItem
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Reproduces the "cannot select search results" bug: SearchScreen rendered its
// result rows with on_long_click = {}, so long-pressing a result did nothing
// and there was no way to bulk-delete from search. The fix wires a selection
// mode (search_results_list + search_select_top_bar + search_select_bottom_bar)
// mirroring the inbox. These tests drive the real composables end to end:
// long-press selects, taps toggle, select-all covers every result, and delete
// removes exactly the selected rows.
//
@RunWith(AndroidJUnit4::class)
class SearchSelectionDeleteTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun fixture_items(count: Int): List<InboxItem> = (1..count).map { n ->
        InboxItem(
            id = "m$n",
            thread_token = "t$n",
            thread_message_count = 1,
            sender_name = "GitHub",
            sender_email = "notifications@github.com",
            subject = "GitHub alert $n",
            preview = "Some GitHub notification body $n",
            timestamp = "2026-07-%02dT10:00:00Z".format((n % 28) + 1),
            is_read = n % 2 == 0,
            is_starred = false,
            is_encrypted = false,
            has_attachments = false,
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            labels = emptyList(),
            raw_item = MailItem(id = "m$n"),
        )
    }

    private val deleted_ids = mutableListOf<String>()
    private val remaining_ids = mutableListOf<String>()

    @Composable
    private fun selection_harness(initial_items: List<InboxItem>) {
        val items = remember { mutableStateListOf<InboxItem>().apply { addAll(initial_items) } }
        var select_mode by remember { mutableStateOf(false) }
        val selected_ids = remember { mutableStateListOf<String>() }

        fun exit_select_mode() {
            select_mode = false
            selected_ids.clear()
        }

        fun toggle_selection(id: String) {
            if (selected_ids.contains(id)) selected_ids.remove(id) else selected_ids.add(id)
            if (selected_ids.isEmpty()) select_mode = false
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (select_mode) {
                search_select_top_bar(
                    selected_count = selected_ids.size,
                    on_close = ::exit_select_mode,
                    on_select_all = {
                        selected_ids.clear()
                        selected_ids.addAll(items.map { it.id })
                    },
                )
            }
            search_results_list(
                items = items.toList(),
                select_mode = select_mode,
                selected_ids = selected_ids,
                on_open_email = {},
                on_toggle_selection = ::toggle_selection,
                on_enter_select_mode = { id ->
                    select_mode = true
                    selected_ids.clear()
                    selected_ids.add(id)
                },
                on_toggle_star = {},
                modifier = Modifier.weight(1f),
            )
            if (select_mode) {
                search_select_bottom_bar(
                    selected_count = selected_ids.size,
                    on_mark_read = {},
                    on_archive = {},
                    on_delete = {
                        deleted_ids.addAll(selected_ids)
                        items.removeAll { it.id in selected_ids.toSet() }
                        remaining_ids.clear()
                        remaining_ids.addAll(items.map { it.id })
                        exit_select_mode()
                    },
                )
            }
        }
    }

    private fun set_harness(count: Int) {
        deleted_ids.clear()
        remaining_ids.clear()
        compose_rule.setContent { AsterTheme { selection_harness(fixture_items(count)) } }
        compose_rule.waitForIdle()
    }

    private fun long_press_row(id: String) {
        compose_rule.onNodeWithTag("search_row_$id").performTouchInput { longClick() }
        compose_rule.waitForIdle()
    }

    private fun assert_selected_count(n: Int) {
        compose_rule.onNodeWithTag("search_selected_count")
            .assertTextEquals(context.getString(R.string.inbox_selected_count, n))
    }

    @Test
    fun long_press_enters_selection_mode_and_selects_the_row() {
        set_harness(12)

        compose_rule.onNodeWithTag("search_select_bar").assertDoesNotExist()

        long_press_row("m1")

        compose_rule.onNodeWithTag("search_select_bar").assertIsDisplayed()
        compose_rule.onNodeWithTag("search_delete").assertIsDisplayed()
        assert_selected_count(1)
    }

    @Test
    fun taps_toggle_selection_while_in_selection_mode() {
        set_harness(12)
        long_press_row("m1")

        compose_rule.onNodeWithTag("search_row_m2").performClick()
        compose_rule.waitForIdle()
        assert_selected_count(2)

        compose_rule.onNodeWithTag("search_row_m3").performClick()
        compose_rule.waitForIdle()
        assert_selected_count(3)

        compose_rule.onNodeWithTag("search_row_m2").performClick()
        compose_rule.waitForIdle()
        assert_selected_count(2)
    }

    @Test
    fun delete_removes_only_the_selected_rows() {
        set_harness(12)
        long_press_row("m1")
        compose_rule.onNodeWithTag("search_row_m2").performClick()
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("search_delete").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("m1", "m2"), deleted_ids.sorted())
        assertEquals(10, remaining_ids.size)
        assertTrue("m1" !in remaining_ids && "m2" !in remaining_ids)
        compose_rule.onNodeWithTag("search_select_bar").assertDoesNotExist()
        compose_rule.onNodeWithTag("search_row_m1").assertDoesNotExist()
        compose_rule.onNodeWithTag("search_row_m2").assertDoesNotExist()
        compose_rule.onNodeWithTag("search_row_m3").assertIsDisplayed()
    }

    @Test
    fun select_all_then_delete_removes_every_result() {
        set_harness(12)
        long_press_row("m1")

        compose_rule.onNodeWithTag("search_select_all").performClick()
        compose_rule.waitForIdle()
        assert_selected_count(12)

        compose_rule.onNodeWithTag("search_delete").performClick()
        compose_rule.waitForIdle()

        assertEquals(12, deleted_ids.size)
        assertEquals(0, remaining_ids.size)
        compose_rule.onNodeWithTag("search_select_bar").assertDoesNotExist()
        compose_rule.onNodeWithTag("search_row_m1").assertDoesNotExist()
    }

    @Test
    fun exit_selection_restores_normal_mode() {
        set_harness(12)
        long_press_row("m1")

        compose_rule.onNodeWithTag("search_exit_select").performClick()
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("search_select_bar").assertDoesNotExist()
        compose_rule.onNodeWithTag("search_delete").assertDoesNotExist()
    }
}
