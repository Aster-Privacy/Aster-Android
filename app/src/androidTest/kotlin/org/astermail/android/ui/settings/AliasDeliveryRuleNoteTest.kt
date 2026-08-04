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

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.settings.detail.AliasRuleDeliveryNote
import org.astermail.android.ui.settings.detail.alias_delivery_rule_note
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AliasDeliveryRuleNoteTest {

    @get:Rule
    val compose_rule = createComposeRule()

    @Test
    fun matching_rule_names_the_rule_and_its_folder() {
        compose_rule.setContent {
            AsterTheme {
                alias_delivery_rule_note(
                    note = AliasRuleDeliveryNote(
                        rule_name = "Receipts",
                        folder_name = "My Feed",
                        matches_alias_delivery = true,
                    ),
                    selected_label = "My Feed",
                )
            }
        }
        compose_rule.onNodeWithTag("alias_delivery_rule_note").assertIsDisplayed()
        compose_rule.onNodeWithTag("alias_delivery_rule_note").assertTextContains(
            "Mail rule \"Receipts\" already moves mail for this alias to My Feed.",
        )
    }

    @Test
    fun conflicting_rule_names_the_selected_destination_too() {
        compose_rule.setContent {
            AsterTheme {
                alias_delivery_rule_note(
                    note = AliasRuleDeliveryNote(
                        rule_name = "Receipts",
                        folder_name = "My Feed",
                        matches_alias_delivery = false,
                    ),
                    selected_label = "Inbox",
                )
            }
        }
        compose_rule.onNodeWithTag("alias_delivery_rule_note").assertTextContains(
            "Mail rule \"Receipts\" moves mail for this alias to My Feed, so it will not land in Inbox.",
        )
    }
}
