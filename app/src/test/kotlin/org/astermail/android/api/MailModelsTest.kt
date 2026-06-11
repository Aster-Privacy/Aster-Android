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

package org.astermail.android.api

import org.astermail.android.api.mail.BulkScopeFilter
import org.astermail.android.api.mail.BulkScopeRequest
import org.astermail.android.api.mail.BulkScopeResponse
import org.astermail.android.api.mail.CreateMailItemRequest
import org.astermail.android.api.mail.CreateMailItemResponse
import org.astermail.android.api.mail.DeleteResponse
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemFolder
import org.astermail.android.api.mail.MailItemLabel
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.mail.MailThread
import org.astermail.android.api.mail.MailUserStatsResponse
import org.astermail.android.api.mail.PatchMetadataRequest
import org.astermail.android.api.mail.PatchMetadataResponse
import org.astermail.android.api.mail.SyncMailItemsResponse
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.mail.ThreadWithMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailModelsTest {

    @Test
    fun `MailItemMetadata defaults`() {
        val meta = MailItemMetadata()
        assertFalse(meta.is_read)
        assertFalse(meta.is_starred)
        assertFalse(meta.is_pinned)
        assertFalse(meta.is_trashed)
        assertFalse(meta.is_archived)
        assertFalse(meta.is_spam)
        assertEquals(0L, meta.size_bytes)
        assertFalse(meta.has_attachments)
        assertEquals(0, meta.attachment_count)
        assertNull(meta.scheduled_at)
        assertNull(meta.send_status)
        assertNull(meta.snoozed_until)
        assertNull(meta.trashed_at)
        assertNull(meta.message_ts)
        assertNull(meta.created_at)
        assertNull(meta.updated_at)
        assertNull(meta.item_type)
    }

    @Test
    fun `MailItemMetadata with all fields`() {
        val meta = MailItemMetadata(
            is_read = true,
            is_starred = true,
            is_pinned = true,
            is_trashed = true,
            is_archived = true,
            is_spam = true,
            size_bytes = 1024L,
            has_attachments = true,
            attachment_count = 3,
            scheduled_at = "2026-05-01T00:00:00Z",
            send_status = "sent",
            snoozed_until = "2026-05-02T00:00:00Z",
            trashed_at = "2026-04-20T00:00:00Z",
            message_ts = "2026-04-19T12:00:00Z",
            created_at = "2026-04-19T12:00:00Z",
            updated_at = "2026-04-20T00:00:00Z",
            item_type = "received",
        )
        assertTrue(meta.is_read)
        assertTrue(meta.is_starred)
        assertTrue(meta.is_pinned)
        assertTrue(meta.is_trashed)
        assertTrue(meta.is_archived)
        assertTrue(meta.is_spam)
        assertEquals(1024L, meta.size_bytes)
        assertTrue(meta.has_attachments)
        assertEquals(3, meta.attachment_count)
        assertEquals("2026-05-01T00:00:00Z", meta.scheduled_at)
        assertEquals("sent", meta.send_status)
        assertEquals("2026-05-02T00:00:00Z", meta.snoozed_until)
        assertEquals("2026-04-20T00:00:00Z", meta.trashed_at)
        assertEquals("received", meta.item_type)
    }

    @Test
    fun `MailItemMetadata copy`() {
        val original = MailItemMetadata()
        val modified = original.copy(is_read = true, is_starred = true, size_bytes = 2048L)
        assertTrue(modified.is_read)
        assertTrue(modified.is_starred)
        assertEquals(2048L, modified.size_bytes)
        assertFalse(modified.is_trashed)
    }

    @Test
    fun `MailItemLabel defaults`() {
        val label = MailItemLabel()
        assertNull(label.folder_token)
        assertNull(label.name)
        assertNull(label.color)
    }

    @Test
    fun `MailItemLabel with values`() {
        val label = MailItemLabel(folder_token = "ft_1", name = "Work", color = "#0000FF")
        assertEquals("ft_1", label.folder_token)
        assertEquals("Work", label.name)
        assertEquals("#0000FF", label.color)
    }

    @Test
    fun `MailItemFolder defaults`() {
        val folder = MailItemFolder()
        assertNull(folder.folder_token)
        assertNull(folder.name)
    }

    @Test
    fun `MailItemFolder with values`() {
        val folder = MailItemFolder(folder_token = "ft_inbox", name = "Inbox")
        assertEquals("ft_inbox", folder.folder_token)
        assertEquals("Inbox", folder.name)
    }

    @Test
    fun `MailItem required id only`() {
        val item = MailItem(id = "m1")
        assertEquals("m1", item.id)
        assertNull(item.item_type)
        assertNull(item.encrypted_envelope)
        assertNull(item.envelope_nonce)
        assertNull(item.ephemeral_key)
        assertNull(item.ephemeral_pq_key)
        assertNull(item.sender_sealed)
        assertNull(item.folder_token)
        assertFalse(item.is_external)
        assertNull(item.has_recipient_key)
        assertNull(item.thread_token)
        assertNull(item.thread_message_count)
        assertNull(item.created_at)
        assertNull(item.labels)
        assertNull(item.encrypted_metadata)
        assertNull(item.metadata_nonce)
        assertNull(item.metadata_version)
        assertNull(item.scheduled_at)
        assertNull(item.send_status)
        assertNull(item.message_ts)
        assertNull(item.snoozed_until)
        assertNull(item.is_trashed)
        assertNull(item.is_spam)
        assertNull(item.is_read)
        assertNull(item.folders)
        assertNull(item.tag_tokens)
        assertNull(item.metadata)
        assertNull(item.expires_at)
        assertNull(item.expiry_type)
        assertNull(item.phishing_level)
    }

    @Test
    fun `MailItem with all fields populated`() {
        val label = MailItemLabel(folder_token = "lbl_1", name = "Work", color = "#00F")
        val folder = MailItemFolder(folder_token = "fld_1", name = "Inbox")
        val meta = MailItemMetadata(is_read = true, is_starred = true)
        val item = MailItem(
            id = "m1",
            item_type = "received",
            encrypted_envelope = "enc_env",
            envelope_nonce = "env_nonce",
            ephemeral_key = "eph_key",
            ephemeral_pq_key = "eph_pq",
            sender_sealed = "sealed",
            folder_token = "ft_inbox",
            is_external = true,
            has_recipient_key = true,
            thread_token = "tt_1",
            thread_message_count = 5,
            created_at = "2026-04-26T10:00:00Z",
            labels = listOf(label),
            encrypted_metadata = "enc_meta",
            metadata_nonce = "meta_nonce",
            metadata_version = 2,
            scheduled_at = "2026-05-01T00:00:00Z",
            send_status = "delivered",
            message_ts = "2026-04-26T10:00:00Z",
            snoozed_until = "2026-05-01T00:00:00Z",
            is_trashed = false,
            is_spam = false,
            is_read = true,
            folders = listOf(folder),
            tag_tokens = listOf("tag1", "tag2"),
            metadata = meta,
            expires_at = "2026-06-01T00:00:00Z",
            expiry_type = "auto",
            phishing_level = "none",
        )
        assertEquals("m1", item.id)
        assertEquals("received", item.item_type)
        assertTrue(item.is_external)
        assertTrue(item.has_recipient_key!!)
        assertEquals(5, item.thread_message_count)
        assertEquals(1, item.labels!!.size)
        assertEquals("Work", item.labels!![0].name)
        assertEquals(1, item.folders!!.size)
        assertEquals(2, item.tag_tokens!!.size)
        assertTrue(item.metadata!!.is_read)
        assertEquals("none", item.phishing_level)
    }

    @Test
    fun `MailItem with empty lists`() {
        val item = MailItem(
            id = "m2",
            labels = emptyList(),
            folders = emptyList(),
            tag_tokens = emptyList(),
        )
        assertTrue(item.labels!!.isEmpty())
        assertTrue(item.folders!!.isEmpty())
        assertTrue(item.tag_tokens!!.isEmpty())
    }

    @Test
    fun `MailItem copy`() {
        val original = MailItem(id = "m1", is_external = false)
        val copied = original.copy(is_external = true, thread_token = "tt_new")
        assertTrue(copied.is_external)
        assertEquals("tt_new", copied.thread_token)
        assertEquals("m1", copied.id)
    }

    @Test
    fun `MailItemsListResponse defaults`() {
        val response = MailItemsListResponse()
        assertTrue(response.items.isEmpty())
        assertEquals(0, response.total)
        assertNull(response.next_cursor)
        assertFalse(response.has_more)
    }

    @Test
    fun `MailItemsListResponse with items`() {
        val items = listOf(MailItem(id = "m1"), MailItem(id = "m2"))
        val response = MailItemsListResponse(
            items = items,
            total = 100,
            next_cursor = "cursor_xyz",
            has_more = true,
        )
        assertEquals(2, response.items.size)
        assertEquals(100, response.total)
        assertEquals("cursor_xyz", response.next_cursor)
        assertTrue(response.has_more)
    }

    @Test
    fun `MailThread defaults`() {
        val thread = MailThread(thread_token = "tt_1")
        assertNull(thread.user_id)
        assertEquals("tt_1", thread.thread_token)
        assertNull(thread.encrypted_meta)
        assertNull(thread.meta_nonce)
        assertEquals(0, thread.message_count)
        assertEquals(0, thread.unread_count)
        assertNull(thread.latest_ts)
        assertNull(thread.created_at)
    }

    @Test
    fun `MailThread with all fields`() {
        val thread = MailThread(
            user_id = "u1",
            thread_token = "tt_1",
            encrypted_meta = "enc_meta",
            meta_nonce = "meta_n",
            message_count = 10,
            unread_count = 3,
            latest_ts = "2026-04-26T12:00:00Z",
            created_at = "2026-04-20T08:00:00Z",
        )
        assertEquals("u1", thread.user_id)
        assertEquals(10, thread.message_count)
        assertEquals(3, thread.unread_count)
        assertEquals("2026-04-26T12:00:00Z", thread.latest_ts)
    }

    @Test
    fun `ThreadMessageItem defaults`() {
        val msg = ThreadMessageItem(id = "tm_1")
        assertEquals("tm_1", msg.id)
        assertNull(msg.item_type)
        assertNull(msg.encrypted_envelope)
        assertNull(msg.envelope_nonce)
        assertNull(msg.encrypted_metadata)
        assertNull(msg.metadata_nonce)
        assertNull(msg.metadata_version)
        assertNull(msg.is_external)
        assertNull(msg.has_recipient_key)
        assertNull(msg.ephemeral_key)
        assertNull(msg.ephemeral_pq_key)
        assertNull(msg.send_status)
        assertNull(msg.message_ts)
        assertNull(msg.created_at)
        assertNull(msg.metadata)
    }

    @Test
    fun `ThreadMessageItem with all fields`() {
        val meta = MailItemMetadata(is_read = true, attachment_count = 2)
        val msg = ThreadMessageItem(
            id = "tm_1",
            item_type = "sent",
            encrypted_envelope = "enc",
            envelope_nonce = "nonce",
            encrypted_metadata = "emeta",
            metadata_nonce = "mnonce",
            metadata_version = 3,
            is_external = true,
            has_recipient_key = false,
            ephemeral_key = "ek",
            ephemeral_pq_key = "epq",
            send_status = "delivered",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            metadata = meta,
        )
        assertEquals("sent", msg.item_type)
        assertTrue(msg.is_external!!)
        assertFalse(msg.has_recipient_key!!)
        assertEquals(3, msg.metadata_version)
        assertTrue(msg.metadata!!.is_read)
        assertEquals(2, msg.metadata!!.attachment_count)
    }

    @Test
    fun `ThreadWithMessages defaults`() {
        val tw = ThreadWithMessages()
        assertNull(tw.thread)
        assertTrue(tw.messages.isEmpty())
    }

    @Test
    fun `ThreadWithMessages with data`() {
        val thread = MailThread(thread_token = "tt_1", message_count = 2)
        val messages = listOf(
            ThreadMessageItem(id = "tm_1"),
            ThreadMessageItem(id = "tm_2"),
        )
        val tw = ThreadWithMessages(thread = thread, messages = messages)
        assertEquals("tt_1", tw.thread!!.thread_token)
        assertEquals(2, tw.messages.size)
        assertEquals("tm_2", tw.messages[1].id)
    }

    @Test
    fun `MailUserStatsResponse defaults`() {
        val stats = MailUserStatsResponse()
        assertEquals(0, stats.total_items)
        assertEquals(0, stats.inbox)
        assertEquals(0, stats.sent)
        assertEquals(0, stats.drafts)
        assertEquals(0, stats.scheduled)
        assertEquals(0, stats.starred)
        assertEquals(0, stats.archived)
        assertEquals(0, stats.spam)
        assertEquals(0, stats.trash)
        assertEquals(0, stats.unread)
        assertEquals(0L, stats.storage_used_bytes)
        assertEquals(0L, stats.storage_total_bytes)
    }

    @Test
    fun `MailUserStatsResponse with values`() {
        val stats = MailUserStatsResponse(
            total_items = 500,
            inbox = 200,
            sent = 100,
            drafts = 10,
            scheduled = 5,
            starred = 25,
            archived = 50,
            spam = 30,
            trash = 80,
            unread = 42,
            storage_used_bytes = 536_870_912L,
            storage_total_bytes = 1_073_741_824L,
        )
        assertEquals(500, stats.total_items)
        assertEquals(200, stats.inbox)
        assertEquals(42, stats.unread)
        assertEquals(536_870_912L, stats.storage_used_bytes)
    }

    @Test
    fun `PatchMetadataRequest defaults all null`() {
        val request = PatchMetadataRequest()
        assertNull(request.encrypted_metadata)
        assertNull(request.metadata_nonce)
        assertNull(request.is_read)
        assertNull(request.is_starred)
        assertNull(request.is_pinned)
        assertNull(request.is_trashed)
        assertNull(request.is_archived)
        assertNull(request.is_spam)
    }

    @Test
    fun `PatchMetadataRequest with selective fields`() {
        val request = PatchMetadataRequest(is_read = true, is_starred = false)
        assertTrue(request.is_read!!)
        assertFalse(request.is_starred!!)
        assertNull(request.is_pinned)
    }

    @Test
    fun `PatchMetadataResponse defaults`() {
        val response = PatchMetadataResponse()
        assertFalse(response.success)
        assertEquals(0, response.updated_count)
    }

    @Test
    fun `PatchMetadataResponse with values`() {
        val response = PatchMetadataResponse(success = true, updated_count = 5)
        assertTrue(response.success)
        assertEquals(5, response.updated_count)
    }

    @Test
    fun `BulkScopeFilter defaults all null`() {
        val filter = BulkScopeFilter()
        assertNull(filter.item_type)
        assertNull(filter.is_archived)
        assertNull(filter.is_trashed)
        assertNull(filter.is_spam)
        assertNull(filter.is_starred)
        assertNull(filter.is_snoozed)
    }

    @Test
    fun `BulkScopeFilter with values`() {
        val filter = BulkScopeFilter(
            item_type = "received",
            is_archived = false,
            is_trashed = false,
            is_spam = false,
            is_starred = true,
            is_snoozed = false,
        )
        assertEquals("received", filter.item_type)
        assertFalse(filter.is_archived!!)
        assertTrue(filter.is_starred!!)
    }

    @Test
    fun `BulkScopeRequest with ids`() {
        val request = BulkScopeRequest(
            action = "mark_read",
            ids = listOf("m1", "m2", "m3"),
        )
        assertEquals("mark_read", request.action)
        assertEquals(3, request.ids!!.size)
        assertNull(request.scope)
        assertNull(request.exclude_ids)
    }

    @Test
    fun `BulkScopeRequest with scope and exclude_ids`() {
        val filter = BulkScopeFilter(item_type = "received", is_trashed = false)
        val request = BulkScopeRequest(
            action = "trash",
            scope = filter,
            exclude_ids = listOf("m5"),
        )
        assertEquals("trash", request.action)
        assertNull(request.ids)
        assertEquals("received", request.scope!!.item_type)
        assertEquals(1, request.exclude_ids!!.size)
    }

    @Test
    fun `BulkScopeResponse defaults`() {
        val response = BulkScopeResponse()
        assertNull(response.batch_id)
        assertEquals(0, response.affected_count)
        assertFalse(response.undoable)
    }

    @Test
    fun `BulkScopeResponse with values`() {
        val response = BulkScopeResponse(
            batch_id = "batch_123",
            affected_count = 42,
            undoable = true,
        )
        assertEquals("batch_123", response.batch_id)
        assertEquals(42, response.affected_count)
        assertTrue(response.undoable)
    }

    @Test
    fun `CreateMailItemRequest required fields`() {
        val request = CreateMailItemRequest(
            item_type = "draft",
            encrypted_envelope = "enc_env",
            envelope_nonce = "env_nonce",
        )
        assertEquals("draft", request.item_type)
        assertEquals("enc_env", request.encrypted_envelope)
        assertEquals("env_nonce", request.envelope_nonce)
        assertNull(request.folder_token)
        assertNull(request.content_hash)
        assertNull(request.ephemeral_key)
        assertNull(request.ephemeral_pq_key)
        assertNull(request.sender_sealed)
        assertNull(request.scheduled_at)
        assertNull(request.is_external)
        assertNull(request.thread_token)
        assertNull(request.encrypted_metadata)
        assertNull(request.metadata_nonce)
    }

    @Test
    fun `CreateMailItemRequest with all fields`() {
        val request = CreateMailItemRequest(
            item_type = "draft",
            encrypted_envelope = "enc_env",
            envelope_nonce = "env_nonce",
            folder_token = "ft_drafts",
            content_hash = "sha256_abc",
            ephemeral_key = "ek",
            ephemeral_pq_key = "epq",
            sender_sealed = "sealed",
            scheduled_at = "2026-05-01T00:00:00Z",
            is_external = false,
            thread_token = "tt_1",
            encrypted_metadata = "emeta",
            metadata_nonce = "mnonce",
        )
        assertEquals("ft_drafts", request.folder_token)
        assertEquals("sha256_abc", request.content_hash)
        assertFalse(request.is_external!!)
        assertEquals("tt_1", request.thread_token)
    }

    @Test
    fun `CreateMailItemResponse defaults`() {
        val response = CreateMailItemResponse()
        assertNull(response.id)
        assertFalse(response.success)
    }

    @Test
    fun `CreateMailItemResponse with values`() {
        val response = CreateMailItemResponse(id = "new_m1", success = true)
        assertEquals("new_m1", response.id)
        assertTrue(response.success)
    }

    @Test
    fun `DeleteResponse defaults`() {
        val response = DeleteResponse()
        assertFalse(response.success)
        assertEquals(0, response.deleted_count)
        assertNull(response.status)
    }

    @Test
    fun `DeleteResponse with values`() {
        val response = DeleteResponse(success = true, deleted_count = 3, status = "completed")
        assertTrue(response.success)
        assertEquals(3, response.deleted_count)
        assertEquals("completed", response.status)
    }

    @Test
    fun `SyncMailItemsResponse defaults`() {
        val response = SyncMailItemsResponse()
        assertTrue(response.items.isEmpty())
        assertNull(response.next_cursor)
        assertFalse(response.has_more)
        assertNull(response.sync_token)
    }

    @Test
    fun `SyncMailItemsResponse with data`() {
        val items = listOf(MailItem(id = "m1"), MailItem(id = "m2"))
        val response = SyncMailItemsResponse(
            items = items,
            next_cursor = "sync_cursor",
            has_more = true,
            sync_token = "st_abc",
        )
        assertEquals(2, response.items.size)
        assertEquals("sync_cursor", response.next_cursor)
        assertTrue(response.has_more)
        assertEquals("st_abc", response.sync_token)
    }

    @Test
    fun `MailItemMetadata equality`() {
        val a = MailItemMetadata(is_read = true, size_bytes = 100L)
        val b = MailItemMetadata(is_read = true, size_bytes = 100L)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `MailItem equality`() {
        val a = MailItem(id = "m1", is_external = true)
        val b = MailItem(id = "m1", is_external = true)
        assertEquals(a, b)
    }
}
