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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Exercises the REAL production reply-From resolution (compute_received_on_alias /
// resolve_reply_from_alias in reply_from_resolver.kt, the exact functions
// ComposeScreen calls). The compose harness drives the same rememberSaveable +
// LaunchedEffect state pattern as ComposeScreen using those production functions.
//
@RunWith(AndroidJUnit4::class)
class ReplyFromReceivedAliasTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val primary = "familyownervigkl@astermail.org"
    private val received_alias = "shopdeals@aster.cx"
    private val other_alias = "news@aster.cx"
    private val pinned = "quick.leaf91@astermail.org"
    private val ghost = "ghost.robin7x9q@astermail.org"
    private val options = listOf(primary, received_alias, other_alias, pinned)

    @Test
    fun received_on_alias_beats_pinned_and_primary() {
        val received = compute_received_on_alias(listOf(received_alias), options, primary)
        assertEquals(received_alias, received)
        val from = resolve_reply_from_alias(received, null, pinned, options)
        assertEquals(received_alias, from)
    }

    @Test
    fun prefers_non_primary_when_both_addressed() {
        val received = compute_received_on_alias(listOf(primary, received_alias), options, primary)
        assertEquals(received_alias, received)
    }

    @Test
    fun matches_case_insensitively_and_trims() {
        val received = compute_received_on_alias(listOf("  ShopDeals@ASTER.cx "), options, primary)
        assertEquals(received_alias, received)
    }

    @Test
    fun matches_alias_present_in_cc() {
        val received = compute_received_on_alias(
            listOf("stranger@example.com", other_alias),
            options,
            primary,
        )
        assertEquals(other_alias, received)
    }

    @Test
    fun no_owned_recipient_falls_back_to_pinned() {
        val received = compute_received_on_alias(listOf("someone-else@example.com"), options, primary)
        assertNull(received)
        val from = resolve_reply_from_alias(received, null, pinned, options)
        assertEquals(pinned, from)
    }

    @Test
    fun received_on_primary_stays_primary() {
        val received = compute_received_on_alias(listOf(primary), options, primary)
        assertEquals(primary, received)
        val from = resolve_reply_from_alias(received, null, pinned, options)
        assertEquals(primary, from)
    }

    @Test
    fun ghost_match_used_when_no_received_alias() {
        val ghost_options = options + ghost
        val from = resolve_reply_from_alias(null, ghost, pinned, ghost_options)
        assertEquals(ghost, from)
    }

    @Test
    fun received_alias_beats_ghost_match() {
        val ghost_options = options + ghost
        val from = resolve_reply_from_alias(received_alias, ghost, pinned, ghost_options)
        assertEquals(received_alias, from)
    }

    // Drives the real production resolver through ComposeScreen's actual state
    // pattern: initial value at first composition (thread not yet loaded), then
    // the async thread load supplies the received alias via LaunchedEffect.
    @Composable
    private fun reply_from_harness(
        received_on_alias: String?,
        thread_ghost_match: String?,
    ) {
        var from_alias by rememberSaveable {
            mutableStateOf(resolve_reply_from_alias(received_on_alias, thread_ghost_match, pinned, options))
        }
        var from_manually_selected by rememberSaveable { mutableStateOf(false) }

        LaunchedEffect(received_on_alias, thread_ghost_match) {
            if (from_manually_selected) return@LaunchedEffect
            val resolved = resolve_reply_from_alias(received_on_alias, thread_ghost_match, pinned, options)
            if (resolved.isNotBlank() && resolved != from_alias) from_alias = resolved
        }

        Column {
            Text(from_alias, modifier = Modifier.testTag("from_value"))
            Button(onClick = {
                from_alias = other_alias
                from_manually_selected = true
            }) { Text("pick_other") }
        }
    }

    @Test
    fun harness_resolves_received_alias_after_async_load() {
        var received by mutableStateOf<String?>(null)
        compose_rule.setContent { AsterTheme { reply_from_harness(received, null) } }
        compose_rule.waitForIdle()
        // before the thread loads, nothing owned resolved -> pinned fallback
        compose_rule.onNodeWithTag("from_value").assertTextEquals(pinned)

        compose_rule.runOnUiThread { received = received_alias }
        compose_rule.waitForIdle()
        // async thread load supplies the received alias -> From snaps to it
        compose_rule.onNodeWithTag("from_value").assertTextEquals(received_alias)
    }

    @Test
    fun harness_manual_selection_survives_async_load() {
        var received by mutableStateOf<String?>(null)
        compose_rule.setContent { AsterTheme { reply_from_harness(received, null) } }
        compose_rule.waitForIdle()

        compose_rule.onNodeWithText("pick_other").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("from_value").assertTextEquals(other_alias)

        compose_rule.runOnUiThread { received = received_alias }
        compose_rule.waitForIdle()
        // manual choice must not be overridden by the late received-alias resolve
        compose_rule.onNodeWithTag("from_value").assertTextEquals(other_alias)
    }
}
