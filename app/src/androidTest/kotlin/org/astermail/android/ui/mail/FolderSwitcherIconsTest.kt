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
import org.astermail.android.ui.capture_device_screenshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderSwitcherIconsTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val expected_labels = listOf(
        "Inbox",
        "Sent",
        "Drafts",
        "Archive",
        "Starred",
        "Scheduled",
        "Snoozed",
        "Spam",
        "Trash",
    )

    private fun render_top_bar(selected_folder: String) {
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    inbox_top_bar(
                        folder_title = "Spam",
                        unread_count = 6,
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
                        current_folder = selected_folder,
                        on_folder_change = {},
                        custom_folders = listOf("f1" to "Receipts", "f2" to "Newsletters"),
                    )
                }
            }
        }
    }

    @Test
    fun folder_switcher_shows_icon_for_every_entry() {
        render_top_bar("spam")
        compose_rule.onNodeWithText("Spam").performClick()
        compose_rule.waitForIdle()

        (expected_labels + listOf("Receipts", "Newsletters")).forEach { label ->
            compose_rule.onNode(hasText(label) and hasAnyAncestor(isPopup()))
                .performScrollTo()
                .assertIsDisplayed()
        }

        assertEquals(expected_labels.size, quick_switch_folders.size)
        assertEquals(expected_labels.size, quick_switch_folders.map { it.icon.name }.distinct().size)

        capture_device_screenshot("folder_switcher_dropdown_bottom")

        compose_rule.onNode(hasText("Inbox") and hasAnyAncestor(isPopup())).performScrollTo()
        compose_rule.waitForIdle()
        capture_device_screenshot("folder_switcher_dropdown_top")
    }
}
