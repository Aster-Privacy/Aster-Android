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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.api.settings.AliasInfo
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.alias_list_row
import org.astermail.android.ui.settings.detail.detail_scaffold
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

//
// Regression guard for the 500-600 alias ANR crash (Discord report: opening
// Settings > Aliases with ~600 aliases froze and the app was killed after ~5s).
//
// Root cause: aliases_tab rendered every alias eagerly with forEachIndexed inside
// the detail scaffold's non-lazy verticalScroll Column, so all ~600 heavy rows
// (Text + Switch + two IconButtons each) were composed, measured and laid out on
// the main thread in one pass -> ANR at the 5s watchdog.
//
// Fix: render the alias list with a LazyColumn so only the visible rows compose.
// This test drives the real production row composable (alias_list_row) through a
// LazyColumn built exactly the way aliases_tab now builds it, and proves that a
// 600-item list composes only the on-screen rows (never all 600 at once).
//
@RunWith(AndroidJUnit4::class)
class AliasesLargeListTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val count = 600

    private fun sample_aliases(): List<AliasInfo> =
        (0 until count).map { i ->
            AliasInfo(
                id = "alias-$i",
                encrypted_local_part = "alias.$i",
                domain = "astermail.org",
                is_enabled = i % 4 != 0,
            )
        }

    @Test
    fun lazy_list_of_600_aliases_composes_only_visible_rows() {
        val aliases = sample_aliases()

        compose_rule.setContent {
            AsterTheme {
                val context = LocalContext.current
                LazyColumn(modifier = Modifier.fillMaxSize().testTag("alias_list")) {
                    itemsIndexed(
                        items = aliases,
                        key = { _, alias -> "alias_${alias.id}" },
                    ) { idx, alias ->
                        alias_list_row(
                            alias = alias,
                            idx = idx,
                            last_index = aliases.lastIndex,
                            context = context,
                            on_toggle = {},
                            on_delete = {},
                        )
                    }
                }
            }
        }
        compose_rule.waitForIdle()

        // The first row renders immediately (list is usable, no freeze).
        compose_rule.onNodeWithText("alias.0@astermail.org").assertIsDisplayed()

        // The far-end row is NOT composed on entry - this is what proves the list
        // is lazy. Under the old eager forEachIndexed all 600 composed up front,
        // which is exactly the main-thread work that produced the ANR.
        compose_rule.onNodeWithText("alias.$count@astermail.org").assertDoesNotExist()
        compose_rule.onNodeWithText("alias.599@astermail.org").assertDoesNotExist()

        // Scrolling brings the last row into composition on demand.
        compose_rule.onNodeWithTag("alias_list").performScrollToIndex(count - 1)
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("alias.599@astermail.org").assertIsDisplayed()

        // ...and the early rows are recycled back out of composition.
        compose_rule.onNodeWithText("alias.0@astermail.org").assertDoesNotExist()
    }

    //
    // Same 600 aliases, but rendered through the EXACT production nesting the real
    // AliasesScreen uses: detail_scaffold(scrollable = false) -> Column content ->
    // ScrollableTabRow -> Box(Modifier.weight(1f).fillMaxSize()) -> LazyColumn.
    //
    // This guards the structural half of the fix. A LazyColumn given unbounded
    // height inside a Column throws "infinite max height" at measure time; if the
    // weight/non-scroll-scaffold wiring were wrong this test would crash on entry
    // or (if it fell back to composing everything) the far-end row would already
    // exist without scrolling. Neither happens -> the real nesting is lazy.
    //
    @Test
    fun aliases_screen_nesting_stays_lazy_with_600_aliases() {
        val aliases = sample_aliases()

        compose_rule.setContent {
            AsterTheme {
                val context = LocalContext.current
                detail_scaffold(title = "Aliases", on_back = {}, scrollable = false) {
                    ScrollableTabRow(selectedTabIndex = 0) {
                        listOf("Aliases", "Custom domains", "Directories").forEachIndexed { i, label ->
                            Tab(selected = i == 0, onClick = {}, text = { Text(label) })
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                        LazyColumn(modifier = Modifier.fillMaxSize().testTag("alias_list")) {
                            itemsIndexed(
                                items = aliases,
                                key = { _, alias -> "alias_${alias.id}" },
                            ) { idx, alias ->
                                alias_list_row(
                                    alias = alias,
                                    idx = idx,
                                    last_index = aliases.lastIndex,
                                    context = context,
                                    on_toggle = {},
                                    on_delete = {},
                                )
                            }
                        }
                    }
                }
            }
        }
        compose_rule.waitForIdle()

        // Screen renders (did not crash on the nested LazyColumn) and the first
        // row is visible.
        compose_rule.onNodeWithText("alias.0@astermail.org").assertIsDisplayed()

        // The far row is not composed up front -> lazy inside the real nesting.
        compose_rule.onNodeWithText("alias.599@astermail.org").assertDoesNotExist()

        // Scrolling the list inside the weighted Box still works.
        compose_rule.onNodeWithTag("alias_list").performScrollToIndex(count - 1)
        compose_rule.waitForIdle()
        compose_rule.onNodeWithText("alias.599@astermail.org").assertIsDisplayed()
    }
}
