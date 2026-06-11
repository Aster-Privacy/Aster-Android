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

package org.astermail.android.api.mail

import kotlinx.serialization.Serializable

@Serializable
data class AttachmentMetaItem(
    val id: String,
    val mail_item_id: String,
    val encrypted_meta: String,
    val meta_nonce: String? = null,
    val size_bytes: Long,
    val seq_num: Int = 0,
)

@Serializable
data class AttachmentResponse(
    val id: String,
    val mail_item_id: String,
    val encrypted_data: String,
    val data_nonce: String,
    val encrypted_meta: String,
    val meta_nonce: String? = null,
    val size_bytes: Long,
    val seq_num: Int = 0,
    val created_at: String? = null,
)

@Serializable
data class AttachmentListResponse(
    val attachments: List<AttachmentResponse> = emptyList(),
)

@Serializable
data class BatchAttachmentMetaRequest(
    val mail_ids: List<String>,
)

@Serializable
data class BatchAttachmentMetaResponse(
    val items: Map<String, List<AttachmentMetaItem>> = emptyMap(),
)
