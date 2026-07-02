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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

private fun json_kind_matches(incoming: JsonElement, base: JsonElement): Boolean {
    if (incoming !is JsonPrimitive || base !is JsonPrimitive) {
        return incoming::class == base::class
    }
    return when {
        base.booleanOrNull != null -> incoming.booleanOrNull != null
        base.isString -> incoming.isString
        base.longOrNull != null || base.doubleOrNull != null ->
            !incoming.isString && (incoming.longOrNull != null || incoming.doubleOrNull != null)
        else -> true
    }
}

fun merge_decrypted_preferences(
    json: Json,
    json_str: String,
    previous: UserPreferences?,
): UserPreferences {
    val incoming = json.parseToJsonElement(json_str).jsonObject
    val base = json.encodeToJsonElement(
        UserPreferences.serializer(),
        previous ?: UserPreferences(),
    ).jsonObject
    val merged = buildJsonObject {
        for ((k, base_value) in base) {
            val in_value = incoming[k]
            put(k, if (in_value != null && json_kind_matches(in_value, base_value)) in_value else base_value)
        }
    }
    return json.decodeFromJsonElement(UserPreferences.serializer(), merged)
}

fun rebase_preferences_changes(
    json: Json,
    base: UserPreferences,
    baseline: UserPreferences?,
    updated: UserPreferences,
): UserPreferences {
    val base_obj = json.encodeToJsonElement(UserPreferences.serializer(), base).jsonObject
    val baseline_obj = json.encodeToJsonElement(
        UserPreferences.serializer(),
        baseline ?: UserPreferences(),
    ).jsonObject
    val updated_obj = json.encodeToJsonElement(UserPreferences.serializer(), updated).jsonObject
    val merged = buildJsonObject {
        for ((k, base_value) in base_obj) {
            val new_value = updated_obj[k]
            put(k, if (new_value != null && new_value != baseline_obj[k]) new_value else base_value)
        }
    }
    return json.decodeFromJsonElement(UserPreferences.serializer(), merged)
}

fun encode_preferences_preserving_unknown(
    json: Json,
    prefs: UserPreferences,
    original_json_str: String?,
): String {
    val known = json.encodeToJsonElement(UserPreferences.serializer(), prefs).jsonObject
    val original = original_json_str
        ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        ?: return json.encodeToString(JsonObject.serializer(), known)
    val known_keys = known.keys
    val merged = buildJsonObject {
        for ((k, v) in original) if (k !in known_keys) put(k, v)
        for ((k, v) in known) put(k, v)
    }
    return json.encodeToString(JsonObject.serializer(), merged)
}
