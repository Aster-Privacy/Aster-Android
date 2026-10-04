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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListPositionSurvivesBackNavTest {
    @get:Rule
    val compose_rule = createComposeRule()

    private lateinit var nav_controller: NavHostController

    private fun background_and_return() {
        app_session.mark_backgrounded()
        app_session.mark_foregrounded()
    }

    @Composable
    private fun fixed_reset(list_state: LazyListState) {
        on_return_to_foreground { list_state.scrollToItem(0) }
    }

    @Composable
    private fun buggy_reset(list_state: LazyListState) {
        val epoch by app_session.foreground_epoch.collectAsStateWithLifecycle()
        LaunchedEffect(epoch) {
            if (epoch > 0) list_state.scrollToItem(0)
        }
    }

    @Composable
    private fun nav_harness(reset: @Composable (LazyListState) -> Unit) {
        val controller = rememberNavController()
        nav_controller = controller
        NavHost(navController = controller, startDestination = "list") {
            composable("list") {
                val list_state = remember_session_lazy_list_state()
                reset(list_state)
                LazyColumn(state = list_state, modifier = Modifier.fillMaxSize().testTag("list")) {
                    items((0 until 60).toList(), key = { "m$it" }) { index ->
                        Text(
                            "Message $index",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clickable { controller.navigate("detail") },
                        )
                    }
                }
            }
            composable("detail") {
                Text("mail_detail", modifier = Modifier.testTag("detail"))
            }
        }
    }

    private fun scroll_open_and_go_back() {
        compose_rule.onNodeWithTag("list").performScrollToIndex(40)
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("Message 40").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("detail").assertIsDisplayed()
        compose_rule.runOnUiThread { nav_controller.popBackStack() }
        compose_rule.waitForIdle()
    }

    @Test
    fun position_survives_opening_a_message_after_the_app_was_backgrounded() {
        background_and_return()
        compose_rule.setContent { AsterTheme { nav_harness { fixed_reset(it) } } }
        compose_rule.waitForIdle()

        scroll_open_and_go_back()

        compose_rule.onNodeWithText("Message 40").assertIsDisplayed()
        compose_rule.onNodeWithText("Message 0").assertDoesNotExist()
    }

    @Test
    fun old_pattern_reproduces_the_jump_to_the_top_on_back() {
        background_and_return()
        compose_rule.setContent { AsterTheme { nav_harness { buggy_reset(it) } } }
        compose_rule.waitForIdle()

        scroll_open_and_go_back()

        compose_rule.onNodeWithText("Message 0").assertIsDisplayed()
    }

    @Test
    fun returning_to_the_app_with_the_list_on_screen_still_shows_the_top() {
        compose_rule.setContent { AsterTheme { nav_harness { fixed_reset(it) } } }
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("list").performScrollToIndex(40)
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("Message 0").assertDoesNotExist()

        compose_rule.runOnUiThread { background_and_return() }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("Message 0").assertIsDisplayed()
    }
}
