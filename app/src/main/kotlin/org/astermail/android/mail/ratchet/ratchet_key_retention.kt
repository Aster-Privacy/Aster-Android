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

package org.astermail.android.mail.ratchet

import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject

const val max_retained_ratchet_key_sets = 32

private val retained_key_fields = listOf(
    "ratchet_identity_key",
    "ratchet_identity_public",
    "ratchet_signed_prekey",
    "ratchet_signed_prekey_public",
    "ratchet_pq_identity_key",
    "ratchet_pq_identity_public",
    "ratchet_pq_identity_seed",
)

private fun JSONObject.text(name: String): String = optString(name, "").trim()

private fun pq_secret_of(entry: JSONObject): String =
    entry.text("ratchet_pq_identity_key").ifBlank { entry.text("ratchet_pq_identity_seed") }

private fun holds_retained_keys(entry: JSONObject): Boolean =
    entry.text("ratchet_identity_key").isNotBlank() || pq_secret_of(entry).isNotBlank()

private fun same_signed_prekey(a: JSONObject, b: JSONObject): Boolean {
    val a_public = a.text("ratchet_signed_prekey_public")
    val b_public = b.text("ratchet_signed_prekey_public")
    val a_jwk = a.text("ratchet_signed_prekey")
    val b_jwk = b.text("ratchet_signed_prekey")
    if ((a_public.isBlank() && a_jwk.isBlank()) || (b_public.isBlank() && b_jwk.isBlank())) return true
    if (a_public.isNotBlank() && b_public.isNotBlank()) return a_public == b_public
    return a_jwk.isNotBlank() && a_jwk == b_jwk
}

private fun same_retained_set(a: JSONObject, b: JSONObject): Boolean =
    a.text("ratchet_identity_key") == b.text("ratchet_identity_key") &&
        pq_secret_of(a) == pq_secret_of(b) &&
        same_signed_prekey(a, b)

fun receiver_key_set_tag(
    identity_public_b64: String,
    identity_jwk: String,
    signed_prekey_public_b64: String,
): String = "${identity_public_b64.ifBlank { identity_jwk }}|$signed_prekey_public_b64"

fun merge_retained_ratchet_keys(vararg sources: String?): String? {
    val merged = mutableListOf<JSONObject>()
    for (source in sources) {
        if (source.isNullOrBlank()) continue
        val entries = runCatching { JSONArray(source) }.getOrNull() ?: continue
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            if (!holds_retained_keys(entry)) continue
            val existing = merged.firstOrNull { same_retained_set(it, entry) }
            if (existing == null) {
                merged.add(JSONObject(entry.toString()))
                continue
            }
            for (field in retained_key_fields) {
                if (existing.text(field).isBlank() && entry.text(field).isNotBlank()) {
                    existing.put(field, entry.text(field))
                }
            }
        }
    }
    if (merged.isEmpty()) return null
    val result = JSONArray()
    merged.take(max_retained_ratchet_key_sets).forEach { result.put(it) }
    return result.toString()
}

fun retained_key_sets_equal(a: JSONArray?, b: JSONArray?): Boolean {
    val left = a ?: JSONArray()
    val right = b ?: JSONArray()
    if (left.length() != right.length()) return false
    for (index in 0 until left.length()) {
        val x = left.optJSONObject(index)
        val y = right.optJSONObject(index)
        if (x == null || y == null) {
            if (x != null || y != null) return false
            continue
        }
        if (retained_key_fields.any { x.text(it) != y.text(it) }) return false
    }
    return true
}

private fun merge_into_vault_previous_keys(vault_json: JSONObject, newest: JSONObject) {
    val merged = merge_retained_ratchet_keys(
        JSONArray().put(newest).toString(),
        vault_json.optJSONArray("ratchet_previous_keys")?.toString(),
    ) ?: return
    vault_json.put("ratchet_previous_keys", JSONArray(merged))
}

