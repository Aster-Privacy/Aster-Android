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
import org.astermail.android.api.ApiError
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
const val DEVICE_RECOVERY_SNAPSHOT_TTL_MS = 30L * 24 * 60 * 60 * 1000
const val DEVICE_RECOVERY_ATTEMPT_SPACING_MS = 24L * 60 * 60 * 1000
const val DEVICE_RECOVERY_MAX_ATTEMPTS = 3
const val DEVICE_RECOVERY_PUT_BACKOFF_MS = 60L * 60 * 1000
const val DEVICE_RECOVERY_PUT_RATE_LIMITED_BACKOFF_MS = 6L * 60 * 60 * 1000

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
        base_encrypted_vault: String,
    ): RecoveredKeyCommitResult
}

fun device_recovery_attempt_allowed(attempt: DeviceRecoveryAttempt?, now_ms: Long): Boolean {
    if (attempt == null) return true
    if (attempt.count >= DEVICE_RECOVERY_MAX_ATTEMPTS) return false
    return now_ms - attempt.last_at >= DEVICE_RECOVERY_ATTEMPT_SPACING_MS
}

private class OpenedSnapshot(val record: DeviceSnapshotRecord, val payload: DeviceSnapshotPayload)

private class OpenedSnapshots(val snapshots: List<OpenedSnapshot>, val attempted: List<String>)

