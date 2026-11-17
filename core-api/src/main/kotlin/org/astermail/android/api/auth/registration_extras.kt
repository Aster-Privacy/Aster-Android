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

package org.astermail.android.api.auth

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

class RegistrationExtras(
    val headers: Map<String, String> = emptyMap(),
    val fields: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        val none = RegistrationExtras()
    }
}

fun registration_body(json: Json, request: RegisterRequest, extras: RegistrationExtras): JsonObject {
    val base = json.encodeToJsonElement(RegisterRequest.serializer(), request).jsonObject
    if (extras.fields.isEmpty()) return base
    val merged = LinkedHashMap<String, JsonElement>(base)
    extras.fields.forEach { (key, value) -> if (key !in base) merged[key] = value }
    return JsonObject(merged)
}
