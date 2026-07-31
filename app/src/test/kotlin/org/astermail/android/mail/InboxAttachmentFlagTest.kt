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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.storage.search.DecryptedMailDao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxAttachmentFlagTest {

    private suspend fun partition(
        repository: MailRepository,
        dao: DecryptedMailDao,
        ids: List<String>,
        batch_size: Int,
    ): Pair<Set<String>, List<String>> {
        val found = HashSet<String>()
        val failed = ArrayList<String>()
        for (batch in ids.chunked(batch_size)) {
            repository.probe_messages_with_attachments(batch).fold(
                onSuccess = { found.addAll(it) },
                onFailure = { failed.addAll(batch) },
            )
        }
        if (found.isNotEmpty()) dao.mark_has_attachments(found.toList())
        return found to failed
    }

    @Test
    fun a_failed_batch_is_reported_separately_from_an_empty_one() = runTest {
        val repository = mockk<MailRepository>()
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        coEvery { repository.probe_messages_with_attachments(listOf("a", "b")) } returns
            Result.success(listOf("a"))
        coEvery { repository.probe_messages_with_attachments(listOf("c", "d")) } returns
            Result.failure(IllegalStateException("unauthorized"))
        coEvery { repository.probe_messages_with_attachments(listOf("e", "f")) } returns
            Result.success(emptyList())

        val (found, failed) = partition(repository, dao, listOf("a", "b", "c", "d", "e", "f"), 2)

        assertEquals(setOf("a"), found)
        assertEquals(listOf("c", "d"), failed)
    }

    @Test
    fun a_successful_empty_batch_leaves_nothing_to_retry() = runTest {
        val repository = mockk<MailRepository>()
        val dao = mockk<DecryptedMailDao>(relaxed = true)
        coEvery { repository.probe_messages_with_attachments(any()) } returns Result.success(emptyList())

        val (found, failed) = partition(repository, dao, listOf("a", "b"), 50)

        assertTrue(found.isEmpty())
        assertTrue(failed.isEmpty())
    }

    @Test
    fun resolved_ids_override_a_false_metadata_flag_on_the_row() {
        val resolved = setOf("with-attachment")
        val items = listOf(
            fake_inbox_item("with-attachment", has_attachments = false),
            fake_inbox_item("plain", has_attachments = false),
            fake_inbox_item("already-flagged", has_attachments = true),
        )

        val mapped = items.map {
            if (!it.has_attachments && it.id in resolved) it.copy(has_attachments = true) else it
        }

        assertTrue(mapped[0].has_attachments)
        assertTrue(!mapped[1].has_attachments)
        assertTrue(mapped[2].has_attachments)
    }

    private fun fake_inbox_item(id: String, has_attachments: Boolean) = InboxItem(
        id = id, thread_token = null, thread_message_count = 1,
        sender_name = "S", sender_email = "s@x.com",
        subject = "Sub", preview = "P",
        timestamp = "2026-07-31T12:00:00Z",
        is_read = false, is_starred = false, is_encrypted = true,
        has_attachments = has_attachments, is_trashed = false, is_archived = false,
        is_spam = false, labels = emptyList(), raw_item = mockk(relaxed = true),
    )
}
