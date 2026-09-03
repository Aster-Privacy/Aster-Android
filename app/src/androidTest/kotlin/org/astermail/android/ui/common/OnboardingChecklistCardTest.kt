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

package org.astermail.android.ui.common

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.design.AsterTheme
import org.astermail.android.ui.mail.onboarding_task_destination
import org.astermail.android.ui.mail.onboarding_task_destination_for
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingChecklistCardTest {

    @get:Rule
    val compose_rule = createComposeRule()

    private val all_pending = mapOf(
        "recovery_method" to false,
        "import_mail" to false,
        "install_app" to false,
        "first_email" to false,
    )

    private fun render(tapped: MutableList<String>, tasks: Map<String, Boolean> = all_pending) {
        compose_rule.setContent {
            AsterTheme {
                onboarding_checklist_card(
                    tasks = tasks,
                    on_task = { tapped.add(it) },
                    on_dismiss = {},
                )
            }
        }
    }

    @Test
    fun each_row_reports_its_own_task_key() {
        val tapped = mutableListOf<String>()
        render(tapped)

        compose_rule.onNodeWithText("Add a recovery email address").performClick()
        compose_rule.onNodeWithText("Import your old mail").performClick()
        compose_rule.onNodeWithText("Install Aster on your computer").performClick()
        compose_rule.onNodeWithText("Send your first message").performClick()

        assertEquals(
            listOf("recovery_method", "import_mail", "install_app", "first_email"),
            tapped,
        )
    }

    @Test
    fun no_row_resolves_to_the_settings_screen() {
        val tapped = mutableListOf<String>()
        render(tapped)

        compose_rule.onNodeWithText("Add a recovery email address").performClick()
        compose_rule.onNodeWithText("Import your old mail").performClick()
        compose_rule.onNodeWithText("Install Aster on your computer").performClick()
        compose_rule.onNodeWithText("Send your first message").performClick()

        val destinations = tapped.map { onboarding_task_destination_for(it) }
        assertEquals(
            listOf(
                onboarding_task_destination.recovery_email,
                onboarding_task_destination.import_mail,
                onboarding_task_destination.download_page,
                onboarding_task_destination.compose,
            ),
            destinations,
        )
    }

    @Test
    fun a_completed_row_is_not_clickable() {
        val tapped = mutableListOf<String>()
        render(tapped, all_pending + ("import_mail" to true))

        compose_rule.onNodeWithText("Import your old mail").performClick()

        assertEquals(emptyList<String>(), tapped)
    }
}
