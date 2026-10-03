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

package org.astermail.android.ui.search


package org.astermail.android.ui.search

import io.mockk.mockk
import org.astermail.android.mail.InboxItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ChipPeopleForTest {

    private fun item(id: String) = InboxItem(
        id = id, thread_token = null, thread_message_count = 1,
        sender_name = "Aster", sender_email = "noreply@astermail.org",
        subject = "Welcome", preview = "hi",
        timestamp = "2026-07-31T10:00:00Z",
        is_read = false, is_starred = false, is_encrypted = true,
        has_attachments = false, is_trashed = false, is_archived = false,
        is_spam = false, labels = emptyList(), raw_item = mockk(relaxed = true),
    )

    private val people: ChipPeopleLists =
        listOf(ChipPerson("Aster", "noreply@astermail.org", 1)) to emptyList()

    @Test
    fun people_built_for_the_current_corpus_are_shown() {
        val corpus = listOf(item("a"))

        assertEquals(people, chip_people_for(corpus, corpus to people))
    }

    @Test
    fun people_from_a_previous_corpus_are_not_shown() {
        val previous = listOf(item("a"), item("b"))
        val current = listOf(item("a"))

        assertEquals(NO_CHIP_PEOPLE, chip_people_for(current, previous to people))
    }

    @Test
    fun a_refiltered_corpus_with_the_same_items_waits_for_its_own_people() {
        val previous = listOf(item("a"))
        val refiltered = previous.toList()

        assertEquals(NO_CHIP_PEOPLE, chip_people_for(refiltered, previous to people))
    }

    @Test
    fun nothing_produced_yet_shows_no_people() {
        assertEquals(NO_CHIP_PEOPLE, chip_people_for(listOf(item("a")), null))
    }
}
