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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.label_settings_row
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LabelMoveControlsTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val moves = mutableListOf<String>()

    private fun render(names: List<String> = listOf("Work", "Receipts", "Travel")) {
        moves.clear()
        compose_rule.setContent {
            AsterTheme {
                Column(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    names.forEachIndexed { idx, name ->
                        label_settings_row(
                            name = name,
                            color = Color(0xFF6B7280),
                            count_text = "",
                            can_move_up = idx > 0,
                            can_move_down = idx < names.lastIndex,
                            can_delete = true,
                            tag_suffix = idx.toString(),
                            on_move_up = { moves += "up_$name" },
                            on_move_down = { moves += "down_$name" },
                            on_delete = { moves += "delete_$name" },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun every_label_row_shows_both_move_controls() {
        render()
        compose_rule.onNodeWithText("Work").assertIsDisplayed()
        listOf(0, 1, 2).forEach { idx ->
            compose_rule.onNodeWithTag("label_move_up_$idx").assertIsDisplayed()
            compose_rule.onNodeWithTag("label_move_down_$idx").assertIsDisplayed()
        }
    }

    @Test
    fun middle_row_moves_in_both_directions() {
        render()
        compose_rule.onNodeWithTag("label_move_up_1").performClick()
        compose_rule.onNodeWithTag("label_move_down_1").performClick()
        compose_rule.waitForIdle()
        assertEquals(listOf("up_Receipts", "down_Receipts"), moves)
    }

    @Test
    fun first_row_cannot_move_up_and_last_row_cannot_move_down() {
        render()
        compose_rule.onNodeWithTag("label_move_up_0").performClick()
        compose_rule.onNodeWithTag("label_move_down_2").performClick()
        compose_rule.waitForIdle()
        assertEquals(emptyList<String>(), moves)
    }

    @Test
    fun first_row_can_still_move_down_and_last_row_up() {
        render()
        compose_rule.onNodeWithTag("label_move_down_0").performClick()
        compose_rule.onNodeWithTag("label_move_up_2").performClick()
        compose_rule.waitForIdle()
        assertEquals(listOf("down_Work", "up_Travel"), moves)
    }

    @Test
    fun a_single_label_has_no_enabled_move_controls() {
        render(names = listOf("Only"))
        compose_rule.onNodeWithTag("label_move_up_0").performClick()
        compose_rule.onNodeWithTag("label_move_down_0").performClick()
        compose_rule.waitForIdle()
        assertEquals(emptyList<String>(), moves)
    }

    @Test
    fun move_controls_do_not_shadow_the_delete_action() {
        render()
        compose_rule.onNodeWithTag("label_delete_1").performClick()
        compose_rule.waitForIdle()
        assertEquals(listOf("delete_Receipts"), moves)
    }
}
