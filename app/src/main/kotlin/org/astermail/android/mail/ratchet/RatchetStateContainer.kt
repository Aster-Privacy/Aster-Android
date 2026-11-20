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

data class RatchetStateHeader(val epoch: Long, val sync_version: Long)

class RatchetStatePayload(
    val header: RatchetStateHeader,
    val header_bytes: ByteArray,
    val ciphertext: ByteArray,
)

object RatchetStateContainer {

    private const val bound_aad_prefix = "aster-ratchet-state-v2:"
    private const val state_field = "state"
    private const val conversation_field = "conversation_id"
    private const val version_field = "sync_version"
    private const val sync_aad_label = "aster-ratchet-state-v3"
    private const val max_header_value = 9_007_199_254_740_991L
    private val header_magic = byteArrayOf(0x41, 0x52, 0x53, 0x03)
    val header_length = header_magic.size + 16

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun bound_aad(conversation_id: String): ByteArray =
        (bound_aad_prefix + conversation_id).toByteArray(Charsets.UTF_8)

    fun encode_header(header: RatchetStateHeader): ByteArray {
        require(header.epoch in 0..max_header_value && header.sync_version in 0..max_header_value) {
            "ratchet state header out of range"
        }
        return java.nio.ByteBuffer.allocate(header_length)
            .put(header_magic)
            .putLong(header.epoch)
            .putLong(header.sync_version)
            .array()
    }

    fun split_payload(bytes: ByteArray): RatchetStatePayload? {
        if (bytes.size <= header_length) return null
        for (i in header_magic.indices) {
            if (bytes[i] != header_magic[i]) return null
        }
        val buffer = java.nio.ByteBuffer.wrap(bytes, header_magic.size, 16)
        val epoch = buffer.long
        val sync_version = buffer.long
        if (epoch !in 0..max_header_value || sync_version !in 0..max_header_value) return null
        return RatchetStatePayload(
            header = RatchetStateHeader(epoch, sync_version),
            header_bytes = bytes.copyOfRange(0, header_length),
            ciphertext = bytes.copyOfRange(header_length, bytes.size),
        )
    }

    fun sync_aad(user_id: String, conversation_id: String, header_bytes: ByteArray): ByteArray {
        val label = sync_aad_label.toByteArray(Charsets.UTF_8)
        val user = user_id.toByteArray(Charsets.UTF_8)
        val conversation = conversation_id.toByteArray(Charsets.UTF_8)
        return java.nio.ByteBuffer.allocate(label.size + 4 + user.size + 4 + conversation.size + header_bytes.size)
            .put(label)
            .putInt(user.size)
            .put(user)
            .putInt(conversation.size)
            .put(conversation)
            .put(header_bytes)
            .array()
    }

    fun header_matches(header: RatchetStateHeader, state: RatchetState, sync_version: Long?): Boolean =
        sync_version == header.sync_version && state.epoch.toLong() == header.epoch

    fun next_version(floor: Long, server_version: Long = 0L): Long = maxOf(floor, server_version) + 1

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
        established: Boolean = floor > 0,
        allow_unversioned: Boolean = false,
    ): RatchetStateContainerResult {
        val parsed = runCatching { json.parseToJsonElement(plaintext_json) }.getOrNull() as? JsonObject
            ?: return RatchetStateContainerResult.Malformed
        val wrapped = parsed[state_field] as? JsonObject
        val outer_id = (parsed[conversation_field] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val inner = if (wrapped != null && !wrapped.containsKey(conversation_field) && outer_id != null) {
            JsonObject(wrapped + (conversation_field to JsonPrimitive(outer_id)))
        } else {
            wrapped ?: parsed
        }
        val state = runCatching {
            json.decodeFromString(RatchetState.serializer(), inner.toString())
        }.getOrNull()?.takeIf { DoubleRatchet.is_well_formed(it) } ?: return RatchetStateContainerResult.Malformed

        if (state.conversation_id != conversation_id) return RatchetStateContainerResult.WrongConversation
        if (wrapped != null) {
            if (outer_id != null && outer_id != conversation_id) {
                return RatchetStateContainerResult.WrongConversation
            }
            if (outer_id == null && opened_bound) return RatchetStateContainerResult.Unbound
        }

        val sync_version = if (wrapped != null) (parsed[version_field] as? JsonPrimitive)?.longOrNull else null
        if (opened_bound && sync_version == null) return RatchetStateContainerResult.Unbound
        if (sync_version != null && sync_version < floor) return RatchetStateContainerResult.RolledBack
        if ((established || floor > 0) && !allow_unversioned &&
            (wrapped == null || outer_id == null || sync_version == null)
        ) {
            return RatchetStateContainerResult.Unbound
        }

        return RatchetStateContainerResult.Accepted(state, sync_version)
    }
}