private fun local_ratchet_key_entry(session_key_store: SessionKeyStore): JSONObject? {
    val local_identity = session_key_store.get_ratchet_identity_jwk()?.takeIf { it.isNotBlank() }
    val local_spk = session_key_store.get_ratchet_signed_prekey_jwk()?.takeIf { it.isNotBlank() }
    val local_pq_secret = session_key_store.get_ratchet_pq_identity_secret()?.takeIf { it.isNotBlank() }
    val local_pq_public = session_key_store.get_ratchet_pq_identity_public()?.takeIf { it.isNotBlank() }
    if (local_identity == null && local_pq_secret == null) return null

    val entry = JSONObject()
    if (local_identity != null) {
        entry.put("ratchet_identity_key", local_identity)
        val identity_public = session_key_store.get_ratchet_identity_public_b64()?.takeIf { it.isNotBlank() }
            ?: runCatching { RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(local_identity)) }
                .getOrNull()
        identity_public?.let { entry.put("ratchet_identity_public", it) }
    }
    if (local_spk != null) {
        entry.put("ratchet_signed_prekey", local_spk)
        session_key_store.get_ratchet_signed_prekey_public_b64()
            ?.takeIf { it.isNotBlank() }
            ?.let { entry.put("ratchet_signed_prekey_public", it) }
    }
    local_pq_secret?.let { entry.put("ratchet_pq_identity_key", it) }
    local_pq_public?.let { entry.put("ratchet_pq_identity_public", it) }
    return entry
}

fun fold_local_ratchet_keys_into_vault(vault_json: JSONObject, session_key_store: SessionKeyStore) {
    val local = local_ratchet_key_entry(session_key_store)
    val vault_identity_public = runCatching {
        RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(vault_json.text("ratchet_identity_key")))
    }.getOrDefault("")
    val local_differs = local != null && (
        local.text("ratchet_identity_public") != vault_identity_public ||
            local.text("ratchet_signed_prekey_public") != vault_json.text("ratchet_signed_prekey_public") ||
            (local.text("ratchet_pq_identity_public").isNotBlank() &&
                local.text("ratchet_pq_identity_public") != vault_json.text("ratchet_pq_identity_public"))
        )
    val merged = merge_retained_ratchet_keys(
        if (local_differs) JSONArray().put(local).toString() else null,
        vault_json.optJSONArray("ratchet_previous_keys")?.toString(),
        session_key_store.get_ratchet_previous_keys_json(),
    ) ?: return
    vault_json.put("ratchet_previous_keys", JSONArray(merged))
}

fun retain_local_ratchet_keys(
    session_key_store: SessionKeyStore,
    incoming_identity_jwk: String?,
    incoming_signed_prekey_jwk: String?,
    incoming_pq_identity_public_b64: String?,
) {
    val local_identity = session_key_store.get_ratchet_identity_jwk()?.takeIf { it.isNotBlank() }
    val local_spk = session_key_store.get_ratchet_signed_prekey_jwk()?.takeIf { it.isNotBlank() }
    val local_pq_secret = session_key_store.get_ratchet_pq_identity_secret()?.takeIf { it.isNotBlank() }
    val local_pq_public = session_key_store.get_ratchet_pq_identity_public()?.takeIf { it.isNotBlank() }

    val identity_replaced = local_identity != null &&
        !incoming_identity_jwk.isNullOrBlank() && incoming_identity_jwk != local_identity
    val spk_replaced = local_spk != null &&
        !incoming_signed_prekey_jwk.isNullOrBlank() && incoming_signed_prekey_jwk != local_spk
    val pq_replaced = local_pq_secret != null &&
        !incoming_pq_identity_public_b64.isNullOrBlank() && incoming_pq_identity_public_b64 != local_pq_public
    if (!identity_replaced && !spk_replaced && !pq_replaced) return

    val entry = local_ratchet_key_entry(session_key_store) ?: return

    val merged = merge_retained_ratchet_keys(
        JSONArray().put(entry).toString(),
        session_key_store.get_ratchet_previous_keys_json(),
    )
    session_key_store.put_ratchet_previous_keys(merged)
}

data class VaultSignedPrekey(val jwk: String, val public_b64: String)

private fun public_b64_from_private_jwk(jwk: String): String? = runCatching {
    RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(jwk))
}.getOrNull()

