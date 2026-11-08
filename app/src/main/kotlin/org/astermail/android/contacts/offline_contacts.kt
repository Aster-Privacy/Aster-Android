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

import kotlinx.serialization.Serializable
import org.astermail.android.mail.PendingActionKind
import org.json.JSONArray
import org.json.JSONObject

const val LOCAL_CONTACT_PREFIX = "local-contact-"

fun is_local_contact_id(id: String?): Boolean = id?.startsWith(LOCAL_CONTACT_PREFIX) == true

@Serializable
data class PendingContactCipher(
    val encrypted_data: String,
    val data_nonce: String,
    val integrity_hash: String? = null,
    val data_version: Int? = null,
)

@Serializable
data class PendingContactPayload(
    val contact_id: String,
    val local: PendingContactCipher? = null,
    val base: PendingContactCipher? = null,
    val base_revision: Long? = null,
    val created_at: String? = null,
)

data class ContactOp(val kind: PendingActionKind, val payload: PendingContactPayload)

sealed interface ContactOpMerge {
    data class Replace(val op: ContactOp) : ContactOpMerge
    data object Drop : ContactOpMerge
    data object Keep : ContactOpMerge
}

fun coalesce_contact_op(pending: ContactOp, incoming: ContactOp): ContactOpMerge = when (pending.kind) {
    PendingActionKind.contact_create -> when (incoming.kind) {
        PendingActionKind.contact_delete -> ContactOpMerge.Drop
        else -> ContactOpMerge.Replace(
            ContactOp(PendingActionKind.contact_create, pending.payload.copy(local = incoming.payload.local ?: pending.payload.local)),
        )
    }
    PendingActionKind.contact_update -> when (incoming.kind) {
        PendingActionKind.contact_delete -> ContactOpMerge.Replace(
            ContactOp(PendingActionKind.contact_delete, pending.payload.copy(local = null)),
        )
        else -> ContactOpMerge.Replace(
            ContactOp(PendingActionKind.contact_update, pending.payload.copy(local = incoming.payload.local ?: pending.payload.local)),
        )
    }
    PendingActionKind.contact_delete -> ContactOpMerge.Keep
    else -> ContactOpMerge.Replace(incoming)
}

private val envelope_keys = setOf("_version", "_encrypted_at")

private const val trash_key = "deleted_at"

private val coupled_units: List<Set<String>> = listOf(
    setOf("emails", "email_entries"),
    setOf("phone", "phone_entries"),
    setOf("address", "address_entries"),
    setOf("job_title", "role"),
    setOf("first_name", "last_name"),
)

private fun unit_of(key: String): Set<String> = coupled_units.firstOrNull { key in it } ?: setOf(key)

private fun normalized_value(value: Any?): Any? = when {
    value == null || value == JSONObject.NULL -> null
    value is String && value.isEmpty() -> null
    value is JSONArray && value.length() == 0 -> null
    value is JSONObject && value.length() == 0 -> null
    else -> value
}

private fun canonical(value: Any?): String {
    val v = normalized_value(value)
    return when (v) {
        null -> "u"
        is JSONObject -> {
            val keys = v.keys().asSequence().filter { normalized_value(v.opt(it)) != null }.sorted().toList()
            keys.joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(v.opt(it)) }
        }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canonical(v.opt(it)) }
        is String -> JSONObject.quote(v)
        is Number -> if (v.toDouble() == Math.floor(v.toDouble()) && !v.toDouble().isInfinite()) {
            v.toLong().toString()
        } else {
            v.toString()
        }
        else -> v.toString()
    }
}

private fun same_unit(a: JSONObject, b: JSONObject, unit: Set<String>): Boolean =
    unit.all { canonical(a.opt(it)) == canonical(b.opt(it)) }

private fun is_trashed(obj: JSONObject): Boolean = normalized_value(obj.opt(trash_key)) is String

private fun content_keys(vararg objects: JSONObject): Set<String> =
    objects.flatMap { it.keys().asSequence().toList() }
        .filterNot { it in envelope_keys || it == trash_key }
        .toSet()

fun merge_contact_json(base: String?, local: String, remote: String): String {
    if (base == null) return local
    val base_obj = JSONObject(base)
    val local_obj = JSONObject(local)
    val remote_obj = JSONObject(remote)
    val merged = JSONObject()
    var local_edited = false
    var remote_edited = false
    val handled = mutableSetOf<String>()
    for (key in content_keys(base_obj, local_obj, remote_obj).sorted()) {
        if (key in handled) continue
        val unit = unit_of(key)
        handled.addAll(unit)
        val local_touched = !same_unit(local_obj, base_obj, unit)
        val remote_touched = !same_unit(remote_obj, base_obj, unit)
        if (local_touched) local_edited = true
        if (remote_touched) remote_edited = true
        val source = if (local_touched) local_obj else remote_obj
        for (name in unit) {
            val value = source.opt(name)
            if (value != null && value != JSONObject.NULL) merged.put(name, value)
        }
    }
    val base_trashed = is_trashed(base_obj)
    val local_trash_touched = canonical(local_obj.opt(trash_key)) != canonical(base_obj.opt(trash_key))
    val remote_trash_touched = canonical(remote_obj.opt(trash_key)) != canonical(base_obj.opt(trash_key))
    var trash_source: JSONObject? = if (local_trash_touched) local_obj else remote_obj
    if (local_trash_touched && is_trashed(local_obj) && !base_trashed && remote_edited) trash_source = remote_obj
    if (remote_trash_touched && is_trashed(remote_obj) && !base_trashed && local_edited && !local_trash_touched) {
        trash_source = null
    }
    val trash = trash_source?.opt(trash_key)
    if (normalized_value(trash) != null) merged.put(trash_key, trash)
    return merged.toString()
}

fun contact_json_equivalent(a: String, b: String): Boolean {
    val left = JSONObject(a)
    val right = JSONObject(b)
    envelope_keys.forEach {
        left.remove(it)
        right.remove(it)
    }
    return canonical(left) == canonical(right)
}

fun contact_json_only_trash_changed(base: String, local: String): Boolean {
    val base_obj = JSONObject(base)
    val local_obj = JSONObject(local)
    return content_keys(base_obj, local_obj).all { canonical(base_obj.opt(it)) == canonical(local_obj.opt(it)) }
}
