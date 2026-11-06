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
import org.astermail.android.auth.stored_vault_format
import org.json.JSONArray
import org.json.JSONObject

const val VAULT_PATH = "/crypto/v1/keys/vault"
const val MAX_VAULT_CONFLICT_RETRIES = 3
const val VAULT_CHANGED_CODE = "VAULT_CHANGED"
private const val CLIENT_UPGRADE_REQUIRED_CODE = "CLIENT_UPGRADE_REQUIRED"
private val STORAGE_KEY_FIELDS = listOf("data_kek", "data_kek_random", "storage_rekey")

data class ServerVaultSnapshot(
    val encrypted_vault: String,
    val vault_nonce: String,
    val vault_sha256: String,
)

data class SealedVault(val encrypted_vault: String, val vault_nonce: String)

class LocalStorageKeys(val data_kek: String?, val legacy_keks: List<String>) {
    companion object {
        val NONE = LocalStorageKeys(null, emptyList())
    }
}

enum class VaultEdit { CHANGED, UNCHANGED, ABORT }

class VaultDraft(val vault: JSONObject, val snapshot: ServerVaultSnapshot)

sealed class VaultUpdateOutcome {
    class Written(val vault: JSONObject, val sealed: SealedVault) : VaultUpdateOutcome()
    class Unchanged(val vault: JSONObject) : VaultUpdateOutcome()
    object Aborted : VaultUpdateOutcome()
    class Failed(val reason: String) : VaultUpdateOutcome()
}

private enum class CasWrite { WRITTEN, CONFLICT, ENCRYPT_FAILED, ROUNDTRIP_FAILED, FAILED }

private fun legacy_entries(vault: JSONObject): List<JSONObject> {
    val array = vault.optJSONArray("legacy_keks") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
        array.optJSONObject(i)?.takeIf { it.optString("k", "").isNotBlank() }
    }
}

fun preserve_vault_fields(
    server: JSONObject,
    next: JSONObject,
    local: LocalStorageKeys,
    allow_storage_key_change: Boolean,
    now: String,
): JSONObject {
    val server_fields = server.keys().asSequence().toList()
    for (field in server_fields) {
        if (!next.has(field)) next.put(field, server.get(field))
    }
    val displaced = ArrayList<String>()
    if (!allow_storage_key_change) {
        next.optString("data_kek", "").takeIf { it.isNotBlank() }?.let(displaced::add)
        for (field in STORAGE_KEY_FIELDS) {
            if (server.has(field)) next.put(field, server.get(field)) else next.remove(field)
        }
    } else {
        server.optString("data_kek", "").takeIf { it.isNotBlank() }?.let(displaced::add)
    }
    local.data_kek?.takeIf { it.isNotBlank() }?.let(displaced::add)
    displaced.addAll(local.legacy_keks.filter { it.isNotBlank() })
    val current = next.optString("data_kek", "")
    val merged = JSONArray()
    val seen = HashSet<String>()
    for (entry in legacy_entries(server) + legacy_entries(next)) {
        val value = entry.optString("k", "")
        if (value == current || !seen.add(value)) continue
        merged.put(entry)
    }
    for (value in displaced) {
        if (value == current || !seen.add(value)) continue
        merged.put(JSONObject().put("k", value).put("added_at", now))
    }
    if (merged.length() > 0 || server.has("legacy_keks") || next.has("legacy_keks")) {
        next.put("legacy_keks", merged)
    }
    return next
}

