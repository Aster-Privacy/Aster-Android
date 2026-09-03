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

package org.astermail.android.mail

import org.astermail.android.ui.common.onboarding_checklist_order
import org.astermail.android.ui.mail.onboarding_task_destination
import org.astermail.android.ui.mail.onboarding_task_destination_for
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingTaskDestinationTest {

    @Test
    fun recovery_row_opens_the_recovery_email_screen() {
        assertEquals(
            onboarding_task_destination.recovery_email,
            onboarding_task_destination_for("recovery_method"),
        )
    }

    @Test
    fun import_row_opens_the_import_screen() {
        assertEquals(
            onboarding_task_destination.import_mail,
            onboarding_task_destination_for("import_mail"),
        )
    }

    @Test
    fun install_row_opens_the_download_page() {
        assertEquals(
            onboarding_task_destination.download_page,
            onboarding_task_destination_for("install_app"),
        )
    }

    @Test
    fun first_email_row_opens_compose() {
        assertEquals(
            onboarding_task_destination.compose,
            onboarding_task_destination_for("first_email"),
        )
    }

    @Test
    fun no_rendered_row_falls_back_to_settings() {
        onboarding_checklist_order.forEach { key ->
            assertNotEquals(
                "checklist row $key must not open settings",
                onboarding_task_destination.settings,
                onboarding_task_destination_for(key),
            )
        }
    }

    @Test
    fun every_rendered_row_has_its_own_destination() {
        val destinations = onboarding_checklist_order.map { onboarding_task_destination_for(it) }
        assertEquals(onboarding_checklist_order.size, destinations.toSet().size)
    }

    @Test
    fun an_unknown_key_still_falls_back_to_settings() {
        assertEquals(
            onboarding_task_destination.settings,
            onboarding_task_destination_for("something_the_server_added_later"),
        )
    }

    @Test
    fun the_checklist_covers_the_four_setup_tasks() {
        assertEquals(4, onboarding_checklist_order.size)
        assertTrue(onboarding_checklist_order.containsAll(
            listOf("recovery_method", "import_mail", "install_app", "first_email"),
        ))
    }
}
