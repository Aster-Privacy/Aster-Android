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

package org.astermail.android.contacts

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class RelationKind(val id: String, val english: String) {
    FRIEND("friend", "Friend"),
    MANAGER("manager", "Manager"),
    PARTNER("partner", "Partner"),
    ASSISTANT("assistant", "Assistant"),
    SPOUSE("spouse", "Spouse"),
    RELATIVE("relative", "Relative"),
    CHILD("child", "Child"),
    MOTHER("mother", "Mother"),
    FATHER("father", "Father"),
    BROTHER("brother", "Brother"),
    SISTER("sister", "Sister"),
    CUSTOM("custom", "Custom"),
    PARENT("parent", "Parent"),
    SIBLING("sibling", "Sibling"),
    ;

    val is_legacy: Boolean get() = this == PARENT || this == SIBLING

    companion object {
        val PICKER: List<RelationKind> = listOf(
            FRIEND, MANAGER, PARTNER, ASSISTANT, SPOUSE, RELATIVE, CHILD, MOTHER, FATHER, BROTHER, SISTER, CUSTOM,
        )

        fun from_id(id: String): RelationKind? {
            val lowered = id.trim().lowercase(Locale.ROOT)
            return entries.firstOrNull { it.id == lowered }
        }

        fun from_english(name: String): RelationKind? {
            val lowered = name.trim().lowercase(Locale.ROOT)
            if (lowered == "domestic partner") return PARTNER
            return entries.firstOrNull { it != CUSTOM && it.id == lowered }
        }
    }
}

data class ContactRelation(
    val kind: RelationKind,
    val value: String,
    val label: String = "",
) {
    fun picker_kinds(): List<RelationKind> =
        if (kind.is_legacy) RelationKind.PICKER + kind else RelationKind.PICKER
}

const val MAX_RELATIONS = 50
const val MAX_RELATION_VALUE = 200
const val MAX_RELATION_LABEL = 50
const val RELATIONS_KEY = "related_people"
const val ANDROID_RELATION_MIMETYPE = "vnd.android.cursor.item/relation"
const val REFERRED_BY_LABEL = "Referred by"

private val DIRECT_KINDS = setOf(
    "friend", "manager", "partner", "assistant", "spouse", "child", "mother", "father", "brother", "sister",
    "relative", "parent", "sibling",
)

private fun is_relation_control(code_point: Int): Boolean =
    code_point in 0x00..0x1F || code_point == 0x7F || code_point == 0x2028 || code_point == 0x2029

fun cap_code_points(text: String, cap: Int): String {
    if (text.codePointCount(0, text.length) <= cap) return text
    return text.substring(0, text.offsetByCodePoints(0, cap))
}

fun clean_relation_text(text: String, cap: Int): String {
    val replaced = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val code_point = text.codePointAt(index)
        if (is_relation_control(code_point)) replaced.append(' ') else replaced.appendCodePoint(code_point)
        index += Character.charCount(code_point)
    }
    return cap_code_points(replaced.toString().trim(), cap).trim()
}

private fun JSONObject.text_or_empty(key: String): String = opt(key) as? String ?: ""

fun decode_relation(obj: JSONObject): ContactRelation? {
    val value = obj.opt("value") as? String ?: return null
    val raw_type = obj.text_or_empty("type").trim()
    val type = raw_type.lowercase(Locale.ROOT)
    val relation = obj.text_or_empty("relation").trim().lowercase(Locale.ROOT)
    val label = obj.text_or_empty("label").trim()
    val direct = RelationKind.from_id(relation)
    return when {
        (relation == "mother" || relation == "father") && type == "parent" && direct != null ->
            ContactRelation(direct, value)
        (relation == "brother" || relation == "sister") && type == "sibling" && direct != null ->
            ContactRelation(direct, value)
        relation == "relative" && (type == "other" || type == "" || type == "custom") ->
            ContactRelation(RelationKind.RELATIVE, value)
        type in DIRECT_KINDS -> ContactRelation(RelationKind.from_id(type) ?: RelationKind.CUSTOM, value)
        type == "other" || type == "custom" || type == "" -> ContactRelation(RelationKind.CUSTOM, value, label)
        else -> ContactRelation(RelationKind.CUSTOM, value, label.ifEmpty { raw_type })
    }
}

fun decode_relations(arr: JSONArray?): List<ContactRelation> {
    if (arr == null) return emptyList()
    val out = mutableListOf<ContactRelation>()
    for (i in 0 until arr.length()) {
        val entry = arr.opt(i) as? JSONObject ?: continue
        decode_relation(entry)?.let { out.add(it) }
    }
    return out
}

fun decode_contact_relations(obj: JSONObject): List<ContactRelation> =
    decode_relations(obj.opt(RELATIONS_KEY) as? JSONArray)

