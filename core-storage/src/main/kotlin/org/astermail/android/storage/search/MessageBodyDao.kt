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
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

const val message_body_cache_limit = 400

@Dao
interface MessageBodyDao {

    @Query("SELECT * FROM message_body_cache WHERE id = :id")
    suspend fun get(id: String): MessageBodyEntity?

    @Query("SELECT * FROM message_body_cache WHERE id IN (:ids)")
    suspend fun get_many(ids: List<String>): List<MessageBodyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert_all(rows: List<MessageBodyEntity>)

    @Query("UPDATE message_body_cache SET built_key = :built_key, built_html = :built_html WHERE id = :id")
    suspend fun set_built(id: String, built_key: Long, built_html: String)

    @Query("DELETE FROM message_body_cache WHERE id IN (:ids)")
    suspend fun delete_by_ids(ids: List<String>)

    @Query("DELETE FROM message_body_cache")
    suspend fun clear_all()

    @Query("SELECT COUNT(*) FROM message_body_cache")
    suspend fun count(): Int

    @Query(
        "DELETE FROM message_body_cache WHERE id NOT IN (" +
            "SELECT id FROM message_body_cache ORDER BY cached_at DESC LIMIT :keep)",
    )
    suspend fun trim_to(keep: Int)
}
