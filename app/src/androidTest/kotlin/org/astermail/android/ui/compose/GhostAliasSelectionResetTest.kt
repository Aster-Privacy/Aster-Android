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

@RunWith(AndroidJUnit4::class)
class GhostAliasSelectionResetTest {
    @get:Rule
    val compose_rule = createComposeRule()

    private val ghost = "ghost.robin@astermail.org"
    private val pinned = "quick.leaf91@astermail.org"
    private val primary = "primary.owner@astermail.org"

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

        compose_rule.onNodeWithText("gen_ghost").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("from_value").assertTextEquals(ghost)

        compose_rule.runOnUiThread {
            settings.user_email = primary
            settings.aliases = listOf(pinned)
            settings.default_sender = pinned
        }
        compose_rule.waitForIdle()

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

        compose_rule.onNodeWithTag("from_value").assertTextEquals(pinned)
    }
}
