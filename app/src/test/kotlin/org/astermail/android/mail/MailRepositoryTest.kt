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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.BulkLabelRequest
import org.astermail.android.api.mail.BulkLabelResponse
import org.astermail.android.api.mail.BulkPatchMetadataRequest
import org.astermail.android.api.mail.BulkPatchMetadataResponse
import org.astermail.android.api.mail.BulkScopeFilter
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
import org.astermail.android.api.mail.AttachmentResponse
import org.astermail.android.api.mail.CreateAttachmentRequestBody
import org.astermail.android.api.send.ExternalAttachmentPayload
import org.astermail.android.api.send.SendApi
import org.astermail.android.api.send.SimpleSendResponse
import io.mockk.slot
import org.astermail.android.api.mail.CreateMailItemResponse
import org.astermail.android.api.mail.DeleteResponse
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.mail.ratchet.RatchetEncryptionException
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.outbox.PendingSendEntity
import org.junit.Assert.assertArrayEquals
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
    private lateinit var snooze_api: org.astermail.android.api.snooze.SnoozeApi
    private lateinit var labels_api: org.astermail.android.api.labels.LabelsApi
    private lateinit var keys_api: org.astermail.android.api.keys.KeysApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var scheduled_api: org.astermail.android.api.scheduled.ScheduledApi
    private lateinit var ratchet_decryptor: org.astermail.android.mail.ratchet.RatchetDecryptor
    private lateinit var ratchet_encryptor: org.astermail.android.mail.ratchet.RatchetEncryptor
    private lateinit var ratchet_plaintext_cache: org.astermail.android.mail.ratchet.RatchetPlaintextCache
    private lateinit var context: android.content.Context
    private lateinit var pending_send_dao: FakePendingSendDao
    private lateinit var repo: MailRepository

    private class FakePendingSendDao : PendingSendDao {
        val rows = java.util.concurrent.ConcurrentHashMap<String, PendingSendEntity>()
        override suspend fun upsert(row: PendingSendEntity) { rows[row.id] = row }
        override suspend fun get_by_id(id: String): PendingSendEntity? = rows[id]
        override suspend fun get_all(): List<PendingSendEntity> = rows.values.toList()
        override suspend fun update_draft_id(id: String, draft_id: String?) {
            rows[id]?.let { rows[id] = it.copy(draft_id = draft_id) }
        }
        override suspend fun mark_sending(id: String, now: Long): Int {
            val row = rows[id] ?: return 0
            if (row.status != "pending") return 0
            rows[id] = row.copy(status = "sending", sending_started_at_ms = now)
            return 1
        }
        override suspend fun claim_stale_sending(id: String, now: Long, stale_before: Long): Int {
            val row = rows[id] ?: return 0
            if (row.status != "sending" || row.sending_started_at_ms >= stale_before) return 0
            rows[id] = row.copy(sending_started_at_ms = now)
            return 1
        }
        override suspend fun mark_pending(id: String) {
            rows[id]?.let { rows[id] = it.copy(status = "pending") }
        }
        override suspend fun mark_failed(id: String) {
            rows[id]?.let { rows[id] = it.copy(status = "failed") }
        }
        override suspend fun delete_by_id(id: String) { rows.remove(id) }
        override suspend fun clear_all() { rows.clear() }
    }

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
        snooze_api = mockk(relaxed = true)
        labels_api = mockk(relaxed = true)
        keys_api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        scheduled_api = mockk(relaxed = true)
        ratchet_decryptor = mockk(relaxed = true)
        ratchet_encryptor = mockk(relaxed = true)
        ratchet_plaintext_cache = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false
        coEvery { mail_api.get_message(any()) } answers { fake_mail_item(firstArg()) }
        pending_send_dao = FakePendingSendDao()
        repo = MailRepository(
            mail_api = mail_api,
            send_api = send_api,
            snooze_api = snooze_api,
            labels_api = labels_api,
            keys_api = keys_api,
            session_key_store = session_key_store,
            scheduled_api = scheduled_api,
            ratchet_decryptor = ratchet_decryptor,
            ratchet_encryptor = ratchet_encryptor,
            ratchet_plaintext_cache = ratchet_plaintext_cache,
            pending_send_dao = pending_send_dao,
            context = context,
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
        )
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = false, next_cursor = null, total = 2)

        val result = repo.fetch_inbox()
        assertTrue(result.isSuccess)

        val page = result.getOrThrow()
        assertEquals(2, page.items.size)
        assertFalse(page.has_more)
        assertNull(page.next_cursor)
    }

    @Test
    fun `fetch_inbox plain inbox excludes archived trashed spam server-side`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox()

        coVerify {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                offset = any(),
                item_type = any(),
                is_starred = any(),
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                label_token = null,
                tag_token = null,
                group_by_thread = any(),
                is_snoozed = any(),
                routing_token = any(),
                order = any(),
                skip_total = any(),
                include_envelope = any(),
            )
        }
    }

    @Test
    fun `fetch_inbox for label does not force archived filter`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(label_token = "work")

        coVerify {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                offset = any(),
                item_type = any(),
                is_starred = any(),
                is_trashed = null,
                is_archived = null,
                is_spam = null,
                label_token = "work",
                tag_token = any(),
                group_by_thread = any(),
                is_snoozed = any(),
                routing_token = any(),
                order = any(),
                skip_total = any(),
                include_envelope = any(),
            )
        }
    }

    @Test
    fun `fetch_inbox label scope paginates by offset and synthesizes next cursor`() = runTest {
        val items = listOf(fake_mail_item("i1"), fake_mail_item("i2"))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = true, next_cursor = null, total = 185)

        val page = repo.fetch_inbox(label_token = "work", offset = 50).getOrThrow()

        assertTrue(page.has_more)
        assertEquals("52", page.next_cursor)
        coVerify {
            mail_api.list_messages(
                limit = any(),
                cursor = null,
                offset = 50,
                item_type = any(),
                is_starred = any(),
                is_trashed = any(),
                is_archived = any(),
                is_spam = any(),
                label_token = "work",
                tag_token = any(),
                group_by_thread = any(),
                is_snoozed = any(),
                routing_token = any(),
                order = any(),
                skip_total = any(),
                include_envelope = any(),
            )
        }
    }

    @Test
    fun `fetch_inbox with null envelope yields empty sender`() = runTest {
        val items = listOf(fake_mail_item("i1", encrypted_envelope = null))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = false, next_cursor = null, total = 1)

        val result = repo.fetch_inbox()
        val item = result.getOrThrow().items[0]

        assertEquals("", item.sender_name)
        assertEquals("", item.sender_email)
        assertEquals("", item.subject)
    }

    @Test
    fun `fetch_inbox propagates api errors`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("api down")

        val result = repo.fetch_inbox()
        assertTrue(result.isFailure)
        assertEquals("api down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_sent delegates to list_messages with sent type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), item_type = eq("sent"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_sent()
        coVerify { mail_api.list_messages(any(), any(), any(), item_type = "sent", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_drafts delegates with draft type`() = runTest {
        coEvery { mail_api.list_drafts(any(), any()) } returns
            org.astermail.android.api.mail.DraftsListResponse(items = emptyList(), next_cursor = null, has_more = false)

        repo.fetch_drafts()
        coVerify { mail_api.list_drafts(any(), any()) }
    }

    @Test
    fun `fetch_starred passes is_starred flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), is_starred = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_starred()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), is_starred = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_trash passes is_trashed flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), is_trashed = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_trash()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), is_trashed = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_spam passes is_spam flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), is_spam = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_spam()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), is_spam = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_archive passes is_archived flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_archived = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_archive()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_archived = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
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
        coVerify { mail_api.bulk_action(BulkScopeRequest(action = "archive", ids = listOf("a", "b", "c"))) }
    }

    @Test
    fun `trash calls bulk_action with trash action`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 2)

        repo.trash(listOf("x", "y"))
        coVerify { mail_api.bulk_action(BulkScopeRequest(action = "trash", ids = listOf("x", "y"))) }
    }

    @Test
    fun `custom label folder supports bulk scope`() {
        assertTrue(repo.folder_supports_bulk_scope("label:work_token"))
        assertTrue(repo.folder_supports_bulk_scope("inbox"))
        assertFalse(repo.folder_supports_bulk_scope("label:"))
        assertFalse(repo.folder_supports_bulk_scope("drafts"))
        assertFalse(repo.folder_supports_bulk_scope("scheduled"))
    }

    @Test
    fun `custom tag folder supports bulk scope`() {
        assertTrue(repo.folder_supports_bulk_scope("tag:work_token"))
        assertFalse(repo.folder_supports_bulk_scope("tag:"))
        assertFalse(repo.folder_supports_bulk_scope("routing:alias_token"))
    }

    @Test
    fun `bulk_scope_action on a tag folder scopes by tag token and excludes trash`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 86)

        val result = repo.bulk_scope_action("tag:work_token", "archive")

        assertEquals(86, result.getOrThrow().affected_count)
        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "archive",
                    scope = BulkScopeFilter(tag_token = "work_token", is_trashed = false),
                ),
            )
        }
    }

    @Test
    fun `archive sends one batched metadata request instead of one per item`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 3)
        val captured = slot<BulkPatchMetadataRequest>()
        coEvery { mail_api.bulk_patch_metadata(capture(captured)) } returns
            BulkPatchMetadataResponse(success = true, updated_count = 3)

        repo.archive(listOf("a", "b", "c"))

        coVerify(exactly = 1) { mail_api.bulk_patch_metadata(any()) }
        coVerify(exactly = 0) { mail_api.patch_metadata(any(), any()) }
        assertEquals(listOf("a", "b", "c"), captured.captured.items.map { it.id })
        assertTrue(captured.captured.items.all { it.is_archived == true })
    }

    @Test
    fun `batched metadata splits into chunks of one hundred`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 250)
        val captured = mutableListOf<BulkPatchMetadataRequest>()
        coEvery { mail_api.bulk_patch_metadata(capture(captured)) } returns
            BulkPatchMetadataResponse(success = true, updated_count = 100)

        repo.archive((1..250).map { "item_$it" })

        assertEquals(listOf(100, 100, 50), captured.map { it.items.size })
    }

    @Test
    fun `a failed metadata batch falls back to per item patches`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 2)
        coEvery { mail_api.bulk_patch_metadata(any()) } throws RuntimeException("batch rejected")

        val result = repo.unarchive(listOf("a", "b"))

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { mail_api.patch_metadata("a", any()) }
        coVerify(exactly = 1) { mail_api.patch_metadata("b", any()) }
    }

    @Test
    fun `unarchive fails when both the batch and the per item fallback fail`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 2)
        coEvery { mail_api.bulk_patch_metadata(any()) } throws RuntimeException("batch rejected")
        coEvery { mail_api.patch_metadata(any(), any()) } throws RuntimeException("patch rejected")

        val result = repo.unarchive(listOf("a", "b"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `star_bulk batches the metadata patch for every selected item`() = runTest {
        val captured = slot<BulkPatchMetadataRequest>()
        coEvery { mail_api.bulk_patch_metadata(capture(captured)) } returns
            BulkPatchMetadataResponse(success = true, updated_count = 2)

        val result = repo.star_bulk(listOf("a", "b"), true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { mail_api.patch_metadata(any(), any()) }
        assertTrue(captured.captured.items.all { it.is_starred == true })
    }

    @Test
    fun `star_scope sends the star action scoped to the folder`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 4200)

        val result = repo.star_scope("inbox", true)

        assertEquals(4200, result.getOrThrow().affected_count)
        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(action = "star", scope = BulkScopeFilter(item_type = "received")),
            )
        }
    }

    @Test
    fun `star_scope sends the unstar action when clearing stars`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 12)

        repo.star_scope("starred", false)

        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(action = "unstar", scope = BulkScopeFilter(is_starred = true)),
            )
        }
    }

    @Test
    fun `add_label_bulk chunks ids and reports no failures on success`() = runTest {
        val captured = mutableListOf<BulkLabelRequest>()
        coEvery { mail_api.bulk_add_label(capture(captured)) } returns BulkLabelResponse("ok", 100)

        val failed = repo.add_label_bulk((1..150).map { "item_$it" }, "work_token")

        assertTrue(failed.isEmpty())
        assertEquals(listOf(100, 50), captured.map { it.ids.size })
        assertTrue(captured.all { it.label_token == "work_token" })
    }

    @Test
    fun `add_label_bulk falls back per item and reports the ids that failed`() = runTest {
        coEvery { mail_api.bulk_add_label(any()) } throws RuntimeException("bulk unavailable")
        coEvery { mail_api.add_label_to_item("b", any()) } throws RuntimeException("nope")

        val failed = repo.add_label_bulk(listOf("a", "b"), "work_token")

        assertEquals(setOf("b"), failed)
        coVerify(exactly = 1) { mail_api.add_label_to_item("a", "work_token") }
    }

    @Test
    fun `add_tag_bulk falls back per item and reports the ids that failed`() = runTest {
        coEvery { mail_api.bulk_add_tag(any()) } throws RuntimeException("bulk unavailable")
        coEvery { mail_api.add_tag_to_item("y", any()) } throws RuntimeException("nope")

        val failed = repo.add_tag_bulk(listOf("x", "y"), "tag_token")

        assertEquals(setOf("y"), failed)
        coVerify(exactly = 1) { mail_api.add_tag_to_item("x", "tag_token") }
    }

    @Test
    fun `mark_all_read_scope on a tag folder scopes by tag token`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 9)

        repo.mark_all_read_scope("tag:work_token")

        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "mark_read",
                    scope = BulkScopeFilter(tag_token = "work_token", is_trashed = false),
                ),
            )
        }
    }

    @Test
    fun `bulk_scope_action on a label folder scopes by label token and excludes trash`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 180)

        val result = repo.bulk_scope_action("label:work_token", "trash")

        assertEquals(180, result.getOrThrow().affected_count)
        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "trash",
                    scope = BulkScopeFilter(label_token = "work_token", is_trashed = false),
                ),
            )
        }
    }

    @Test
    fun `mark_all_read_scope on a label folder scopes by label token`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 12)

        repo.mark_all_read_scope("label:work_token")

        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "mark_read",
                    scope = BulkScopeFilter(label_token = "work_token", is_trashed = false),
                ),
            )
        }
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
            mail_api.list_messages(limit = 200, cursor = isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page1_items, has_more = true, next_cursor = "c1", total = 60)

        coEvery {
            mail_api.list_messages(limit = 200, cursor = eq("c1"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page2_items, has_more = false, next_cursor = null, total = 60)

        val result = repo.fetch_all_for_search()
        assertTrue(result.isSuccess)
        assertEquals(60, result.getOrThrow().size)
    }

    @Test
    fun `fetch_all_for_search stops at max_pages`() = runTest {
        val page1_items = (1..50).map { fake_mail_item("p1_$it") }
        val page2_items = (1..50).map { fake_mail_item("p2_$it") }
        coEvery {
            mail_api.list_messages(any(), cursor = isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page1_items, has_more = true, next_cursor = "next", total = 1000)
        coEvery {
            mail_api.list_messages(any(), cursor = eq("next"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page2_items, has_more = true, next_cursor = "next2", total = 1000)

        val result = repo.fetch_all_for_search(max_pages = 2)
        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrThrow().size)
    }

    @Test
    fun `fetch_inbox with cursor passes cursor to api`() = runTest {
        coEvery { mail_api.list_messages(any(), cursor = eq("my_cursor"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(cursor = "my_cursor")
        coVerify { mail_api.list_messages(any(), cursor = "my_cursor", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_inbox with label_token passes it to api`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), label_token = eq("lbl_abc"), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(label_token = "lbl_abc")
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), label_token = "lbl_abc", any(), any(), any(), any(), any(), any(), any()) }
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"

        val result = repo.send_email(
            to = listOf("recipient@astermail.org"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isSuccess)
        coVerify { send_api.send_simple(any()) }
    }

    @Test
    fun `send_email fails closed for internal recipient without ratchet keys`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns false

        val result = repo.send_email(
            to = listOf("recipient@astermail.org"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { send_api.send_simple(any()) }
    }

    @Test
    fun `save_draft delegates to mail_api create_draft`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_99", success = true)

        val result = repo.save_draft(
            subject = "Draft subject",
            body_html = "<p>draft</p>",
        )

        assertTrue(result.isSuccess)
        assertEquals("draft_99", result.getOrThrow())
        coVerify { mail_api.create_draft(any()) }
        coVerify(exactly = 0) { mail_api.create_message(any()) }
    }

    @Test
    fun `save_draft forwards reply metadata and replaces previous draft`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        val captured = slot<org.astermail.android.api.mail.CreateDraftRequestBody>()
        coEvery { mail_api.create_draft(capture(captured)) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_new", success = true)
        coEvery { mail_api.delete_draft(any()) } returns
            org.astermail.android.api.mail.DeleteResponse(success = true, deleted_count = 1)

        val result = repo.save_draft(
            subject = "Reply draft",
            body_html = "<p>reply</p>",
            existing_draft_id = "draft_old",
            draft_type = "reply_all",
            reply_to_id = "550e8400-e29b-41d4-a716-446655440000",
            thread_token = "thread_abc",
        )

        assertTrue(result.isSuccess)
        assertEquals("reply", captured.captured.draft_type)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", captured.captured.reply_to_id)
        assertEquals("thread_abc", captured.captured.thread_token)
        coVerify { mail_api.delete_draft("draft_old") }
    }

    @Test
    fun `save_draft drops non uuid reply_to_id`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        val captured = slot<org.astermail.android.api.mail.CreateDraftRequestBody>()
        coEvery { mail_api.create_draft(capture(captured)) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_new", success = true)

        val result = repo.save_draft(
            subject = "Draft",
            body_html = "<p>x</p>",
            draft_type = "forward",
            reply_to_id = "not-a-uuid",
        )

        assertTrue(result.isSuccess)
        assertEquals("forward", captured.captured.draft_type)
        assertEquals(null, captured.captured.reply_to_id)
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

    private val draft_uuid = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `save_draft updates the same draft instead of creating another`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        val update_versions = mutableListOf<Int>()
        coEvery { mail_api.update_draft(draft_uuid, any()) } answers {
            update_versions.add(
                secondArg<org.astermail.android.api.mail.UpdateDraftRequestBody>().version,
            )
            org.astermail.android.api.mail.UpdateDraftResponse(
                success = true,
                version = update_versions.size + 1,
            )
        }

        val first = repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_1")
        val second = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_1")
        val third = repo.save_draft(subject = "Hello", body_html = "<p>123</p>", session_id = "compose_1")

        assertEquals(draft_uuid, first.getOrThrow())
        assertEquals(draft_uuid, second.getOrThrow())
        assertEquals(draft_uuid, third.getOrThrow())
        assertEquals(listOf(1, 2), update_versions)
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
        coVerify(exactly = 2) { mail_api.update_draft(draft_uuid, any()) }
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `save_draft reuses the session draft when the caller passes a stale id`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        coEvery { mail_api.update_draft(draft_uuid, any()) } returns
            org.astermail.android.api.mail.UpdateDraftResponse(success = true, version = 2)

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_2")
        val second = repo.save_draft(
            subject = "Hello",
            body_html = "<p>12</p>",
            existing_draft_id = null,
            session_id = "compose_2",
        )

        assertEquals(draft_uuid, second.getOrThrow())
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
    }

    @Test
    fun `save_draft retries the update once after a version conflict`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        val versions = mutableListOf<Int>()
        coEvery { mail_api.update_draft(draft_uuid, any()) } answers {
            val body = secondArg<org.astermail.android.api.mail.UpdateDraftRequestBody>()
            versions.add(body.version)
            if (body.version == 1) {
                org.astermail.android.api.mail.UpdateDraftResponse(
                    success = false,
                    version = 1,
                    current_version = 7,
                )
            } else {
                org.astermail.android.api.mail.UpdateDraftResponse(success = true, version = 8)
            }
        }

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_3")
        val second = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_3")

        assertEquals(draft_uuid, second.getOrThrow())
        assertEquals(listOf(1, 7), versions)
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
    }

    @Test
    fun `save_draft recreates the draft when the server no longer has it`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        val second_uuid = "99999999-2222-3333-4444-555555555555"
        var created = 0
        coEvery { mail_api.create_draft(any()) } answers {
            created += 1
            org.astermail.android.api.mail.CreateDraftResponse(
                id = if (created == 1) draft_uuid else second_uuid,
                version = 1,
            )
        }
        coEvery { mail_api.update_draft(draft_uuid, any()) } throws
            org.astermail.android.api.ApiError.NotFoundError

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_4")
        val second = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_4")

        assertEquals(second_uuid, second.getOrThrow())
        assertEquals(2, created)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `save_draft keeps a single draft when the caller is cancelled mid save`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        val gate = CompletableDeferred<Unit>()
        coEvery { mail_api.create_draft(any()) } coAnswers {
            gate.await()
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        }
        coEvery { mail_api.update_draft(draft_uuid, any()) } returns
            org.astermail.android.api.mail.UpdateDraftResponse(success = true, version = 2)

        var assigned: String? = null
        val job = launch {
            repo.save_draft(
                subject = "Hello",
                body_html = "<p>1</p>",
                session_id = "compose_5",
                on_id_assigned = { assigned = it },
            )
        }
        advanceUntilIdle()
        job.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(draft_uuid, assigned)

        val second = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_5")

        assertEquals(draft_uuid, second.getOrThrow())
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
        coVerify(exactly = 1) { mail_api.update_draft(draft_uuid, any()) }
    }

    @Test
    fun `release_draft_session drops the session mapping`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        val second_uuid = "88888888-2222-3333-4444-555555555555"
        var created = 0
        coEvery { mail_api.create_draft(any()) } answers {
            created += 1
            org.astermail.android.api.mail.CreateDraftResponse(
                id = if (created == 1) draft_uuid else second_uuid,
                version = 1,
            )
        }

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_6")
        repo.release_draft_session("compose_6")
        val second = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_6")

        assertEquals(second_uuid, second.getOrThrow())
        coVerify(exactly = 0) { mail_api.update_draft(any(), any()) }
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

        val result = kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(item) }

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

        val result = kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(item) }

        assertEquals("msg_enc", result.id)
        assertTrue(result.is_encrypted)
        assertEquals("", result.sender_name)
        assertEquals("", result.body_text)
    }

    @Test
    fun `fetch_inbox with custom limit passes limit to api`() = runTest {
        coEvery { mail_api.list_messages(limit = eq(25), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(limit = 25)
        coVerify { mail_api.list_messages(limit = 25, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_inbox with default limit uses 50`() = runTest {
        coEvery { mail_api.list_messages(limit = eq(50), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox()
        coVerify { mail_api.list_messages(limit = 50, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_sent routes to list_messages with sent type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), item_type = eq("sent"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_sent()
        assertTrue(result.isSuccess)
        coVerify { mail_api.list_messages(any(), any(), any(), item_type = "sent", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_drafts routes to list_messages with draft type`() = runTest {
        coEvery { mail_api.list_drafts(any(), any()) } returns
            org.astermail.android.api.mail.DraftsListResponse(items = emptyList(), next_cursor = null, has_more = false)

        val result = repo.fetch_drafts()
        assertTrue(result.isSuccess)
        coVerify { mail_api.list_drafts(any(), any()) }
    }

    @Test
    fun `fetch_starred routes with is_starred true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), is_starred = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_starred()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_trash routes with is_trashed true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), is_trashed = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_trash()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_spam routes with is_spam true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), is_spam = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_spam()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_archive routes with is_archived true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_archived = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_archive()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_all_for_search error on page 2 propagates`() = runTest {
        val page1_items = (1..50).map { fake_mail_item("p1_$it") }
        coEvery {
            mail_api.list_messages(limit = 200, cursor = isNull(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(page1_items, has_more = true, next_cursor = "c1", total = 100)

        coEvery {
            mail_api.list_messages(limit = 200, cursor = eq("c1"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws RuntimeException("page 2 error")

        val result = repo.fetch_all_for_search()

        assertTrue(result.isFailure)
        assertEquals("page 2 error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_all_for_search with empty first page returns empty list`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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

        val result = kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(item) }

        assertFalse(result.is_read)
    }

    @Test
    fun `decrypt_single_thread_message without metadata defaults received to unread`() {
        val item = ThreadMessageItem(
            id = "msg_no_meta",
            item_type = "received",
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            metadata = null,
        )

        val result = kotlinx.coroutines.runBlocking { repo.decrypt_single_thread_message(item) }

        assertFalse(result.is_read)
    }

    @Test
    fun `fetch_inbox item without thread_token has null thread_token`() = runTest {
        val item = fake_mail_item("no_thread", thread_token = null)
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val result = repo.fetch_inbox()
        val inbox_item = result.getOrThrow().items[0]

        assertNull(inbox_item.thread_token)
    }

    @Test
    fun `server is_read true overrides stale metadata is_read false`() = runTest {
        val item = MailItem(
            id = "stuck_unread",
            item_type = "received",
            encrypted_envelope = null,
            envelope_nonce = null,
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            is_read = true,
            metadata = MailItemMetadata(is_read = false),
        )
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val inbox_item = repo.fetch_inbox().getOrThrow().items[0]

        assertTrue(inbox_item.is_read)
    }

    @Test
    fun `unread when both server and metadata are unread`() = runTest {
        val item = MailItem(
            id = "genuinely_unread",
            item_type = "received",
            encrypted_envelope = null,
            envelope_nonce = null,
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            is_read = false,
            metadata = MailItemMetadata(is_read = false),
        )
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val inbox_item = repo.fetch_inbox().getOrThrow().items[0]

        assertFalse(inbox_item.is_read)
    }

    @Test
    fun `server unread flag wins over stale read metadata`() = runTest {
        val item = MailItem(
            id = "meta_read",
            item_type = "received",
            encrypted_envelope = null,
            envelope_nonce = null,
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            is_read = false,
            metadata = MailItemMetadata(is_read = true),
        )
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val inbox_item = repo.fetch_inbox().getOrThrow().items[0]

        assertFalse(inbox_item.is_read)
    }

    @Test
    fun `fresh delivered item with no metadata uses server unread flag`() = runTest {
        val item = MailItem(
            id = "fresh",
            item_type = "received",
            encrypted_envelope = null,
            envelope_nonce = null,
            message_ts = "2026-04-26T10:00:00Z",
            created_at = "2026-04-26T10:00:00Z",
            is_read = false,
            metadata = null,
        )
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = listOf(item), has_more = false, next_cursor = null, total = 1)

        val inbox_item = repo.fetch_inbox().getOrThrow().items[0]

        assertFalse(inbox_item.is_read)
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

    private fun pending_row(
        id: String,
        status: String = "pending",
        draft_id: String? = null,
        to: String = "friend@astermail.org",
        fire_at_ms: Long = 0L,
    ): PendingSendEntity = PendingSendEntity(
        id = id,
        to_json = "[\"$to\"]",
        cc_json = "[]",
        bcc_json = "[]",
        subject = "Subject",
        body_html = "<p>body</p>",
        sender_email = "me@astermail.org",
        sender_display_name = null,
        thread_token = null,
        expires_at = null,
        expiry_password = null,
        attachments_json = "[]",
        sender_alias_hash = null,
        suppress_branding = null,
        draft_id = draft_id,
        fire_at_ms = fire_at_ms,
        status = status,
        created_at_ms = 0L,
    )

    private fun wait_until(timeout_ms: Long = 2000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeout_ms
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `persist_and_schedule_undo_send stores a pending row and writes a safety draft`() = runTest {
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "safety_draft_1", success = true)

        repo.persist_and_schedule_undo_send(
            pending_id = "pend_1",
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Hi",
            body_html = "<p>hello</p>",
            sender_email = "me@astermail.org",
            sender_display_name = null,
            thread_token = null,
            expires_at = null,
            expiry_password = null,
            attachments = emptyList(),
            sender_alias_hash = null,
            suppress_branding = null,
            delay_ms = 10_000L,
            draft_id = null,
        )

        val row = pending_send_dao.get_by_id("pend_1")
        assertNotNull(row)
        assertEquals("pending", row!!.status)
        assertEquals("safety_draft_1", row.draft_id)
        coVerify { mail_api.create_draft(any()) }
    }

    @Test
    fun `run_pending_send delivers once and reports gone on a second run`() = runTest {
        pending_send_dao.upsert(pending_row("pend_2", draft_id = "draft_2"))
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_simple(any()) } returns SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_2")
        coEvery { mail_api.delete_draft(any()) } returns DeleteResponse(success = true, deleted_count = 1)

        val first = repo.run_pending_send("pend_2")

        assertEquals(PendingSendOutcome.SENT, first)
        assertNull(pending_send_dao.get_by_id("pend_2"))
        coVerify(exactly = 1) { send_api.send_simple(any()) }
        coVerify { mail_api.delete_draft("draft_2") }

        val second = repo.run_pending_send("pend_2")

        assertEquals(PendingSendOutcome.GONE, second)
        coVerify(exactly = 1) { send_api.send_simple(any()) }
    }

    @Test
    fun `run_pending_send keeps the row and draft when the send throws`() = runTest {
        pending_send_dao.upsert(pending_row("pend_3", draft_id = "draft_3"))
        coEvery { send_api.send_simple(any()) } throws RuntimeException("network down")

        val outcome = repo.run_pending_send("pend_3")

        assertEquals(PendingSendOutcome.RETRY, outcome)
        assertEquals("pending", pending_send_dao.get_by_id("pend_3")?.status)
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `run_pending_send keeps the row and draft when the server rejects the send`() = runTest {
        pending_send_dao.upsert(pending_row("pend_4", draft_id = "draft_4"))
        coEvery { send_api.send_simple(any()) } returns SimpleSendResponse(success = false, message = "rejected", mail_item_id = null)

        val outcome = repo.run_pending_send("pend_4")

        assertEquals(PendingSendOutcome.RETRY, outcome)
        assertEquals("pending", pending_send_dao.get_by_id("pend_4")?.status)
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `run_pending_send marks failed when recipient prekey bundle is missing`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } throws
            RatchetEncryptionException("friend@astermail.org", "no prekey bundle available for recipient")
        pending_send_dao.upsert(pending_row("pend_perm", draft_id = "draft_perm"))

        val outcome = repo.run_pending_send("pend_perm")

        assertEquals(PendingSendOutcome.FAILED, outcome)
        assertEquals("failed", pending_send_dao.get_by_id("pend_perm")?.status)
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `run_pending_send does nothing for an already-undone send`() = runTest {
        val outcome = repo.run_pending_send("never_persisted")

        assertEquals(PendingSendOutcome.GONE, outcome)
        coVerify(exactly = 0) { send_api.send_simple(any()) }
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `undo cancels a scheduled send without deleting the draft`() = runTest {
        coEvery { mail_api.create_message(any()) } returns CreateMailItemResponse(id = "safety_draft_6", success = true)

        repo.schedule_send_with_undo(
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Hi",
            body_html = "<p>hello</p>",
            sender_email = "me@astermail.org",
            sender_display_name = null,
            undo_seconds = 10,
            draft_id = null,
        )

        val pending = repo.pending_undo_send.value
        assertNotNull(pending)
        pending!!.undo()

        wait_until { pending_send_dao.rows.isEmpty() }

        assertNull(repo.pending_undo_send.value)
        assertTrue(pending_send_dao.rows.isEmpty())
        coVerify(exactly = 0) { send_api.send_simple(any()) }
        coVerify(exactly = 0) { send_api.send_external(any()) }
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `signal_new_mail emits on new_mail_events`() = runTest {
        val received = java.util.concurrent.atomic.AtomicInteger(0)
        val collector_scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        val job = collector_scope.launch {
            repo.new_mail_events.collect { received.incrementAndGet() }
        }

        Thread.sleep(150)
        repo.signal_new_mail()
        repo.signal_new_mail()

        wait_until { received.get() >= 2 }
        assertTrue(received.get() >= 2)
        job.cancel()
    }

    @Test
    fun `signal_new_mail without collectors does not throw`() {
        repo.signal_new_mail()
    }

    @Test
    fun `list_notifiable_folders excludes system folders and folders with no unread`() = runTest {
        val custom_with_unread = org.astermail.android.api.labels.LabelItem(
            id = "l1",
            label_token = "folder_work",
            is_system = false,
            unread_count = 3,
        )
        val custom_without_unread = org.astermail.android.api.labels.LabelItem(
            id = "l2",
            label_token = "folder_empty",
            is_system = false,
            unread_count = 0,
        )
        val custom_null_unread = org.astermail.android.api.labels.LabelItem(
            id = "l3",
            label_token = "folder_null",
            is_system = false,
            unread_count = null,
        )
        val system_with_unread = org.astermail.android.api.labels.LabelItem(
            id = "l4",
            label_token = "spam",
            is_system = true,
            unread_count = 5,
        )
        coEvery { labels_api.list_labels(include_counts = true) } returns
            org.astermail.android.api.labels.LabelsListResponse(
                labels = listOf(custom_with_unread, custom_without_unread, custom_null_unread, system_with_unread),
            )

        val result = repo.list_notifiable_folders().getOrNull()

        assertNotNull(result)
        assertEquals(listOf("folder_work"), result!!.map { it.label_token })
    }

    @Test
    fun `list_notifiable_folders returns failure when labels_api throws`() = runTest {
        coEvery { labels_api.list_labels(include_counts = true) } throws RuntimeException("network error")

        val result = repo.list_notifiable_folders()

        assertTrue(result.isFailure)
    }

    @Test
    fun `link_sender_attachments round-trips filename and bytes with backend-legal nonces`() = runTest {
        every { session_key_store.get_passphrase() } answers {
            "correct horse battery staple".toByteArray(Charsets.UTF_8)
        }
        val raw_bytes = ByteArray(4096) { (it % 251).toByte() }
        val payload = ExternalAttachmentPayload(
            data = java.util.Base64.getEncoder().encodeToString(raw_bytes),
            filename = "book.epub",
            content_type = "application/epub+zip",
            size_bytes = raw_bytes.size.toLong(),
        )
        val captured = slot<CreateAttachmentRequestBody>()
        coEvery { mail_api.create_attachment("sent_1", capture(captured)) } answers {
            val body = captured.captured
            AttachmentResponse(
                id = "att_1",
                mail_item_id = "sent_1",
                encrypted_data = body.encrypted_data,
                data_nonce = body.data_nonce,
                encrypted_meta = body.encrypted_meta,
                meta_nonce = body.meta_nonce,
                size_bytes = body.encrypted_data.length.toLong(),
                seq_num = body.seq_num ?: 0,
            )
        }

        repo.link_sender_attachments("sent_1", listOf(payload))

        assertTrue("create_attachment must have been called", captured.isCaptured)
        val body = captured.captured
        assertEquals(
            "backend requires a 12-byte data_nonce",
            12,
            java.util.Base64.getDecoder().decode(body.data_nonce).size,
        )
        assertEquals(
            "backend requires a 12-byte meta_nonce",
            12,
            java.util.Base64.getDecoder().decode(body.meta_nonce).size,
        )

        val meta = repo.decrypt_attachment_meta(body.encrypted_meta, body.meta_nonce)
        assertNotNull("sent-copy attachment meta must decrypt", meta)
        assertEquals("book.epub", meta!!.filename)
        assertEquals("application/epub+zip", meta.content_type)

        val decrypted = repo.decrypt_attachment_data(body.encrypted_data, body.data_nonce, meta.session_key)
        assertArrayEquals("sent-copy attachment bytes must round-trip", raw_bytes, decrypted)
    }

    @Test
    fun `reconcile_pending_sends counts failed rows and raises the send problem`() = runTest {
        pending_send_dao.rows["p_failed"] = pending_row("p_failed", status = "failed")
        pending_send_dao.rows["p_ok"] = pending_row("p_ok", status = "pending")

        repo.reconcile_pending_sends()

        assertTrue(repo.send_problem.value)
        assertEquals(1, repo.failed_send_count.value)
    }

    @Test
    fun `retry_failed_sends returns failed rows to pending and clears the problem`() = runTest {
        pending_send_dao.rows["p_failed"] = pending_row("p_failed", status = "failed")
        repo.reconcile_pending_sends()

        repo.retry_failed_sends()

        assertEquals("pending", pending_send_dao.rows["p_failed"]?.status)
        assertEquals(0, repo.failed_send_count.value)
        assertFalse(repo.send_problem.value)
    }

    @Test
    fun `discard_failed_sends deletes only the failed rows`() = runTest {
        pending_send_dao.rows["p_failed"] = pending_row("p_failed", status = "failed")
        pending_send_dao.rows["p_ok"] = pending_row("p_ok", status = "pending")
        repo.reconcile_pending_sends()

        repo.discard_failed_sends()

        assertNull(pending_send_dao.rows["p_failed"])
        assertNotNull(pending_send_dao.rows["p_ok"])
        assertEquals(0, repo.failed_send_count.value)
        assertFalse(repo.send_problem.value)
    }
}
