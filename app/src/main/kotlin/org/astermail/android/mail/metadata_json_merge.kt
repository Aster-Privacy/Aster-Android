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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.astermail.android.api.mail.MailItemMetadata

internal fun merge_metadata_json(json: Json, original: String?, updated: MailItemMetadata): String {
    val overlay = json.encodeToJsonElement(MailItemMetadata.serializer(), updated).jsonObject
    val base = original?.let {
        try {
            json.parseToJsonElement(it) as? JsonObject
        } catch (_: Throwable) {
            null
        }
    } ?: JsonObject(emptyMap())
    return JsonObject(base + overlay).toString()
}
