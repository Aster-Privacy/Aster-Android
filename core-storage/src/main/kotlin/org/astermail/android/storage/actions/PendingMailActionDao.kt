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

package org.astermail.android.storage.actions

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PendingMailActionDao {

    @Insert
    suspend fun insert(row: PendingMailActionEntity): Long

    @Query("SELECT * FROM pending_mail_action ORDER BY id ASC")
    suspend fun get_all(): List<PendingMailActionEntity>

    @Query("SELECT * FROM pending_mail_action WHERE account_id = :account_id ORDER BY id ASC")
    suspend fun get_for_account(account_id: String): List<PendingMailActionEntity>

    @Query("UPDATE pending_mail_action SET attempts = attempts + 1 WHERE id = :id")
    suspend fun bump_attempts(id: Long)

    @Query("DELETE FROM pending_mail_action WHERE id = :id")
    suspend fun delete_by_id(id: Long)

    @Query("DELETE FROM pending_mail_action WHERE account_id = :account_id")
    suspend fun clear_for_account(account_id: String)

    @Query("DELETE FROM pending_mail_action")
    suspend fun clear_all()

    @Transaction
    suspend fun replace_rows(stale_ids: List<Long>, row: PendingMailActionEntity): Long {
        stale_ids.forEach { delete_by_id(it) }
        return insert(row)
    }
}
