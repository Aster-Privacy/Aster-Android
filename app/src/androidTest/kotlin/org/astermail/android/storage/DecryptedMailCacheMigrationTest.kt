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

private const val PROBE_DB = "decrypted_mail_cache_migration_probe.db"

private const val V8_SCHEMA = """
CREATE TABLE IF NOT EXISTS decrypted_mail_cache (
    id TEXT NOT NULL PRIMARY KEY,
    thread_token TEXT,
    thread_message_count INTEGER NOT NULL,
    sender_name TEXT NOT NULL,
    sender_email TEXT NOT NULL,
    subject TEXT NOT NULL,
    preview TEXT NOT NULL,
    timestamp TEXT NOT NULL,
    is_read INTEGER NOT NULL,
    is_starred INTEGER NOT NULL,
    is_encrypted INTEGER NOT NULL,
    has_attachments INTEGER NOT NULL,
    is_trashed INTEGER NOT NULL,
    is_archived INTEGER NOT NULL,
    is_spam INTEGER NOT NULL,
    labels TEXT NOT NULL,
    indexed_at INTEGER NOT NULL,
    category TEXT NOT NULL,
    received_on TEXT,
    display_sender_name TEXT,
    display_sender_email TEXT,
    to_addresses TEXT
)
"""

@RunWith(AndroidJUnit4::class)
class DecryptedMailCacheMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun open_a_version_8_cache() {
        context.deleteDatabase(PROBE_DB)
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(PROBE_DB)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(8) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(V8_SCHEMA)
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
    fun migration_8_9_adds_routing_token_and_keeps_existing_rows() {
        val db = helper.writableDatabase
        db.execSQL(
            """
            INSERT INTO decrypted_mail_cache (
                id, thread_token, thread_message_count, sender_name, sender_email,
                subject, preview, timestamp, is_read, is_starred, is_encrypted,
                has_attachments, is_trashed, is_archived, is_spam, labels,
                indexed_at, category, received_on, display_sender_name,
                display_sender_email, to_addresses
            ) VALUES (
                'legacy-1', 't1', 1, 'Aster', 'noreply@astermail.org',
                'Welcome', 'hi', '2026-08-01T00:00:00Z', 1, 0, 1,
                0, 0, 0, 0, '', 1, 'primary', 'shopping@aster.cx', NULL,
                NULL, 'shopping@aster.cx'
            )
            """.trimIndent(),
        )

        AsterDatabase.migration_8_9.migrate(db)

        db.query("SELECT id, received_on, routing_token FROM decrypted_mail_cache").use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-1", cursor.getString(0))
            assertEquals("shopping@aster.cx", cursor.getString(1))
            assertTrue(cursor.isNull(2))
        }

        db.execSQL(
            "UPDATE decrypted_mail_cache SET routing_token = 'hash-shopping' WHERE id = 'legacy-1'",
        )
        db.query("SELECT routing_token FROM decrypted_mail_cache").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("hash-shopping", cursor.getString(0))
        }
    }

    @Test
    fun migration_8_9_leaves_an_empty_cache_usable() {
        val db = helper.writableDatabase

        AsterDatabase.migration_8_9.migrate(db)

        db.query("SELECT routing_token FROM decrypted_mail_cache").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }
}
