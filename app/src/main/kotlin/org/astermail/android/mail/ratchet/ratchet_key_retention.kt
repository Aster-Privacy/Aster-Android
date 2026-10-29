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

const val max_retained_ratchet_key_sets = 64

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

private fun retained_set_tag(entry: JSONObject): String? {
    val identity = entry.text("ratchet_identity_key")
    val pq = entry.text("ratchet_pq_identity_key").ifBlank { entry.text("ratchet_pq_identity_seed") }
    if (identity.isBlank() && pq.isBlank()) return null
    return "$identity|$pq"
}

fun merge_retained_ratchet_keys(vararg sources: String?): String? {
    val merged = mutableListOf<JSONObject>()
    val tags = mutableSetOf<String>()
    for (source in sources) {
        if (source.isNullOrBlank()) continue
        val entries = runCatching { JSONArray(source) }.getOrNull() ?: continue
        for (index in 0 until entries.length()) {
            val entry = entries.optJSONObject(index) ?: continue
            val tag = retained_set_tag(entry) ?: continue
            if (tags.add(tag)) {
                merged.add(JSONObject(entry.toString()))
                continue
            }
            merged.firstOrNull { retained_set_tag(it) == tag }?.let { existing ->
                for (field in retained_key_fields) {
                    if (existing.text(field).isBlank() && entry.text(field).isNotBlank()) {
                        existing.put(field, entry.text(field))
                    }
                }
            }
        }
    }
    if (merged.isEmpty()) return null
    val result = JSONArray()
    merged.take(max_retained_ratchet_key_sets).forEach { result.put(it) }
    return result.toString()
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

    val merged = merge_retained_ratchet_keys(
        JSONArray().put(entry).toString(),
        session_key_store.get_ratchet_previous_keys_json(),
    )
    session_key_store.put_ratchet_previous_keys(merged)
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
