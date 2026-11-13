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
    private val info_pq_identity = "Aster Mail_PQXDH_identity_v1".toByteArray(Charsets.UTF_8)
    private val info_classical_v2 = "Aster Mail_X3DH_v2".toByteArray(Charsets.UTF_8)
    private val info_pq_v2 = "Aster Mail_PQXDH_v2".toByteArray(Charsets.UTF_8)
    private val info_pq_identity_v2 = "Aster Mail_PQXDH_identity_v2".toByteArray(Charsets.UTF_8)
    private val info_classical_v3 = "Aster Mail_X3DH_v3".toByteArray(Charsets.UTF_8)
    private val info_pq_v3 = "Aster Mail_PQXDH_v3".toByteArray(Charsets.UTF_8)
    private val info_pq_identity_v3 = "Aster Mail_PQXDH_identity_v3".toByteArray(Charsets.UTF_8)
    private val info_classical_v4 = "Aster Mail_X3DH_v4".toByteArray(Charsets.UTF_8)
    private val info_pq_v4 = "Aster Mail_PQXDH_v4".toByteArray(Charsets.UTF_8)
    private val info_pq_identity_v4 = "Aster Mail_PQXDH_identity_v4".toByteArray(Charsets.UTF_8)
    private val salt = ByteArray(32)

    const val PQ_IDENTITY_KEY_ID = -1
    const val VERSION_LEGACY = 1
    const val VERSION_TRANSCRIPT_BOUND = 2
    const val VERSION_KEY_BOUND = 3
    const val VERSION_MAX = VERSION_KEY_BOUND
    const val VERSION_ONE_TIME_BOUND = 4
    const val READ_VERSION_MAX = VERSION_ONE_TIME_BOUND
    const val ONE_TIME_PREKEY_ID_MAX = 0x7fffffff
    const val P256_PUBLIC_RAW_LEN = 65
    private const val SIGNED_PREKEY_ID_BYTES = 8
    const val ML_KEM_768_EK_LEN = 1184

    enum class PqMode { ONETIME, IDENTITY, NONE }

    data class SenderResult(
        val shared_secret: ByteArray,
        val ephemeral_public_raw: ByteArray,
        val pq_ciphertext: ByteArray? = null,
        val pq_key_id: Int? = null,
        val pq_mode: PqMode = PqMode.NONE,
        val x3dh_version: Int = VERSION_LEGACY,
        val one_time_prekey_id: Int? = null,
        val signed_prekey_id: String? = null,
    )

    private data class PqTarget(
        val public_key: ByteArray,
        val key_id: Int,
        val mode: PqMode,
    )

    fun negotiated_sender_version(advertised: Int?, remembered: Int = VERSION_LEGACY): Int {
        val offered = (advertised ?: VERSION_LEGACY).coerceIn(VERSION_LEGACY, VERSION_MAX)
        val floor = remembered.coerceIn(VERSION_LEGACY, VERSION_TRANSCRIPT_BOUND)
        return maxOf(offered, floor)
    }

    fun receive_floor(highest_seen: Int): Int = highest_seen.coerceAtMost(VERSION_TRANSCRIPT_BOUND)

    private fun known_version(version: Int?): Int = when (version) {
        VERSION_TRANSCRIPT_BOUND, VERSION_KEY_BOUND -> version
        else -> VERSION_LEGACY
    }

    private fun readable_version(version: Int?): Int = when (version) {
        VERSION_TRANSCRIPT_BOUND, VERSION_KEY_BOUND, VERSION_ONE_TIME_BOUND -> version
        else -> VERSION_LEGACY
    }

    fun is_valid_one_time_prekey_id(key_id: Int?): Boolean =
        key_id != null && key_id >= 1 && key_id <= ONE_TIME_PREKEY_ID_MAX

    fun encode_one_time_prekey_id(key_id: Int): ByteArray {
        require(is_valid_one_time_prekey_id(key_id)) { "one-time prekey id out of range" }
        return byteArrayOf(
            (key_id ushr 24).toByte(),
            (key_id ushr 16).toByte(),
            (key_id ushr 8).toByte(),
            key_id.toByte(),
        )
    }

    fun signed_prekey_id(signed_prekey_raw: ByteArray): String =
        RatchetCrypto.sha256(signed_prekey_raw)
            .take(SIGNED_PREKEY_ID_BYTES)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    fun usable_one_time_prekey(candidate: Pair<Int, ByteArray>?): Pair<Int, ByteArray>? {
        if (candidate == null) return null
        if (!is_valid_one_time_prekey_id(candidate.first)) return null
        if (!is_public_raw(candidate.second)) return null
        return candidate
    }

    private fun is_public_raw(raw: ByteArray?): Boolean =
        raw != null && raw.size == P256_PUBLIC_RAW_LEN && raw[0] == 0x04.toByte()

    private fun info_for(version: Int, mode: PqMode): ByteArray = when (mode) {
        PqMode.NONE -> when (version) {
            VERSION_ONE_TIME_BOUND -> info_classical_v4
            VERSION_KEY_BOUND -> info_classical_v3
            VERSION_TRANSCRIPT_BOUND -> info_classical_v2
            else -> info_classical
        }
        PqMode.ONETIME -> when (version) {
            VERSION_ONE_TIME_BOUND -> info_pq_v4
            VERSION_KEY_BOUND -> info_pq_v3
            VERSION_TRANSCRIPT_BOUND -> info_pq_v2
            else -> info_pq
        }
        PqMode.IDENTITY -> when (version) {
            VERSION_ONE_TIME_BOUND -> info_pq_identity_v4
            VERSION_KEY_BOUND -> info_pq_identity_v3
            VERSION_TRANSCRIPT_BOUND -> info_pq_identity_v2
            else -> info_pq_identity
        }
    }

    private fun transcript_inputs(
        version: Int,
        initiator_identity: ByteArray,
        responder_identity: ByteArray,
        responder_signed_prekey: ByteArray,
        initiator_ephemeral: ByteArray,
        kem_ciphertext: ByteArray?,
        responder_one_time_prekey: ByteArray? = null,
        one_time_prekey_id: Int? = null,
    ): ByteArray {
        if (version < VERSION_TRANSCRIPT_BOUND) return ByteArray(0)
        var bound = initiator_identity + responder_identity
        if (version >= VERSION_KEY_BOUND) {
            bound += responder_signed_prekey + initiator_ephemeral
        }
        if (version >= VERSION_ONE_TIME_BOUND) {
            require(
                is_public_raw(initiator_identity) &&
                    is_public_raw(responder_identity) &&
                    is_public_raw(responder_signed_prekey) &&
                    is_public_raw(initiator_ephemeral) &&
                    responder_one_time_prekey != null &&
                    is_public_raw(responder_one_time_prekey),
            ) { "one-time bound handshake is missing a public key" }
            require(one_time_prekey_id != null && is_valid_one_time_prekey_id(one_time_prekey_id)) {
                "one-time bound handshake is missing its prekey id"
            }
            bound += responder_one_time_prekey + encode_one_time_prekey_id(one_time_prekey_id)
        }
        return if (kem_ciphertext != null) bound + kem_ciphertext else bound
    }

    private fun select_pq_target(
        recipient_pq_prekey: Pair<Int, ByteArray>?,
        recipient_pq_identity: ByteArray?,
        signature_covers_pq_identity: Boolean,
    ): PqTarget? {
        val identity_usable = recipient_pq_identity != null && recipient_pq_identity.size == ML_KEM_768_EK_LEN
        val prefer_signed_identity = signature_covers_pq_identity && identity_usable
        if (!prefer_signed_identity &&
            recipient_pq_prekey != null &&
            recipient_pq_prekey.second.size == ML_KEM_768_EK_LEN
        ) {
            return PqTarget(
                public_key = recipient_pq_prekey.second,
                key_id = recipient_pq_prekey.first,
                mode = PqMode.ONETIME,
            )
        }

        if (recipient_pq_identity != null && recipient_pq_identity.size == ML_KEM_768_EK_LEN) {
            return PqTarget(
                public_key = recipient_pq_identity,
                key_id = PQ_IDENTITY_KEY_ID,
                mode = PqMode.IDENTITY,
            )
        }

        return null
    }

    fun supports_pq(
        recipient_pq_prekey: Pair<Int, ByteArray>?,
        recipient_pq_identity: ByteArray?,
    ): Boolean = select_pq_target(recipient_pq_prekey, recipient_pq_identity, false) != null

    fun perform_sender(
        sender_identity_jwk: String,
        recipient_identity_raw: ByteArray,
        recipient_signed_prekey_raw: ByteArray,
        recipient_pq_prekey: Pair<Int, ByteArray>? = null,
        recipient_pq_identity: ByteArray? = null,
        signature_covers_pq_identity: Boolean = false,
        x3dh_version: Int = VERSION_LEGACY,
        recipient_one_time_prekey: Pair<Int, ByteArray>? = null,
        advertised_x3dh_version: Int? = null,
    ): SenderResult {
        val sender_identity_priv = RatchetCrypto.parse_p256_private_jwk(sender_identity_jwk)
        val recipient_identity_pub = RatchetCrypto.parse_p256_public_raw(recipient_identity_raw)
        val recipient_spk_pub = RatchetCrypto.parse_p256_public_raw(recipient_signed_prekey_raw)

        val ephemeral_kp = RatchetCrypto.generate_p256_keypair()

        val dh1 = RatchetCrypto.ecdh(sender_identity_priv, recipient_spk_pub)
        val dh2 = RatchetCrypto.ecdh(ephemeral_kp.private_key, recipient_identity_pub)
        val dh3 = RatchetCrypto.ecdh(ephemeral_kp.private_key, recipient_spk_pub)

        val pq_target = select_pq_target(recipient_pq_prekey, recipient_pq_identity, signature_covers_pq_identity)

        val pq_pair = if (pq_target != null) {
            val encap = RatchetCrypto.ml_kem_768_encapsulate(pq_target.public_key)
            encap.ciphertext to encap.shared_secret
        } else null

        val base_version = known_version(x3dh_version)
        val initiator_identity = if (base_version >= VERSION_TRANSCRIPT_BOUND) {
            RatchetCrypto.p256_public_raw_from_private_jwk(sender_identity_jwk)
        } else {
            ByteArray(0)
        }

        val one_time_target = usable_one_time_prekey(recipient_one_time_prekey).takeIf {
            base_version >= VERSION_TRANSCRIPT_BOUND &&
                (advertised_x3dh_version ?: VERSION_LEGACY) >= VERSION_ONE_TIME_BOUND &&
                is_public_raw(initiator_identity) &&
                is_public_raw(recipient_identity_raw) &&
                is_public_raw(recipient_signed_prekey_raw)
        }
        val dh4 = one_time_target?.let { target ->
            try {
                RatchetCrypto.ecdh(ephemeral_kp.private_key, RatchetCrypto.parse_p256_public_raw(target.second))
            } catch (_: Throwable) {
                null
            }
        }
        val bound_target = one_time_target.takeIf { dh4 != null }
        val version = if (bound_target != null) VERSION_ONE_TIME_BOUND else base_version

        val transcript = if (version >= VERSION_TRANSCRIPT_BOUND) {
            transcript_inputs(
                version = version,
                initiator_identity = initiator_identity,
                responder_identity = recipient_identity_raw,
                responder_signed_prekey = recipient_signed_prekey_raw,
                initiator_ephemeral = ephemeral_kp.public_raw,
                kem_ciphertext = pq_pair?.first,
                responder_one_time_prekey = bound_target?.second,
                one_time_prekey_id = bound_target?.first,
            )
        } else {
            ByteArray(0)
        }

        val classical = if (dh4 != null) dh1 + dh2 + dh3 + dh4 else dh1 + dh2 + dh3
        val combined = if (pq_pair != null) {
            classical + pq_pair.second + transcript
        } else {
            classical + transcript
        }
        classical.fill(0)
        dh4?.fill(0)
        val info = info_for(version, pq_target?.mode ?: PqMode.NONE)
        val shared = RatchetCrypto.hkdf_sha256(combined, salt, info, 32)

        dh1.fill(0); dh2.fill(0); dh3.fill(0); combined.fill(0)
        pq_pair?.second?.fill(0)

        return SenderResult(
            shared_secret = shared,
            ephemeral_public_raw = ephemeral_kp.public_raw,
            pq_ciphertext = pq_pair?.first,
            pq_key_id = pq_target?.key_id,
            pq_mode = pq_target?.mode ?: PqMode.NONE,
            x3dh_version = version,
            one_time_prekey_id = bound_target?.first,
            signed_prekey_id = bound_target?.let { signed_prekey_id(recipient_signed_prekey_raw) },
        )
    }

    fun perform_receiver(
        receiver_identity_jwk: String,
        receiver_signed_prekey_jwk: String,
        sender_identity_raw: ByteArray,
        sender_ephemeral_raw: ByteArray,
        pq_shared_secret: ByteArray? = null,
        pq_from_identity: Boolean = false,
        x3dh_version: Int? = null,
        pq_ciphertext: ByteArray? = null,
        one_time_prekey_jwk: String? = null,
        one_time_prekey_id: Int? = null,
    ): ByteArray {
        val version = readable_version(x3dh_version)
        val one_time_jwk = one_time_prekey_jwk.takeIf { version >= VERSION_ONE_TIME_BOUND }
        if (version >= VERSION_ONE_TIME_BOUND) {
            require(one_time_jwk != null && is_valid_one_time_prekey_id(one_time_prekey_id)) {
                "one-time bound handshake is missing its prekey"
            }
        }

        val identity_priv = RatchetCrypto.parse_p256_private_jwk(receiver_identity_jwk)
        val spk_priv = RatchetCrypto.parse_p256_private_jwk(receiver_signed_prekey_jwk)

        val sender_identity_pub = RatchetCrypto.parse_p256_public_raw(sender_identity_raw)
        val sender_ephemeral_pub = RatchetCrypto.parse_p256_public_raw(sender_ephemeral_raw)

        val dh1 = RatchetCrypto.ecdh(spk_priv, sender_identity_pub)
        val dh2 = RatchetCrypto.ecdh(identity_priv, sender_ephemeral_pub)
        val dh3 = RatchetCrypto.ecdh(spk_priv, sender_ephemeral_pub)
        val dh4 = one_time_jwk?.let {
            RatchetCrypto.ecdh(RatchetCrypto.parse_p256_private_jwk(it), sender_ephemeral_pub)
        }

        val transcript = if (version >= VERSION_TRANSCRIPT_BOUND) {
            transcript_inputs(
                version = version,
                initiator_identity = sender_identity_raw,
                responder_identity = RatchetCrypto.p256_public_raw_from_private_jwk(receiver_identity_jwk),
                responder_signed_prekey = if (version >= VERSION_KEY_BOUND) {
                    RatchetCrypto.p256_public_raw_from_private_jwk(receiver_signed_prekey_jwk)
                } else {
                    ByteArray(0)
                },
                initiator_ephemeral = sender_ephemeral_raw,
                kem_ciphertext = pq_ciphertext.takeIf { pq_shared_secret != null },
                responder_one_time_prekey = one_time_jwk?.let {
                    RatchetCrypto.p256_public_raw_from_private_jwk(it)
                },
                one_time_prekey_id = one_time_prekey_id.takeIf { one_time_jwk != null },
            )
        } else {
            ByteArray(0)
        }

        val classical = if (dh4 != null) dh1 + dh2 + dh3 + dh4 else dh1 + dh2 + dh3
        val combined = if (pq_shared_secret != null) {
            classical + pq_shared_secret + transcript
        } else {
            classical + transcript
        }
        classical.fill(0)
        dh4?.fill(0)
        val mode = when {
            pq_shared_secret == null -> PqMode.NONE
            pq_from_identity -> PqMode.IDENTITY
            else -> PqMode.ONETIME
        }
        val secret = RatchetCrypto.hkdf_sha256(combined, salt, info_for(version, mode), 32)

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
        val sorted = listOf(email_a.lowercase(java.util.Locale.ROOT), email_b.lowercase(java.util.Locale.ROOT)).sorted()
        val input = sorted.joinToString(":").toByteArray(Charsets.UTF_8)
        return RatchetCrypto.b64_encode(RatchetCrypto.sha256(input))
    }
}
