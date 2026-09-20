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

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.astermail.android.storage.search.AsterDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val PROBE_DB = "folder_row_cache_migration_probe.db"

private const val V14_MAIL_SCHEMA = """
CREATE TABLE IF NOT EXISTS decrypted_mail_cache (
    id TEXT NOT NULL PRIMARY KEY,
    subject TEXT NOT NULL,
    timestamp TEXT NOT NULL
)
"""

@RunWith(AndroidJUnit4::class)
class FolderRowCacheMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun open_a_version_14_database() {
        context.deleteDatabase(PROBE_DB)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(PROBE_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(14) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(V14_MAIL_SCHEMA)
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            old_version: Int,
                            new_version: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
    }

    @After
    fun close_the_probe() {
        helper.close()
        context.deleteDatabase(PROBE_DB)
    }

    @Test
    fun migration_14_15_creates_the_folder_cache_and_keeps_existing_mail() {
        val db = helper.writableDatabase
        db.execSQL(
            "INSERT INTO decrypted_mail_cache (id, subject, timestamp) " +
                "VALUES ('legacy-1', 'Welcome', '2026-09-01T00:00:00Z')",
        )

        AsterDatabase.migration_14_15.migrate(db)

        db.query("SELECT COUNT(*) FROM decrypted_mail_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM folder_row_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun the_migrated_folder_cache_stores_one_row_per_folder_and_id() {
        val db = helper.writableDatabase

        AsterDatabase.migration_14_15.migrate(db)

        db.execSQL(
            "INSERT INTO folder_row_cache (folder, id, position, thread_message_count, sender_name, " +
                "sender_email, subject, preview, timestamp, is_read, is_starred, is_encrypted, " +
                "has_attachments, attachment_count, is_trashed, is_archived, is_spam, is_pinned, " +
                "labels, category, is_external, system_origin, cached_at) VALUES " +
                "('inbox', 'm1', 0, 1, 'Sender', 'sender@example.com', 'Subject', 'Preview', " +
                "'2026-09-19T00:00:00Z', 0, 0, 1, 0, 0, 0, 0, 0, 0, '', 'primary', 0, 0, 1)",
        )
        db.execSQL(
            "INSERT INTO folder_row_cache (folder, id, position, thread_message_count, sender_name, " +
                "sender_email, subject, preview, timestamp, is_read, is_starred, is_encrypted, " +
                "has_attachments, attachment_count, is_trashed, is_archived, is_spam, is_pinned, " +
                "labels, category, is_external, system_origin, cached_at) VALUES " +
                "('starred', 'm1', 0, 1, 'Sender', 'sender@example.com', 'Subject', 'Preview', " +
                "'2026-09-19T00:00:00Z', 0, 1, 1, 0, 0, 0, 0, 0, 0, '', 'primary', 0, 0, 1)",
        )

        db.query("SELECT COUNT(*) FROM folder_row_cache WHERE id = 'm1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM folder_row_cache WHERE folder = 'inbox'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }

    @Test
    fun migration_14_15_is_safe_to_run_twice() {
        val db = helper.writableDatabase

        AsterDatabase.migration_14_15.migrate(db)
        AsterDatabase.migration_14_15.migrate(db)

        db.query("SELECT COUNT(*) FROM folder_row_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }
}
