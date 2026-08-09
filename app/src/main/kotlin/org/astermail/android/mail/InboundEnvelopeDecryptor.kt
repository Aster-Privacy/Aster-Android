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

package org.astermail.android.mail

import org.astermail.android.crypto.ratchet.RatchetCrypto

data class InboundRatchetKeySet(
    val identity_jwk: String,
    val pq_identity_secret_b64: String?,
)

object InboundEnvelopeDecryptor {

    const val ECDH_MARKER: Byte = 0x02
    const val ECDH_COMPRESSED_MARKER: Byte = 0x03
    const val PQ_HYBRID_MARKER: Byte = 0x04

    private const val EPH_KEY_LEN = 65
    private const val ML_KEM_CT_LEN = 1088
    private const val MAX_DECOMPRESSED_BYTES = 48L * 1024 * 1024

    private val ECDH_INFO = "aster-inbound-v1".toByteArray(Charsets.UTF_8)
    private val PQ_HYBRID_INFO = "aster-inbound-pq-v1".toByteArray(Charsets.UTF_8)

    fun decrypt(
        encrypted_b64: String,
        nonce: ByteArray,
        key_sets: List<InboundRatchetKeySet>,
    ): ByteArray? {
        if (nonce.size != 12) return null

        val enc = runCatching {
            android.util.Base64.decode(encrypted_b64, android.util.Base64.DEFAULT)
        }.getOrNull() ?: return null

        return decrypt_bytes(enc, nonce, key_sets)
    }

    fun decrypt_bytes(
        enc: ByteArray,
        nonce: ByteArray,
        key_sets: List<InboundRatchetKeySet>,
    ): ByteArray? {
        if (nonce.size != 12 || enc.isEmpty()) return null

        val marker = enc[0]
        val header_len = header_length(marker) ?: return null
        if (enc.size <= header_len) return null

        for (key_set in key_sets) {
            val plaintext = when (marker) {
                PQ_HYBRID_MARKER -> key_set.pq_identity_secret_b64?.let { pq_secret ->
                    decrypt_pq_hybrid(enc, nonce, key_set.identity_jwk, pq_secret)
                }
                else -> decrypt_ecdh(
                    enc,
                    nonce,
                    key_set.identity_jwk,
                    marker == ECDH_COMPRESSED_MARKER,
                )
            }
            if (plaintext != null) return plaintext
        }

        return null
    }

    fun header_length(marker: Byte): Int? = when (marker) {
        ECDH_MARKER, ECDH_COMPRESSED_MARKER -> 1 + EPH_KEY_LEN
        PQ_HYBRID_MARKER -> 1 + EPH_KEY_LEN + ML_KEM_CT_LEN
        else -> null
    }

    private fun decrypt_ecdh(
        enc: ByteArray,
        nonce: ByteArray,
        identity_jwk: String,
        compressed: Boolean,
    ): ByteArray? {
        var shared_secret: ByteArray? = null
        var aes_key: ByteArray? = null
        var plaintext: ByteArray? = null
        return try {
            val ephemeral_public = RatchetCrypto.parse_p256_public_raw(
                enc.copyOfRange(1, 1 + EPH_KEY_LEN),
            )
            val identity_private = RatchetCrypto.parse_p256_private_jwk(identity_jwk)
            shared_secret = RatchetCrypto.ecdh(identity_private, ephemeral_public)
            if (shared_secret.size < 32) return null
            aes_key = RatchetCrypto.hkdf_sha256(shared_secret, ByteArray(0), ECDH_INFO, 32)
            plaintext = RatchetCrypto.aes_gcm_decrypt(
                enc.copyOfRange(1 + EPH_KEY_LEN, enc.size),
                aes_key,
                nonce,
            )
            if (compressed) inflate_zlib(plaintext) else plaintext.copyOf()
        } catch (_: Throwable) {
            null
        } finally {
            shared_secret?.fill(0)
            aes_key?.fill(0)
            plaintext?.fill(0)
        }
    }

    private fun decrypt_pq_hybrid(
        enc: ByteArray,
        nonce: ByteArray,
        identity_jwk: String,
        pq_identity_secret_b64: String,
    ): ByteArray? {
        var ecdh_shared: ByteArray? = null
        var pq_secret: ByteArray? = null
        var ml_kem_shared: ByteArray? = null
        var ikm: ByteArray? = null
        var aes_key: ByteArray? = null
        var plaintext: ByteArray? = null
        return try {
            val ephemeral_public = RatchetCrypto.parse_p256_public_raw(
                enc.copyOfRange(1, 1 + EPH_KEY_LEN),
            )
            val identity_private = RatchetCrypto.parse_p256_private_jwk(identity_jwk)
            ecdh_shared = RatchetCrypto.ecdh(identity_private, ephemeral_public)
            if (ecdh_shared.size < 32) return null

            pq_secret = RatchetCrypto.b64_decode(pq_identity_secret_b64)
            ml_kem_shared = RatchetCrypto.ml_kem_768_decapsulate(
                enc.copyOfRange(1 + EPH_KEY_LEN, 1 + EPH_KEY_LEN + ML_KEM_CT_LEN),
                pq_secret,
            )
            if (ml_kem_shared.size < 32) return null

            ikm = ByteArray(64)
            System.arraycopy(ecdh_shared, 0, ikm, 0, 32)
            System.arraycopy(ml_kem_shared, 0, ikm, 32, 32)
            aes_key = RatchetCrypto.hkdf_sha256(ikm, ByteArray(0), PQ_HYBRID_INFO, 32)

            plaintext = RatchetCrypto.aes_gcm_decrypt(
                enc.copyOfRange(1 + EPH_KEY_LEN + ML_KEM_CT_LEN, enc.size),
                aes_key,
                nonce,
            )
            inflate_zlib(plaintext)
        } catch (_: Throwable) {
            null
        } finally {
            ecdh_shared?.fill(0)
            pq_secret?.fill(0)
            ml_kem_shared?.fill(0)
            ikm?.fill(0)
            aes_key?.fill(0)
            plaintext?.fill(0)
        }
    }

    private fun inflate_zlib(compressed: ByteArray): ByteArray {
        val inflater = java.util.zip.Inflater()
        try {
            inflater.setInput(compressed)
            val out = java.io.ByteArrayOutputStream(compressed.size.coerceAtMost(1 shl 20))
            val buffer = ByteArray(1 shl 16)
            var total = 0L
            while (!inflater.finished()) {
                val produced = inflater.inflate(buffer)
                if (produced == 0 && !inflater.finished()) {
                    throw IllegalStateException("truncated zlib stream")
                }
                total += produced
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw IllegalStateException("decompressed envelope exceeds size limit")
                }
                out.write(buffer, 0, produced)
            }
            buffer.fill(0)
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }
}
