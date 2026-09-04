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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.astermail.android.api.ApiError
import org.astermail.android.api.mail.DeleteResponse
import org.astermail.android.api.mail.DraftItem
import org.astermail.android.api.mail.DraftsListResponse
import org.astermail.android.api.mail.MailApi
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.outbox.PendingSendEntity
import org.astermail.android.storage.search.AsterDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SentDraftCleanupInstrumentedTest {

    private lateinit var database: AsterDatabase
    private lateinit var dao: PendingSendDao

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun sweep_prefs() =
        context.getSharedPreferences("outbox_sent_drafts", Context.MODE_PRIVATE)

    private fun seed_sweep(vararg ids: String) {
        sweep_prefs().edit().putStringSet("sent_draft_ids", ids.toSet()).commit()
    }

    private fun sweep_ids(): Set<String> =
        sweep_prefs().getStringSet("sent_draft_ids", emptySet())?.toSet() ?: emptySet()

    private fun pending_row(id: String, draft_id: String, status: String) = PendingSendEntity(
        id = id,
        to_json = "[\"friend@example.com\"]",
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
        fire_at_ms = 42L,
        status = status,
        created_at_ms = 7L,
    )

    private fun draft(id: String) = DraftItem(id = id)

    private suspend fun settled_repo(mail_api: MailApi): MailRepository {
        val repo = build_repo(mail_api)
        kotlinx.coroutines.delay(500)
        return repo
    }

    private fun build_repo(mail_api: MailApi): MailRepository {
        val session_key_store = mockk<org.astermail.android.storage.SessionKeyStore>(relaxed = true)
        every { session_key_store.get_passphrase() } answers { "pass".toByteArray(Charsets.UTF_8) }
        every { session_key_store.get_identity_key() } returns "device_identity_key"
        every { session_key_store.get_legacy_keks() } returns null
        every { session_key_store.get_previous_keys() } returns null
        return MailRepository(
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
            pending_send_dao_provider = dagger.Lazy { dao },
            context = context,
            auth_repository = mockk(relaxed = true),
        )
    }

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(context, AsterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.pending_send_dao()
        sweep_prefs().edit().clear().commit()
    }

    @After
    fun teardown() {
        sweep_prefs().edit().clear().commit()
        database.close()
    }

    @Test
    fun a_safety_draft_for_an_in_flight_send_never_shows_in_drafts() = runBlocking {
        dao.upsert(pending_row("p1", "safety_draft", "pending"))
        val mail_api = mockk<MailApi>(relaxed = true)
        coEvery { mail_api.list_drafts(any(), any()) } returns DraftsListResponse(
            items = listOf(draft("safety_draft"), draft("real_draft")),
        )
        val repo = settled_repo(mail_api)

        val page = repo.fetch_drafts().getOrThrow()

        assertEquals(listOf("real_draft"), page.items.map { it.id })
    }

    @Test
    fun a_draft_awaiting_cleanup_never_shows_in_drafts() = runBlocking {
        val mail_api = mockk<MailApi>(relaxed = true)
        coEvery { mail_api.list_drafts(any(), any()) } returns DraftsListResponse(
            items = listOf(draft("orphan_draft"), draft("real_draft")),
        )
        val repo = settled_repo(mail_api)
        seed_sweep("orphan_draft")

        val page = repo.fetch_drafts().getOrThrow()

        assertEquals(listOf("real_draft"), page.items.map { it.id })
    }

    @Test
    fun a_failed_send_stops_hiding_its_draft_so_the_user_can_recover_it() = runBlocking {
        dao.upsert(pending_row("p1", "safety_draft", "failed"))
        val mail_api = mockk<MailApi>(relaxed = true)
        coEvery { mail_api.list_drafts(any(), any()) } returns DraftsListResponse(
            items = listOf(draft("safety_draft")),
        )
        val repo = settled_repo(mail_api)

        val page = repo.fetch_drafts().getOrThrow()

        assertEquals(listOf("safety_draft"), page.items.map { it.id })
    }

    @Test
    fun the_sweep_retries_a_delete_that_failed_and_then_forgets_it() = runBlocking {
        val mail_api = mockk<MailApi>(relaxed = true)
        var calls = 0
        coEvery { mail_api.delete_draft("orphan_draft") } answers {
            calls += 1
            if (calls < 3) throw java.io.IOException("offline") else DeleteResponse(success = true)
        }
        val repo = settled_repo(mail_api)
        seed_sweep("orphan_draft")

        repo.sweep_sent_drafts()

        assertEquals(3, calls)
        assertTrue("swept id must be forgotten", sweep_ids().isEmpty())
    }

    @Test
    fun the_sweep_keeps_an_id_it_could_not_delete() = runBlocking {
        val mail_api = mockk<MailApi>(relaxed = true)
        coEvery { mail_api.delete_draft("orphan_draft") } throws java.io.IOException("offline")
        val repo = settled_repo(mail_api)
        seed_sweep("orphan_draft")

        repo.sweep_sent_drafts()

        assertEquals(setOf("orphan_draft"), sweep_ids())
        coVerify(exactly = 3) { mail_api.delete_draft("orphan_draft") }
    }

    @Test
    fun a_draft_the_server_already_removed_counts_as_swept() = runBlocking {
        val mail_api = mockk<MailApi>(relaxed = true)
        coEvery { mail_api.delete_draft("orphan_draft") } throws ApiError.NotFoundError
        val repo = settled_repo(mail_api)
        seed_sweep("orphan_draft")

        repo.sweep_sent_drafts()

        assertTrue(sweep_ids().isEmpty())
        coVerify(exactly = 1) { mail_api.delete_draft("orphan_draft") }
    }
}
