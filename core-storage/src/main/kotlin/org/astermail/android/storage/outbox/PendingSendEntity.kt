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

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_send_queue")
data class PendingSendEntity(
    @PrimaryKey val id: String,
    val to_json: String,
    val cc_json: String,
    val bcc_json: String,
    val subject: String,
    val body_html: String,
    val sender_email: String?,
    val sender_display_name: String?,
    val thread_token: String?,
    val expires_at: String?,
    val expiry_password: String?,
    val attachments_json: String,
    val sender_alias_hash: String?,
    val suppress_branding: Boolean?,
    val draft_id: String?,
    val fire_at_ms: Long,
    val status: String,
    val created_at_ms: Long,
)
