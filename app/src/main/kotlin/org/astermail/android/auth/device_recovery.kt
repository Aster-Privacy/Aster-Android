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

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.recovery.ConsumeInactiveKeySetRequest
import org.astermail.android.api.recovery.DeleteDeviceSecretsRequest
import org.astermail.android.api.recovery.FetchDeviceSecretsRequest
import org.astermail.android.api.recovery.FetchInactiveKeySetRequest
import org.astermail.android.api.recovery.PutDeviceSecretRequest
import org.astermail.android.api.recovery.RecoveryApi
import org.astermail.android.crypto.CryptoNative
import org.astermail.android.storage.SessionKeyStore
import org.astermail.android.util.passphrase_chars
import org.json.JSONArray
import org.json.JSONObject

const val DEVICE_RECOVERY_START_DELAY_MS = 30_000L
const val DEVICE_RECOVERY_REFRESH_INTERVAL_MS = 10 * 60 * 1000L
const val DEVICE_RECOVERY_SNAPSHOTS_KEPT = 2

interface DeviceRecoveryVaultCodec {
    fun decrypt_vault(encrypted_vault: String, vault_nonce: String, passphrase: ByteArray): ByteArray?
    fun derive_storage_key(passphrase: ByteArray): String?
}

class NativeDeviceRecoveryVaultCodec : DeviceRecoveryVaultCodec {
    override fun decrypt_vault(
        encrypted_vault: String,
        vault_nonce: String,
        passphrase: ByteArray,
    ): ByteArray? = runCatching {
        CryptoNative.decrypt_vault_with_password(
            Base64.getDecoder().decode(encrypted_vault),
            Base64.getDecoder().decode(vault_nonce),
            passphrase,
        )
    }.getOrNull()

    override fun derive_storage_key(passphrase: ByteArray): String? = runCatching {
        val raw = CryptoNative.derive_storage_key(passphrase)
        val encoded = Base64.getEncoder().encodeToString(raw)
        raw.fill(0)
        encoded
    }.getOrNull()
}

fun interface DeviceRecoveryCommit {
    suspend fun commit(
        user_id: String,
        passphrase: ByteArray,
        vault_obj: JSONObject,
        identity_keys: RecoveredIdentityKeys,
        recovered_keks: List<String>,
        recovered_ratchet: List<JSONObject>,
    ): Boolean
}

private class OpenedSnapshot(val record: DeviceSnapshotRecord, val payload: DeviceSnapshotPayload)