fun vault_signed_prekey_to_adopt(vault_json: JSONObject, session_key_store: SessionKeyStore): VaultSignedPrekey? {
    val local_identity = session_key_store.get_ratchet_identity_jwk()?.trim()?.takeIf { it.isNotBlank() }
        ?: return null
    if (vault_json.text("ratchet_identity_key") != local_identity) return null
    val vault_spk = vault_json.text("ratchet_signed_prekey").takeIf { it.isNotBlank() } ?: return null
    val vault_spk_public = vault_json.text("ratchet_signed_prekey_public").takeIf { it.isNotBlank() }
        ?: public_b64_from_private_jwk(vault_spk)
        ?: return null
    val local_spk = session_key_store.get_ratchet_signed_prekey_jwk()?.trim()
    val local_spk_public = session_key_store.get_ratchet_signed_prekey_public_b64()?.trim()
    if (vault_spk == local_spk && vault_spk_public == local_spk_public) return null
    return VaultSignedPrekey(vault_spk, vault_spk_public)
}

fun adopt_vault_signed_prekey(session_key_store: SessionKeyStore, prekey: VaultSignedPrekey): Boolean {
    val identity = session_key_store.get_ratchet_identity_jwk()?.takeIf { it.isNotBlank() } ?: return false
    val identity_public = session_key_store.get_ratchet_identity_public_b64()?.takeIf { it.isNotBlank() }
        ?: public_b64_from_private_jwk(identity)
        ?: return false
    retain_local_ratchet_keys(session_key_store, identity, prekey.jwk, null)
    session_key_store.put_ratchet_keys(identity, identity_public, prekey.jwk, prekey.public_b64)
    return true
}

fun retain_vault_key_set(vault_json: JSONObject, new_identity_jwk: String, new_signed_prekey_jwk: String) {
    val identity = vault_json.text("ratchet_identity_key")
    if (identity.isBlank()) return
    val spk = vault_json.text("ratchet_signed_prekey")
    if (identity == new_identity_jwk.trim() && (spk.isBlank() || spk == new_signed_prekey_jwk.trim())) return
    val entry = JSONObject()
    for (field in retained_key_fields) {
        vault_json.text(field).takeIf { it.isNotBlank() }?.let { entry.put(field, it) }
    }
    merge_into_vault_previous_keys(vault_json, entry)
}

fun retain_vault_pq_identity(vault_json: JSONObject) {
    val entry = JSONObject()
    for (field in listOf("ratchet_pq_identity_key", "ratchet_pq_identity_public", "ratchet_pq_identity_seed")) {
        vault_json.text(field).takeIf { it.isNotBlank() }?.let { entry.put(field, it) }
    }
    if (!holds_retained_keys(entry)) return
    merge_into_vault_previous_keys(vault_json, entry)
}

fun retain_local_pq_identity_in_vault(vault_json: JSONObject, session_key_store: SessionKeyStore) {
    val secret = session_key_store.get_ratchet_pq_identity_secret()?.trim()?.takeIf { it.isNotBlank() } ?: return
    if (secret == vault_json.text("ratchet_pq_identity_key")) return
    val entry = JSONObject().put("ratchet_pq_identity_key", secret)
    session_key_store.get_ratchet_pq_identity_public()
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?.let { entry.put("ratchet_pq_identity_public", it) }
    merge_into_vault_previous_keys(vault_json, entry)
}

fun vault_holds_ratchet_keys(
    vault_json: JSONObject,
    identity_public_b64: String,
    signed_prekey_public_b64: String,
    pq_identity_public_b64: String?,
): Boolean {
    val vault_identity_jwk = vault_json.text("ratchet_identity_key")
    if (vault_identity_jwk.isBlank()) return false
    val vault_identity_public = runCatching {
        RatchetCrypto.b64_encode(RatchetCrypto.p256_public_raw_from_private_jwk(vault_identity_jwk))
    }.getOrNull() ?: return false
    if (vault_identity_public != identity_public_b64.trim()) return false

    if (vault_json.text("ratchet_signed_prekey").isBlank()) return false
    if (vault_json.text("ratchet_signed_prekey_public") != signed_prekey_public_b64.trim()) return false

    if (pq_identity_public_b64.isNullOrBlank()) return true
    if (vault_json.text("ratchet_pq_identity_public") != pq_identity_public_b64.trim()) return false
    return vault_json.text("ratchet_pq_identity_key").isNotBlank() ||
        vault_json.text("ratchet_pq_identity_seed").isNotBlank()
}
