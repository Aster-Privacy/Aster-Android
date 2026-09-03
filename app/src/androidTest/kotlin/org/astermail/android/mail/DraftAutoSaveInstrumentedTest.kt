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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.astermail.android.api.mail.CreateDraftResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.UpdateDraftRequestBody
import org.astermail.android.api.mail.UpdateDraftResponse
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.outbox.PendingSendEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftAutoSaveInstrumentedTest {

    private lateinit var mail_api: MailApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var repo: MailRepository

    private val first_id = "11111111-2222-3333-4444-555555555555"
    private val second_id = "99999999-8888-7777-6666-555555555555"

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
        override suspend fun clear_for_account(account_id: String) {
            rows.values.filter { it.account_id == account_id || it.account_id == null }
                .forEach { rows.remove(it.id) }
        }
        override suspend fun get_for_account(account_id: String): List<PendingSendEntity> =
            rows.values.filter { it.account_id == account_id || it.account_id == null }
    }

    @Before
    fun setup() {
        mail_api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false
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
            pending_send_dao_provider = dagger.Lazy { FakePendingSendDao() },
            context = InstrumentationRegistry.getInstrumentation().targetContext,
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
            system_folder_bootstrap = mockk(relaxed = true),
        )
    }

    @Test
    fun typing_a_message_keeps_exactly_one_draft() = runBlocking {
        var created = 0
        coEvery { mail_api.create_draft(any()) } answers {
            created += 1
            CreateDraftResponse(id = first_id, version = 1)
        }
        var version = 1
        coEvery { mail_api.update_draft(first_id, any()) } answers {
            version += 1
            UpdateDraftResponse(success = true, version = version)
        }

        val keystrokes = listOf("H", "He", "Hel", "Hell", "Hello", "Hello ", "Hello t", "Hello there")
        keystrokes.forEach { text ->
            val result = repo.save_draft(
                subject = "Emulator draft",
                body_html = "<p>$text</p>",
                session_id = "emulator_session",
            )
            assertEquals(first_id, result.getOrThrow())
        }

        assertEquals(1, created)
        coVerify(exactly = keystrokes.size - 1) { mail_api.update_draft(first_id, any()) }
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun overlapping_autosaves_never_create_a_second_draft() = runBlocking {
        var created = 0
        coEvery { mail_api.create_draft(any()) } coAnswers {
            created += 1
            kotlinx.coroutines.delay(40)
            CreateDraftResponse(id = if (created == 1) first_id else second_id, version = 1)
        }
        coEvery { mail_api.update_draft(any(), any()) } coAnswers {
            kotlinx.coroutines.delay(20)
            UpdateDraftResponse(success = true, version = 2)
        }

        val saves = (1..8).map { index ->
            async(Dispatchers.IO) {
                repo.save_draft(
                    subject = "Race",
                    body_html = "<p>body $index</p>",
                    session_id = "race_session",
                ).getOrThrow()
            }
        }
        val ids = saves.awaitAll()

        assertEquals(1, created)
        assertEquals(setOf(first_id), ids.toSet())
    }

    @Test
    fun a_cancelled_autosave_does_not_orphan_a_draft() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var created = 0
        coEvery { mail_api.create_draft(any()) } coAnswers {
            created += 1
            gate.await()
            CreateDraftResponse(id = if (created == 1) first_id else second_id, version = 1)
        }
        coEvery { mail_api.update_draft(first_id, any()) } returns
            UpdateDraftResponse(success = true, version = 2)

        val assigned = CompletableDeferred<String>()
        val job = launch(Dispatchers.IO) {
            repo.save_draft(
                subject = "Cancelled",
                body_html = "<p>body</p>",
                session_id = "cancel_session",
                on_id_assigned = { assigned.complete(it) },
            )
        }
        kotlinx.coroutines.delay(50)
        job.cancel()
        gate.complete(Unit)

        val id = withTimeout(5000) { assigned.await() }
        assertEquals(first_id, id)

        val later = repo.save_draft(
            subject = "Cancelled",
            body_html = "<p>body more</p>",
            session_id = "cancel_session",
        )

        assertEquals(first_id, later.getOrThrow())
        assertEquals(1, created)
        coVerify(exactly = 1) { mail_api.update_draft(first_id, any()) }
    }

    @Test
    fun a_version_conflict_retries_against_the_server_version() = runBlocking {
        coEvery { mail_api.create_draft(any()) } returns CreateDraftResponse(id = first_id, version = 1)
        val seen = java.util.Collections.synchronizedList(mutableListOf<Int>())
        coEvery { mail_api.update_draft(first_id, any()) } answers {
            val body = secondArg<UpdateDraftRequestBody>()
            seen.add(body.version)
            if (body.version == 1) {
                UpdateDraftResponse(success = false, version = 1, current_version = 12)
            } else {
                UpdateDraftResponse(success = true, version = 13)
            }
        }

        repo.save_draft(subject = "Conflict", body_html = "<p>a</p>", session_id = "conflict_session")
        val second = repo.save_draft(
            subject = "Conflict",
            body_html = "<p>ab</p>",
            session_id = "conflict_session",
        )

        assertEquals(first_id, second.getOrThrow())
        assertEquals(listOf(1, 12), seen.toList())
    }

    @Test
    fun a_draft_the_server_rejects_is_recreated_once() = runBlocking {
        var created = 0
        coEvery { mail_api.create_draft(any()) } answers {
            created += 1
            CreateDraftResponse(id = if (created == 1) first_id else second_id, version = 1)
        }
        coEvery { mail_api.update_draft(first_id, any()) } returns
            UpdateDraftResponse(success = false, version = 1, current_version = null)

        repo.save_draft(subject = "Gone", body_html = "<p>a</p>", session_id = "gone_session")
        val second = repo.save_draft(
            subject = "Gone",
            body_html = "<p>ab</p>",
            session_id = "gone_session",
        )

        assertEquals(second_id, second.getOrThrow())
        assertEquals(2, created)
    }

    @Test
    fun sending_removes_the_draft_and_the_session() = runBlocking {
        coEvery { mail_api.create_draft(any()) } returns CreateDraftResponse(id = first_id, version = 1)
        coEvery { mail_api.update_draft(first_id, any()) } returns
            UpdateDraftResponse(success = true, version = 2)
        coEvery { mail_api.delete_draft(first_id) } returns
            org.astermail.android.api.mail.DeleteResponse(success = true)

        repo.save_draft(subject = "Send", body_html = "<p>a</p>", session_id = "send_session")
        val deleted = repo.delete_draft(first_id)
        assertTrue(deleted.isSuccess)

        val after_send = repo.save_draft(
            subject = "Send",
            body_html = "<p>a</p>",
            session_id = "send_session",
        )

        assertEquals(first_id, after_send.getOrThrow())
        coVerify(exactly = 2) { mail_api.create_draft(any()) }
    }
}
