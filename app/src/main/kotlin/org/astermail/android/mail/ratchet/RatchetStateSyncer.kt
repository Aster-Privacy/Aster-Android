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

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.astermail.android.BuildConfig
import org.astermail.android.api.ratchet.PostStateOutcome
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto

@Singleton
class RatchetStateSyncer @Inject constructor(
    private val state_store: RatchetStateStore,
    private val ratchet_api: RatchetApi,
    private val sync_vault: RatchetSyncVault,
    private val format_gate: SyncFormatGate,
) {

    constructor(state_store: RatchetStateStore, ratchet_api: RatchetApi) :
        this(state_store, ratchet_api, RatchetSyncVault.none, SyncFormatGate.never)

    private val locks = mutableMapOf<String, Mutex>()
    private val locks_guard = Mutex()
    private val known_versions = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val missing_state_at = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val synced_fingerprints = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val remote_v3_seen = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private suspend fun lock_for(conversation_id: String): Mutex = locks_guard.withLock {
        locks.getOrPut(conversation_id) { Mutex() }
    }

    fun clear_missing_state_cache() {
        missing_state_at.clear()
        synced_fingerprints.clear()
        remote_v3_seen.clear()
    }

    private suspend fun effective_floor(conversation_id: String, vault: RatchetVaultFloors): Long =
        maxOf(state_store.sync_floor(conversation_id), vault.floor(conversation_id))

    private fun state_recently_missing(conversation_id: String): Boolean {
        val missed_at = missing_state_at[conversation_id] ?: return false
        if (System.currentTimeMillis() - missed_at < MISSING_STATE_TTL_MS) return true
        missing_state_at.remove(conversation_id)
        return false
    }

    private class OpenedState(
        val plaintext: ByteArray,
        val opened_bound: Boolean,
        val header: RatchetStateHeader?,
    )

    private fun try_open(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray, aad: ByteArray?): ByteArray? =
        runCatching { RatchetCrypto.aes_gcm_decrypt(ciphertext, key, nonce, aad) }.getOrNull()

    private fun open_state(
        bytes: ByteArray,
        nonce: ByteArray,
        conversation_id: String,
        user_id: String,
        state_key: ByteArray,
        compat_key: ByteArray,
    ): OpenedState? {
        val payload = RatchetStateContainer.split_payload(bytes)
        if (payload != null) {
            val aad = RatchetStateContainer.sync_aad(user_id, conversation_id, payload.header_bytes)
            try_open(payload.ciphertext, state_key, nonce, aad)?.let {
                return OpenedState(it, true, payload.header)
            }
        }
        try_open(bytes, compat_key, nonce, RatchetStateContainer.bound_aad(conversation_id))?.let {
            return OpenedState(it, true, null)
        }
        try_open(bytes, compat_key, nonce, null)?.let {
            return OpenedState(it, false, null)
        }
        return null
    }

    private fun open_under_retired_key(
        bytes: ByteArray,
        nonce: ByteArray,
        conversation_id: String,
        user_id: String,
    ): OpenedState? {
        val retired = state_store.retired_state_key_candidates()
        return try {
            retired.firstNotNullOfOrNull { key -> open_state(bytes, nonce, conversation_id, user_id, key, key) }
        } finally {
            retired.forEach { it.fill(0) }
        }
    }

    private fun decode_retired(opened: OpenedState, conversation_id: String, vault: RatchetVaultFloors): RatchetState? {
        try {
            if (vault.state_v2 && opened.header == null) return null
            val decoded = RatchetStateContainer.decode(
                String(opened.plaintext, Charsets.UTF_8),
                conversation_id,
                0L,
                opened.opened_bound,
                established = false,
                allow_unversioned = true,
            ) as? RatchetStateContainerResult.Accepted ?: return null
            val header = opened.header
            if (header != null && !RatchetStateContainer.header_matches(header, decoded.state, decoded.sync_version)) return null
            return decoded.state
        } finally {
            opened.plaintext.fill(0)
        }
    }

    private sealed class RemoteFetch {
        data class Found(val state: RatchetState, val versioned: Boolean, val sync_version: Long?) : RemoteFetch()
        data class Retired(val state: RatchetState) : RemoteFetch()
        object Missing : RemoteFetch()
        object Rejected : RemoteFetch()
        object Legacy : RemoteFetch()
        object Unreadable : RemoteFetch()
        object Failed : RemoteFetch()
    }

    suspend fun fetch_from_server(conversation_id: String): RatchetState? {
        if (state_recently_missing(conversation_id)) return null
        return when (val remote = fetch_remote(conversation_id)) {
            is RemoteFetch.Found -> remote.state
            RemoteFetch.Legacy -> {
                missing_state_at[conversation_id] = System.currentTimeMillis()
                null
            }
            else -> null
        }
    }

    suspend fun settle_intents() {
        for ((conversation_id, intended) in sync_vault.unconfirmed()) {
            lock_for(conversation_id).withLock {
                when (val remote = fetch_remote(conversation_id)) {
                    is RemoteFetch.Found -> {
                        val held = remote.sync_version
                        if (held != null && held >= intended) {
                            sync_vault.record(conversation_id, held, false)
                        } else {
                            sync_vault.drop_intent(conversation_id, intended)
                        }
                    }
                    RemoteFetch.Failed -> Unit
                    else -> sync_vault.drop_intent(conversation_id, intended)
                }
            }
        }
    }

    private suspend fun fetch_remote(conversation_id: String, for_merge: Boolean = false): RemoteFetch {
        val keys = state_store.derive_sync_keys() ?: return RemoteFetch.Failed
        return try {
            val conv_b64 = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8))
            val resp = ratchet_api.fetch_state(conv_b64)
            if (resp == null) {
                missing_state_at[conversation_id] = System.currentTimeMillis()
                return RemoteFetch.Missing
            }
            missing_state_at.remove(conversation_id)
            val bytes = RatchetCrypto.b64_decode(resp.encrypted_state)
            val nonce = RatchetCrypto.b64_decode(resp.state_nonce)
            val vault = sync_vault.read()
            val opened = open_state(bytes, nonce, conversation_id, keys.user_id, keys.state_key, keys.compat_key)
            if (opened == null) {
                val retired = open_under_retired_key(bytes, nonce, conversation_id, keys.user_id)
                    ?: return RemoteFetch.Unreadable
                if (vault.state_v2 && retired.header == null) {
                    retired.plaintext.fill(0)
                    return RemoteFetch.Legacy
                }
                if (retired.header != null) remote_v3_seen[conversation_id] = true
                known_versions[conversation_id] = resp.state_version
                val state = decode_retired(retired, conversation_id, vault)
                return if (for_merge && state != null) RemoteFetch.Retired(state) else RemoteFetch.Rejected
            }
            if (vault.state_v2 && opened.header == null) {
                opened.plaintext.fill(0)
                return RemoteFetch.Legacy
            }
            if (opened.header != null) remote_v3_seen[conversation_id] = true
            val floor = effective_floor(conversation_id, vault)
            val established = floor > 0 || state_store.load(conversation_id) != null
            val decoded = RatchetStateContainer.decode(
                String(opened.plaintext, Charsets.UTF_8),
                conversation_id,
                floor,
                opened.opened_bound,
                established = established,
                allow_unversioned = for_merge,
            )
            opened.plaintext.fill(0)
            val accepted = decoded as? RatchetStateContainerResult.Accepted ?: return RemoteFetch.Rejected
            val header = opened.header
            if (header != null && !RatchetStateContainer.header_matches(header, accepted.state, accepted.sync_version)) {
                return RemoteFetch.Rejected
            }
            accepted.sync_version?.let { state_store.raise_sync_floor(conversation_id, it) }
            known_versions[conversation_id] = resp.state_version
            RemoteFetch.Found(accepted.state, accepted.sync_version != null, accepted.sync_version)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "fetch_from_server failed: ${t.javaClass.simpleName}")
            RemoteFetch.Failed
        } finally {
            keys.wipe()
        }
    }

    suspend fun sync(conversation_id: String, state: RatchetState): Boolean {
        return lock_for(conversation_id).withLock { do_sync(conversation_id, state) }
    }

    suspend fun reset(conversation_id: String) {
        lock_for(conversation_id).withLock {
            known_versions.remove(conversation_id)
            missing_state_at.remove(conversation_id)
            synced_fingerprints.remove(conversation_id)
            remote_v3_seen.remove(conversation_id)
            state_store.delete(conversation_id)
            try {
                val conv_b64 = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8))
                ratchet_api.delete_state(conv_b64)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "reset: server delete failed (continuing): ${t.javaClass.simpleName}")
            }
        }
    }


    private sealed class Absorb {
        data class Merged(val state: RatchetState) : Absorb()
        object Overwrite : Absorb()
        object Abort : Absorb()
    }

    private suspend fun absorb_remote(
        conversation_id: String,
        working: RatchetState,
    ): Absorb {
        return when (val remote = fetch_remote(conversation_id, for_merge = true)) {
            is RemoteFetch.Found -> {
                val merged = RatchetStateMerge.merge(working, remote.state, remote.versioned)
                state_store.save(merged)
                Absorb.Merged(merged)
            }
            is RemoteFetch.Retired -> if (RatchetStateMerge.shares_epoch(working, remote.state)) {
                val merged = RatchetStateMerge.merge_retired(working, remote.state)
                DoubleRatchet.advance_send_chain(merged, RatchetStateMerge.retired_send_target(working, remote.state))
                state_store.save(merged)
                Absorb.Merged(merged)
            } else {
                Absorb.Overwrite
            }
            RemoteFetch.Missing, RemoteFetch.Rejected, RemoteFetch.Legacy -> Absorb.Overwrite
            RemoteFetch.Unreadable, RemoteFetch.Failed -> Absorb.Abort
        }
    }

    private suspend fun do_sync(conversation_id: String, state: RatchetState): Boolean {
        val keys = state_store.derive_sync_keys() ?: return false
        try {
            val conv_b64 = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8))
            var working = state
            var fingerprint = RatchetStateContainer.state_fingerprint(working)
            if (synced_fingerprints[conversation_id] == fingerprint &&
                known_versions.containsKey(conversation_id)
            ) {
                return true
            }
            val vault = sync_vault.read()
            val format_ready = vault.state_v2 || format_gate.ratchet_state_v2_ready()
            var sync_version = RatchetStateContainer.next_version(effective_floor(conversation_id, vault))
            var plaintext = RatchetStateContainer.encode(conversation_id, working, sync_version)
                .toByteArray(Charsets.UTF_8)

            var known_version: Int? = known_versions[conversation_id]
            var last_error = "sync failed"

            for (attempt in 0 until MAX_ATTEMPTS) {
                val write_v3 = format_ready || remote_v3_seen[conversation_id] == true
                val nonce = RatchetCrypto.random_bytes(12)
                val ct_b64 = if (write_v3) {
                    val header_bytes = RatchetStateContainer.encode_header(
                        RatchetStateHeader(working.epoch.toLong(), sync_version),
                    )
                    val ciphertext = RatchetCrypto.aes_gcm_encrypt(
                        plaintext,
                        keys.state_key,
                        nonce,
                        RatchetStateContainer.sync_aad(keys.user_id, conversation_id, header_bytes),
                    )
                    RatchetCrypto.b64_encode(header_bytes + ciphertext)
                } else {
                    RatchetCrypto.b64_encode(
                        RatchetCrypto.aes_gcm_encrypt(
                            plaintext,
                            keys.compat_key,
                            nonce,
                            RatchetStateContainer.bound_aad(conversation_id),
                        ),
                    )
                }
                val nonce_b64 = RatchetCrypto.b64_encode(nonce)

                if (known_version == null) {
                    val existing = try { ratchet_api.fetch_state(conv_b64) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { null }
                    if (existing == null) {
                        sync_vault.intend(conversation_id, sync_version)
                        when (val outcome = try_post(conv_b64, ct_b64, nonce_b64)) {
                            is PostStateOutcome.Success -> {
                                state_store.raise_sync_floor(conversation_id, sync_version)
                                sync_vault.record(conversation_id, sync_version, write_v3)
                                if (write_v3) remote_v3_seen[conversation_id] = true
                                known_versions[conversation_id] = outcome.response.state_version
                                missing_state_at.remove(conversation_id)
                                synced_fingerprints[conversation_id] = fingerprint
                                return true
                            }
                            PostStateOutcome.AlreadyExists -> {
                                last_error = "post 409"
                                delay(50L * (attempt + 1))
                                continue
                            }
                            is PostStateOutcome.Failure -> {
                                last_error = "post failed status=${outcome.status}"
                                delay(50L * (attempt + 1))
                                continue
                            }
                        }
                    } else {
                        known_version = existing.state_version
                        known_versions[conversation_id] = existing.state_version
                        when (val absorbed = absorb_remote(conversation_id, working)) {
                            is Absorb.Merged -> {
                                working = absorbed.state
                                fingerprint = RatchetStateContainer.state_fingerprint(working)
                                sync_version = RatchetStateContainer.next_version(effective_floor(conversation_id, vault))
                                plaintext = RatchetStateContainer.encode(conversation_id, working, sync_version)
                                    .toByteArray(Charsets.UTF_8)
                                known_version = known_versions[conversation_id] ?: known_version
                            }
                            Absorb.Overwrite -> Unit
                            Absorb.Abort -> return abandon_sync(conversation_id)
                        }
                        continue
                    }
                }

                val current_version = known_version ?: continue
                sync_vault.intend(conversation_id, sync_version)
                when (val outcome = try_put(conv_b64, ct_b64, nonce_b64, current_version)) {
                    is PutStateOutcome.Success -> {
                        state_store.raise_sync_floor(conversation_id, sync_version)
                        sync_vault.record(conversation_id, sync_version, write_v3)
                        if (write_v3) remote_v3_seen[conversation_id] = true
                        known_versions[conversation_id] = outcome.response.state_version
                        missing_state_at.remove(conversation_id)
                        synced_fingerprints[conversation_id] = fingerprint
                        return true
                    }
                    PutStateOutcome.VersionConflict -> {
                        when (val absorbed = absorb_remote(conversation_id, working)) {
                            is Absorb.Merged -> {
                                working = absorbed.state
                                fingerprint = RatchetStateContainer.state_fingerprint(working)
                                sync_version = RatchetStateContainer.next_version(effective_floor(conversation_id, vault))
                                plaintext = RatchetStateContainer.encode(conversation_id, working, sync_version)
                                    .toByteArray(Charsets.UTF_8)
                                known_version = known_versions[conversation_id]
                            }
                            Absorb.Overwrite -> {
                                val recheck = try { ratchet_api.fetch_state(conv_b64) } catch (c: kotlinx.coroutines.CancellationException) { throw c } catch (_: Throwable) { null }
                                if (recheck != null) {
                                    known_version = recheck.state_version
                                    known_versions[conversation_id] = recheck.state_version
                                }
                            }
                            Absorb.Abort -> return abandon_sync(conversation_id)
                        }
                        last_error = "put 409"
                        delay(50L * (attempt + 1))
                    }
                    PutStateOutcome.NotFound -> {
                        if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync put 404 -> server lost state, re-posting local copy")
                        known_version = null
                        known_versions.remove(conversation_id)
                        delay(50L * (attempt + 1))
                    }
                    is PutStateOutcome.Failure -> {
                        last_error = "put failed status=${outcome.status}"
                        delay(50L * (attempt + 1))
                    }
                }
            }
            if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync exhausted attempts ($last_error) -> keeping local state, will retry later")
            known_versions.remove(conversation_id)
            synced_fingerprints.remove(conversation_id)
            return false
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync threw: ${t.javaClass.simpleName}")
            return false
        } finally {
            keys.wipe()
        }
    }

    private fun abandon_sync(conversation_id: String): Boolean {
        if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync refused: server state is not readable here, keeping it")
        known_versions.remove(conversation_id)
        synced_fingerprints.remove(conversation_id)
        return false
    }

    private suspend fun try_post(
        conv_b64: String,
        ct_b64: String,
        nonce_b64: String,
    ): PostStateOutcome {
        return try {
            ratchet_api.post_state(conv_b64, ct_b64, nonce_b64)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            PostStateOutcome.Failure(-1)
        }
    }

    private suspend fun try_put(
        conv_b64: String,
        ct_b64: String,
        nonce_b64: String,
        expected_version: Int,
    ): PutStateOutcome {
        return try {
            ratchet_api.put_state(conv_b64, ct_b64, nonce_b64, expected_version)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            PutStateOutcome.Failure(-1)
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
        private const val MISSING_STATE_TTL_MS = 60L * 1000L
    }
}
