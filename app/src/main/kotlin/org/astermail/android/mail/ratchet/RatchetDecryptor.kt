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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.astermail.android.api.keys.KeysApi
import org.astermail.android.api.ratchet.OwnRatchetIdentityLookup
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.astermail.android.mail.strip_body_framing
import org.astermail.android.storage.SessionKeyStore

const val RATCHET_UNDECRYPTABLE_SENTINEL = "\u0000ASTER_RATCHET_UNDECRYPTABLE\u0000"

private const val VAULT_REFRESH_COOLDOWN_MS = 5L * 60L * 1000L
private const val FORCED_RECOVERY_WINDOW_MS = 30L * 1000L
private const val PQ_SECRET_MISS_TTL_MS = 10L * 60L * 1000L
private const val MAX_ALIAS_RECIPIENT_ATTEMPTS = 8

@Singleton
class RatchetDecryptor @Inject constructor(
    private val state_store: RatchetStateStore,
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val syncer: RatchetStateSyncer,
    private val conversation_locks: ConversationLocks,
    private val auth_repository: dagger.Lazy<org.astermail.android.auth.AuthRepository>,
    private val identity_pins: RatchetIdentityPinStore,
    private val plaintext_cache: RatchetPlaintextCache,
    private val escrow: MessageEscrow,
    private val address_directory: AddressIdentityDirectory,
    private val keys_api: KeysApi,
    private val lane_replay_vault: LaneReplayVault,
) {

    private data class ReceiverKeySet(
        val identity_jwk: String,
        val identity_public_b64: String,
        val signed_prekey_jwk: String,
        val signed_prekey_public_b64: String,
        val pq_identity_secret: String? = null,
    )

    @Volatile
    private var last_vault_refresh_at = 0L

    @Volatile
    private var forced_recovery_until = 0L

    private val pq_secret_missed_at = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    private val ec_one_time_prekeys by lazy {
        EcOneTimePrekeyStore(
            storage = SessionEcOneTimeSecretStorage(session_key_store),
            ratchet_api = ratchet_api,
            sync_key_candidates = { state_store.state_encryption_key_candidates() },
            prekey_seal = { PrekeySeal.from_store(session_key_store) },
        )
    }

    private val escrow_upgrade_attempted = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val background = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    fun begin_forced_recovery() {
        forced_recovery_until = System.currentTimeMillis() + FORCED_RECOVERY_WINDOW_MS
        last_vault_refresh_at = 0L
        pq_secret_missed_at.clear()
        ec_one_time_prekeys.clear_missing()
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

    suspend fun try_decrypt(
        body: String,
        our_addresses: List<String>,
        sender_email: String,
        message_id: String? = null,
    ): String {
        if (!looks_like_ratchet_envelope(body)) return body
        val envelope = parse_envelope(body)
        if (envelope == null) {
            return RATCHET_UNDECRYPTABLE_SENTINEL
        }
        if (!session_key_store.has_ratchet_keys()) {
            refresh_vault_keys()
            if (!session_key_store.has_ratchet_keys()) {
                return RATCHET_UNDECRYPTABLE_SENTINEL
            }
        }
        return try {
            val result = decrypt(envelope, our_addresses, sender_email, message_id)
            if (result == null && org.astermail.android.BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "decrypt() returned null")
            result ?: RATCHET_UNDECRYPTABLE_SENTINEL
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            if (org.astermail.android.BuildConfig.DEBUG) android.util.Log.e("AsterRatchet", "decrypt threw")
            RATCHET_UNDECRYPTABLE_SENTINEL
        }
    }

    suspend fun settle_cached_sender(
        body: String,
        our_addresses: List<String>,
        sender_email: String,
        message_id: String?,
    ) {
        if (message_id.isNullOrBlank()) return
        if (!plaintext_cache.is_sender_unconfirmed(message_id)) return
        if (plaintext_cache.is_sender_unauthenticated(message_id)) return
        val envelope = parse_envelope(body) ?: return
        val address = our_addresses.firstOrNull { address ->
            envelope.recipients.keys.any { it.equals(address, ignoreCase = true) }
        } ?: our_addresses.firstOrNull() ?: return
        val conversation_id = X3dh.derive_conversation_id(address, sender_email)
        val bound_key = runCatching { state_store.load(conversation_id)?.remote_identity_key }.getOrNull() ?: return
        val continuity = sender_continuity(conversation_id, sender_email, bound_key, session_bound = true, address)
        if (continuity != SenderIdentityContinuity.KNOWN) return
        runCatching { plaintext_cache.confirm_sender(message_id) }
    }

    fun schedule_escrow_upgrade(
        body: String,
        our_addresses: List<String>,
        sender_email: String,
        message_id: String?,
    ) {
        if (message_id.isNullOrBlank()) return
        if (!plaintext_cache.is_sender_unauthenticated(message_id)) return
        if (message_id in escrow_upgrade_attempted) return
        background.launch {
            runCatching { upgrade_from_escrow(body, our_addresses, sender_email, message_id) }
        }
    }

    suspend fun upgrade_from_escrow(
        body: String,
        our_addresses: List<String>,
        sender_email: String,
        message_id: String?,
    ): String? {
        if (message_id.isNullOrBlank()) return null
        if (!plaintext_cache.is_sender_unauthenticated(message_id)) return null
        if (!escrow_upgrade_attempted.add(message_id)) return null
        if (!looks_like_ratchet_envelope(body)) return null
        val envelope = parse_envelope(body) ?: return null
        for ((address, recipient) in escrow_candidates(envelope, our_addresses, sender_email)) {
            val conversation_id = X3dh.derive_conversation_id(address, sender_email)
            val escrowed = fetch_escrow(message_id, recipient, sender_email, conversation_id) ?: continue
            if (!escrowed.verified) continue
            cache_plaintext(
                message_id,
                escrowed.plaintext,
                unconfirmed_sender = escrowed.unconfirmed_sender,
                binding = PlaintextBinding(sender_email, conversation_id),
            )
            return escrowed.plaintext
        }
        return null
    }

    private fun escrow_candidates(
        envelope: RatchetEnvelope,
        our_addresses: List<String>,
        sender_email: String,
    ): List<Pair<String, RatchetRecipientData>> {
        val owned_lower = our_addresses.map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.toSet()
        val sender_lower = sender_email.trim().lowercase(java.util.Locale.ROOT)
        val (owned, others) = envelope.recipients.entries.partition {
            it.key.trim().lowercase(java.util.Locale.ROOT) in owned_lower
        }
        val fallback = others
            .filter { it.key.trim().lowercase(java.util.Locale.ROOT) != sender_lower }
            .take(MAX_ALIAS_RECIPIENT_ATTEMPTS)
        return (owned + fallback).map { it.key to it.value }
    }

    private suspend fun fetch_escrow(
        message_id: String?,
        recipient: RatchetRecipientData,
        sender_email: String,
        conversation_id: String,
    ): EscrowedPlaintext? {
        if (message_id.isNullOrBlank()) return null
        return try {
            escrow.fetch_bound(MessageEscrow.dedupe_key(message_id, recipient.header), sender_email, conversation_id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
    }

    private fun upload_escrow(
        message_id: String?,
        recipient: RatchetRecipientData,
        plaintext: String,
        sender_email: String,
        conversation_id: String,
        unconfirmed_sender: Boolean,
    ) {
        if (message_id.isNullOrBlank()) return
        runCatching {
            escrow.upload_bound_in_background(
                MessageEscrow.dedupe_key(message_id, recipient.header),
                plaintext,
                sender_email,
                conversation_id,
                unconfirmed_sender,
            )
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

    private suspend fun decrypt(
        envelope: RatchetEnvelope,
        our_addresses: List<String>,
        sender_email: String,
        message_id: String?,
    ): String? {
        val owned_lower = our_addresses.map { it.trim().lowercase(java.util.Locale.ROOT) }.filter { it.isNotBlank() }.toSet()
        val matched = envelope.recipients.entries.firstOrNull { it.key.trim().lowercase(java.util.Locale.ROOT) in owned_lower }
        if (matched != null) {
            return decrypt_entry(envelope, matched.key, matched.value, sender_email, message_id)
        }

        val sender_lower = sender_email.trim().lowercase(java.util.Locale.ROOT)
        val fallback = envelope.recipients.entries
            .filter { it.key.trim().lowercase(java.util.Locale.ROOT) !in owned_lower && it.key.trim().lowercase(java.util.Locale.ROOT) != sender_lower }
            .take(MAX_ALIAS_RECIPIENT_ATTEMPTS)

        for (entry in fallback) {
            val plaintext = try {
                decrypt_entry(envelope, entry.key, entry.value, sender_email, message_id)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (_: Throwable) {
                null
            }
            if (plaintext != null) return plaintext
        }

        return null
    }

    private suspend fun decrypt_entry(
        envelope: RatchetEnvelope,
        matched_address: String,
        recipient: RatchetRecipientData,
        sender_email: String,
        message_id: String?,
    ): String? {
        val conversation_id = X3dh.derive_conversation_id(matched_address, sender_email)
        val binding = PlaintextBinding(sender_email, conversation_id)

        return conversation_locks.with_lock(conversation_id) {
            val is_fresh_bootstrap = recipient.ephemeral_key != null &&
                recipient.header.message_number == 0 &&
                recipient.header.previous_chain_length == 0
            val can_bootstrap = recipient.ephemeral_key != null

            var state = state_store.load(conversation_id)
            val state_loaded_locally = state != null
            val loaded_dh_chains = state?.let { loaded ->
                setOfNotNull(loaded.dh_remote_public) + loaded.skipped_message_keys.map { it.dh_public }
            }.orEmpty()
            val established_identity_key = state?.remote_identity_key
            val bootstrap_already_accepted = can_bootstrap && runCatching {
                identity_pins.is_replayed_bootstrap(conversation_id, recipient.ephemeral_key.orEmpty())
            }.getOrDefault(false)
            val replayed_bootstrap = is_fresh_bootstrap && state != null && bootstrap_already_accepted
            if (state != null && is_fresh_bootstrap && !replayed_bootstrap) {
                state = null
            }
            if (state == null) {
                val from_server = syncer.fetch_from_server(conversation_id)
                if (from_server != null) {
                    state = from_server
                }
            }

            var plaintext: String? = null
            var decrypt_error: Throwable? = null
            var chained = false
            var bootstrap_claim: BootstrapClaim? = null

            if (state != null) {
                try {
                    plaintext = DoubleRatchet.decrypt(state!!, recipient)
                    chained = true
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (e: Throwable) {
                    decrypt_error = e
                }
            }

            val forced = forced_recovery_active()
            if (plaintext == null && (decrypt_error != null || forced) && state_loaded_locally && !is_fresh_bootstrap) {
                val refreshed = try {
                    syncer.fetch_from_server(conversation_id)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (_: Throwable) {
                    null
                }
                if (refreshed != null) {
                    try {
                        plaintext = DoubleRatchet.decrypt(refreshed, recipient)
                        state = refreshed
                        chained = true
                        decrypt_error = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
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
                    matched_address,
                )
                if (recovered == null) {
                    refresh_vault_keys()
                    recovered = decrypt_with_key_sets(
                        conversation_id,
                        envelope.sender_identity_key,
                        recipient,
                        tried,
                        matched_address,
                    )
                }
                if (recovered != null) {
                    val claim = identity_pins.claim_bootstrap(
                        sender_email,
                        envelope.sender_identity_key,
                        recipient,
                        message_id,
                    )
                    if (claim == BootstrapClaim.REPLAYED) {
                        throw IllegalStateException("opening message was already used")
                    }
                    bootstrap_claim = claim
                    state = recovered.first
                    plaintext = recovered.second
                }
            }
            val bootstrapped = bootstrap_claim != null

            suspend fun settle(resolved: String, unconfirmed: Boolean, saved_state: RatchetState?) {
                withContext(NonCancellable) {
                    cache_plaintext(message_id, resolved, unconfirmed_sender = unconfirmed, binding = binding)
                    saved_state?.let { state_store.save(it) }
                    if (bootstrapped) consume_pq_prekey(recipient)
                }
                if (bootstrapped) consume_ec_one_time_prekey(recipient)
            }

            if (plaintext == null) {
                val escrowed = fetch_escrow(message_id, recipient, sender_email, conversation_id)
                if (escrowed != null) {
                    cache_plaintext(
                        message_id,
                        escrowed.plaintext,
                        sender_unauthenticated = !escrowed.verified,
                        unconfirmed_sender = escrowed.unconfirmed_sender,
                        binding = binding,
                    )
                    return@with_lock escrowed.plaintext
                }
                val recovered = try_recovery_lane(
                    recipient,
                    conversation_id,
                    envelope.sender_identity_key,
                    matched_address,
                )
                if (recovered == null && recipient.recovery != null) {
                    refresh_vault_keys()
                }
                val final_recovery = recovered ?: try_recovery_lane(
                    recipient,
                    conversation_id,
                    envelope.sender_identity_key,
                    matched_address,
                )
                if (final_recovery != null) {
                    val continuity = sender_continuity(
                        conversation_id,
                        sender_email,
                        envelope.sender_identity_key,
                        session_bound = false,
                        matched_address,
                    )
                    if (continuity != SenderIdentityContinuity.CHANGED &&
                        RatchetIdentityPinRules.records_identity(IdentitySighting.RECOVERY_LANE, bootstrap_already_accepted)
                    ) {
                        record_identity_pin(conversation_id, sender_email, envelope.sender_identity_key)
                    }
                    if (continuity == SenderIdentityContinuity.CHANGED) {
                        flag_sender_rotation(conversation_id, sender_email, envelope.sender_identity_key)
                    }
                    val verdict = recovery_lane_verdict(
                        final_recovery,
                        continuity,
                        conversation_id,
                        sender_email,
                        lane_message_identity(message_id, recipient.header),
                    )
                    cache_plaintext(
                        message_id,
                        final_recovery.plaintext,
                        sender_unauthenticated = verdict != RecoveryLaneVerdict.CONFIRMED,
                        binding = binding,
                    )
                    return@with_lock final_recovery.plaintext
                }
                decrypt_error?.let { throw it }
                return@with_lock null
            }

            val final_state = state ?: return@with_lock null
            val resolved = plaintext ?: return@with_lock null
            val identity_key = final_state.remote_identity_key ?: envelope.sender_identity_key
            val continuity = sender_continuity(
                conversation_id,
                sender_email,
                identity_key,
                session_bound = final_state.remote_identity_key != null,
                matched_address,
            )
            val directory_confirmed = continuity != SenderIdentityContinuity.KNOWN &&
                directory_confirms_identity(sender_email, identity_key)
            val trusted_sighting = continuity == SenderIdentityContinuity.KNOWN ||
                (continuity == SenderIdentityContinuity.NEW && directory_confirmed)
            val unconfirmed_sender = continuity != SenderIdentityContinuity.KNOWN
            if (!chained && state_loaded_locally &&
                (bootstrap_claim != BootstrapClaim.FIRST || recipient.header.dh_public in loaded_dh_chains)
            ) {
                settle(resolved, unconfirmed_sender, null)
                return@with_lock resolved
            }
            if (!chained && state_loaded_locally &&
                !bootstrap_may_replace_session(
                    conversation_id,
                    sender_email,
                    established_identity_key,
                    identity_key,
                    continuity,
                    directory_confirmed,
                )
            ) {
                flag_sender_rotation(conversation_id, sender_email, identity_key)
                settle(resolved, true, null)
                return@with_lock resolved
            }
            if (chained && final_state.remote_identity_key == null && continuity == SenderIdentityContinuity.KNOWN) {
                final_state.remote_identity_key = identity_key
            }
            val sighting = if (chained) IdentitySighting.CHAINED else IdentitySighting.BOOTSTRAP
            if (trusted_sighting &&
                RatchetIdentityPinRules.records_identity(sighting, bootstrap_already_accepted)
            ) {
                record_identity_pin(conversation_id, sender_email, identity_key)
            }
            if (is_fresh_bootstrap || sighting == IdentitySighting.BOOTSTRAP) {
                runCatching {
                    identity_pins.record_bootstrap(conversation_id, recipient.ephemeral_key.orEmpty())
                }
            }
            if (recipient.pq_ciphertext != null || recipient.recovery?.kem_ct != null) {
                runCatching { identity_pins.record_post_quantum(conversation_id) }
            }
            recipient.x3dh_v?.let { version ->
                runCatching { identity_pins.record_x3dh_version(conversation_id, version) }
            }

            if (continuity == SenderIdentityContinuity.CHANGED) {
                flag_sender_rotation(conversation_id, sender_email, identity_key)
            } else if (trusted_sighting) {
                remember_sender_identity(sender_email, identity_key)
            }
            settle(resolved, unconfirmed_sender, final_state)
            upload_escrow(message_id, recipient, resolved, sender_email, conversation_id, unconfirmed_sender)
            syncer.sync(conversation_id, final_state)
            resolved
        }
    }

    private fun lane_message_identity(message_id: String?, header: MessageHeader): String =
        if (message_id.isNullOrBlank()) "" else "$message_id:${header.dh_public}:${header.message_number}"

    private suspend fun recovery_lane_verdict(
        recovered: RecoveredLanePlaintext,
        continuity: SenderIdentityContinuity,
        conversation_id: String,
        sender_email: String,
        message_identity: String,
    ): RecoveryLaneVerdict {
        val sender_pin = identity_pins.lane_sender_pin(sender_email)
        val freshness = if (recovered.binding != RecoveryLaneBinding.NONE) {
            claim_lane_freshness(sender_email, conversation_id, recovered, message_identity)
        } else {
            LaneFreshness.UNKNOWN
        }
        val verdict = RecoveryLaneTrust.verdict(recovered.binding, freshness, continuity, sender_pin)
        if (RecoveryLaneTrust.pins(recovered.binding, verdict)) {
            runCatching { identity_pins.pin_lane_sender(sender_email) }
        }
        return verdict
    }

    private suspend fun claim_lane_freshness(
        sender_email: String,
        conversation_id: String,
        recovered: RecoveredLanePlaintext,
        message_identity: String,
    ): LaneFreshness {
        val sender = sender_email.trim().lowercase(java.util.Locale.ROOT)
        return try {
            val claim = identity_pins.claim_lane_nonce(
                sender,
                conversation_id,
                recovered.snonce,
                message_identity,
                recovered.sent_at,
                lane_replay_vault.record(sender),
            ) ?: return LaneFreshness.UNKNOWN
            if (claim.changed) lane_replay_vault.queue(sender, claim.record)
            claim.freshness
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            LaneFreshness.UNKNOWN
        }
    }

    private suspend fun cache_plaintext(
        message_id: String?,
        plaintext: String,
        sender_unauthenticated: Boolean = false,
        unconfirmed_sender: Boolean = false,
        binding: PlaintextBinding? = null,
    ) {
        if (message_id.isNullOrBlank()) return
        runCatching { plaintext_cache.put(message_id, plaintext, sender_unauthenticated, unconfirmed_sender, binding) }
    }

    private suspend fun sender_continuity(
        conversation_id: String,
        sender_email: String,
        sender_identity_key: String,
        session_bound: Boolean,
        own_address: String,
    ): SenderIdentityContinuity {
        if (session_bound && is_own_identity_key(sender_identity_key, listOf(sender_email, own_address))) {
            return SenderIdentityContinuity.KNOWN
        }
        val stored = runCatching {
            identity_pins.sender_continuity(conversation_id, sender_email, sender_identity_key)
        }.getOrNull() ?: return SenderIdentityContinuity.CHANGED
        if (stored != SenderIdentityContinuity.CHANGED) return stored
        return if (is_endorsed_address_rotation(conversation_id, sender_email, sender_identity_key)) {
            SenderIdentityContinuity.KNOWN
        } else {
            stored
        }
    }

    private suspend fun is_endorsed_address_rotation(
        conversation_id: String,
        sender_email: String,
        sender_identity_key: String,
    ): Boolean {
        val known = try {
            (identity_pins.remembered_sender_fingerprints(sender_email) +
                listOfNotNull(identity_pins.pinned_fingerprint(conversation_id))).distinct()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            return false
        }
        if (known.isEmpty()) return false
        val endorsed = try {
            address_directory.is_sender_entry_endorsed(
                sender_email.substringBefore("@"),
                sender_email,
                sender_identity_key,
                known,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (_: Throwable) {
            false
        }
        if (endorsed) runCatching { identity_pins.replace_pin(conversation_id, sender_identity_key) }
        return endorsed
    }

    private fun is_own_identity_key(sender_identity_key: String, addresses: List<String> = emptyList()): Boolean {
        val candidate = sender_identity_key.trim()
        if (candidate.isEmpty()) return false
        if (address_directory.is_own_address_identity_key(addresses.filter { it.isNotBlank() }, candidate)) return true
        val current = runCatching { session_key_store.get_ratchet_identity_public_b64() }.getOrNull()
        if (current?.trim() == candidate) return true
        val previous_json = runCatching { session_key_store.get_ratchet_previous_keys_json() }.getOrNull()
        if (previous_json.isNullOrBlank()) return false
        return try {
            val entries = org.json.JSONArray(previous_json)
            (0 until entries.length()).any { index ->
                entries.optJSONObject(index)?.optString("ratchet_identity_public", "")?.trim() == candidate
            }
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun bootstrap_may_replace_session(
        conversation_id: String,
        sender_email: String,
        established_identity_key: String?,
        bootstrap_identity_key: String,
        continuity: SenderIdentityContinuity,
        directory_confirmed: Boolean,
    ): Boolean {
        if (established_identity_key != null && established_identity_key == bootstrap_identity_key) return true
        return when (continuity) {
            SenderIdentityContinuity.KNOWN -> true
            SenderIdentityContinuity.NEW -> directory_confirmed
            SenderIdentityContinuity.CHANGED -> directory_confirmed && (
                is_unbound_sender(sender_email) ||
                    sender_binding_vouches_for(conversation_id, sender_email, bootstrap_identity_key)
                )
        }
    }

    private fun is_unbound_sender(sender_email: String): Boolean {
        val owner_pin = try {
            identity_pins.owner_key_pin(sender_email)
        } catch (_: Throwable) {
            return false
        }
        if (!owner_pin.isNullOrBlank()) return false
        return try {
            !identity_pins.is_prekey_binding_verified(sender_email)
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun sender_binding_vouches_for(
        conversation_id: String,
        sender_email: String,
        identity_key: String,
    ): Boolean {
        val email = sender_email.trim().lowercase()
        if (!email.contains('@') || identity_key.isBlank()) return false
        val username = email.substringBefore('@')
        val saved_owner = try {
            identity_pins.owner_key_pin(sender_email)
        } catch (_: Throwable) {
            return false
        }
        val pinned_owner = saved_owner?.takeIf { it.isNotBlank() }
        val candidates = sender_bundles(username, email, conversation_id).filter { bundle ->
            bundle.kem_identity_key == identity_key &&
                PrekeyBindingVerifier.is_pgp_signature(bundle.signed_prekey_signature)
        }
        if (candidates.isEmpty()) return false
        val owner_key = try {
            keys_api.get_recipient_public_key(username, email).public_key
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (owner_key.isNullOrBlank()) return false
        val served_owner = PrekeyBindingVerifier.owner_fingerprint(owner_key) ?: return false
        return candidates.any { bundle ->
            val result = PrekeyBindingVerifier.verify_binding(
                signature_block = bundle.signed_prekey_signature,
                recipient_public_key_armored = owner_key,
                kem_identity_key_b64 = bundle.kem_identity_key,
                signed_prekey_b64 = bundle.signed_prekey,
                pq_identity_key_b64 = bundle.pq_kem_public_key,
            ).result
            result == PrekeyBindingResult.VERIFIED &&
                when (RatchetIdentityPinRules.decide_owner_key(pinned_owner, served_owner, result)) {
                    OwnerKeyDecision.ACCEPT, OwnerKeyDecision.PIN_FIRST -> true
                    OwnerKeyDecision.UNAVAILABLE, OwnerKeyDecision.UNTRUSTED -> false
                }
        }
    }

    private suspend fun sender_bundles(
        username: String,
        email: String,
        conversation_id: String,
    ): List<PrekeyBundleResponse> {
        val resolved = try {
            address_directory.resolve_recipient_bundle(username, email, conversation_id)?.bundle
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (resolved != null && resolved.address_scoped != true) return listOf(resolved)
        val account = try {
            ratchet_api.fetch_prekey_bundle(username, email)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        return listOfNotNull(resolved, account?.takeIf { it.address_scoped != true })
    }

    private suspend fun directory_confirms_identity(sender_email: String, identity_key: String): Boolean {
        val email = sender_email.trim().lowercase()
        if (!email.contains('@') || identity_key.isBlank()) return false
        val username = email.substringBefore('@')
        val current = try {
            ratchet_api.fetch_own_ratchet_identity(username, email)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        if (current is OwnRatchetIdentityLookup.Found && current.identity.kem_identity_key == identity_key) return true
        val history = try {
            ratchet_api.fetch_identity_history(username, email)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        }
        return history?.contains(identity_key) == true
    }

    private suspend fun remember_sender_identity(sender_email: String, sender_identity_key: String) {
        runCatching { identity_pins.remember_sender_identity(sender_email, sender_identity_key) }
    }

    private suspend fun flag_sender_rotation(
        conversation_id: String,
        sender_email: String,
        sender_identity_key: String,
    ) {
        runCatching {
            identity_pins.flag_sender_rotation(
                conversation_id = conversation_id,
                sender_email = sender_email,
                sender_identity_key_b64 = sender_identity_key,
                observed_at = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun consume_pq_prekey(recipient: RatchetRecipientData) {
        val key_id = recipient.pq_key_id ?: return
        val ephemeral = recipient.ephemeral_key ?: return
        if (recipient.pq_ciphertext == null || key_id == X3dh.PQ_IDENTITY_KEY_ID) return
        runCatching { identity_pins.record_pq_prekey_consumed(key_id, ephemeral) }
        runCatching { session_key_store.remove_pq_secret(key_id) }
    }

    private suspend fun consume_ec_one_time_prekey(recipient: RatchetRecipientData) {
        if (recipient.x3dh_v != X3dh.VERSION_ONE_TIME_BOUND) return
        try {
            ec_one_time_prekeys.consume(recipient.opk_id)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
        }
    }

    private suspend fun record_identity_pin(
        conversation_id: String,
        sender_email: String,
        sender_identity_key: String,
    ) {
        val outcome = runCatching {
            identity_pins.record(
                conversation_id = conversation_id,
                sender_email = sender_email,
                sender_identity_key_b64 = sender_identity_key,
                observed_at = System.currentTimeMillis(),
            )
        }.getOrNull()
        if (outcome == IdentityPinOutcome.CHANGED && org.astermail.android.BuildConfig.DEBUG) {
            android.util.Log.w("AsterRatchet", "sender identity key changed for conversation")
        }
    }

    private data class RecoveryLaneCandidate(
        val own_keys: RecoveryLane.OwnKeys,
        val pq_identity_public: String,
    )

    private fun recovery_lane_candidates(own_address: String? = null): List<RecoveryLaneCandidate> {
        val candidates = mutableListOf<RecoveryLaneCandidate>()
        address_directory.receiver_key_sets(own_address).forEach { set ->
            candidates.add(
                RecoveryLaneCandidate(
                    own_keys = RecoveryLane.OwnKeys(
                        identity_jwk = set.identity_jwk,
                        identity_public = set.identity_public,
                        pq_identity_secret = set.pq_identity_secret,
                    ),
                    pq_identity_public = set.pq_identity_public.orEmpty(),
                ),
            )
        }

        val identity_jwk = session_key_store.get_ratchet_identity_jwk()
        val identity_public = session_key_store.get_ratchet_identity_public_b64()
        if (!identity_jwk.isNullOrBlank() && !identity_public.isNullOrBlank()) {
            candidates.add(
                RecoveryLaneCandidate(
                    own_keys = RecoveryLane.OwnKeys(
                        identity_jwk = identity_jwk,
                        identity_public = identity_public,
                        pq_identity_secret = session_key_store.get_ratchet_pq_identity_secret(),
                    ),
                    pq_identity_public = session_key_store.get_ratchet_pq_identity_public().orEmpty(),
                ),
            )
        }

        val previous_json = session_key_store.get_ratchet_previous_keys_json()
        if (!previous_json.isNullOrBlank()) {
            try {
                val entries = org.json.JSONArray(previous_json)
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val previous_jwk = entry.optString("ratchet_identity_key", "")
                    val previous_public = entry.optString("ratchet_identity_public", "")
                    if (previous_jwk.isBlank() || previous_public.isBlank()) continue
                    candidates.add(
                        RecoveryLaneCandidate(
                            own_keys = RecoveryLane.OwnKeys(
                                identity_jwk = previous_jwk,
                                identity_public = previous_public,
                                pq_identity_secret = entry
                                    .optString("ratchet_pq_identity_key", "")
                                    .ifBlank {
                                        entry.optString("ratchet_pq_identity_seed", "")
                                            .takeIf { it.isNotBlank() }
                                            ?.let { expand_pq_identity_secret(it) }
                                            .orEmpty()
                                    }
                                    .ifBlank { null },
                            ),
                            pq_identity_public = entry.optString("ratchet_pq_identity_public", ""),
                        ),
                    )
                }
            } catch (_: Throwable) {
            }
        }

        return candidates
    }

    private fun pq_identity_secret_candidates(): List<String> {
        val secrets = mutableListOf<String>()

        session_key_store.get_ratchet_pq_identity_secret()
            ?.takeIf { it.isNotBlank() }
            ?.let { secrets.add(it) }

        val previous_json = session_key_store.get_ratchet_previous_keys_json()
        if (!previous_json.isNullOrBlank()) {
            try {
                val entries = org.json.JSONArray(previous_json)
                for (index in 0 until entries.length()) {
                    val entry = entries.optJSONObject(index) ?: continue
                    val previous_secret = entry.optString("ratchet_pq_identity_key", "")
                        .ifBlank {
                            entry.optString("ratchet_pq_identity_seed", "")
                                .takeIf { it.isNotBlank() }
                                ?.let { expand_pq_identity_secret(it) }
                                .orEmpty()
                        }
                    if (previous_secret.isNotBlank()) secrets.add(previous_secret)
                }
            } catch (_: Throwable) {
            }
        }

        return secrets.distinct()
    }

    private fun try_recovery_lane(
        recipient: RatchetRecipientData,
        conversation_id: String,
        sender_identity_key: String,
        own_address: String? = null,
    ): RecoveredLanePlaintext? {
        val opened = open_recovery_lane_message_key(recipient, conversation_id, sender_identity_key, own_address)
            ?: return null

        val message_key = try {
            RatchetCrypto.b64_decode(opened.message_key)
        } catch (t: Throwable) {
            return null
        }

        return try {
            RecoveredLanePlaintext(
                DoubleRatchet.decrypt_with_message_key(recipient, message_key),
                opened.binding,
                opened.snonce,
                opened.sent_at,
            )
        } catch (t: Throwable) {
            null
        } finally {
            message_key.fill(0)
        }
    }

    private fun open_recovery_lane_message_key(
        recipient: RatchetRecipientData,
        conversation_id: String,
        sender_identity_key: String,
        own_address: String? = null,
    ): OpenedRecoveryLane? {
        val lane = recipient.recovery ?: return null
        if (lane.kem_ct == null && identity_pins.is_post_quantum_established(conversation_id)) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("AsterRatchet", "refusing a classical recovery lane on a post-quantum session")
            }
            return null
        }

        val data = RecoveryLane.Data(
            v = lane.v,
            epk = lane.epk,
            kem_ct = lane.kem_ct,
            ciphertext = lane.ciphertext,
            nonce = lane.nonce,
            rid = lane.rid,
            sct = lane.sct,
            snonce = lane.snonce,
            t = lane.t,
        )

        val sent_at = data.sent_at
        val transcript = sent_at?.let {
            RecoveryLane.Transcript(
                dh_public = recipient.header.dh_public,
                previous_chain_length = recipient.header.previous_chain_length,
                message_number = recipient.header.message_number,
                nonce = recipient.nonce,
                sent_at = it,
            )
        }
        val bindings = if (transcript == null) {
            listOf(RecoveryLaneBinding.LEGACY, RecoveryLaneBinding.NONE)
        } else {
            listOf(RecoveryLaneBinding.TRANSCRIPT, RecoveryLaneBinding.LEGACY, RecoveryLaneBinding.NONE)
        }
        val ordered = recovery_lane_candidates(own_address)
            .sortedByDescending { it.own_keys.identity_public == lane.rid }

        for (binding in bindings) {
            for (candidate in ordered) {
                val opened = if (binding == RecoveryLaneBinding.NONE) {
                    RecoveryLane.open_unauthenticated(
                        data,
                        conversation_id,
                        sender_identity_key,
                        candidate.own_keys,
                        candidate.pq_identity_public,
                    )
                } else {
                    RecoveryLane.open(
                        data,
                        conversation_id,
                        sender_identity_key,
                        candidate.own_keys,
                        candidate.pq_identity_public,
                        if (binding == RecoveryLaneBinding.TRANSCRIPT) transcript else null,
                    )
                }
                if (opened != null) {
                    return OpenedRecoveryLane(
                        opened,
                        binding,
                        if (binding == RecoveryLaneBinding.NONE) "" else lane.snonce.orEmpty(),
                        if (binding == RecoveryLaneBinding.TRANSCRIPT) sent_at ?: 0L else 0L,
                    )
                }
            }
        }

        return null
    }

    private fun receiver_key_sets(own_address: String? = null): List<ReceiverKeySet> {
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
        address_directory.receiver_key_sets(own_address).forEach { set ->
            sets.add(
                ReceiverKeySet(
                    identity_jwk = set.identity_jwk,
                    identity_public_b64 = set.identity_public,
                    signed_prekey_jwk = set.signed_prekey_jwk,
                    signed_prekey_public_b64 = set.signed_prekey_public,
                    pq_identity_secret = set.pq_identity_secret,
                ),
            )
        }
        return sets
    }

    private suspend fun decrypt_with_key_sets(
        conversation_id: String,
        sender_identity_key_b64: String,
        recipient: RatchetRecipientData,
        tried: MutableSet<String>,
        own_address: String? = null,
    ): Pair<RatchetState, String>? {
        val key_sets = order_receiver_key_sets(receiver_key_sets(own_address), recipient)
        if (org.astermail.android.BuildConfig.DEBUG) {
            android.util.Log.i("AsterRatchet", "bootstrap over ${key_sets.size} receiver key sets")
        }
        for (keys in key_sets) {
            val tag = receiver_key_set_tag(keys.identity_public_b64, keys.identity_jwk, keys.signed_prekey_public_b64)
            if (!tried.add(tag)) continue
            val candidates = try {
                bootstrap(conversation_id, sender_identity_key_b64, recipient, keys)
            } catch (c: kotlinx.coroutines.CancellationException) {
                throw c
            } catch (t: Throwable) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("AsterRatchet", "bootstrap threw: ${t.javaClass.simpleName}")
                }
                emptyList()
            }
            for (candidate in candidates) {
                try {
                    return candidate to DoubleRatchet.decrypt(candidate, recipient)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    if (org.astermail.android.BuildConfig.DEBUG) {
                        android.util.Log.w("AsterRatchet", "bootstrapped state failed to decrypt: ${t.javaClass.simpleName}")
                    }
                }
            }
        }
        return null
    }

    private fun order_receiver_key_sets(
        key_sets: List<ReceiverKeySet>,
        recipient: RatchetRecipientData,
    ): List<ReceiverKeySet> {
        val hint = recipient.spk_id?.takeIf { recipient.x3dh_v == X3dh.VERSION_ONE_TIME_BOUND && it.isNotBlank() }
            ?: return key_sets
        val (matching, others) = key_sets.partition { keys ->
            runCatching {
                X3dh.signed_prekey_id(RatchetCrypto.b64_decode(keys.signed_prekey_public_b64)) == hint
            }.getOrDefault(false)
        }
        return matching + others
    }

    private suspend fun refresh_vault_keys(): Boolean {
        val now = System.currentTimeMillis()
        if (now - last_vault_refresh_at < VAULT_REFRESH_COOLDOWN_MS) return false
        last_vault_refresh_at = now
        return try {
            auth_repository.get().try_refresh_vault_keys()
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun bootstrap(
        conversation_id: String,
        sender_identity_key_b64: String,
        recipient: RatchetRecipientData,
        keys: ReceiverKeySet,
    ): List<RatchetState> {
        val ephemeral_b64 = recipient.ephemeral_key ?: return emptyList()
        val offered_x3dh_version = recipient.x3dh_v ?: 1
        if (offered_x3dh_version < X3dh.receive_floor(identity_pins.highest_x3dh_version(conversation_id))) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("AsterRatchet", "refusing a downgraded x3dh handshake")
            }
            return emptyList()
        }
        val identity_jwk = keys.identity_jwk
        val spk_jwk = keys.signed_prekey_jwk
        val spk_pub_b64 = keys.signed_prekey_public_b64

        val sender_identity_raw = RatchetCrypto.b64_decode(sender_identity_key_b64)
        val sender_ephemeral_raw = RatchetCrypto.b64_decode(ephemeral_b64)

        val pq_ciphertext = recipient.pq_ciphertext
        val pq_key_id = recipient.pq_key_id
        val from_identity = pq_key_id == X3dh.PQ_IDENTITY_KEY_ID

        if (pq_ciphertext != null && pq_key_id != null && !from_identity) {
            val consumer = runCatching { identity_pins.pq_prekey_consumer(pq_key_id) }.getOrNull()
            if (!RatchetIdentityPinRules.pq_prekey_accepts(consumer, ephemeral_b64)) {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("AsterRatchet", "refusing a reused one-time pq prekey")
                }
                return emptyList()
            }
        }

        val pq_secrets: List<ByteArray> = when {
            pq_ciphertext == null || pq_key_id == null -> emptyList()
            from_identity -> (keys.pq_identity_secret?.let { listOf(it) } ?: pq_identity_secret_candidates()).mapNotNull {
                try {
                    RatchetCrypto.b64_decode(it)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    null
                }
            }
            else -> listOfNotNull(fetch_pq_secret(pq_key_id))
        }

        if (pq_ciphertext != null && pq_key_id != null && pq_secrets.isEmpty()) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("AsterRatchet", "bootstrap aborted: pq secret $pq_key_id unavailable")
            }
            return emptyList()
        }

        val one_time_bound = recipient.x3dh_v == X3dh.VERSION_ONE_TIME_BOUND
        val one_time_prekey_id = recipient.opk_id.takeIf { one_time_bound }
        val one_time_prekey_jwk = if (one_time_bound) {
            if (!X3dh.is_valid_one_time_prekey_id(one_time_prekey_id)) {
                pq_secrets.forEach { it.fill(0) }
                return emptyList()
            }
            ec_one_time_prekeys.load_private_jwk(one_time_prekey_id) ?: run {
                if (org.astermail.android.BuildConfig.DEBUG) {
                    android.util.Log.w("AsterRatchet", "bootstrap aborted: one-time prekey unavailable")
                }
                pq_secrets.forEach { it.fill(0) }
                return emptyList()
            }
        } else {
            null
        }

        val pq_ciphertext_raw = pq_ciphertext?.let {
            try {
                RatchetCrypto.b64_decode(it)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                null
            }
        }

        fun derive(pq_shared: ByteArray?): RatchetState {
            val shared_secret = X3dh.perform_receiver(
                receiver_identity_jwk = identity_jwk,
                receiver_signed_prekey_jwk = spk_jwk,
                sender_identity_raw = sender_identity_raw,
                sender_ephemeral_raw = sender_ephemeral_raw,
                pq_shared_secret = pq_shared,
                pq_from_identity = from_identity,
                x3dh_version = recipient.x3dh_v,
                pq_ciphertext = pq_ciphertext_raw,
                one_time_prekey_jwk = one_time_prekey_jwk,
                one_time_prekey_id = one_time_prekey_id,
            )
            return X3dh.init_receiver_state(
                conversation_id = conversation_id,
                shared_secret = shared_secret,
                own_signed_prekey_jwk = spk_jwk,
                own_signed_prekey_public_b64 = spk_pub_b64,
            ).also {
                it.remote_identity_key = sender_identity_key_b64
                shared_secret.fill(0)
            }
        }

        if (pq_ciphertext == null || pq_key_id == null) return listOf(derive(null))

        val pq_ct = RatchetCrypto.b64_decode(pq_ciphertext)

        return pq_secrets.mapNotNull { pq_secret ->
            val pq_shared = try {
                RatchetCrypto.ml_kem_768_decapsulate(pq_ct, pq_secret)
            } catch (_: Throwable) {
                null
            } finally {
                pq_secret.fill(0)
            }
            pq_shared?.let { derive(it).also { _ -> pq_shared.fill(0) } }
        }
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
        val resp = try {
            ratchet_api.fetch_pq_secret(key_id)
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (_: Throwable) {
            return null
        }
        if (resp == null) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("AsterRatchet", "pq secret $key_id: api returned null")
            }
            mark_pq_secret_missed(key_id)
            return null
        }
        val seal = PrekeySeal.from_store(session_key_store)
        val pt = try {
            open_and_reseal_pq_secret(
                ratchet_api,
                seal,
                key_id,
                resp,
                org.astermail.android.api.keys.BoundWriteSwitches.prekey(),
            ) { state_store.state_encryption_key_candidates() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            null
        } finally {
            seal.wipe()
        }
        if (pt == null) {
            if (org.astermail.android.BuildConfig.DEBUG) {
                android.util.Log.w("AsterRatchet", "pq secret $key_id: not readable")
            }
            mark_pq_secret_missed(key_id)
            return null
        }
        session_key_store.put_pq_secret(key_id, pt)
        pq_secret_missed_at.remove(key_id)
        return pt
    }

}
