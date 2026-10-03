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

import org.astermail.android.api.mail.MailUserStatsResponse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderKnownEmptyTest {

    @Test
    fun an_empty_counted_folder_is_known_empty() {
        val stats = MailUserStatsResponse(spam = 0, trash = 3)
        assertTrue(folder_known_empty("spam", stats))
        assertFalse(folder_known_empty("trash", stats))
    }

    @Test
    fun missing_stats_are_never_known_empty() {
        assertFalse(folder_known_empty("spam", null))
    }

    @Test
    fun folders_without_a_reliable_count_are_never_known_empty() {
        val stats = MailUserStatsResponse()
        assertFalse(folder_known_empty("inbox", stats))
        assertFalse(folder_known_empty("sent", stats))
        assertFalse(folder_known_empty("label:work", stats))
    }
}
