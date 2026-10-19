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
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.astermail.android.api.mail.MailApi
import org.astermail.android.api.mail.MailItem
import org.astermail.android.api.mail.ThreadMessageItem
import org.astermail.android.api.network.low_network_state
import org.astermail.android.storage.search.ThreadSnapshotDao
import org.astermail.android.storage.search.ThreadSnapshotEntity
import org.astermail.android.storage.search.ThreadSnapshotStamp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ThreadSnapshotPrefetchTest {

    private class FakeThreadSnapshotDao : ThreadSnapshotDao {
        val rows = java.util.concurrent.ConcurrentHashMap<String, ThreadSnapshotEntity>()
        val get_calls = java.util.concurrent.atomic.AtomicInteger(0)
        override suspend fun get(thread_token: String): ThreadSnapshotEntity? {
            get_calls.incrementAndGet()
            return rows[thread_token]
        }
        override suspend fun stamps(thread_tokens: List<String>): List<ThreadSnapshotStamp> =
            thread_tokens.mapNotNull { token -> rows[token]?.let { ThreadSnapshotStamp(it.thread_token, it.cached_at) } }
        override suspend fun upsert(row: ThreadSnapshotEntity) { rows[row.thread_token] = row }
        override suspend fun delete(thread_token: String) { rows.remove(thread_token) }
        override suspend fun clear_all() { rows.clear() }
        override suspend fun trim_to(keep: Int) {
            val kept = rows.values.sortedByDescending { it.cached_at }.take(keep).map { it.thread_token }.toSet()
            rows.keys.filterNot { it in kept }.forEach { rows.remove(it) }
        }
    }

    private lateinit var mail_api: MailApi
    private lateinit var dao: FakeThreadSnapshotDao
    private lateinit var repo: MailRepository

    @Before
    fun setup() {
        low_network_state.reset()
        mail_api = mockk(relaxed = true)
        coEvery { mail_api.get_thread_messages(any()) } throws java.io.IOException("offline")
        dao = FakeThreadSnapshotDao()
        repo = MailRepository(
            mail_api = mail_api,
            send_api = mockk(relaxed = true),
            snooze_api = mockk(relaxed = true),
            labels_api = mockk(relaxed = true),
            keys_api = mockk(relaxed = true),
            session_key_store = mockk(relaxed = true),
            scheduled_api = mockk(relaxed = true),
            ratchet_decryptor = mockk(relaxed = true),
            ratchet_encryptor = mockk(relaxed = true),
            ratchet_plaintext_cache = mockk(relaxed = true),
            system_folder_bootstrap = mockk(relaxed = true),
            pending_send_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            message_body_dao_provider = dagger.Lazy { mockk(relaxed = true) },
            thread_snapshot_dao_provider = dagger.Lazy { dao },
            context = mockk(relaxed = true),
            auth_repository = dagger.Lazy { mockk(relaxed = true) },
        )
    }

    private fun snapshot_payload(vararg ids: String): String = thread_snapshot_json.encodeToString(
        ids.map { id ->
            thread_snapshot_message(
                id = id,
                sender_name = "Sender",
                sender_email = "sender@astermail.org",
                to_label = "me",
                timestamp = "2026-04-26T10:00:00Z",
                body_text = "body $id",
                body_html = "<p>body $id</p>",
                raw_item = ThreadMessageItem(id = id),
            )
        },
    )

    private fun seed(thread_token: String, cached_at: Long, vararg ids: String) {
        dao.rows[thread_token] = ThreadSnapshotEntity(
            thread_token = thread_token,
            payload = snapshot_payload(*ids),
            cached_at = cached_at,
        )
    }

    private fun inbox_item(id: String, thread_token: String): InboxItem = InboxItem(
        id = id,
        thread_token = thread_token,
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@astermail.org",
        subject = "Subject",
        preview = "",
        timestamp = "2026-04-26T10:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = emptyList(),
        raw_item = MailItem(id = id, item_type = "received", thread_token = thread_token),
    )

    private val items = listOf(
        inbox_item("m_cached", "t_cached"),
        inbox_item("m_new", "t_stale"),
        inbox_item("m_absent", "t_absent"),
    )

    @Test
    fun `prefetch fetches only threads whose latest message is not cached`() = runTest {
        seed("t_cached", 1L, "m_old", "m_cached")
        seed("t_stale", 1L, "m_old_reply")

        repo.prefetch_threads_for_offline(items)

        coVerify(exactly = 0) { mail_api.get_thread_messages("t_cached") }
        coVerify(exactly = 1) { mail_api.get_thread_messages("t_stale") }
        coVerify(exactly = 1) { mail_api.get_thread_messages("t_absent") }
    }

    @Test
    fun `repeat prefetch does not reread or decode unchanged snapshots`() = runTest {
        seed("t_cached", 1L, "m_old", "m_cached")
        seed("t_stale", 1L, "m_old_reply")

        repo.prefetch_threads_for_offline(items)
        repo.prefetch_threads_for_offline(items)
        repo.prefetch_threads_for_offline(items)

        assertEquals(2, dao.get_calls.get())
        coVerify(exactly = 0) { mail_api.get_thread_messages("t_cached") }
        coVerify(exactly = 3) { mail_api.get_thread_messages("t_stale") }
    }

    @Test
    fun `evicted snapshot is fetched again`() = runTest {
        seed("t_cached", 1L, "m_cached")

        repo.prefetch_threads_for_offline(items.take(1))
        dao.rows.remove("t_cached")
        repo.prefetch_threads_for_offline(items.take(1))

        coVerify(exactly = 1) { mail_api.get_thread_messages("t_cached") }
    }

    @Test
    fun `rewritten snapshot is decoded again`() = runTest {
        seed("t_stale", 1L, "m_old_reply")

        repo.prefetch_threads_for_offline(listOf(items[1]))
        seed("t_stale", 2L, "m_old_reply", "m_new")
        repo.prefetch_threads_for_offline(listOf(items[1]))

        assertEquals(2, dao.get_calls.get())
        coVerify(exactly = 1) { mail_api.get_thread_messages("t_stale") }
    }

    @Test
    fun `clearing cached bodies drops known snapshot ids`() = runTest {
        seed("t_cached", 1L, "m_cached")

        repo.prefetch_threads_for_offline(items.take(1))
        repo.clear_cached_message_bodies()
        repo.prefetch_threads_for_offline(items.take(1))

        coVerify(exactly = 1) { mail_api.get_thread_messages("t_cached") }
    }

    @Test
    fun `clear_caches forgets known snapshot ids`() = runTest {
        seed("t_cached", 1L, "m_cached")

        repo.prefetch_threads_for_offline(items.take(1))
        repo.clear_caches()
        repo.prefetch_threads_for_offline(items.take(1))

        assertEquals(2, dao.get_calls.get())
        coVerify(exactly = 0) { mail_api.get_thread_messages("t_cached") }
    }
}
