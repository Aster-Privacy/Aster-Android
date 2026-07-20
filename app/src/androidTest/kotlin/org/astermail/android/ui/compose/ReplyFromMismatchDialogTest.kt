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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Exercises the REAL production guard (reply_from_mismatch in
// reply_from_resolver.kt) wired through the same dialog pattern
// ComposeScreen uses: do_send gate + AsterDialog with Cancel /
// Send anyway / Use received address text actions.
//
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@RunWith(AndroidJUnit4::class)
class ReplyFromMismatchDialogTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val primary = "me@astermail.org"
    private val received_alias = "testing7363672g@aster.cx"

    @Composable
    private fun guard_harness(
        mode: String,
        received_on_alias: String?,
        initial_from: String,
        on_send: (String) -> Unit,
    ) {
        var from_alias by remember { mutableStateOf(initial_from) }
        var show_dialog by remember { mutableStateOf(false) }

        fun do_send(skip_from_guard: Boolean = false) {
            if (!skip_from_guard && reply_from_mismatch(mode, received_on_alias, from_alias)) {
                show_dialog = true
                return
            }
            on_send(from_alias)
        }

        Column {
            Text(from_alias, modifier = Modifier.testTag("from_value"))
            Button(
                modifier = Modifier.testTag("send"),
                onClick = { do_send() },
            ) { Text("send") }
        }

        if (show_dialog) {
            val received_address = received_on_alias.orEmpty()
            org.astermail.android.design.components.AsterDialog(
                on_dismiss = { show_dialog = false },
                title = "Reply from a different address?",
                message = "received on $received_address, sending from $from_alias",
                footer = {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    ) {
                        TextButton(
                            modifier = Modifier.testTag("mismatch_cancel"),
                            onClick = { show_dialog = false },
                        ) { Text("Cancel") }
                        TextButton(
                            modifier = Modifier.testTag("mismatch_send_anyway"),
                            onClick = {
                                show_dialog = false
                                do_send(skip_from_guard = true)
                            },
                        ) { Text("Send anyway") }
                        TextButton(
                            modifier = Modifier.testTag("mismatch_use_received"),
                            onClick = {
                                show_dialog = false
                                if (received_address.isNotBlank()) from_alias = received_address
                                do_send(skip_from_guard = true)
                            },
                        ) { Text("Use received address") }
                    }
                },
            )
        }
    }

    @Test
    fun mismatched_reply_blocks_send_and_shows_dialog() {
        var sent: String? = null
        compose_rule.setContent {
            AsterTheme {
                guard_harness("reply", received_alias, primary, on_send = { sent = it })
            }
        }
        compose_rule.onNodeWithTag("send").performClick()
        compose_rule.waitForIdle()

        assertNull(sent)
        compose_rule.onNodeWithText("Reply from a different address?").assertExists()
        compose_rule.onNodeWithTag("mismatch_cancel").assertExists()
        compose_rule.onNodeWithTag("mismatch_send_anyway").assertExists()
        compose_rule.onNodeWithTag("mismatch_use_received").assertExists()
    }

    @Test
    fun cancel_closes_dialog_without_sending() {
        var sent: String? = null
        compose_rule.setContent {
            AsterTheme {
                guard_harness("reply", received_alias, primary, on_send = { sent = it })
            }
        }
        compose_rule.onNodeWithTag("send").performClick()
        compose_rule.onNodeWithTag("mismatch_cancel").performClick()
        compose_rule.waitForIdle()

        assertNull(sent)
        compose_rule.onNodeWithText("Reply from a different address?").assertDoesNotExist()
    }

    @Test
    fun use_received_switches_from_and_sends() {
        var sent: String? = null
        compose_rule.setContent {
            AsterTheme {
                guard_harness("reply", received_alias, primary, on_send = { sent = it })
            }
        }
        compose_rule.onNodeWithTag("send").performClick()
        compose_rule.onNodeWithTag("mismatch_use_received").performClick()
        compose_rule.waitForIdle()

        assertEquals(received_alias, sent)
        compose_rule.onNodeWithTag("from_value").assertTextEquals(received_alias)
    }

    @Test
    fun send_anyway_sends_from_selected_address() {
        var sent: String? = null
        compose_rule.setContent {
            AsterTheme {
                guard_harness("reply", received_alias, primary, on_send = { sent = it })
            }
        }
        compose_rule.onNodeWithTag("send").performClick()
        compose_rule.onNodeWithTag("mismatch_send_anyway").performClick()
        compose_rule.waitForIdle()

        assertEquals(primary, sent)
    }

    @Test
    fun matching_from_sends_without_dialog() {
        var sent: String? = null
        compose_rule.setContent {
            AsterTheme {
                guard_harness("reply", received_alias, received_alias, on_send = { sent = it })
            }
        }
        compose_rule.onNodeWithTag("send").performClick()
        compose_rule.waitForIdle()

        assertEquals(received_alias, sent)
        compose_rule.onNodeWithText("Reply from a different address?").assertDoesNotExist()
    }

    @Test
    fun forward_mode_never_guards() {
        var sent: String? = null
        compose_rule.setContent {
            AsterTheme {
                guard_harness("forward", received_alias, primary, on_send = { sent = it })
            }
        }
        compose_rule.onNodeWithTag("send").performClick()
        compose_rule.waitForIdle()

        assertEquals(primary, sent)
    }
}
