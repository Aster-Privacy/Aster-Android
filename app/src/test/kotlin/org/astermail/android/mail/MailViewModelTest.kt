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

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.astermail.android.api.mail.BulkScopeResponse
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.mail.ThreadWithMessages
import org.astermail.android.api.send.SimpleSendResponse
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: MailRepository
    private lateinit var vm: MailViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        vm = MailViewModel(repository)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun fake_inbox_page(
        count: Int = 3,
        has_more: Boolean = false,
        next_cursor: String? = null,
    ): InboxPage {
        val items = (1..count).map { i ->
            InboxItem(
                id = "id_$i",
                thread_token = "thread_$i",
                thread_message_count = 1,
                sender_name = "Sender $i",
                sender_email = "sender$i@example.com",
                subject = "Subject $i",
                preview = "Preview $i",
                timestamp = "2026-04-26T10:0$i:00Z",
                is_read = i % 2 == 0,
                is_starred = false,
                is_encrypted = true,
                has_attachments = false,
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                labels = emptyList(),
                raw_item = mockk(relaxed = true),
            )
        }
        return InboxPage(items, has_more, next_cursor, count)
    }

    @Test
    fun `load_inbox sets loading then populates items`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        assertTrue(vm.inbox_state.value.is_loading)

        advanceUntilIdle()

        val state = vm.inbox_state.value
        assertFalse(state.is_loading)
        assertEquals(3, state.items.size)
        assertEquals("id_1", state.items[0].id)
        assertEquals("inbox", state.current_folder)
    }

    @Test
    fun `load_inbox error sets error message`() = runTest {
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("network failure"))

        vm.load_inbox(force = true)
        advanceUntilIdle()

        val state = vm.inbox_state.value
        assertFalse(state.is_loading)
        assertEquals("network failure", state.error)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `load_inbox skips if already loaded same folder`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()
        assertEquals(2, vm.inbox_state.value.items.size)

        vm.load_inbox()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_inbox(any(), any(), any(), any()) }
    }

    @Test
    fun `load_inbox force reloads even when items exist`() = runTest {
        val page1 = fake_inbox_page(2)
        val page2 = fake_inbox_page(5)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returnsMany
            listOf(Result.success(page1), Result.success(page2))

        vm.load_inbox()
        advanceUntilIdle()
        assertEquals(2, vm.inbox_state.value.items.size)

        vm.load_inbox(force = true)
        advanceUntilIdle()
        assertEquals(5, vm.inbox_state.value.items.size)
    }

    @Test
    fun `load_inbox with different folder triggers new fetch`() = runTest {
        val inbox_page = fake_inbox_page(3)
        val sent_page = fake_inbox_page(1)

        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(inbox_page)
        coEvery { repository.fetch_sent(any(), any()) } returns Result.success(sent_page)

        vm.load_inbox("inbox")
        advanceUntilIdle()
        assertEquals(3, vm.inbox_state.value.items.size)
        assertEquals("inbox", vm.inbox_state.value.current_folder)

        vm.load_inbox("sent")
        advanceUntilIdle()
        assertEquals(1, vm.inbox_state.value.items.size)
        assertEquals("sent", vm.inbox_state.value.current_folder)
    }

    @Test
    fun `load_more appends items and updates cursor`() = runTest {
        val page1 = fake_inbox_page(3, has_more = true, next_cursor = "cursor_1")
        coEvery { repository.fetch_inbox(any(), cursor = isNull(), any(), any()) } returns Result.success(page1)

        vm.load_inbox()
        advanceUntilIdle()
        assertEquals(3, vm.inbox_state.value.items.size)
        assertTrue(vm.inbox_state.value.has_more)

        val page2 = InboxPage(
            items = listOf(
                InboxItem(
                    id = "id_4", thread_token = "t4", thread_message_count = 1,
                    sender_name = "S4", sender_email = "s4@x.com",
                    subject = "Sub4", preview = "P4", timestamp = "2026-04-26T10:04:00Z",
                    is_read = false, is_starred = false, is_encrypted = true,
                    has_attachments = false, is_trashed = false, is_archived = false,
                    is_spam = false, labels = emptyList(), raw_item = mockk(relaxed = true),
                ),
            ),
            has_more = false,
            next_cursor = null,
            total = 4,
        )
        coEvery { repository.fetch_inbox(any(), cursor = eq("cursor_1"), any(), any()) } returns Result.success(page2)

        vm.load_more()
        advanceUntilIdle()

        val state = vm.inbox_state.value
        assertEquals(4, state.items.size)
        assertEquals("id_4", state.items.last().id)
        assertFalse(state.has_more)
        assertNull(state.next_cursor)
    }

    @Test
    fun `load_more does nothing when no more pages`() = runTest {
        val page = fake_inbox_page(2, has_more = false)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        vm.load_more()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_inbox(any(), any(), any(), any()) }
    }

    @Test
    fun `load_more does nothing when already loading more`() = runTest {
        val page = fake_inbox_page(2, has_more = true, next_cursor = "c1")
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        coEvery { repository.fetch_inbox(any(), cursor = eq("c1"), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(fake_inbox_page(1))
        }

        vm.load_more()
        vm.load_more()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_inbox(any(), cursor = eq("c1"), any(), any()) }
    }

    @Test
    fun `mark_read updates state and calls repository`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_read("id_1", true) } returns Result.success(Unit)

        vm.load_inbox()
        advanceUntilIdle()
        assertFalse(vm.inbox_state.value.items[0].is_read)

        vm.mark_read("id_1")
        advanceUntilIdle()

        assertTrue(vm.inbox_state.value.items[0].is_read)
        coVerify { repository.mark_read("id_1", true) }
    }

    @Test
    fun `mark_unread updates state and calls repository`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_read("id_2", false) } returns Result.success(Unit)

        vm.load_inbox()
        advanceUntilIdle()
        assertTrue(vm.inbox_state.value.items[1].is_read)

        vm.mark_unread("id_2")
        advanceUntilIdle()

        assertFalse(vm.inbox_state.value.items[1].is_read)
        coVerify { repository.mark_read("id_2", false) }
    }

    @Test
    fun `toggle_star flips star state and calls repository`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()
        assertFalse(vm.inbox_state.value.items[0].is_starred)

        vm.toggle_star("id_1")
        advanceUntilIdle()

        assertTrue(vm.inbox_state.value.items[0].is_starred)
        coVerify { repository.toggle_star("id_1", true) }

        vm.toggle_star("id_1")
        advanceUntilIdle()

        assertFalse(vm.inbox_state.value.items[0].is_starred)
        coVerify { repository.toggle_star("id_1", false) }
    }

    @Test
    fun `toggle_star on nonexistent item does nothing`() = runTest {
        val page = fake_inbox_page(1)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        vm.toggle_star("nonexistent")
        advanceUntilIdle()

        coVerify(exactly = 0) { repository.toggle_star(any(), any()) }
    }

    @Test
    fun `archive removes items from state and calls repository`() = runTest {
        val page = fake_inbox_page(5)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.archive(any()) } returns Result.success(BulkScopeResponse(affected_count =2))

        vm.load_inbox()
        advanceUntilIdle()
        assertEquals(5, vm.inbox_state.value.items.size)

        vm.archive(listOf("id_2", "id_4"))
        advanceUntilIdle()

        val remaining = vm.inbox_state.value.items
        assertEquals(3, remaining.size)
        assertTrue(remaining.none { it.id == "id_2" || it.id == "id_4" })
        coVerify { repository.archive(listOf("id_2", "id_4")) }
    }

    @Test
    fun `trash removes items from state and calls repository`() = runTest {
        val page = fake_inbox_page(4)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.trash(any()) } returns Result.success(BulkScopeResponse(affected_count =1))

        vm.load_inbox()
        advanceUntilIdle()

        vm.trash(listOf("id_3"))
        advanceUntilIdle()

        assertEquals(3, vm.inbox_state.value.items.size)
        assertTrue(vm.inbox_state.value.items.none { it.id == "id_3" })
        coVerify { repository.trash(listOf("id_3")) }
    }

    @Test
    fun `mark_spam removes items from state and calls repository`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_spam(any()) } returns Result.success(BulkScopeResponse(affected_count =1))

        vm.load_inbox()
        advanceUntilIdle()

        vm.mark_spam(listOf("id_1"))
        advanceUntilIdle()

        assertEquals(2, vm.inbox_state.value.items.size)
        assertTrue(vm.inbox_state.value.items.none { it.id == "id_1" })
        coVerify { repository.mark_spam(listOf("id_1")) }
    }

    @Test
    fun `mark_read_bulk marks multiple items read and calls repository`() = runTest {
        val page = fake_inbox_page(5)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_read_bulk(any()) } returns Result.success(BulkScopeResponse(affected_count =3))

        vm.load_inbox()
        advanceUntilIdle()

        val unread_ids = vm.inbox_state.value.items.filter { !it.is_read }.map { it.id }
        vm.mark_read_bulk(unread_ids)
        advanceUntilIdle()

        assertTrue(vm.inbox_state.value.items.filter { it.id in unread_ids }.all { it.is_read })
        coVerify { repository.mark_read_bulk(unread_ids) }
    }

    @Test
    fun `archive empty list does not crash`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        vm.archive(emptyList())
        advanceUntilIdle()

        assertEquals(2, vm.inbox_state.value.items.size)
        coVerify { repository.archive(emptyList()) }
    }

    @Test
    fun `trash all items results in empty list`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.trash(any()) } returns Result.success(BulkScopeResponse(affected_count =3))

        vm.load_inbox()
        advanceUntilIdle()

        vm.trash(listOf("id_1", "id_2", "id_3"))
        advanceUntilIdle()

        assertTrue(vm.inbox_state.value.items.isEmpty())
    }

    @Test
    fun `load_stats updates stats in state`() = runTest {
        val stats = MailUserStatsResponse(
            total_items = 100,
            unread = 17,
            starred = 5,
        )
        coEvery { repository.get_stats() } returns Result.success(stats)

        vm.load_stats()
        advanceUntilIdle()

        assertEquals(stats, vm.inbox_state.value.stats)
    }

    @Test
    fun `build_search_index sets indexing then indexed`() = runTest {
        val items = fake_inbox_page(10).items
        coEvery { repository.fetch_all_for_search(any()) } returns Result.success(items)

        vm.build_search_index()
        assertTrue(vm.search_state.value.is_indexing)

        advanceUntilIdle()

        val search = vm.search_state.value
        assertFalse(search.is_indexing)
        assertTrue(search.is_indexed)
        assertEquals(10, search.all_items.size)
    }

    @Test
    fun `build_search_index error sets error state`() = runTest {
        coEvery { repository.fetch_all_for_search(any()) } returns
            Result.failure(RuntimeException("timeout"))

        vm.build_search_index()
        advanceUntilIdle()

        val search = vm.search_state.value
        assertFalse(search.is_indexing)
        assertFalse(search.is_indexed)
        assertEquals("timeout", search.error)
    }

    @Test
    fun `build_search_index skips if already indexed`() = runTest {
        val items = fake_inbox_page(5).items
        coEvery { repository.fetch_all_for_search(any()) } returns Result.success(items)

        vm.build_search_index()
        advanceUntilIdle()
        assertTrue(vm.search_state.value.is_indexed)

        vm.build_search_index()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_all_for_search(any()) }
    }

    @Test
    fun `build_search_index force re-indexes`() = runTest {
        val items1 = fake_inbox_page(3).items
        val items2 = fake_inbox_page(7).items
        coEvery { repository.fetch_all_for_search(any()) } returnsMany
            listOf(Result.success(items1), Result.success(items2))

        vm.build_search_index()
        advanceUntilIdle()
        assertEquals(3, vm.search_state.value.all_items.size)

        vm.build_search_index(force = true)
        advanceUntilIdle()
        assertEquals(7, vm.search_state.value.all_items.size)
    }

    @Test
    fun `refresh reloads inbox and stats`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.get_stats() } returns Result.success(
            MailUserStatsResponse(total_items = 2, unread = 1, starred = 0),
        )

        vm.refresh()
        advanceUntilIdle()

        assertEquals(2, vm.inbox_state.value.items.size)
        coVerify { repository.fetch_inbox(any(), any(), any(), any()) }
        coVerify { repository.get_stats() }
    }

    @Test
    fun `load_inbox folder routing calls correct repository methods`() = runTest {
        coEvery { repository.fetch_drafts(any(), any()) } returns Result.success(fake_inbox_page(1))
        coEvery { repository.fetch_starred(any(), any()) } returns Result.success(fake_inbox_page(2))
        coEvery { repository.fetch_trash(any(), any()) } returns Result.success(fake_inbox_page(3))
        coEvery { repository.fetch_spam(any(), any()) } returns Result.success(fake_inbox_page(4))
        coEvery { repository.fetch_archive(any(), any()) } returns Result.success(fake_inbox_page(5))

        vm.load_inbox("drafts", force = true)
        advanceUntilIdle()
        assertEquals(1, vm.inbox_state.value.items.size)

        vm.load_inbox("starred", force = true)
        advanceUntilIdle()
        assertEquals(2, vm.inbox_state.value.items.size)

        vm.load_inbox("trash", force = true)
        advanceUntilIdle()
        assertEquals(3, vm.inbox_state.value.items.size)

        vm.load_inbox("spam", force = true)
        advanceUntilIdle()
        assertEquals(4, vm.inbox_state.value.items.size)

        vm.load_inbox("archive", force = true)
        advanceUntilIdle()
        assertEquals(5, vm.inbox_state.value.items.size)
    }

    @Test
    fun `load_inbox label folder passes label_token`() = runTest {
        coEvery { repository.fetch_inbox(any(), any(), any(), label_token = eq("lbl_123")) } returns
            Result.success(fake_inbox_page(2))

        vm.load_inbox("label:lbl_123", force = true)
        advanceUntilIdle()

        assertEquals(2, vm.inbox_state.value.items.size)
        coVerify { repository.fetch_inbox(any(), any(), any(), label_token = "lbl_123") }
    }

    @Test
    fun `initial state is correct`() {
        val inbox = vm.inbox_state.value
        assertTrue(inbox.items.isEmpty())
        assertFalse(inbox.is_loading)
        assertTrue(inbox.initial)
        assertFalse(inbox.has_more)
        assertEquals("inbox", inbox.current_folder)

        val thread = vm.thread_state.value
        assertTrue(thread.messages.isEmpty())
        assertFalse(thread.is_loading)
        assertNull(thread.error)

        val search = vm.search_state.value
        assertTrue(search.all_items.isEmpty())
        assertFalse(search.is_indexing)
        assertFalse(search.is_indexed)
    }

    @Test
    fun `send_email success delegates to repository`() = runTest {
        val response = SimpleSendResponse(success = true, message = "sent", mail_item_id = "m1")
        coEvery {
            repository.send_email(
                to = any(),
                cc = any(),
                bcc = any(),
                subject = any(),
                body_html = any(),
                sender_email = any(),
                sender_display_name = any(),
                thread_token = any(),
                expires_at = any(),
                attachments = any(),
            )
        } returns Result.success(response)

        val result = vm.send_email(
            to = listOf("user@example.com"),
            subject = "Hello",
            body_html = "<p>Hi</p>",
        )

        assertTrue(result.isSuccess)
        assertEquals("m1", result.getOrThrow().mail_item_id)
    }

    @Test
    fun `send_email failure returns error`() = runTest {
        coEvery {
            repository.send_email(
                to = any(),
                cc = any(),
                bcc = any(),
                subject = any(),
                body_html = any(),
                sender_email = any(),
                sender_display_name = any(),
                thread_token = any(),
                expires_at = any(),
                attachments = any(),
            )
        } returns Result.failure(RuntimeException("send failed"))

        val result = vm.send_email(
            to = listOf("user@example.com"),
            subject = "Hello",
            body_html = "<p>Hi</p>",
        )

        assertTrue(result.isFailure)
        assertEquals("send failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `save_draft success delegates to repository`() = runTest {
        coEvery {
            repository.save_draft(
                subject = any(),
                body_html = any(),
                sender_email = any(),
                to = any(),
                cc = any(),
            )
        } returns Result.success("draft_123")

        val result = vm.save_draft(
            subject = "Draft subject",
            body_html = "<p>draft body</p>",
        )

        assertTrue(result.isSuccess)
        assertEquals("draft_123", result.getOrThrow())
    }

    @Test
    fun `save_draft failure returns error`() = runTest {
        coEvery {
            repository.save_draft(
                subject = any(),
                body_html = any(),
                sender_email = any(),
                to = any(),
                cc = any(),
            )
        } returns Result.failure(RuntimeException("draft save failed"))

        val result = vm.save_draft(
            subject = "Draft",
            body_html = "<p>body</p>",
        )

        assertTrue(result.isFailure)
        assertEquals("draft save failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `load_thread success populates thread state`() = runTest {
        val inbox_item = fake_inbox_page(1).items[0]
        val thread_messages = listOf(
            ThreadMessageDecrypted(
                id = "msg_1",
                sender_name = "Alice",
                sender_email = "alice@example.com",
                to_label = "me",
                timestamp = "2026-04-26T10:00:00Z",
                body_text = "Hello",
                body_html = "<p>Hello</p>",
                is_encrypted = true,
                is_read = true,
                raw_item = mockk(relaxed = true),
            ),
        )

        coEvery { repository.fetch_single_message("id_1") } returns Result.success(inbox_item)
        coEvery { repository.fetch_thread("thread_1") } returns Result.success(thread_messages)

        vm.load_thread("id_1")
        assertTrue(vm.thread_state.value.is_loading)

        advanceUntilIdle()

        val state = vm.thread_state.value
        assertFalse(state.is_loading)
        assertNull(state.error)
        assertEquals(1, state.messages.size)
        assertEquals("msg_1", state.messages[0].id)
        assertNotNull(state.item)
    }

    @Test
    fun `load_thread failure when fetch_single_message fails sets error`() = runTest {
        coEvery { repository.fetch_single_message("bad_id") } returns
            Result.failure(RuntimeException("not found"))

        vm.load_thread("bad_id")
        advanceUntilIdle()

        val state = vm.thread_state.value
        assertFalse(state.is_loading)
        assertEquals("not found", state.error)
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `load_thread loading state is set before async work`() = runTest {
        coEvery { repository.fetch_single_message(any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.failure(RuntimeException("timeout"))
        }

        vm.load_thread("id_1")
        assertTrue(vm.thread_state.value.is_loading)

        advanceUntilIdle()
        assertFalse(vm.thread_state.value.is_loading)
    }

    @Test
    fun `load_thread with null thread_token falls back to single message`() = runTest {
        val item = fake_inbox_page(1).items[0].copy(thread_token = null)
        coEvery { repository.fetch_single_message("id_1") } returns Result.success(item)
        coEvery { repository.decrypt_single_thread_message(any()) } returns ThreadMessageDecrypted(
            id = "id_1",
            sender_name = "",
            sender_email = "",
            to_label = "me",
            timestamp = "2026-04-26T10:01:00Z",
            body_text = "",
            body_html = null,
            is_encrypted = false,
            is_read = false,
            raw_item = mockk(relaxed = true),
        )

        vm.load_thread("id_1")
        advanceUntilIdle()

        val state = vm.thread_state.value
        assertNull(state.error)
        assertEquals(1, state.messages.size)
        assertNotNull(state.item)
    }

    @Test
    fun `load_thread fetch_thread failure falls back to single message`() = runTest {
        val item = fake_inbox_page(1).items[0]
        coEvery { repository.fetch_single_message("id_1") } returns Result.success(item)
        coEvery { repository.fetch_thread("thread_1") } returns
            Result.failure(RuntimeException("thread api down"))
        coEvery { repository.decrypt_single_thread_message(any()) } returns ThreadMessageDecrypted(
            id = "id_1",
            sender_name = "",
            sender_email = "",
            to_label = "me",
            timestamp = "2026-04-26T10:01:00Z",
            body_text = "",
            body_html = null,
            is_encrypted = false,
            is_read = false,
            raw_item = mockk(relaxed = true),
        )

        vm.load_thread("id_1")
        advanceUntilIdle()

        val state = vm.thread_state.value
        assertEquals("thread api down", state.error)
        assertEquals(1, state.messages.size)
    }

    @Test
    fun `load_inbox sent folder calls fetch_sent`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_sent(any(), any()) } returns Result.success(page)

        vm.load_inbox("sent", force = true)
        advanceUntilIdle()

        assertEquals(2, vm.inbox_state.value.items.size)
        assertEquals("sent", vm.inbox_state.value.current_folder)
        coVerify { repository.fetch_sent(any(), any()) }
    }

    @Test
    fun `load_inbox all folder calls fetch_inbox with all type`() = runTest {
        val page = fake_inbox_page(4)
        coEvery { repository.fetch_inbox(any(), any(), item_type = eq("all"), any()) } returns
            Result.success(page)

        vm.load_inbox("all", force = true)
        advanceUntilIdle()

        assertEquals(4, vm.inbox_state.value.items.size)
        assertEquals("all", vm.inbox_state.value.current_folder)
    }

    @Test
    fun `load_inbox unknown folder falls back to fetch_inbox`() = runTest {
        val page = fake_inbox_page(1)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox("unknown_folder", force = true)
        advanceUntilIdle()

        assertEquals(1, vm.inbox_state.value.items.size)
        assertEquals("unknown_folder", vm.inbox_state.value.current_folder)
    }

    @Test
    fun `load_inbox skips when already loading`() = runTest {
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(fake_inbox_page(3))
        }

        vm.load_inbox()
        vm.load_inbox(force = true)

        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_inbox(any(), any(), any(), any()) }
    }

    @Test
    fun `load_inbox initial false with items skips fetch`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()
        assertEquals(3, vm.inbox_state.value.items.size)

        vm.load_inbox()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_inbox(any(), any(), any(), any()) }
    }

    @Test
    fun `refresh calls load_inbox with force and preserves folder`() = runTest {
        val sent_page = fake_inbox_page(2)
        coEvery { repository.fetch_sent(any(), any()) } returns Result.success(sent_page)
        coEvery { repository.get_stats() } returns Result.success(
            MailUserStatsResponse(total_items = 10, unread = 3, starred = 1),
        )

        vm.load_inbox("sent", force = true)
        advanceUntilIdle()
        assertEquals("sent", vm.inbox_state.value.current_folder)

        val new_page = fake_inbox_page(4)
        coEvery { repository.fetch_sent(any(), any()) } returns Result.success(new_page)

        vm.refresh()
        advanceUntilIdle()

        assertEquals("sent", vm.inbox_state.value.current_folder)
        coVerify(atLeast = 2) { repository.fetch_sent(any(), any()) }
    }

    @Test
    fun `mark_read on nonexistent item still calls repository`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_read(any(), any()) } returns Result.success(Unit)

        vm.load_inbox()
        advanceUntilIdle()

        vm.mark_read("nonexistent")
        advanceUntilIdle()

        coVerify { repository.mark_read("nonexistent", true) }
        assertEquals(2, vm.inbox_state.value.items.size)
    }

    @Test
    fun `mark_unread on nonexistent item still calls repository`() = runTest {
        val page = fake_inbox_page(2)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_read(any(), any()) } returns Result.success(Unit)

        vm.load_inbox()
        advanceUntilIdle()

        vm.mark_unread("nonexistent")
        advanceUntilIdle()

        coVerify { repository.mark_read("nonexistent", false) }
    }

    @Test
    fun `archive with nonexistent ids does not remove existing items`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.archive(any()) } returns Result.success(BulkScopeResponse(affected_count = 0))

        vm.load_inbox()
        advanceUntilIdle()

        vm.archive(listOf("nonexistent_1", "nonexistent_2"))
        advanceUntilIdle()

        assertEquals(3, vm.inbox_state.value.items.size)
    }

    @Test
    fun `trash with nonexistent ids does not remove existing items`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.trash(any()) } returns Result.success(BulkScopeResponse(affected_count = 0))

        vm.load_inbox()
        advanceUntilIdle()

        vm.trash(listOf("nonexistent"))
        advanceUntilIdle()

        assertEquals(3, vm.inbox_state.value.items.size)
    }

    @Test
    fun `mark_spam with nonexistent ids does not remove existing items`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_spam(any()) } returns Result.success(BulkScopeResponse(affected_count = 0))

        vm.load_inbox()
        advanceUntilIdle()

        vm.mark_spam(listOf("nonexistent"))
        advanceUntilIdle()

        assertEquals(3, vm.inbox_state.value.items.size)
    }

    @Test
    fun `load_inbox error clears on subsequent success`() = runTest {
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("first failure"))

        vm.load_inbox(force = true)
        advanceUntilIdle()
        assertEquals("first failure", vm.inbox_state.value.error)

        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns
            Result.success(fake_inbox_page(2))

        vm.load_inbox(force = true)
        advanceUntilIdle()

        assertNull(vm.inbox_state.value.error)
        assertEquals(2, vm.inbox_state.value.items.size)
    }

    @Test
    fun `load_stats failure does not crash`() = runTest {
        coEvery { repository.get_stats() } returns Result.failure(RuntimeException("stats error"))

        vm.load_stats()
        advanceUntilIdle()

        assertNull(vm.inbox_state.value.stats)
    }

    @Test
    fun `load_stats updates stats in inbox_state`() = runTest {
        val stats = MailUserStatsResponse(
            total_items = 200,
            unread = 42,
            starred = 10,
            trash = 5,
        )
        coEvery { repository.get_stats() } returns Result.success(stats)

        vm.load_stats()
        advanceUntilIdle()

        val loaded = vm.inbox_state.value.stats
        assertNotNull(loaded)
        assertEquals(42, loaded!!.unread)
        assertEquals(10, loaded.starred)
    }

    @Test
    fun `empty inbox has correct state`() = runTest {
        val page = InboxPage(items = emptyList(), has_more = false, next_cursor = null, total = 0)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        val state = vm.inbox_state.value
        assertTrue(state.items.isEmpty())
        assertFalse(state.is_loading)
        assertFalse(state.has_more)
        assertEquals(0, state.total)
    }

    @Test
    fun `load_more error does not crash and resets loading_more`() = runTest {
        val page = fake_inbox_page(3, has_more = true, next_cursor = "c1")
        coEvery { repository.fetch_inbox(any(), cursor = isNull(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        coEvery { repository.fetch_inbox(any(), cursor = eq("c1"), any(), any()) } returns
            Result.failure(RuntimeException("load more failed"))

        vm.load_more()
        advanceUntilIdle()

        assertFalse(vm.inbox_state.value.is_loading_more)
        assertEquals(3, vm.inbox_state.value.items.size)
    }

    @Test
    fun `load_more with null next_cursor does nothing`() = runTest {
        val page = fake_inbox_page(3, has_more = true, next_cursor = null)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        vm.load_more()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_inbox(any(), any(), any(), any()) }
    }

    @Test
    fun `build_search_index skips when currently indexing`() = runTest {
        coEvery { repository.fetch_all_for_search(any()) } coAnswers {
            kotlinx.coroutines.delay(5000)
            Result.success(fake_inbox_page(3).items)
        }

        vm.build_search_index()
        vm.build_search_index()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.fetch_all_for_search(any()) }
    }

    @Test
    fun `toggle_star twice returns to original state`() = runTest {
        val page = fake_inbox_page(1)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        assertFalse(vm.inbox_state.value.items[0].is_starred)

        vm.toggle_star("id_1")
        assertTrue(vm.inbox_state.value.items[0].is_starred)

        vm.toggle_star("id_1")
        assertFalse(vm.inbox_state.value.items[0].is_starred)
    }

    @Test
    fun `mark_read_bulk on empty list does not crash`() = runTest {
        val page = fake_inbox_page(3)
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)
        coEvery { repository.mark_read_bulk(any()) } returns Result.success(BulkScopeResponse(affected_count = 0))

        vm.load_inbox()
        advanceUntilIdle()

        vm.mark_read_bulk(emptyList())
        advanceUntilIdle()

        coVerify { repository.mark_read_bulk(emptyList()) }
    }

    @Test
    fun `load_inbox total is preserved from page`() = runTest {
        val page = InboxPage(
            items = fake_inbox_page(3).items,
            has_more = true,
            next_cursor = "c1",
            total = 150,
        )
        coEvery { repository.fetch_inbox(any(), any(), any(), any()) } returns Result.success(page)

        vm.load_inbox()
        advanceUntilIdle()

        assertEquals(150, vm.inbox_state.value.total)
    }
}
