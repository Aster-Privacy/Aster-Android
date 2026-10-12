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
import org.astermail.android.ui.common.tutorial_list_is_active
import org.astermail.android.ui.common.tutorial_list_progress
import org.astermail.android.ui.common.tutorial_list_progress_of
import org.astermail.android.ui.common.tutorial_list_rows
import org.astermail.android.ui.common.tutorial_list_should_mark_seen
import org.astermail.android.ui.common.tutorial_list_visible
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialListStateTest {

    private val all_pending = mapOf(
        "recovery_method" to false,
        "import_mail" to false,
        "install_app" to false,
        "first_email" to false,
    )

    private val all_done = all_pending.mapValues { true }

    @Test
    fun rows_follow_the_tutorial_order() {
        assertEquals(
            listOf("import_mail", "first_email", "recovery_method", "install_app"),
            onboarding_checklist_order,
        )
        assertEquals(onboarding_checklist_order, tutorial_list_rows(all_pending).map { it.key })
    }

    @Test
    fun rows_skip_tasks_the_server_did_not_send_and_ignore_unknown_keys() {
        val rows = tutorial_list_rows(mapOf("first_email" to true, "something_new" to false))

        assertEquals(listOf("first_email"), rows.map { it.key })
        assertTrue(rows.single().done)
    }

    @Test
    fun progress_counts_done_and_remaining_tasks() {
        val progress = tutorial_list_progress_of(all_pending + ("import_mail" to true))

        assertEquals(1, progress.done)
        assertEquals(4, progress.total)
        assertEquals(3, progress.remaining)
        assertFalse(progress.all_done)
        assertEquals(0.25f, progress.fraction, 0.0001f)
    }

    @Test
    fun progress_is_complete_when_every_task_is_done() {
        val progress = tutorial_list_progress_of(all_done)

        assertEquals(0, progress.remaining)
        assertTrue(progress.all_done)
        assertEquals(1f, progress.fraction, 0.0001f)
    }

    @Test
    fun progress_without_tasks_is_never_complete() {
        val progress = tutorial_list_progress_of(emptyMap())

        assertEquals(0, progress.total)
        assertFalse(progress.all_done)
        assertEquals(0f, progress.fraction, 0.0001f)
    }

    @Test
    fun list_is_inactive_until_loaded_and_after_dismissal() {
        val progress = tutorial_list_progress_of(all_pending)

        assertFalse(tutorial_list_is_active(is_loaded = false, is_dismissed = false, progress = progress))
        assertFalse(tutorial_list_is_active(is_loaded = true, is_dismissed = true, progress = progress))
        assertTrue(tutorial_list_is_active(is_loaded = true, is_dismissed = false, progress = progress))
    }

    @Test
    fun list_is_inactive_without_any_task() {
        assertFalse(
            tutorial_list_is_active(
                is_loaded = true,
                is_dismissed = false,
                progress = tutorial_list_progress(done = 0, total = 0),
            ),
        )
    }

    @Test
    fun incomplete_list_is_visible_and_marks_the_device_as_seen() {
        val progress = tutorial_list_progress_of(all_pending)

        assertTrue(tutorial_list_visible(is_active = true, progress = progress, seen_incomplete = false))
        assertTrue(tutorial_list_should_mark_seen(is_active = true, progress = progress, seen_incomplete = false))
        assertFalse(tutorial_list_should_mark_seen(is_active = true, progress = progress, seen_incomplete = true))
    }

    @Test
    fun completed_list_stays_visible_only_when_it_was_seen_incomplete() {
        val progress = tutorial_list_progress_of(all_done)

        assertTrue(tutorial_list_visible(is_active = true, progress = progress, seen_incomplete = true))
        assertFalse(tutorial_list_visible(is_active = true, progress = progress, seen_incomplete = false))
        assertFalse(tutorial_list_should_mark_seen(is_active = true, progress = progress, seen_incomplete = false))
    }

    @Test
    fun inactive_list_is_never_visible_or_marked() {
        val progress = tutorial_list_progress_of(all_pending)

        assertFalse(tutorial_list_visible(is_active = false, progress = progress, seen_incomplete = true))
        assertFalse(tutorial_list_should_mark_seen(is_active = false, progress = progress, seen_incomplete = false))
    }
}
