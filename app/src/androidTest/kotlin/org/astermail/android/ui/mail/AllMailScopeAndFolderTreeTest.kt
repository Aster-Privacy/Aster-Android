//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.ui.mail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.mail.all_mail_folder_id
import org.astermail.android.mail.all_mail_includes_spam
import org.astermail.android.mail.all_mail_includes_trash
import org.astermail.android.mail.is_all_mail_folder
import org.astermail.android.ui.capture_device_screenshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllMailScopeAndFolderTreeTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val tree = listOf(
        quick_folder_node("work", "Work", 0, true, null),
        quick_folder_node("work_receipts", "Receipts", 1, true, "work"),
        quick_folder_node("work_receipts_2026", "2026", 2, false, "work_receipts"),
        quick_folder_node("personal", "Personal", 0, false, null),
    )

    private fun render(start_folder: String) {
        compose_rule.setContent {
            var folder by remember { mutableStateOf(start_folder) }
            var include_spam by remember { mutableStateOf(false) }
            var include_trash by remember { mutableStateOf(false) }
            AsterTheme {
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    inbox_top_bar(
                        folder_title = "All Mail",
                        unread_count = 0,
                        on_open_drawer = {},
                        on_open_search = {},
                        on_enter_select_mode = {},
                        on_refresh = {},
                        on_mark_all_read = {},
                        has_unread = true,
                        on_select_all = {},
                        on_open_settings = {},
                        sort_mode = InboxSortMode.newest,
                        on_sort_change = {},
                        show_divider = true,
                        current_folder = folder,
                        on_folder_change = { folder = it },
                        custom_folders = tree,
                        on_custom_folder_change = { id, _ -> folder = id },
                        all_mail_include_spam = include_spam,
                        all_mail_include_trash = include_trash,
                        on_all_mail_scope_change = { spam, trash ->
                            include_spam = spam
                            include_trash = trash
                            folder = all_mail_folder_id(spam, trash)
                        },
                    )
                }
            }
        }
    }

    @Test
    fun all_mail_scope_ids_encode_spam_and_trash() {
        assertEquals("all", all_mail_folder_id(false, false))
        assertEquals("all+spam", all_mail_folder_id(true, false))
        assertEquals("all+trash", all_mail_folder_id(false, true))
        assertEquals("all+spam+trash", all_mail_folder_id(true, true))
        assertTrue(is_all_mail_folder("all+spam+trash"))
        assertFalse(is_all_mail_folder("allowance"))
        assertTrue(all_mail_includes_spam("all+spam+trash"))
        assertFalse(all_mail_includes_trash("all+spam"))
    }

    @Test
    fun include_spam_and_trash_chips_toggle_the_folder_scope() {
        render("all")
        compose_rule.onNodeWithText("Include Spam").assertIsDisplayed()
        compose_rule.onNodeWithText("Include Trash").assertIsDisplayed()
        capture_device_screenshot("all_mail_chips_off")

        compose_rule.onNodeWithText("Include Spam").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("Include Trash").performClick()
        compose_rule.waitForIdle()
        capture_device_screenshot("all_mail_chips_on")

        compose_rule.onNodeWithText("All Mail").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNode(hasText("All Mail") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        capture_device_screenshot("all_mail_still_selected_with_scope")
    }

    @Test
    fun chips_are_hidden_outside_all_mail() {
        render("inbox")
        compose_rule.onNodeWithText("Include Spam").assertDoesNotExist()
        compose_rule.onNodeWithText("Include Trash").assertDoesNotExist()
    }

    @Test
    fun folder_dropdown_expands_and_collapses_subfolders() {
        render("inbox")
        compose_rule.onNodeWithText("All Mail").performClick()
        compose_rule.waitForIdle()

        compose_rule.onNode(hasText("Work") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        compose_rule.onNode(hasText("Personal") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        compose_rule.onNode(hasText("Receipts") and hasAnyAncestor(isPopup()))
            .assertDoesNotExist()
        capture_device_screenshot("folder_tree_collapsed")

        compose_rule.onNode(hasContentDescription("Expand Work") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .performClick()
        compose_rule.waitForIdle()
        compose_rule.onNode(hasText("Receipts") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        compose_rule.onNode(hasText("2026") and hasAnyAncestor(isPopup()))
            .assertDoesNotExist()

        compose_rule.onNode(hasContentDescription("Expand Receipts") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .performClick()
        compose_rule.waitForIdle()
        compose_rule.onNode(hasText("2026") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        capture_device_screenshot("folder_tree_expanded")

        compose_rule.onNode(hasContentDescription("Collapse Work") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .performClick()
        compose_rule.waitForIdle()
        compose_rule.onNode(hasText("Receipts") and hasAnyAncestor(isPopup()))
            .assertDoesNotExist()
        compose_rule.onNode(hasText("2026") and hasAnyAncestor(isPopup()))
            .assertDoesNotExist()
    }

    @Test
    fun dropdown_auto_expands_ancestors_of_the_current_folder() {
        render("work_receipts_2026")
        compose_rule.onNodeWithText("All Mail").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNode(hasText("Receipts") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        compose_rule.onNode(hasText("2026") and hasAnyAncestor(isPopup()))
            .performScrollTo()
            .assertIsDisplayed()
        capture_device_screenshot("folder_tree_auto_expanded")
    }
}
