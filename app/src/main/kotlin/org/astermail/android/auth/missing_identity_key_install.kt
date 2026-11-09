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

import java.security.MessageDigest
import java.util.Base64
import org.astermail.android.crypto.AccountKey
import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.json.JSONArray
import org.json.JSONObject

private const val PGP_PRIVATE_KEY_PREFIX = "-----BEGIN PGP PRIVATE KEY"
private const val MISSING_IDENTITY_KEY_MATERIAL = "undefined"
private const val MAX_INSTALL_LEGACY_KEKS = 64

val MISSING_IDENTITY_KEK_CONTEXTS: List<String> = AccountKey.DATA_CONTEXTS + "astermail-calendar-v2"

fun is_pgp_private_key(value: String?): Boolean =
    value?.trimStart()?.startsWith(PGP_PRIVATE_KEY_PREFIX) == true

fun vault_holds_pgp_identity_key(vault: JSONObject): Boolean =
    is_pgp_private_key(vault_identity_key(vault))

fun missing_identity_keks(): List<String> =
    MISSING_IDENTITY_KEK_CONTEXTS.map { context ->
        val material = (MISSING_IDENTITY_KEY_MATERIAL + context).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(material)
        try {
            Base64.getEncoder().encodeToString(digest)
        } finally {
            digest.fill(0)
        }
    }

private fun prepend_install_kek(existing: JSONArray?, encoded_key: String, added_at: String): JSONArray {
    val updated = JSONArray()
    updated.put(JSONObject().put("k", encoded_key).put("added_at", added_at))
    for (i in 0 until (existing?.length() ?: 0)) {
        if (updated.length() >= MAX_INSTALL_LEGACY_KEKS) break
        val entry = existing?.optJSONObject(i) ?: continue
        if (entry.optString("k", "") == encoded_key) continue
        updated.put(entry)
    }
    return updated
}

fun install_identity_key_into_vault(vault: JSONObject, identity_key: String, added_at: String) {
    var legacy_keks = vault.optJSONArray("legacy_keks")
    for (kek in missing_identity_keks()) {
        legacy_keks = prepend_install_kek(legacy_keks, kek, added_at)
    }
    vault.put("legacy_keks", legacy_keks)
    replace_vault_identity_key(vault, identity_key)
}

class MissingIdentityKeyEdit(
    private val generate_identity_key: () -> String?,
    private val now: () -> String,
) {
    private var generated: String? = null

    var installed_key: String? = null
        private set

    fun apply(vault: JSONObject): VaultEdit {
        installed_key = null
        if (vault_holds_pgp_identity_key(vault)) return VaultEdit.UNCHANGED
        val identity_key = generated
            ?: generate_identity_key()?.takeIf { is_pgp_private_key(it) }?.also { generated = it }
            ?: return VaultEdit.ABORT
        install_identity_key_into_vault(vault, identity_key, now())
        installed_key = identity_key
        return VaultEdit.CHANGED
    }
}