class VaultUpdater(
    private val api: ResealApi,
    private val codec: StorageVaultCodec,
    private val user_id: () -> String?,
    private val lock: Mutex,
    private val on_vault_changed: suspend (JSONObject, SealedVault) -> Unit,
    private val now: () -> String = { Instant.now().toString() },
    private val local_keys: () -> LocalStorageKeys = { LocalStorageKeys.NONE },
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

    fun decrypt_snapshot(snapshot: ServerVaultSnapshot): JSONObject? =
        runCatching { codec.decrypt(snapshot.encrypted_vault, snapshot.vault_nonce) }.getOrNull()

    suspend fun latest(): VaultDraft? {
        val snapshot = fetch_snapshot() ?: return null
        val vault = decrypt_snapshot(snapshot) ?: return null
        return VaultDraft(vault, snapshot)
    }

    fun preserve(server: JSONObject, next: JSONObject): JSONObject =
        preserve_vault_fields(server, next, local_keys(), false, now())

    private fun rekey_state(vault: JSONObject): String? =
        vault.optJSONObject("storage_rekey")?.optString("state", "")?.takeIf { it.isNotEmpty() }

    private fun legacy_values(vault: JSONObject): Set<String> =
        legacy_entries(vault).map { it.optString("k", "") }.toSet()

    private fun same_key_fields(a: JSONObject, b: JSONObject): Boolean =
        a.optString("identity_key", "") == b.optString("identity_key", "") &&
            a.optString("data_kek", "") == b.optString("data_kek", "") &&
            (a.opt("data_kek_random") == true) == (b.opt("data_kek_random") == true) &&
            rekey_state(a) == rekey_state(b) &&
            legacy_values(a) == legacy_values(b)

    private suspend fun write_with_cas(
        next: JSONObject,
        snapshot: ServerVaultSnapshot,
        user: String,
    ): Pair<CasWrite, SealedVault?> {
        val sealed = runCatching { codec.encrypt(next) }.getOrNull() ?: return CasWrite.ENCRYPT_FAILED to null
        val roundtrip = runCatching { codec.decrypt(sealed.encrypted_vault, sealed.vault_nonce) }.getOrNull()
        if (roundtrip == null || !same_key_fields(roundtrip, next)) return CasWrite.ROUNDTRIP_FAILED to null
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
        if (reply.code == CLIENT_UPGRADE_REQUIRED_CODE) return CasWrite.FAILED to null
        if (reply.status == 409 || reply.code == VAULT_CHANGED_CODE || reply.code == "CONFLICT") {
            return CasWrite.CONFLICT to null
        }
        if (reply.error) return CasWrite.FAILED to null
        return CasWrite.WRITTEN to sealed
    }

    suspend fun update(
        allow_storage_key_change: Boolean = false,
        edit: suspend (VaultDraft) -> VaultEdit,
    ): VaultUpdateOutcome = lock.withLock { update_locked(allow_storage_key_change, edit) }

    suspend fun replace_through(
        edit: suspend (VaultDraft) -> VaultEdit,
        send: suspend (JSONObject, SealedVault, ServerVaultSnapshot) -> Boolean,
    ): VaultUpdateOutcome = lock.withLock {
        if (user_id() == null) return@withLock VaultUpdateOutcome.Failed("no_account")
        val snapshot = fetch_snapshot() ?: return@withLock VaultUpdateOutcome.Failed("vault_unavailable")
        val server = decrypt_snapshot(snapshot) ?: return@withLock VaultUpdateOutcome.Failed("vault_unreadable")
        val next = JSONObject(server.toString())
        when (edit(VaultDraft(next, snapshot))) {
            VaultEdit.ABORT -> return@withLock VaultUpdateOutcome.Aborted
            VaultEdit.UNCHANGED -> return@withLock VaultUpdateOutcome.Unchanged(server)
            VaultEdit.CHANGED -> Unit
        }
        preserve_vault_fields(server, next, local_keys(), false, now())
        val sealed = runCatching { codec.encrypt(next) }.getOrNull()
            ?: return@withLock VaultUpdateOutcome.Failed("encrypt_failed")
        val roundtrip = runCatching { codec.decrypt(sealed.encrypted_vault, sealed.vault_nonce) }.getOrNull()
        if (roundtrip == null || !same_key_fields(roundtrip, next)) {
            return@withLock VaultUpdateOutcome.Failed("roundtrip_failed")
        }
        if (!send(next, sealed, snapshot)) return@withLock VaultUpdateOutcome.Failed("write_failed")
        on_vault_changed(next, sealed)
        VaultUpdateOutcome.Written(next, sealed)
    }

    suspend fun update_locked(
        allow_storage_key_change: Boolean = false,
        edit: suspend (VaultDraft) -> VaultEdit,
    ): VaultUpdateOutcome {
        val user = user_id() ?: return VaultUpdateOutcome.Failed("no_account")
        repeat(MAX_VAULT_CONFLICT_RETRIES + 1) {
            val snapshot = fetch_snapshot() ?: return VaultUpdateOutcome.Failed("vault_unavailable")
            val server = decrypt_snapshot(snapshot) ?: return VaultUpdateOutcome.Failed("vault_unreadable")
            val next = JSONObject(server.toString())
            when (edit(VaultDraft(next, snapshot))) {
                VaultEdit.ABORT -> return VaultUpdateOutcome.Aborted
                VaultEdit.UNCHANGED -> {
                    on_vault_changed(server, SealedVault(snapshot.encrypted_vault, snapshot.vault_nonce))
                    return VaultUpdateOutcome.Unchanged(server)
                }
                VaultEdit.CHANGED -> Unit
            }
            preserve_vault_fields(server, next, local_keys(), allow_storage_key_change, now())
            val (result, sealed) = write_with_cas(next, snapshot, user)
            when (result) {
                CasWrite.WRITTEN -> {
                    val written = sealed ?: return VaultUpdateOutcome.Failed("write_failed")
                    on_vault_changed(next, written)
                    return VaultUpdateOutcome.Written(next, written)
                }
                CasWrite.CONFLICT -> Unit
                CasWrite.ENCRYPT_FAILED -> return VaultUpdateOutcome.Failed("encrypt_failed")
                CasWrite.ROUNDTRIP_FAILED -> return VaultUpdateOutcome.Failed("roundtrip_failed")
                CasWrite.FAILED -> return VaultUpdateOutcome.Failed("write_failed")
            }
        }
        return VaultUpdateOutcome.Failed("conflict_retries_exhausted")
    }
}
