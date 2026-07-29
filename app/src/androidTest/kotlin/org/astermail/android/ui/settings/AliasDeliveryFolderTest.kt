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

package org.astermail.android.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.alias_delivery_folder_name
import org.astermail.android.ui.settings.detail.alias_delivery_picker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AliasDeliveryFolderTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val folders = listOf(
        LabelItem(id = "l1", label_token = "dG9rZW4x", encrypted_name = "Banking", folder_type = "folder"),
        LabelItem(id = "l2", label_token = "dG9rZW4y", encrypted_name = "Receipts", folder_type = "custom"),
    )

    @Test
    fun picker_lists_inbox_archive_and_custom_folders() {
        compose_rule.setContent {
            AsterTheme {
                alias_delivery_picker(
                    selected_label = "Inbox",
                    folders = folders,
                    on_select_inbox = {},
                    on_select_archive = {},
                    on_select_folder = {},
                )
            }
        }

        compose_rule.onNodeWithTag("alias_delivery_folder_selector").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_delivery_folder_selector").performClick()

        assertEquals(2, compose_rule.onAllNodesWithText("Inbox").fetchSemanticsNodes().size)
        compose_rule.onNodeWithText("Archive").assertIsDisplayed()
        compose_rule.onNodeWithText("Banking").assertIsDisplayed()
        compose_rule.onNodeWithText("Receipts").assertIsDisplayed()
    }

    @Test
    fun picking_a_folder_emits_its_token() {
        var picked: String? = null
        compose_rule.setContent {
            AsterTheme {
                alias_delivery_picker(
                    selected_label = "Inbox",
                    folders = folders,
                    on_select_inbox = {},
                    on_select_archive = {},
                    on_select_folder = { picked = it },
                )
            }
        }

        compose_rule.onNodeWithTag("alias_delivery_folder_selector").performClick()
        compose_rule.onNodeWithText("Receipts").performClick()
        compose_rule.waitForIdle()

        assertEquals("dG9rZW4y", picked)
    }

    @Test
    fun picking_archive_emits_archive_callback() {
        var archive_picked = false
        compose_rule.setContent {
            AsterTheme {
                alias_delivery_picker(
                    selected_label = "Banking",
                    folders = folders,
                    on_select_inbox = {},
                    on_select_archive = { archive_picked = true },
                    on_select_folder = {},
                )
            }
        }

        compose_rule.onNodeWithTag("alias_delivery_folder_selector").performClick()
        compose_rule.onNodeWithText("Archive").performClick()
        compose_rule.waitForIdle()

        assertEquals(true, archive_picked)
    }

    @Test
    fun row_label_resolves_token_to_folder_name() {
        var resolved: String? = null
        compose_rule.setContent {
            AsterTheme {
                resolved = alias_delivery_folder_name(
                    alias = AliasInfo(
                        id = "a1",
                        encrypted_local_part = "alias1",
                        domain = "astermail.org",
                        delivery_folder_token = "dG9rZW4x",
                    ),
                    labels = folders,
                )
            }
        }
        compose_rule.waitForIdle()

        assertEquals("Banking", resolved)
    }

    @Test
    fun row_label_falls_back_to_archive_for_never_inbox() {
        var resolved: String? = null
        compose_rule.setContent {
            AsterTheme {
                resolved = alias_delivery_folder_name(
                    alias = AliasInfo(
                        id = "a1",
                        encrypted_local_part = "alias1",
                        domain = "astermail.org",
                        never_inbox = true,
                    ),
                    labels = folders,
                )
            }
        }
        compose_rule.waitForIdle()

        assertEquals("Archive", resolved)
    }

    @Test
    fun row_label_is_null_for_plain_inbox_alias() {
        var resolved: String? = "unset"
        compose_rule.setContent {
            AsterTheme {
                resolved = alias_delivery_folder_name(
                    alias = AliasInfo(
                        id = "a1",
                        encrypted_local_part = "alias1",
                        domain = "astermail.org",
                    ),
                    labels = folders,
                )
            }
        }
        compose_rule.waitForIdle()

        assertNull(resolved)
    }
}
