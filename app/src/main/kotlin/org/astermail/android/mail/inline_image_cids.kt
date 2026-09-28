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

package org.astermail.android.mail

import org.astermail.android.api.send.ExternalAttachmentPayload

internal fun new_inline_image_content_id(): String =
    "img_${java.util.UUID.randomUUID()}@astermail.org"

internal fun is_inline_image_payload(payload: ExternalAttachmentPayload): Boolean =
    !payload.content_id.isNullOrBlank()

internal fun without_inline_images(
    attachments: List<ExternalAttachmentPayload>,
): List<ExternalAttachmentPayload> = attachments.filterNot { is_inline_image_payload(it) }

internal fun with_cid_image_references(
    html: String,
    attachments: List<ExternalAttachmentPayload>,
): String {
    if (!html.contains("data:")) return html
    var result = html
    attachments.forEach { att ->
        val cid = att.content_id?.takeIf { it.isNotBlank() } ?: return@forEach
        val data_uri = "data:${att.content_type};base64,${att.data}"
        result = result.replace(data_uri, "cid:$cid")
    }
    return result
}
