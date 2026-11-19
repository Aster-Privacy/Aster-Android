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
import org.astermail.android.crypto.storage_rotation.VaultEdit
import org.astermail.android.crypto.storage_rotation.VaultUpdateOutcome
import org.astermail.android.crypto.storage_rotation.VaultUpdater
import org.astermail.android.crypto.storage_rotation.decode_b64
import org.astermail.android.crypto.storage_rotation.encode_b64
import org.json.JSONObject

const val VAULT_PREKEY_SEAL_FIELD = "prekey_seal_key"
const val VAULT_PREKEY_SEAL_MIGRATED_FIELD = "prekey_seal_migrated"

class AdoptedPrekeySeal(val key: ByteArray, val migrated: Boolean)

fun vault_prekey_seal_key(vault: JSONObject): ByteArray? {
    val raw = vault.optString(VAULT_PREKEY_SEAL_FIELD, "").trim()
    if (raw.isEmpty()) return null
    val bytes = decode_b64(raw) ?: return null
    return bytes.takeIf { it.size == PREKEY_SEAL_KEY_LEN }
}

fun vault_prekey_seal_migrated(vault: JSONObject): Boolean =
    vault.optBoolean(VAULT_PREKEY_SEAL_MIGRATED_FIELD, false)

suspend fun ensure_vault_prekey_seal(
    updater: VaultUpdater,
    generate: () -> ByteArray = { RatchetCrypto.random_bytes(PREKEY_SEAL_KEY_LEN) },
): AdoptedPrekeySeal? {
    val created = updater.update { draft ->
        vault_prekey_seal_key(draft.vault)?.let { existing ->
            existing.fill(0)
            return@update VaultEdit.UNCHANGED
        }
        if (vault_prekey_seal_migrated(draft.vault)) return@update VaultEdit.ABORT
        val fresh = generate()
        try {
            if (fresh.size != PREKEY_SEAL_KEY_LEN) return@update VaultEdit.ABORT
            draft.vault.put(VAULT_PREKEY_SEAL_FIELD, encode_b64(fresh))
        } finally {
            fresh.fill(0)
        }
        VaultEdit.CHANGED
    }
    if (created is VaultUpdateOutcome.Failed || created is VaultUpdateOutcome.Aborted) return null
    val stored = updater.update { VaultEdit.UNCHANGED }
    val vault = when (stored) {
        is VaultUpdateOutcome.Unchanged -> stored.vault
        is VaultUpdateOutcome.Written -> stored.vault
        else -> return null
    }
    val key = vault_prekey_seal_key(vault) ?: return null
    return AdoptedPrekeySeal(key, vault_prekey_seal_migrated(vault))
}