fun encode_relation(relation: ContactRelation): JSONObject {
    val obj = JSONObject().put("value", relation.value)
    when (relation.kind) {
        RelationKind.MOTHER, RelationKind.FATHER -> obj.put("type", "parent").put("relation", relation.kind.id)
        RelationKind.BROTHER, RelationKind.SISTER -> obj.put("type", "sibling").put("relation", relation.kind.id)
        RelationKind.RELATIVE -> obj.put("type", "other").put("relation", "relative")
        RelationKind.CUSTOM -> {
            obj.put("type", "other")
            if (relation.label.isNotEmpty()) obj.put("label", relation.label)
        }
        else -> obj.put("type", relation.kind.id)
    }
    return obj
}

fun normalize_relation(relation: ContactRelation): ContactRelation? {
    val value = clean_relation_text(relation.value, MAX_RELATION_VALUE)
    if (value.isEmpty()) return null
    val label = if (relation.kind == RelationKind.CUSTOM) {
        clean_relation_text(relation.label, MAX_RELATION_LABEL)
    } else {
        ""
    }
    return ContactRelation(relation.kind, value, label)
}

fun relation_dedupe_key(relation: ContactRelation): String {
    val label = if (relation.kind == RelationKind.CUSTOM) relation.label.lowercase(Locale.ROOT) else ""
    val value = Normalizer.normalize(relation.value, Normalizer.Form.NFC).lowercase(Locale.ROOT)
    return relation.kind.id + "\u0000" + label + "\u0000" + value
}

fun normalize_relations(relations: List<ContactRelation>): List<ContactRelation> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<ContactRelation>()
    for (relation in relations) {
        if (out.size >= MAX_RELATIONS) break
        val clean = normalize_relation(relation) ?: continue
        if (!seen.add(relation_dedupe_key(clean))) continue
        out.add(clean)
    }
    return out
}

fun merge_relations(first: List<ContactRelation>, second: List<ContactRelation>): List<ContactRelation> =
    normalize_relations(first + second)

fun relations_json_array(original: JSONArray?, relations: List<ContactRelation>): JSONArray {
    val originals = mutableListOf<Pair<JSONObject, ContactRelation?>>()
    if (original != null) {
        for (i in 0 until original.length()) {
            val entry = original.opt(i) as? JSONObject ?: continue
            originals.add(entry to decode_relation(entry)?.let { normalize_relation(it) })
        }
    }
    val used = BooleanArray(originals.size)
    val out = JSONArray()
    for (relation in normalize_relations(relations)) {
        val match = originals.indices.firstOrNull { !used[it] && originals[it].second == relation }
        if (match != null) {
            used[match] = true
            out.put(originals[match].first)
        } else {
            out.put(encode_relation(relation))
        }
    }
    return out
}

fun write_contact_relations(obj: JSONObject, relations: List<ContactRelation>?) {
    if (relations == null) return
    val original = obj.opt(RELATIONS_KEY) as? JSONArray
    if (decode_relations(original) == relations) return
    val arr = relations_json_array(original, relations)
    if (arr.length() == 0) obj.remove(RELATIONS_KEY) else obj.put(RELATIONS_KEY, arr)
}

fun relation_matches(
    relation: ContactRelation,
    kind_name: String,
    folded_query: String,
    fold: (String) -> String,
): Boolean {
    if (folded_query.isEmpty()) return false
    return fold(relation.value).contains(folded_query) ||
        (relation.label.isNotEmpty() && fold(relation.label).contains(folded_query)) ||
        fold(kind_name).contains(folded_query)
}

fun relation_vcard_type(kind: RelationKind): String? = when (kind) {
    RelationKind.FRIEND -> "friend"
    RelationKind.SPOUSE -> "spouse"
    RelationKind.CHILD -> "child"
    RelationKind.MOTHER, RelationKind.FATHER, RelationKind.PARENT -> "parent"
    RelationKind.BROTHER, RelationKind.SISTER, RelationKind.SIBLING -> "sibling"
    RelationKind.RELATIVE -> "kin"
    else -> null
}

fun relation_vcard_label(relation: ContactRelation): String? = when (relation.kind) {
    RelationKind.RELATIVE -> "Relative"
    RelationKind.CUSTOM -> relation.label.ifEmpty { null }
    else -> "_$!<" + relation.kind.english + ">!$" + "_"
}

private val IGNORED_VCARD_TYPES = setOf("pref", "text", "uri", "voice", "internet")

