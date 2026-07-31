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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListSearchBarTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private var query = ""

    private fun render() {
        query = ""
        compose_rule.setContent {
            var value by remember { mutableStateOf("") }
            AsterTheme {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    list_search_bar(
                        query = value,
                        on_query_change = { value = it; query = it },
                        placeholder = "Search aliases",
                        test_tag = "alias_search_bar",
                    )
                }
            }
        }
        compose_rule.waitForIdle()
    }

    @Test
    fun the_bar_shows_its_placeholder_until_something_is_typed() {
        render()

        compose_rule.onNodeWithText("Search aliases").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_search_bar_input").performTextInput("news")
        compose_rule.waitForIdle()

        assertEquals("news", query)
        compose_rule.onNodeWithText("Search aliases").assertDoesNotExist()
    }

    @Test
    fun the_clear_button_only_appears_with_a_query_and_empties_it() {
        render()

        compose_rule.onNodeWithTag("alias_search_bar_clear").assertDoesNotExist()

        compose_rule.onNodeWithTag("alias_search_bar_input").performTextInput("promo")
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("alias_search_bar_clear").assertIsDisplayed().performClick()
        compose_rule.waitForIdle()

        assertEquals("", query)
        compose_rule.onNodeWithText("Search aliases").assertIsDisplayed()
    }

    @Test
    fun the_bar_matches_the_inbox_search_field_height() {
        render()

        val height = compose_rule.onNodeWithTag("alias_search_bar").fetchSemanticsNode().size.height
        val density = compose_rule.density.density
        assertTrue("the search bar must be a 48dp pill (was ${height / density}dp)", height / density in 46f..50f)
    }
}
