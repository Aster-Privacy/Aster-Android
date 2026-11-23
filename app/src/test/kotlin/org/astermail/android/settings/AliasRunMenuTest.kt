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

package org.astermail.android.settings

import org.astermail.android.api.settings.AliasRun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AliasRunMenuTest {

    private fun run(status: String) = AliasRun(
        run_id = "r1",
        alias_id = "a1",
        status = status,
        scanned = 12,
        matched = 7,
        applied = 7,
    )

    @Test
    fun inbox_alias_without_label_has_nothing_to_apply() {
        assertNull(alias_run_confirm_kind(folder_token = null, never_inbox = false, label_token = null))
    }

    @Test
    fun folder_only_alias_confirms_the_folder() {
        assertEquals(
            AliasRunConfirmKind.FOLDER,
            alias_run_confirm_kind(folder_token = "f1", never_inbox = false, label_token = null),
        )
    }

    @Test
    fun archive_alias_counts_as_a_folder() {
        assertEquals(
            AliasRunConfirmKind.FOLDER,
            alias_run_confirm_kind(folder_token = null, never_inbox = true, label_token = null),
        )
    }

    @Test
    fun label_only_alias_confirms_the_label() {
        assertEquals(
            AliasRunConfirmKind.LABEL,
            alias_run_confirm_kind(folder_token = null, never_inbox = false, label_token = "t1"),
        )
    }

    @Test
    fun folder_and_label_alias_confirms_both() {
        assertEquals(
            AliasRunConfirmKind.FOLDER_LABEL,
            alias_run_confirm_kind(folder_token = "f1", never_inbox = false, label_token = "t1"),
        )
    }

    @Test
    fun run_starts_when_there_is_something_to_apply() {
        assertTrue(can_start_alias_run(AliasRunConfirmKind.FOLDER, folder_is_spam = false, run = null, busy = false))
        assertTrue(can_start_alias_run(AliasRunConfirmKind.LABEL, folder_is_spam = false, run = null, busy = false))
    }

    @Test
    fun run_is_blocked_with_nothing_to_apply() {
        assertFalse(can_start_alias_run(null, folder_is_spam = false, run = null, busy = false))
    }

    @Test
    fun run_into_spam_is_blocked() {
        assertFalse(can_start_alias_run(AliasRunConfirmKind.FOLDER, folder_is_spam = true, run = null, busy = false))
    }

    @Test
    fun second_run_is_blocked_while_one_is_active() {
        assertFalse(can_start_alias_run(AliasRunConfirmKind.FOLDER, false, run("pending"), busy = false))
        assertFalse(can_start_alias_run(AliasRunConfirmKind.FOLDER, false, run("running"), busy = false))
    }

    @Test
    fun new_run_is_allowed_after_the_last_one_finished() {
        assertTrue(can_start_alias_run(AliasRunConfirmKind.FOLDER, false, run("completed"), busy = false))
        assertTrue(can_start_alias_run(AliasRunConfirmKind.FOLDER, false, run("canceled"), busy = false))
        assertTrue(can_start_alias_run(AliasRunConfirmKind.FOLDER, false, run("failed"), busy = false))
    }

    @Test
    fun run_is_blocked_while_busy() {
        assertFalse(can_start_alias_run(AliasRunConfirmKind.FOLDER, false, run = null, busy = true))
    }

    @Test
    fun finish_is_reported_when_an_active_run_completes() {
        assertEquals("completed", alias_run_finished(run("running"), run("completed"))?.status)
        assertEquals("failed", alias_run_finished(run("pending"), run("failed"))?.status)
    }

    @Test
    fun finish_is_not_reported_for_a_run_that_was_already_done() {
        assertNull(alias_run_finished(run("completed"), run("completed")))
        assertNull(alias_run_finished(null, run("completed")))
    }

    @Test
    fun finish_is_not_reported_while_the_run_is_still_active() {
        assertNull(alias_run_finished(run("pending"), run("running")))
        assertNull(alias_run_finished(run("running"), null))
    }
}
