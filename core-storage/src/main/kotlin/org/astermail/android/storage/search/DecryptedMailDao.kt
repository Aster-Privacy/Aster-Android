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

@Dao
interface DecryptedMailDao {

    @Query("SELECT * FROM decrypted_mail_cache ORDER BY timestamp DESC")
    suspend fun get_all(): List<DecryptedMailEntity>

    @Query("SELECT id FROM decrypted_mail_cache")
    suspend fun get_all_ids(): List<String>

    @Query("SELECT COUNT(*) FROM decrypted_mail_cache")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert_all(items: List<DecryptedMailEntity>)

    @Query("DELETE FROM decrypted_mail_cache WHERE id IN (:ids)")
    suspend fun delete_by_ids(ids: List<String>)

    @Query("DELETE FROM decrypted_mail_cache")
    suspend fun clear_all()

    @Query("UPDATE decrypted_mail_cache SET is_read = :is_read WHERE id = :id")
    suspend fun update_read(id: String, is_read: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_starred = :is_starred WHERE id = :id")
    suspend fun update_starred(id: String, is_starred: Boolean)

    @Query("UPDATE decrypted_mail_cache SET is_trashed = 1 WHERE id IN (:ids)")
    suspend fun mark_trashed(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_archived = 1 WHERE id IN (:ids)")
    suspend fun mark_archived(ids: List<String>)

    @Query("UPDATE decrypted_mail_cache SET is_spam = 1 WHERE id IN (:ids)")
    suspend fun mark_spam(ids: List<String>)

    @Query("DELETE FROM decrypted_mail_cache WHERE id IN (:ids)")
    suspend fun remove_items(ids: List<String>)
}
