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

import org.astermail.android.crypto.ratchet.RatchetCrypto

object X3dh {

    private val info_classical = "Aster Mail_X3DH_v1".toByteArray(Charsets.UTF_8)
    private val info_pq = "Aster Mail_PQXDH_v1".toByteArray(Charsets.UTF_8)
    private val salt = ByteArray(32)

    data class SenderResult(
        val shared_secret: ByteArray,
        val ephemeral_public_raw: ByteArray,
        val pq_ciphertext: ByteArray? = null,
        val pq_key_id: Int? = null,
    )

    fun perform_sender(
        sender_identity_jwk: String,
        recipient_identity_raw: ByteArray,
        recipient_signed_prekey_raw: ByteArray,
        recipient_pq_prekey: Pair<Int, ByteArray>? = null,
    ): SenderResult {
        val sender_identity_priv = RatchetCrypto.parse_p256_private_jwk(sender_identity_jwk)
        val recipient_identity_pub = RatchetCrypto.parse_p256_public_raw(recipient_identity_raw)
        val recipient_spk_pub = RatchetCrypto.parse_p256_public_raw(recipient_signed_prekey_raw)

        val ephemeral_kp = RatchetCrypto.generate_p256_keypair()

        val dh1 = RatchetCrypto.ecdh(sender_identity_priv, recipient_spk_pub)
        val dh2 = RatchetCrypto.ecdh(ephemeral_kp.private_key, recipient_identity_pub)
        val dh3 = RatchetCrypto.ecdh(ephemeral_kp.private_key, recipient_spk_pub)

        val pq_pair = if (recipient_pq_prekey != null) {
            val encap = RatchetCrypto.ml_kem_768_encapsulate(recipient_pq_prekey.second)
            encap.ciphertext to encap.shared_secret
        } else null

        val combined = if (pq_pair != null) dh1 + dh2 + dh3 + pq_pair.second else dh1 + dh2 + dh3
        val info = if (pq_pair != null) info_pq else info_classical
        val shared = RatchetCrypto.hkdf_sha256(combined, salt, info, 32)

        dh1.fill(0); dh2.fill(0); dh3.fill(0); combined.fill(0)
        pq_pair?.second?.fill(0)

        return SenderResult(
            shared_secret = shared,
            ephemeral_public_raw = ephemeral_kp.public_raw,
            pq_ciphertext = pq_pair?.first,
            pq_key_id = recipient_pq_prekey?.first,
        )
    }

    fun perform_receiver(
        receiver_identity_jwk: String,
        receiver_signed_prekey_jwk: String,
        sender_identity_raw: ByteArray,
        sender_ephemeral_raw: ByteArray,
        pq_shared_secret: ByteArray? = null,
    ): ByteArray {
        val identity_priv = RatchetCrypto.parse_p256_private_jwk(receiver_identity_jwk)
        val spk_priv = RatchetCrypto.parse_p256_private_jwk(receiver_signed_prekey_jwk)

        val sender_identity_pub = RatchetCrypto.parse_p256_public_raw(sender_identity_raw)
        val sender_ephemeral_pub = RatchetCrypto.parse_p256_public_raw(sender_ephemeral_raw)

        val dh1 = RatchetCrypto.ecdh(spk_priv, sender_identity_pub)
        val dh2 = RatchetCrypto.ecdh(identity_priv, sender_ephemeral_pub)
        val dh3 = RatchetCrypto.ecdh(spk_priv, sender_ephemeral_pub)

        val combined = if (pq_shared_secret != null) {
            dh1 + dh2 + dh3 + pq_shared_secret
        } else {
            dh1 + dh2 + dh3
        }
        val info = if (pq_shared_secret != null) info_pq else info_classical
        val secret = RatchetCrypto.hkdf_sha256(combined, salt, info, 32)

        dh1.fill(0)
        dh2.fill(0)
        dh3.fill(0)
        combined.fill(0)
        return secret
    }

    fun init_receiver_state(
        conversation_id: String,
        shared_secret: ByteArray,
        own_signed_prekey_jwk: String,
        own_signed_prekey_public_b64: String,
    ): RatchetState {
        val d_bytes = RatchetCrypto.jwk_extract_d_bytes(own_signed_prekey_jwk)
        val now = System.currentTimeMillis()
        return RatchetState(
            conversation_id = conversation_id,
            dh_keypair = RatchetDhKeyPair(
                public_key = own_signed_prekey_public_b64,
                secret_key = RatchetCrypto.b64_encode(d_bytes),
            ),
            dh_remote_public = null,
            root_key = RatchetCrypto.b64_encode(shared_secret),
            chain_key_send = null,
            chain_key_recv = null,
            send_message_number = 0,
            recv_message_number = 0,
            previous_chain_length = 0,
            version = 1,
            created_at = now,
            updated_at = now,
        )
    }

    fun derive_conversation_id(email_a: String, email_b: String): String {
        val sorted = listOf(email_a.lowercase(), email_b.lowercase()).sorted()
        val input = sorted.joinToString(":").toByteArray(Charsets.UTF_8)
        return RatchetCrypto.b64_encode(RatchetCrypto.sha256(input))
    }
}
