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

package org.astermail.android.storage.contacts

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ContactCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ContactCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert_all(rows: List<ContactCacheEntity>)

    @Query("SELECT * FROM contact_cache WHERE account_id = :account_id")
    suspend fun get_for_account(account_id: String): List<ContactCacheEntity>

    @Query("SELECT * FROM contact_cache WHERE account_id = :account_id AND id = :id")
    suspend fun get(account_id: String, id: String): ContactCacheEntity?

    @Query("SELECT COUNT(*) FROM contact_cache WHERE account_id = :account_id")
    suspend fun count_for_account(account_id: String): Int

    @Query("DELETE FROM contact_cache WHERE account_id = :account_id AND id = :id")
    suspend fun delete(account_id: String, id: String)

    @Query("DELETE FROM contact_cache WHERE account_id = :account_id")
    suspend fun clear_for_account(account_id: String)

    @Query("DELETE FROM contact_cache")
    suspend fun clear_all()

    @Transaction
    suspend fun replace_for_account(account_id: String, rows: List<ContactCacheEntity>) {
        clear_for_account(account_id)
        upsert_all(rows.filter { it.account_id == account_id })
    }
}

@Dao
interface ContactGroupCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert_all(rows: List<ContactGroupCacheEntity>)

    @Query("SELECT * FROM contact_group_cache WHERE account_id = :account_id ORDER BY position ASC")
    suspend fun get_for_account(account_id: String): List<ContactGroupCacheEntity>

    @Query("DELETE FROM contact_group_cache WHERE account_id = :account_id")
    suspend fun clear_for_account(account_id: String)

    @Query("DELETE FROM contact_group_cache")
    suspend fun clear_all()

    @Transaction
    suspend fun replace_for_account(account_id: String, rows: List<ContactGroupCacheEntity>) {
        clear_for_account(account_id)
        upsert_all(rows.filter { it.account_id == account_id })
    }
}
