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

package org.astermail.android.api.preferences

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

fun merge_decrypted_preferences(
    json: Json,
    json_str: String,
    previous: UserPreferences?,
): UserPreferences {
    val incoming = json.parseToJsonElement(json_str).jsonObject

    if (previous == null) {
        return json.decodeFromJsonElement(UserPreferences.serializer(), incoming)
    }

    val base = json.encodeToJsonElement(UserPreferences.serializer(), previous).jsonObject
    val merged = buildJsonObject {
        for ((k, v) in base) put(k, v)
        for ((k, v) in incoming) put(k, v)
    }

    return json.decodeFromJsonElement(UserPreferences.serializer(), merged)
}
