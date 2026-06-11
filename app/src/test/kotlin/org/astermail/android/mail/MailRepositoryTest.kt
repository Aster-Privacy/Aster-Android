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

import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.BulkScopeRequest
import org.astermail.android.api.mail.BulkScopeResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.api.mail.PatchMetadataRequest
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.mail.ThreadWithMessages
import org.astermail.android.api.send.SendApi
import org.astermail.android.api.send.SimpleSendResponse
import org.astermail.android.storage.SessionKeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test

class MailRepositoryTest {

    private lateinit var mail_api: MailApi
    private lateinit var send_api: SendApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        every { Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        mail_api = mockk(relaxed = true)
        send_api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns null
        every { session_key_store.get_passphrase() } returns null
        repo = MailRepository(mail_api, send_api, session_key_store)
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private fun fake_mail_item(
        id: String = "item_1",
        thread_token: String? = "thread_1",
        encrypted_envelope: String? = null,
        envelope_nonce: String? = null,
    ): MailItem = MailItem(
        id = id,
        item_type = "received",
        thread_token = thread_token,
        thread_message_count = 1,
        encrypted_envelope = encrypted_envelope,
        envelope_nonce = envelope_nonce,
        message_ts = "2026-04-26T10:00:00Z",
        created_at = "2026-04-26T10:00:00Z",
    )

    @Test
    fun `fetch_inbox returns decrypted inbox page`() = runTest {
        val items = listOf(fake_mail_item("i1"), fake_mail_item("i2"))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = false, next_cursor = null, total = 2)

        val result = repo.fetch_inbox()
        assertTrue(result.isSuccess)

        val page = result.getOrThrow()
        assertEquals(2, page.items.size)
        assertFalse(page.has_more)
        assertNull(page.next_cursor)
    }

    @Test
    fun `fetch_inbox with null envelope yields empty sender`() = runTest {
        val items = listOf(fake_mail_item("i1", encrypted_envelope = null))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = false, next_cursor = null, total = 1)

        val result = repo.fetch_inbox()
        val item = result.getOrThrow().items[0]

