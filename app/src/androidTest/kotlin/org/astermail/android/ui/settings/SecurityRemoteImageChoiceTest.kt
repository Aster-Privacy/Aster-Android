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

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.security_choice_row
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecurityRemoteImageChoiceTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val options = listOf(
        "never" to "Never load",
        "ask" to "Ask before loading",
        "always" to "Always load",
    )

    @Test
    fun selecting_a_remote_image_option_updates_the_selection() {
        var last_selected = ""
        compose_rule.setContent {
            AsterTheme {
                var selection by remember { mutableStateOf("never") }
                Column {
                    options.forEach { (id, label) ->
                        security_choice_row(
                            label = label,
                            selected = selection == id,
                            test_tag = "remote_image_loading_$id",
                        ) {
                            selection = id
                            last_selected = id
                        }
                    }
                }
            }
        }

        options.forEach { (id, label) ->
            compose_rule.onNodeWithTag("remote_image_loading_$id", useUnmergedTree = true).assertExists()
            compose_rule.onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }

        compose_rule.onNodeWithTag("remote_image_loading_ask", useUnmergedTree = true).performClick()
        compose_rule.waitForIdle()

        assertEquals("ask", last_selected)
    }
}
