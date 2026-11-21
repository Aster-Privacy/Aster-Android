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

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

const val message_body_index_limit = 20_000

@Entity(
    tableName = "message_body_index",
    indices = [
        Index(value = ["message_id"], unique = true),
        Index(value = ["received_at"]),
    ],
)
data class MessageBodyIndexEntity(
    @PrimaryKey(autoGenerate = true) val doc_id: Long = 0,
    val message_id: String,
    val body: String,
    val received_at: Long,
    val indexed_at: Long,
)

@Fts4(
    contentEntity = MessageBodyIndexEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    prefix = [2, 3],
)
@Entity(tableName = "message_body_fts")
data class MessageBodyFtsEntity(
    val body: String,
)

@Dao
abstract class MessageBodyIndexDao {

    @Insert
    protected abstract suspend fun insert_rows(rows: List<MessageBodyIndexEntity>)

    @Query("DELETE FROM message_body_index WHERE message_id IN (:ids)")
    abstract suspend fun delete_ids(ids: List<String>)

    @Query("SELECT message_id FROM message_body_index WHERE message_id IN (:ids)")
    abstract suspend fun indexed_among(ids: List<String>): List<String>

    @Query("SELECT COUNT(*) FROM message_body_index")
    abstract suspend fun count(): Int

    @Query("DELETE FROM message_body_index")
    abstract suspend fun clear_all()

    @Query(
        "DELETE FROM message_body_index WHERE doc_id NOT IN (" +
            "SELECT doc_id FROM message_body_index ORDER BY received_at DESC LIMIT :keep)",
    )
    abstract suspend fun trim_to(keep: Int)

    @Query(
        "SELECT message_body_index.message_id FROM message_body_index " +
            "JOIN message_body_fts ON message_body_fts.rowid = message_body_index.doc_id " +
            "WHERE message_body_fts MATCH :match LIMIT :limit",
    )
    abstract suspend fun match_ids(match: String, limit: Int): List<String>

    @Query(
        "SELECT d.id, d.thread_token FROM decrypted_mail_cache d " +
            "LEFT JOIN message_body_index b ON b.message_id = d.id " +
            "WHERE b.message_id IS NULL AND d.thread_token IS NOT NULL AND d.thread_token != '' " +
            "AND d.id NOT IN (:skip) ORDER BY d.timestamp DESC LIMIT :limit",
    )
    abstract suspend fun unindexed_rows(skip: List<String>, limit: Int): List<InboxWindowRow>

    @Query(
        "SELECT COUNT(*) FROM decrypted_mail_cache " +
            "WHERE thread_token IS NOT NULL AND thread_token != ''",
    )
    abstract suspend fun cached_mail_count(): Int

    @Query(
        "SELECT COUNT(*) FROM message_body_index b " +
            "JOIN decrypted_mail_cache d ON d.id = b.message_id " +
            "WHERE d.thread_token IS NOT NULL AND d.thread_token != ''",
    )
    abstract suspend fun indexed_cached_count(): Int

    @Transaction
    open suspend fun replace_all(rows: List<MessageBodyIndexEntity>, keep: Int = message_body_index_limit) {
        if (rows.isEmpty()) return
        val unique = rows.associateBy { it.message_id }.values.toList()
        unique.chunked(500).forEach { batch -> delete_ids(batch.map { it.message_id }) }
        insert_rows(unique)
        if (count() > keep) trim_to(keep)
    }
}
