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
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.astermail.android.R
import org.astermail.android.api.labels.LabelItem
import org.astermail.android.design.AsterTheme
import org.astermail.android.settings.SettingsUiState
import org.astermail.android.settings.SettingsViewModel
import org.astermail.android.ui.capture_device_screenshot
import org.astermail.android.ui.settings.detail.FoldersScreen
import org.astermail.android.ui.settings.detail.LabelsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDeleteConfirmTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun folder(
        id: String,
        token: String,
        name: String,
        parent: String? = null,
    ) = LabelItem(
        id = id,
        label_token = token,
        encrypted_name = name,
        folder_type = "folder",
        item_count = 0L,
        parent_token = parent,
    )

    private fun set_folders(labels: List<LabelItem>): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(SettingsUiState(labels = labels, is_loading = false))
        compose_rule.setContent {
            AsterTheme {
                FoldersScreen(on_back = {}, vm = vm)
            }
        }
        compose_rule.waitForIdle()
        return vm
    }

    private fun set_labels(labels: List<LabelItem>): SettingsViewModel {
        val vm = mockk<SettingsViewModel>(relaxed = true)
        every { vm.state } returns MutableStateFlow(SettingsUiState(labels = labels, is_loading = false))
        compose_rule.setContent {
            AsterTheme {
                LabelsScreen(on_back = {}, vm = vm)
            }
        }
        compose_rule.waitForIdle()
        return vm
    }

    private fun tap_trash(description_res: Int) {
        compose_rule.onNodeWithContentDescription(context.getString(description_res)).performClick()
        compose_rule.waitForIdle()
    }

    @Test
    fun tapping_the_trash_icon_asks_before_deleting_a_folder() {
        val vm = set_folders(listOf(folder("f1", "tok-1", "hii")))

        tap_trash(R.string.delete_folder)

        compose_rule.onNodeWithText(context.getString(R.string.delete_folder_confirm_title)).assertIsDisplayed()
        compose_rule
            .onNodeWithText(context.getString(R.string.delete_folder_confirm_message, "hii"))
            .assertIsDisplayed()
        verify(exactly = 0) { vm.delete_label(any()) }
        capture_device_screenshot("folder_delete_confirm")
    }

    @Test
    fun cancelling_the_folder_dialog_deletes_nothing() {
        val vm = set_folders(listOf(folder("f1", "tok-1", "hii")))

        tap_trash(R.string.delete_folder)
        compose_rule.onNodeWithText(context.getString(R.string.cancel)).performClick()
        compose_rule.waitForIdle()

        verify(exactly = 0) { vm.delete_label(any()) }
        compose_rule.onNodeWithText(context.getString(R.string.delete_folder_confirm_title)).assertDoesNotExist()
    }

    @Test
    fun confirming_the_folder_dialog_deletes_that_folder() {
        val vm = set_folders(listOf(folder("f1", "tok-1", "hii")))

        tap_trash(R.string.delete_folder)
        compose_rule.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose_rule.waitForIdle()

        verify(exactly = 1) { vm.delete_label("f1") }
    }

    @Test
    fun a_folder_with_children_warns_about_subfolders() {
        set_folders(
            listOf(
                folder("f1", "tok-1", "parent"),
                folder("f2", "tok-2", "child", parent = "tok-1"),
            ),
        )

        compose_rule.onAllNodesWithContentDescription(context.getString(R.string.delete_folder))[0].performClick()
        compose_rule.waitForIdle()

        compose_rule
            .onNodeWithText(context.getString(R.string.delete_folder_confirm_subfolders), substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun a_leaf_folder_does_not_mention_subfolders() {
        set_folders(listOf(folder("f1", "tok-1", "hii")))

        tap_trash(R.string.delete_folder)

        compose_rule
            .onNodeWithText(context.getString(R.string.delete_folder_confirm_subfolders), substring = true)
            .assertDoesNotExist()
    }

    @Test
    fun tapping_the_trash_icon_asks_before_deleting_a_label() {
        val vm = set_labels(listOf(folder("l1", "tok-1", "receipts").copy(folder_type = "label")))

        tap_trash(R.string.delete_label)

        compose_rule.onNodeWithText(context.getString(R.string.delete_label_confirm_title)).assertIsDisplayed()
        verify(exactly = 0) { vm.delete_label(any()) }
    }

    @Test
    fun confirming_the_label_dialog_deletes_that_label() {
        val vm = set_labels(listOf(folder("l1", "tok-1", "receipts").copy(folder_type = "label")))

        tap_trash(R.string.delete_label)
        compose_rule.onNodeWithText(context.getString(R.string.delete)).performClick()
        compose_rule.waitForIdle()

        verify(exactly = 1) { vm.delete_label("l1") }
    }
}
