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

@Entity(tableName = "folder_row_cache", primaryKeys = ["folder", "id"])
data class FolderRowEntity(
    val folder: String,
    val id: String,
    val position: Int,
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
    val attachment_count: Int,
    val is_trashed: Boolean,
    val is_archived: Boolean,
    val is_spam: Boolean,
    val is_pinned: Boolean,
    val labels: String,
    val tag_tokens: String?,
    val category: String,
    val received_on: String?,
    val display_sender_name: String?,
    val display_sender_email: String?,
    val to_addresses: String?,
    val routing_token: String?,
    val item_type: String?,
    val is_external: Boolean,
    val system_origin: Boolean,
    val has_recipient_key: Boolean?,
    val cached_at: Long,
)
