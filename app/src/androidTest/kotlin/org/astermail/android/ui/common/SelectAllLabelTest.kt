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

package org.astermail.android.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterMaterial
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SelectAllLabelTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var clicks = 0

    private fun render() {
        compose_rule.setContent {
            AsterTheme {
                Box(Modifier.fillMaxSize().background(AsterMaterial.colors.bg_primary)) {
                    select_all_button(
                        on_click = { clicks += 1 },
                        modifier = Modifier.testTag("select_all"),
                    )
                }
            }
        }
    }

    @Test
    fun select_all_shows_a_readable_label() {
        render()
        compose_rule.onNodeWithText("Select all").assertIsDisplayed()
    }

    @Test
    fun select_all_label_click_invokes_the_action() {
        render()
        compose_rule.onNodeWithTag("select_all").performClick()
        compose_rule.waitForIdle()
        assertEquals(1, clicks)
    }
}
