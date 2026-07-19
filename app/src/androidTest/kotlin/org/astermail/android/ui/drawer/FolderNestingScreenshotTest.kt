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

package org.astermail.android.ui.drawer

import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class FolderNestingScreenshotTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun save_screenshot(name: String, node: SemanticsNodeInteraction = compose_rule.onRoot()) {
        val bitmap = node.captureToImage().asAndroidBitmap()
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun capture_nested_drawer_and_create_dialog() {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = {},
                    on_close = {},
                    api_folder_items = listOf(
                        drawer_folder_item(id = "t1", label = "Test 1", icon = Icons.Outlined.Folder, count = 3, depth = 0),
                        drawer_folder_item(id = "apple", label = "Apple", icon = Icons.Outlined.Folder, count = 0, depth = 1, trail = listOf(true), has_next = true),
                        drawer_folder_item(id = "boy", label = "Boy", icon = Icons.Outlined.Folder, count = 1, depth = 1, trail = listOf(true), has_next = false),
                        drawer_folder_item(id = "cat", label = "Cat", icon = Icons.Outlined.Folder, count = 0, depth = 2, trail = listOf(true, false), has_next = false),
                        drawer_folder_item(id = "zoo", label = "Zoo", icon = Icons.Outlined.Folder, count = 0, depth = 0),
                    ),
                    folder_parent_options = listOf(
                        folder_parent_option(token = "t1", label = "Test 1", depth = 0, path_label = "Test 1"),
                        folder_parent_option(token = "boy", label = "Boy", depth = 1, path_label = "Test 1 · Boy"),
                        folder_parent_option(token = "zoo", label = "Zoo", depth = 0, path_label = "Zoo"),
                    ),
                )
            }
        }

        compose_rule.onNodeWithText("Zoo").performScrollTo()
        compose_rule.waitForIdle()
        save_screenshot("folder_drawer_nested")

        compose_rule.onNodeWithTag("create_folder").performScrollTo().performClick()
        compose_rule.waitForIdle()
        compose_rule.onNode(hasSetTextAction()).performTextInput("Receipts")
        compose_rule.onNodeWithTag("parent_folder_selector").performClick()
        compose_rule.waitForIdle()
        compose_rule.onAllNodesWithText("Boy")
            .filterToOne(hasAnyAncestor(isPopup()))
            .performClick()
        compose_rule.waitForIdle()
        save_screenshot("folder_create_dialog", compose_rule.onNode(isDialog()))
    }
}
