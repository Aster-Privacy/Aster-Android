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

package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemMoveTargetsTest {
    @Test
    fun inbox_mail_can_go_to_archive_spam_trash_and_star() {
        assertEquals(listOf("archive", "spam", "trash", "star"), system_move_targets("inbox", all_starred = false))
    }

    @Test
    fun custom_folder_and_starred_views_offer_every_target() {
        assertEquals(listOf("archive", "spam", "trash", "star"), system_move_targets("f_token", all_starred = false))
        assertEquals(listOf("archive", "spam", "trash"), system_move_targets("starred", all_starred = true))
    }

    @Test
    fun mail_is_never_offered_the_folder_it_is_already_in() {
        assertFalse("archive" in system_move_targets("archive", all_starred = false))
        assertFalse("spam" in system_move_targets("spam", all_starred = false))
        assertTrue(system_move_targets("trash", all_starred = false).isEmpty())
    }

    @Test
    fun star_is_hidden_when_every_selected_row_is_starred() {
        assertFalse("star" in system_move_targets("inbox", all_starred = true))
    }

    @Test
    fun drafts_and_scheduled_mail_cannot_be_moved_to_system_folders() {
        assertTrue(system_move_targets("drafts", all_starred = false).isEmpty())
        assertTrue(system_move_targets("scheduled", all_starred = false).isEmpty())
        assertTrue(system_move_targets("outbox", all_starred = false).isEmpty())
        assertTrue(is_unmovable_mail_folder("drafts"))
        assertFalse(is_unmovable_mail_folder("inbox"))
    }

    @Test
    fun sent_mail_is_never_reported_as_spam() {
        assertEquals(listOf("archive", "trash", "star"), system_move_targets("sent", all_starred = false))
    }

    @Test
    fun spam_can_only_go_to_trash() {
        assertEquals(listOf("trash"), system_move_targets("spam", all_starred = false))
    }
}
