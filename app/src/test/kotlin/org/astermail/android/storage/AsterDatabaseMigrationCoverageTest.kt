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

import org.astermail.android.storage.outbox.PendingSendEntity
import org.astermail.android.storage.search.AsterDatabase
import org.astermail.android.storage.search.DecryptedMailEntity
import org.astermail.android.storage.search.FolderRowEntity
import org.astermail.android.storage.search.MessageBodyEntity
import org.astermail.android.storage.search.ThreadSnapshotEntity
import org.astermail.android.storage.search.aster_database_version
import org.astermail.android.storage.search.create_folder_row_cache
import org.astermail.android.storage.search.create_message_body_cache
import org.astermail.android.storage.search.create_thread_snapshot_cache
import org.astermail.android.storage.search.migration_columns
import org.astermail.android.storage.search.migration_statements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AsterDatabaseMigrationCoverageTest {

    private val decrypted_mail_table = "decrypted_mail_cache"
    private val pending_send_table = "pending_send_queue"
    private val folder_row_table = "folder_row_cache"
    private val message_body_table = "message_body_cache"
    private val thread_snapshot_table = "thread_snapshot_cache"

    private val decrypted_mail_baseline = listOf(
        "id",
        "thread_token",
        "thread_message_count",
        "sender_name",
        "sender_email",
        "subject",
        "preview",
        "timestamp",
        "is_read",
        "is_starred",
        "is_encrypted",
        "has_attachments",
        "is_trashed",
        "is_archived",
        "is_spam",
        "labels",
        "indexed_at",
    )

    private fun entity_columns(type: Class<*>): List<String> =
        type.declaredFields.filterNot { it.isSynthetic }.map { it.name }

    private fun added_columns(table: String): List<String> =
        migration_columns.entries.sortedBy { it.key }
            .flatMap { entry -> entry.value.filter { it.table == table }.map { it.name } }

    private fun created_columns(statement: String, table: String = pending_send_table): List<String> =
        Regex("`([a-z_]+)`").findAll(statement)
            .map { it.groupValues[1] }
            .filterNot { it == table }
            .distinct()
            .toList()

    @Test
    fun every_shipped_database_version_has_a_migration_path_forward() {
        val steps = AsterDatabase.all_migrations.map { it.startVersion to it.endVersion }

        assertEquals((1 until aster_database_version).map { it to it + 1 }, steps)
    }

    @Test
    fun every_decrypted_mail_column_is_created_by_a_migration() {
        val expected = entity_columns(DecryptedMailEntity::class.java).sorted()
        val actual = (decrypted_mail_baseline + added_columns(decrypted_mail_table)).sorted()

        assertEquals(expected, actual)
    }

    @Test
    fun every_pending_send_column_is_created_by_a_migration() {
        val create = migration_statements.getValue(4).first { it.startsWith("CREATE") }
        val expected = entity_columns(PendingSendEntity::class.java).sorted()
        val actual = (created_columns(create) + added_columns(pending_send_table)).sorted()

        assertEquals(expected, actual)
    }

    @Test
    fun the_outbox_claim_column_is_added_when_the_outbox_gains_it() {
        val added = migration_columns.getValue(5)

        assertTrue(added.any { it.table == pending_send_table && it.name == "sending_started_at_ms" })
    }

    @Test
    fun every_folder_row_column_is_created_by_the_folder_cache_migration() {
        val expected = entity_columns(FolderRowEntity::class.java).sorted()
        val actual = created_columns(create_folder_row_cache, folder_row_table).sorted()

        assertEquals(expected, actual)
    }

    @Test
    fun the_folder_cache_table_is_created_by_its_migration_step() {
        val statements = migration_statements.getValue(15)

        assertTrue(statements.contains(create_folder_row_cache))
        assertTrue(create_folder_row_cache.contains("PRIMARY KEY(`folder`, `id`)"))
    }

    @Test
    fun the_message_body_cache_table_is_created_by_its_migration_step() {
        val statements = migration_statements.getValue(16)

        assertTrue(statements.contains(create_message_body_cache))
        assertTrue(create_message_body_cache.contains("PRIMARY KEY(`id`)"))
    }

    @Test
    fun every_message_body_column_is_created_by_the_message_body_migration() {
        val expected = entity_columns(MessageBodyEntity::class.java).sorted()
        val actual = created_columns(create_message_body_cache, message_body_table).sorted()

        assertEquals(expected, actual)
    }

    @Test
    fun the_thread_snapshot_table_is_created_by_the_latest_migration_step() {
        val statements = migration_statements.getValue(aster_database_version)

        assertTrue(statements.contains(create_thread_snapshot_cache))
        assertTrue(create_thread_snapshot_cache.contains("PRIMARY KEY(`thread_token`)"))
    }

    @Test
    fun every_thread_snapshot_column_is_created_by_the_thread_snapshot_migration() {
        val expected = entity_columns(ThreadSnapshotEntity::class.java).sorted()
        val actual = created_columns(create_thread_snapshot_cache, thread_snapshot_table).sorted()

        assertEquals(expected, actual)
    }

    @Test
    fun no_migration_step_is_declared_twice() {
        val steps = AsterDatabase.all_migrations.map { it.startVersion to it.endVersion }

        assertEquals(steps.size, steps.toSet().size)
    }
}
