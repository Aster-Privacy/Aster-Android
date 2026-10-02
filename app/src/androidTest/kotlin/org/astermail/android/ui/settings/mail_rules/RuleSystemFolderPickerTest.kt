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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.material3.Text
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.mail_rules.RuleSystemFolder
import org.astermail.android.ui.settings.mail_rules.pickers.folder_picker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleSystemFolderPickerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val names = mapOf(
        RuleSystemFolder.INBOX to context.getString(R.string.folder_inbox),
        RuleSystemFolder.ARCHIVE to context.getString(R.string.folder_archive),
        RuleSystemFolder.SPAM to context.getString(R.string.folder_spam),
        RuleSystemFolder.TRASH to context.getString(R.string.folder_trash),
    )

    private val labels = listOf(
        LabelItem(id = "1", label_token = "tok_trash", folder_type = "trash", is_system = true),
        LabelItem(id = "2", label_token = "tok_sent", folder_type = "sent", is_system = true),
        LabelItem(id = "3", label_token = "tok_inbox", folder_type = "inbox", is_system = true),
        LabelItem(id = "4", label_token = "tok_drafts", folder_type = "drafts", is_system = true),
        LabelItem(id = "5", label_token = "tok_spam", encrypted_name = "spam", folder_type = "spam", is_system = true),
        LabelItem(id = "6", label_token = "tok_archive", folder_type = "archive", is_system = true),
        LabelItem(id = "7", label_token = "tok_receipts", encrypted_name = "Receipts", folder_type = "folder"),
        LabelItem(id = "8", label_token = "tok_work", encrypted_name = "Work tag", folder_type = "label"),
    )

    @Test
    fun picker_lists_system_folders_first_then_custom_folders() {
        val items = rule_folder_picker_items(labels, names)

        assertEquals(
            listOf("tok_inbox", "tok_archive", "tok_spam", "tok_trash", "tok_receipts"),
            items.map { it.id },
        )
        assertEquals(
            listOf(names.getValue(RuleSystemFolder.INBOX), names.getValue(RuleSystemFolder.ARCHIVE),
                names.getValue(RuleSystemFolder.SPAM), names.getValue(RuleSystemFolder.TRASH), "Receipts"),
            items.map { it.label },
        )
    }

    @Test
    fun default_open_folder_stands_in_for_a_missing_inbox() {
        val without_inbox = labels.filter { it.folder_type != "inbox" } +
            LabelItem(id = "9", label_token = "tok_open", folder_type = "default_open", is_system = true)

        val items = rule_folder_picker_items(without_inbox, names)

        assertEquals("tok_open", items.first().id)
        assertEquals(names.getValue(RuleSystemFolder.INBOX), items.first().label)
    }

    @Test
    fun system_folders_render_and_pick_their_own_tokens() {
        val picked = mutableListOf<String>()
        compose_rule.setContent {
            folder_picker(
                on_dismiss = {},
                folders = rule_folder_picker_items(labels, names),
                selected_token = null,
                on_pick = { token, _ -> picked += token },
            )
        }

        for (type in RuleSystemFolder.entries) {
            compose_rule.onNodeWithTag("folder_${names.getValue(type)}").assertIsDisplayed()
        }
        compose_rule.onNodeWithTag("folder_Receipts").assertIsDisplayed()
        compose_rule.onNodeWithTag("folder_${names.getValue(RuleSystemFolder.SPAM)}").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("tok_spam"), picked)
    }

    @Test
    fun picker_hides_sent_drafts_and_labels() {
        compose_rule.setContent {
            folder_picker(
                on_dismiss = {},
                folders = rule_folder_picker_items(labels, names),
                selected_token = "tok_inbox",
                on_pick = { _, _ -> },
            )
        }

        compose_rule.onNodeWithTag("folder_Work tag").assertDoesNotExist()
        compose_rule.onNodeWithTag("folder_sent").assertDoesNotExist()
        compose_rule.onNodeWithTag("folder_drafts").assertDoesNotExist()
        compose_rule.onNodeWithTag("folder_spam").assertDoesNotExist()
    }

    @Test
    fun saved_system_target_shows_its_localized_name() {
        compose_rule.setContent {
            Text(rule_folder_name(labels, "tok_spam").orEmpty())
        }

        compose_rule.onNodeWithText(names.getValue(RuleSystemFolder.SPAM)).assertIsDisplayed()
    }
}
