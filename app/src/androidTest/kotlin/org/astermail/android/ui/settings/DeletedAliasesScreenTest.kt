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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.astermail.android.settings.DecryptedDeletedAlias
import org.astermail.android.ui.settings.detail.deleted_aliases_screen
import org.astermail.android.ui.settings.detail.recently_deleted_entry_row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeletedAliasesScreenTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val restored = mutableListOf<String>()
    private val purged = mutableListOf<String>()
    private var emptied = 0
    private var upgraded = 0
    private var backed = 0
    private var opened = 0

    private fun alias(id: String, address: String) = DecryptedDeletedAlias(
        id = id,
        address = address,
        display_name = null,
        deleted_at = "2026-08-30T10:15:00Z",
    )

    private fun render_entry_row(count: Int) {
        compose_rule.setContent {
            AsterTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    recently_deleted_entry_row(count = count, on_click = { opened++ })
                }
            }
        }
        compose_rule.waitForIdle()
    }

    private fun render_screen(
        deleted: List<DecryptedDeletedAlias>,
        restore_locked: Boolean = false,
    ) {
        compose_rule.setContent {
            AsterTheme {
                deleted_aliases_screen(
                    deleted = deleted,
                    restore_locked = restore_locked,
                    on_restore = { restored += it },
                    on_purge = { purged += it },
                    on_empty = { emptied++ },
                    on_upgrade = { upgraded++ },
                    on_back = { backed++ },
                )
            }
        }
        compose_rule.waitForIdle()
    }

    @Test
    fun the_entry_row_shows_how_many_aliases_wait_in_the_bin_and_opens_it() {
        render_entry_row(count = 3)

        compose_rule.onNodeWithText("Recently deleted").assertIsDisplayed()
        compose_rule.onNodeWithText("(3)").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_recently_deleted_entry").performClick()
        compose_rule.waitForIdle()

        assertEquals(1, opened)
    }

    @Test
    fun the_bin_lists_every_deleted_alias_without_scrolling_past_the_live_ones() {
        render_screen(listOf(alias("a1", "first@aster.cx"), alias("a2", "second@aster.cx")))

        compose_rule.onNodeWithText("first@aster.cx").assertIsDisplayed()
        compose_rule.onNodeWithText("second@aster.cx").assertIsDisplayed()
    }

    @Test
    fun an_empty_bin_says_so_instead_of_showing_nothing() {
        render_screen(emptyList())

        compose_rule.onNodeWithText("No deleted aliases.").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_deleted_empty_all").assertDoesNotExist()
    }

    @Test
    fun restore_reports_the_alias_the_row_belongs_to() {
        render_screen(listOf(alias("a1", "first@aster.cx"), alias("a2", "second@aster.cx")))

        compose_rule.onAllNodesWithText("Restore")[1].performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("a2"), restored)
    }

    @Test
    fun a_permanent_delete_asks_before_it_happens() {
        render_screen(listOf(alias("a1", "first@aster.cx")))

        compose_rule.onNodeWithContentDescription("Delete permanently").performClick()
        compose_rule.waitForIdle()

        assertTrue(purged.isEmpty())

        compose_rule.onAllNodesWithText("Delete permanently")[0].performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("a1"), purged)
    }

    @Test
    fun emptying_the_whole_bin_asks_before_it_happens() {
        render_screen(listOf(alias("a1", "first@aster.cx")))

        compose_rule.onNodeWithTag("alias_deleted_empty_all").performClick()
        compose_rule.waitForIdle()

        assertEquals(0, emptied)

        compose_rule.onNodeWithText("Delete all").performClick()
        compose_rule.waitForIdle()

        assertEquals(1, emptied)
    }

    @Test
    fun a_locked_plan_offers_an_upgrade_instead_of_a_restore() {
        render_screen(listOf(alias("a1", "first@aster.cx")), restore_locked = true)

        compose_rule.onNodeWithText("Restore").assertDoesNotExist()
        compose_rule.onNodeWithTag("alias_deleted_empty_all").assertDoesNotExist()
        compose_rule.onNodeWithText("Upgrade").performClick()
        compose_rule.waitForIdle()

        assertEquals(1, upgraded)
    }
}