private val VCARD_TYPE_KINDS = mapOf(
    "friend" to RelationKind.FRIEND,
    "spouse" to RelationKind.SPOUSE,
    "child" to RelationKind.CHILD,
    "parent" to RelationKind.PARENT,
    "sibling" to RelationKind.SIBLING,
    "kin" to RelationKind.RELATIVE,
    "mother" to RelationKind.MOTHER,
    "father" to RelationKind.FATHER,
    "brother" to RelationKind.BROTHER,
    "sister" to RelationKind.SISTER,
    "manager" to RelationKind.MANAGER,
    "supervisor" to RelationKind.MANAGER,
    "assistant" to RelationKind.ASSISTANT,
    "partner" to RelationKind.PARTNER,
    "domestic_partner" to RelationKind.PARTNER,
)

private fun unwrap_ab_label(label: String): String {
    val trimmed = label.trim()
    val prefix = "_$!<"
    val suffix = ">!$" + "_"
    return if (trimmed.startsWith(prefix) && trimmed.endsWith(suffix) && trimmed.length >= prefix.length + suffix.length) {
        trimmed.substring(prefix.length, trimmed.length - suffix.length).trim()
    } else {
        trimmed
    }
}

fun relation_from_label(label: String, value: String): ContactRelation {
    val inner = unwrap_ab_label(label)
    val kind = RelationKind.from_english(inner)
    return if (kind != null) ContactRelation(kind, value) else ContactRelation(RelationKind.CUSTOM, value, inner)
}

fun relation_from_vcard(value: String, types: List<String>, ab_label: String?): ContactRelation? {
    if (value.trim().lowercase(Locale.ROOT).startsWith("urn:uuid:")) return null
    if (!ab_label.isNullOrBlank()) return relation_from_label(ab_label, value)
    val tokens = types.flatMap { it.split(',') }
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotEmpty() && it !in IGNORED_VCARD_TYPES }
    for (token in tokens) {
        VCARD_TYPE_KINDS[token]?.let { return ContactRelation(it, value) }
    }
    tokens.firstOrNull()?.let { return ContactRelation(RelationKind.CUSTOM, value, it) }
    return ContactRelation(RelationKind.CUSTOM, value)
}

fun relation_to_device_type(relation: ContactRelation, sibling_label: String): Pair<Int, String?> =
    when (relation.kind) {
        RelationKind.ASSISTANT -> 1 to null
        RelationKind.BROTHER -> 2 to null
        RelationKind.CHILD -> 3 to null
        RelationKind.FATHER -> 5 to null
        RelationKind.FRIEND -> 6 to null
        RelationKind.MANAGER -> 7 to null
        RelationKind.MOTHER -> 8 to null
        RelationKind.PARENT -> 9 to null
        RelationKind.PARTNER -> 10 to null
        RelationKind.RELATIVE -> 12 to null
        RelationKind.SISTER -> 13 to null
        RelationKind.SPOUSE -> 14 to null
        RelationKind.SIBLING -> 0 to sibling_label
        RelationKind.CUSTOM -> 0 to relation.label
    }

fun relation_from_device_type(
    type: Int,
    label: String,
    value: String,
    sibling_label: String = RelationKind.SIBLING.english,
): ContactRelation = when (type) {
    1 -> ContactRelation(RelationKind.ASSISTANT, value)
    2 -> ContactRelation(RelationKind.BROTHER, value)
    3 -> ContactRelation(RelationKind.CHILD, value)
    4, 10 -> ContactRelation(RelationKind.PARTNER, value)
    5 -> ContactRelation(RelationKind.FATHER, value)
    6 -> ContactRelation(RelationKind.FRIEND, value)
    7 -> ContactRelation(RelationKind.MANAGER, value)
    8 -> ContactRelation(RelationKind.MOTHER, value)
    9 -> ContactRelation(RelationKind.PARENT, value)
    11 -> ContactRelation(RelationKind.CUSTOM, value, REFERRED_BY_LABEL)
    12 -> ContactRelation(RelationKind.RELATIVE, value)
    13 -> ContactRelation(RelationKind.SISTER, value)
    14 -> ContactRelation(RelationKind.SPOUSE, value)
    else -> {
        val trimmed = label.trim()
        if (trimmed.isNotEmpty() && trimmed.equals(sibling_label.trim(), ignoreCase = true)) {
            ContactRelation(RelationKind.SIBLING, value)
        } else {
            RelationKind.from_english(trimmed)?.let { ContactRelation(it, value) }
                ?: ContactRelation(RelationKind.CUSTOM, value, trimmed)
        }
    }
}

fun relation_from_android_custom(components: List<String>): ContactRelation? {
    if (components.firstOrNull()?.trim() != ANDROID_RELATION_MIMETYPE) return null
    val value = components.getOrNull(1).orEmpty()
    val type = components.getOrNull(2)?.trim()?.toIntOrNull() ?: 0
    val label = components.getOrNull(3).orEmpty()
    return relation_from_device_type(type, label, value)
}

fun relation_csv_label(relation: ContactRelation): String =
    if (relation.kind == RelationKind.CUSTOM) relation.label else relation.kind.english
