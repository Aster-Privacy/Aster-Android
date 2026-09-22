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

const val thread_snapshot_cache_limit = 80

@Dao
interface ThreadSnapshotDao {

    @Query("SELECT * FROM thread_snapshot_cache WHERE thread_token = :thread_token")
    suspend fun get(thread_token: String): ThreadSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ThreadSnapshotEntity)

    @Query("DELETE FROM thread_snapshot_cache WHERE thread_token = :thread_token")
    suspend fun delete(thread_token: String)

    @Query("DELETE FROM thread_snapshot_cache")
    suspend fun clear_all()

    @Query(
        "DELETE FROM thread_snapshot_cache WHERE thread_token NOT IN (" +
            "SELECT thread_token FROM thread_snapshot_cache ORDER BY cached_at DESC LIMIT :keep)",
    )
    suspend fun trim_to(keep: Int)
}