@Singleton
class DeviceRecovery internal constructor(
    private val recovery_api: RecoveryApi,
    private val keys_api: KeysApi,
    private val session_key_store: SessionKeyStore,
    private val snapshot_store: DeviceSnapshotStore,
    private val key_provider: DeviceRecoveryKeyProvider,
    private val vault_codec: DeviceRecoveryVaultCodec,
    private val state_store: DeviceRecoveryStateStore,
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
        PrefsDeviceRecoveryStateStore(context),
        DeviceRecoveryCommit { user_id, passphrase, vault_obj, identity_keys, keks, ratchet, base_vault ->
            auth_repository.get().commit_recovered_keys(
                user_id = user_id,
                passphrase = passphrase,
                vault_obj = vault_obj,
                identity_keys = identity_keys,
                recovered_keks = keks,
                recovered_ratchet = ratchet,
                base_encrypted_vault = base_vault,
            )
        },
        System::currentTimeMillis,
        { UUID.randomUUID().toString() },
    )

    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var last_run_user: String? = null
    private val archived_hashes = mutableMapOf<String, Set<String>>()

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
            guarded { tick(session_key_store.get_user_id().orEmpty()) }
            while (isActive) {
                delay(DEVICE_RECOVERY_REFRESH_INTERVAL_MS)
                if (!foreground_check()) continue
                guarded { tick(session_key_store.get_user_id().orEmpty()) }
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

    internal suspend fun tick(user_id: String) {
        if (user_id.isEmpty()) {
            expire_snapshots("")
            return
        }
        if (user_id != last_run_user || archived_hashes[user_id] == null) {
            last_run_user = user_id
            run(user_id)
            return
        }
        expire_snapshots(user_id)
        if (!enabled()) return
        refresh_snapshot(user_id)
        archived_hashes[user_id]?.let { prune_snapshots(user_id, it) }
    }

    suspend fun enabled(): Boolean =
        runCatching { keys_api.get_account_key_capabilities().device_recovery }.getOrDefault(false)

    suspend fun run(user_id: String): Int {
        expire_snapshots(user_id)
        if (user_id.isEmpty()) return 0
        if (!enabled()) return 0
        if (session_key_store.get_user_id() != user_id) return 0

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

        if (inactive != null) {
            val hashes = inactive.values.toSet()
            archived_hashes[user_id] = hashes
            prune_snapshots(user_id, hashes)
        }
        forget_orphaned_attempts(user_id)

        return consumed
    }

    suspend fun refresh_snapshot(user_id: String): Boolean {
        if (user_id.isEmpty()) return false
        if (session_key_store.get_user_id() != user_id) return false
        val stored = session_key_store.get_encrypted_vault() ?: return false
        val passphrase = session_key_store.get_passphrase() ?: return false

        try {
            if (session_key_store.get_user_id() != user_id) return false
            if (session_key_store.get_encrypted_vault()?.first != stored.first) return false

            val source_hash = runCatching { vault_ciphertext_hash(stored.first) }.getOrNull() ?: return false
            if (snapshot_store.list(user_id).any { it.source_hash == source_hash }) return false
            if (now_ms() < state_store.refresh_not_before(user_id)) return false

            val key = when (val loaded = key_provider.load(true)) {
                is DeviceRecoveryKeyLoad.Ready -> {
                    if (loaded.created) drop_unopenable_snapshots()
                    loaded.key
                }
                DeviceRecoveryKeyLoad.Missing -> return false
                DeviceRecoveryKeyLoad.Unavailable -> return false
            }

            val vault_obj = load_vault(stored.first, stored.second, passphrase) ?: return false
            val payload = build_payload(vault_obj, passphrase)
            val snapshot_id = new_snapshot_id()
            val secret = random_device_secret()

            try {
                val put = runCatching {
                    recovery_api.put_device_recovery_secret(
                        PutDeviceSecretRequest(snapshot_id, Base64.getEncoder().encodeToString(secret)),
                    ).success
                }
                if (!put.getOrDefault(false)) {
                    val wait = if (put.exceptionOrNull() is ApiError.RateLimited) {
                        DEVICE_RECOVERY_PUT_RATE_LIMITED_BACKOFF_MS
                    } else {
                        DEVICE_RECOVERY_PUT_BACKOFF_MS
                    }
                    state_store.put_refresh_not_before(user_id, now_ms() + wait)
                    return false
                }
                state_store.put_refresh_not_before(user_id, 0L)

                val sealed = runCatching {
                    seal_device_snapshot(
                        payload,
                        secret,
                        key,
                        DeviceSnapshotMeta(user_id, snapshot_id, source_hash),
                    )
                }.getOrNull()

                val saved = sealed != null &&
                    session_key_store.get_user_id() == user_id &&
                    snapshot_store.save(
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

    private suspend fun recover_from_snapshots(user_id: String, archived: Set<String>): Set<String> {
        val started_at = now_ms()
        val attempts = state_store.attempts(user_id)
        val matching = snapshot_store.list(user_id).filter {
            it.source_hash in archived &&
                device_recovery_attempt_allowed(attempts[it.snapshot_id], started_at)
        }
        if (matching.isEmpty()) return emptySet()

        val opened = open_snapshots(matching)
        record_attempts(user_id, opened.attempted, started_at)
        if (opened.snapshots.isEmpty()) return emptySet()
        if (session_key_store.get_user_id() != user_id) return emptySet()

        val stored = session_key_store.get_encrypted_vault() ?: return emptySet()
        val passphrase = session_key_store.get_passphrase() ?: return emptySet()

        try {
            if (session_key_store.get_user_id() != user_id) return emptySet()
            val vault_obj = load_vault(stored.first, stored.second, passphrase) ?: return emptySet()
            val unlocked = mutableMapOf<String, String>()
            for (entry in opened.snapshots) {
                for ((armored, unlocked_armored) in entry.payload.unlocked_keys) {
                    unlocked[armored] = unlocked_armored
                }
            }

            val chars = passphrase_chars(passphrase)
            val identity_keys = try {
                merge_identity_keys_with(
                    vault_obj,
                    opened.snapshots.map { snapshot_vault(it.payload) },
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
                recovered_keks = opened.snapshots.flatMap { it.payload.storage_keys },
                recovered_ratchet = opened.snapshots.flatMap { it.payload.ratchet_keys },
                base_encrypted_vault = stored.first,
            )
            if (!committed.written || committed.dropped_any) return emptySet()

            return opened.snapshots
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

    private suspend fun open_snapshots(records: List<DeviceSnapshotRecord>): OpenedSnapshots {
        val key = when (val loaded = key_provider.load(false)) {
            is DeviceRecoveryKeyLoad.Ready -> loaded.key
            DeviceRecoveryKeyLoad.Missing -> {
                drop_unopenable_snapshots()
                return OpenedSnapshots(emptyList(), emptyList())
            }
            DeviceRecoveryKeyLoad.Unavailable -> return OpenedSnapshots(emptyList(), emptyList())
        }

        val opened = mutableListOf<OpenedSnapshot>()
        val attempted = mutableListOf<String>()

        for (chunk in records.chunked(MAX_DEVICE_SECRETS_PER_REQUEST)) {
            val response = runCatching {
                recovery_api.fetch_device_recovery_secrets(
                    FetchDeviceSecretsRequest(chunk.map { it.snapshot_id }),
                )
            }.getOrNull() ?: continue

            val secrets = response.secrets.associate { it.snapshot_id to it.secret }

            for (record in chunk) {
                val encoded = secrets[record.snapshot_id] ?: continue
                attempted.add(record.snapshot_id)
                val secret = runCatching { Base64.getDecoder().decode(encoded) }.getOrNull() ?: continue
                if (secret.size != DEVICE_RECOVERY_SECRET_BYTES) {
                    secret.fill(0)
                    continue
                }
                val payload = open_device_snapshot(record, secret, key)
                secret.fill(0)
                if (payload != null) opened.add(OpenedSnapshot(record, payload))
            }
        }

        return OpenedSnapshots(opened, attempted)
    }

    private fun record_attempts(user_id: String, snapshot_ids: List<String>, at_ms: Long) {
        if (snapshot_ids.isEmpty()) return
        val attempts = state_store.attempts(user_id).toMutableMap()
        for (id in snapshot_ids) {
            val count = attempts[id]?.count ?: 0
            attempts[id] = DeviceRecoveryAttempt(count + 1, at_ms)
        }
        state_store.put_attempts(user_id, attempts)
    }

    private fun drop_attempts(user_id: String, snapshot_ids: Collection<String>) {
        if (snapshot_ids.isEmpty()) return
        val attempts = state_store.attempts(user_id)
        if (snapshot_ids.none { it in attempts }) return
        state_store.put_attempts(user_id, attempts - snapshot_ids.toSet())
    }

    private fun forget_orphaned_attempts(user_id: String) {
        val attempts = state_store.attempts(user_id)
        if (attempts.isEmpty()) return
        val present = snapshot_store.list(user_id).map { it.snapshot_id }.toSet()
        val kept = attempts.filterKeys { it in present }
        if (kept.size != attempts.size) state_store.put_attempts(user_id, kept)
    }

    private fun drop_unopenable_snapshots() {
        for ((owner, records) in snapshot_store.list_all().groupBy { it.user_id }) {
            val ids = records.map { it.snapshot_id }
            snapshot_store.delete(owner, ids)
            drop_attempts(owner, ids)
        }
    }

    private suspend fun expire_snapshots(signed_in_user: String) {
        val cutoff = now_ms() - DEVICE_RECOVERY_SNAPSHOT_TTL_MS
        val expired = snapshot_store.list_all().filter { it.created_at <= cutoff }
        if (expired.isEmpty()) return

        for ((owner, records) in expired.groupBy { it.user_id }) {
            val ids = records.map { it.snapshot_id }
            val owner_signed_in = owner.isNotEmpty() &&
                owner == signed_in_user &&
                session_key_store.get_user_id() == owner
            if (owner_signed_in) {
                for (chunk in ids.chunked(MAX_DEVICE_SECRETS_PER_REQUEST)) {
                    runCatching {
                        recovery_api.delete_device_recovery_secrets(DeleteDeviceSecretsRequest(chunk))
                    }
                }
            }
            snapshot_store.delete(owner, ids)
            drop_attempts(owner, ids)
        }
    }

    private suspend fun forget_snapshots(user_id: String, snapshot_ids: List<String>) {
        if (snapshot_ids.isEmpty()) return
        for (chunk in snapshot_ids.chunked(MAX_DEVICE_SECRETS_PER_REQUEST)) {
            val deleted = runCatching {
                recovery_api.delete_device_recovery_secrets(DeleteDeviceSecretsRequest(chunk)).success
            }.getOrDefault(false)
            if (deleted) {
                snapshot_store.delete(user_id, chunk)
                drop_attempts(user_id, chunk)
            }
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
