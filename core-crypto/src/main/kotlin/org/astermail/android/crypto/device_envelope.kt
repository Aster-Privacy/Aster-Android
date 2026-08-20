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

package org.astermail.android.crypto

import java.security.SecureRandom
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

object DeviceEnvelope {

    const val ED25519_PK_BYTES = 32
    const val X25519_PK_BYTES = 32
    const val MLKEM_768_PK_BYTES = 1184
    const val MLKEM_768_CT_BYTES = 1088
    const val XNONCE_BYTES = 24
    const val POLY1305_TAG_BYTES = 16

    const val MIN_ENVELOPE_BYTES =
        X25519_PK_BYTES + MLKEM_768_CT_BYTES + XNONCE_BYTES + POLY1305_TAG_BYTES

    private const val ENROLL_INFO = "astermail-device-enroll-v1"

    private val secure_random = SecureRandom()

    class InvalidDeviceKeyException(message: String) : IllegalArgumentException(message)

    fun seal_secret_for_device(
        secret: ByteArray,
        device_mlkem_pk: ByteArray,
        device_x25519_pk: ByteArray,
    ): ByteArray {
        val eph_sk = ByteArray(X25519_PK_BYTES)
        secure_random.nextBytes(eph_sk)
        try {
            return seal_secret_for_device(
                secret = secret,
                device_mlkem_pk = device_mlkem_pk,
                device_x25519_pk = device_x25519_pk,
                eph_sk = eph_sk,
                nonce = RatchetCrypto.random_bytes(XNONCE_BYTES),
            )
        } finally {
            zeroize(eph_sk)
        }
    }

    internal fun seal_secret_for_device(
        secret: ByteArray,
        device_mlkem_pk: ByteArray,
        device_x25519_pk: ByteArray,
        eph_sk: ByteArray,
        nonce: ByteArray,
    ): ByteArray {
        if (device_mlkem_pk.size != MLKEM_768_PK_BYTES) {
            throw InvalidDeviceKeyException("ml-kem public key must be $MLKEM_768_PK_BYTES bytes")
        }
        if (device_x25519_pk.size != X25519_PK_BYTES) {
            throw InvalidDeviceKeyException("x25519 public key must be $X25519_PK_BYTES bytes")
        }
        require(nonce.size == XNONCE_BYTES) { "nonce must be $XNONCE_BYTES bytes" }
        require(secret.isNotEmpty()) { "secret must not be empty" }

        val encapsulation = RatchetCrypto.ml_kem_768_encapsulate(device_mlkem_pk)
        val ss_pq = encapsulation.shared_secret
        val eph_pk = x25519_public_key(eph_sk)
        val ss_cl = x25519_agree(eph_sk, device_x25519_pk)
        val ikm = ss_pq + ss_cl
        val shared_key = hkdf_sha256(ikm, nonce, ENROLL_INFO.toByteArray(Charsets.UTF_8), 32)

        try {
            val ciphertext = xchacha20_poly1305_encrypt(shared_key, nonce, secret)
            return eph_pk + encapsulation.ciphertext + nonce + ciphertext
        } finally {
            zeroize(ss_pq, ss_cl, ikm, shared_key)
        }
    }

    fun open_secret_for_device(
        envelope: ByteArray,
        device_mlkem_sk: ByteArray,
        device_x25519_sk: ByteArray,
    ): ByteArray {
        require(envelope.size >= MIN_ENVELOPE_BYTES) { "envelope too short" }

        var offset = 0
        val eph_pk = envelope.copyOfRange(offset, offset + X25519_PK_BYTES)
        offset += X25519_PK_BYTES
        val mlkem_ct = envelope.copyOfRange(offset, offset + MLKEM_768_CT_BYTES)
        offset += MLKEM_768_CT_BYTES
        val nonce = envelope.copyOfRange(offset, offset + XNONCE_BYTES)
        offset += XNONCE_BYTES
        val ciphertext = envelope.copyOfRange(offset, envelope.size)

        val ss_pq = RatchetCrypto.ml_kem_768_decapsulate(mlkem_ct, device_mlkem_sk)
        val ss_cl = x25519_agree(device_x25519_sk, eph_pk)
        val ikm = ss_pq + ss_cl
        val shared_key = hkdf_sha256(ikm, nonce, ENROLL_INFO.toByteArray(Charsets.UTF_8), 32)

        try {
            return xchacha20_poly1305_decrypt(shared_key, nonce, ciphertext)
        } finally {
            zeroize(ss_pq, ss_cl, ikm, shared_key)
        }
    }

