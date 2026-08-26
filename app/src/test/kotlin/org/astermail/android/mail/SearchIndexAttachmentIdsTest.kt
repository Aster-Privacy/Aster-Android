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

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.MailApi
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchIndexAttachmentIdsTest {

    private fun manager(dao: DecryptedMailDao): SearchIndexManager {
        val database = mockk<AsterDatabase>()
        every { database.decrypted_mail_dao() } returns dao
        return SearchIndexManager(
            { database },
            mockk<MailApi>(relaxed = true),
            mockk<MailRepository>(relaxed = true),
            mockk<android.content.Context>(relaxed = true),
        )
    }

    @Test
    fun a_successful_read_reports_the_ids_that_carry_attachments() = runTest {
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        coEvery { dao.ids_with_attachments() } returns listOf("one", "two")

        assertEquals(setOf("one", "two"), manager(dao).known_attachment_ids())
    }

    @Test
    fun a_failed_read_is_distinguishable_from_having_no_attachments() = runTest {
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        coEvery { dao.ids_with_attachments() } throws IllegalStateException("database unavailable")

        assertNull(manager(dao).known_attachment_ids())
    }

    @Test
    fun an_empty_index_reports_an_empty_set_rather_than_a_failure() = runTest {
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        coEvery { dao.ids_with_attachments() } returns emptyList()

        assertEquals(emptySet<String>(), manager(dao).known_attachment_ids())
    }
}
