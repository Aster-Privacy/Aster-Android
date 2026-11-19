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

package org.astermail.android.crypto.ratchet

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object RecoveryLane {

    const val VERSION = 1

    private const val LANE_LABEL = "aster.ratchet.recovery.lane.v1"
    private const val SENDER_LANE_LABEL = "aster.ratchet.recovery.lane.sender.v1"
    private const val NONCE_BYTES = 12
    private const val LANE_KEY_BYTES = 32
    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L

    data class Data(
        val v: Int,
        val epk: String,
        val kem_ct: String?,
        val ciphertext: String,
        val nonce: String,
        val rid: String,
        val sct: String? = null,
        val snonce: String? = null,
        val t: Long? = null,
    ) {
        val sent_at: Long?
            get() = t?.takeIf { it in 1..MAX_SAFE_INTEGER }

        val has_sender_proof: Boolean
            get() = !sct.isNullOrEmpty() && !snonce.isNullOrEmpty()
    }

    data class RecipientKeys(
        val identity_public: String,
        val pq_identity_public: String? = null,
    )

    data class OwnKeys(
        val identity_jwk: String,
        val identity_public: String,
        val pq_identity_secret: String? = null,
    )

    private class LaneSecrets(val dh_secret: ByteArray, val pq_secret: ByteArray?) {
        fun with_static(static_secret: ByteArray?): List<ByteArray> =
            listOfNotNull(dh_secret, static_secret, pq_secret)

        fun wipe() {
            dh_secret.fill(0)
            pq_secret?.fill(0)
        }
    }

    fun can_seal(recipient: RecipientKeys?): Boolean =
        recipient != null && recipient.identity_public.isNotBlank()

    fun is_post_quantum(recipient: RecipientKeys): Boolean =
        !recipient.pq_identity_public.isNullOrBlank()

    private fun frame_and_digest(parts: List<String>): ByteArray {
        val framed = ByteArrayOutputStream()
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            framed.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            framed.write(bytes)
        }
        return RatchetCrypto.sha256(framed.toByteArray())
    }

    private fun build_binding_info(
        conversation_id: String,
        sender_identity_public: String,
        recipient_identity_public: String,
        recipient_pq_identity_public: String,
    ): ByteArray = frame_and_digest(
        listOf(
            LANE_LABEL,
            conversation_id,
            sender_identity_public,
            recipient_identity_public,
            recipient_pq_identity_public,
        ),
    )

    data class Transcript(
        val dh_public: String,
        val previous_chain_length: Int,
        val message_number: Int,
        val nonce: String,
        val sent_at: Long,
    )

    class SenderKeyMissingException : IllegalStateException("recovery lane needs the sender identity key")

    private fun build_sender_binding_info(
        conversation_id: String,
        sender_identity_public: String,
        recipient_identity_public: String,
        recipient_pq_identity_public: String,
        epk: String,
        kem_ct: String,
        transcript: Transcript?,
    ): ByteArray = frame_and_digest(
        listOf(
            SENDER_LANE_LABEL,
            conversation_id,
            sender_identity_public,
            recipient_identity_public,
            recipient_pq_identity_public,
            epk,
            kem_ct,
        ) + if (transcript == null) {
            emptyList()
        } else {
            listOf(
                transcript.dh_public,
                transcript.previous_chain_length.toString(),
                transcript.message_number.toString(),
                transcript.nonce,
                transcript.sent_at.toString(),
            )
        },
    )

    private fun lane_salt(label: String): ByteArray =
        RatchetCrypto.sha256(label.toByteArray(Charsets.UTF_8))

    private fun derive_lane_key(secrets: List<ByteArray>, info: ByteArray, label: String): ByteArray {
        val concatenated = ByteArray(secrets.sumOf { it.size })
        var offset = 0
        for (secret in secrets) {
            System.arraycopy(secret, 0, concatenated, offset, secret.size)
            offset += secret.size
        }
        return try {
            RatchetCrypto.hkdf_sha256(concatenated, lane_salt(label), info, LANE_KEY_BYTES)
        } finally {
            concatenated.fill(0)
        }
    }

    private fun static_agreement(identity_jwk: String, peer_identity_public: String): ByteArray =
        RatchetCrypto.ecdh(
            RatchetCrypto.parse_p256_private_jwk(identity_jwk),
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(peer_identity_public)),
        )

    private fun sender_static_agreement(identity_jwk: String, peer_identity_public: String): ByteArray {
        val own_private = try {
            RatchetCrypto.parse_p256_private_jwk(identity_jwk)
        } catch (_: Throwable) {
            throw SenderKeyMissingException()
        }
        return RatchetCrypto.ecdh(
            own_private,
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(peer_identity_public)),
        )
    }

    private fun seal_with_key(
        plaintext: String,
        secrets: List<ByteArray>,
        info: ByteArray,
        label: String,
    ): Pair<String, String> {
        val key = derive_lane_key(secrets, info, label)
        return try {
            val nonce = RatchetCrypto.random_bytes(NONCE_BYTES)
            val ciphertext = RatchetCrypto.aes_gcm_encrypt(
                plaintext.toByteArray(Charsets.UTF_8),
                key,
                nonce,
                info,
            )
            RatchetCrypto.b64_encode(ciphertext) to RatchetCrypto.b64_encode(nonce)
        } finally {
            key.fill(0)
        }
    }

    private fun open_with_key(
        ciphertext: String,
        nonce: String,
        secrets: List<ByteArray>,
        info: ByteArray,
        label: String,
    ): String {
        val key = derive_lane_key(secrets, info, label)
        return try {
            String(
                RatchetCrypto.aes_gcm_decrypt(
                    RatchetCrypto.b64_decode(ciphertext),
                    key,
                    RatchetCrypto.b64_decode(nonce),
                    info,
                ),
                Charsets.UTF_8,
            )
        } finally {
            key.fill(0)
        }
    }

    fun seal(
        plaintext: String,
        conversation_id: String,
        sender_identity_public: String,
        recipient: RecipientKeys,
        sender_identity_jwk: String?,
        transcript: Transcript,
    ): Data {
        if (sender_identity_jwk.isNullOrBlank()) throw SenderKeyMissingException()
        require(transcript.sent_at in 1..MAX_SAFE_INTEGER) { "recovery lane needs a send time" }
        return seal_parts(plaintext, conversation_id, sender_identity_public, recipient, sender_identity_jwk to transcript)
    }

    fun seal_unbound(
        plaintext: String,
        conversation_id: String,
        sender_identity_public: String,
        recipient: RecipientKeys,
    ): Data = seal_parts(plaintext, conversation_id, sender_identity_public, recipient, null)

    private fun seal_parts(
        plaintext: String,
        conversation_id: String,
        sender_identity_public: String,
        recipient: RecipientKeys,
        sender_part: Pair<String, Transcript>?,
    ): Data {
        val recipient_pq_public = recipient.pq_identity_public.orEmpty()
        val info = build_binding_info(
            conversation_id,
            sender_identity_public,
            recipient.identity_public,
            recipient_pq_public,
        )

        val ephemeral = RatchetCrypto.generate_p256_keypair()
        val recipient_identity =
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(recipient.identity_public))
        val encapsulation = if (is_post_quantum(recipient)) {
            RatchetCrypto.ml_kem_768_encapsulate(
                RatchetCrypto.b64_decode(recipient.pq_identity_public!!),
            )
        } else {
            null
        }
        val secrets = LaneSecrets(
            RatchetCrypto.ecdh(ephemeral.private_key, recipient_identity),
            encapsulation?.shared_secret,
        )
        var static_secret: ByteArray? = null

        return try {
            val epk = RatchetCrypto.b64_encode(ephemeral.public_raw)
            val kem_ct = encapsulation?.let { RatchetCrypto.b64_encode(it.ciphertext) }
            val (ciphertext, nonce) = seal_with_key(plaintext, secrets.with_static(null), info, LANE_LABEL)
            val lane = Data(
                v = VERSION,
                epk = epk,
                kem_ct = kem_ct,
                ciphertext = ciphertext,
                nonce = nonce,
                rid = recipient.identity_public,
            )
            if (sender_part == null) return lane

            static_secret = sender_static_agreement(sender_part.first, recipient.identity_public)
            val sender_info = build_sender_binding_info(
                conversation_id,
                sender_identity_public,
                recipient.identity_public,
                recipient_pq_public,
                epk,
                kem_ct.orEmpty(),
                sender_part.second,
            )
            val (sct, snonce) = seal_with_key(
                plaintext,
                secrets.with_static(static_secret),
                sender_info,
                SENDER_LANE_LABEL,
            )
            lane.copy(sct = sct, snonce = snonce, t = sender_part.second.sent_at)
        } finally {
            secrets.wipe()
            static_secret?.fill(0)
        }
    }

    private fun recover_secrets(data: Data, own_keys: OwnKeys): LaneSecrets? {
        if (data.kem_ct != null && own_keys.pq_identity_secret.isNullOrBlank()) return null
        val dh_secret = RatchetCrypto.ecdh(
            RatchetCrypto.parse_p256_private_jwk(own_keys.identity_jwk),
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(data.epk)),
        )
        if (data.kem_ct == null) return LaneSecrets(dh_secret, null)

        val pq_secret_key = RatchetCrypto.b64_decode(own_keys.pq_identity_secret!!)
        return try {
            LaneSecrets(
                dh_secret,
                RatchetCrypto.ml_kem_768_decapsulate(RatchetCrypto.b64_decode(data.kem_ct), pq_secret_key),
            )
        } catch (t: Throwable) {
            dh_secret.fill(0)
            throw t
        } finally {
            pq_secret_key.fill(0)
        }
    }

    private fun own_pq_binding(data: Data, own_pq_identity_public: String): String =
        if (data.kem_ct != null) own_pq_identity_public else ""

    fun open(
        data: Data,
        conversation_id: String,
        sender_identity_public: String,
        own_keys: OwnKeys,
        own_pq_identity_public: String,
        transcript: Transcript?,
    ): String? {
        if (data.v != VERSION || !data.has_sender_proof) return null

        var secrets: LaneSecrets? = null
        var static_secret: ByteArray? = null

        return try {
            secrets = recover_secrets(data, own_keys) ?: return null
            static_secret = static_agreement(own_keys.identity_jwk, sender_identity_public)
            val sender_info = build_sender_binding_info(
                conversation_id,
                sender_identity_public,
                own_keys.identity_public,
                own_pq_binding(data, own_pq_identity_public),
                data.epk,
                data.kem_ct.orEmpty(),
                transcript,
            )
            open_with_key(
                data.sct!!,
                data.snonce!!,
                secrets.with_static(static_secret),
                sender_info,
                SENDER_LANE_LABEL,
            )
        } catch (_: Throwable) {
            null
        } finally {
            secrets?.wipe()
            static_secret?.fill(0)
        }
    }

    fun open_unauthenticated(
        data: Data,
        conversation_id: String,
        sender_identity_public: String,
        own_keys: OwnKeys,
        own_pq_identity_public: String,
    ): String? {
        if (data.v != VERSION) return null

        var secrets: LaneSecrets? = null

        return try {
            secrets = recover_secrets(data, own_keys) ?: return null
            val info = build_binding_info(
                conversation_id,
                sender_identity_public,
                own_keys.identity_public,
                own_pq_binding(data, own_pq_identity_public),
            )
            open_with_key(data.ciphertext, data.nonce, secrets.with_static(null), info, LANE_LABEL)
        } catch (_: Throwable) {
            null
        } finally {
            secrets?.wipe()
        }
    }
}
