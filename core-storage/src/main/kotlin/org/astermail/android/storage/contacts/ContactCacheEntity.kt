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

import androidx.room.Entity

@Entity(tableName = "contact_cache", primaryKeys = ["account_id", "id"])
data class ContactCacheEntity(
    val account_id: String,
    val id: String,
    val contact_token: String?,
    val encrypted_data: String,
    val data_nonce: String,
    val integrity_hash: String?,
    val data_version: Int?,
    val created_at: String?,
    val updated_at: String?,
    val change_seq: Long?,
    val revision: Long?,
)

@Entity(tableName = "contact_group_cache", primaryKeys = ["account_id", "id"])
data class ContactGroupCacheEntity(
    val account_id: String,
    val id: String,
    val encrypted_name: String,
    val name_nonce: String,
    val contact_count: Int,
    val created_at: String?,
    val position: Int,
)
