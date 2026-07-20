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

package org.astermail.android.storage.outbox

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingSendDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: PendingSendEntity)

    @Query("SELECT * FROM pending_send_queue WHERE id = :id")
    suspend fun get_by_id(id: String): PendingSendEntity?

    @Query("SELECT * FROM pending_send_queue")
    suspend fun get_all(): List<PendingSendEntity>

    @Query("UPDATE pending_send_queue SET draft_id = :draft_id WHERE id = :id")
    suspend fun update_draft_id(id: String, draft_id: String?)

    @Query("UPDATE pending_send_queue SET status = 'sending', sending_started_at_ms = :now WHERE id = :id AND status = 'pending'")
    suspend fun mark_sending(id: String, now: Long): Int

    @Query("UPDATE pending_send_queue SET sending_started_at_ms = :now WHERE id = :id AND status = 'sending' AND sending_started_at_ms < :stale_before")
    suspend fun claim_stale_sending(id: String, now: Long, stale_before: Long): Int

    @Query("UPDATE pending_send_queue SET status = 'pending' WHERE id = :id")
    suspend fun mark_pending(id: String)

    @Query("DELETE FROM pending_send_queue WHERE id = :id")
    suspend fun delete_by_id(id: String)

    @Query("DELETE FROM pending_send_queue")
    suspend fun clear_all()
}
