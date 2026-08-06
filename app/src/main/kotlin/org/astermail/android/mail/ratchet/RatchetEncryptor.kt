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

import org.astermail.android.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.ratchet.PrekeyBundleResponse
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.ratchet.RecoveryLane
import org.astermail.android.storage.SessionKeyStore

class RatchetEncryptionException(
    val recipient: String?,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

@Singleton
class RatchetEncryptor @Inject constructor(
    private val state_store: RatchetStateStore,
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val syncer: RatchetStateSyncer,
    private val conversation_locks: ConversationLocks,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    suspend fun encrypt_envelope(
        sender_email: String,
        recipients: List<String>,
        body: String,
    ): String? {
        if (recipients.isEmpty()) return null
        val sender_identity_public = session_key_store.get_ratchet_identity_public_b64()
            ?: throw RatchetEncryptionException(null, "missing ratchet identity public key")
        val sender_identity_jwk = session_key_store.get_ratchet_identity_jwk()
            ?: throw RatchetEncryptionException(null, "missing ratchet identity key")

        val per_recipient = mutableMapOf<String, RatchetRecipientData>()
        for (recipient_email in recipients) {
            val data = try {
                encrypt_for_recipient(sender_email, sender_identity_public, sender_identity_jwk, recipient_email, body)
            } catch (e: RatchetEncryptionException) {
                throw e
            } catch (e: Throwable) {
                throw RatchetEncryptionException(recipient_email, "ratchet encryption failed for recipient", e)
            } ?: throw RatchetEncryptionException(recipient_email, "no prekey bundle available for recipient")
            per_recipient[recipient_email.lowercase()] = data
        }

        val envelope = RatchetEnvelope(
            type = "double_ratchet_v2",
            sender_identity_key = sender_identity_public,
            recipients = per_recipient,
        )
        return json.encodeToString(envelope)
    }

    private suspend fun encrypt_for_recipient(
        sender_email: String,
        sender_identity_public: String,
        sender_identity_jwk: String,
        recipient_email: String,
        body: String,
    ): RatchetRecipientData? {
        val conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)
        val username = recipient_email.substringBefore('@')

        return conversation_locks.with_lock(conversation_id) {
            encrypt_for_recipient_locked(conversation_id, username, sender_identity_public, sender_identity_jwk, recipient_email, body)
        }
    }

    private suspend fun encrypt_for_recipient_locked(
        conversation_id: String,
        username: String,
        sender_identity_public: String,
        sender_identity_jwk: String,
        recipient_email: String,
        body: String,
    ): RatchetRecipientData? {
        var state = state_store.load(conversation_id)
        if (state != null && state.bootstrap == null) {
            state = null
        }

        var bundle: PrekeyBundleResponse? = null

        // Reuse an existing session only if neither party rotated identities
        // since it was bootstrapped. Sessions created before identity tracking
        // (null sender/recipient identity) are refreshed once. If the current
        // bundle cannot be fetched, keep the session rather than failing the send.
        if (state != null) {
            val boot = state.bootstrap!!
            val sender_changed = boot.sender_identity_key != sender_identity_public
            var recipient_changed = false
            if (!sender_changed) {
                bundle = try {
                    ratchet_api.fetch_prekey_bundle(username, recipient_email)
                } catch (t: Throwable) {
                    null
                }
                if (bundle != null && boot.recipient_identity_key != bundle.kem_identity_key) {
                    recipient_changed = true
                }
            }
            if (sender_changed || recipient_changed) {
                state = null
            }
        }

        var ephemeral_b64: String? = null
        var pq_ciphertext_b64: String? = null
        var pq_key_id: Int? = null

        if (state == null) {
            val resolved_bundle = (bundle ?: try {
                ratchet_api.fetch_prekey_bundle(username, recipient_email)
            } catch (t: Throwable) {
                if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "prekey bundle fetch threw", t)
                null
            }) ?: run {
                if (BuildConfig.DEBUG) android.util.Log.w("AsterRatchet", "no prekey bundle for recipient")
                return null
            }

            if (BuildConfig.DEBUG && resolved_bundle.signed_prekey_signature.isNotBlank()) {
                val provided = runCatching { RatchetCrypto.b64_decode(resolved_bundle.signed_prekey_signature) }.getOrNull()
                if (provided != null && provided.size == 32) {
                    val sig_input = (resolved_bundle.kem_identity_key + resolved_bundle.signed_prekey).toByteArray(Charsets.UTF_8)
                    if (!RatchetCrypto.sha256(sig_input).contentEquals(provided)) {
                        android.util.Log.w("AsterRatchet", "prekey bundle hash inconsistent")
                    }
                }
            }

            bundle = resolved_bundle

            val recipient_identity_raw = RatchetCrypto.b64_decode(resolved_bundle.kem_identity_key)
            val recipient_spk_raw = RatchetCrypto.b64_decode(resolved_bundle.signed_prekey)
            val pq_prekey_pair = resolved_bundle.pq_prekey?.let { it.key_id to RatchetCrypto.b64_decode(it.public_key) }

            val x3dh_result = X3dh.perform_sender(
                sender_identity_jwk = sender_identity_jwk,
                recipient_identity_raw = recipient_identity_raw,
                recipient_signed_prekey_raw = recipient_spk_raw,
                recipient_pq_prekey = pq_prekey_pair,
            )

            try {
                state = DoubleRatchet.init_sender(
                    conversation_id = conversation_id,
                    shared_secret = x3dh_result.shared_secret,
                    remote_signed_prekey_raw_b64 = resolved_bundle.signed_prekey,
                )
                ephemeral_b64 = RatchetCrypto.b64_encode(x3dh_result.ephemeral_public_raw)
                if (x3dh_result.pq_ciphertext != null && x3dh_result.pq_key_id != null) {
                    pq_ciphertext_b64 = RatchetCrypto.b64_encode(x3dh_result.pq_ciphertext!!)
                    pq_key_id = x3dh_result.pq_key_id
                }
                state.bootstrap = BootstrapData(
                    ephemeral_key = ephemeral_b64!!,
                    pq_ciphertext = pq_ciphertext_b64,
                    pq_key_id = pq_key_id,
                    sender_identity_key = sender_identity_public,
                    recipient_identity_key = resolved_bundle.kem_identity_key,
                    recipient_pq_identity_key = resolved_bundle.pq_kem_public_key,
                )
            } finally {
                x3dh_result.shared_secret.fill(0)
            }
        } else {
            val boot = state.bootstrap!!
            ephemeral_b64 = boot.ephemeral_key
            pq_ciphertext_b64 = boot.pq_ciphertext
            pq_key_id = boot.pq_key_id
        }

        val recovery_keys = resolve_recovery_lane_keys(bundle, state.bootstrap)
            ?: return null

        val encrypted = DoubleRatchet.encrypt(state, body)

        val recovery = try {
            RecoveryLane.seal(
                RatchetCrypto.b64_encode(encrypted.message_key),
                conversation_id,
                sender_identity_public,
                recovery_keys,
            )
        } catch (t: Throwable) {
            throw RatchetEncryptionException(recipient_email, "recovery lane unavailable", t)
        } finally {
            encrypted.message_key.fill(0)
        }

        state_store.save(state)
        syncer.sync(conversation_id, state)

        return RatchetRecipientData(
            ephemeral_key = ephemeral_b64,
            header = encrypted.header,
            ciphertext = RatchetCrypto.b64_encode(encrypted.ciphertext),
            nonce = RatchetCrypto.b64_encode(encrypted.nonce),
            pq_ciphertext = pq_ciphertext_b64,
            pq_key_id = pq_key_id,
            recovery = RecoveryLaneData(
                v = recovery.v,
                epk = recovery.epk,
                kem_ct = recovery.kem_ct,
                ciphertext = recovery.ciphertext,
                nonce = recovery.nonce,
                rid = recovery.rid,
            ),
        )
    }

    private fun resolve_recovery_lane_keys(
        bundle: PrekeyBundleResponse?,
        bootstrap: BootstrapData?,
    ): RecoveryLane.RecipientKeys? {
        val identity_public = bundle?.kem_identity_key
            ?: bootstrap?.recipient_identity_key
            ?: return null
        if (identity_public.isBlank()) return null

        val pq_identity_public = if (bundle != null) {
            bundle.pq_kem_public_key
        } else {
            bootstrap?.recipient_pq_identity_key
        }

        return RecoveryLane.RecipientKeys(identity_public, pq_identity_public)
    }
}
