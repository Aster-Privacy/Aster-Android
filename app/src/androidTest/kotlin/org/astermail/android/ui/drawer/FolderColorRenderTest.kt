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

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class FolderColorRenderTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val red = "#ef4444"
    private val green = "#22c55e"

    @Before
    fun clear_sidebar_prefs() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("aster_sidebar", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun save_screenshot(name: String, node: SemanticsNodeInteraction = compose_rule.onRoot()) {
        val bitmap = runCatching { node.captureToImage().asAndroidBitmap() }.getOrNull() ?: return
        val dir = InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null) ?: return
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun pixel_count(bitmap: Bitmap, hex: String): Int {
        val target = android.graphics.Color.parseColor(hex)
        val want_r = android.graphics.Color.red(target)
        val want_g = android.graphics.Color.green(target)
        val want_b = android.graphics.Color.blue(target)
        var found = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val p = bitmap.getPixel(x, y)
                if (android.graphics.Color.alpha(p) < 250) continue
                if (kotlin.math.abs(android.graphics.Color.red(p) - want_r) <= 4 &&
                    kotlin.math.abs(android.graphics.Color.green(p) - want_g) <= 4 &&
                    kotlin.math.abs(android.graphics.Color.blue(p) - want_b) <= 4
                ) {
                    found++
                }
            }
        }
        return found
    }

    private fun render(on_recolor: (drawer_folder_item, String) -> Unit = { _, _ -> }) {
        compose_rule.setContent {
            AsterTheme {
                DrawerContent(
                    selected_id = "inbox",
                    on_select = {},
                    on_close = {},
                    api_folder_items = listOf(
                        drawer_folder_item(
                            id = "red_folder",
                            label = "Red Folder",
                            icon = Icons.Outlined.Folder,
                            count = 0,
                            label_id = "id_red",
                            color = red,
                        ),
                        drawer_folder_item(
                            id = "green_folder",
                            label = "Green Folder",
                            icon = Icons.Outlined.Folder,
                            count = 0,
                            label_id = "id_green",
                            color = green,
                        ),
                        drawer_folder_item(
                            id = "plain_folder",
                            label = "Plain Folder",
                            icon = Icons.Outlined.Folder,
                            count = 0,
                            label_id = "id_plain",
                        ),
                    ),
                    folder_actions = folder_menu_actions(on_recolor = on_recolor),
                )
            }
        }
    }

    @Test
    fun folder_rows_paint_their_own_color() {
        render()
        compose_rule.onNodeWithText("Green Folder").performScrollTo()
        compose_rule.waitForIdle()
        val bitmap = compose_rule.onRoot().captureToImage().asAndroidBitmap()
        save_screenshot("folder_colors_drawer")
        assertTrue("red folder icon not painted", pixel_count(bitmap, red) > 20)
        assertTrue("green folder icon not painted", pixel_count(bitmap, green) > 20)
    }

    @Test
    fun recolor_dialog_reports_the_picked_swatch() {
        val picked = mutableListOf<Pair<String, String>>()
        render { item, color -> picked.add(item.label_id to color) }
        compose_rule.onNodeWithText("Red Folder").performScrollTo()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("Red Folder").performTouchInput {
            longClick()
        }
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("folder_action_color").performClick()
        compose_rule.waitForIdle()
        save_screenshot("folder_color_dialog")
        compose_rule.onNodeWithTag("folder_color_swatch_$green").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("Save").performClick()
        compose_rule.waitForIdle()
        assertEquals(listOf("id_red" to green), picked)
    }
}