        assertEquals("", item.sender_name)
        assertEquals("", item.sender_email)
        assertEquals("", item.subject)
    }

    @Test
    fun `fetch_inbox propagates api errors`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("api down")

        val result = repo.fetch_inbox()
        assertTrue(result.isFailure)
        assertEquals("api down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_sent delegates to list_messages with sent type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), item_type = eq("sent"), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_sent()
        coVerify { mail_api.list_messages(any(), any(), item_type = "sent", any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_drafts delegates with draft type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), item_type = eq("draft"), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_drafts()
        coVerify { mail_api.list_messages(any(), any(), item_type = "draft", any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_starred passes is_starred flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), is_starred = eq(true), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_starred()
        coVerify { mail_api.list_messages(any(), any(), any(), is_starred = true, any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_trash passes is_trashed flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), is_trashed = eq(true), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_trash()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), is_trashed = true, any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_spam passes is_spam flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_spam = eq(true), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_spam()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_spam = true, any(), any()) }
    }

    @Test
    fun `fetch_archive passes is_archived flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), is_archived = eq(true), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_archive()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), is_archived = true, any(), any(), any()) }
    }

    @Test
    fun `mark_read calls patch_metadata with is_read true`() = runTest {
        repo.mark_read("item_1", true)
        coVerify { mail_api.patch_metadata("item_1", PatchMetadataRequest(is_read = true)) }
    }

    @Test
    fun `mark_read false calls patch_metadata with is_read false`() = runTest {
        repo.mark_read("item_1", false)
        coVerify { mail_api.patch_metadata("item_1", PatchMetadataRequest(is_read = false)) }
    }

    @Test
    fun `toggle_star calls patch_metadata`() = runTest {
        repo.toggle_star("item_1", true)
        coVerify { mail_api.patch_metadata("item_1", PatchMetadataRequest(is_starred = true)) }
    }

    @Test
    fun `archive calls bulk_action with archive action`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 3)

        val result = repo.archive(listOf("a", "b", "c"))
        assertTrue(result.isSuccess)
        assertEquals(3, result.getOrThrow().affected_count)
        coVerify { mail_api.bulk_action(BulkScopeRequest(action = "archive", ids = listOf("a", "b", "c"))) }
    }

    @Test
    fun `trash calls bulk_action with trash action`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 2)

        repo.trash(listOf("x", "y"))
        coVerify { mail_api.bulk_action(BulkScopeRequest(action = "trash", ids = listOf("x", "y"))) }
    }

    @Test
    fun `mark_spam calls bulk_action with mark_spam action`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 1)

        repo.mark_spam(listOf("s1"))
        coVerify { mail_api.bulk_action(BulkScopeRequest(action = "mark_spam", ids = listOf("s1"))) }
    }

    @Test
    fun `mark_read_bulk calls bulk_action with mark_read action`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 5)

        repo.mark_read_bulk(listOf("a", "b", "c", "d", "e"))
        coVerify { mail_api.bulk_action(BulkScopeRequest(action = "mark_read", ids = listOf("a", "b", "c", "d", "e"))) }
    }

    @Test
    fun `delete_permanent calls api delete`() = runTest {
        repo.delete_permanent("item_1")
        coVerify { mail_api.delete_permanent("item_1") }
    }

    @Test
    fun `get_stats returns stats`() = runTest {
        val stats = MailUserStatsResponse(
            total_items = 100,
            unread = 17,
            starred = 5,
        )
        coEvery { mail_api.get_stats() } returns stats

        val result = repo.get_stats()
        assertTrue(result.isSuccess)
        assertEquals(17, result.getOrThrow().unread)
    }

    @Test
    fun `fetch_all_for_search pages through results`() = runTest {
        val page1_items = (1..50).map { fake_mail_item("page1_$it") }
        val page2_items = (1..10).map { fake_mail_item("page2_$it") }

        coEvery {
            mail_api.list_messages(limit = 50, cursor = isNull(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page1_items, has_more = true, next_cursor = "c1", total = 60)

        coEvery {
            mail_api.list_messages(limit = 50, cursor = eq("c1"), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page2_items, has_more = false, next_cursor = null, total = 60)

        val result = repo.fetch_all_for_search()
        assertTrue(result.isSuccess)
        assertEquals(60, result.getOrThrow().size)
    }

    @Test
    fun `fetch_all_for_search stops at max_pages`() = runTest {
        val page_items = (1..50).map { fake_mail_item("p_$it") }
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(page_items, has_more = true, next_cursor = "next", total = 1000)

        val result = repo.fetch_all_for_search(max_pages = 2)
        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrThrow().size)
    }

    @Test
    fun `fetch_inbox with cursor passes cursor to api`() = runTest {
        coEvery { mail_api.list_messages(any(), cursor = eq("my_cursor"), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(cursor = "my_cursor")
        coVerify { mail_api.list_messages(any(), cursor = "my_cursor", any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_inbox with label_token passes it to api`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), label_token = eq("lbl_abc"), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(label_token = "lbl_abc")
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), label_token = "lbl_abc", any()) }
    }

    @Test
    fun `inbox item with metadata extracts flags`() = runTest {
        val item = MailItem(
            id = "flagged",
            item_type = "received",
            encrypted_envelope = null,
            envelope_nonce = null,
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            metadata = MailItemMetadata(
                is_read = true,
                is_starred = true,
                is_trashed = false,
                is_archived = true,
                is_spam = false,
                has_attachments = true,
            ),
        )
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val result = repo.fetch_inbox()
        val inbox_item = result.getOrThrow().items[0]

        assertTrue(inbox_item.is_read)
        assertTrue(inbox_item.is_starred)
        assertFalse(inbox_item.is_trashed)
        assertTrue(inbox_item.is_archived)
        assertFalse(inbox_item.is_spam)
        assertTrue(inbox_item.has_attachments)
    }

    @Test
    fun `fetch_thread success returns decrypted messages`() = runTest {
        val thread_messages = listOf(
            ThreadMessageItem(
                id = "msg_1",
                item_type = "received",
                message_ts = "2026-04-26T10:00:00Z",
                created_at = "2026-04-26T10:00:00Z",
            ),
            ThreadMessageItem(
                id = "msg_2",
                item_type = "sent",
                message_ts = "2026-04-26T10:05:00Z",
                created_at = "2026-04-26T10:05:00Z",
            ),
        )
        coEvery { mail_api.get_thread_messages("thread_abc") } returns
            ThreadWithMessages(messages = thread_messages)

        val result = repo.fetch_thread("thread_abc")

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().size)
        assertEquals("msg_1", result.getOrThrow()[0].id)
        assertEquals("msg_2", result.getOrThrow()[1].id)
    }

    @Test
    fun `fetch_thread with multiple messages preserves order`() = runTest {
        val messages = (1..5).map { i ->
            ThreadMessageItem(
                id = "msg_$i",
                item_type = "received",
                message_ts = "2026-04-26T10:0${i}:00Z",
                created_at = "2026-04-26T10:0${i}:00Z",
            )
        }
        coEvery { mail_api.get_thread_messages("thread_multi") } returns
            ThreadWithMessages(messages = messages)

        val result = repo.fetch_thread("thread_multi")

        assertTrue(result.isSuccess)
        val decrypted = result.getOrThrow()
        assertEquals(5, decrypted.size)
        assertEquals("msg_1", decrypted[0].id)
        assertEquals("msg_5", decrypted[4].id)
    }

    @Test
    fun `fetch_thread error propagates`() = runTest {
        coEvery { mail_api.get_thread_messages("bad_thread") } throws
            RuntimeException("thread not found")

        val result = repo.fetch_thread("bad_thread")

        assertTrue(result.isFailure)
        assertEquals("thread not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_single_message success returns decrypted item`() = runTest {
        val item = fake_mail_item("single_1", thread_token = "t_single")
        coEvery { mail_api.get_message("single_1") } returns item

        val result = repo.fetch_single_message("single_1")

        assertTrue(result.isSuccess)
        val inbox_item = result.getOrThrow()
        assertEquals("single_1", inbox_item.id)
        assertEquals("t_single", inbox_item.thread_token)
    }

    @Test
    fun `fetch_single_message error propagates`() = runTest {
        coEvery { mail_api.get_message("missing") } throws RuntimeException("404 not found")

        val result = repo.fetch_single_message("missing")

        assertTrue(result.isFailure)
        assertEquals("404 not found", result.exceptionOrNull()?.message)
    }

    @Test
    fun `send_email delegates to send_api`() = runTest {
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_1")
        every { session_key_store.get_identity_key() } returns "test_identity_key"

        val result = repo.send_email(
            to = listOf("recipient@astermail.org"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isSuccess)
        coVerify { send_api.send_simple(any()) }
    }

    @Test
    fun `save_draft delegates to mail_api create_message`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_message(any()) } returns
            org.astermail.android.api.mail.CreateMailItemResponse(id = "draft_99", success = true)

        val result = repo.save_draft(
            subject = "Draft subject",
            body_html = "<p>draft</p>",
        )

        assertTrue(result.isSuccess)
        assertEquals("draft_99", result.getOrThrow())
        coVerify { mail_api.create_message(any()) }
    }

    @Test
    fun `save_draft fails when no identity key`() = runTest {
        every { session_key_store.get_identity_key() } returns null

        val result = repo.save_draft(
            subject = "Draft",
            body_html = "<p>body</p>",
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `decrypt_single_thread_message with null envelope returns empty fields`() {
        val item = ThreadMessageItem(
            id = "msg_null",
            item_type = "received",
            encrypted_envelope = null,
            envelope_nonce = null,
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )

        val result = repo.decrypt_single_thread_message(item)

        assertEquals("msg_null", result.id)
        assertEquals("", result.sender_name)
        assertEquals("", result.sender_email)
        assertEquals("", result.body_text)
        assertNull(result.body_html)
        assertFalse(result.is_encrypted)
    }

    @Test
    fun `decrypt_single_thread_message with encrypted envelope falls back gracefully`() {
        every { session_key_store.get_identity_key() } returns null
        every { session_key_store.get_passphrase() } returns null

        val item = ThreadMessageItem(
            id = "msg_enc",
            item_type = "received",
            encrypted_envelope = "c29tZV9lbmNyeXB0ZWRfZGF0YQ==",
            envelope_nonce = "c29tZV9ub25jZQ==",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
        )

        val result = repo.decrypt_single_thread_message(item)

        assertEquals("msg_enc", result.id)
        assertTrue(result.is_encrypted)
        assertEquals("", result.sender_name)
        assertEquals("", result.body_text)
    }

    @Test
    fun `fetch_inbox with custom limit passes limit to api`() = runTest {
        coEvery { mail_api.list_messages(limit = eq(25), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(limit = 25)
        coVerify { mail_api.list_messages(limit = 25, any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_inbox with default limit uses 50`() = runTest {
        coEvery { mail_api.list_messages(limit = eq(50), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox()
        coVerify { mail_api.list_messages(limit = 50, any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_sent routes to list_messages with sent type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), item_type = eq("sent"), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_sent()
        assertTrue(result.isSuccess)
        coVerify { mail_api.list_messages(any(), any(), item_type = "sent", any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_drafts routes to list_messages with draft type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), item_type = eq("draft"), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_drafts()
        assertTrue(result.isSuccess)
        coVerify { mail_api.list_messages(any(), any(), item_type = "draft", any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_starred routes with is_starred true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), is_starred = eq(true), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_starred()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_trash routes with is_trashed true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), is_trashed = eq(true), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_trash()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_spam routes with is_spam true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_spam = eq(true), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_spam()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_archive routes with is_archived true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), is_archived = eq(true), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_archive()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_all_for_search error on page 2 propagates`() = runTest {
        val page1_items = (1..50).map { fake_mail_item("p1_$it") }
        coEvery {
            mail_api.list_messages(limit = 50, cursor = isNull(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page1_items, has_more = true, next_cursor = "c1", total = 100)

        coEvery {
            mail_api.list_messages(limit = 50, cursor = eq("c1"), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("page 2 error")

        val result = repo.fetch_all_for_search()

        assertTrue(result.isFailure)
        assertEquals("page 2 error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_all_for_search with empty first page returns empty list`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_all_for_search()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isEmpty())
    }

    @Test
    fun `get_stats error propagates`() = runTest {
        coEvery { mail_api.get_stats() } throws RuntimeException("stats unavailable")

        val result = repo.get_stats()

        assertTrue(result.isFailure)
        assertEquals("stats unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `mark_read error propagates`() = runTest {
        coEvery { mail_api.patch_metadata(any(), any()) } throws RuntimeException("patch error")

        val result = repo.mark_read("item_1", true)

        assertTrue(result.isFailure)
    }

    @Test
    fun `toggle_star error propagates`() = runTest {
        coEvery { mail_api.patch_metadata(any(), any()) } throws RuntimeException("star error")

        val result = repo.toggle_star("item_1", true)

        assertTrue(result.isFailure)
    }

    @Test
    fun `archive error propagates`() = runTest {
        coEvery { mail_api.bulk_action(any()) } throws RuntimeException("archive error")

        val result = repo.archive(listOf("a", "b"))

        assertTrue(result.isFailure)
        assertEquals("archive error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `trash error propagates`() = runTest {
        coEvery { mail_api.bulk_action(any()) } throws RuntimeException("trash error")

        val result = repo.trash(listOf("x"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `mark_spam error propagates`() = runTest {
        coEvery { mail_api.bulk_action(any()) } throws RuntimeException("spam error")

        val result = repo.mark_spam(listOf("s"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `delete_permanent error propagates`() = runTest {
        coEvery { mail_api.delete_permanent(any()) } throws RuntimeException("delete error")

        val result = repo.delete_permanent("item_1")

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetch_inbox has_more and next_cursor are preserved`() = runTest {
        val items = listOf(fake_mail_item("i1"))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = true, next_cursor = "cursor_abc", total = 100)

        val result = repo.fetch_inbox()
        val page = result.getOrThrow()

        assertTrue(page.has_more)
        assertEquals("cursor_abc", page.next_cursor)
        assertEquals(100, page.total)
    }

    @Test
    fun `decrypt_single_thread_message with metadata extracts is_read`() {
        val item = ThreadMessageItem(
            id = "msg_meta",
            item_type = "received",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            metadata = MailItemMetadata(is_read = false),
        )

        val result = repo.decrypt_single_thread_message(item)

        assertFalse(result.is_read)
    }

    @Test
    fun `decrypt_single_thread_message without metadata defaults is_read true`() {
        val item = ThreadMessageItem(
            id = "msg_no_meta",
            item_type = "received",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            metadata = null,
        )

        val result = repo.decrypt_single_thread_message(item)

        assertTrue(result.is_read)
    }

    @Test
    fun `fetch_inbox item without thread_token has null thread_token`() = runTest {
        val item = fake_mail_item("no_thread", thread_token = null)
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val result = repo.fetch_inbox()
        val inbox_item = result.getOrThrow().items[0]

        assertNull(inbox_item.thread_token)
    }

    @Test
    fun `fetch_single_message with metadata preserves flags`() = runTest {
        val item = MailItem(
            id = "flagged_single",
            item_type = "received",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            metadata = MailItemMetadata(
                is_read = true,
                is_starred = true,
                has_attachments = true,
            ),
        )
        coEvery { mail_api.get_message("flagged_single") } returns item

        val result = repo.fetch_single_message("flagged_single")
        val inbox_item = result.getOrThrow()

        assertTrue(inbox_item.is_read)
        assertTrue(inbox_item.is_starred)
        assertTrue(inbox_item.has_attachments)
    }
}
