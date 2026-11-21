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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

const val LEARNED_CATEGORY_SENDERS_KEY = "learned_category_senders"
const val LEARN_FROM_CATEGORY_MOVES_KEY = "learn_from_category_moves"
const val MAX_LEARNED_CATEGORY_SENDERS = 500
const val MAX_LEARNED_SENDER_LENGTH = 320
const val MAX_LEARNED_CATEGORY_ID_LENGTH = 128

val LEARNED_BUILTIN_CATEGORY_IDS: Set<String> = setOf(
    "primary",
    "promotions",
    "newsletters",
    "social",
    "updates",
    "transactions",
    "forums",
    "finance",
    "travel",
    "shopping",
)

const val LEARNED_CUSTOM_CATEGORY_PREFIX = "custom:"

@Serializable
data class LearnedCategorySender(
    val sender: String = "",
    val category: String = "",
    val updated_at: Long = 0L,
)

fun normalize_learned_sender(raw: String?): String? {
    if (raw == null) return null
    val sender = raw.trim().lowercase(java.util.Locale.ROOT)
    if (sender.isEmpty() || sender.length > MAX_LEARNED_SENDER_LENGTH) return null
    if (sender.count { it == '@' } != 1) return null
    if (sender.any { it.isWhitespace() }) return null
    val at = sender.indexOf('@')
    if (at == 0 || at == sender.length - 1) return null
    return sender
}

fun is_valid_learned_category(category: String?): Boolean {
    if (category.isNullOrEmpty()) return false
    if (category.length > MAX_LEARNED_CATEGORY_ID_LENGTH) return false
    if (category in LEARNED_BUILTIN_CATEGORY_IDS) return true
    return category.startsWith(LEARNED_CUSTOM_CATEGORY_PREFIX) &&
        category.length > LEARNED_CUSTOM_CATEGORY_PREFIX.length &&
        category.none { it.isWhitespace() }
}

fun normalize_learned_entry(entry: LearnedCategorySender): LearnedCategorySender? {
    val sender = normalize_learned_sender(entry.sender) ?: return null
    if (!is_valid_learned_category(entry.category)) return null
    if (entry.updated_at < 0L) return null
    return if (sender == entry.sender) entry else entry.copy(sender = sender)
}

fun preferred_learned_entry(
    first: LearnedCategorySender,
    second: LearnedCategorySender,
): LearnedCategorySender = when {
    first.updated_at > second.updated_at -> first
    second.updated_at > first.updated_at -> second
    first.category <= second.category -> first
    else -> second
}

fun sanitize_learned_category_senders(
    entries: List<LearnedCategorySender>,
): List<LearnedCategorySender> {
    val by_sender = LinkedHashMap<String, LearnedCategorySender>()
    for (raw in entries) {
        val entry = normalize_learned_entry(raw) ?: continue
        val existing = by_sender[entry.sender]
        by_sender[entry.sender] = if (existing == null) entry else preferred_learned_entry(existing, entry)
    }
    return by_sender.values
        .sortedWith(compareByDescending<LearnedCategorySender> { it.updated_at }.thenBy { it.sender })
        .take(MAX_LEARNED_CATEGORY_SENDERS)
}

fun merge_learned_category_senders(
    first: List<LearnedCategorySender>,
    second: List<LearnedCategorySender>,
): List<LearnedCategorySender> = sanitize_learned_category_senders(first + second)

fun rebase_learned_category_senders(
    server: List<LearnedCategorySender>,
    baseline: List<LearnedCategorySender>?,
    local: List<LearnedCategorySender>,
): List<LearnedCategorySender> {
    if (baseline == null) return merge_learned_category_senders(server, local)
    val server_map = sanitize_learned_category_senders(server).associateBy { it.sender }
    val baseline_map = sanitize_learned_category_senders(baseline).associateBy { it.sender }
    val local_map = sanitize_learned_category_senders(local).associateBy { it.sender }
    val senders = LinkedHashSet<String>().apply {
        addAll(server_map.keys)
        addAll(baseline_map.keys)
        addAll(local_map.keys)
    }
    val result = ArrayList<LearnedCategorySender>()
    for (sender in senders) {
        val chosen = reconcile_learned_sender(baseline_map[sender], local_map[sender], server_map[sender])
        if (chosen != null) result.add(chosen)
    }
    return sanitize_learned_category_senders(result)
}

private fun reconcile_learned_sender(
    before: LearnedCategorySender?,
    mine: LearnedCategorySender?,
    theirs: LearnedCategorySender?,
): LearnedCategorySender? {
    if (before == null) {
        return when {
            mine == null -> theirs
            theirs == null -> mine
            else -> preferred_learned_entry(mine, theirs)
        }
    }
    return when {
        mine == before -> theirs
        theirs == before -> mine
        mine == null -> theirs
        theirs == null -> mine
        else -> preferred_learned_entry(mine, theirs)
    }
}

private fun primitive_string(element: JsonElement?): String? {
    val primitive = element as? JsonPrimitive ?: return null
    return if (primitive.isString) primitive.content else null
}

private fun primitive_epoch_seconds(element: JsonElement?): Long? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    val whole = primitive.longOrNull
    if (whole != null) return whole
    val fractional = primitive.doubleOrNull ?: return null
    if (fractional.isNaN() || fractional.isInfinite()) return null
    return fractional.toLong()
}

fun parse_learned_category_senders(element: JsonElement?): List<LearnedCategorySender> {
    val array = element as? JsonArray ?: return emptyList()
    val entries = ArrayList<LearnedCategorySender>(array.size)
    for (item in array) {
        val obj = item as? JsonObject ?: continue
        val sender = primitive_string(obj["sender"]) ?: continue
        val category = primitive_string(obj["category"]) ?: continue
        val updated_at = primitive_epoch_seconds(obj["updated_at"]) ?: continue
        entries.add(LearnedCategorySender(sender, category, updated_at))
    }
    return sanitize_learned_category_senders(entries)
}

object LearnedCategorySendersSerializer : KSerializer<List<LearnedCategorySender>> {
    private val delegate = ListSerializer(LearnedCategorySender.serializer())

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<LearnedCategorySender> {
        val json_decoder = decoder as? JsonDecoder
            ?: return sanitize_learned_category_senders(delegate.deserialize(decoder))
        return parse_learned_category_senders(json_decoder.decodeJsonElement())
    }

    override fun serialize(encoder: Encoder, value: List<LearnedCategorySender>) {
        delegate.serialize(encoder, sanitize_learned_category_senders(value))
    }
}
