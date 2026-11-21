//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//
package org.astermail.android.ui.guard

import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GuardDigitBoxesTest {

    @get:Rule(order = 0)
    val activity_rule = object : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val info = ActivityInfo().apply {
                name = ComponentActivity::class.java.name
                packageName = app.packageName
            }
            shadowOf(app.packageManager).addOrUpdateActivity(info)
        }
    }

    @get:Rule(order = 1)
    val rule = createComposeRule()

    private var current = ""

    private fun show() {
        rule.setContent {
            AsterTheme {
                var value by remember { mutableStateOf("") }
                guard_digit_boxes(
                    value = value,
                    on_value = { value = it; current = it },
                    digits = 2,
                    error = false,
                    shake_key = 0,
                    enabled = true,
                    description = "number",
                )
            }
        }
    }

    private fun field() = rule.onNode(hasContentDescription("number"))

    @Test
    fun input_keeps_digits_only_and_caps_length() {
        assertEquals("42", guard_digit_input("4a2 9", 2))
        assertEquals("", guard_digit_input("ab", 2))
    }

    @Test
    fun active_box_follows_entered_digits() {
        assertEquals(0, guard_active_digit("", 2))
        assertEquals(1, guard_active_digit("4", 2))
        assertEquals(1, guard_active_digit("42", 2))
    }

    @Test
    fun typing_a_digit_moves_to_the_next_box() {
        show()
        field().performTextInput("4")
        rule.waitForIdle()
        assertEquals("4", current)
        rule.onNodeWithTag("guard_digit_box_0", useUnmergedTree = true).assertExists()
        field().performTextInput("2")
        rule.waitForIdle()
        assertEquals("42", current)
        field().performTextInput("7")
        rule.waitForIdle()
        assertEquals("42", current)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backspace_on_the_empty_second_box_returns_to_the_first() {
        show()
        field().performTextInput("4")
        rule.waitForIdle()
        field().requestFocus()
        field().performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()
        assertEquals("", current)
        field().performTextInput("9")
        rule.waitForIdle()
        assertEquals("9", current)
    }

    @Test
    fun pasting_two_digits_fills_both_boxes() {
        show()
        field().performTextReplacement("42")
        rule.waitForIdle()
        assertEquals("42", current)
    }
}
