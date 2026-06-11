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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "decrypted_mail_cache")
data class DecryptedMailEntity(
    @PrimaryKey val id: String,
    val thread_token: String?,
    val thread_message_count: Int,
    val sender_name: String,
    val sender_email: String,
    val subject: String,
    val preview: String,
    val timestamp: String,
    val is_read: Boolean,
    val is_starred: Boolean,
    val is_encrypted: Boolean,
    val has_attachments: Boolean,
    val is_trashed: Boolean,
    val is_archived: Boolean,
    val is_spam: Boolean,
    val labels: String,
    val indexed_at: Long,
)
