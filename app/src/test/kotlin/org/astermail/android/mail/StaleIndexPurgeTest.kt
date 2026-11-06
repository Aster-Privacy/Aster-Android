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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailDao
import org.astermail.android.storage.search.InboxWindowRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StaleIndexPurgeTest {

    private lateinit var dao: DecryptedMailDao
    private lateinit var mail_api: MailApi
    private lateinit var manager: SearchIndexManager

    private fun item(id: String): MailItem = MailItem(
        id = id,
        item_type = "received",
        thread_token = null,
        thread_message_count = 1,
        message_ts = "2026-11-03T10:00:00Z",
        created_at = "2026-11-03T10:00:00Z",
    )

    private fun page(items: List<MailItem>): MailItemsListResponse = MailItemsListResponse(
        items = items,
        has_more = false,
        next_cursor = null,
        total = items.size,
    )

    private fun inbox_scope_returns(items: List<MailItem>) {
        coEvery {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = "received",
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                skip_total = any(),
            )
        } returns page(items)
    }

    private fun server_ids_return(items: List<MailItem>) {
        coEvery {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = "all",
                include_spam = true,
                include_trash = true,
                group_by_thread = false,
                skip_total = true,
                include_envelope = false,
            )
        } returns page(items)
    }

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        mail_api = mockk(relaxed = true)
        coEvery {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = any(),
                is_trashed = any(),
                is_archived = any(),
                is_spam = any(),
                skip_total = any(),
            )
        } returns page(emptyList())
        coEvery {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        } returns page(emptyList())
        val db = mockk<AsterDatabase>(relaxed = true)
        every { db.decrypted_mail_dao() } returns dao
        manager = SearchIndexManager(dagger.Lazy { db }, mail_api, mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `rows missing from a page are reported unless returned, threaded, or protected`() {
        val rows = listOf(
            InboxWindowRow("kept", null),
            InboxWindowRow("reply", "thread_a"),
            InboxWindowRow("pending", null),
            InboxWindowRow("gone", "thread_b"),
        )

        val missing = index_rows_missing_from_page(rows, setOf("kept"), setOf("thread_a"), setOf("pending"))

        assertEquals(listOf("gone"), missing)
    }

    @Test
    fun `ids missing from the server listing are reported`() {
        val missing = index_ids_missing_from_server(
            candidate_ids = listOf("live", "deleted"),
            server_ids = setOf("live", "other"),
            walked_ids = setOf("live"),
        )

        assertEquals(listOf("deleted"), missing)
    }

    @Test
    fun `nothing is reported when the server listing misses walked mail`() {
        val missing = index_ids_missing_from_server(
            candidate_ids = listOf("live", "deleted"),
            server_ids = emptySet(),
            walked_ids = setOf("live"),
        )

        assertTrue(missing.isEmpty())
    }

    @Test
    fun `an empty whole inbox removes old cached rows after two syncs`() = runTest {
        coEvery { dao.unlabeled_inbox_rows() } returns listOf(InboxWindowRow("old_newsletter", null))

        manager.reconcile_inbox_window(emptySet(), emptySet(), "", whole_scope = true)
        coVerify(exactly = 0) { dao.remove_items(any()) }

        manager.reconcile_inbox_window(emptySet(), emptySet(), "", whole_scope = true)
        coVerify(exactly = 1) { dao.remove_items(listOf("old_newsletter")) }
        coVerify(exactly = 0) { dao.inbox_window_rows_newer_than(any()) }
    }

    @Test
    fun `a whole inbox page removes rows older than the page`() = runTest {
        coEvery { dao.unlabeled_inbox_rows() } returns listOf(
            InboxWindowRow("sign_in_notice", null),
            InboxWindowRow("old_newsletter", null),
        )

        repeat(2) {
            manager.reconcile_inbox_window(
                setOf("sign_in_notice"),
                emptySet(),
                "2026-11-03T10:00:00Z",
                whole_scope = true,
            )
        }

        coVerify(exactly = 1) { dao.remove_items(listOf("old_newsletter")) }
    }

    @Test
    fun `a whole inbox page keeps rows with pending local actions`() = runTest {
        coEvery { dao.unlabeled_inbox_rows() } returns listOf(InboxWindowRow("restored", null))

        repeat(2) {
            manager.reconcile_inbox_window(
                emptySet(),
                emptySet(),
                "",
                whole_scope = true,
                protected_ids = setOf("restored"),
            )
        }

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `a partial page with no items does not touch the cache`() = runTest {
        manager.reconcile_inbox_window(emptySet(), emptySet(), "")

        coVerify(exactly = 0) { dao.unlabeled_inbox_rows() }
        coVerify(exactly = 0) { dao.inbox_window_rows_newer_than(any()) }
        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `a full index build removes cached mail the server no longer has`() = runTest {
        coEvery { dao.get_all_ids() } returns listOf("sign_in_notice", "old_newsletter")
        coEvery { dao.unlabeled_ids() } returns listOf("sign_in_notice", "old_newsletter")
        inbox_scope_returns(listOf(item("sign_in_notice")))
        server_ids_return(listOf(item("sign_in_notice"), item("sent_reply")))

        manager.refresh_index_and_wait()

        coVerify(exactly = 1) { dao.remove_items(listOf("old_newsletter")) }
    }

    @Test
    fun `a full index build keeps cached mail when the server listing looks incomplete`() = runTest {
        coEvery { dao.get_all_ids() } returns listOf("sign_in_notice", "old_newsletter")
        coEvery { dao.unlabeled_ids() } returns listOf("sign_in_notice", "old_newsletter")
        inbox_scope_returns(listOf(item("sign_in_notice")))
        server_ids_return(emptyList())

        manager.refresh_index_and_wait()

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `a full index build leaves labeled mail alone`() = runTest {
        coEvery { dao.get_all_ids() } returns listOf("sign_in_notice", "in_a_folder")
        coEvery { dao.unlabeled_ids() } returns listOf("sign_in_notice")
        inbox_scope_returns(listOf(item("sign_in_notice")))
        server_ids_return(listOf(item("sign_in_notice")))

        manager.refresh_index_and_wait()

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `offline warming clears a folder only when the whole folder came back empty`() {
        assertTrue(folder_cache_should_warm(items_empty = false, whole_scope = false))
        assertTrue(folder_cache_should_warm(items_empty = true, whole_scope = true))
        assertFalse(folder_cache_should_warm(items_empty = true, whole_scope = false))
    }
}