@Singleton
class DeviceRecovery internal constructor(
    private val recovery_api: RecoveryApi,
    private val keys_api: KeysApi,
    private val session_key_store: SessionKeyStore,
    private val snapshot_store: DeviceSnapshotStore,
    private val key_provider: DeviceRecoveryKeyProvider,
    private val vault_codec: DeviceRecoveryVaultCodec,
    private val commit: DeviceRecoveryCommit,
    private val now_ms: () -> Long,
    private val new_snapshot_id: () -> String,
) {
    @Inject
    constructor(
        recovery_api: RecoveryApi,
        keys_api: KeysApi,
        session_key_store: SessionKeyStore,
        auth_repository: dagger.Lazy<AuthRepository>,
        @ApplicationContext context: Context,
    ) : this(
        recovery_api,
        keys_api,
        session_key_store,
        FileDeviceSnapshotStore(context),
        KeystoreDeviceRecoveryKeyProvider(),
        NativeDeviceRecoveryVaultCodec(),
        DeviceRecoveryCommit { user_id, passphrase, vault_obj, identity_keys, keks, ratchet ->
            auth_repository.get().commit_recovered_keys(
                user_id = user_id,
                passphrase = passphrase,
                vault_obj = vault_obj,
                identity_keys = identity_keys,
                recovered_keks = keks,
                recovered_ratchet = ratchet,
            )
        },
        System::currentTimeMillis,
        { UUID.randomUUID().toString() },
    )

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal var foreground_check: () -> Boolean = {
        runCatching {
            androidx.lifecycle.ProcessLifecycleOwner.get()
                .lifecycle.currentState
                .isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        }.getOrDefault(true)
    }

    @Volatile
    private var scheduled: Job? = null

    fun schedule(delay_ms: Long = DEVICE_RECOVERY_START_DELAY_MS) {
        if (scheduled?.isActive == true) return
        scheduled = scope.launch {
            delay(delay_ms)
            guarded { run(session_key_store.get_user_id().orEmpty()) }
            while (isActive) {
                delay(DEVICE_RECOVERY_REFRESH_INTERVAL_MS)
                if (!foreground_check()) continue
                guarded {
                    if (enabled()) refresh_snapshot(session_key_store.get_user_id().orEmpty())
                }
            }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) {
        if (!lock.tryLock()) return
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        } finally {
            lock.unlock()
        }
    }

    suspend fun enabled(): Boolean =
        runCatching { keys_api.get_account_key_capabilities().device_recovery }.getOrDefault(false)

    suspend fun run(user_id: String): Int {
        if (user_id.isEmpty()) return 0
        if (!enabled()) return 0

        val inactive = inactive_key_set_hashes()
        var consumed = 0

        if (inactive != null && inactive.isNotEmpty()) {
            val absorbed = recover_from_snapshots(user_id, inactive.values.toSet())
            val used = mutableSetOf<String>()

            for ((id, hash) in inactive.entries.toList()) {
                if (hash !in absorbed) continue
                val ok = runCatching {
                    recovery_api.consume_inactive_key_set(ConsumeInactiveKeySetRequest(id)).success
                }.getOrDefault(false)
                if (!ok) continue
                inactive.remove(id)
                used.add(hash)
                consumed += 1
            }

            if (used.isNotEmpty()) {
                forget_snapshots(
                    user_id,
                    snapshot_store.list(user_id).filter { it.source_hash in used }.map { it.snapshot_id },
                )
            }
        }

        refresh_snapshot(user_id)

        if (inactive != null) prune_snapshots(user_id, inactive.values.toSet())

        return consumed
    }

    suspend fun refresh_snapshot(user_id: String): Boolean {
        if (user_id.isEmpty()) return false
        val stored = session_key_store.get_encrypted_vault() ?: return false
        val passphrase = session_key_store.get_passphrase() ?: return false

        try {
            val source_hash = runCatching { vault_ciphertext_hash(stored.first) }.getOrNull() ?: return false
            if (snapshot_store.list(user_id).any { it.source_hash == source_hash }) return false

            val vault_obj = load_vault(stored.first, stored.second, passphrase) ?: return false
            val loaded = key_provider.load(true) ?: return false
            if (loaded.created) {
                forget_snapshots(user_id, snapshot_store.list(user_id).map { it.snapshot_id })
            }

            val payload = build_payload(vault_obj, passphrase)
            val snapshot_id = new_snapshot_id()
            val secret = random_device_secret()

            try {
                val stored_on_server = runCatching {
                    recovery_api.put_device_recovery_secret(
                        PutDeviceSecretRequest(snapshot_id, Base64.getEncoder().encodeToString(secret)),
                    ).success
                }.getOrDefault(false)
                if (!stored_on_server) return false

                val sealed = runCatching {
                    seal_device_snapshot(
                        payload,
                        secret,
                        loaded.key,
                        DeviceSnapshotMeta(user_id, snapshot_id, source_hash),
                    )
                }.getOrNull()

                val saved = sealed != null && snapshot_store.save(
                    DeviceSnapshotRecord(
                        snapshot_id = snapshot_id,
                        user_id = user_id,
                        source_hash = source_hash,
                        created_at = now_ms(),
                        iv = sealed.iv,
                        sealed = sealed.ciphertext,
                    ),
                )

                if (!saved) {
                    runCatching {
                        recovery_api.delete_device_recovery_secrets(
                            DeleteDeviceSecretsRequest(listOf(snapshot_id)),
                        )
                    }
                    return false
                }

                return true
            } finally {
                secret.fill(0)
            }
        } finally {
            passphrase.fill(0)
        }
    }

    private fun load_vault(encrypted_vault: String, vault_nonce: String, passphrase: ByteArray): JSONObject? {
        val plain = vault_codec.decrypt_vault(encrypted_vault, vault_nonce, passphrase) ?: return null
        val vault_obj = runCatching { JSONObject(String(plain, Charsets.UTF_8)) }.getOrNull()
        plain.fill(0)
        return vault_obj
    }

    private fun build_payload(vault_obj: JSONObject, passphrase: ByteArray): DeviceSnapshotPayload {
        val identity_key = vault_identity_key(vault_obj)
        val previous_keys = json_strings(vault_obj.optJSONArray("previous_keys"))
        val legacy_identity_keys = json_strings(vault_obj.optJSONArray("legacy_identity_keys"))
        val chars = passphrase_chars(passphrase)
        val unlocked_keys = mutableListOf<Pair<String, String>>()

        try {
            val seen = mutableSetOf<String>()
            for (armored in listOf(identity_key) + previous_keys) {
                if (armored.isEmpty() || !seen.add(armored)) continue
                val unlocked = runCatching { unlock_pgp_key(armored, chars) }.getOrNull() ?: continue
                unlocked_keys.add(armored to unlocked)
            }
        } finally {
            chars.fill('\u0000')
        }

        return DeviceSnapshotPayload(
            identity_key = identity_key,
            previous_keys = previous_keys,
            legacy_identity_keys = legacy_identity_keys,
            unlocked_keys = unlocked_keys,
            storage_keys = harvest_storage_keks(vault_obj, vault_codec.derive_storage_key(passphrase)),
            ratchet_keys = retain_previous_ratchet_keys(vault_obj),
        )
    }

    private suspend fun inactive_key_set_hashes(): MutableMap<String, String>? {
        val sets = runCatching { recovery_api.list_inactive_key_sets().inactive_key_sets }.getOrNull() ?: return null
        val hashes = mutableMapOf<String, String>()

        for (set in sets) {
            val fetched = runCatching {
                recovery_api.fetch_inactive_key_set(FetchInactiveKeySetRequest(set.id))
            }.getOrNull() ?: return null
            val hash = runCatching { vault_ciphertext_hash(fetched.encrypted_vault) }.getOrNull() ?: return null
            hashes[set.id] = hash
        }

        return hashes
    }

    private suspend fun recover_from_snapshots(user_id: String, archived_hashes: Set<String>): Set<String> {
        val matching = snapshot_store.list(user_id).filter { it.source_hash in archived_hashes }
        if (matching.isEmpty()) return emptySet()

        val opened = open_snapshots(user_id, matching)
        if (opened.isEmpty()) return emptySet()

        val stored = session_key_store.get_encrypted_vault() ?: return emptySet()
        val passphrase = session_key_store.get_passphrase() ?: return emptySet()

        try {
            val vault_obj = load_vault(stored.first, stored.second, passphrase) ?: return emptySet()
            val unlocked = mutableMapOf<String, String>()
            for (entry in opened) {
                for ((armored, unlocked_armored) in entry.payload.unlocked_keys) {
                    unlocked[armored] = unlocked_armored
                }
            }

            val chars = passphrase_chars(passphrase)
            val identity_keys = try {
                merge_identity_keys_with(
                    vault_obj,
                    opened.map { snapshot_vault(it.payload) },
                ) { armored ->
                    val unlocked_armored = unlocked[armored] ?: error("device recovery snapshot has no key")
                    lock_unlocked_pgp_key(unlocked_armored, chars)
                }
            } finally {
                chars.fill('\u0000')
                unlocked.clear()
            }

            val committed = commit.commit(
                user_id = user_id,
                passphrase = passphrase,
                vault_obj = vault_obj,
                identity_keys = identity_keys,
                recovered_keks = opened.flatMap { it.payload.storage_keys },
                recovered_ratchet = opened.flatMap { it.payload.ratchet_keys },
            )
            if (!committed) return emptySet()

            return opened
                .filterIndexed { index, _ -> identity_keys.absorbed.getOrElse(index) { false } }
                .map { it.record.source_hash }
                .toSet()
        } finally {
            passphrase.fill(0)
        }
    }

    private fun snapshot_vault(payload: DeviceSnapshotPayload): JSONObject =
        JSONObject()
            .put("identity_key", payload.identity_key)
            .put("previous_keys", JSONArray(payload.previous_keys))
            .put("legacy_identity_keys", JSONArray(payload.legacy_identity_keys))

    private suspend fun open_snapshots(
        user_id: String,
        records: List<DeviceSnapshotRecord>,
    ): List<OpenedSnapshot> {
        val loaded = key_provider.load(false)
        if (loaded == null) {
            forget_snapshots(user_id, snapshot_store.list(user_id).map { it.snapshot_id })
            return emptyList()
        }

        val candidates = records.take(MAX_DEVICE_SECRETS_PER_REQUEST)
        val response = runCatching {
            recovery_api.fetch_device_recovery_secrets(
                FetchDeviceSecretsRequest(candidates.map { it.snapshot_id }),
            )
        }.getOrNull() ?: return emptyList()

        val secrets = response.secrets.associate { it.snapshot_id to it.secret }
        val opened = mutableListOf<OpenedSnapshot>()

        for (record in candidates) {
            val encoded = secrets[record.snapshot_id] ?: continue
            val secret = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: continue
            if (secret.size != DEVICE_RECOVERY_SECRET_BYTES) {
                secret.fill(0)
                continue
            }
            val payload = open_device_snapshot(record, secret, loaded.key)
            secret.fill(0)
            if (payload != null) opened.add(OpenedSnapshot(record, payload))
        }

        return opened
    }

    private suspend fun forget_snapshots(user_id: String, snapshot_ids: List<String>) {
        if (snapshot_ids.isEmpty()) return
        for (chunk in snapshot_ids.chunked(MAX_DEVICE_SECRETS_PER_REQUEST)) {
            val deleted = runCatching {
                recovery_api.delete_device_recovery_secrets(DeleteDeviceSecretsRequest(chunk)).success
            }.getOrDefault(false)
            if (deleted) snapshot_store.delete(user_id, chunk)
        }
    }

    private suspend fun prune_snapshots(user_id: String, protected_hashes: Set<String>) {
        val stale = mutableListOf<String>()
        var recent_kept = 0

        for (record in snapshot_store.list(user_id)) {
            if (record.source_hash in protected_hashes) continue
            if (recent_kept < DEVICE_RECOVERY_SNAPSHOTS_KEPT) {
                recent_kept += 1
                continue
            }
            stale.add(record.snapshot_id)
        }

        forget_snapshots(user_id, stale)
    }
}
