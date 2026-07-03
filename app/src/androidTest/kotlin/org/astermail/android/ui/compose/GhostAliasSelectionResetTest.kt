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
import androidx.compose.material3.Button
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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Reproduces the compose "From" sender reset bug: generating a ghost alias
// selects it, then the async settings loads (profile/aliases/prefs fired by
// LaunchedEffect(Unit) on compose entry, plus the load_aliases() reload after
// ghost creation) land ~2s later and used to reset the selection back to the
// pinned sender. See compose_screen.kt from_alias handling.
//
@RunWith(AndroidJUnit4::class)
class GhostAliasSelectionResetTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val ghost = "ghost.robin@astermail.org"
    private val pinned = "quick.leaf91@astermail.org"
    private val primary = "ghosttestj702q@astermail.org"

    // mirrors the state that lands asynchronously in the real SettingsViewModel
    private class Settings {
        var user_email by mutableStateOf("")
        var aliases by mutableStateOf(listOf<String>())
        var default_sender by mutableStateOf("")
    }

    private fun resolve_options(user_email: String, aliases: List<String>): List<String> {
        val options = mutableListOf<String>()
        if (user_email.isNotBlank()) options.add(user_email)
        aliases.forEach { if (it.isNotBlank() && it !in options) options.add(it) }
        if (options.isEmpty()) options.add("you@astermail.org")
        return options.toList()
    }

    // The FIXED construct now used in compose_screen.kt: a stable from_alias plus
    // a manual-override flag; the initial resolution runs in a LaunchedEffect that
    // bails out once the user has explicitly chosen a sender.
    @Composable
    private fun fixed_from_field(settings: Settings) {
        val alias_options = resolve_options(settings.user_email, settings.aliases)
        val primary_sender_email = settings.default_sender.takeIf { it.isNotBlank() }
            ?: settings.user_email

        var from_alias by rememberSaveable {
            val initial = primary_sender_email.takeIf { it.isNotBlank() && it in alias_options }
                ?: alias_options.firstOrNull().orEmpty()
            mutableStateOf(initial)
        }
        var from_manually_selected by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(alias_options, primary_sender_email) {
            if (from_manually_selected) return@LaunchedEffect
            val resolved = primary_sender_email.takeIf { it.isNotBlank() && it in alias_options }
                ?: alias_options.firstOrNull().orEmpty()
            if (resolved.isNotBlank() && resolved != from_alias) from_alias = resolved
        }

        Column {
            Text(from_alias, modifier = Modifier.testTag("from_value"))
            Button(onClick = {
                from_alias = ghost
                from_manually_selected = true
            }) { Text("gen_ghost") }
        }
    }

    // The OLD (buggy) construct: from_alias held by a keyed remember that
    // re-initialises whenever alias_options / primary_sender_email change, which
    // is exactly what the async settings load triggers.
    @Composable
    private fun buggy_from_field(settings: Settings) {
        val alias_options = resolve_options(settings.user_email, settings.aliases)
        val primary_sender_email = settings.default_sender.takeIf { it.isNotBlank() }
            ?: settings.user_email

        var from_alias by remember(alias_options, primary_sender_email) {
            val initial = primary_sender_email.takeIf { it.isNotBlank() && it in alias_options }
                ?: alias_options.firstOrNull().orEmpty()
            mutableStateOf(initial)
        }

        Column {
            Text(from_alias, modifier = Modifier.testTag("from_value"))
            Button(onClick = { from_alias = ghost }) { Text("gen_ghost") }
        }
    }

    @Test
    fun fixed_keeps_ghost_selection_after_async_settings_load() {
        val settings = Settings()
        compose_rule.setContent { AsterTheme { fixed_from_field(settings) } }
        compose_rule.waitForIdle()

        // user generates a ghost alias in the From sheet
        compose_rule.onNodeWithText("gen_ghost").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("from_value").assertTextEquals(ghost)

        // the deferred settings loads (incl. the pinned default sender) land
        compose_rule.runOnUiThread {
            settings.user_email = primary
            settings.aliases = listOf(pinned)
            settings.default_sender = pinned
        }
        compose_rule.waitForIdle()

        // the ghost selection must survive
        compose_rule.onNodeWithTag("from_value").assertTextEquals(ghost)
    }

    @Test
    fun old_pattern_reproduces_the_reset_to_pinned_sender() {
        val settings = Settings()
        compose_rule.setContent { AsterTheme { buggy_from_field(settings) } }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("gen_ghost").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("from_value").assertTextEquals(ghost)

        compose_rule.runOnUiThread {
            settings.user_email = primary
            settings.aliases = listOf(pinned)
            settings.default_sender = pinned
        }
        compose_rule.waitForIdle()

        // demonstrates the bug: the keyed remember discards the ghost and snaps
        // back to the pinned sender
        compose_rule.onNodeWithTag("from_value").assertTextEquals(pinned)
    }
}
