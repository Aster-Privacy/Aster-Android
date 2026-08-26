//
// Aster Mail - Privacy-first encrypted email
// Copyright (C) 2026 Aster Privacy
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveToInboxScopeTest {

    @Test
    fun bare_custom_folder_token_resolves_to_a_label() {
        assertEquals("bills", folder_label_token("bills"))
        assertEquals("my_feed", folder_label_token("my_feed"))
    }

    @Test
    fun prefixed_label_folder_resolves_to_its_token() {
        assertEquals("bills", folder_label_token("label:bills"))
    }

    @Test
    fun builtin_and_non_label_folders_have_no_token() {
        for (folder in listOf("inbox", "sent", "drafts", "starred", "archive", "trash", "spam", "all", "all+read")) {
            assertNull(folder, folder_label_token(folder))
        }
        assertNull(folder_label_token("tag:work"))
        assertNull(folder_label_token("routing:shopqa@astermail.org"))
        assertNull(folder_label_token("label:"))
        assertNull(folder_label_token(""))
    }

    @Test
    fun inbox_row_shows_for_every_folder_a_message_can_leave() {
        for (folder in listOf("archive", "trash", "spam", "bills", "label:bills")) {
            assertTrue(folder, can_move_to_inbox(folder))
        }
    }

    @Test
    fun inbox_row_hides_where_a_move_has_no_meaning() {
        for (folder in listOf("inbox", "sent", "drafts", "starred", "all", "tag:work", "routing:shopqa@astermail.org")) {
            assertFalse(folder, can_move_to_inbox(folder))
        }
    }
}
