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

import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject

data class VaultRatchetKeys(
    val identity_jwk: String? = null,
    val identity_public_b64: String? = null,
    val signed_prekey_jwk: String? = null,
    val signed_prekey_public_b64: String? = null,
    val pq_identity_secret_b64: String? = null,
    val pq_identity_public_b64: String? = null,
    val previous_keys_json: String? = null,
) {
    val has_identity: Boolean get() = identity_jwk != null
    val has_signed_prekey: Boolean get() = signed_prekey_jwk != null && signed_prekey_public_b64 != null
    val has_pq_identity: Boolean get() = pq_identity_secret_b64 != null
}

private fun JSONObject.optional_field(name: String): String? =
    optString(name, "").takeIf { it.isNotBlank() }

fun parse_vault_ratchet_keys(vault_obj: JSONObject): VaultRatchetKeys = VaultRatchetKeys(
    identity_jwk = vault_obj.optional_field("ratchet_identity_key"),
    identity_public_b64 = vault_obj.optional_field("ratchet_identity_public"),
    signed_prekey_jwk = vault_obj.optional_field("ratchet_signed_prekey"),
    signed_prekey_public_b64 = vault_obj.optional_field("ratchet_signed_prekey_public"),
    pq_identity_secret_b64 = vault_obj.optional_field("ratchet_pq_identity_key"),
    pq_identity_public_b64 = vault_obj.optional_field("ratchet_pq_identity_public"),
    previous_keys_json = vault_obj.optJSONArray("ratchet_previous_keys")?.toString(),
)

fun apply_vault_ratchet_keys(keys: VaultRatchetKeys, session_key_store: SessionKeyStore) {
    if (keys.has_identity) {
        session_key_store.put_ratchet_keys(
            identity_jwk = keys.identity_jwk!!,
            identity_public_b64 = keys.identity_public_b64.orEmpty(),
            signed_prekey_jwk = keys.signed_prekey_jwk,
            signed_prekey_public_b64 = keys.signed_prekey_public_b64,
        )
    }
    if (keys.has_pq_identity) {
        session_key_store.put_ratchet_pq_identity(
            keys.pq_identity_secret_b64,
            keys.pq_identity_public_b64,
        )
    }
    session_key_store.put_ratchet_previous_keys(keys.previous_keys_json)
}
