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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.astermail.android.auth.MASTER_KEY_VAULT_FORMAT
import org.astermail.android.auth.STORAGE_REKEY_DONE
import org.astermail.android.auth.STORAGE_REKEY_PENDING
import org.astermail.android.auth.collect_prior_storage_keys
import org.astermail.android.auth.install_random_storage_key
import org.astermail.android.auth.is_weak_storage_vault
import org.astermail.android.auth.stored_vault_format
import org.json.JSONObject

const val VAULT_PATH = "/crypto/v1/keys/vault"
const val MAX_VAULT_CONFLICT_RETRIES = 3
const val VAULT_CHANGED_CODE = "VAULT_CHANGED"
private const val CLIENT_UPGRADE_REQUIRED_CODE = "CLIENT_UPGRADE_REQUIRED"

data class ServerVaultSnapshot(
    val encrypted_vault: String,
    val vault_nonce: String,
    val vault_sha256: String,
)

data class SealedVault(val encrypted_vault: String, val vault_nonce: String)

interface StorageVaultCodec {
    fun decrypt(encrypted_vault: String, vault_nonce: String): JSONObject?
    fun encrypt(vault: JSONObject): SealedVault?
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

private enum class CasWrite { WRITTEN, CONFLICT, FAILED }

class StorageVaultRotator(
    private val api: ResealApi,
    private val codec: StorageVaultCodec,
    private val user_id: () -> String?,
    private val lock: Mutex,
    private val on_vault_changed: suspend (JSONObject, SealedVault) -> Unit,
    private val now: () -> String = { Instant.now().toString() },
) {

    suspend fun fetch_snapshot(): ServerVaultSnapshot? {
        val reply = api.get(VAULT_PATH)
        if (reply.error) return null
        val data = reply.data?.as_object() ?: return null
        val encrypted_vault = data.str("encrypted_vault")
        val vault_nonce = data.str("vault_nonce")
        val vault_sha256 = data.str("vault_sha256")
        if (encrypted_vault.isNullOrEmpty() || vault_nonce.isNullOrEmpty() || vault_sha256.isNullOrEmpty()) return null
        return ServerVaultSnapshot(encrypted_vault, vault_nonce, vault_sha256)
    }

    private fun decrypt_snapshot(snapshot: ServerVaultSnapshot): JSONObject? =
        runCatching { codec.decrypt(snapshot.encrypted_vault, snapshot.vault_nonce) }.getOrNull()

    private fun rekey_state(vault: JSONObject): String? =
        vault.optJSONObject("storage_rekey")?.optString("state", "")?.takeIf { it.isNotEmpty() }

    private fun same_key_fields(a: JSONObject, b: JSONObject): Boolean =
        a.optString("identity_key", "") == b.optString("identity_key", "") &&
            a.optString("data_kek", "") == b.optString("data_kek", "") &&
            (a.opt("data_kek_random") == true) == (b.opt("data_kek_random") == true) &&
            rekey_state(a) == rekey_state(b)

    private suspend fun adopt(vault: JSONObject, snapshot: ServerVaultSnapshot) {
        on_vault_changed(vault, SealedVault(snapshot.encrypted_vault, snapshot.vault_nonce))
    }

    private suspend fun write_with_cas(next: JSONObject, snapshot: ServerVaultSnapshot, user: String): CasWrite {
        val sealed = runCatching { codec.encrypt(next) }.getOrNull() ?: return CasWrite.FAILED
        val roundtrip = runCatching { codec.decrypt(sealed.encrypted_vault, sealed.vault_nonce) }.getOrNull()
        if (roundtrip == null || !same_key_fields(roundtrip, next)) return CasWrite.FAILED
        val fingerprints = runCatching { codec.fingerprints(next) }.getOrDefault(emptyList())
        val body = json_object(
            "encrypted_vault" to sealed.encrypted_vault,
            "vault_nonce" to sealed.vault_nonce,
            "expected_user_id" to user,
            "expected_vault_sha256" to snapshot.vault_sha256,
            "vault_format" to maxOf(stored_vault_format(next), MASTER_KEY_VAULT_FORMAT),
            "preserve_pq_prekeys" to true,
            "vault_key_fingerprints" to fingerprints.takeIf { it.isNotEmpty() }?.let { list ->
                JsonArray(list.map { JsonPrimitive(it) })
            },
        )
        val reply = api.put(VAULT_PATH, body)
        if (reply.code == CLIENT_UPGRADE_REQUIRED_CODE) return CasWrite.FAILED
        if (reply.status == 409 || reply.code == VAULT_CHANGED_CODE || reply.code == "CONFLICT") return CasWrite.CONFLICT
        if (reply.error) return CasWrite.FAILED
        on_vault_changed(next, sealed)
        return CasWrite.WRITTEN
    }

    private suspend fun rotate_locked(): StorageVaultOutcome {
        val user = user_id() ?: return StorageVaultOutcome.Skipped("no_account")
        for (attempt in 0..MAX_VAULT_CONFLICT_RETRIES) {
            val snapshot = fetch_snapshot() ?: return StorageVaultOutcome.Skipped("vault_unavailable")
            val vault = decrypt_snapshot(snapshot) ?: return StorageVaultOutcome.Skipped("vault_unreadable")
            val password_keys = runCatching { codec.password_keys(vault) }.getOrDefault(emptyList())
            try {
                if (!is_weak_storage_vault(vault, password_keys, ::decode_required)) {
                    adopt(vault, snapshot)
                    return if (attempt > 0) StorageVaultOutcome.Adopted(vault) else StorageVaultOutcome.Current(vault)
                }
                val next = JSONObject(vault.toString())
                val prior = collect_prior_storage_keys(vault, password_keys, ::decode_required)
                try {
                    install_random_storage_key(next, prior, ::encode_b64, codec::generate_key, now).fill(0)
                } finally {
                    prior.forEach { it.fill(0) }
                }
                when (write_with_cas(next, snapshot, user)) {
                    CasWrite.WRITTEN -> return StorageVaultOutcome.Rotated(next)
                    CasWrite.FAILED -> return StorageVaultOutcome.Skipped("write_failed")
                    CasWrite.CONFLICT -> Unit
                }
            } finally {
                password_keys.forEach { it.fill(0) }
            }
        }
        return StorageVaultOutcome.Skipped("conflict_retries_exhausted")
    }

    suspend fun ensure_random_storage_key(): StorageVaultOutcome = lock.withLock { rotate_locked() }

    private suspend fun finish_locked(expected_data_kek: String): StorageRekeyFinish {
        val user = user_id() ?: return StorageRekeyFinish.FAILED
        repeat(MAX_VAULT_CONFLICT_RETRIES + 1) {
            val snapshot = fetch_snapshot() ?: return StorageRekeyFinish.FAILED
            val vault = decrypt_snapshot(snapshot) ?: return StorageRekeyFinish.FAILED
            if (vault.optString("data_kek", "") != expected_data_kek) {
                adopt(vault, snapshot)
                return StorageRekeyFinish.SUPERSEDED
            }
            val rekey = vault.optJSONObject("storage_rekey")
            if (rekey == null || rekey.optString("state", "") != STORAGE_REKEY_PENDING) {
                adopt(vault, snapshot)
                return StorageRekeyFinish.DONE
            }
            val next = JSONObject(vault.toString())
            val finished = JSONObject().put("state", STORAGE_REKEY_DONE)
            rekey.opt("started_at")?.let { finished.put("started_at", it) }
            finished.put("done_at", now())
            next.put("storage_rekey", finished)
            when (write_with_cas(next, snapshot, user)) {
                CasWrite.WRITTEN -> return StorageRekeyFinish.DONE
                CasWrite.FAILED -> return StorageRekeyFinish.FAILED
                CasWrite.CONFLICT -> Unit
            }
        }
        return StorageRekeyFinish.FAILED
    }

    suspend fun mark_storage_rekey_done(expected_data_kek: String): StorageRekeyFinish =
        lock.withLock { finish_locked(expected_data_kek) }

    suspend fun server_storage_key_matches(expected_data_kek: String): Boolean {
        val snapshot = fetch_snapshot() ?: return false
        val vault = decrypt_snapshot(snapshot) ?: return false
        return vault.optString("data_kek", "") == expected_data_kek && vault.opt("data_kek_random") == true
    }
}

private fun decode_required(value: String): ByteArray =
    decode_b64(value) ?: throw IllegalArgumentException("invalid base64")
