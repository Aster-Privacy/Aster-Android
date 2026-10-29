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


package org.astermail.android.auth

import org.json.JSONArray
import org.json.JSONObject

const val MAX_RATCHET_PREVIOUS_KEYS = 32

data class RecoveredKeyCommitResult(
    val written: Boolean,
    val dropped_keks: Int = 0,
)

val RECOVERED_KEYS_NOT_WRITTEN = RecoveredKeyCommitResult(written = false)

fun harvest_storage_keks(old_vault: JSONObject, derived_old_key: String?): List<String> {
    val harvested = mutableListOf<String>()

    old_vault.optString("data_kek", "").takeIf { it.isNotBlank() }?.let { harvested.add(it) }

    old_vault.optJSONArray("legacy_keks")?.let { array ->
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.optString("k", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { harvested.add(it) }
        }
    }

    if (vault_storage_kdf_version(old_vault) <= SUPPORTED_STORAGE_KDF_VERSION) {
        derived_old_key?.takeIf { it.isNotBlank() }?.let { harvested.add(it) }
    }

    return harvested
}

class MergedLegacyKeks(val keks: JSONArray, val dropped: Int)

fun merge_legacy_keks(existing: JSONArray?, recovered: List<String>, added_at: String): JSONArray =
    merge_legacy_keks_reporting(existing, recovered, added_at).keks

fun merge_legacy_keks_reporting(
    existing: JSONArray?,
    recovered: List<String>,
    added_at: String,
): MergedLegacyKeks {
    val merged = JSONArray()
    val seen = mutableSetOf<String>()
    var dropped = 0

    if (existing != null) {
        for (i in 0 until existing.length()) {
            val entry = existing.optJSONObject(i) ?: continue
            val kek = entry.optString("k", "")
            if (kek.isBlank() || !seen.add(kek)) continue
            if (merged.length() >= MAX_LEGACY_KEKS) {
                dropped += 1
                continue
            }
            merged.put(entry)
        }
    }

    for (kek in recovered) {
        if (kek.isBlank() || !seen.add(kek)) continue
        if (merged.length() >= MAX_LEGACY_KEKS) {
            dropped += 1
            continue
        }
        merged.put(JSONObject().put("k", kek).put("added_at", added_at))
    }

    return MergedLegacyKeks(merged, dropped)
}

fun retain_previous_ratchet_keys(vault: JSONObject): List<JSONObject> {
    val retained = mutableListOf<JSONObject>()
    val identity_key = vault.optString("ratchet_identity_key", "")
    val identity_public = vault.optString("ratchet_identity_public", "")
    val signed_prekey = vault.optString("ratchet_signed_prekey", "")
    val signed_prekey_public = vault.optString("ratchet_signed_prekey_public", "")

    if (
        identity_key.isNotBlank() &&
        identity_public.isNotBlank() &&
        signed_prekey.isNotBlank() &&
        signed_prekey_public.isNotBlank()
    ) {
        val set = JSONObject()
            .put("ratchet_identity_key", identity_key)
            .put("ratchet_identity_public", identity_public)
            .put("ratchet_signed_prekey", signed_prekey)
            .put("ratchet_signed_prekey_public", signed_prekey_public)

        vault.optString("ratchet_pq_identity_key", "").takeIf { it.isNotBlank() }
            ?.let { set.put("ratchet_pq_identity_key", it) }
        vault.optString("ratchet_pq_identity_public", "").takeIf { it.isNotBlank() }
            ?.let { set.put("ratchet_pq_identity_public", it) }
        vault.optString("ratchet_pq_identity_seed", "").takeIf { it.isNotBlank() }
            ?.let { set.put("ratchet_pq_identity_seed", it) }

        retained.add(set)
    }

    vault.optJSONArray("ratchet_previous_keys")?.let { array ->
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { retained.add(it) }
        }
    }

    return retained
}

fun merge_previous_ratchet_keys(existing: JSONArray?, recovered: List<JSONObject>): JSONArray {
    val merged = JSONArray()
    val seen = mutableSetOf<String>()

    fun absorb(entry: JSONObject?) {
        val public_key = entry?.optString("ratchet_identity_public", "").orEmpty()
        if (public_key.isBlank() || !seen.add(public_key)) return
        if (merged.length() < MAX_RATCHET_PREVIOUS_KEYS) merged.put(entry)
    }

    if (existing != null) {
        for (i in 0 until existing.length()) absorb(existing.optJSONObject(i))
    }
    for (entry in recovered) absorb(entry)

    return merged
}
