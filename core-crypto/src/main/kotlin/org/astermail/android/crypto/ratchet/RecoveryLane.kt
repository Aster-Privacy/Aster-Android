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
    private const val NONCE_BYTES = 12
    private const val LANE_KEY_BYTES = 32

    data class Data(
        val v: Int,
        val epk: String,
        val kem_ct: String?,
        val ciphertext: String,
        val nonce: String,
        val rid: String,
    )

    data class RecipientKeys(
        val identity_public: String,
        val pq_identity_public: String? = null,
    )

    data class OwnKeys(
        val identity_jwk: String,
        val identity_public: String,
        val pq_identity_secret: String? = null,
    )

    fun can_seal(recipient: RecipientKeys?): Boolean =
        recipient != null && recipient.identity_public.isNotBlank()

    fun is_post_quantum(recipient: RecipientKeys): Boolean =
        !recipient.pq_identity_public.isNullOrBlank()

    private fun build_binding_info(
        conversation_id: String,
        sender_identity_public: String,
        recipient_identity_public: String,
        recipient_pq_identity_public: String,
    ): ByteArray {
        val parts = listOf(
            LANE_LABEL,
            conversation_id,
            sender_identity_public,
            recipient_identity_public,
            recipient_pq_identity_public,
        )
        val framed = ByteArrayOutputStream()
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.UTF_8)
            framed.write(ByteBuffer.allocate(4).putInt(bytes.size).array())
            framed.write(bytes)
        }
        return RatchetCrypto.sha256(framed.toByteArray())
    }

    private fun lane_salt(): ByteArray =
        RatchetCrypto.sha256(LANE_LABEL.toByteArray(Charsets.UTF_8))

    private fun derive_lane_key(secrets: List<ByteArray>, info: ByteArray): ByteArray {
        val concatenated = ByteArray(secrets.sumOf { it.size })
        var offset = 0
        for (secret in secrets) {
            System.arraycopy(secret, 0, concatenated, offset, secret.size)
            offset += secret.size
        }
        return try {
            RatchetCrypto.hkdf_sha256(concatenated, lane_salt(), info, LANE_KEY_BYTES)
        } finally {
            concatenated.fill(0)
        }
    }

    fun seal(
        plaintext: String,
        conversation_id: String,
        sender_identity_public: String,
        recipient: RecipientKeys,
    ): Data {
        val info = build_binding_info(
            conversation_id,
            sender_identity_public,
            recipient.identity_public,
            recipient.pq_identity_public.orEmpty(),
        )

        val ephemeral = RatchetCrypto.generate_p256_keypair()
        val recipient_identity =
            RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(recipient.identity_public))
        val dh_secret = RatchetCrypto.ecdh(ephemeral.private_key, recipient_identity)

        val encapsulation = if (is_post_quantum(recipient)) {
            RatchetCrypto.ml_kem_768_encapsulate(
                RatchetCrypto.b64_decode(recipient.pq_identity_public!!),
            )
        } else {
            null
        }

        var lane_key: ByteArray? = null
        return try {
            lane_key = derive_lane_key(
                if (encapsulation != null) listOf(dh_secret, encapsulation.shared_secret) else listOf(dh_secret),
                info,
            )
            val nonce = RatchetCrypto.random_bytes(NONCE_BYTES)
            val ciphertext = RatchetCrypto.aes_gcm_encrypt(
                plaintext.toByteArray(Charsets.UTF_8),
                lane_key,
                nonce,
                info,
            )
            Data(
                v = VERSION,
                epk = RatchetCrypto.b64_encode(ephemeral.public_raw),
                kem_ct = encapsulation?.let { RatchetCrypto.b64_encode(it.ciphertext) },
                ciphertext = RatchetCrypto.b64_encode(ciphertext),
                nonce = RatchetCrypto.b64_encode(nonce),
                rid = recipient.identity_public,
            )
        } finally {
            dh_secret.fill(0)
            encapsulation?.shared_secret?.fill(0)
            lane_key?.fill(0)
        }
    }

    fun open(
        data: Data,
        conversation_id: String,
        sender_identity_public: String,
        own_keys: OwnKeys,
        own_pq_identity_public: String,
    ): String? {
        if (data.v != VERSION) return null

        val info = build_binding_info(
            conversation_id,
            sender_identity_public,
            own_keys.identity_public,
            if (data.kem_ct != null) own_pq_identity_public else "",
        )

        var dh_secret: ByteArray? = null
        var pq_secret: ByteArray? = null
        var pq_secret_key: ByteArray? = null
        var lane_key: ByteArray? = null

        return try {
            val identity_private = RatchetCrypto.parse_p256_private_jwk(own_keys.identity_jwk)
            val ephemeral_public =
                RatchetCrypto.parse_p256_public_raw(RatchetCrypto.b64_decode(data.epk))
            dh_secret = RatchetCrypto.ecdh(identity_private, ephemeral_public)

            if (data.kem_ct != null) {
                if (own_keys.pq_identity_secret.isNullOrBlank()) return null
                pq_secret_key = RatchetCrypto.b64_decode(own_keys.pq_identity_secret)
                pq_secret = RatchetCrypto.ml_kem_768_decapsulate(
                    RatchetCrypto.b64_decode(data.kem_ct),
                    pq_secret_key,
                )
            }

            lane_key = derive_lane_key(
                if (pq_secret != null) listOf(dh_secret, pq_secret) else listOf(dh_secret),
                info,
            )

            String(
                RatchetCrypto.aes_gcm_decrypt(
                    RatchetCrypto.b64_decode(data.ciphertext),
                    lane_key,
                    RatchetCrypto.b64_decode(data.nonce),
                    info,
                ),
                Charsets.UTF_8,
            )
        } catch (_: Throwable) {
            null
        } finally {
            dh_secret?.fill(0)
            pq_secret?.fill(0)
            pq_secret_key?.fill(0)
            lane_key?.fill(0)
        }
    }
}