    fun base64url_encode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun base64url_decode(value: String): ByteArray {
        val normalized = value.trim().replace('+', '-').replace('/', '_').trimEnd('=')
        return java.util.Base64.getUrlDecoder().decode(normalized)
    }

    private fun x25519_public_key(secret_key: ByteArray): ByteArray =
        X25519PrivateKeyParameters(secret_key, 0).generatePublicKey().encoded

    private fun x25519_agree(secret_key: ByteArray, public_key: ByteArray): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(X25519PrivateKeyParameters(secret_key, 0))
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(public_key, 0), shared, 0)
        if (shared.all { it.toInt() == 0 }) {
            throw InvalidDeviceKeyException("x25519 agreement produced an all-zero shared secret")
        }
        return shared
    }

    internal fun xchacha20_poly1305_encrypt(
        key: ByteArray,
        nonce: ByteArray,
        plaintext: ByteArray,
    ): ByteArray = xchacha20_poly1305(key, nonce, plaintext, encrypt = true)

    internal fun xchacha20_poly1305_decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray = xchacha20_poly1305(key, nonce, ciphertext, encrypt = false)

    private fun xchacha20_poly1305(
        key: ByteArray,
        nonce: ByteArray,
        input: ByteArray,
        encrypt: Boolean,
    ): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == XNONCE_BYTES) { "nonce must be $XNONCE_BYTES bytes" }

        val subkey = hchacha20(key, nonce.copyOfRange(0, 16))
        val inner_nonce = ByteArray(12)
        System.arraycopy(nonce, 16, inner_nonce, 4, 8)

        val cipher = ChaCha20Poly1305()
        try {
            cipher.init(
                encrypt,
                AEADParameters(KeyParameter(subkey), POLY1305_TAG_BYTES * 8, inner_nonce),
            )
            val out = ByteArray(cipher.getOutputSize(input.size))
            val written = cipher.processBytes(input, 0, input.size, out, 0)
            val total = written + cipher.doFinal(out, written)
            return if (total == out.size) out else out.copyOfRange(0, total)
        } finally {
            zeroize(subkey)
        }
    }

    internal fun hchacha20(key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 16) { "hchacha20 nonce must be 16 bytes" }

        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        for (i in 0 until 8) state[4 + i] = load_le(key, i * 4)
        for (i in 0 until 4) state[12 + i] = load_le(nonce, i * 4)

        for (round in 0 until 10) {
            quarter_round(state, 0, 4, 8, 12)
            quarter_round(state, 1, 5, 9, 13)
            quarter_round(state, 2, 6, 10, 14)
            quarter_round(state, 3, 7, 11, 15)
            quarter_round(state, 0, 5, 10, 15)
            quarter_round(state, 1, 6, 11, 12)
            quarter_round(state, 2, 7, 8, 13)
            quarter_round(state, 3, 4, 9, 14)
        }

        val out = ByteArray(32)
        for (i in 0 until 4) store_le(out, i * 4, state[i])
        for (i in 0 until 4) store_le(out, 16 + i * 4, state[12 + i])
        state.fill(0)
        return out
    }

    private fun quarter_round(state: IntArray, a: Int, b: Int, c: Int, d: Int) {
        state[a] += state[b]
        state[d] = Integer.rotateLeft(state[d] xor state[a], 16)
        state[c] += state[d]
        state[b] = Integer.rotateLeft(state[b] xor state[c], 12)
        state[a] += state[b]
        state[d] = Integer.rotateLeft(state[d] xor state[a], 8)
        state[c] += state[d]
        state[b] = Integer.rotateLeft(state[b] xor state[c], 7)
    }

    private fun load_le(source: ByteArray, offset: Int): Int =
        (source[offset].toInt() and 0xff) or
            ((source[offset + 1].toInt() and 0xff) shl 8) or
            ((source[offset + 2].toInt() and 0xff) shl 16) or
            ((source[offset + 3].toInt() and 0xff) shl 24)

    private fun store_le(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
        target[offset + 2] = ((value ushr 16) and 0xff).toByte()
        target[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
