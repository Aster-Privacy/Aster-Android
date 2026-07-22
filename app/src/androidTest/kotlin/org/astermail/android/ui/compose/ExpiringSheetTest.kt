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

package org.astermail.android.ui.compose

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpiringSheetTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private data class PickResult(val expires_epoch_ms: Long, val label: String, val password: String?)

    private fun render(on_pick: (PickResult) -> Unit) {
        compose_rule.setContent {
            AsterTheme {
                var visible by remember { mutableStateOf(true) }
                if (visible) {
                    ExpiringSheet(
                        on_close = { visible = false },
                        on_pick = { expires_epoch_ms, label, password ->
                            on_pick(PickResult(expires_epoch_ms, label, password))
                            visible = false
                        },
                    )
                }
            }
        }
    }

    private fun assert_epoch_close(expected_offset_ms: Long, actual: Long?) {
        val now = System.currentTimeMillis()
        val delta = Math.abs((actual ?: 0L) - (now + expected_offset_ms))
        if (delta > 120_000L) {
            throw AssertionError("expiry epoch off by ${delta}ms from expected offset $expected_offset_ms")
        }
    }

    @Test
    fun picking_a_duration_does_not_commit_or_close() {
        var picked: PickResult? = null
        render { picked = it }

        compose_rule.onNodeWithText("Accept").assertIsNotEnabled()
        compose_rule.onNodeWithText("Expires in 1 day").performClick()

        compose_rule.waitForIdle()
        assertNull("selecting a duration must not commit until Accept", picked)
        compose_rule.onNodeWithText("Expires in 1 day").assertIsDisplayed()
        compose_rule.onNodeWithText("Accept").assertIsEnabled()
    }

    @Test
    fun accept_commits_selected_duration_and_password_together() {
        var picked: PickResult? = null
        render { picked = it }

        compose_rule.onNodeWithTag("expiry_password_field").performTextInput("hunter2")
        compose_rule.onNodeWithText("Expires in 1 hour").performClick()
        compose_rule.onNodeWithText("Accept").performClick()

        compose_rule.waitForIdle()
        val result = picked
        assert_epoch_close(3_600_000L, result?.expires_epoch_ms)
        assertEquals("hunter2", result?.password)
    }

    @Test
    fun accept_without_password_passes_null() {
        var picked: PickResult? = null
        render { picked = it }

        compose_rule.onNodeWithText("Expires in 7 days").performClick()
        compose_rule.onNodeWithText("Accept").performClick()

        compose_rule.waitForIdle()
        assert_epoch_close(7L * 24L * 3_600_000L, picked?.expires_epoch_ms)
        assertNull(picked?.password)
    }
}
