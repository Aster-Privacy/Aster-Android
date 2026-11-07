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

import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.auth.STORAGE_REKEY_DONE
import org.astermail.android.auth.STORAGE_REKEY_PENDING
import org.astermail.android.auth.collect_prior_storage_keys
import org.astermail.android.auth.install_random_storage_key
import org.astermail.android.auth.is_weak_storage_vault
import org.json.JSONObject

interface StorageVaultCodec {
    fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject?
    fun encrypt(vault: JSONObject): SealedVault?
    fun decrypt_at(encrypted_vault: String, vault_nonce: String, iterations: Int): JSONObject? =
        decrypt(encrypted_vault, vault_nonce)
    fun encrypt_at(vault: JSONObject, iterations: Int): SealedVault? = encrypt(vault)
    fun password_keys(vault: JSONObject): List<ByteArray>
    fun generate_key(): ByteArray
    fun fingerprints(vault: JSONObject): List<String>
}

sealed class StorageVaultOutcome {
    class Rotated(val vault: JSONObject) : StorageVaultOutcome()
    class Adopted(val vault: JSONObject) : StorageVaultOutcome()
    class Current(val vault: JSONObject) : StorageVaultOutcome()
    class Skipped(val reason: String) : StorageVaultOutcome()
}

enum class StorageRekeyFinish { DONE, SUPERSEDED, FAILED }

class StorageVaultRotator(
    api: ResealApi,
    private val codec: StorageVaultCodec,
    user_id: () -> String?,
    private val lock: Mutex,
    on_vault_changed: suspend (JSONObject, SealedVault) -> Unit,
    private val now: () -> String = { Instant.now().toString() },
    local_keys: () -> LocalStorageKeys = { LocalStorageKeys.NONE },
    upgrade_on_rotation: Boolean = false,
) {
    private val updater =
        VaultUpdater(api, codec, user_id, lock, on_vault_changed, now, local_keys, upgrade_on_rotation)

    suspend fun fetch_snapshot(): ServerVaultSnapshot? = updater.fetch_snapshot()

    private fun skip_reason(reason: String): String = when (reason) {
        "encrypt_failed", "roundtrip_failed" -> "write_failed"
        else -> reason
    }

    private suspend fun rotate_locked(): StorageVaultOutcome {
        var attempts = 0
        val outcome = updater.update_locked(allow_storage_key_change = true) { draft ->
            attempts += 1
            val vault = draft.vault
            val password_keys = runCatching { codec.password_keys(vault) }.getOrDefault(emptyList())
            try {
                if (!is_weak_storage_vault(vault, password_keys, ::decode_required)) {
                    VaultEdit.UNCHANGED
                } else {
                    val prior = collect_prior_storage_keys(vault, password_keys, ::decode_required)
                    try {
                        install_random_storage_key(vault, prior, ::encode_b64, codec::generate_key, now).fill(0)
                    } finally {
                        prior.forEach { it.fill(0) }
                    }
                    VaultEdit.CHANGED
                }
            } finally {
                password_keys.forEach { it.fill(0) }
            }
        }
        return when (outcome) {
            is VaultUpdateOutcome.Written -> StorageVaultOutcome.Rotated(outcome.vault)
            is VaultUpdateOutcome.Unchanged ->
                if (attempts > 1) StorageVaultOutcome.Adopted(outcome.vault) else StorageVaultOutcome.Current(outcome.vault)
            VaultUpdateOutcome.Aborted -> StorageVaultOutcome.Skipped("aborted")
            is VaultUpdateOutcome.Failed -> StorageVaultOutcome.Skipped(skip_reason(outcome.reason))
        }
    }

    suspend fun ensure_random_storage_key(): StorageVaultOutcome = lock.withLock { rotate_locked() }

    private suspend fun finish_locked(expected_data_kek: String): StorageRekeyFinish {
        var finish = StorageRekeyFinish.DONE
        val outcome = updater.update_locked(allow_storage_key_change = true) { draft ->
            val vault = draft.vault
            val rekey = vault.optJSONObject("storage_rekey")
            if (vault.optString("data_kek", "") != expected_data_kek) {
                finish = StorageRekeyFinish.SUPERSEDED
                VaultEdit.UNCHANGED
            } else if (rekey == null || rekey.optString("state", "") != STORAGE_REKEY_PENDING) {
                finish = StorageRekeyFinish.DONE
                VaultEdit.UNCHANGED
            } else {
                val finished = JSONObject().put("state", STORAGE_REKEY_DONE)
                rekey.opt("started_at")?.let { finished.put("started_at", it) }
                finished.put("done_at", now())
                vault.put("storage_rekey", finished)
                finish = StorageRekeyFinish.DONE
                VaultEdit.CHANGED
            }
        }
        return when (outcome) {
            is VaultUpdateOutcome.Written, is VaultUpdateOutcome.Unchanged -> finish
            else -> StorageRekeyFinish.FAILED
        }
    }

    suspend fun mark_storage_rekey_done(expected_data_kek: String): StorageRekeyFinish =
        lock.withLock { finish_locked(expected_data_kek) }

    suspend fun server_storage_key_matches(expected_data_kek: String): Boolean {
        val draft = updater.latest() ?: return false
        return draft.vault.optString("data_kek", "") == expected_data_kek && draft.vault.opt("data_kek_random") == true
    }
}

private fun decode_required(value: String): ByteArray =
    decode_b64(value) ?: throw IllegalArgumentException("invalid base64")
