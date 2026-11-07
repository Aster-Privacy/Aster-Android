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
import java.time.Instant

const val MASTER_KEY_VAULT_FORMAT = 2

fun stored_vault_format(vault_obj: JSONObject): Int = maxOf(
    vault_obj.optInt("vault_format", 1),
    if (vault_obj.optString("data_kek", "").isNotBlank()) MASTER_KEY_VAULT_FORMAT else 1,
)
const val SUPPORTED_STORAGE_KDF_VERSION = 1
const val ROTATABLE_STORAGE_KDF_VERSION = 2
const val MAX_PRIOR_STORAGE_KEYS = 8
const val STORAGE_REKEY_PENDING = "pending"
const val STORAGE_REKEY_DONE = "done"

class UnsupportedVaultKdfException(val kdf_version: Int) : Exception(
    "vault uses storage kdf version $kdf_version",
)

fun vault_storage_kdf_version(vault_obj: JSONObject): Int =
    vault_obj.optInt("kdf_version", SUPPORTED_STORAGE_KDF_VERSION)
        .coerceAtLeast(SUPPORTED_STORAGE_KDF_VERSION)

fun install_signup_data_kek(
    vault_obj: JSONObject,
    data_kek_b64: String,
    now: () -> String = { Instant.now().toString() },
) {
    vault_obj.put("data_kek", data_kek_b64)
    vault_obj.put("data_kek_random", true)
    vault_obj.put("vault_format", MASTER_KEY_VAULT_FORMAT)
    if (vault_obj.optString("mk_created_at", "").isBlank()) {
        vault_obj.put("mk_created_at", now())
    }
}

fun vault_legacy_kek_values(vault_obj: JSONObject): List<String> {
    val array = vault_obj.optJSONArray("legacy_keks") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.optString("k", "")?.takeIf { it.isNotBlank() }
    }
}

fun constant_time_equal(a: ByteArray, b: ByteArray): Boolean =
    java.security.MessageDigest.isEqual(a, b)

fun has_pending_storage_rekey(vault_obj: JSONObject): Boolean =
    vault_obj.optJSONObject("storage_rekey")?.optString("state", "") == STORAGE_REKEY_PENDING

fun decode_vault_storage_key(vault_obj: JSONObject, decode: (String) -> ByteArray): ByteArray? {
    val encoded = vault_obj.optString("data_kek", "")
    if (encoded.isBlank()) return null
    val raw = runCatching { decode(encoded) }.getOrNull() ?: return null
    if (raw.size == 32) return raw
    raw.fill(0)
    return null
}

fun is_weak_storage_vault(
    vault_obj: JSONObject,
    password_keys: List<ByteArray>,
    decode: (String) -> ByteArray,
): Boolean {
    if (vault_obj.optString("data_kek", "").isBlank()) return true
    if (vault_obj.opt("data_kek_random") == true) return false
    val stored = decode_vault_storage_key(vault_obj, decode) ?: return true
    var matches = false
    for (candidate in password_keys) {
        if (candidate.size == stored.size && constant_time_equal(stored, candidate)) matches = true
    }
    stored.fill(0)
    return matches
}

fun collect_prior_storage_keys(
    vault_obj: JSONObject,
    password_keys: List<ByteArray>,
    decode: (String) -> ByteArray,
): List<ByteArray> {
    val prior = ArrayList<ByteArray>()
    decode_vault_storage_key(vault_obj, decode)?.let { prior.add(it) }
    password_keys.filter { it.size == 32 }.forEach { prior.add(it.copyOf()) }
    return prior
}

