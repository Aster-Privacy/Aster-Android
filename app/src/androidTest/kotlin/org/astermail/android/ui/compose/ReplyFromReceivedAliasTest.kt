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

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Reproduces the compose "From" bug where replying defaulted to the pinned
// sender instead of the alias the original message was received on. The fixed
// resolution mirrors compose_screen.kt: received_on_alias (the user-owned
// address in the original message's To/Cc) takes priority over the pinned
// primary sender.
//
@RunWith(AndroidJUnit4::class)
class ReplyFromReceivedAliasTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val primary = "myfeedreader34@aster.cx"
    private val received_alias = "shopdeals@aster.cx"
    private val other_alias = "news@aster.cx"

    // mirrors the async SettingsViewModel + loaded thread state
    private class State {
        var user_email by mutableStateOf("")
        var aliases by mutableStateOf(listOf<String>())
        var default_sender by mutableStateOf("")
        var reply_to_addresses by mutableStateOf(listOf<String>())
    }

    private fun resolve_options(user_email: String, aliases: List<String>): List<String> {
        val options = mutableListOf<String>()
        if (user_email.isNotBlank()) options.add(user_email)
        aliases.forEach { if (it.isNotBlank() && it !in options) options.add(it) }
        if (options.isEmpty()) options.add("you@astermail.org")
        return options.toList()
    }

    @Composable
    private fun reply_from_field(state: State) {
        val alias_options = resolve_options(state.user_email, state.aliases)
        val primary_sender_email = state.default_sender.takeIf { it.isNotBlank() }
            ?: state.user_email

        val received_on_alias = remember(state.reply_to_addresses, alias_options, state.user_email) {
            val options_by_lower = alias_options.associateBy { it.lowercase() }
            val matches = state.reply_to_addresses
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .mapNotNull { options_by_lower[it.lowercase()] }
            matches.firstOrNull { it.lowercase() != state.user_email.lowercase() } ?: matches.firstOrNull()
        }

        var from_alias by rememberSaveable {
            val initial = received_on_alias
                ?: primary_sender_email.takeIf { it.isNotBlank() && it in alias_options }
                ?: alias_options.firstOrNull().orEmpty()
            mutableStateOf(initial)
        }
        var from_manually_selected by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(alias_options, received_on_alias, primary_sender_email) {
            if (from_manually_selected) return@LaunchedEffect
            val resolved = received_on_alias
                ?: primary_sender_email.takeIf { it.isNotBlank() && it in alias_options }
                ?: alias_options.firstOrNull().orEmpty()
            if (resolved.isNotBlank() && resolved != from_alias) from_alias = resolved
        }

        Column { Text(from_alias, modifier = Modifier.testTag("from_value")) }
    }

    @Test
    fun reply_defaults_to_received_on_alias_not_pinned() {
        val state = State()
        compose_rule.setContent { AsterTheme { reply_from_field(state) } }
        compose_rule.waitForIdle()

        // settings + the replied-to message land: mail was received on received_alias,
        // but the pinned default sender is primary
        compose_rule.runOnUiThread {
            state.user_email = primary
            state.aliases = listOf(received_alias, other_alias)
            state.default_sender = primary
            state.reply_to_addresses = listOf(received_alias)
        }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("from_value").assertTextEquals(received_alias)
    }

    @Test
    fun reply_falls_back_to_pinned_when_no_owned_recipient() {
        val state = State()
        compose_rule.setContent { AsterTheme { reply_from_field(state) } }
        compose_rule.waitForIdle()

        // the original message was addressed to a non-owned address only
        compose_rule.runOnUiThread {
            state.user_email = primary
            state.aliases = listOf(received_alias, other_alias)
            state.default_sender = primary
            state.reply_to_addresses = listOf("someone-else@example.com")
        }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("from_value").assertTextEquals(primary)
    }
}
