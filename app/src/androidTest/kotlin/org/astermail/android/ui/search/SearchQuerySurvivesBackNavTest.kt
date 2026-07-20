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

package org.astermail.android.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
// Reproduces the "search results lost on back" bug: SearchScreen held its
// query (free_text / operator_chips / active_filter) in plain remember state.
// Navigation-compose keeps each destination's back stack entry inside a
// SaveableStateHolder and DISPOSES the search composition while mail_detail
// is on top, so on back the plain remember state re-initialised to empty and
// the results list vanished. The fix moves the query into rememberSaveable
// (with a listSaver for the operator chips), which the SaveableStateHolder
// preserves across the disposal. See search_screen.kt.
//
@RunWith(AndroidJUnit4::class)
class SearchQuerySurvivesBackNavTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private data class TestOperator(
        val negated: Boolean,
        val key: String,
        val value: String,
    )

    private val chips_saver = listSaver<List<TestOperator>, String>(
        save = { chips ->
            chips.map { "${if (it.negated) "1" else "0"}|${it.key}|${it.value}" }
        },
        restore = { saved ->
            saved.map {
                val parts = it.split("|", limit = 3)
                TestOperator(parts[0] == "1", parts[1], parts[2])
            }
        },
    )

    private val typed_query = "github"
    private val chip = TestOperator(true, "from", "ci|bot alerts")
    private val filter = "Unread"

    @Composable
    private fun fixed_search_state() {
        var free_text by rememberSaveable("") { mutableStateOf("") }
        var operator_chips by rememberSaveable("", stateSaver = chips_saver) {
            mutableStateOf(emptyList<TestOperator>())
        }
        var active_filter by rememberSaveable { mutableStateOf<String?>(null) }
        search_state_body(
            free_text, operator_chips, active_filter,
            { free_text = it }, { operator_chips = it }, { active_filter = it },
        )
    }

    @Composable
    private fun buggy_search_state() {
        var free_text by remember("") { mutableStateOf("") }
        var operator_chips by remember("") { mutableStateOf(emptyList<TestOperator>()) }
        var active_filter by remember { mutableStateOf<String?>(null) }
        search_state_body(
            free_text, operator_chips, active_filter,
            { free_text = it }, { operator_chips = it }, { active_filter = it },
        )
    }

    @Composable
    private fun search_state_body(
        free_text: String,
        operator_chips: List<TestOperator>,
        active_filter: String?,
        set_text: (String) -> Unit,
        set_chips: (List<TestOperator>) -> Unit,
        set_filter: (String?) -> Unit,
    ) {
        Column {
            Text(free_text, modifier = Modifier.testTag("free_text"))
            Text(
                operator_chips.joinToString(";") { "${it.negated},${it.key},${it.value}" },
                modifier = Modifier.testTag("chips"),
            )
            Text(active_filter.orEmpty(), modifier = Modifier.testTag("filter"))
            Button(onClick = {
                set_text(typed_query)
                set_chips(listOf(chip))
                set_filter(filter)
            }) { Text("type_query") }
        }
    }

    @Composable
    private fun nav_harness(search_content: @Composable () -> Unit) {
        val holder = rememberSaveableStateHolder()
        var on_detail by remember { mutableStateOf(false) }
        Column {
            if (!on_detail) {
                holder.SaveableStateProvider("search") { search_content() }
            } else {
                Text("mail_detail", modifier = Modifier.testTag("detail"))
            }
            Button(onClick = { on_detail = true }) { Text("open_email") }
            Button(onClick = { on_detail = false }) { Text("back") }
        }
    }

    private fun type_open_and_go_back() {
        compose_rule.onNodeWithText("type_query").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("free_text").assertTextEquals(typed_query)

        compose_rule.onNodeWithText("open_email").performClick()
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("detail").assertTextEquals("mail_detail")

        compose_rule.onNodeWithText("back").performClick()
        compose_rule.waitForIdle()
    }

    @Test
    fun fixed_query_chips_and_filter_survive_open_email_and_back() {
        compose_rule.setContent { AsterTheme { nav_harness { fixed_search_state() } } }
        compose_rule.waitForIdle()

        type_open_and_go_back()

        compose_rule.onNodeWithTag("free_text").assertTextEquals(typed_query)
        compose_rule.onNodeWithTag("chips")
            .assertTextEquals("${chip.negated},${chip.key},${chip.value}")
        compose_rule.onNodeWithTag("filter").assertTextEquals(filter)
    }

    @Test
    fun old_pattern_reproduces_the_query_loss_on_back() {
        compose_rule.setContent { AsterTheme { nav_harness { buggy_search_state() } } }
        compose_rule.waitForIdle()

        type_open_and_go_back()

        compose_rule.onNodeWithTag("free_text").assertTextEquals("")
        compose_rule.onNodeWithTag("chips").assertTextEquals("")
        compose_rule.onNodeWithTag("filter").assertTextEquals("")
    }
}
