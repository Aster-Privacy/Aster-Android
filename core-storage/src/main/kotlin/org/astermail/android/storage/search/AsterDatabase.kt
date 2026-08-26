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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.astermail.android.storage.outbox.PendingSendDao
import org.astermail.android.storage.outbox.PendingSendEntity

const val aster_database_version = 14

data class schema_column(val table: String, val name: String, val definition: String)

private const val decrypted_mail_table = "decrypted_mail_cache"
private const val pending_send_table = "pending_send_queue"

private const val create_pending_send_queue =
    "CREATE TABLE IF NOT EXISTS `pending_send_queue` (" +
        "`id` TEXT NOT NULL, " +
        "`to_json` TEXT NOT NULL, " +
        "`cc_json` TEXT NOT NULL, " +
        "`bcc_json` TEXT NOT NULL, " +
        "`subject` TEXT NOT NULL, " +
        "`body_html` TEXT NOT NULL, " +
        "`sender_email` TEXT, " +
        "`sender_display_name` TEXT, " +
        "`thread_token` TEXT, " +
        "`expires_at` TEXT, " +
        "`expiry_password` TEXT, " +
        "`attachments_json` TEXT NOT NULL, " +
        "`sender_alias_hash` TEXT, " +
        "`suppress_branding` INTEGER, " +
        "`draft_id` TEXT, " +
        "`fire_at_ms` INTEGER NOT NULL, " +
        "`status` TEXT NOT NULL, " +
        "`created_at_ms` INTEGER NOT NULL, " +
        "PRIMARY KEY(`id`))"

val migration_columns: Map<Int, List<schema_column>> = mapOf(
    3 to listOf(schema_column(decrypted_mail_table, "category", "TEXT NOT NULL DEFAULT 'primary'")),
    5 to listOf(
        schema_column(decrypted_mail_table, "received_on", "TEXT"),
        schema_column(pending_send_table, "sending_started_at_ms", "INTEGER NOT NULL DEFAULT 0"),
    ),
    6 to listOf(schema_column(pending_send_table, "account_id", "TEXT")),
    7 to listOf(
        schema_column(decrypted_mail_table, "display_sender_name", "TEXT"),
        schema_column(decrypted_mail_table, "display_sender_email", "TEXT"),
    ),
    8 to listOf(schema_column(decrypted_mail_table, "to_addresses", "TEXT")),
    9 to listOf(schema_column(decrypted_mail_table, "routing_token", "TEXT")),
    10 to listOf(
        schema_column(decrypted_mail_table, "is_external", "INTEGER NOT NULL DEFAULT 0"),
        schema_column(decrypted_mail_table, "has_recipient_key", "INTEGER"),
    ),
    11 to listOf(
        schema_column(pending_send_table, "allow_non_post_quantum", "INTEGER NOT NULL DEFAULT 0"),
    ),
    12 to listOf(schema_column(decrypted_mail_table, "is_pinned", "INTEGER NOT NULL DEFAULT 0")),
    13 to listOf(schema_column(decrypted_mail_table, "tag_tokens", "TEXT")),
    14 to listOf(schema_column(decrypted_mail_table, "system_origin", "INTEGER NOT NULL DEFAULT 0")),
)

val migration_statements: Map<Int, List<String>> = mapOf(
    4 to listOf(create_pending_send_queue),
    8 to listOf("DELETE FROM decrypted_mail_cache"),
    10 to listOf("DELETE FROM decrypted_mail_cache"),
)

private fun has_table(db: SupportSQLiteDatabase, table: String): Boolean =
    db.query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf<Any>(table)).use {
        it.moveToFirst()
    }

private fun has_column(db: SupportSQLiteDatabase, table: String, column: String): Boolean =
    db.query("PRAGMA table_info(`$table`)").use { cursor ->
        val name_index = cursor.getColumnIndex("name")
        if (name_index < 0) return false
        while (cursor.moveToNext()) {
            if (cursor.getString(name_index) == column) return true
        }
        false
    }

private fun add_column_if_absent(db: SupportSQLiteDatabase, column: schema_column) {
    if (!has_table(db, column.table)) return
    if (has_column(db, column.table, column.name)) return
    db.execSQL("ALTER TABLE `${column.table}` ADD COLUMN `${column.name}` ${column.definition}")
}

private fun step_migration(to_version: Int): Migration = object : Migration(to_version - 1, to_version) {
    override fun migrate(db: SupportSQLiteDatabase) {
        for (statement in migration_statements[to_version].orEmpty()) {
            if (statement.startsWith("CREATE")) db.execSQL(statement)
        }
        for (column in migration_columns[to_version].orEmpty()) {
            add_column_if_absent(db, column)
        }
        for (statement in migration_statements[to_version].orEmpty()) {
            if (!statement.startsWith("CREATE")) db.execSQL(statement)
        }
    }
}

@Database(
    entities = [DecryptedMailEntity::class, PendingSendEntity::class],
    version = aster_database_version,
    exportSchema = false,
)
abstract class AsterDatabase : RoomDatabase() {
    abstract fun decrypted_mail_dao(): DecryptedMailDao
    abstract fun pending_send_dao(): PendingSendDao

    companion object {
        val migration_1_2 = step_migration(2)
        val migration_2_3 = step_migration(3)
        val migration_3_4 = step_migration(4)
        val migration_4_5 = step_migration(5)
        val migration_5_6 = step_migration(6)
        val migration_6_7 = step_migration(7)
        val migration_7_8 = step_migration(8)
        val migration_8_9 = step_migration(9)
        val migration_9_10 = step_migration(10)
        val migration_10_11 = step_migration(11)
        val migration_11_12 = step_migration(12)
        val migration_12_13 = step_migration(13)
        val migration_13_14 = step_migration(14)

        val all_migrations: Array<Migration> = arrayOf(
            migration_1_2,
            migration_2_3,
            migration_3_4,
            migration_4_5,
            migration_5_6,
            migration_6_7,
            migration_7_8,
            migration_8_9,
            migration_9_10,
            migration_10_11,
            migration_11_12,
            migration_12_13,
            migration_13_14,
        )
    }
}
