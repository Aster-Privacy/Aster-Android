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

import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore
import org.json.JSONObject

data class VaultRatchetKeys(
    val identity_jwk: String? = null,
    val identity_public_b64: String? = null,
    val signed_prekey_jwk: String? = null,
    val signed_prekey_public_b64: String? = null,
    val pq_identity_secret_b64: String? = null,
    val pq_identity_public_b64: String? = null,
    val pq_identity_seed_b64: String? = null,
    val previous_keys_json: String? = null,
) {
    val has_identity: Boolean get() = identity_jwk != null
    val has_signed_prekey: Boolean get() = signed_prekey_jwk != null && signed_prekey_public_b64 != null
    val has_pq_identity: Boolean get() = pq_identity_secret_b64 != null || pq_identity_seed_b64 != null
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
    pq_identity_seed_b64 = vault_obj.optional_field("ratchet_pq_identity_seed"),
    previous_keys_json = vault_obj.optJSONArray("ratchet_previous_keys")?.toString(),
)

fun expand_pq_identity_secret(seed_b64: String): String? = runCatching {
    val pair = RatchetCrypto.ml_kem_768_keypair_from_seed(RatchetCrypto.b64_decode(seed_b64))
    val secret = RatchetCrypto.b64_encode(pair.secret_key)
    pair.secret_key.fill(0)
    pair.seed.fill(0)
    secret
}.getOrNull()

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
        val secret_b64 = keys.pq_identity_secret_b64
            ?: keys.pq_identity_seed_b64?.let { expand_pq_identity_secret(it) }
        if (secret_b64 != null) {
            session_key_store.put_ratchet_pq_identity(
                secret_b64,
                keys.pq_identity_public_b64,
            )
        }
    }
    session_key_store.put_ratchet_previous_keys(keys.previous_keys_json)
}
