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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.astermail.android.design.keep_visible_above_keyboard
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepVisibleAboveKeyboardTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private fun is_displayed(tag: String): Boolean = runCatching {
        compose_rule.onNodeWithTag(tag).assertIsDisplayed()
    }.isSuccess

    @Test
    fun focused_field_scrolls_into_view() {
        val requester = FocusRequester()
        compose_rule.setContent {
            AsterTheme {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(2000.dp))
                    BasicTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier
                            .keep_visible_above_keyboard()
                            .focusRequester(requester)
                            .testTag("field_under_test"),
                    )
                    Spacer(Modifier.height(2000.dp))
                }
            }
        }

        compose_rule.onNodeWithTag("field_under_test").assertIsNotDisplayed()

        compose_rule.runOnIdle { requester.requestFocus() }
        compose_rule.waitUntil(timeoutMillis = 5_000) { is_displayed("field_under_test") }

        compose_rule.onNodeWithTag("field_under_test").assertIsDisplayed()
    }
}
