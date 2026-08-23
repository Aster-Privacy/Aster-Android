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

package org.astermail.android.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.mail.MailApi
import org.astermail.android.mail.MailRepository
import org.astermail.android.mail.SearchIndexManager
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecryptedMailCachePurgeInstrumentedTest {

    private lateinit var database: AsterDatabase

    private fun row(id: String, subject: String, preview: String): DecryptedMailEntity =
        DecryptedMailEntity(
            id = id,
            thread_token = id,
            thread_message_count = 1,
            sender_name = "Sender",
            sender_email = "sender@astermail.org",
            subject = subject,
            preview = preview,
            timestamp = "2026-07-24T12:00:00Z",
            is_read = false,
            is_starred = false,
            is_encrypted = true,
            has_attachments = false,
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            labels = "",
            indexed_at = 1L,
        )

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AsterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun purges_rows_cached_with_a_leaked_bundle_preview() = runBlocking {
        val dao = database.decrypted_mail_dao()
        dao.insert_all(
            listOf(
                row("clean", "Question", "Hi, I'm new here"),
                row("poisoned", "", "ASTER_BUNDLE_V2{\"s\":\"Question\",\"b\":\"<p>Hi</p>"),
                row("poisoned_subject", "ASTER_BUNDLE_V2{\"s\":\"x\"", "body"),
            ),
        )

        val removed = dao.delete_bundle_poisoned()

        assertEquals(2, removed)
        assertEquals(listOf("clean"), dao.get_all_ids())
    }

    @Test
    fun cached_items_read_by_the_index_manager_never_contain_a_leaked_bundle() = runBlocking {
        val dao = database.decrypted_mail_dao()
        dao.insert_all(
            listOf(
                row("clean", "Question", "Hi, I'm new here"),
                row("poisoned", "", "ASTER_BUNDLE_V2{\"s\":\"Question\",\"b\":\"<p>Hi</p>"),
            ),
        )
        val manager = SearchIndexManager(
            dagger.Lazy { database },
            mockk<MailApi>(relaxed = true),
            mockk<MailRepository>(relaxed = true),
            ApplicationProvider.getApplicationContext(),
        )

        val cached = manager.get_cached_items()

        assertEquals(listOf("clean"), cached.map { it.id })
        assertTrue(cached.none { it.preview.contains("ASTER_BUNDLE_V2") || it.subject.contains("ASTER_BUNDLE_V2") })
    }

    @Test
    fun keeps_every_row_when_no_bundle_marker_was_cached() = runBlocking {
        val dao = database.decrypted_mail_dao()
        dao.insert_all(listOf(row("a", "One", "body one"), row("b", "Two", "body two")))

        val removed = dao.delete_bundle_poisoned()

        assertEquals(0, removed)
        assertEquals(2, dao.count())
    }
}
