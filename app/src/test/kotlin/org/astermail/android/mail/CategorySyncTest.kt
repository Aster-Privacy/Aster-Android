//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the AGPLv3 as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// AGPLv3 for more details.
//
// You should have received a copy of the AGPLv3
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

package org.astermail.android.mail

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.MailItemMetadata
import org.astermail.android.api.mail.MailItemsListResponse
import org.astermail.android.api.mail.PatchMetadataRequest
import org.astermail.android.api.mail.PatchMetadataResponse
import org.astermail.android.crypto.AesGcm
import org.astermail.android.crypto.hkdf_sha256
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest

class CategorySyncTest {

    private val passphrase = "category sync passphrase"
    private val json = Json { ignoreUnknownKeys = true }

    private lateinit var mail_api: MailApi
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
        every { android.util.Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg<ByteArray>())
        }
        val session_key_store = mockk<SessionKeyStore>(relaxed = true)
        every { session_key_store.get_passphrase() } answers { passphrase.toByteArray(Charsets.UTF_8) }
        every { session_key_store.get_user_email() } returns "me@example.test"
        every { session_key_store.has_ratchet_keys() } returns false
        mail_api = mockk(relaxed = true)
        repo = MailRepository(
            mail_api = mail_api,
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = session_key_store,
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            system_folder_bootstrap = mockk(relaxed = true),
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = mockk(relaxed = true),
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
        )
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    private fun metadata_key(): ByteArray {
        val secret = passphrase.toByteArray(Charsets.UTF_8)
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("aster-hkdf-salt-v1:".toByteArray(Charsets.UTF_8) + secret)
        val master = hkdf_sha256(secret, salt, "aster-storage-encryption-key-v1".toByteArray(Charsets.UTF_8), 32)
        return hkdf_sha256(
            master,
            "aster-metadata-salt-v1".toByteArray(Charsets.UTF_8),
            "aster-metadata-encryption-v1:mail-item-metadata".toByteArray(Charsets.UTF_8),
            32,
        )
    }

    private fun seal_like_web(blob_json: String): Pair<String, String> {
        val nonce = ByteArray(12) { (it + 1).toByte() }
        val sealed = AesGcm.encrypt(metadata_key(), nonce, blob_json.toByteArray(Charsets.UTF_8))
        val encoder = java.util.Base64.getEncoder()
        return encoder.encodeToString(sealed) to encoder.encodeToString(nonce)
    }

    private fun open_blob(encrypted: String, nonce: String): MailItemMetadata {
        val decoder = java.util.Base64.getDecoder()
        val plain = AesGcm.decrypt(metadata_key(), decoder.decode(nonce), decoder.decode(encrypted))
        return json.decodeFromString(MailItemMetadata.serializer(), String(plain, Charsets.UTF_8))
    }

    private val moved_to_travel_on_web = """
        {"is_read":false,"is_starred":false,"is_pinned":false,"is_trashed":false,
        "is_archived":false,"is_spam":false,"size_bytes":4096,"has_attachments":false,
        "attachment_count":0,"message_ts":"2026-09-30T08:00:00.000Z",
        "created_at":"2026-09-30T08:00:00.000Z","updated_at":"2026-10-01T09:30:00.000Z",
        "item_type":"received","category":"travel","category_pinned":true}
    """.trimIndent()

    private fun server_item(with_server_metadata: Boolean): MailItem {
        val (encrypted, nonce) = seal_like_web(moved_to_travel_on_web)
        return MailItem(
            id = "deal-1",
            item_type = "received",
            thread_token = "thread-deal-1",
            thread_message_count = 1,
            message_ts = "2026-09-30T08:00:00Z",
            created_at = "2026-09-30T08:00:00Z",
            encrypted_metadata = encrypted,
            metadata_nonce = nonce,
            metadata_version = 2,
            is_read = false,
            metadata = if (with_server_metadata) MailItemMetadata(is_read = false) else null,
        )
    }

    private fun list_returns(vararg items: MailItem) {
        coEvery {
            mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns MailItemsListResponse(items = items.toList(), has_more = false, next_cursor = null, total = items.size)
    }

    private fun capture_patch(): io.mockk.CapturingSlot<PatchMetadataRequest> {
        val captured = slot<PatchMetadataRequest>()
        coEvery { mail_api.patch_metadata(any(), capture(captured)) } returns PatchMetadataResponse()
        return captured
    }

    @Test
    fun `a category pinned on another client reaches the list even when the server sends flag metadata`() = runTest {
        list_returns(server_item(with_server_metadata = true))

        val meta = repo.fetch_inbox().getOrThrow().items.single().raw_item.metadata

        assertNotNull(meta)
        assertEquals("travel", meta!!.category)
        assertTrue(meta.category_pinned)
    }

    @Test
    fun `a category pinned on another client reaches the list from the encrypted metadata alone`() = runTest {
        list_returns(server_item(with_server_metadata = false))

        val meta = repo.fetch_inbox().getOrThrow().items.single().raw_item.metadata

        assertEquals("travel", meta?.category)
        assertEquals(true, meta?.category_pinned)
    }

    @Test
    fun `server flag metadata still applies when the item has no encrypted metadata`() = runTest {
        list_returns(
            MailItem(
                id = "plain-1",
                item_type = "received",
                message_ts = "2026-09-30T08:00:00Z",
                metadata = MailItemMetadata(is_read = true, is_starred = true),
            ),
        )

        val item = repo.fetch_inbox().getOrThrow().items.single()

        assertTrue(item.is_read)
        assertTrue(item.is_starred)
    }

    @Test
    fun `marking read keeps a category pinned on another client`() = runTest {
        val captured = capture_patch()

        repo.mark_read("deal-1", true, server_item(with_server_metadata = true)).getOrThrow()

        val request = captured.captured
        assertEquals(true, request.is_read)
        val written = open_blob(request.encrypted_metadata!!, request.metadata_nonce!!)
        assertEquals("travel", written.category)
        assertTrue(written.category_pinned)
        assertTrue(written.is_read)
        assertEquals(4096L, written.size_bytes)
    }

    @Test
    fun `starring a row restored from the folder cache does not overwrite the encrypted metadata`() = runTest {
        val captured = capture_patch()
        val listed = InboxItem(
            id = "deal-1",
            thread_token = "thread-deal-1",
            thread_message_count = 1,
            sender_name = "Example Deals",
            sender_email = "offers@deals.example.test",
            subject = "Weekend offer",
            preview = "Synthetic preview",
            timestamp = "2026-09-30T08:00:00Z",
            is_read = false,
            is_starred = false,
            is_encrypted = true,
            has_attachments = false,
            is_trashed = false,
            is_archived = false,
            is_spam = false,
            labels = emptyList(),
            category = "promotions",
            raw_item = MailItem(id = "deal-1", item_type = "received"),
        )
        val cached_row = folder_cache_rows("inbox", listOf(listed), cached_at = 0L).single().to_inbox_item()

        repo.toggle_star("deal-1", true, cached_row.raw_item).getOrThrow()

        val request = captured.captured
        assertEquals(true, request.is_starred)
        assertNull(request.encrypted_metadata)
        assertNull(request.metadata_nonce)
    }

    @Test
    fun `moving to a category pins it inside the existing encrypted metadata`() = runTest {
        val captured = capture_patch()
        val (encrypted, nonce) = seal_like_web(moved_to_travel_on_web.replace("\"travel\"", "\"promotions\"").replace("\"category_pinned\":true", "\"category_pinned\":false"))
        val listed = server_item(with_server_metadata = false).copy(encrypted_metadata = encrypted, metadata_nonce = nonce)

        val written = repo.set_category("deal-1", "finance", listed).getOrThrow()

        val request = captured.captured
        assertNull(request.is_read)
        assertNull(request.is_starred)
        assertNull(request.is_pinned)
        val blob = open_blob(request.encrypted_metadata!!, request.metadata_nonce!!)
        assertEquals("finance", blob.category)
        assertTrue(blob.category_pinned)
        assertEquals(4096L, blob.size_bytes)
        assertEquals("2026-09-30T08:00:00.000Z", blob.created_at)
        assertEquals(request.encrypted_metadata, written.encrypted_metadata)
        assertEquals("finance", written.metadata?.category)
    }

    @Test
    fun `moving a cached row reads the current metadata from the server first`() = runTest {
        val captured = capture_patch()
        coEvery { mail_api.get_message("deal-1") } returns server_item(with_server_metadata = false)

        repo.set_category("deal-1", "shopping", MailItem(id = "deal-1", item_type = "received")).getOrThrow()

        val blob = open_blob(captured.captured.encrypted_metadata!!, captured.captured.metadata_nonce!!)
        assertEquals("shopping", blob.category)
        assertTrue(blob.category_pinned)
        assertEquals(4096L, blob.size_bytes)
    }

    @Test
    fun `moving a cached row changes nothing when the server copy cannot be read`() = runTest {
        coEvery { mail_api.get_message("deal-1") } throws java.io.IOException("offline")

        val result = repo.set_category("deal-1", "shopping", MailItem(id = "deal-1", item_type = "received"))

        assertTrue(result.isFailure)
        io.mockk.coVerify(exactly = 0) { mail_api.patch_metadata(any(), any()) }
    }

    @Test
    fun `moving a message whose metadata this device cannot open changes nothing`() = runTest {
        val unreadable = server_item(with_server_metadata = false).copy(
            encrypted_metadata = java.util.Base64.getEncoder().encodeToString(ByteArray(48) { 7 }),
        )

        val result = repo.set_category("deal-1", "travel", unreadable)

        assertTrue(result.exceptionOrNull() is MetadataUndecryptableException)
        io.mockk.coVerify(exactly = 0) { mail_api.patch_metadata(any(), any()) }
    }
}
