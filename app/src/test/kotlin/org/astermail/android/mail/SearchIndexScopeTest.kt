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
import org.junit.Before
import org.junit.Test
import org.astermail.android.storage.search.InboxWindowRow

class SearchIndexScopeTest {

    private lateinit var dao: DecryptedMailDao
    private lateinit var mail_api: MailApi
    private lateinit var manager: SearchIndexManager

    private fun item(id: String): MailItem = MailItem(
        id = id,
        item_type = "received",
        thread_token = null,
        thread_message_count = 1,
        message_ts = "2026-04-26T10:00:00Z",
        created_at = "2026-04-26T10:00:00Z",
    )

    private fun page(items: List<MailItem>): MailItemsListResponse = MailItemsListResponse(
        items = items,
        has_more = false,
        next_cursor = null,
        total = items.size,
    )

    @Before
    fun setup() {
        dao = mockk(relaxed = true)
        coEvery { dao.get_all_ids() } returns listOf("i1")
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
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = any(),
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                skip_total = any(),
            )
        } returns page(listOf(item("i1")))
        val db = mockk<AsterDatabase>(relaxed = true)
        every { db.decrypted_mail_dao() } returns dao
        repository = mockk(relaxed = true)
        manager = SearchIndexManager(dagger.Lazy { db }, mail_api, repository, mockk(relaxed = true))
    }

    private lateinit var repository: MailRepository

    private fun live_page(items: List<MailItem>) {
        coEvery {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                item_type = any(),
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                skip_total = any(),
            )
        } returns page(items)
    }

    @Test
    fun `a pending read wins over a stale unread on already indexed mail`() = runTest {
        live_page(listOf(item("i1").copy(is_read = false)))
        manager.add_read_overlay { id -> if (id == "i1") true else null }

        manager.refresh_index_and_wait()

        coVerify(exactly = 1) { dao.set_read(listOf("i1"), true) }
        coVerify(exactly = 0) { dao.set_read(any(), false) }
    }

    @Test
    fun `new mail written to the index keeps a pending read and drops it once cleared`() = runTest {
        coEvery { dao.get_all_ids() } returns emptyList()
        live_page(listOf(item("i2").copy(is_read = false)))
        val stale = InboxItem(
            id = "i2",
            thread_token = "thread_2",
            thread_message_count = 1,
            sender_name = "Sender",
            sender_email = "sender@example.com",
            subject = "Subject",
            preview = "Preview",
            timestamp = "2026-04-26T10:00:00Z",
            is_read = false,
            is_starred = false,
            is_encrypted = true,
            has_attachments = false,
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            labels = emptyList(),
            raw_item = mockk(relaxed = true),
        )
        coEvery { repository.decrypt_items_for_cache(any()) } returns listOf(stale)
        var pending: Boolean? = true
        val overlay: (String) -> Boolean? = { id -> if (id == "i2") pending else null }
        manager.add_read_overlay(overlay)

        manager.refresh_index_and_wait()
        coVerify { dao.insert_all(match { rows -> rows.single().is_read }) }

        pending = null
        manager.remove_read_overlay(overlay)
        manager.refresh_index_and_wait()
        coVerify { dao.insert_all(match { rows -> !rows.single().is_read }) }
    }

    @Test
    fun `the inbox window reconcile only considers inbox scoped rows`() = runTest {
        coEvery { dao.inbox_window_rows_newer_than(any()) } returns listOf(
            InboxWindowRow("stale_1", null),
        )
        coEvery {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        } returns page(emptyList())

        manager.reconcile_inbox_window(setOf("kept_1"), emptySet(), "2026-04-26T09:00:00Z")

        coVerify(exactly = 1) { dao.inbox_window_rows_newer_than("2026-04-26T09:00:00Z") }
        coVerify(exactly = 0) { dao.remove_items(any()) }

        manager.reconcile_inbox_window(setOf("kept_1"), emptySet(), "2026-04-26T09:00:00Z")

        coVerify(exactly = 1) { dao.remove_items(listOf("stale_1")) }
    }

    @Test
    fun `a cached thread reply is kept when its thread head came back in the page`() = runTest {
        coEvery { dao.inbox_window_rows_newer_than(any()) } returns listOf(
            InboxWindowRow("reply_1", "thread_a"),
        )
        coEvery {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        } returns page(emptyList())

        manager.reconcile_inbox_window(
            setOf("head_1"),
            setOf("thread_a"),
            "2026-04-26T09:00:00Z",
        )

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `a cached message from an unreturned thread is still removed`() = runTest {
        coEvery { dao.inbox_window_rows_newer_than(any()) } returns listOf(
            InboxWindowRow("reply_1", "thread_b"),
        )
        coEvery {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        } returns page(emptyList())

        repeat(2) {
            manager.reconcile_inbox_window(
                setOf("head_1"),
                setOf("thread_a"),
                "2026-04-26T09:00:00Z",
            )
        }

        coVerify(exactly = 1) { dao.remove_items(listOf("reply_1")) }
    }

    @Test
    fun `a failed snooze lookup never removes indexed mail`() = runTest {
        coEvery { dao.inbox_window_rows_newer_than(any()) } returns listOf(
            InboxWindowRow("stale_1", null),
        )
        coEvery {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        } throws java.io.IOException("offline")

        manager.reconcile_inbox_window(setOf("kept_1"), emptySet(), "2026-04-26T09:00:00Z")

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `an incomplete snooze lookup never removes indexed mail`() = runTest {
        coEvery { dao.inbox_window_rows_newer_than(any()) } returns listOf(
            InboxWindowRow("stale_1", null),
        )
        coEvery {
            mail_api.list_messages(
                limit = 200,
                cursor = any(),
                item_type = "received",
                is_snoozed = true,
            )
        } returns MailItemsListResponse(
            items = emptyList(),
            has_more = true,
            next_cursor = "more",
            total = 500,
        )

        repeat(3) {
            manager.reconcile_inbox_window(setOf("kept_1"), emptySet(), "2026-04-26T09:00:00Z")
        }

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `a message that comes back in a later window keeps its strike cleared`() = runTest {
        coEvery { dao.inbox_window_rows_newer_than(any()) } returns listOf(
            InboxWindowRow("flaky_1", null),
        )
        coEvery {
            mail_api.list_messages(limit = 200, item_type = "received", is_snoozed = true)
        } returns page(emptyList())

        manager.reconcile_inbox_window(setOf("kept_1"), emptySet(), "2026-04-26T09:00:00Z")
        manager.reconcile_inbox_window(setOf("flaky_1"), emptySet(), "2026-04-26T09:00:00Z")
        manager.reconcile_inbox_window(setOf("kept_1"), emptySet(), "2026-04-26T09:00:00Z")

        coVerify(exactly = 0) { dao.remove_items(any()) }
    }

    @Test
    fun `live scope clears stale trashed and spam flags on already indexed mail`() = runTest {
        manager.refresh_index_and_wait()

        coVerify(exactly = 1) { dao.mark_untrashed(listOf("i1")) }
        coVerify(exactly = 1) { dao.mark_unspam(listOf("i1")) }
        coVerify(exactly = 1) { dao.mark_unarchived(listOf("i1")) }
    }
}
