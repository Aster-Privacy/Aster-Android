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

package org.astermail.android.storage.search

import androidx.room.Database
import androidx.room.RoomDatabase
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.outbox.PendingSendEntity

@Database(
    entities = [DecryptedMailEntity::class, PendingSendEntity::class],
    version = 10,
    exportSchema = false,
)
abstract class AsterDatabase : RoomDatabase() {
    abstract fun decrypted_mail_dao(): DecryptedMailDao
    abstract fun pending_send_dao(): PendingSendDao

    companion object {
        val migration_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decrypted_mail_cache ADD COLUMN received_on TEXT")
            }
        }

        val migration_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pending_send_queue ADD COLUMN account_id TEXT")
            }
        }

        val migration_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decrypted_mail_cache ADD COLUMN display_sender_name TEXT")
                db.execSQL("ALTER TABLE decrypted_mail_cache ADD COLUMN display_sender_email TEXT")
            }
        }

        val migration_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decrypted_mail_cache ADD COLUMN to_addresses TEXT")
                db.execSQL("DELETE FROM decrypted_mail_cache")
            }
        }

        val migration_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE decrypted_mail_cache ADD COLUMN routing_token TEXT")
            }
        }

        val migration_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE decrypted_mail_cache ADD COLUMN is_external INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE decrypted_mail_cache ADD COLUMN has_recipient_key INTEGER")
                db.execSQL("DELETE FROM decrypted_mail_cache")
            }
        }
    }
}
