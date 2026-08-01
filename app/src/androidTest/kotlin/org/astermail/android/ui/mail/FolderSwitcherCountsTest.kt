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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.capture_screenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSwitcherCountsTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val counts = mapOf(
        "inbox" to 12,
        "drafts" to 3,
        "spam" to 7,
        "trash" to 4,
        "f1" to 5,
        "f2" to 11,
    )

    private fun render_top_bar() {
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    inbox_top_bar(
                        folder_title = "Inbox",
                        unread_count = 12,
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
                        current_folder = "inbox",
                        on_folder_change = {},
                        custom_folders = listOf(
                            quick_folder_node("f1", "Receipts", 0, false, null),
                            quick_folder_node("f2", "My Feed", 0, false, null),
                        ),
                        folder_unread_counts = counts,
                    )
                }
            }
        }
    }

    private fun assert_row_count(label: String, count: String) {
        compose_rule.onNode(hasText(label) and hasAnyAncestor(isPopup())).performScrollTo()
        compose_rule.onNode(
            hasText(label) and hasText(count) and hasAnyAncestor(isPopup()),
        ).assertIsDisplayed()
    }

    private fun assert_row_has_no_count(label: String) {
        compose_rule.onNode(hasText(label) and hasAnyAncestor(isPopup())).performScrollTo()
        compose_rule.onNode(
            hasText(label) and hasAnyAncestor(isPopup()),
        ).assertIsDisplayed()
        compose_rule.onNode(
            hasText(label) and hasText("0") and hasAnyAncestor(isPopup()),
        ).assertDoesNotExist()
    }

    @Test
    fun dropdown_shows_unread_counts_for_system_and_custom_folders() {
        render_top_bar()
        compose_rule.onNodeWithText("Inbox").performClick()
        compose_rule.waitForIdle()

        assert_row_count("Inbox", "12")
        assert_row_count("Drafts", "3")
        assert_row_count("Spam", "7")
        assert_row_count("Trash", "4")

        compose_rule.onNode(hasText("Inbox") and hasAnyAncestor(isPopup())).performScrollTo()
        capture_screenshot("folder_switcher_counts_top", compose_rule.onNode(isPopup()))

        assert_row_count("Receipts", "5")
        assert_row_count("My Feed", "11")
        capture_screenshot("folder_switcher_counts_custom", compose_rule.onNode(isPopup()))

        assert_row_has_no_count("Sent")
        assert_row_has_no_count("Archive")
    }
}
