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

package org.astermail.android.crypto.storage_rotation

import org.astermail.android.storage.RetiredStorageKeys
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

const val RETIRED_STORAGE_KEYS_FIELD = "retired_storage_key_hashes"

private const val MAX_RETIRED_HASHES = 256

private fun is_retired_hash(value: String): Boolean {
    if (value.length != 44) return false
    val raw = runCatching { Base64.getDecoder().decode(value) }.getOrNull() ?: return false
    return raw.size == RetiredStorageKeys.key_length
}

fun vault_retired_hashes(vault: JSONObject): List<String> {
    val array = vault.optJSONArray(RETIRED_STORAGE_KEYS_FIELD) ?: return emptyList()
    return (0 until array.length())
        .mapNotNull { array.opt(it) as? String }
        .filter(::is_retired_hash)
        .distinct()
        .take(MAX_RETIRED_HASHES)
}

fun merge_retired_marker(vault: JSONObject, sources: List<Collection<String>>): Boolean {
    val current = vault_retired_hashes(vault)
    val merged = (current + sources.flatten().filter(::is_retired_hash)).distinct().take(MAX_RETIRED_HASHES)
    if (merged.isEmpty()) return false
    val stored = vault.optJSONArray(RETIRED_STORAGE_KEYS_FIELD)
    if (merged == current && stored != null && stored.length() == current.size) return false
    vault.put(RETIRED_STORAGE_KEYS_FIELD, JSONArray(merged))
    return true
}

fun vault_lacks_retired_marker(vault: JSONObject, local_hashes: Collection<String>): Boolean {
    if (local_hashes.isEmpty()) return false
    val known = vault_retired_hashes(vault).toSet()
    return local_hashes.any { is_retired_hash(it) && it !in known }
}

fun absorb_retired_marker(store: SessionKeyStore, vault: JSONObject): Boolean {
    val hashes = vault_retired_hashes(vault)
    if (hashes.isEmpty()) return false
    val known = store.retired_storage_key_hashes().toSet()
    if (store.legacy_storage_keys_retired() && known.containsAll(hashes)) return false
    return store.retire_legacy_storage_keys(hashes)
}
