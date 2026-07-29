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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.mail.strip_body_framing
import org.astermail.android.storage.SessionKeyStore

const val RATCHET_UNDECRYPTABLE_SENTINEL = "\u0000ASTER_RATCHET_UNDECRYPTABLE\u0000"

private const val VAULT_REFRESH_COOLDOWN_MS = 5L * 60L * 1000L
private const val FORCED_RECOVERY_WINDOW_MS = 30L * 1000L
private const val PQ_SECRET_MISS_TTL_MS = 10L * 60L * 1000L

@Singleton
class RatchetDecryptor @Inject constructor(
    private val state_store: RatchetStateStore,
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val syncer: RatchetStateSyncer,
    private val conversation_locks: ConversationLocks,
    private val auth_repository: dagger.Lazy<org.astermail.android.auth.AuthRepository>,
) {

    private data class ReceiverKeySet(
        val identity_jwk: String,
        val identity_public_b64: String,
        val signed_prekey_jwk: String,
        val signed_prekey_public_b64: String,
    )

    @Volatile
    private var last_vault_refresh_at = 0L

    @Volatile
    private var forced_recovery_until = 0L

    private val pq_secret_missed_at = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    fun begin_forced_recovery() {
        forced_recovery_until = System.currentTimeMillis() + FORCED_RECOVERY_WINDOW_MS
        last_vault_refresh_at = 0L
        pq_secret_missed_at.clear()
        syncer.clear_missing_state_cache()
    }

    private fun forced_recovery_active(): Boolean = System.currentTimeMillis() < forced_recovery_until

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun looks_like_ratchet_envelope(body: String): Boolean {
        val trimmed = strip_body_framing(body)
        if (!trimmed.startsWith("{")) return false
        return trimmed.contains("\"double_ratchet_v1\"") || trimmed.contains("\"double_ratchet_v2\"")
    }

    suspend fun try_decrypt(body: String, our_addresses: List<String>, sender_email: String): String {
        if (!looks_like_ratchet_envelope(body)) return body
        val envelope = parse_envelope(body)
        if (envelope == null) {
            if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "parse_envelope returned null")
            return RATCHET_UNDECRYPTABLE_SENTINEL
        }
        if (!session_key_store.has_ratchet_keys()) {
            if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "no ratchet keys in session_key_store")
            return RATCHET_UNDECRYPTABLE_SENTINEL
        }
        return try {
            val result = decrypt(envelope, our_addresses, sender_email)
            if (result == null && org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "decrypt() returned null")
            result ?: RATCHET_UNDECRYPTABLE_SENTINEL
        } catch (t: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.e("AsterRatchet", "decrypt threw")
            RATCHET_UNDECRYPTABLE_SENTINEL
        }
    }

    private fun parse_envelope(body: String): RatchetEnvelope? {
        val payload = strip_body_framing(body).trimEnd()
        return try {
            val parsed = json.parseToJsonElement(payload) as? JsonObject ?: return null
            val type = parsed["type"]?.jsonPrimitive?.content
            if (type != "double_ratchet_v1" && type != "double_ratchet_v2") return null
            json.decodeFromString(RatchetEnvelope.serializer(), payload)
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun decrypt(envelope: RatchetEnvelope, our_addresses: List<String>, sender_email: String): String? {
        val owned_lower = our_addresses.map { it.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
        val matched = envelope.recipients.entries.firstOrNull { it.key.trim().lowercase() in owned_lower }
        if (matched == null) {
            if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "no recipient match")
            return null
        }
        val matched_address = matched.key
        val recipient = matched.value
        val conversation_id = X3dh.derive_conversation_id(matched_address, sender_email)

        return conversation_locks.with_lock(conversation_id) {
            val is_fresh_bootstrap = recipient.ephemeral_key != null &&
                recipient.header.message_number == 0 &&
                recipient.header.previous_chain_length == 0
            val can_bootstrap = recipient.ephemeral_key != null

            var state = state_store.load(conversation_id)
            val state_loaded_locally = state != null
            if (state != null && is_fresh_bootstrap) {
                state = null
            }
            if (state == null) {
                val from_server = syncer.fetch_from_server(conversation_id)
                if (from_server != null) {
                    state_store.save(from_server)
                    state = from_server
                }
            }

            var plaintext: String? = null
            var decrypt_error: Throwable? = null

            if (state != null) {
                try {
                    plaintext = DoubleRatchet.decrypt(state!!, recipient)
                } catch (e: Throwable) {
                    if (!can_bootstrap) throw e
                    decrypt_error = e
                }
            }

            val forced = forced_recovery_active()
            if (plaintext == null && (decrypt_error != null || forced) && state_loaded_locally && !is_fresh_bootstrap) {
                val refreshed = try {
                    syncer.fetch_from_server(conversation_id)
                } catch (_: Throwable) {
                    null
                }
                if (refreshed != null) {
                    try {
                        plaintext = DoubleRatchet.decrypt(refreshed, recipient)
                        state = refreshed
                        decrypt_error = null
                    } catch (e2: Throwable) {
                        decrypt_error = e2
                    }
                }
            }

            if (plaintext == null && can_bootstrap) {
                val tried = mutableSetOf<String>()
                var recovered = decrypt_with_key_sets(
                    conversation_id,
                    envelope.sender_identity_key,
                    recipient,
                    tried,
                )
                if (recovered == null && refresh_vault_keys()) {
                    recovered = decrypt_with_key_sets(
                        conversation_id,
                        envelope.sender_identity_key,
                        recipient,
                        tried,
                    )
                }
                if (recovered != null) {
                    state = recovered.first
                    plaintext = recovered.second
                }
            }

            if (plaintext == null) {
                decrypt_error?.let { throw it }
                return@with_lock null
            }

            val final_state = state ?: return@with_lock null
            state_store.save(final_state)
            syncer.sync(conversation_id, final_state)
            plaintext
        }
    }

    private fun receiver_key_sets(): List<ReceiverKeySet> {
        val sets = mutableListOf<ReceiverKeySet>()
        val identity_jwk = session_key_store.get_ratchet_identity_jwk()
        val identity_pub = session_key_store.get_ratchet_identity_public_b64()
        val spk_jwk = session_key_store.get_ratchet_signed_prekey_jwk()
        val spk_pub = session_key_store.get_ratchet_signed_prekey_public_b64()
        if (identity_jwk != null && spk_jwk != null && spk_pub != null) {
            sets.add(ReceiverKeySet(identity_jwk, identity_pub ?: "", spk_jwk, spk_pub))
        }
        val previous_json = session_key_store.get_ratchet_previous_keys_json()
        if (!previous_json.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(previous_json)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val prev_identity = obj.optString("ratchet_identity_key", "")
                    val prev_identity_pub = obj.optString("ratchet_identity_public", "")
                    val prev_spk = obj.optString("ratchet_signed_prekey", "")
                    val prev_spk_pub = obj.optString("ratchet_signed_prekey_public", "")
                    if (prev_identity.isNotBlank() && prev_spk.isNotBlank() && prev_spk_pub.isNotBlank()) {
                        sets.add(ReceiverKeySet(prev_identity, prev_identity_pub, prev_spk, prev_spk_pub))
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return sets
    }

    private suspend fun decrypt_with_key_sets(
        conversation_id: String,
        sender_identity_key_b64: String,
        recipient: RatchetRecipientData,
        tried: MutableSet<String>,
    ): Pair<RatchetState, String>? {
        for (keys in receiver_key_sets()) {
            val tag = keys.identity_public_b64.ifBlank { keys.identity_jwk }
            if (!tried.add(tag)) continue
            val candidate = try {
                bootstrap(conversation_id, sender_identity_key_b64, recipient, keys)
            } catch (_: Throwable) {
                null
            } ?: continue
            try {
                return candidate to DoubleRatchet.decrypt(candidate, recipient)
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private suspend fun refresh_vault_keys(): Boolean {
        val now = System.currentTimeMillis()
        if (now - last_vault_refresh_at < VAULT_REFRESH_COOLDOWN_MS) return false
        last_vault_refresh_at = now
        return try {
            auth_repository.get().try_refresh_vault_keys()
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun bootstrap(
        conversation_id: String,
        sender_identity_key_b64: String,
        recipient: RatchetRecipientData,
        keys: ReceiverKeySet,
    ): RatchetState? {
        val ephemeral_b64 = recipient.ephemeral_key ?: return null
        val identity_jwk = keys.identity_jwk
        val spk_jwk = keys.signed_prekey_jwk
        val spk_pub_b64 = keys.signed_prekey_public_b64

        val sender_identity_raw = RatchetCrypto.b64_decode(sender_identity_key_b64)
        val sender_ephemeral_raw = RatchetCrypto.b64_decode(ephemeral_b64)

        val pq_shared = if (recipient.pq_ciphertext != null && recipient.pq_key_id != null) {
            val pq_secret = fetch_pq_secret(recipient.pq_key_id!!)
            if (pq_secret == null) {
                if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "pq secret unavailable")
                return null
            }
            try {
                val pq_ct = RatchetCrypto.b64_decode(recipient.pq_ciphertext!!)
                RatchetCrypto.ml_kem_768_decapsulate(pq_ct, pq_secret)
            } finally {
                pq_secret.fill(0)
            }
        } else null

        val shared_secret = X3dh.perform_receiver(
            receiver_identity_jwk = identity_jwk,
            receiver_signed_prekey_jwk = spk_jwk,
            sender_identity_raw = sender_identity_raw,
            sender_ephemeral_raw = sender_ephemeral_raw,
            pq_shared_secret = pq_shared,
        )
        pq_shared?.fill(0)

        return X3dh.init_receiver_state(
            conversation_id = conversation_id,
            shared_secret = shared_secret,
            own_signed_prekey_jwk = spk_jwk,
            own_signed_prekey_public_b64 = spk_pub_b64,
        ).also { shared_secret.fill(0) }
    }

    private fun pq_secret_recently_missed(key_id: Int): Boolean {
        val missed_at = pq_secret_missed_at[key_id] ?: return false
        if (System.currentTimeMillis() - missed_at < PQ_SECRET_MISS_TTL_MS) return true
        pq_secret_missed_at.remove(key_id)
        return false
    }

    private fun mark_pq_secret_missed(key_id: Int) {
        pq_secret_missed_at[key_id] = System.currentTimeMillis()
    }

    private suspend fun fetch_pq_secret(key_id: Int): ByteArray? {
        session_key_store.get_pq_secret(key_id)?.let { return it }
        if (pq_secret_recently_missed(key_id)) return null
        val resp = try { ratchet_api.fetch_pq_secret(key_id) } catch (_: Throwable) { null }
        if (resp == null) {
            mark_pq_secret_missed(key_id)
            return null
        }
        val key = state_store.derive_state_encryption_key() ?: run {
            mark_pq_secret_missed(key_id)
            return null
        }
        return try {
            val ct = RatchetCrypto.b64_decode(resp.encrypted_secret)
            val nonce = RatchetCrypto.b64_decode(resp.secret_nonce)
            val pt = RatchetCrypto.aes_gcm_decrypt(ct, key, nonce, null)
            session_key_store.put_pq_secret(key_id, pt)
            pq_secret_missed_at.remove(key_id)
            pt
        } catch (t: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "pq decrypt failed", t)
            mark_pq_secret_missed(key_id)
            null
        } finally {
            key.fill(0)
        }
    }

}