fun install_random_storage_key(
    vault_obj: JSONObject,
    prior_keys: List<ByteArray>,
    encode: (ByteArray) -> String,
    generate: () -> ByteArray,
    now: () -> String = { Instant.now().toString() },
): ByteArray {
    val fresh = generate()
    require(fresh.size == 32) { "data key must be 32 bytes" }
    val fresh_encoded = encode(fresh)
    val stamp = now()
    val prior_encoded = ArrayList<String>()
    for (raw in prior_keys) {
        if (raw.size != 32) continue
        val encoded = encode(raw)
        if (encoded != fresh_encoded && encoded !in prior_encoded) prior_encoded.add(encoded)
    }
    val carried = if (has_pending_storage_rekey(vault_obj)) {
        vault_obj.optJSONObject("storage_rekey")?.optJSONArray("prior_keys")
    } else {
        null
    }
    val merged_prior = ArrayList(prior_encoded)
    for (i in 0 until (carried?.length() ?: 0)) {
        val encoded = carried?.optString(i, "").orEmpty()
        if (encoded.isNotBlank() && encoded != fresh_encoded && encoded !in merged_prior) merged_prior.add(encoded)
    }
    vault_obj.put("data_kek", fresh_encoded)
    vault_obj.put("data_kek_random", true)
    vault_obj.put("vault_format", MASTER_KEY_VAULT_FORMAT)
    vault_obj.put("mk_created_at", stamp)
    vault_obj.put(
        "legacy_keks",
        prepend_keks_keeping_all(vault_obj.optJSONArray("legacy_keks"), prior_encoded, stamp, fresh_encoded),
    )
    if (merged_prior.isNotEmpty()) {
        val prior_array = JSONArray()
        merged_prior.take(MAX_PRIOR_STORAGE_KEYS).forEach { prior_array.put(it) }
        vault_obj.put(
            "storage_rekey",
            JSONObject()
                .put("state", STORAGE_REKEY_PENDING)
                .put("started_at", stamp)
                .put("prior_keys", prior_array),
        )
    }
    return fresh
}

fun prepend_keks_keeping_all(
    existing: JSONArray?,
    encoded_keys: List<String>,
    added_at: String,
    keep: String? = null,
): JSONArray {
    val updated = JSONArray()
    val seen = mutableSetOf<String>()
    for (encoded in encoded_keys) {
        if (encoded.isBlank() || encoded == keep || !seen.add(encoded)) continue
        updated.put(JSONObject().put("k", encoded).put("added_at", added_at))
    }
    for (i in 0 until (existing?.length() ?: 0)) {
        val entry = existing?.optJSONObject(i) ?: continue
        val k = entry.optString("k", "")
        if (k.isBlank() || k == keep || !seen.add(k)) continue
        updated.put(entry)
    }
    return updated
}

fun ensure_master_key_vault(
    vault_obj: JSONObject,
    derive_storage_key: () -> ByteArray,
    encode: (ByteArray) -> String,
    decode: (String) -> ByteArray,
    now: () -> String = { Instant.now().toString() },
    generate: () -> ByteArray,
    extra_password_keys: () -> List<ByteArray> = { emptyList() },
): ByteArray {
    val existing = decode_vault_storage_key(vault_obj, decode)
    if (existing != null && vault_obj.opt("data_kek_random") == true) {
        vault_obj.put("vault_format", maxOf(vault_obj.optInt("vault_format", 1), MASTER_KEY_VAULT_FORMAT))
        return existing
    }

    val kdf_version = vault_storage_kdf_version(vault_obj)
    if (existing == null && kdf_version > ROTATABLE_STORAGE_KDF_VERSION) {
        throw UnsupportedVaultKdfException(kdf_version)
    }

    val password_keys = ArrayList<ByteArray>()
    try {
        if (existing == null || kdf_version <= ROTATABLE_STORAGE_KDF_VERSION) {
            password_keys.addAll(extra_password_keys())
            password_keys.add(derive_storage_key())
        }
        if (existing != null && !is_weak_storage_vault(vault_obj, password_keys, decode)) {
            vault_obj.put("vault_format", maxOf(vault_obj.optInt("vault_format", 1), MASTER_KEY_VAULT_FORMAT))
            return existing
        }
        existing?.fill(0)
        val prior = collect_prior_storage_keys(vault_obj, password_keys, decode)
        try {
            return install_random_storage_key(vault_obj, prior, encode, generate, now)
        } finally {
            prior.forEach { it.fill(0) }
        }
    } finally {
        password_keys.forEach { it.fill(0) }
    }
}

fun retain_password_storage_key(
    vault_obj: JSONObject,
    outgoing_key: ByteArray,
    encode: (ByteArray) -> String,
    now: () -> String = { Instant.now().toString() },
) {
    if (outgoing_key.size != 32) return
    val encoded = encode(outgoing_key)
    val data_kek = vault_obj.optString("data_kek", "")
    if (encoded == data_kek) return
    vault_obj.put(
        "legacy_keks",
        prepend_keks_keeping_all(vault_obj.optJSONArray("legacy_keks"), listOf(encoded), now(), data_kek.ifBlank { null }),
    )
}
