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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScopeSelectionBannerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    @Test
    fun banner_stays_hidden_when_nothing_is_offered() {
        compose_rule.setContent {
            AsterTheme {
                scope_selection_banner(
                    offered = false,
                    confirmed = false,
                    folder_total = 30000,
                    folder_name = "Inbox",
                    crosses_categories = false,
                    on_confirm = {},
                )
            }
        }

        compose_rule.onNodeWithTag("scope_selection_offer").assertDoesNotExist()
        compose_rule.onNodeWithTag("scope_selection_confirmed").assertDoesNotExist()
    }

    @Test
    fun tapping_the_offer_confirms_the_folder_wide_selection() {
        var confirms = 0
        compose_rule.setContent {
            var confirmed by remember { mutableStateOf(false) }
            AsterTheme {
                scope_selection_banner(
                    offered = !confirmed,
                    confirmed = confirmed,
                    folder_total = 30000,
                    folder_name = "Inbox",
                    crosses_categories = false,
                    on_confirm = { confirms++; confirmed = true },
                )
            }
        }

        compose_rule.onNodeWithText("Select all 30000 in Inbox").assertIsDisplayed()
        compose_rule.onNodeWithTag("scope_selection_offer").performClick()
        compose_rule.waitForIdle()

        assertEquals(1, confirms)
        compose_rule.onNodeWithTag("scope_selection_confirmed").assertIsDisplayed()
        compose_rule.onNodeWithText("All 30000 in Inbox are selected").assertIsDisplayed()
    }

    @Test
    fun the_offer_says_when_it_crosses_every_category() {
        compose_rule.setContent {
            AsterTheme {
                scope_selection_banner(
                    offered = true,
                    confirmed = false,
                    folder_total = 30000,
                    folder_name = "Inbox",
                    crosses_categories = true,
                    on_confirm = {},
                )
            }
        }

        compose_rule
            .onNodeWithText("Select all 30000 in Inbox, including every category")
            .assertIsDisplayed()
    }
}
