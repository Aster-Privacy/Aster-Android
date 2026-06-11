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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.astermail.android.api.ratchet.RatchetApi
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.storage.SessionKeyStore

@Singleton
class RatchetEncryptor @Inject constructor(
    private val state_store: RatchetStateStore,
    private val session_key_store: SessionKeyStore,
    private val ratchet_api: RatchetApi,
    private val syncer: RatchetStateSyncer,
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
        val sender_identity_public = session_key_store.get_ratchet_identity_public_b64() ?: return null
        val sender_identity_jwk = session_key_store.get_ratchet_identity_jwk() ?: return null

        val per_recipient = mutableMapOf<String, RatchetRecipientData>()
        for (recipient_email in recipients) {
            val data = encrypt_for_recipient(sender_email, sender_identity_jwk, recipient_email, body) ?: return null
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
        sender_identity_jwk: String,
        recipient_email: String,
        body: String,
    ): RatchetRecipientData? {
        val conversation_id = X3dh.derive_conversation_id(sender_email, recipient_email)

        var state = state_store.load(conversation_id)
        if (state != null && state.bootstrap == null) {
            state = null
        }

        var ephemeral_b64: String? = null
        var pq_ciphertext_b64: String? = null
        var pq_key_id: Int? = null

        if (state == null) {
            val username = recipient_email.substringBefore('@')
            val bundle = try {
                ratchet_api.fetch_prekey_bundle(username, recipient_email)
            } catch (t: Throwable) {
                android.util.Log.w("AsterRatchet", "prekey bundle fetch threw", t)
                null
            } ?: run {
                android.util.Log.w("AsterRatchet", "no prekey bundle for $recipient_email")
                return null
            }

            val recipient_identity_raw = RatchetCrypto.b64_decode(bundle.kem_identity_key)
            val recipient_spk_raw = RatchetCrypto.b64_decode(bundle.signed_prekey)
            val pq_prekey_pair = bundle.pq_prekey?.let { it.key_id to RatchetCrypto.b64_decode(it.public_key) }

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
                    remote_signed_prekey_raw_b64 = bundle.signed_prekey,
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

        val encrypted = DoubleRatchet.encrypt(state, body)
        state_store.save(state)
        syncer.sync(conversation_id, state)

        return RatchetRecipientData(
            ephemeral_key = ephemeral_b64,
            header = encrypted.header,
            ciphertext = RatchetCrypto.b64_encode(encrypted.ciphertext),
            nonce = RatchetCrypto.b64_encode(encrypted.nonce),
            pq_ciphertext = pq_ciphertext_b64,
            pq_key_id = pq_key_id,
        )
    }
}
