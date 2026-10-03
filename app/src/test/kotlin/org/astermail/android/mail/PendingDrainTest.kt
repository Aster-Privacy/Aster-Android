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
import org.astermail.android.api.ApiError
import org.astermail.android.api.mail.BulkScopeResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.storage.SessionKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingDrainTest {

    private lateinit var mail_api: MailApi
    private lateinit var session_key_store: SessionKeyStore
    private lateinit var queue: PendingMailActionQueue
    private lateinit var repo: MailRepository
    private val rows = mutableListOf<PendingMailAction>()
    private val completed = mutableListOf<Long>()
    private val recorded = mutableListOf<Long>()
    private var user_id = "account_a"

    private fun archive(id: Long, account: String = "account_a", attempts: Int = 0) =
        PendingMailAction(
            id,
            account,
            PendingActionKind.archive,
            PendingActionPayload(ids = listOf("item_$id")),
            System.currentTimeMillis(),
            attempts,
        )

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any(), any()) } answers {
            java.util.Base64.getEncoder().encodeToString(firstArg())
        }
        mail_api = mockk(relaxed = true)
        session_key_store = mockk(relaxed = true)
        every { session_key_store.get_user_id() } answers { user_id }
        queue = mockk(relaxed = true)
        every { queue.for_account(any()) } answers { rows.filter { it.account_id == firstArg<String?>() } }
        every { queue.has_pending(any()) } answers { rows.any { it.account_id == firstArg<String?>() } }
        coEvery { queue.complete(any()) } answers {
            val id = firstArg<Long>()
            completed.add(id)
            rows.removeAll { it.id == id }
        }
        coEvery { queue.record_attempt(any()) } answers { recorded.add(firstArg()) }
        val system_folder_bootstrap: SystemFolderBootstrap = mockk(relaxed = true)
        coEvery { system_folder_bootstrap.ensure_system_folders() } returns emptyMap()
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
            context = mockk(relaxed = true),
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
            pending_action_queue_provider = dagger.Lazy { queue },
        )
    }

    @After
    fun teardown() {
        unmockkStatic(Base64::class)
    }

    @Test
    fun a_paused_drain_replays_nothing() = runTest {
        rows.add(archive(1))
        repo.pause_pending_drain()
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        coVerify(exactly = 0) { mail_api.bulk_action(any()) }
        assertTrue(completed.isEmpty())
        repo.resume_pending_drain()
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        assertEquals(listOf(1L), completed)
    }

    @Test
    fun an_account_switch_stops_the_drain_before_the_next_action() = runTest {
        rows.add(archive(1))
        rows.add(archive(2))
        coEvery { mail_api.bulk_action(any()) } answers {
            user_id = "account_b"
            BulkScopeResponse()
        }
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        assertEquals(listOf(1L), completed)
        coVerify(exactly = 1) { mail_api.bulk_action(any()) }
    }

    @Test
    fun another_accounts_actions_are_never_replayed() = runTest {
        rows.add(archive(1, account = "account_b"))
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        coVerify(exactly = 0) { mail_api.bulk_action(any()) }
        assertTrue(completed.isEmpty())
    }

    @Test
    fun an_expired_session_keeps_the_queue_without_retrying() = runTest {
        rows.add(archive(1))
        coEvery { mail_api.bulk_action(any()) } throws ApiError.UnauthorizedError
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        assertTrue(completed.isEmpty())
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun a_lost_connection_retries_without_using_up_attempts() = runTest {
        rows.add(archive(1))
        coEvery { mail_api.bulk_action(any()) } throws java.net.UnknownHostException("offline")
        assertEquals(PendingDrainOutcome.Retry, repo.drain_pending_actions())
        assertTrue(completed.isEmpty())
        assertTrue(recorded.isEmpty())
    }

    @Test
    fun a_server_error_counts_an_attempt_and_retries() = runTest {
        rows.add(archive(1))
        coEvery { mail_api.bulk_action(any()) } throws ApiError.ServerError(503)
        assertEquals(PendingDrainOutcome.Retry, repo.drain_pending_actions())
        assertEquals(listOf(1L), recorded)
        assertTrue(completed.isEmpty())
    }

    @Test
    fun a_worn_out_action_is_dropped_so_the_queue_moves_on() = runTest {
        rows.add(archive(1, attempts = PENDING_ACTION_MAX_SERVER_ATTEMPTS - 1))
        rows.add(archive(2))
        var calls = 0
        coEvery { mail_api.bulk_action(any()) } answers {
            calls++
            if (calls == 1) throw ApiError.ServerError(503)
            BulkScopeResponse()
        }
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        assertEquals(listOf(1L, 2L), completed)
    }

    @Test
    fun a_rejected_request_is_dropped() = runTest {
        rows.add(archive(1))
        coEvery { mail_api.bulk_action(any()) } throws ApiError.ServerError(405)
        assertEquals(PendingDrainOutcome.Done, repo.drain_pending_actions())
        assertEquals(listOf(1L), completed)
    }
}
