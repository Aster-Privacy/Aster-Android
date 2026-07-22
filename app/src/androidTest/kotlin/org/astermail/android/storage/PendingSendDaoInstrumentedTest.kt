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

package org.astermail.android.storage

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.astermail.android.storage.outbox.PendingSendEntity
import org.astermail.android.storage.search.AsterDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingSendDaoInstrumentedTest {

    private lateinit var database: AsterDatabase

    private fun row(
        id: String,
        status: String = "pending",
        draft_id: String? = null,
    ): PendingSendEntity = PendingSendEntity(
        id = id,
        to_json = "[\"friend@astermail.org\"]",
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

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AsterDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun upsert_and_get_round_trips_every_column() = runBlocking {
        val dao = database.pending_send_dao()
        val original = row("p1", draft_id = "draft_1")

        dao.upsert(original)
        val loaded = dao.get_by_id("p1")

        assertNotNull(loaded)
        assertEquals(original, loaded)
    }

    @Test
    fun mark_sending_claims_exactly_once() = runBlocking {
        val dao = database.pending_send_dao()
        dao.upsert(row("p2"))

        val first_claim = dao.mark_sending("p2", 1_000L)
        val second_claim = dao.mark_sending("p2", 1_000L)

        assertEquals(1, first_claim)
        assertEquals(0, second_claim)
        assertEquals("sending", dao.get_by_id("p2")?.status)
    }

    @Test
    fun mark_pending_allows_a_reclaim_on_retry() = runBlocking {
        val dao = database.pending_send_dao()
        dao.upsert(row("p3"))

        assertEquals(1, dao.mark_sending("p3", 1_000L))
        dao.mark_pending("p3")
        assertEquals("pending", dao.get_by_id("p3")?.status)
        assertEquals(1, dao.mark_sending("p3", 1_000L))
    }

    @Test
    fun update_draft_id_persists() = runBlocking {
        val dao = database.pending_send_dao()
        dao.upsert(row("p4", draft_id = null))

        dao.update_draft_id("p4", "safety_draft_4")

        assertEquals("safety_draft_4", dao.get_by_id("p4")?.draft_id)
    }

    @Test
    fun delete_and_clear_remove_rows() = runBlocking {
        val dao = database.pending_send_dao()
        dao.upsert(row("p5"))
        dao.upsert(row("p6"))

        dao.delete_by_id("p5")
        assertNull(dao.get_by_id("p5"))
        assertEquals(1, dao.get_all().size)

        dao.clear_all()
        assertTrue(dao.get_all().isEmpty())
    }

    @Test
    fun a_pending_row_survives_a_database_reopen() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db_name = "pending_send_reopen_test.db"
        context.deleteDatabase(db_name)

        val first = Room.databaseBuilder(context, AsterDatabase::class.java, db_name)
            .allowMainThreadQueries()
            .build()
        runBlocking { first.pending_send_dao().upsert(row("survivor", draft_id = "kept_draft")) }
        first.close()

        val second = Room.databaseBuilder(context, AsterDatabase::class.java, db_name)
            .allowMainThreadQueries()
            .build()
        val recovered = runBlocking { second.pending_send_dao().get_by_id("survivor") }
        second.close()
        context.deleteDatabase(db_name)

        assertNotNull(recovered)
        assertEquals("kept_draft", recovered?.draft_id)
        assertEquals("pending", recovered?.status)
    }
}
