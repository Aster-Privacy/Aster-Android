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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.astermail.android.BuildConfig
import org.astermail.android.api.ratchet.PostStateOutcome
import org.astermail.android.api.ratchet.PutStateOutcome
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto

@Singleton
class RatchetStateSyncer @Inject constructor(
    private val state_store: RatchetStateStore,
    private val ratchet_api: RatchetApi,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val locks = mutableMapOf<String, Mutex>()
    private val locks_guard = Mutex()
    private val known_versions = mutableMapOf<String, Int>()

    private suspend fun lock_for(conversation_id: String): Mutex = locks_guard.withLock {
        locks.getOrPut(conversation_id) { Mutex() }
    }

    fun decode_server_state(plaintext_json: String): RatchetState? {
        return try {
            val parsed = json.parseToJsonElement(plaintext_json)
            if (parsed is JsonObject && parsed.containsKey("state")) {
                json.decodeFromString(RatchetState.serializer(), parsed["state"]!!.toString())
            } else {
                json.decodeFromString(RatchetState.serializer(), plaintext_json)
            }
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun fetch_from_server(conversation_id: String): RatchetState? {
        val key = state_store.derive_state_encryption_key() ?: return null
        return try {
            val conv_b64 = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8))
            val resp = ratchet_api.fetch_state(conv_b64) ?: return null
            val ciphertext = RatchetCrypto.b64_decode(resp.encrypted_state)
            val nonce = RatchetCrypto.b64_decode(resp.state_nonce)
            val plaintext = RatchetCrypto.aes_gcm_decrypt(ciphertext, key, nonce, null)
            val state = decode_server_state(String(plaintext, Charsets.UTF_8)) ?: return null
            known_versions[conversation_id] = resp.state_version
            state
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "fetch_from_server failed", t)
            null
        } finally {
            key.fill(0)
        }
    }

    suspend fun sync(conversation_id: String, state: RatchetState): Boolean {
        return lock_for(conversation_id).withLock { do_sync(conversation_id, state) }
    }

    suspend fun reset(conversation_id: String) {
        lock_for(conversation_id).withLock {
            known_versions.remove(conversation_id)
            state_store.delete(conversation_id)
            try {
                val conv_b64 = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8))
                ratchet_api.delete_state(conv_b64)
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "reset: server delete failed (continuing)", t)
            }
        }
    }

    private suspend fun do_sync(conversation_id: String, state: RatchetState): Boolean {
        val key = state_store.derive_state_encryption_key() ?: return false
        try {
            val conv_b64 = RatchetCrypto.b64_encode(conversation_id.toByteArray(Charsets.UTF_8))
            val state_element = json.encodeToString(RatchetState.serializer(), state)
            val container = buildJsonObject {
                put("state", json.parseToJsonElement(state_element))
                put("conversation_id", JsonPrimitive(conversation_id))
            }
            val plaintext = container.toString().toByteArray(Charsets.UTF_8)

            var known_version: Int? = known_versions[conversation_id]
            var last_error = "sync failed"

            for (attempt in 0 until MAX_ATTEMPTS) {
                val nonce = RatchetCrypto.random_bytes(12)
                val ciphertext = RatchetCrypto.aes_gcm_encrypt(plaintext, key, nonce, null)
                val ct_b64 = RatchetCrypto.b64_encode(ciphertext)
                val nonce_b64 = RatchetCrypto.b64_encode(nonce)

                if (known_version == null) {
                    val existing = try { ratchet_api.fetch_state(conv_b64) } catch (_: Throwable) { null }
                    if (existing == null) {
                        when (val outcome = try_post(conv_b64, ct_b64, nonce_b64)) {
                            is PostStateOutcome.Success -> {
                                known_versions[conversation_id] = outcome.response.state_version
                                return true
                            }
                            PostStateOutcome.AlreadyExists -> {
                                val recheck = try { ratchet_api.fetch_state(conv_b64) } catch (_: Throwable) { null }
                                if (recheck == null) {
                                    last_error = "post 409 recheck failed"
                                    delay(50L * (attempt + 1))
                                    continue
                                }
                                known_version = recheck.state_version
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
                    }
                }

                val current_version = known_version ?: continue
                when (val outcome = try_put(conv_b64, ct_b64, nonce_b64, current_version)) {
                    is PutStateOutcome.Success -> {
                        known_versions[conversation_id] = outcome.response.state_version
                        return true
                    }
                    PutStateOutcome.VersionConflict -> {
                        val recheck = try { ratchet_api.fetch_state(conv_b64) } catch (_: Throwable) { null }
                        if (recheck != null) {
                            known_version = recheck.state_version
                            known_versions[conversation_id] = recheck.state_version
                        }
                        last_error = "put 409"
                        delay(50L * (attempt + 1))
                    }
                    PutStateOutcome.NotFound -> {
                        if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync put 404 -> wiping local state to force rebootstrap on next send")
                        known_versions.remove(conversation_id)
                        state_store.delete(conversation_id)
                        return false
                    }
                    is PutStateOutcome.Failure -> {
                        last_error = "put failed status=${outcome.status}"
                        delay(50L * (attempt + 1))
                    }
                }
            }
            if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync exhausted attempts ($last_error) -> keeping local state, will retry later")
            known_versions.remove(conversation_id)
            return false
        } catch (t: Throwable) {
            if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "sync threw", t)
            return false
        } finally {
            key.fill(0)
        }
    }

    private suspend fun try_post(
        conv_b64: String,
        ct_b64: String,
        nonce_b64: String,
    ): PostStateOutcome {
        return try {
            ratchet_api.post_state(conv_b64, ct_b64, nonce_b64)
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
        } catch (t: Throwable) {
            PutStateOutcome.Failure(-1)
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 4
    }
}
