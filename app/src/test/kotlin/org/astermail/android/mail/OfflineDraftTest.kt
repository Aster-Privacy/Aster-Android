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

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.astermail.android.api.mail.CreateDraftResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.UpdateDraftResponse
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineDraftTest {

    private val server_id = "11111111-2222-3333-4444-555555555555"
    private lateinit var mail_api: MailApi
    private lateinit var queue: PendingMailActionQueue
    private lateinit var repo: MailRepository
    private val rows = mutableListOf<PendingMailAction>()
    private val prefs_store = mutableMapOf<String, String>()
    private var online = false
    private var next_row = 1L

    private fun fake_prefs(): SharedPreferences {
        val prefs: SharedPreferences = mockk(relaxed = true)
        val editor: SharedPreferences.Editor = mockk(relaxed = true)
        val staged = mutableMapOf<String, String?>()
        var clear = false
        every { prefs.getString(any(), any()) } answers { prefs_store[firstArg()] ?: secondArg() }
        every { prefs.all } answers { prefs_store.toMap() }
        every { prefs.edit() } answers {
            staged.clear()
            clear = false
            editor
        }
        every { editor.putString(any(), any()) } answers {
            staged[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            staged[firstArg()] = null
            editor
        }
        every { editor.clear() } answers {
            clear = true
            editor
        }
        every { editor.apply() } answers {
            if (clear) prefs_store.clear()
            staged.forEach { (key, value) -> if (value == null) prefs_store.remove(key) else prefs_store[key] = value }
        }
        return prefs
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
        val session_key_store: SessionKeyStore = mockk(relaxed = true)
        every { session_key_store.get_identity_key() } returns "test_identity_key"
        every { session_key_store.get_passphrase() } returns null
        every { session_key_store.get_user_id() } returns "account_a"
        every { session_key_store.get_user_email() } returns "me@astermail.org"
        every { session_key_store.has_ratchet_keys() } returns false
        queue = mockk(relaxed = true)
        every { queue.is_network_available() } answers { online }
        every { queue.for_account(any()) } answers { rows.filter { it.account_id == firstArg<String?>() } }
        every { queue.has_pending(any()) } answers { rows.any { it.account_id == firstArg<String?>() } }
        every { queue.is_queued(any()) } answers { rows.any { it.id == firstArg<Long>() } }
        coEvery { queue.replace_draft(any(), any(), any()) } answers {
            val account = firstArg<String>()
            val key = secondArg<String>()
            rows.removeAll { it.account_id == account && it.payload.key == key }
            rows.add(
                PendingMailAction(next_row++, account, PendingActionKind.save_draft, thirdArg(), System.currentTimeMillis(), 0),
            )
        }
        coEvery { queue.remove_drafts(any()) } answers {
            val key = firstArg<String>()
            rows.removeAll { it.kind == PendingActionKind.save_draft && it.payload.key == key }
        }
        coEvery { queue.complete(any()) } answers {
            val id = firstArg<Long>()
            rows.removeAll { it.id == id }
        }
        val context: Context = mockk(relaxed = true)
        val prefs = fake_prefs()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        val system_folder_bootstrap: SystemFolderBootstrap = mockk(relaxed = true)
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns emptyMap()
        coEvery { mail_api.create_draft(any()) } returns CreateDraftResponse(id = server_id, version = 1)
        coEvery { mail_api.update_draft(server_id, any()) } returns UpdateDraftResponse(success = true, version = 2)
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
            system_folder_bootstrap = system_folder_bootstrap,
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            context = context,
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
            pending_action_queue_provider = dagger.Lazy { queue },
        )
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    private suspend fun save(body: String, queue_offline: Boolean = true) =
        repo.save_draft(subject = "Hi", body_html = body, session_id = "s1", queue_offline = queue_offline)

    @Test
    fun an_offline_save_is_kept_on_the_device() = runTest {
        val id = save("<p>1</p>").getOrThrow()

        assertTrue(is_local_draft_id(id))
        assertEquals(1, rows.size)
        assertEquals(id, rows.single().payload.key)
        assertNotNull(rows.single().payload.content)
        coVerify(exactly = 0) { mail_api.create_draft(any()) }
    }

    @Test
    fun repeated_offline_saves_keep_one_queued_row() = runTest {
        val first = save("<p>1</p>").getOrThrow()
        val second = save("<p>12</p>").getOrThrow()

        assertEquals(first, second)
        assertEquals(1, rows.size)
    }

    @Test
    fun a_save_that_does_not_opt_in_still_fails_offline() = runTest {
        coEvery { mail_api.create_draft(any()) } throws java.io.IOException("offline")

        val result = save("<p>1</p>", queue_offline = false)

        assertTrue(result.isFailure)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun the_drain_creates_the_draft_and_later_saves_update_it() = runTest {
        val local = save("<p>1</p>").getOrThrow()
        online = true

        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        assertTrue(rows.isEmpty())
        coVerify(exactly = 1) { mail_api.create_draft(any()) }

        val next = save("<p>12</p>").getOrThrow()

        assertEquals(local, next)
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
        coVerify(exactly = 1) { mail_api.update_draft(server_id, any()) }
    }

    @Test
    fun a_transient_failure_online_queues_the_draft() = runTest {
        online = true
        coEvery { mail_api.create_draft(any()) } throws java.io.IOException("timeout")

        val id = save("<p>1</p>").getOrThrow()

        assertTrue(is_local_draft_id(id))
        assertEquals(1, rows.size)
    }

    @Test
    fun an_online_save_replaces_the_queued_copy() = runTest {
        val local = save("<p>1</p>").getOrThrow()
        online = true

        val next = save("<p>12</p>").getOrThrow()

        assertEquals(local, next)
        assertTrue(rows.isEmpty())
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        coVerify(exactly = 1) { mail_api.create_draft(any()) }
    }

    @Test
    fun deleting_a_local_only_draft_never_reaches_the_server() = runTest {
        val local = save("<p>1</p>").getOrThrow()

        assertTrue(repo.delete_draft(local).isSuccess)

        assertTrue(rows.isEmpty())
        online = true
        repo.drain_pending_actions()
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
        coVerify(exactly = 0) { mail_api.create_draft(any()) }
    }

    @Test
    fun discarding_a_sent_local_draft_drops_the_queued_copy() = runTest {
        save("<p>1</p>").getOrThrow()

        assertTrue(repo.discard_sent_draft(null, "s1").await())

        assertTrue(rows.isEmpty())
        coVerify(exactly = 0) { mail_api.delete_draft(any()) }
    }

    @Test
    fun an_oversized_draft_is_not_queued() = runTest {
        coEvery { mail_api.create_draft(any()) } throws java.io.IOException("offline")

        val result = save("a".repeat(PENDING_DRAFT_MAX_CHARS + 10))

        assertTrue(result.isFailure)
        assertTrue(rows.isEmpty())
    }
}
