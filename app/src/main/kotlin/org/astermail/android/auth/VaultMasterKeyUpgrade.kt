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
const val MAX_LEGACY_KEKS = 16
const val SUPPORTED_STORAGE_KDF_VERSION = 1

class UnsupportedVaultKdfException(val kdf_version: Int) : Exception(
    "vault uses storage kdf version $kdf_version",
)

fun vault_storage_kdf_version(vault_obj: JSONObject): Int =
    vault_obj.optInt("kdf_version", SUPPORTED_STORAGE_KDF_VERSION)
        .coerceAtLeast(SUPPORTED_STORAGE_KDF_VERSION)

fun ensure_master_key_vault(
    vault_obj: JSONObject,
    derive_storage_key: () -> ByteArray,
    encode: (ByteArray) -> String,
    decode: (String) -> ByteArray,
    now: () -> String = { Instant.now().toString() },
): ByteArray {
    val existing = vault_obj.optString("data_kek", "")
    if (existing.isNotBlank()) {
        val decoded = runCatching { decode(existing) }.getOrNull()
        if (decoded != null && decoded.size == 32) {
            vault_obj.put(
                "vault_format",
                maxOf(vault_obj.optInt("vault_format", 1), MASTER_KEY_VAULT_FORMAT),
            )
            return decoded
        }
    }

    val kdf_version = vault_storage_kdf_version(vault_obj)
    if (kdf_version > SUPPORTED_STORAGE_KDF_VERSION) {
        throw UnsupportedVaultKdfException(kdf_version)
    }

    val storage_key = derive_storage_key()
    val encoded = encode(storage_key)

    vault_obj.put("data_kek", encoded)
    vault_obj.put("vault_format", MASTER_KEY_VAULT_FORMAT)
    if (vault_obj.optString("mk_created_at", "").isBlank()) {
        vault_obj.put("mk_created_at", now())
    }
    vault_obj.put(
        "legacy_keks",
        prepend_legacy_kek(vault_obj.optJSONArray("legacy_keks"), encoded, now()),
    )

    return storage_key
}

fun prepend_legacy_kek(
    existing: JSONArray?,
    encoded_key: String,
    added_at: String,
): JSONArray {
    val updated = JSONArray()
    updated.put(JSONObject().put("k", encoded_key).put("added_at", added_at))
    for (i in 0 until (existing?.length() ?: 0)) {
        if (updated.length() >= MAX_LEGACY_KEKS) break
        val entry = existing?.optJSONObject(i) ?: continue
        if (entry.optString("k", "") == encoded_key) continue
        updated.put(entry)
    }
    return updated
}
