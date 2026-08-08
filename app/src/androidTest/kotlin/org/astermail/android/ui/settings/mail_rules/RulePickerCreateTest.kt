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

package org.astermail.android.ui.settings.mail_rules

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.mail_rules.Action
import org.astermail.android.api.mail_rules.ReadState
import org.astermail.android.ui.settings.mail_rules.pickers.folder_picker
import org.astermail.android.ui.settings.mail_rules.pickers.label_multi_picker
import org.astermail.android.ui.settings.mail_rules.pickers.picker_item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RulePickerCreateTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val folders = listOf(
        picker_item(id = "folder_token_a", label = "Receipts"),
        picker_item(id = "folder_token_b", label = "Newsletters"),
    )

    private val labels = listOf(
        picker_item(id = "label_token_a", label = "Important"),
    )

    @Test
    fun folder_picker_offers_create_when_folders_exist() {
        var created = 0
        compose_rule.setContent {
            folder_picker(
                on_dismiss = {},
                folders = folders,
                selected_token = null,
                on_pick = { _, _ -> },
                on_create = { created += 1 },
            )
        }

        compose_rule.onNodeWithTag("picker_create_folder").assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals(1, created)
    }

    @Test
    fun folder_picker_offers_create_when_empty() {
        var created = 0
        compose_rule.setContent {
            folder_picker(
                on_dismiss = {},
                folders = emptyList(),
                selected_token = null,
                on_pick = { _, _ -> },
                on_create = { created += 1 },
            )
        }

        compose_rule.onNodeWithTag("picker_create_folder").assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals(1, created)
    }

    @Test
    fun label_picker_offers_create_when_labels_exist() {
        var created = 0
        compose_rule.setContent {
            label_multi_picker(
                on_dismiss = {},
                labels = labels,
                selected_tokens = emptyList(),
                on_confirm = {},
                on_create = { created += 1 },
            )
        }

        compose_rule.onNodeWithTag("picker_create_label").assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals(1, created)
    }

    @Test
    fun label_picker_offers_create_when_empty() {
        var created = 0
        compose_rule.setContent {
            label_multi_picker(
                on_dismiss = {},
                labels = emptyList(),
                selected_tokens = emptyList(),
                on_confirm = {},
                on_create = { created += 1 },
            )
        }

        compose_rule.onNodeWithTag("picker_create_label").assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals(1, created)
    }

    @Test
    fun label_picker_shows_new_label_as_selected_after_creation() {
        val created_token = "label_token_new"
        compose_rule.setContent {
            label_multi_picker(
                on_dismiss = {},
                labels = labels + picker_item(id = created_token, label = "Invoices"),
                selected_tokens = listOf(created_token),
                on_confirm = {},
                on_create = {},
            )
        }

        compose_rule.onNodeWithTag("rule_label_Invoices").assertIsDisplayed()
    }

    @Test
    fun created_folder_token_is_applied_to_the_move_action() {
        val updated = apply_created_target(Action.MoveTo(folder_token = ""), "folder_token_new")

        assertEquals(Action.MoveTo(folder_token = "folder_token_new"), updated)
    }

    @Test
    fun created_label_token_is_appended_to_the_label_action() {
        val updated = apply_created_target(
            Action.ApplyLabels(label_tokens = listOf("label_token_a")),
            "label_token_new",
        )

        assertEquals(
            Action.ApplyLabels(label_tokens = listOf("label_token_a", "label_token_new")),
            updated,
        )
    }

    @Test
    fun already_selected_label_token_is_not_duplicated() {
        val updated = apply_created_target(
            Action.ApplyLabels(label_tokens = listOf("label_token_new")),
            "label_token_new",
        )

        assertNull(updated)
    }

    @Test
    fun unrelated_actions_and_blank_tokens_are_ignored() {
        assertNull(apply_created_target(Action.MarkAs(value = ReadState.READ), "folder_token_new"))
        assertNull(apply_created_target(Action.MoveTo(folder_token = ""), ""))
        assertNull(apply_created_target(null, "folder_token_new"))
    }

    @Test
    fun move_action_keeps_a_single_folder_token() {
        val first = apply_created_target(Action.MoveTo(folder_token = "folder_token_a"), "folder_token_new")

        assertTrue(first is Action.MoveTo)
        assertEquals("folder_token_new", (first as Action.MoveTo).folder_token)
    }
}
