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


data class RecoveredKeyCommitResult(
    val written: Boolean,
    val dropped_keks: Int = 0,
    val dropped_ratchet_keys: Int = 0,
    val roundtrip_failed: Boolean = false,
) {
    val dropped_any: Boolean get() = dropped_keks > 0 || dropped_ratchet_keys > 0
}

val RECOVERED_KEYS_NOT_WRITTEN = RecoveredKeyCommitResult(written = false)

val RECOVERED_KEYS_ROUNDTRIP_FAILED = RecoveredKeyCommitResult(written = false, roundtrip_failed = true)

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

class MergedRatchetKeys(val keys: JSONArray, val dropped: Int)

fun merge_previous_ratchet_keys(existing: JSONArray?, recovered: List<JSONObject>): JSONArray =
    merge_previous_ratchet_keys_reporting(existing, recovered).keys

private fun ratchet_entry_identity(entry: JSONObject): String? {
    fun text(name: String) = entry.optString(name, "").trim()
    val identity = text("ratchet_identity_public").ifBlank { text("ratchet_identity_key") }
    val pq = text("ratchet_pq_identity_public")
        .ifBlank { text("ratchet_pq_identity_key") }
        .ifBlank { text("ratchet_pq_identity_seed") }
    if (identity.isBlank() && pq.isBlank()) return null
    return listOf(identity, text("ratchet_signed_prekey_public"), pq).joinToString("|")
}

fun merge_previous_ratchet_keys_reporting(
    existing: JSONArray?,
    recovered: List<JSONObject>,
): MergedRatchetKeys {
    val merged = JSONArray()
    val seen = mutableSetOf<String>()

    fun absorb(entry: JSONObject?) {
        val identity = entry?.let(::ratchet_entry_identity) ?: return
        if (seen.add(identity)) merged.put(entry)
    }

    if (existing != null) {
        for (i in 0 until existing.length()) absorb(existing.optJSONObject(i))
    }
    for (entry in recovered) absorb(entry)

    return MergedRatchetKeys(merged, 0)
}
