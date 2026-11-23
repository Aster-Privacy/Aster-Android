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

package org.astermail.android.mail

import org.json.JSONArray
import org.json.JSONObject

fun inbound_key_sets_from(
    identity_jwk: String?,
    pq_identity_secret_b64: String?,
    previous_keys_json: String?,
    expand_seed: (String) -> String?,
): List<InboundRatchetKeySet> {
    val key_sets = mutableListOf<InboundRatchetKeySet>()
    if (!identity_jwk.isNullOrBlank()) {
        key_sets.add(InboundRatchetKeySet(identity_jwk, pq_identity_secret_b64?.ifBlank { null }))
    }
    if (previous_keys_json.isNullOrBlank()) return key_sets
    val entries = runCatching { JSONArray(previous_keys_json) }.getOrNull() ?: return key_sets
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        val previous_jwk = entry.optString("ratchet_identity_key", "")
        val previous_pq_secret = pq_secret_of(entry, expand_seed)
        if (previous_jwk.isBlank() && previous_pq_secret == null) continue
        key_sets.add(InboundRatchetKeySet(previous_jwk, previous_pq_secret))
    }
    return key_sets
}

fun vault_inbound_key_sets(
    vault_obj: JSONObject,
    expand_seed: (String) -> String?,
): List<InboundRatchetKeySet> = inbound_key_sets_from(
    identity_jwk = vault_obj.optString("ratchet_identity_key", ""),
    pq_identity_secret_b64 = pq_secret_of(vault_obj, expand_seed),
    previous_keys_json = vault_obj.optJSONArray("ratchet_previous_keys")?.toString(),
    expand_seed = expand_seed,
)

fun fallback_inbound_key_sets(
    local: List<InboundRatchetKeySet>,
    vault: List<InboundRatchetKeySet>,
): List<InboundRatchetKeySet> {
    val missing = vault.filterNot { it in local }
    return if (missing.isEmpty()) emptyList() else missing + local
}

private fun pq_secret_of(entry: JSONObject, expand_seed: (String) -> String?): String? =
    entry.optString("ratchet_pq_identity_key", "")
        .ifBlank {
            entry.optString("ratchet_pq_identity_seed", "")
                .takeIf { it.isNotBlank() }
                ?.let(expand_seed)
                .orEmpty()
        }
        .ifBlank { null }
