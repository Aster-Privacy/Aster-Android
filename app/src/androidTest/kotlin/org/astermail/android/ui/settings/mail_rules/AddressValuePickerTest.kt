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

package org.astermail.android.ui.settings.mail_rules

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.ui.settings.mail_rules.pickers.address_value_picker
import org.astermail.android.ui.settings.mail_rules.pickers.alias_option
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddressValuePickerTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val aliases = listOf(
        alias_option(address = "shop@astermail.org", display_name = "Online orders"),
        alias_option(address = "news@astermail.org", display_name = null),
        alias_option(address = "bank@astermail.org", display_name = null),
    )

    private fun field(tag: String): SemanticsNodeInteraction = compose_rule.onNode(
        hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)),
    )

    private fun show(
        initial: String = "",
        is_regex: Boolean = false,
        show_aliases: Boolean = true,
        options: List<alias_option> = aliases,
        loading: Boolean = false,
        on_confirm: (List<String>, Boolean) -> Unit = { _, _ -> },
    ) {
        compose_rule.setContent {
            address_value_picker(
                on_dismiss = {},
                title = "Enter value",
                initial = initial,
                case_sensitive = false,
                is_regex = is_regex,
                show_aliases = show_aliases,
                aliases = options,
                aliases_loading = loading,
                on_confirm = on_confirm,
            )
        }
    }

    @Test
    fun every_alias_is_listed_with_a_search_field() {
        show()

        compose_rule.onNodeWithTag("alias_search").assertIsDisplayed()
        aliases.forEach { compose_rule.onNodeWithTag("alias_${it.address}").assertIsDisplayed() }
    }

    @Test
    fun searching_narrows_the_list_to_matching_aliases() {
        show()

        field("alias_search").performTextInput("bank")
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("alias_bank@astermail.org").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_shop@astermail.org").assertDoesNotExist()
        compose_rule.onNodeWithTag("alias_news@astermail.org").assertDoesNotExist()
    }

    @Test
    fun searching_by_display_name_finds_the_alias() {
        show()

        field("alias_search").performTextInput("orders")
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("alias_shop@astermail.org").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_bank@astermail.org").assertDoesNotExist()
    }

    @Test
    fun a_search_with_no_results_explains_itself() {
        show()

        field("alias_search").performTextInput("nothing-here")
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("alias_no_matches").assertIsDisplayed()
    }

    @Test
    fun picking_several_aliases_confirms_all_of_them() {
        var confirmed: List<String> = emptyList()
        show(on_confirm = { values, _ -> confirmed = values })

        compose_rule.onNodeWithTag("alias_shop@astermail.org").performClick()
        compose_rule.onNodeWithTag("alias_bank@astermail.org").performClick()
        compose_rule.onNodeWithTag("alias_selected_count").assertIsDisplayed()
        compose_rule.onNodeWithTag("confirm_value").performClick()
        compose_rule.waitForIdle()

        assertEquals(
            listOf("shop@astermail.org", "bank@astermail.org"),
            confirmed.filter { it.isNotBlank() },
        )
    }

    @Test
    fun a_search_does_not_drop_aliases_picked_earlier() {
        var confirmed: List<String> = emptyList()
        show(on_confirm = { values, _ -> confirmed = values })

        compose_rule.onNodeWithTag("alias_shop@astermail.org").performClick()
        field("alias_search").performTextInput("bank")
        compose_rule.waitForIdle()
        compose_rule.onNodeWithTag("alias_bank@astermail.org").performClick()
        compose_rule.onNodeWithTag("confirm_value").performClick()
        compose_rule.waitForIdle()

        assertEquals(
            listOf("shop@astermail.org", "bank@astermail.org"),
            confirmed.filter { it.isNotBlank() },
        )
    }

    @Test
    fun tapping_a_picked_alias_again_removes_it() {
        var confirmed: List<String> = emptyList()
        show(on_confirm = { values, _ -> confirmed = values })

        compose_rule.onNodeWithTag("alias_shop@astermail.org").performClick()
        compose_rule.onNodeWithTag("alias_shop@astermail.org").performClick()
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("alias_selected_count").assertDoesNotExist()

        compose_rule.onNodeWithTag("confirm_value").performClick()
        compose_rule.waitForIdle()

        assertTrue(confirmed.none { it.isNotBlank() })
    }

    @Test
    fun a_typed_address_is_confirmed_alongside_picked_aliases() {
        var confirmed: List<String> = emptyList()
        show(on_confirm = { values, _ -> confirmed = values })

        compose_rule.onNodeWithTag("alias_news@astermail.org").performClick()
        field("value_input").performTextInput("outside@example.com")
        compose_rule.onNodeWithTag("confirm_value").performClick()
        compose_rule.waitForIdle()

        assertEquals(
            listOf("news@astermail.org", "outside@example.com"),
            confirmed.filter { it.isNotBlank() },
        )
    }

    @Test
    fun an_existing_value_that_matches_an_alias_starts_out_picked() {
        var confirmed: List<String> = emptyList()
        show(initial = "news@astermail.org", on_confirm = { values, _ -> confirmed = values })

        compose_rule.onNodeWithTag("alias_selected_count").assertIsDisplayed()
        compose_rule.onNodeWithTag("confirm_value").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("news@astermail.org"), confirmed.filter { it.isNotBlank() })
    }

    @Test
    fun an_existing_value_that_is_not_an_alias_stays_in_the_text_field() {
        var confirmed: List<String> = emptyList()
        show(initial = "outside@example.com", on_confirm = { values, _ -> confirmed = values })

        compose_rule.onNodeWithTag("alias_selected_count").assertDoesNotExist()
        compose_rule.onNodeWithTag("confirm_value").performClick()
        compose_rule.waitForIdle()

        assertEquals(listOf("outside@example.com"), confirmed.filter { it.isNotBlank() })
    }

    @Test
    fun the_alias_list_is_hidden_for_operators_that_take_a_pattern() {
        show(show_aliases = false)

        compose_rule.onNodeWithTag("alias_search").assertDoesNotExist()
        compose_rule.onNodeWithTag("value_input").assertIsDisplayed()
    }

    @Test
    fun an_account_with_no_aliases_says_so() {
        show(options = emptyList())

        compose_rule.onNodeWithTag("alias_empty").assertIsDisplayed()
    }

    @Test
    fun aliases_still_loading_show_progress_instead_of_an_empty_state() {
        show(options = emptyList(), loading = true)

        compose_rule.onNodeWithTag("alias_loading").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_empty").assertDoesNotExist()
    }

    @Test
    fun an_invalid_pattern_blocks_confirmation() {
        show(is_regex = true, show_aliases = false)

        field("value_input").performTextInput("[unclosed")
        compose_rule.waitForIdle()

        compose_rule.onNodeWithTag("regex_error").assertIsDisplayed()
        compose_rule.onNodeWithTag("confirm_value").assertIsNotEnabled()
    }
}
