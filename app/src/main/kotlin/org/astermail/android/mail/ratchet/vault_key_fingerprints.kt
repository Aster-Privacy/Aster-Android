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

package org.astermail.android.mail.ratchet

import android.util.Base64
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.json.JSONObject

private const val max_vault_key_fingerprints = 128

fun collect_vault_key_fingerprints(vault_json: JSONObject): List<String> {
    val public_keys = mutableListOf<ByteArray>()

    val identity_public = identity_public_raw(vault_json)
    if (identity_public == null) {
        if (vault_json.optString("ratchet_identity_key", "").isNotBlank()) return emptyList()
    } else {
        public_keys.add(identity_public)
    }
    decode_public(vault_json.optString("ratchet_pq_identity_public", ""))?.let { public_keys.add(it) }

    val previous = vault_json.optJSONArray("ratchet_previous_keys")
    if (previous != null) {
        for (index in 0 until previous.length()) {
            val entry = previous.optJSONObject(index) ?: continue
            val previous_public = identity_public_raw(entry)
            if (previous_public == null) {
                if (entry.optString("ratchet_identity_key", "").isNotBlank()) return emptyList()
            } else {
                public_keys.add(previous_public)
            }
            decode_public(entry.optString("ratchet_pq_identity_public", ""))?.let { public_keys.add(it) }
        }
    }

    val fingerprints = mutableListOf<String>()
    for (public_key in public_keys) {
        if (fingerprints.size >= max_vault_key_fingerprints) break
        val value = Base64.encodeToString(RatchetCrypto.sha256(public_key), Base64.NO_WRAP)
        if (!fingerprints.contains(value)) fingerprints.add(value)
    }

    return fingerprints
}

private fun identity_public_raw(source: JSONObject): ByteArray? {
    decode_public(source.optString("ratchet_identity_public", ""))?.let { return it }
    val jwk = source.optString("ratchet_identity_key", "")
    if (jwk.isBlank()) return null

    return runCatching { RatchetCrypto.p256_public_raw_from_private_jwk(jwk) }.getOrNull()
}

private fun decode_public(value: String): ByteArray? {
    if (value.isBlank()) return null

    return runCatching { Base64.decode(value, Base64.DEFAULT) }.getOrNull()?.takeIf { it.isNotEmpty() }
}
