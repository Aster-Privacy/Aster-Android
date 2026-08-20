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

package org.astermail.android.ui.mail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.astermail.android.R
import org.astermail.android.design.AsterTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Reproduces the "tapping an email in select mode does nothing" report: the
// 32dp star control stayed interactive while the inbox was in selection mode,
// so every tap landing in the row's top-right corner toggled a star instead of
// toggling the selection, and the row looked unchanged.
//
@RunWith(AndroidJUnit4::class)
class InboxSelectionTapTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val star_toggles = mutableListOf<String>()
    private val selection_log = mutableListOf<String>()

    private fun sample(id: String) = Email(
        id = id,
        sender_name = "Aster Team",
        sender_email = "hello@astermail.org",
        subject = "Subject $id",
        preview = "Preview body for $id",
        received_at = 1_700_000_000_000L,
        is_read = false,
        is_starred = false,
        has_attachment = false,
    )

    @Composable
    private fun harness(select_mode: Boolean) {
        val selected = remember { mutableStateListOf<String>() }
        val thread = remember { flat_thread_rows(listOf(sample("m1"))).first() }
        Box(modifier = Modifier.testTag("row_host")) {
            ThreadInboxRow(
                modifier = Modifier.fillMaxWidth(),
                thread = thread,
                on_click = {
                    if (selected.contains(thread.thread_id)) {
                        selected.remove(thread.thread_id)
                    } else {
                        selected.add(thread.thread_id)
                    }
                    selection_log.clear()
                    selection_log.addAll(selected)
                },
                on_long_click = {},
                on_toggle_star = { star_toggles.add(thread.thread_id) },
                is_selected = selected.contains(thread.thread_id),
                select_mode = select_mode,
            )
        }
    }

    private fun set_harness(select_mode: Boolean) {
        star_toggles.clear()
        selection_log.clear()
        compose_rule.setContent { AsterTheme { harness(select_mode) } }
        compose_rule.waitForIdle()
    }

    private fun tap_star_center() {
        val star = compose_rule
            .onNodeWithContentDescription(context.getString(R.string.not_starred))
            .fetchSemanticsNode()
        val bounds = star.boundsInRoot
        compose_rule.onNodeWithTag("row_host").performTouchInput {
            click(Offset(bounds.center.x, bounds.center.y))
        }
        compose_rule.waitForIdle()
    }

    //
    // In selection mode a tap on the star control must select the conversation
    // and must not star it.
    //
    @Test
    fun tap_on_star_selects_in_select_mode() {
        set_harness(select_mode = true)
        tap_star_center()
        assertEquals(emptyList<String>(), star_toggles)
        assertEquals(listOf("m1"), selection_log)
    }

    //
    // Outside selection mode the star keeps working exactly as before.
    //
    @Test
    fun tap_on_star_stars_outside_select_mode() {
        set_harness(select_mode = false)
        tap_star_center()
        assertEquals(listOf("m1"), star_toggles)
        assertEquals(emptyList<String>(), selection_log)
    }
}
