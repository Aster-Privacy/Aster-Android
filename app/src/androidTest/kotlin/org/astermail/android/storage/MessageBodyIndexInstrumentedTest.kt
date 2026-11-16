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
import org.astermail.android.mail.body_match_expression
import org.astermail.android.mail.build_body_index_text
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailEntity
import org.astermail.android.storage.search.MessageBodyIndexEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageBodyIndexInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val db_name = "body_index_test.db"
    private lateinit var database: AsterDatabase

    private fun open_encrypted(): AsterDatabase {
        System.loadLibrary("sqlcipher")
        return Room.databaseBuilder(context, AsterDatabase::class.java, db_name)
            .openHelperFactory(
                net.zetetic.database.sqlcipher.SupportOpenHelperFactory(ByteArray(32) { 7 }, null, false),
            )
            .allowMainThreadQueries()
            .build()
    }

    private fun entry(id: String, body: String, received_at: Long = 1L) = MessageBodyIndexEntity(
        message_id = id,
        body = build_body_index_text(body, null),
        received_at = received_at,
        indexed_at = 1L,
    )

    private fun cached(id: String) = DecryptedMailEntity(
        id = id,
        thread_token = "t_$id",
        thread_message_count = 1,
        sender_name = "Sender",
        sender_email = "sender@astermail.org",
        subject = "Subject",
        preview = "preview",
        timestamp = "2026-07-24T12:00:00Z",
        is_read = false,
        is_starred = false,
        is_encrypted = true,
        has_attachments = false,
        is_trashed = false,
        is_archived = false,
        is_spam = false,
        labels = "",
        indexed_at = 1L,
    )

    private suspend fun search(term: String): List<String> =
        database.message_body_index_dao().match_ids(body_match_expression(term)!!, 100).sorted()

    @Before
    fun setup() {
        context.deleteDatabase(db_name)
        database = open_encrypted()
    }

    @After
    fun teardown() {
        database.close()
        context.deleteDatabase(db_name)
    }

    @Test
    fun finds_words_deep_in_the_body_by_prefix_and_without_accents() = runBlocking {
        val filler = "lorem ipsum ".repeat(1_500)
        database.message_body_index_dao().replace_all(
            listOf(
                entry("deep", "$filler Réunion quetzalcoatl"),
                entry("other", "nothing to see"),
            ),
        )

        assertEquals(listOf("deep"), search("quetz"))
        assertEquals(listOf("deep"), search("reunion"))
        assertEquals(listOf("deep"), search("RÉUNION quetzal"))
        assertEquals(listOf("other"), search("nothing"))
        assertEquals(emptyList<String>(), search("quetzalx"))
    }

    @Test
    fun reindexing_a_message_replaces_its_old_text() = runBlocking {
        val dao = database.message_body_index_dao()
        dao.replace_all(listOf(entry("m1", "first orchid")))
        dao.replace_all(listOf(entry("m1", "second tulip")))

        assertEquals(emptyList<String>(), search("orchid"))
        assertEquals(listOf("m1"), search("tulip"))
        assertEquals(1, dao.count())
    }

    @Test
    fun deleted_mail_leaves_the_index() = runBlocking {
        val dao = database.message_body_index_dao()
        dao.replace_all(listOf(entry("keep", "orchid"), entry("gone", "orchid")))

        dao.delete_ids(listOf("gone"))

        assertEquals(listOf("keep"), search("orchid"))
    }

    @Test
    fun sign_out_wipe_clears_every_indexed_body() = runBlocking {
        val dao = database.message_body_index_dao()
        dao.replace_all(listOf(entry("a", "orchid"), entry("b", "tulip")))

        dao.clear_all()

        assertEquals(0, dao.count())
        assertEquals(emptyList<String>(), search("orchid"))
    }

    @Test
    fun trims_the_oldest_rows_past_the_cap() = runBlocking {
        val dao = database.message_body_index_dao()
        dao.replace_all((1..5).map { entry("m$it", "orchid", received_at = it.toLong()) }, keep = 3)

        assertEquals(listOf("m3", "m4", "m5"), search("orchid"))
    }

    @Test
    fun lists_cached_mail_that_still_needs_its_body_indexed() = runBlocking {
        database.decrypted_mail_dao().insert_all(listOf(cached("a"), cached("b"), cached("c")))
        val dao = database.message_body_index_dao()
        dao.replace_all(listOf(entry("a", "orchid")))

        assertEquals(listOf("b", "c"), dao.unindexed_rows(emptyList(), 10).map { it.id }.sorted())
        assertEquals(listOf("c"), dao.unindexed_rows(listOf("b"), 10).map { it.id })
        assertEquals(1, dao.indexed_cached_count())
        assertEquals(3, dao.cached_mail_count())
    }

    @Test
    fun indexed_body_text_is_never_written_to_disk_in_plain_text() = runBlocking {
        val marker = "zanzibarquetzalmarker"
        database.message_body_index_dao().replace_all(listOf(entry("secret", "the code is $marker")))
        assertEquals(listOf("secret"), search(marker))
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        database.close()

        val files = context.getDatabasePath(db_name).parentFile!!
            .listFiles { file -> file.name.startsWith(db_name) }!!
        assertTrue(files.isNotEmpty())
        val needle = marker.toByteArray()
        for (file in files) {
            val bytes = file.readBytes()
            val found = (0..bytes.size - needle.size).any { start ->
                needle.indices.all { bytes[start + it] == needle[it] }
            }
            assertFalse("plain text found in ${file.name}", found)
        }

        database = open_encrypted()
        assertEquals(listOf("secret"), search(marker))
    }
}
