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
import androidx.room.Transaction

@Dao
interface FolderRowDao {

    @Query("SELECT * FROM folder_row_cache WHERE folder = :folder ORDER BY position ASC LIMIT :limit")
    suspend fun rows_for_folder(folder: String, limit: Int): List<FolderRowEntity>

    @Query("SELECT COUNT(*) FROM folder_row_cache WHERE folder = :folder")
    suspend fun count_for_folder(folder: String): Int

    @Query("SELECT DISTINCT folder FROM folder_row_cache")
    suspend fun cached_folders(): List<String>

    @Query("SELECT id FROM folder_row_cache WHERE folder = :folder")
    suspend fun ids_for_folder(folder: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert_all(rows: List<FolderRowEntity>)

    @Query("DELETE FROM folder_row_cache WHERE folder = :folder")
    suspend fun clear_folder(folder: String)

    @Query("DELETE FROM folder_row_cache WHERE folder = :folder AND id IN (:ids)")
    suspend fun remove_from_folder(folder: String, ids: List<String>)

    @Query("DELETE FROM folder_row_cache WHERE id IN (:ids)")
    suspend fun remove_items(ids: List<String>)

    @Query("DELETE FROM folder_row_cache")
    suspend fun clear_all()

    @Transaction
    suspend fun replace_folder(folder: String, rows: List<FolderRowEntity>, stale_ids: List<String>) {
        if (rows.isEmpty()) {
            clear_folder(folder)
            return
        }
        insert_all(rows)
        if (stale_ids.isNotEmpty()) remove_from_folder(folder, stale_ids)
    }
}
