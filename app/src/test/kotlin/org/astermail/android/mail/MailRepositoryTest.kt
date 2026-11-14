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
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.runBlocking
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
import org.astermail.android.api.mail.CreateAttachmentRequestBody
import org.astermail.android.api.mail.CreateAttachmentResponse
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
    private lateinit var system_folder_bootstrap: SystemFolderBootstrap
    private lateinit var pending_send_dao: FakePendingSendDao
    private lateinit var repo: MailRepository

    private class FakePendingSendDao : PendingSendDao {
        val rows = java.util.concurrent.ConcurrentHashMap<String, PendingSendEntity>()
        override suspend fun upsert(row: PendingSendEntity) { rows[row.id] = row }
        override suspend fun get_by_id(id: String): PendingSendEntity? = rows[id]
        override suspend fun get_all(): List<PendingSendEntity> = rows.values.toList()
        override suspend fun active_draft_ids(): List<String> =
            rows.values.filter { it.status != "failed" }.mapNotNull { it.draft_id }.filter { it.isNotBlank() }
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
        override suspend fun delete_if_pending(id: String): Int =
            if (rows[id]?.status == "pending" && rows.remove(id) != null) 1 else 0
        override suspend fun clear_all() { rows.clear() }
        override suspend fun clear_for_account(account_id: String) {
            rows.values.filter { it.account_id == account_id || it.account_id == null }
                .forEach { rows.remove(it.id) }
        }
        override suspend fun get_for_account(account_id: String): List<PendingSendEntity> =
            rows.values.filter { it.account_id == account_id || it.account_id == null }
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
        system_folder_bootstrap = mockk(relaxed = true)
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns emptyMap()
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false
        coEvery { mail_api.get_message(any()) } answers { fake_mail_item(firstArg()) }
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(
                labels = listOf(
                    org.astermail.android.api.labels.LabelItem(
                        id = "l_sent",
                        label_token = "sent_token",
                        is_system = true,
                        folder_type = "sent",
                    ),
                ),
            )
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
            system_folder_bootstrap = system_folder_bootstrap,
            pending_send_dao_provider = dagger.Lazy { pending_send_dao },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = context,
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
            encryption_api_provider = dagger.Lazy { encryption_api },
            identity_pins_provider = dagger.Lazy { first_seen_sender_pins() },
        )
        repo.outbox_file_cipher_override = OutboxFileCipher { outbox_row_key.copyOf() }
    }

    private val outbox_row_key = ByteArray(32) { 11 }

    private val encryption_api: org.astermail.android.api.encryption.EncryptionApi = mockk {
        coEvery { get_encryption_settings() } returns org.astermail.android.api.encryption.EncryptionSettings()
    }

    private fun first_seen_sender_pins(): org.astermail.android.mail.ratchet.RatchetIdentityPinStore {
        val pins = mockk<org.astermail.android.mail.ratchet.RatchetIdentityPinStore>(relaxed = true)
        coEvery { pins.check_sender_key(any(), any(), any()) } returns
            org.astermail.android.mail.ratchet.SavedKeyCheck.FIRST
        return pins
    }

    private fun require_encryption_repo(
        settings: suspend () -> org.astermail.android.api.encryption.EncryptionSettings,
    ): MailRepository {
        val api = mockk<org.astermail.android.api.encryption.EncryptionApi>()
        coEvery { api.get_encryption_settings() } coAnswers { settings() }
        return MailRepository(
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
            system_folder_bootstrap = system_folder_bootstrap,
            pending_send_dao_provider = dagger.Lazy { pending_send_dao },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = context,
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
            encryption_api_provider = dagger.Lazy { api },
            identity_pins_provider = dagger.Lazy { first_seen_sender_pins() },
        )
    }

    @Test
    fun `require encryption blocks external reactions and scheduled sends`() = runTest {
        every { session_key_store.get_user_id() } returns "user_1"
        val guarded = require_encryption_repo {
            org.astermail.android.api.encryption.EncryptionSettings(require_encryption = true)
        }
        val reaction = guarded.send_reaction("msg_1", null, null, "friend@outside.example", "x")
        assertTrue(reaction.exceptionOrNull() is EncryptionRequiredException)
        val scheduled = guarded.schedule_email(
            subject = "Later",
            body_html = "<p>later</p>",
            to = listOf("to@astermail.org"),
            bcc = listOf("friend@outside.example"),
            scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString(),
        )
        assertTrue(scheduled.exceptionOrNull() is EncryptionRequiredException)
        coVerify(exactly = 0) { send_api.react(any()) }
        coVerify(exactly = 0) { scheduled_api.create_scheduled(any()) }
    }

    @Test
    fun `an unreadable encryption setting while offline is a connection failure`() = runTest {
        every { session_key_store.get_user_id() } returns "user_1"
        val guarded = require_encryption_repo { throw java.net.UnknownHostException("offline") }
        val err = guarded.send_email(
            to = listOf("friend@outside.example"),
            subject = "Hi",
            body_html = "<p>hi</p>",
        ).exceptionOrNull()
        assertTrue(err is EncryptionSettingUnavailableException)
        assertFalse(is_permanent_send_failure_cause(err))
        assertTrue(is_connectivity_failure(err))
        assertEquals(SendFailureReason.CONNECTION, send_failure_reason_for(err))
        assertTrue(should_wait_for_network(err, false, 0L, 1L))
    }

    @Test
    fun `the cached encryption setting belongs to one account`() = runTest {
        var account = "user_1"
        var online = true
        every { session_key_store.get_user_id() } answers { account }
        val guarded = require_encryption_repo {
            if (!online) throw java.net.UnknownHostException("offline")
            org.astermail.android.api.encryption.EncryptionSettings(require_encryption = account == "user_1")
        }
        assertTrue(
            guarded.send_reaction("msg_1", null, null, "friend@outside.example", "x")
                .exceptionOrNull() is EncryptionRequiredException,
        )
        online = false
        assertTrue(
            guarded.send_reaction("msg_1", null, null, "friend@outside.example", "x")
                .exceptionOrNull() is EncryptionRequiredException,
        )
        account = "user_2"
        assertTrue(
            guarded.send_reaction("msg_1", null, null, "friend@outside.example", "x")
                .exceptionOrNull() is EncryptionSettingUnavailableException,
        )
        coVerify(exactly = 0) { send_api.react(any()) }
    }

    @Test
    fun `reaction to an Aster recipient without post-quantum keys asks for consent`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), false) } throws
            org.astermail.android.mail.ratchet.PostQuantumUnavailableException(listOf("friend@astermail.org"))

        val err = repo.send_reaction("msg_1", "group_1", null, "friend@astermail.org", "x").exceptionOrNull()

        assertTrue(err.toString(), err is org.astermail.android.mail.ratchet.PostQuantumUnavailableException)
        assertEquals(
            listOf("friend@astermail.org"),
            (err as org.astermail.android.mail.ratchet.PostQuantumUnavailableException).recipients,
        )
        coVerify(exactly = 0) { send_api.react(any()) }
    }

    @Test
    fun `reaction sent after consent is sealed without post-quantum keys`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), true) } returns "sealed_reaction"
        coEvery { send_api.react(any()) } returns
            org.astermail.android.api.send.ReactResponse(success = true, own_reaction_mail_item_id = "r1")

        val result = repo.send_reaction(
            "msg_1", "group_1", null, "friend@astermail.org", "x",
            allow_non_post_quantum = true,
        )

        assertEquals("r1", result.getOrNull())
        coVerify(exactly = 1) {
            ratchet_encryptor.encrypt_envelope("me@astermail.org", listOf("friend@astermail.org"), any(), true)
        }
        val request = slot<org.astermail.android.api.send.ReactRequest>()
        coVerify(exactly = 1) { send_api.react(capture(request)) }
        assertEquals("sealed_reaction", request.captured.body)
        assertTrue(request.captured.is_e2e_encrypted)
    }

    @Test
    fun `reaction to an Aster recipient is never sent unsealed`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        every { context.getString(org.astermail.android.R.string.e2e_identity_changed_blocked) } returns "identity changed"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) } throws
            org.astermail.android.mail.ratchet.RatchetIdentityPinException("friend@astermail.org", "pin")

        val pinned = repo.send_reaction("msg_1", "group_1", null, "friend@astermail.org", "x").exceptionOrNull()
        assertEquals("identity changed", pinned?.message)

        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) } returns null
        val empty = repo.send_reaction("msg_1", "group_1", null, "friend@astermail.org", "x").exceptionOrNull()
        assertTrue(empty.toString(), empty is E2eEncryptionException)

        coVerify(exactly = 0) { send_api.react(any()) }
    }

    private fun required_encryption_send_repo(): MailRepository {
        every { session_key_store.get_user_id() } returns "user_1"
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")
        return require_encryption_repo {
            org.astermail.android.api.encryption.EncryptionSettings(require_encryption = true)
        }
    }

    @Test
    fun `required encryption sends outside mail encrypted to every discovered key`() = runTest {
        val passphrase = "key check passphrase".toCharArray()
        val curve = org.astermail.android.crypto.PgpKeyGenerator.generate("Wkd", "wkd@outside.example", passphrase)
        val rsa = org.astermail.android.crypto.TestRsaKeys.generate("Keyserver <rsa@outside.example>", passphrase)
        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(
                email = "wkd@outside.example",
                found = true,
                public_key = curve.armored_public_key,
                source = "wkd",
                will_encrypt = true,
            ),
            org.astermail.android.api.keys.ExternalKeyInfo(
                email = "rsa@outside.example",
                found = true,
                public_key = rsa.armored_public_key,
                source = "keyserver",
                will_encrypt = true,
            ),
        )
        val guarded = required_encryption_send_repo()

        val result = guarded.send_email(
            to = listOf("wkd@outside.example"),
            cc = listOf("rsa@outside.example"),
            subject = "Key check",
            body_html = "<p>Hello over PGP</p>",
            sender_email = "me@astermail.org",
        )

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        val copies = request.captured.external_pgp_bodies
        assertEquals(setOf("wkd@outside.example", "rsa@outside.example"), copies?.keys)
        val curve_plain = org.astermail.android.crypto.PgpDecryptor.decrypt(
            copies!!.getValue("wkd@outside.example"),
            curve.armored_private_key,
            passphrase,
        )
        val rsa_plain = org.astermail.android.crypto.PgpDecryptor.decrypt(
            copies.getValue("rsa@outside.example"),
            rsa.armored_private_key,
            passphrase,
        )
        assertTrue(curve_plain.orEmpty().contains("Key check"))
        assertTrue(rsa_plain.orEmpty().contains("Key check"))
        assertTrue(request.captured.attachments.isEmpty())
    }

    @Test
    fun `required encryption refuses outside mail when a recipient has no key`() = runTest {
        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(
                email = "keyed@outside.example",
                found = true,
                public_key = "-----BEGIN PGP PUBLIC KEY BLOCK-----",
                will_encrypt = true,
            ),
            org.astermail.android.api.keys.ExternalKeyInfo(email = "bare@outside.example", found = false),
        )
        val guarded = required_encryption_send_repo()

        val outside_only = guarded.send_email(
            to = listOf("keyed@outside.example", "bare@outside.example"),
            subject = "Hi",
            body_html = "<p>hi</p>",
        )
        val mixed = guarded.send_email(
            to = listOf("friend@astermail.org"),
            bcc = listOf("bare@outside.example"),
            subject = "Hi",
            body_html = "<p>hi</p>",
        )

        assertTrue(outside_only.exceptionOrNull() is EncryptionRequiredException)
        assertTrue(mixed.exceptionOrNull() is EncryptionRequiredException)
        verify { context.getString(org.astermail.android.R.string.send_requires_encryption_missing_key, "bare@outside.example") }
        coVerify(exactly = 0) { send_api.send_external(any()) }
        coVerify(exactly = 0) { send_api.queue_email(any()) }
    }

    @Test
    fun `required encryption still refuses an expiring outside message`() = runTest {
        val guarded = required_encryption_send_repo()

        val result = guarded.send_email(
            to = listOf("friend@outside.example"),
            subject = "Hi",
            body_html = "<p>hi</p>",
            expires_at = "2030-01-01T00:00:00Z",
        )

        assertTrue(result.exceptionOrNull() is EncryptionRequiredException)
        coVerify(exactly = 0) { keys_api.discover_external_keys_batch(any()) }
        coVerify(exactly = 0) { send_api.send_external(any()) }
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
                pinned_first = true,
            )
        }
    }

    @Test
    fun `fetch_inbox for label does not force archived filter`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(label_token = "work")

        coVerify {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                offset = any(),
                item_type = any(),
                is_starred = any(),
                is_trashed = false,
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
                pinned_first = true,
            )
        }
    }

    @Test
    fun `fetch_inbox label scope paginates by offset and synthesizes next cursor`() = runTest {
        val items = listOf(fake_mail_item("i1"), fake_mail_item("i2"))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
                pinned_first = true,
            )
        }
    }

    @Test
    fun `fetch_inbox with null envelope yields empty sender`() = runTest {
        val items = listOf(fake_mail_item("i1", encrypted_envelope = null))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = items, has_more = false, next_cursor = null, total = 1)

        val result = repo.fetch_inbox()
        val item = result.getOrThrow().items[0]

        assertEquals("", item.sender_name)
        assertEquals("", item.sender_email)
        assertEquals("", item.subject)
    }

    @Test
    fun `fetch_inbox propagates api errors`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } throws
            RuntimeException("api down")

        val result = repo.fetch_inbox()
        assertTrue(result.isFailure)
        assertEquals("api down", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetch_sent delegates to list_messages with sent type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), item_type = eq("sent"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_sent()
        coVerify { mail_api.list_messages(any(), any(), any(), item_type = "sent", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), is_starred = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_starred()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), is_starred = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_trash passes is_trashed flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), is_trashed = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_trash()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), is_trashed = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_spam passes is_spam flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), is_spam = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_spam()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), is_spam = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_archive passes is_archived flag`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_archived = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_archive()
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_archived = true, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `folder fetches ask the server for pinned mail first`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_starred()
        repo.fetch_trash()
        repo.fetch_spam()
        repo.fetch_archive()
        repo.fetch_snoozed()
        repo.fetch_sent()

        coVerify(exactly = 6) {
            mail_api.list_messages(
                limit = any(),
                cursor = any(),
                offset = any(),
                item_type = any(),
                is_starred = any(),
                is_trashed = any(),
                is_archived = any(),
                is_spam = any(),
                include_spam = any(),
                include_trash = any(),
                label_token = any(),
                tag_token = any(),
                group_by_thread = any(),
                is_snoozed = any(),
                routing_token = any(),
                order = any(),
                skip_total = any(),
                include_envelope = any(),
                direction = any(),
                pinned_first = true,
            )
        }
    }

    @Test
    fun `snooze_bulk sends 300 ids as three requests of at most 100`() = runTest {
        val sent = mutableListOf<List<String>>()
        coEvery { snooze_api.bulk_snooze(any()) } answers {
            val request = firstArg<org.astermail.android.api.snooze.BulkSnoozeRequest>()
            sent.add(request.mail_item_ids)
            org.astermail.android.api.snooze.BulkSnoozeResponse(snoozed_count = request.mail_item_ids.size)
        }
        val ids = (1..300).map { "id_$it" }

        val outcome = repo.snooze_bulk(ids, "2026-10-10T09:00:00Z").getOrThrow()

        assertEquals(listOf(100, 100, 100), sent.map { it.size })
        assertEquals(ids, sent.flatten())
        assertTrue(outcome.failed_ids.isEmpty())
        assertEquals(0, outcome.skipped)
        coVerify(exactly = 0) { snooze_api.snooze(any()) }
    }

    @Test
    fun `snooze_bulk reports the ids of a rejected chunk and keeps going`() = runTest {
        var call = 0
        coEvery { snooze_api.bulk_snooze(any()) } answers {
            call += 1
            if (call == 2) throw org.astermail.android.api.ApiError.PlanLimitExceeded("limit", "snoozed emails")
            val request = firstArg<org.astermail.android.api.snooze.BulkSnoozeRequest>()
            org.astermail.android.api.snooze.BulkSnoozeResponse(snoozed_count = request.mail_item_ids.size - 1)
        }
        val ids = (1..250).map { "id_$it" }

        val outcome = repo.snooze_bulk(ids, "2026-10-10T09:00:00Z").getOrThrow()

        assertEquals(3, call)
        assertEquals(ids.subList(100, 200).toSet(), outcome.failed_ids)
        assertEquals(2, outcome.skipped)
    }

    @Test
    fun `snooze_bulk fails on a transient error so the action can be retried`() = runTest {
        coEvery { snooze_api.bulk_snooze(any()) } throws java.io.IOException("offline")

        val result = repo.snooze_bulk(listOf("a", "b"), "2026-10-10T09:00:00Z")

        assertTrue(result.isFailure)
    }

    @Test
    fun `unsnooze_bulk sends 300 ids as three requests of at most 100`() = runTest {
        val sent = mutableListOf<List<String>>()
        coEvery { snooze_api.bulk_unsnooze(any()) } answers {
            val request = firstArg<org.astermail.android.api.snooze.BulkUnsnoozeRequest>()
            sent.add(request.mail_item_ids)
            org.astermail.android.api.snooze.BulkUnsnoozeResponse(unsnoozed_count = request.mail_item_ids.size)
        }
        var single_calls = 0
        coEvery { snooze_api.unsnooze_by_mail_item(any()) } answers { single_calls += 1 }
        val ids = (1..300).map { "id_$it" }

        val failed = repo.unsnooze_bulk(ids).getOrThrow()

        assertEquals(listOf(100, 100, 100), sent.map { it.size })
        assertEquals(ids, sent.flatten())
        assertTrue(failed.isEmpty())
        assertEquals(0, single_calls)
    }

    @Test
    fun `unsnooze_bulk falls back to one request per message when the server has no bulk route`() = runTest {
        var bulk_calls = 0
        coEvery { snooze_api.bulk_unsnooze(any()) } answers {
            bulk_calls += 1
            throw org.astermail.android.api.ApiError.NotFoundError
        }
        val single = mutableListOf<String>()
        coEvery { snooze_api.unsnooze_by_mail_item(any()) } answers {
            val id = firstArg<String>()
            if (id == "id_150") throw org.astermail.android.api.ApiError.ForbiddenError("locked", null)
            single.add(id)
        }
        val ids = (1..250).map { "id_$it" }

        val failed = repo.unsnooze_bulk(ids).getOrThrow()

        assertEquals(1, bulk_calls)
        assertEquals(ids - "id_150", single)
        assertEquals(setOf("id_150"), failed)
    }

    @Test
    fun `unsnooze_bulk reports the ids of a rejected chunk and keeps going`() = runTest {
        var call = 0
        coEvery { snooze_api.bulk_unsnooze(any()) } answers {
            call += 1
            if (call == 2) throw org.astermail.android.api.ApiError.ForbiddenError("locked", null)
            org.astermail.android.api.snooze.BulkUnsnoozeResponse(unsnoozed_count = 100)
        }
        val ids = (1..250).map { "id_$it" }

        val failed = repo.unsnooze_bulk(ids).getOrThrow()

        assertEquals(3, call)
        assertEquals(ids.subList(100, 200).toSet(), failed)
    }

    @Test
    fun `unsnooze_bulk fails on a transient error so the action can be retried`() = runTest {
        coEvery { snooze_api.bulk_unsnooze(any()) } throws java.io.IOException("offline")

        val result = repo.unsnooze_bulk(listOf("a", "b"))

        assertTrue(result.isFailure)
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
    fun `move_to_folder_bulk adds the destination label and drops the source label`() = runTest {
        coEvery { mail_api.bulk_add_label(any()) } returns BulkLabelResponse(status = "ok", affected = 1)
        coEvery { mail_api.bulk_remove_label(any()) } returns BulkLabelResponse(status = "ok", affected = 1)

        val failed = repo.move_to_folder_bulk(listOf("m1"), "bills", "myfeed")

        assertTrue(failed.isEmpty())
        coVerify { mail_api.bulk_add_label(BulkLabelRequest(ids = listOf("m1"), label_token = "bills", include_thread = true)) }
        coVerify { mail_api.bulk_remove_label(BulkLabelRequest(ids = listOf("m1"), label_token = "myfeed", include_thread = true)) }
    }

    @Test
    fun `move_to_folder_bulk from the inbox only adds the destination label`() = runTest {
        coEvery { mail_api.bulk_add_label(any()) } returns BulkLabelResponse(status = "ok", affected = 1)

        val failed = repo.move_to_folder_bulk(listOf("m1"), "bills", null)

        assertTrue(failed.isEmpty())
        coVerify { mail_api.bulk_add_label(BulkLabelRequest(ids = listOf("m1"), label_token = "bills", include_thread = true)) }
        coVerify(exactly = 0) { mail_api.bulk_remove_label(any()) }
    }

    @Test
    fun `move_to_folder_bulk keeps the source label when the destination fails`() = runTest {
        coEvery { mail_api.bulk_add_label(any()) } throws RuntimeException("boom")
        coEvery { mail_api.add_label_to_item(any(), any()) } throws RuntimeException("boom")

        val failed = repo.move_to_folder_bulk(listOf("m1"), "bills", "myfeed")

        assertEquals(setOf("m1"), failed)
        coVerify(exactly = 0) { mail_api.bulk_remove_label(any()) }
        coVerify(exactly = 0) { mail_api.remove_label_from_item(any(), any()) }
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
    fun `custom folder opened by its bare token supports bulk scope`() {
        assertTrue(repo.folder_supports_bulk_scope("4JB5b0fX1K/XLf+Q3RpcSg=="))
        assertEquals("4JB5b0fX1K/XLf+Q3RpcSg==", repo.folder_to_bulk_scope("4JB5b0fX1K/XLf+Q3RpcSg==").label_token)
        assertFalse(repo.folder_supports_bulk_scope("all"))
        assertFalse(repo.folder_supports_bulk_scope("all+spam"))
        assertFalse(repo.folder_supports_bulk_scope("not a token"))
        assertEquals(null, repo.folder_to_bulk_scope("spam").label_token)
        assertEquals(null, repo.folder_to_bulk_scope("sent").label_token)
    }

    @Test
    fun `unknown folders never fall back to an unscoped bulk filter`() {
        listOf("all", "all+spam", "drafts", "scheduled", "routing:alias_token", "label:", "tag:").forEach { folder ->
            assertTrue(folder, runCatching { repo.folder_to_bulk_scope(folder) }.isFailure)
        }
    }

    @Test
    fun `bulk_scope_action on a bare folder token scopes by that folder`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 306)

        val result = repo.bulk_scope_action("4JB5b0fX1K/XLf+Q3RpcSg==", "archive")

        assertEquals(306, result.getOrThrow().affected_count)
        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "archive",
                    scope = BulkScopeFilter(label_token = "4JB5b0fX1K/XLf+Q3RpcSg==", is_trashed = false, is_spam = false),
                ),
            )
        }
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
                    scope = BulkScopeFilter(tag_token = "work_token", is_trashed = false, is_spam = false),
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
    fun `unarchive succeeds when the action lands and only the metadata mirror fails`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 2)
        coEvery { mail_api.bulk_patch_metadata(any()) } throws RuntimeException("batch rejected")
        coEvery { mail_api.patch_metadata(any(), any()) } throws RuntimeException("patch rejected")

        val result = repo.unarchive(listOf("a", "b"))

        assertTrue(result.isSuccess)
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
                BulkScopeRequest(
                    action = "star",
                    scope = BulkScopeFilter(
                        item_type = "received",
                        is_archived = false,
                        is_trashed = false,
                        is_spam = false,
                        is_snoozed = false,
                    ),
                ),
            )
        }
    }

    @Test
    fun `inbox scope leaves archived spam trashed snoozed and filed mail alone`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 7)

        repo.bulk_scope_action("inbox", "trash")

        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "trash",
                    scope = BulkScopeFilter(
                        item_type = "received",
                        is_archived = false,
                        is_trashed = false,
                        is_spam = false,
                        is_snoozed = false,
                    ),
                ),
            )
        }
    }

    @Test
    fun `every scoped folder except trash and spam excludes trashed and spam mail`() {
        listOf("inbox", "sent", "starred", "archive", "snoozed", "label:a", "tag:b", "4JB5b0fX1K/XLf+Q3RpcSg==").forEach { folder ->
            val scope = repo.folder_to_bulk_scope(folder)
            assertEquals(folder, false, scope.is_trashed)
            assertEquals(folder, false, scope.is_spam)
        }
        assertEquals(true, repo.folder_to_bulk_scope("trash").is_trashed)
        assertEquals(true, repo.folder_to_bulk_scope("spam").is_spam)
    }

    @Test
    fun `id based scope actions split large selections under the request body limit`() = runTest {
        val captured = mutableListOf<BulkScopeRequest>()
        coEvery { mail_api.bulk_action(capture(captured)) } answers {
            BulkScopeResponse(affected_count = firstArg<BulkScopeRequest>().ids!!.size)
        }
        coEvery { mail_api.bulk_patch_metadata(any()) } returns
            BulkPatchMetadataResponse(success = true, updated_count = 0)

        val result = repo.restore_trash((1..2_500).map { "item_$it" })

        assertEquals(2_500, result.getOrThrow().affected_count)
        assertEquals(listOf(1_000, 1_000, 500), captured.map { it.ids!!.size })
        assertTrue(captured.all { it.action == "restore_trash" })
    }

    @Test
    fun `star_scope sends the unstar action when clearing stars`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 12)

        repo.star_scope("starred", false)

        coVerify {
            mail_api.bulk_action(
                BulkScopeRequest(
                    action = "unstar",
                    scope = BulkScopeFilter(is_starred = true, is_trashed = false, is_spam = false),
                ),
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
        assertTrue(captured.all { it.include_thread })
    }

    @Test
    fun `move_to_folder_bulk moves single messages when conversations are off`() = runTest {
        coEvery { mail_api.bulk_add_label(any()) } returns BulkLabelResponse(status = "ok", affected = 1)
        coEvery { mail_api.bulk_remove_label(any()) } returns BulkLabelResponse(status = "ok", affected = 1)
        repo.set_conversation_grouping(false)

        val failed = repo.move_to_folder_bulk(listOf("m1"), "bills", "myfeed")

        assertTrue(failed.isEmpty())
        coVerify { mail_api.bulk_add_label(BulkLabelRequest(ids = listOf("m1"), label_token = "bills", include_thread = false)) }
        coVerify { mail_api.bulk_remove_label(BulkLabelRequest(ids = listOf("m1"), label_token = "myfeed", include_thread = false)) }
    }

    @Test
    fun `remove_label_bulk covers the whole conversation by default`() = runTest {
        val captured = mutableListOf<BulkLabelRequest>()
        coEvery { mail_api.bulk_remove_label(capture(captured)) } returns BulkLabelResponse("ok", 1)

        val failed = repo.remove_label_bulk(listOf("m1"), "bills")

        assertTrue(failed.isEmpty())
        assertEquals(listOf(true), captured.map { it.include_thread })
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
                    scope = BulkScopeFilter(tag_token = "work_token", is_trashed = false, is_spam = false),
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
                    scope = BulkScopeFilter(label_token = "work_token", is_trashed = false, is_spam = false),
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
                    scope = BulkScopeFilter(label_token = "work_token", is_trashed = false, is_spam = false),
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
        coEvery { mail_api.list_messages(any(), cursor = eq("my_cursor"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(cursor = "my_cursor")
        coVerify { mail_api.list_messages(any(), cursor = "my_cursor", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_inbox with label_token passes it to api`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), label_token = eq("lbl_abc"), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(label_token = "lbl_abc")
        coVerify { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), label_token = "lbl_abc", any(), any(), any(), any(), any(), any(), any(), any(), any()) }
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
    fun `client send id is the pending id only when it is a uuid`() {
        assertEquals(
            "7d4f2c1a-9b3e-4f6a-8c2d-1e5b7a9c3f80",
            client_send_id_for("7d4f2c1a-9b3e-4f6a-8c2d-1e5b7a9c3f80"),
        )
        assertEquals(null, client_send_id_for("pend_1"))
        assertEquals(null, client_send_id_for("1-1-1-1-1"))
    }

    @Test
    fun `send_email passes the client send id to an internal send`() = runTest {
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_1")
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"

        repo.send_email(
            to = listOf("recipient@astermail.org"),
            subject = "Test",
            body_html = "<p>Hello</p>",
            client_send_id = "7d4f2c1a-9b3e-4f6a-8c2d-1e5b7a9c3f80",
        )

        val request = slot<org.astermail.android.api.send.SimpleSendRequest>()
        coVerify(exactly = 1) { send_api.send_simple(capture(request)) }
        assertEquals("7d4f2c1a-9b3e-4f6a-8c2d-1e5b7a9c3f80", request.captured.client_send_id)
    }

    @Test
    fun `send_email passes the client send id to an external send`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(labels = emptyList())
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns mapOf("sent" to "healed_sent_token")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")

        repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
            client_send_id = "7d4f2c1a-9b3e-4f6a-8c2d-1e5b7a9c3f80",
        )

        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        assertEquals("7d4f2c1a-9b3e-4f6a-8c2d-1e5b7a9c3f80", request.captured.client_send_id)
    }

    @Test
    fun `a password protected external send keeps the password and the content on the device`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(labels = emptyList())
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns mapOf("sent" to "healed_sent_token")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")
        val password = "Tr0ub4dor&3-horse-Battery!"
        val file_bytes = "attachment secret".toByteArray()

        val result = repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Launch plan",
            body_html = "<p>The launch is on Tuesday</p>",
            attachments = listOf(
                ExternalAttachmentPayload(
                    data = java.util.Base64.getEncoder().encodeToString(file_bytes),
                    filename = "plan.txt",
                    content_type = "text/plain",
                    size_bytes = file_bytes.size.toLong(),
                ),
            ),
            expires_at = java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString(),
            expiry_password = password,
        )

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        val sent = request.captured
        assertNull(sent.expiry_password)
        assertTrue(sent.attachments.isEmpty())
        assertNull(sent.signed_mime)
        assertNull(sent.external_pgp_bodies)
        assertNull(sent.force_pgp)
        assertNull(sent.pgp_recipients)
        val wire = kotlinx.serialization.json.Json.encodeToString(
            org.astermail.android.api.send.ExternalSendRequest.serializer(),
            sent,
        )
        assertFalse(wire.contains(password))
        assertFalse(wire.contains("Launch plan"))
        assertFalse(wire.contains("Tuesday"))
        assertFalse(wire.contains("plan.txt"))

        val decoder = java.util.Base64.getDecoder()
        val relay_key = decoder.decode(sent.ephemeral_key)
        fun relay_field(ciphertext: String, xor_byte: Int): String {
            val nonce = decoder.decode(sent.nonce)
            nonce[11] = (nonce[11].toInt() xor xor_byte).toByte()
            return String(
                org.astermail.android.crypto.AesGcm.decrypt(relay_key, nonce, decoder.decode(ciphertext)),
                Charsets.UTF_8,
            )
        }
        assertEquals("[secure message]", relay_field(sent.encrypted_subject, 0x02))
        assertEquals("[secure message]", relay_field(sent.encrypted_body, 0x03))

        val secure = checkNotNull(sent.secure_message)
        val material = org.astermail.android.crypto.SecureMessage.derive_password_material(
            password,
            decoder.decode(secure.kdf_salt),
        )
        val seed = org.astermail.android.crypto.AesGcm.decrypt(
            material.kdf_key,
            decoder.decode(secure.kem_seed_nonce),
            decoder.decode(secure.encrypted_kem_seed),
        )
        val shared = org.astermail.android.crypto.ratchet.RatchetCrypto.ml_kem_768_decapsulate(
            decoder.decode(secure.kem_ciphertext),
            org.astermail.android.crypto.ratchet.RatchetCrypto.ml_kem_768_keypair_from_seed(seed).secret_key,
        )
        val content_key = org.astermail.android.crypto.SecureMessage.derive_content_key(shared, material.kdf_key)
        val opened_subject = org.astermail.android.crypto.AesGcm.decrypt(
            content_key,
            decoder.decode(secure.encrypted_subject.nonce),
            decoder.decode(secure.encrypted_subject.ciphertext),
            org.astermail.android.crypto.SecureMessage.field_aad("subject"),
        )
        val opened_body = org.astermail.android.crypto.AesGcm.decrypt(
            content_key,
            decoder.decode(secure.encrypted_body.nonce),
            decoder.decode(secure.encrypted_body.ciphertext),
            org.astermail.android.crypto.SecureMessage.field_aad("body"),
        )
        assertEquals("Launch plan", String(opened_subject, Charsets.UTF_8))
        assertTrue(String(opened_body, Charsets.UTF_8).contains("The launch is on Tuesday"))
        assertNotNull(secure.attachments_bundle)
    }

    @Test
    fun `send_email seals a password protected external message on the device`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(labels = emptyList())
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns mapOf("sent" to "healed_sent_token")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")
        val password = "Tr0ub4dor-and-3"

        val result = repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
            expires_at = "2030-01-01T00:00:00Z",
            expiry_password = password,
            attachments = listOf(
                org.astermail.android.api.send.ExternalAttachmentPayload(
                    filename = "a.txt",
                    content_type = "text/plain",
                    data = "aGVsbG8=",
                    size_bytes = 5,
                ),
            ),
        )

        assertTrue(result.isSuccess)
        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        val sealed = request.captured.secure_message
        assertNotNull(sealed)
        assertNull(request.captured.expiry_password)
        assertTrue(request.captured.attachments.isEmpty())
        assertNull(request.captured.signed_mime)
        assertNull(request.captured.external_pgp_bodies)
        assertNotNull(sealed!!.attachments_bundle)
        assertEquals(
            org.astermail.android.crypto.SecureMessageCrypto.derive_auth_proof(password, sealed.kdf_salt),
            sealed.auth_proof,
        )
    }

    @Test
    fun `send_email without a message password sends no sealed message`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(labels = emptyList())
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns mapOf("sent" to "healed_sent_token")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")

        repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        assertNull(request.captured.secure_message)
        assertNull(request.captured.expiry_password)
    }

    @Test
    fun `send_email refuses to relay when the sent folder cannot be resolved`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } throws RuntimeException("network error")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"

        val result = repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { send_api.send_external(any()) }
    }

    @Test
    fun `send_email creates the missing sent folder before relaying`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(labels = emptyList())
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns mapOf("sent" to "healed_sent_token")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")

        val result = repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isSuccess)
        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        assertEquals("healed_sent_token", request.captured.folder_token)
        coVerify(exactly = 1) { system_folder_bootstrap.ensure_system_folders() }
    }

    @Test
    fun `send_email derives the sent folder token when the folder cannot be created`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } returns
            org.astermail.android.api.labels.LabelsListResponse(labels = emptyList())
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns emptyMap()
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m1")

        val result = repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isSuccess)
        val expected = java.util.Base64.getEncoder().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256")
                .digest("test_identity_keyfolder:sent".toByteArray(Charsets.UTF_8)),
        )
        val request = slot<org.astermail.android.api.send.ExternalSendRequest>()
        coVerify(exactly = 1) { send_api.send_external(capture(request)) }
        assertEquals(expected, request.captured.folder_token)
    }

    @Test
    fun `send_email does not create folders when the label list cannot be fetched`() = runTest {
        coEvery { labels_api.list_labels(include_counts = false) } throws RuntimeException("network error")
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"

        val result = repo.send_email(
            to = listOf("someone@example.com"),
            subject = "Test",
            body_html = "<p>Hello</p>",
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { system_folder_bootstrap.ensure_system_folders() }
        coVerify(exactly = 0) { send_api.send_external(any()) }
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
        val create_started = CompletableDeferred<Unit>()
        coEvery { mail_api.create_draft(any()) } coAnswers {
            create_started.complete(Unit)
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
        await_real { create_started.await() }
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

    private suspend fun <T> await_real(block: suspend () -> T): T =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            kotlinx.coroutines.withTimeout(5_000) { block() }
        }

    private fun stub_thread_draft(thread_token: String) {
        coEvery { mail_api.get_thread_draft(thread_token) } returns
            org.astermail.android.api.mail.DraftItem(id = draft_uuid, thread_token = thread_token)
    }

    @Test
    fun `thread draft stays hidden while the sent draft delete is in flight`() = runTest {
        stub_thread_draft("thread_a")
        val delete_started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        coEvery { mail_api.delete_draft(draft_uuid) } coAnswers {
            delete_started.complete(Unit)
            gate.await()
            DeleteResponse(success = true, deleted_count = 1)
        }

        assertNotNull(repo.fetch_thread_draft("thread_a"))

        val pending = repo.discard_sent_draft(draft_uuid, "compose_send_1")
        await_real { delete_started.await() }

        assertNull(repo.fetch_thread_draft("thread_a"))

        gate.complete(Unit)
        assertTrue(await_real { pending.await() })
    }

    @Test
    fun `sent draft is deleted even when compose never learned its id`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        coEvery { mail_api.delete_draft(draft_uuid) } returns DeleteResponse(success = true, deleted_count = 1)

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_send_2")
        val deleted = await_real { repo.discard_sent_draft("", "compose_send_2").await() }

        assertTrue(deleted)
        coVerify(exactly = 1) { mail_api.delete_draft(draft_uuid) }
    }

    @Test
    fun `autosave landing after send cannot recreate the draft`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        coEvery { mail_api.delete_draft(draft_uuid) } returns DeleteResponse(success = true, deleted_count = 1)

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_send_3")
        await_real { repo.discard_sent_draft(draft_uuid, "compose_send_3").await() }
        val late = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_send_3")

        assertTrue(late.isFailure)
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
        coVerify(exactly = 0) { mail_api.update_draft(any(), any()) }
    }

    @Test
    fun `autosave that lands after send starts cannot create a second draft`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)

        val claimed = repo.claim_draft_session_for_send("compose_send_5", "")
        val late = repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_send_5")

        assertNull(claimed)
        assertTrue(late.exceptionOrNull() is DraftSessionClosedException)
        coVerify(exactly = 0) { mail_api.create_draft(any()) }
    }

    @Test
    fun `send claims the draft that an in flight autosave just created`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_send_6")
        val claimed = repo.claim_draft_session_for_send("compose_send_6", "")

        assertEquals(draft_uuid, claimed)
    }

    @Test
    fun `failed send reopens the session on the same draft`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        coEvery { mail_api.update_draft(draft_uuid, any()) } returns
            org.astermail.android.api.mail.UpdateDraftResponse(success = true, version = 2)

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_send_7")
        val claimed = repo.claim_draft_session_for_send("compose_send_7", "")
        repo.reopen_draft_session("compose_send_7", claimed)
        val resumed = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_send_7")

        assertEquals(draft_uuid, resumed.getOrThrow())
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
        coVerify(exactly = 1) { mail_api.update_draft(draft_uuid, any()) }
    }

    @Test
    fun `claimed draft is deleted after the send and stays closed`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = draft_uuid, version = 1)
        coEvery { mail_api.delete_draft(draft_uuid) } returns DeleteResponse(success = true, deleted_count = 1)

        repo.save_draft(subject = "Hello", body_html = "<p>1</p>", session_id = "compose_send_8")
        val claimed = repo.claim_draft_session_for_send("compose_send_8", "")
        val deleted = await_real { repo.discard_sent_draft(claimed, "compose_send_8").await() }
        val late = repo.save_draft(subject = "Hello", body_html = "<p>12</p>", session_id = "compose_send_8")

        assertTrue(deleted)
        assertTrue(late.isFailure)
        coVerify(exactly = 1) { mail_api.delete_draft(draft_uuid) }
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
    }

    @Test
    fun `thread draft is hidden while its undo send is queued`() = runTest {
        stub_thread_draft("thread_b")

        pending_send_dao.upsert(pending_row(id = "pending_1", status = "pending", draft_id = draft_uuid))
        assertNull(repo.fetch_thread_draft("thread_b"))

        pending_send_dao.upsert(pending_row(id = "pending_1", status = "failed", draft_id = draft_uuid))
        assertNotNull(repo.fetch_thread_draft("thread_b"))
    }

    @Test
    fun `draft_changes emits when the sent draft is deleted`() = runTest {
        coEvery { mail_api.delete_draft(draft_uuid) } returns DeleteResponse(success = true, deleted_count = 1)
        val signal = CompletableDeferred<Unit>()
        val watcher = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch(
            start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED,
        ) {
            repo.draft_changes.first()
            signal.complete(Unit)
        }

        await_real { repo.discard_sent_draft(draft_uuid, "compose_send_4").await() }
        await_real { signal.await() }
        watcher.cancel()
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
        coEvery { mail_api.list_messages(limit = eq(25), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox(limit = 25)
        coVerify { mail_api.list_messages(limit = 25, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_inbox with default limit uses 50`() = runTest {
        coEvery { mail_api.list_messages(limit = eq(50), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        repo.fetch_inbox()
        coVerify { mail_api.list_messages(limit = 50, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `fetch_sent routes to list_messages with sent type`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), item_type = eq("sent"), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_sent()
        assertTrue(result.isSuccess)
        coVerify { mail_api.list_messages(any(), any(), any(), item_type = "sent", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), is_starred = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_starred()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_trash routes with is_trashed true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), is_trashed = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_trash()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_spam routes with is_spam true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), is_spam = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = emptyList(), has_more = false, next_cursor = null, total = 0)

        val result = repo.fetch_spam()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `fetch_archive routes with is_archived true`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), is_archived = eq(true), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
    fun `delete_permanent treats an already deleted item as done`() = runTest {
        coEvery { mail_api.delete_permanent(any()) } throws org.astermail.android.api.ApiError.NotFoundError

        val result = repo.delete_permanent("item_1")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `delete_draft treats an already deleted draft as done`() = runTest {
        coEvery { mail_api.delete_draft(any()) } throws org.astermail.android.api.ApiError.NotFoundError

        val result = repo.delete_draft("5b0c7c3e-2a41-4f6e-9d7a-1c2b3d4e5f60")

        assertTrue(result.isSuccess)
    }

    @Test
    fun `trash treats items and conversations that are already gone as done`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 1)
        coEvery { mail_api.get_message("gone") } throws org.astermail.android.api.ApiError.NotFoundError
        coEvery { mail_api.get_message("kept") } returns fake_mail_item("kept").copy(is_trashed = true)
        coEvery { mail_api.trash_thread("thread_gone", true) } throws org.astermail.android.api.ApiError.NotFoundError

        val result = repo.trash(listOf("kept", "gone"), thread_tokens = listOf("thread_gone"))

        assertTrue(result.isSuccess)
    }

    @Test
    fun `trash still fails when an item exists but was not trashed`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 0)
        coEvery { mail_api.get_message("kept") } returns fake_mail_item("kept").copy(is_trashed = false)

        val result = repo.trash(listOf("kept"))

        assertTrue(result.isFailure)
    }

    @Test
    fun `fetch_inbox has_more and next_cursor are preserved`() = runTest {
        val items = listOf(fake_mail_item("i1"))
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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
        coEvery { mail_api.list_messages(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
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

        await_real {
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
        }

        val row = pending_send_dao.get_by_id("pend_1")
        assertNotNull(row)
        assertEquals("pending", row!!.status)
        assertEquals("safety_draft_1", row.draft_id)
        coVerify { mail_api.create_draft(any()) }
    }

    @Test
    fun `the queued row keeps the subject body and message password sealed at rest`() = runTest {
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "safety_draft_s", success = true)

        await_real {
            repo.persist_and_schedule_undo_send(
                pending_id = "pend_sealed",
                to = listOf("pal@outside.example"),
                cc = emptyList(),
                bcc = emptyList(),
                subject = "Quarterly plans",
                body_html = "<p>hello there</p>",
                sender_email = "me@astermail.org",
                sender_display_name = null,
                thread_token = null,
                expires_at = null,
                expiry_password = "correct horse battery 42",
                attachments = emptyList(),
                sender_alias_hash = null,
                suppress_branding = null,
                delay_ms = 10_000L,
                draft_id = null,
            )
        }

        val stored = pending_send_dao.rows["pend_sealed"]!!
        assertTrue(OutboxRowSeal.is_sealed(stored.subject))
        assertTrue(OutboxRowSeal.is_sealed(stored.body_html))
        assertTrue(OutboxRowSeal.is_sealed(stored.expiry_password!!))
        val at_rest = stored.toString()
        assertFalse(at_rest.contains("Quarterly plans"))
        assertFalse(at_rest.contains("hello there"))
        assertFalse(at_rest.contains("correct horse battery 42"))
        val opened = OutboxRowSeal.open(OutboxFileCipher { outbox_row_key.copyOf() }, stored)
        assertEquals("Quarterly plans", opened.subject)
        assertEquals("<p>hello there</p>", opened.body_html)
        assertEquals("correct horse battery 42", opened.expiry_password)
    }

    @Test
    fun `run_pending_send delivers a sealed row`() = runTest {
        pending_send_dao.upsert(
            OutboxRowSeal.seal(
                OutboxFileCipher { outbox_row_key.copyOf() },
                pending_row("pend_seal_ok", draft_id = "draft_seal"),
            ),
        )
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_seal")
        coEvery { mail_api.delete_draft(any()) } returns DeleteResponse(success = true, deleted_count = 1)

        assertEquals(PendingSendOutcome.SENT, repo.run_pending_send("pend_seal_ok"))
        coVerify(exactly = 1) { send_api.send_simple(any()) }
    }

    @Test
    fun `run_pending_send fails a row it cannot open and sends nothing`() = runTest {
        pending_send_dao.upsert(
            OutboxRowSeal.seal(OutboxFileCipher { ByteArray(32) { 99 } }, pending_row("pend_seal_bad")),
        )
        every { session_key_store.has_ratchet_keys() } returns true

        assertEquals(PendingSendOutcome.FAILED, repo.run_pending_send("pend_seal_bad"))
        assertEquals("failed", pending_send_dao.rows["pend_seal_bad"]?.status)
        coVerify(exactly = 0) { send_api.send_simple(any()) }
        coVerify(exactly = 0) { send_api.send_external(any()) }
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
    fun `undo cancels the send that was already queued for the draft`() = runTest {
        every { session_key_store.get_user_id() } returns "user_1"
        pending_send_dao.upsert(pending_row("pend_queued", draft_id = "draft_q"))

        val result = repo.schedule_send_with_undo(
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Hi",
            body_html = "<p>hello</p>",
            sender_email = "me@astermail.org",
            sender_display_name = null,
            undo_seconds = 10,
            draft_id = "draft_q",
        )

        assertEquals("pend_queued", result.getOrNull())
        val pending = repo.pending_undo_send.value
        assertNotNull(pending)
        pending!!.undo()

        wait_until { pending_send_dao.rows.isEmpty() }

        assertNull(pending_send_dao.get_by_id("pend_queued"))
        coVerify(exactly = 0) { send_api.send_simple(any()) }
    }

    private suspend fun schedule_threaded_reply(): String {
        every { session_key_store.get_user_id() } returns "user_1"
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "safety_reply", success = true)
        val pending_id = repo.schedule_send_with_undo(
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Re: Hi",
            body_html = "<p>thanks</p>",
            sender_email = "me@astermail.org",
            sender_display_name = "Me",
            thread_token = "thread_1",
            undo_seconds = 10,
            draft_id = null,
            reply_to_id = "msg_1",
        ).getOrThrow()
        wait_until(5000L) { runBlocking { pending_send_dao.get_by_id(pending_id)?.draft_id } != null }
        return pending_id
    }

    @Test
    fun `undo send adds the reply to the thread at once for its pending id`() = runTest {
        val pending_id = schedule_threaded_reply()

        val sends = repo.optimistic_sends.value
        assertEquals(1, sends.size)
        val send = sends.single()
        assertEquals(pending_id, send.pending_id)
        assertEquals("user_1", send.account_id)
        assertEquals("thread_1", send.thread_token)
        assertEquals("msg_1", send.reply_to_id)
        assertEquals(optimistic_send_message_id(pending_id), send.message.id)
        assertEquals("thanks", send.message.body_text)
        assertFalse(send.committed)
    }

    @Test
    fun `undo send without a thread adds nothing to any thread`() = runTest {
        coEvery { mail_api.create_message(any()) } returns CreateMailItemResponse(id = "safety_new", success = true)

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

        assertTrue(repo.optimistic_sends.value.isEmpty())
    }

    @Test
    fun `undo removes the optimistic reply and restores the draft`() = runTest {
        val pending_id = schedule_threaded_reply()
        val restored = CompletableDeferred<String>()
        val collector = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            restored.complete(repo.undo_restored_drafts.first())
        }
        wait_until { collector.isActive }
        Thread.sleep(50)
        val safety_draft = pending_send_dao.get_by_id(pending_id)?.draft_id

        val undone = await_real { repo.undo_pending_send_now(pending_id) }

        assertTrue(undone)
        assertTrue(repo.optimistic_sends.value.isEmpty())
        assertNull(pending_send_dao.get_by_id(pending_id))
        wait_until { restored.isCompleted }
        val restored_id = restored.await()
        assertTrue(restored_id.isNotBlank())
        if (!safety_draft.isNullOrBlank()) assertEquals(safety_draft, restored_id)
        collector.cancel()
        coVerify(exactly = 0) { send_api.send_simple(any()) }
    }

    @Test
    fun `undo writes the sent content into the open draft`() = runTest {
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        coEvery { mail_api.get_draft(draft_uuid) } returns mockk(relaxed = true) {
            every { version } returns 3
        }
        coEvery { mail_api.update_draft(draft_uuid, any()) } returns
            org.astermail.android.api.mail.UpdateDraftResponse(success = true, version = 4)
        val pending_id = repo.schedule_send_with_undo(
            to = listOf("friend@astermail.org"),
            cc = emptyList(),
            bcc = emptyList(),
            subject = "Re: Hi",
            body_html = "<p>final words</p>",
            sender_email = "me@astermail.org",
            sender_display_name = "Me",
            thread_token = "thread_1",
            undo_seconds = 10,
            draft_id = draft_uuid,
            reply_to_id = "msg_1",
        ).getOrThrow()

        val undone = await_real { repo.undo_pending_send_now(pending_id) }

        assertTrue(undone)
        coVerify(exactly = 1) { mail_api.update_draft(draft_uuid, any()) }
        coVerify(exactly = 0) { mail_api.create_draft(any()) }
        coVerify(exactly = 0) { send_api.send_simple(any()) }
    }

    @Test
    fun `undo after the worker claimed the send keeps the reply in the thread`() = runTest {
        val pending_id = schedule_threaded_reply()
        assertEquals(1, pending_send_dao.mark_sending(pending_id, System.currentTimeMillis()))

        val undone = await_real { repo.undo_pending_send_now(pending_id) }

        assertFalse(undone)
        assertEquals(1, repo.optimistic_sends.value.size)
        assertNotNull(pending_send_dao.get_by_id(pending_id))
    }

    @Test
    fun `commit keeps the reply until the server copy arrives and never duplicates it`() = runTest {
        val pending_id = schedule_threaded_reply()
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "server_sent_1")

        val outcome = await_real { repo.run_pending_send(pending_id) }

        assertEquals(PendingSendOutcome.SENT, outcome)
        val send = repo.optimistic_sends.value.single()
        assertTrue(send.committed)
        assertEquals("server_sent_1", send.mail_item_id)
        val before = optimistic_sends_for_thread(
            sends = repo.optimistic_sends.value,
            account_id = "user_1",
            thread_token = "thread_1",
            item_id = "msg_1",
            server_messages = emptyList(),
        )
        assertEquals(listOf(optimistic_send_message_id(pending_id)), before.map { it.id })
        val server_copy = send.message.copy(
            id = "server_sent_1",
            raw_item = send.message.raw_item.copy(id = "server_sent_1", send_status = null),
        )
        val after = optimistic_sends_for_thread(
            sends = repo.optimistic_sends.value,
            account_id = "user_1",
            thread_token = "thread_1",
            item_id = "msg_1",
            server_messages = listOf(server_copy),
        )
        assertTrue(after.isEmpty())
    }

    @Test
    fun `a failed send removes the optimistic reply`() = runTest {
        val pending_id = schedule_threaded_reply()
        every { session_key_store.has_ratchet_keys() } returns true
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } throws
            RatchetEncryptionException("friend@astermail.org", "no prekey bundle available for recipient")

        val outcome = await_real { repo.run_pending_send(pending_id) }

        assertEquals(PendingSendOutcome.FAILED, outcome)
        assertTrue(repo.optimistic_sends.value.isEmpty())
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
            CreateAttachmentResponse(id = "att_1", success = true)
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

    private fun attachment_payload(
        name: String = "report.pdf",
        bytes: ByteArray = ByteArray(1024) { (it % 97).toByte() },
    ) = ExternalAttachmentPayload(
        data = java.util.Base64.getEncoder().encodeToString(bytes),
        filename = name,
        content_type = "application/pdf",
        size_bytes = bytes.size.toLong(),
    )

    private fun attachments_json(vararg payloads: ExternalAttachmentPayload): String =
        kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(ExternalAttachmentPayload.serializer()),
            payloads.toList(),
        )

    @Test
    fun `save_draft stores attachments and fetch_draft_for_compose restores them`() = runTest {
        val bytes = ByteArray(4096) { (it % 251).toByte() }
        val captured = slot<org.astermail.android.api.mail.CreateDraftRequestBody>()
        coEvery { mail_api.create_draft(capture(captured)) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_att", success = true)

        val result = repo.save_draft(
            subject = "With a file",
            body_html = "<p>see attached</p>",
            sender_email = "me@astermail.org",
            to = listOf("friend@astermail.org"),
            attachments = listOf(attachment_payload(bytes = bytes)),
        )

        assertEquals("draft_att", result.getOrThrow())
        assertTrue(captured.captured.has_attachments)
        assertEquals(1, captured.captured.attachment_count)

        coEvery { mail_api.get_draft("draft_att") } returns org.astermail.android.api.mail.DraftItem(
            id = "draft_att",
            encrypted_content = captured.captured.encrypted_content,
            content_nonce = captured.captured.content_nonce,
            has_attachments = true,
            attachment_count = 1,
        )

        val (item, envelope) = repo.fetch_draft_for_compose("draft_att").getOrThrow()

        assertTrue(item.has_attachments)
        assertNotNull(envelope)
        val restored = envelope!!.draft_attachments
        assertEquals(1, restored.size)
        assertEquals("report.pdf", restored[0].filename)
        assertEquals("application/pdf", restored[0].content_type)
        assertArrayEquals(bytes, java.util.Base64.getDecoder().decode(restored[0].data))
    }

    @Test
    fun `save_draft keeps the draft but drops attachments over the backend limit`() = runTest {
        val captured = slot<org.astermail.android.api.mail.CreateDraftRequestBody>()
        coEvery { mail_api.create_draft(capture(captured)) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_big", success = true)
        val too_many = (0..DRAFT_MAX_ATTACHMENT_COUNT).map { attachment_payload("f$it.pdf", ByteArray(8)) }

        val result = repo.save_draft(
            subject = "Many files",
            body_html = "<p>hi</p>",
            sender_email = "me@astermail.org",
            to = listOf("friend@astermail.org"),
            attachments = too_many,
        )

        assertEquals("draft_big", result.getOrThrow())
        assertFalse(captured.captured.has_attachments)
        assertEquals(0, captured.captured.attachment_count)
        assertTrue(captured.captured.encrypted_content.length < 100_000)
    }

    @Test
    fun `the undo safety draft includes the staged attachments`() = runTest {
        val files_dir = java.nio.file.Files.createTempDirectory("outbox_test").toFile()
        every { context.filesDir } returns files_dir
        val outbox_key = ByteArray(32) { 7 }
        repo.outbox_file_cipher_override = OutboxFileCipher { outbox_key.copyOf() }
        val captured = slot<org.astermail.android.api.mail.CreateDraftRequestBody>()
        coEvery { mail_api.create_draft(capture(captured)) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "safety_att", success = true)

        await_real {
            repo.persist_and_schedule_undo_send(
                pending_id = "pend_att",
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
                attachments = listOf(attachment_payload()),
                sender_alias_hash = null,
                suppress_branding = null,
                delay_ms = 10_000L,
                draft_id = null,
            )
        }

        assertEquals("safety_att", pending_send_dao.get_by_id("pend_att")?.draft_id)
        assertTrue(captured.captured.has_attachments)
        assertEquals(1, captured.captured.attachment_count)
        val staged = java.io.File(files_dir, "outbox_attachments/pend_att.json").readBytes()
        assertTrue(OutboxFileCipher.is_sealed(staged))
        assertFalse(String(staged, Charsets.ISO_8859_1).contains("report"))
        files_dir.deleteRecursively()
    }

    @Test
    fun `a permanently failed send rewrites its draft with the attachments`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } throws
            RatchetEncryptionException("friend@astermail.org", "no prekey bundle available for recipient")
        coEvery { mail_api.get_draft("5b0c7c3e-2a41-4f6e-9d7a-1c2b3d4e5f60") } returns
            org.astermail.android.api.mail.DraftItem(id = "5b0c7c3e-2a41-4f6e-9d7a-1c2b3d4e5f60", version = 1)
        coEvery { mail_api.update_draft(any(), any()) } returns
            org.astermail.android.api.mail.UpdateDraftResponse(success = true, version = 2)
        coEvery { mail_api.create_draft(any()) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_new", success = true)
        pending_send_dao.upsert(
            pending_row("pend_keep", draft_id = "5b0c7c3e-2a41-4f6e-9d7a-1c2b3d4e5f60").copy(attachments_json = attachments_json(attachment_payload())),
        )

        val outcome = repo.run_pending_send("pend_keep")

        assertEquals(PendingSendOutcome.FAILED, outcome)
        assertEquals("failed", pending_send_dao.get_by_id("pend_keep")?.status)
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
        coVerify {
            mail_api.update_draft("5b0c7c3e-2a41-4f6e-9d7a-1c2b3d4e5f60", match { it.has_attachments && it.attachment_count == 1 })
        }
    }

    @Test
    fun `a self send with attachments uses the local key when the key lookup fails`() = runTest {
        val keys = org.astermail.android.crypto.PgpKeyGenerator.generate("Me", "me@astermail.org", "pw".toCharArray())
        every { session_key_store.get_identity_key() } returns keys.armored_private_key
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { keys_api.get_recipient_public_key(any(), any()) } throws
            org.astermail.android.api.ApiError.NotFoundError
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_self")
        pending_send_dao.upsert(
            pending_row("pend_self", to = "me@astermail.org").copy(attachments_json = attachments_json(attachment_payload())),
        )

        val outcome = repo.run_pending_send("pend_self")

        assertEquals(PendingSendOutcome.SENT, outcome)
        coVerify { keys_api.get_recipient_public_key("me", "me@astermail.org") }
        coVerify(exactly = 1) { send_api.send_simple(any()) }
    }

    @Test
    fun `a send to a registered alias uses the local key when the key lookup fails`() = runTest {
        val keys = org.astermail.android.crypto.PgpKeyGenerator.generate("Me", "me@astermail.org", "pw".toCharArray())
        every { session_key_store.get_identity_key() } returns keys.armored_private_key
        coEvery { keys_api.get_recipient_public_key(any(), any()) } returns
            org.astermail.android.api.keys.PublicKeyResponse(username = "helper", public_key = "")
        repo.set_own_addresses(listOf("Helper@AsterMail.org"))

        val found = repo.fetch_internal_public_keys(listOf("helper@astermail.org", "helper@astermail.org"))

        assertEquals(1, found.size)
        assertTrue(found[0].contains("BEGIN PGP PUBLIC KEY"))
    }

    @Test
    fun `a send to someone else with no key still fails permanently`() = runTest {
        val keys = org.astermail.android.crypto.PgpKeyGenerator.generate("Me", "me@astermail.org", "pw".toCharArray())
        every { session_key_store.get_identity_key() } returns keys.armored_private_key
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any()) } returns "enc_ratchet_body"
        coEvery { keys_api.get_recipient_public_key(any(), any()) } throws
            org.astermail.android.api.ApiError.NotFoundError
        val captured = slot<org.astermail.android.api.mail.CreateDraftRequestBody>()
        coEvery { mail_api.create_draft(capture(captured)) } returns
            org.astermail.android.api.mail.CreateDraftResponse(id = "draft_saved", success = true)
        pending_send_dao.upsert(
            pending_row("pend_other").copy(attachments_json = attachments_json(attachment_payload())),
        )

        val outcome = repo.run_pending_send("pend_other")

        assertEquals(PendingSendOutcome.FAILED, outcome)
        coVerify(exactly = 0) { send_api.send_simple(any()) }
        assertEquals("draft_saved", pending_send_dao.get_by_id("pend_other")?.draft_id)
        assertTrue(captured.captured.has_attachments)

        val direct = runCatching { repo.fetch_internal_public_keys(listOf("friend@astermail.org"), "me@astermail.org") }
        assertTrue(direct.exceptionOrNull() is E2eEncryptionException)
        assertTrue(is_permanent_send_failure_cause(direct.exceptionOrNull()))
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

    @Test
    fun `reconcile_pending_sends exposes the oldest failed message with its recipients`() = runTest {
        pending_send_dao.rows["p_old"] = pending_row("p_old", status = "failed", to = "first@astermail.org")
        pending_send_dao.rows["p_new"] = pending_row("p_new", status = "failed", to = "second@astermail.org")
            .copy(created_at_ms = 10L)

        repo.reconcile_pending_sends()

        val notice = repo.failed_send_notice.value
        assertEquals("p_old", notice?.id)
        assertEquals(listOf("first@astermail.org"), notice?.recipients)
        assertEquals("Subject", notice?.subject)
        assertEquals(1, notice?.more_count)
        assertTrue(repo.send_problem.value)
    }

    @Test
    fun `retry_failed_send requeues one message and keeps the others failed`() = runTest {
        pending_send_dao.rows["p_a"] = pending_row("p_a", status = "failed")
        pending_send_dao.rows["p_b"] = pending_row("p_b", status = "failed").copy(created_at_ms = 10L)
        repo.reconcile_pending_sends()

        repo.retry_failed_send("p_a")

        assertEquals("pending", pending_send_dao.rows["p_a"]?.status)
        assertEquals("failed", pending_send_dao.rows["p_b"]?.status)
        assertEquals(1, repo.failed_send_count.value)
        assertEquals("p_b", repo.failed_send_notice.value?.id)
    }

    @Test
    fun `retry_failed_send with send anyway allows standard encryption`() = runTest {
        pending_send_dao.rows["p_pq"] = pending_row("p_pq", status = "failed")
        repo.reconcile_pending_sends()

        repo.retry_failed_send("p_pq", allow_non_post_quantum = true)

        assertEquals("pending", pending_send_dao.rows["p_pq"]?.status)
        assertTrue(pending_send_dao.rows["p_pq"]?.allow_non_post_quantum == true)
        assertEquals(0, repo.failed_send_count.value)
        assertNull(repo.failed_send_notice.value)
        assertFalse(repo.send_problem.value)
    }

    @Test
    fun `discard_failed_send deletes only that message`() = runTest {
        pending_send_dao.rows["p_a"] = pending_row("p_a", status = "failed")
        pending_send_dao.rows["p_b"] = pending_row("p_b", status = "failed").copy(created_at_ms = 10L)
        pending_send_dao.rows["p_ok"] = pending_row("p_ok", status = "pending")
        repo.reconcile_pending_sends()

        repo.discard_failed_send("p_a")

        assertNull(pending_send_dao.rows["p_a"])
        assertNotNull(pending_send_dao.rows["p_b"])
        assertNotNull(pending_send_dao.rows["p_ok"])
        assertEquals(1, repo.failed_send_count.value)
        assertTrue(repo.send_problem.value)
    }

    @Test
    fun `retry_failed_send ignores a message that is no longer failed`() = runTest {
        pending_send_dao.rows["p_ok"] = pending_row("p_ok", status = "pending")

        repo.retry_failed_send("p_ok")

        assertEquals("pending", pending_send_dao.rows["p_ok"]?.status)
        assertFalse(pending_send_dao.rows["p_ok"]?.allow_non_post_quantum == true)
    }

    @Test
    fun `clear_caches keeps the persistent ratchet plaintext cache`() = runTest {
        repo.clear_caches()

        verify(exactly = 0) { ratchet_plaintext_cache.clear() }
    }

    @Test
    fun `clear_account_data wipes the persistent ratchet plaintext cache`() = runTest {
        repo.clear_account_data()

        verify(exactly = 1) { ratchet_plaintext_cache.clear() }
    }

    private fun scheduled_fixture(
        id: String,
        subject: String,
        recipients: List<String>,
        status: String = "pending",
        scheduled_at: String = "2026-09-01T10:00:00Z",
    ): Pair<org.astermail.android.api.scheduled.ScheduledSummary, org.astermail.android.api.scheduled.ScheduledDetailResponse> {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12) { (it + 7).toByte() }
        val envelope = org.json.JSONObject()
            .put("to_recipients", org.json.JSONArray(recipients))
            .put("subject", subject)
            .put("body", "<p>hello</p>")
            .toString()
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            javax.crypto.Cipher.ENCRYPT_MODE,
            javax.crypto.spec.SecretKeySpec(key, "AES"),
            javax.crypto.spec.GCMParameterSpec(128, nonce),
        )
        val sealed = cipher.doFinal(envelope.toByteArray(Charsets.UTF_8))
        val encoder = java.util.Base64.getEncoder()
        return org.astermail.android.api.scheduled.ScheduledSummary(
            id = id,
            recipient_count = recipients.size,
            scheduled_at = scheduled_at,
            status = status,
        ) to org.astermail.android.api.scheduled.ScheduledDetailResponse(
            id = id,
            encrypted_envelope = encoder.encodeToString(sealed),
            envelope_nonce = encoder.encodeToString(nonce),
            recipient_count = recipients.size,
            scheduled_at = scheduled_at,
            status = status,
            ephemeral_key = encoder.encodeToString(key),
        )
    }

    @Test
    fun `fetch_scheduled reads the scheduled endpoint and decrypts each envelope`() = runTest {
        val (summary, detail) = scheduled_fixture("s1", "Quarterly update", listOf("ada@astermail.org"))
        coEvery { scheduled_api.list_scheduled(any(), any()) } returns
            org.astermail.android.api.scheduled.ListScheduledResponse(
                items = listOf(summary),
                total = 1,
                limit = 50,
                offset = 0,
            )
        coEvery { scheduled_api.get_scheduled("s1") } returns detail

        val page = repo.fetch_scheduled().getOrThrow()

        assertEquals(1, page.items.size)
        assertEquals("Quarterly update", page.items[0].subject)
        assertEquals(listOf("ada@astermail.org"), page.items[0].to_addresses)
        assertEquals("2026-09-01T10:00:00Z", page.items[0].timestamp)
        assertEquals("scheduled", page.items[0].raw_item.item_type)
        assertFalse(page.has_more)
    }

    @Test
    fun `fetch_scheduled hides items that are no longer waiting to send`() = runTest {
        val (pending, pending_detail) = scheduled_fixture("s1", "Still waiting", listOf("ada@astermail.org"))
        val (cancelled, cancelled_detail) = scheduled_fixture(
            "s2",
            "Called off",
            listOf("bob@astermail.org"),
            status = "cancelled",
        )
        coEvery { scheduled_api.list_scheduled(any(), any()) } returns
            org.astermail.android.api.scheduled.ListScheduledResponse(
                items = listOf(pending, cancelled),
                total = 2,
                limit = 50,
                offset = 0,
            )
        coEvery { scheduled_api.get_scheduled("s1") } returns pending_detail
        coEvery { scheduled_api.get_scheduled("s2") } returns cancelled_detail

        val page = repo.fetch_scheduled().getOrThrow()

        assertEquals(1, page.items.size)
        assertEquals("s1", page.items[0].id)
    }

    @Test
    fun `send_email seals hidden bcc separately from the shared copy`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), match { "hidden@astermail.org" !in it }, any(), any()) } returns "shared_body"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), listOf("hidden@astermail.org"), any(), any()) } returns "hidden_body"
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_bcc")

        val result = repo.send_email(
            to = listOf("to@astermail.org"),
            bcc = listOf("hidden@astermail.org", "TO@astermail.org"),
            subject = "Hi",
            body_html = "<p>hello</p>",
        )

        assertTrue(result.isSuccess)
        val request = slot<org.astermail.android.api.send.SimpleSendRequest>()
        coVerify(exactly = 1) { send_api.send_simple(capture(request)) }
        assertEquals("shared_body", request.captured.body)
        assertEquals(mapOf("hidden@astermail.org" to "hidden_body"), request.captured.recipient_bodies)
        coVerify(exactly = 0) {
            ratchet_encryptor.encrypt_envelope(any(), match { it.size > 1 && "hidden@astermail.org" in it }, any(), any())
        }
    }

    @Test
    fun `send_email sends no private copies without hidden bcc`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) } returns "shared_body"
        coEvery { send_api.send_simple(any()) } returns
            SimpleSendResponse(success = true, message = "ok", mail_item_id = "sent_plain")

        repo.send_email(
            to = listOf("to@astermail.org"),
            cc = listOf("cc@astermail.org"),
            subject = "Hi",
            body_html = "<p>hello</p>",
        )

        val request = slot<org.astermail.android.api.send.SimpleSendRequest>()
        coVerify(exactly = 1) { send_api.send_simple(capture(request)) }
        assertNull(request.captured.recipient_bodies)
    }

    @Test
    fun `internal schedule is sealed locally and sends no ephemeral key`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), match { "hidden@astermail.org" !in it }, any(), any()) } returns "shared_body"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), listOf("hidden@astermail.org"), any(), any()) } returns "hidden_body"
        coEvery { scheduled_api.create_scheduled(any()) } returns
            org.astermail.android.api.scheduled.CreateScheduledResponse(id = "sched_1", success = true)
        val scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(2)).toString()

        val result = repo.schedule_email(
            subject = "Later",
            body_html = "<p>later</p>",
            sender_email = "me@astermail.org",
            to = listOf("to@astermail.org"),
            bcc = listOf("hidden@astermail.org"),
            scheduled_at = scheduled_at,
        )

        assertEquals("sched_1", result.getOrThrow())
        val request = slot<org.astermail.android.api.scheduled.CreateScheduledRequest>()
        coVerify(exactly = 1) { scheduled_api.create_scheduled(capture(request)) }
        val sent = request.captured
        assertNull(sent.ephemeral_key)
        assertNull(sent.base_nonce)
        assertEquals(false, sent.is_external)
        assertEquals(2, sent.recipient_count)
        val delivery = sent.delivery!!
        assertEquals("shared_body", delivery.internal_encrypted_body)
        assertEquals(mapOf("hidden@astermail.org" to "hidden_body"), delivery.recipient_bodies)
        assertEquals(listOf("hidden@astermail.org"), delivery.bcc)
        assertTrue(delivery.hosted_recipients.isEmpty())

        val key = java.security.MessageDigest.getInstance("SHA-256")
            .digest("test_identity_keyastermail-scheduled-v1".toByteArray(Charsets.UTF_8))
        val envelope_nonce = java.util.Base64.getDecoder().decode(sent.envelope_nonce)
        val recipients_nonce = java.util.Base64.getDecoder().decode(sent.recipients_nonce)
        assertFalse(envelope_nonce.contentEquals(recipients_nonce))
        val envelope = String(
            org.astermail.android.crypto.AesGcm.decrypt(
                key,
                envelope_nonce,
                java.util.Base64.getDecoder().decode(sent.encrypted_envelope),
            ),
            Charsets.UTF_8,
        )
        assertTrue(envelope.contains("Later"))
    }

    @Test
    fun `external schedule keeps the ephemeral key and sends no delivery`() = runTest {
        coEvery { scheduled_api.create_scheduled(any()) } returns
            org.astermail.android.api.scheduled.CreateScheduledResponse(id = "sched_ext", success = true)
        val scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString()

        val result = repo.schedule_email(
            subject = "Later",
            body_html = "<p>later</p>",
            to = listOf("friend@example.com"),
            scheduled_at = scheduled_at,
        )

        assertEquals("sched_ext", result.getOrThrow())
        val request = slot<org.astermail.android.api.scheduled.CreateScheduledRequest>()
        coVerify(exactly = 1) { scheduled_api.create_scheduled(capture(request)) }
        assertNotNull(request.captured.ephemeral_key)
        assertEquals(true, request.captured.is_external)
        assertNull(request.captured.delivery)
        coVerify(exactly = 0) { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) }
    }

    @Test
    fun `schedule beyond twenty eight days is refused before any request`() = runTest {
        val scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(29)).toString()

        val result = repo.schedule_email(
            subject = "Later",
            body_html = "<p>later</p>",
            to = listOf("to@astermail.org"),
            scheduled_at = scheduled_at,
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { scheduled_api.create_scheduled(any()) }
    }

    @Test
    fun `mixed schedule seals the internal copy and keeps the outside half external`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) } returns "shared_body"
        coEvery { scheduled_api.create_scheduled(any()) } returns
            org.astermail.android.api.scheduled.CreateScheduledResponse(id = "sched_mixed", success = true)
        val scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString()

        val result = repo.schedule_email(
            subject = "Later",
            body_html = "<p>later</p>",
            sender_email = "me@astermail.org",
            to = listOf("to@astermail.org", "friend@example.com"),
            scheduled_at = scheduled_at,
        )

        assertEquals("sched_mixed", result.getOrThrow())
        val request = slot<org.astermail.android.api.scheduled.CreateScheduledRequest>()
        coVerify(exactly = 1) { scheduled_api.create_scheduled(capture(request)) }
        val sent = request.captured
        assertEquals(true, sent.is_external)
        assertNotNull(sent.ephemeral_key)
        val delivery = sent.delivery!!
        assertEquals("shared_body", delivery.internal_encrypted_body)
        assertTrue(delivery.recipient_bodies.isNullOrEmpty())
        assertEquals(listOf("to@astermail.org", "friend@example.com"), delivery.to)
        coVerify(exactly = 1) {
            ratchet_encryptor.encrypt_envelope(any(), listOf("to@astermail.org"), any(), any())
        }
        coVerify(exactly = 0) {
            ratchet_encryptor.encrypt_envelope(any(), match { "friend@example.com" in it }, any(), any())
        }
    }

    @Test
    fun `mixed schedule seals a hidden internal bcc on its own`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), listOf("to@astermail.org"), any(), any()) } returns "shared_body"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), listOf("hidden@astermail.org"), any(), any()) } returns "hidden_body"
        coEvery { scheduled_api.create_scheduled(any()) } returns
            org.astermail.android.api.scheduled.CreateScheduledResponse(id = "sched_mixed_bcc", success = true)
        val scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString()

        repo.schedule_email(
            subject = "Later",
            body_html = "<p>later</p>",
            sender_email = "me@astermail.org",
            to = listOf("to@astermail.org", "friend@example.com"),
            bcc = listOf("hidden@astermail.org"),
            scheduled_at = scheduled_at,
        ).getOrThrow()

        val request = slot<org.astermail.android.api.scheduled.CreateScheduledRequest>()
        coVerify(exactly = 1) { scheduled_api.create_scheduled(capture(request)) }
        val delivery = request.captured.delivery!!
        assertEquals("shared_body", delivery.internal_encrypted_body)
        assertEquals(mapOf("hidden@astermail.org" to "hidden_body"), delivery.recipient_bodies)
    }

    @Test
    fun `mixed send is queued with a sealed internal copy`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), listOf("to@astermail.org"), any(), any()) } returns "shared_body"
        coEvery { ratchet_encryptor.encrypt_envelope(any(), listOf("hidden@astermail.org"), any(), any()) } returns "hidden_body"

        val result = repo.send_email(
            to = listOf("to@astermail.org", "friend@example.com"),
            bcc = listOf("hidden@astermail.org"),
            subject = "Hi",
            body_html = "<p>hello</p>",
            sender_email = "me@astermail.org",
        )

        assertTrue(result.getOrThrow().success)
        val request = slot<org.astermail.android.api.send.QueueEmailRequest>()
        coVerify(exactly = 1) { send_api.queue_email(capture(request)) }
        val queued = request.captured
        assertEquals(MIXED_SEND_QUEUE_DELAY_SECONDS, queued.delay_seconds)
        assertEquals(false, queued.is_e2e_encrypted)
        assertEquals("shared_body", queued.internal_encrypted_body)
        assertEquals(mapOf("hidden@astermail.org" to "hidden_body"), queued.recipient_bodies)
        assertNotNull(queued.folder_token)
        coVerify(exactly = 0) { send_api.send_simple(any()) }
        coVerify(exactly = 0) {
            ratchet_encryptor.encrypt_envelope(any(), match { "friend@example.com" in it }, any(), any())
        }
    }

    private fun stub_mixed_queue(queue_id: String = "q_1") {
        every { session_key_store.has_ratchet_keys() } returns true
        coEvery { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) } returns "shared_body"
        coEvery { send_api.queue_email(any()) } returns
            org.astermail.android.api.send.QueueEmailResponse(queue_id = queue_id, delay_seconds = 1)
    }

    private suspend fun send_mixed(client_send_id: String? = null) = repo.send_email(
        to = listOf("to@astermail.org", "friend@example.com"),
        subject = "Hi",
        body_html = "<p>hello</p>",
        sender_email = "me@astermail.org",
        client_send_id = client_send_id,
    )

    @Test
    fun `mixed send carries the client send id to the queue`() = runTest {
        stub_mixed_queue()
        coEvery { send_api.queue_status("q_1") } returns
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "sent")

        val key = "0f8fad5b-d9cb-469f-a165-70867728950e"
        assertTrue(send_mixed(client_send_id = key).getOrThrow().success)

        val request = slot<org.astermail.android.api.send.QueueEmailRequest>()
        coVerify(exactly = 1) { send_api.queue_email(capture(request)) }
        assertEquals(key, request.captured.client_send_id)
    }

    @Test
    fun `mixed send returns the sent item once the queue reports sent`() = runTest {
        stub_mixed_queue()
        coEvery { send_api.queue_status("q_1") } returnsMany listOf(
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "pending"),
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "sending"),
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "sent", mail_item_id = "item_9"),
        )

        val response = send_mixed().getOrThrow()

        assertTrue(response.success)
        assertEquals("item_9", response.mail_item_id)
        coVerify(exactly = 3) { send_api.queue_status("q_1") }
    }

    @Test
    fun `mixed send fails when the queue reports failed`() = runTest {
        stub_mixed_queue()
        coEvery { send_api.queue_status("q_1") } returns
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "failed")

        val err = send_mixed().exceptionOrNull()

        assertTrue(err is QueuedSendFailedException)
        assertTrue(is_permanent_send_failure_cause(err))
        assertEquals(SendFailureReason.REJECTED, send_failure_reason_for(err))
    }

    @Test
    fun `mixed send fails when the queued copy was cancelled`() = runTest {
        stub_mixed_queue()
        coEvery { send_api.queue_status("q_1") } returns
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "cancelled")

        assertTrue(send_mixed().exceptionOrNull() is QueuedSendFailedException)
    }

    @Test
    fun `mixed send still pending at the poll limit counts as sent`() = runTest {
        stub_mixed_queue()
        coEvery { send_api.queue_status("q_1") } returns
            org.astermail.android.api.send.QueueStatusResponse(queue_id = "q_1", status = "pending")

        val response = send_mixed().getOrThrow()

        assertTrue(response.success)
        assertNull(response.mail_item_id)
        coVerify(exactly = MIXED_SEND_STATUS_POLLS) { send_api.queue_status("q_1") }
    }

    @Test
    fun `mixed send ignores status lookup errors`() = runTest {
        stub_mixed_queue()
        coEvery { send_api.queue_status("q_1") } throws
            org.astermail.android.api.ApiError.NetworkError

        assertTrue(send_mixed().getOrThrow().success)
        coVerify(exactly = MIXED_SEND_STATUS_POLLS) { send_api.queue_status("q_1") }
    }

    @Test
    fun `mixed send without a queue id skips the status lookup`() = runTest {
        stub_mixed_queue(queue_id = "")

        assertTrue(send_mixed().getOrThrow().success)
        coVerify(exactly = 0) { send_api.queue_status(any()) }
    }

    @Test
    fun `mixed send with a message password is refused before any request`() = runTest {
        val result = repo.send_email(
            to = listOf("to@astermail.org", "friend@example.com"),
            subject = "Hi",
            body_html = "<p>hello</p>",
            expires_at = java.time.Instant.now().plus(java.time.Duration.ofDays(1)).toString(),
            expiry_password = "correct horse battery staple",
        )

        assertTrue(result.exceptionOrNull() is MixedRecipientsPasswordException)
        coVerify(exactly = 0) { send_api.queue_email(any()) }
        coVerify(exactly = 0) { send_api.send_simple(any()) }
        coVerify(exactly = 0) { ratchet_encryptor.encrypt_envelope(any(), any(), any(), any()) }
    }

    @Test
    fun `mixed send without ratchet keys is refused before any request`() = runTest {
        every { session_key_store.has_ratchet_keys() } returns false

        val result = repo.send_email(
            to = listOf("to@astermail.org", "friend@example.com"),
            subject = "Hi",
            body_html = "<p>hello</p>",
            sender_email = "me@astermail.org",
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { send_api.queue_email(any()) }
        coVerify(exactly = 0) { send_api.send_simple(any()) }
    }

    @Test
    fun `reschedule beyond twenty eight days is refused before any request`() = runTest {
        val scheduled_at = java.time.Instant.now().plus(java.time.Duration.ofDays(30)).toString()

        val result = repo.reschedule_scheduled("sched_1", scheduled_at)

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { scheduled_api.reschedule(any(), any()) }
    }

    @Test
    fun `run_pending_send stops when an external recipient key changed while queued`() = runTest {
        pending_send_dao.upsert(pending_row("pend_key", draft_id = "draft_key", to = "friend@example.com"))
        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(
                email = "friend@example.com",
                found = true,
                fingerprint_change = org.astermail.android.api.keys.ExternalKeyFingerprintChange(
                    prior_fingerprint = "aa",
                    new_fingerprint = "bb",
                ),
            ),
        )

        val outcome = repo.run_pending_send("pend_key")

        assertEquals(PendingSendOutcome.FAILED, outcome)
        assertEquals("failed", pending_send_dao.get_by_id("pend_key")?.status)
        coVerify(exactly = 0) { send_api.send_external(any()) }
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun `run_pending_send delivers an external send when no key changed`() = runTest {
        pending_send_dao.upsert(pending_row("pend_ok", draft_id = "draft_ok", to = "friend@example.com"))
        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(email = "friend@example.com", found = true),
        )
        coEvery { send_api.send_external(any()) } returns
            org.astermail.android.api.send.ExternalSendResponse(success = true, mail_item_id = "m_ok")
        coEvery { mail_api.delete_draft(any()) } returns DeleteResponse(success = true, deleted_count = 1)

        val outcome = repo.run_pending_send("pend_ok")

        assertEquals(PendingSendOutcome.SENT, outcome)
        coVerify(exactly = 1) { send_api.send_external(any()) }
    }

    @Test
    fun `run_pending_send keeps an external send queued when the key lookup fails`() = runTest {
        pending_send_dao.upsert(pending_row("pend_net", draft_id = "draft_net", to = "friend@example.com"))
        coEvery { keys_api.discover_external_keys_batch(any()) } throws java.io.IOException("offline")

        val outcome = repo.run_pending_send("pend_net")

        assertEquals(PendingSendOutcome.RETRY, outcome)
        assertEquals("pending", pending_send_dao.get_by_id("pend_net")?.status)
        coVerify(exactly = 0) { send_api.send_external(any()) }
    }

    @Test
    fun `run_pending_send defers instead of failing when the key lookup keeps failing`() = runTest {
        pending_send_dao.upsert(pending_row("pend_net_max", draft_id = "draft_net_max", to = "friend@example.com"))
        coEvery { keys_api.discover_external_keys_batch(any()) } throws java.io.IOException("offline")

        val outcome = repo.run_pending_send("pend_net_max", attempt = SEND_RETRY_MAX_ATTEMPTS)

        assertEquals(PendingSendOutcome.DEFERRED, outcome)
        assertEquals("pending", pending_send_dao.get_by_id("pend_net_max")?.status)
        coVerify(exactly = 0) { send_api.send_external(any()) }
    }

    @Test
    fun `a slow cold key lookup is reused instead of repeated at send`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        coEvery { keys_api.discover_external_keys_batch(any()) } coAnswers {
            gate.await()
            listOf(org.astermail.android.api.keys.ExternalKeyInfo(email = "kontakt@distrust.pl", found = false))
        }

        repo.prefetch_external_key_trust(listOf("Kontakt@distrust.pl"))
        val pending = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            repo.verified_external_key_fingerprint_changes(listOf("kontakt@distrust.pl"))
        }
        assertFalse(pending.isCompleted)
        gate.complete(Unit)

        assertEquals(emptyList<RecipientKeyChange>(), pending.await().getOrThrow())
        assertEquals(
            emptyList<RecipientKeyChange>(),
            repo.verified_external_key_fingerprint_changes(listOf("kontakt@distrust.pl")).getOrThrow(),
        )
        coVerify(exactly = 1) { keys_api.discover_external_keys_batch(any()) }
    }

    @Test
    fun `a recipient with a key is checked again at send even after a recent lookup`() = runTest {
        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(email = "friend@example.com", found = true),
        )
        assertEquals(
            emptyList<RecipientKeyChange>(),
            repo.verified_external_key_fingerprint_changes(listOf("friend@example.com")).getOrThrow(),
        )

        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(
                email = "friend@example.com",
                found = true,
                fingerprint_change = org.astermail.android.api.keys.ExternalKeyFingerprintChange("aa", "bb"),
            ),
        )

        val changes = repo.verified_external_key_fingerprint_changes(listOf("friend@example.com")).getOrThrow()
        assertEquals(listOf(RecipientKeyChange("friend@example.com", "aa", "bb")), changes)
        coVerify(exactly = 2) { keys_api.discover_external_keys_batch(any()) }
    }

    @Test
    fun `a failed key lookup is not cached and still blocks the send`() = runTest {
        coEvery { keys_api.discover_external_keys_batch(any()) } throws java.io.IOException("offline")

        assertTrue(repo.verified_external_key_fingerprint_changes(listOf("friend@example.com")).isFailure)

        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(
                email = "friend@example.com",
                found = true,
                fingerprint_change = org.astermail.android.api.keys.ExternalKeyFingerprintChange("aa", "bb"),
            ),
        )

        val changes = repo.verified_external_key_fingerprint_changes(listOf("friend@example.com")).getOrThrow()
        assertEquals(listOf(RecipientKeyChange("friend@example.com", "aa", "bb")), changes)
        coVerify(exactly = 2) { keys_api.discover_external_keys_batch(any()) }
    }

    @Test
    fun `run_pending_send refuses a queued external send with a weak message password`() = runTest {
        pending_send_dao.upsert(
            pending_row("pend_weak", draft_id = "draft_weak", to = "friend@example.com")
                .copy(expires_at = "2030-01-01T00:00:00Z", expiry_password = "password"),
        )
        coEvery { keys_api.discover_external_keys_batch(any()) } returns listOf(
            org.astermail.android.api.keys.ExternalKeyInfo(email = "friend@example.com", found = true),
        )

        val outcome = repo.run_pending_send("pend_weak")

        assertEquals(PendingSendOutcome.FAILED, outcome)
        assertEquals("failed", pending_send_dao.get_by_id("pend_weak")?.status)
        coVerify(exactly = 0) { send_api.send_external(any()) }
    }

    @Test
    fun `collect_scope_ids pages the inbox by cursor until the folder is exhausted`() = runTest {
        val first = (1..500).map { MailItem(id = "a$it") }
        val second = (1..300).map { MailItem(id = "b$it") } + MailItem(id = "a1") + MailItem(id = "r1", is_reaction = true)
        coEvery { mail_api.list_messages(any(), null, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = first, has_more = true, next_cursor = "c2")
        coEvery { mail_api.list_messages(any(), "c2", any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = second, has_more = false, next_cursor = null)

        val result = repo.collect_scope_ids("inbox").getOrThrow()

        assertEquals(800, result.ids.size)
        assertFalse(result.capped)
        assertFalse("r1" in result.ids)
        coVerify(exactly = 2) {
            mail_api.list_messages(
                limit = SCOPE_ID_PAGE_SIZE,
                cursor = any(),
                offset = null,
                item_type = "received",
                is_starred = any(),
                is_trashed = false,
                is_archived = false,
                is_spam = false,
                label_token = null,
                tag_token = null,
                group_by_thread = false,
                is_snoozed = false,
                routing_token = any(),
                order = "desc",
                skip_total = true,
                include_envelope = false,
                pinned_first = any(),
            )
        }
    }

    @Test
    fun `collect_routing_ids pages an alias listing by offset`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), 0, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = (1..500).map { MailItem(id = "m$it") }, has_more = true)
        coEvery { mail_api.list_messages(any(), any(), 500, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = (501..560).map { MailItem(id = "m$it") } + MailItem(id = "r1", is_reaction = true), has_more = false)

        val result = repo.collect_routing_ids("hash-alias", "received").getOrThrow()

        assertEquals(560, result.ids.size)
        assertFalse(result.capped)
        assertFalse("r1" in result.ids)
        coVerify(exactly = 2) {
            mail_api.list_messages(
                limit = SCOPE_ID_PAGE_SIZE,
                cursor = any(),
                offset = any(),
                item_type = null,
                is_starred = any(),
                is_trashed = false,
                is_archived = any(),
                is_spam = any(),
                include_spam = any(),
                include_trash = any(),
                label_token = null,
                tag_token = null,
                group_by_thread = false,
                is_snoozed = any(),
                routing_token = "hash-alias",
                order = "desc",
                skip_total = true,
                include_envelope = false,
                direction = "received",
                pinned_first = any(),
            )
        }
    }

    @Test
    fun `collect_scope_ids pages a folder by offset`() = runTest {
        coEvery { mail_api.list_messages(any(), any(), 0, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = (1..500).map { MailItem(id = "f$it") }, has_more = true)
        coEvery { mail_api.list_messages(any(), any(), 500, any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns
            MailItemsListResponse(items = (501..540).map { MailItem(id = "f$it") }, has_more = false)

        val result = repo.collect_scope_ids("label:work").getOrThrow()

        assertEquals(540, result.ids.size)
        coVerify {
            mail_api.list_messages(
                limit = any(),
                cursor = null,
                offset = 500,
                item_type = null,
                is_starred = any(),
                is_trashed = false,
                is_archived = any(),
                is_spam = any(),
                label_token = "work",
                tag_token = null,
                group_by_thread = false,
                is_snoozed = any(),
                routing_token = any(),
                order = any(),
                skip_total = any(),
                include_envelope = any(),
                pinned_first = any(),
            )
        }
    }

    @Test
    fun `collect_scope_ids refuses folders without a scope listing`() = runTest {
        assertTrue(repo.collect_scope_ids("drafts").isFailure)
    }

    @Test
    fun `bulk_scope_action keeps going while the server makes progress`() = runTest {
        val responses = ArrayDeque<BulkScopeResponse>()
        repeat(15) { responses.add(BulkScopeResponse(affected_count = 500, completed = false)) }
        responses.add(BulkScopeResponse(affected_count = 0, completed = true))
        coEvery { mail_api.bulk_action(any()) } answers { responses.removeFirst() }

        val result = repo.bulk_scope_action("inbox", "archive").getOrThrow()

        assertEquals(7500, result.affected_count)
        assertTrue(result.completed)
    }

    @Test
    fun `bulk_scope_action fails once the server stops making progress`() = runTest {
        coEvery { mail_api.bulk_action(any()) } returns BulkScopeResponse(affected_count = 0, completed = false)

        assertTrue(repo.bulk_scope_action("inbox", "archive").isFailure)
    }

    @Test
    fun `a new thread is created with empty metadata`() = runTest {
        coEvery { mail_api.get_message("original_1") } returns fake_mail_item(id = "original_1", thread_token = null)

        val token = repo.get_or_create_thread_token("original_1", null)

        assertNotNull(token)
        coVerify(exactly = 1) { mail_api.create_thread(token!!, "", "") }
        coVerify(exactly = 1) { mail_api.link_mail_to_thread("original_1", token!!) }
    }

    @Test
    fun `an existing thread token skips thread creation`() = runTest {
        assertEquals("known", repo.get_or_create_thread_token("original_1", "known"))

        coVerify(exactly = 0) { mail_api.create_thread(any(), any(), any()) }
    }

    private val inline_owner_keys by lazy {
        org.astermail.android.crypto.PgpKeyGenerator.generate("Me", "me@astermail.org", "pw".toCharArray())
    }
    private val inline_sender_keys by lazy {
        org.astermail.android.crypto.PgpKeyGenerator.generate("Sender", "sender@example.test", "sender".toCharArray())
    }
    private val inline_impostor_keys by lazy {
        org.astermail.android.crypto.PgpKeyGenerator.generate("Impostor", "impostor@example.test", "impostor".toCharArray())
    }

    private fun use_inline_owner_keys() {
        every { session_key_store.get_identity_key() } returns inline_owner_keys.armored_private_key
        every { session_key_store.get_passphrase() } answers { "pw".toByteArray(Charsets.UTF_8) }
    }

    private fun inline_ciphertext(text: String, signer: org.astermail.android.crypto.PgpKeyPairResult?, passphrase: String = ""): String =
        if (signer == null) {
            org.astermail.android.crypto.PgpEncryptor.encrypt_to_keys(text, listOf(inline_owner_keys.armored_public_key))!!
        } else {
            org.astermail.android.crypto.PgpEncryptor.encrypt_and_sign(
                text,
                listOf(inline_owner_keys.armored_public_key),
                signer.armored_private_key,
                passphrase.toCharArray(),
            )!!
        }

    private fun inline_envelope(from: String, body: String): String {
        val json = org.json.JSONObject()
            .put("from", org.json.JSONObject().put("name", "Sender").put("email", from))
            .put("subject", "Hello")
            .put("body_text", body)
        return java.util.Base64.getEncoder().encodeToString(json.toString().toByteArray(Charsets.UTF_8))
    }

    private fun discovered_sender_key(
        public_key: String = inline_sender_keys.armored_public_key,
        source: String? = "wkd",
        expires_at: String? = null,
        email: String = "sender@example.test",
    ) = org.astermail.android.api.keys.ExternalKeyInfo(
        email = email,
        found = true,
        public_key = public_key,
        source = source,
        expires_at = expires_at,
    )

    private fun open_inline(target: MailRepository, from: String, body: String) =
        target.decrypt_envelope_public(inline_envelope(from, body), "", "inline_msg", allow_external_pgp = true)!!

    private fun fresh_inline_repo(): MailRepository =
        require_encryption_repo { org.astermail.android.api.encryption.EncryptionSettings() }

    @Test
    fun `an unsigned inline PGP body decrypts without a valid signature or a key lookup`() {
        use_inline_owner_keys()
        coEvery { keys_api.discover_external_key(any()) } returns discovered_sender_key()

        val envelope = open_inline(repo, "sender@example.test", inline_ciphertext("no signature", null))

        assertEquals("no signature", envelope.body_text.trim())
        assertTrue(envelope.pgp_encrypted)
        assertEquals(org.astermail.android.crypto.PgpSignatureStatus.NONE, envelope.pgp_signature)
        coVerify(exactly = 0) { keys_api.discover_external_key(any()) }
    }

    @Test
    fun `an inline PGP body signed by the published sender key is valid`() {
        use_inline_owner_keys()
        coEvery { keys_api.discover_external_key("sender@example.test") } returns discovered_sender_key()

        val envelope = open_inline(
            repo,
            "Sender@Example.test",
            inline_ciphertext("signed by sender", inline_sender_keys, "sender"),
        )

        assertEquals("signed by sender", envelope.body_text.trim())
        assertEquals(org.astermail.android.crypto.PgpSignatureStatus.VALID, envelope.pgp_signature)
    }

    @Test
    fun `an inline PGP body signed by another key is not valid`() {
        use_inline_owner_keys()
        coEvery { keys_api.discover_external_key("sender@example.test") } returns discovered_sender_key()

        val envelope = open_inline(
            repo,
            "sender@example.test",
            inline_ciphertext("signed by someone else", inline_impostor_keys, "impostor"),
        )

        assertEquals("signed by someone else", envelope.body_text.trim())
        assertFalse(envelope.pgp_signature == org.astermail.android.crypto.PgpSignatureStatus.VALID)
    }

    @Test
    fun `a sender key from a source the sender does not control is never used to verify`() {
        use_inline_owner_keys()
        val ciphertext = inline_ciphertext("signed by sender", inline_sender_keys, "sender")

        for (source in listOf("keyserver", "database", "", null)) {
            coEvery { keys_api.discover_external_key("sender@example.test") } returns
                discovered_sender_key(source = source)

            val envelope = open_inline(fresh_inline_repo(), "sender@example.test", ciphertext)

            assertEquals("signed by sender", envelope.body_text.trim())
            assertFalse(
                "source $source",
                envelope.pgp_signature == org.astermail.android.crypto.PgpSignatureStatus.VALID,
            )
        }
    }

    @Test
    fun `an expired, unreadable or mismatched sender key is never used to verify`() {
        use_inline_owner_keys()
        val ciphertext = inline_ciphertext("signed by sender", inline_sender_keys, "sender")
        val rejected = listOf(
            discovered_sender_key(expires_at = "2000-01-01T00:00:00Z"),
            discovered_sender_key(expires_at = "not a date"),
            discovered_sender_key(email = "other@example.test"),
            discovered_sender_key(public_key = ""),
        )

        for (info in rejected) {
            coEvery { keys_api.discover_external_key("sender@example.test") } returns info

            val envelope = open_inline(fresh_inline_repo(), "sender@example.test", ciphertext)

            assertFalse(envelope.pgp_signature == org.astermail.android.crypto.PgpSignatureStatus.VALID)
        }
    }

    @Test
    fun `an Aster sender is verified against the Aster key directory only`() {
        use_inline_owner_keys()
        val friend = org.astermail.android.crypto.PgpKeyGenerator.generate("Friend", "friend@astermail.org", "friend".toCharArray())
        coEvery { keys_api.get_recipient_public_key("friend", "friend@astermail.org") } returns
            org.astermail.android.api.keys.PublicKeyResponse(username = "friend", public_key = friend.armored_public_key)

        val envelope = open_inline(repo, "friend@astermail.org", inline_ciphertext("from a friend", friend, "friend"))

        assertEquals(org.astermail.android.crypto.PgpSignatureStatus.VALID, envelope.pgp_signature)
        coVerify(exactly = 0) { keys_api.discover_external_key(any()) }
    }
}
