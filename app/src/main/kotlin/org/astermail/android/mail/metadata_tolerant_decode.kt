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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.api.mail.MailItemMetadata

internal fun decode_metadata_tolerant(json: Json, json_str: String): MailItemMetadata? {
    try {
        return json.decodeFromString(MailItemMetadata.serializer(), json_str)
    } catch (_: Throwable) {
    }
    val obj = try {
        json.parseToJsonElement(json_str) as? JsonObject
    } catch (_: Throwable) {
        null
    } ?: return null
    val defaults = MailItemMetadata()
    return MailItemMetadata(
        is_read = tolerant_boolean(obj["is_read"]) ?: defaults.is_read,
        is_starred = tolerant_boolean(obj["is_starred"]) ?: defaults.is_starred,
        is_pinned = tolerant_boolean(obj["is_pinned"]) ?: defaults.is_pinned,
        is_trashed = tolerant_boolean(obj["is_trashed"]) ?: defaults.is_trashed,
        is_archived = tolerant_boolean(obj["is_archived"]) ?: defaults.is_archived,
        is_spam = tolerant_boolean(obj["is_spam"]) ?: defaults.is_spam,
        size_bytes = tolerant_long(obj["size_bytes"]) ?: defaults.size_bytes,
        has_attachments = tolerant_boolean(obj["has_attachments"]) ?: defaults.has_attachments,
        attachment_count = tolerant_long(obj["attachment_count"])
            ?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: defaults.attachment_count,
        scheduled_at = tolerant_string(obj["scheduled_at"]),
        send_status = tolerant_string(obj["send_status"]),
        send_error = tolerant_string(obj["send_error"]),
        snoozed_until = tolerant_string(obj["snoozed_until"]),
        trashed_at = tolerant_string(obj["trashed_at"]),
        message_ts = tolerant_string(obj["message_ts"]),
        created_at = tolerant_string(obj["created_at"]),
        updated_at = tolerant_string(obj["updated_at"]),
        item_type = tolerant_string(obj["item_type"]),
        category = tolerant_string(obj["category"])?.takeIf { it.isNotBlank() },
        category_pinned = tolerant_boolean(obj["category_pinned"]) ?: defaults.category_pinned,
    )
}

private fun tolerant_primitive(element: JsonElement?): JsonPrimitive? =
    (element as? JsonPrimitive)?.takeIf { it !is JsonNull }

private fun tolerant_boolean(element: JsonElement?): Boolean? {
    val primitive = tolerant_primitive(element) ?: return null
    val content = primitive.content.trim().lowercase()
    return when (content) {
        "true", "1" -> true
        "false", "0", "" -> false
        else -> content.toDoubleOrNull()?.let { it != 0.0 }
    }
}

private fun tolerant_long(element: JsonElement?): Long? {
    val primitive = tolerant_primitive(element) ?: return null
    val content = primitive.content.trim()
    return content.toLongOrNull()
        ?: content.toDoubleOrNull()?.takeIf { it.isFinite() }?.toLong()
}

private fun tolerant_string(element: JsonElement?): String? {
    val primitive = tolerant_primitive(element) ?: return null
    return primitive.content
}
