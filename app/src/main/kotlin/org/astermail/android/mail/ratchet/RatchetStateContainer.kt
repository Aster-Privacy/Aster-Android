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

package org.astermail.android.mail.ratchet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull

sealed class RatchetStateContainerResult {
    data class Accepted(val state: RatchetState, val sync_version: Long?) : RatchetStateContainerResult()
    object Malformed : RatchetStateContainerResult()
    object WrongConversation : RatchetStateContainerResult()
    object RolledBack : RatchetStateContainerResult()
    object Unbound : RatchetStateContainerResult()
}

object RatchetStateContainer {

    private const val bound_aad_prefix = "aster-ratchet-state-v2:"
    private const val state_field = "state"
    private const val conversation_field = "conversation_id"
    private const val version_field = "sync_version"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun bound_aad(conversation_id: String): ByteArray =
        (bound_aad_prefix + conversation_id).toByteArray(Charsets.UTF_8)

    fun next_version(floor: Long, now_ms: Long): Long = maxOf(now_ms, floor + 1)

    fun state_fingerprint(state: RatchetState): Int =
        json.encodeToString(RatchetState.serializer(), state).hashCode()

    fun encode(conversation_id: String, state: RatchetState, sync_version: Long): String {
        val state_element = json.encodeToString(RatchetState.serializer(), state)
        return buildJsonObject {
            put(state_field, json.parseToJsonElement(state_element))
            put(conversation_field, JsonPrimitive(conversation_id))
            put(version_field, JsonPrimitive(sync_version))
        }.toString()
    }

    fun decode(
        plaintext_json: String,
        conversation_id: String,
        floor: Long,
        opened_bound: Boolean,
    ): RatchetStateContainerResult {
        val parsed = runCatching { json.parseToJsonElement(plaintext_json) }.getOrNull() as? JsonObject
            ?: return RatchetStateContainerResult.Malformed
        val wrapped = parsed[state_field] as? JsonObject
        val state = runCatching {
            json.decodeFromString(RatchetState.serializer(), (wrapped ?: parsed).toString())
        }.getOrNull() ?: return RatchetStateContainerResult.Malformed

        if (state.conversation_id != conversation_id) return RatchetStateContainerResult.WrongConversation
        if (wrapped != null) {
            val bound_id = (parsed[conversation_field] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (bound_id != null && bound_id != conversation_id) {
                return RatchetStateContainerResult.WrongConversation
            }
            if (bound_id == null && opened_bound) return RatchetStateContainerResult.Unbound
        }

        val sync_version = if (wrapped != null) (parsed[version_field] as? JsonPrimitive)?.longOrNull else null
        if (opened_bound && sync_version == null) return RatchetStateContainerResult.Unbound
        if (sync_version != null && sync_version < floor) return RatchetStateContainerResult.RolledBack

        return RatchetStateContainerResult.Accepted(state, sync_version)
    }
}
