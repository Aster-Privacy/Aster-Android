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

package org.astermail.android.crypto.guard

import java.security.SecureRandom
import java.util.UUID
import org.astermail.android.crypto.ratchet.RatchetCrypto
import org.astermail.android.crypto.zeroize
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters

class GuardSealException(message: String) : Exception(message)

object GuardContextSeal {
    private const val VERSION: Byte = 0x01
    private const val X25519_LEN = 32
    private const val MLKEM_PK_LEN = 1184
    private const val MLKEM_CT_LEN = 1088
    private const val NONCE_LEN = 12
    private const val TAG_BITS = 128
    private const val TAG_LEN = 16
    const val HEADER_LEN = 1 + X25519_LEN + MLKEM_CT_LEN + NONCE_LEN

    private val context_label = "aster-guard-context-v1".toByteArray(Charsets.US_ASCII)
    private val code_secret_label = "aster-guard-code-secret-v1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    fun context_aad(request_id: UUID, device_id: UUID): ByteArray =
        context_label + GuardDigests.uuid_bytes(request_id) + GuardDigests.uuid_bytes(device_id)

    fun code_secret_aad(device_id: UUID): ByteArray =
        code_secret_label + GuardDigests.uuid_bytes(device_id)

    fun seal(plaintext: ByteArray, aad: ByteArray, x25519_pk: ByteArray, mlkem_pk: ByteArray): ByteArray {
        if (x25519_pk.size != X25519_LEN || mlkem_pk.size != MLKEM_PK_LEN) throw GuardSealException("bad key")
        val eph = X25519PrivateKeyParameters(random)
        val eph_pk = eph.generatePublicKey().encoded
        val x_shared = agree(eph, x25519_pk)
        val encap = RatchetCrypto.ml_kem_768_encapsulate(mlkem_pk)
        val key = derive_key(x_shared, encap.shared_secret, eph_pk, encap.ciphertext, aad)
        zeroize(x_shared, encap.shared_secret)
        val nonce = ByteArray(NONCE_LEN).also { random.nextBytes(it) }
        try {
            val ct = gcm(true, key, nonce, aad, plaintext)
            return byteArrayOf(VERSION) + eph_pk + encap.ciphertext + nonce + ct
        } finally {
            zeroize(key)
        }
    }

    fun open(sealed: ByteArray, aad: ByteArray, x25519_sk: ByteArray, mlkem_sk: ByteArray): ByteArray {
        if (sealed.size < HEADER_LEN + TAG_LEN) throw GuardSealException("short input")
        if (sealed[0] != VERSION) throw GuardSealException("bad version")
        if (x25519_sk.size != X25519_LEN) throw GuardSealException("bad key")
        var pos = 1
        val eph_pk = sealed.copyOfRange(pos, pos + X25519_LEN).also { pos += X25519_LEN }
        val mlkem_ct = sealed.copyOfRange(pos, pos + MLKEM_CT_LEN).also { pos += MLKEM_CT_LEN }
        val nonce = sealed.copyOfRange(pos, pos + NONCE_LEN).also { pos += NONCE_LEN }
        val ct = sealed.copyOfRange(pos, sealed.size)
        val x_shared = agree(X25519PrivateKeyParameters(x25519_sk, 0), eph_pk)
        val mlkem_shared = try {
            RatchetCrypto.ml_kem_768_decapsulate(mlkem_ct, mlkem_sk)
        } catch (t: Throwable) {
            zeroize(x_shared)
            throw GuardSealException("bad key")
        }
        val key = derive_key(x_shared, mlkem_shared, eph_pk, mlkem_ct, aad)
        zeroize(x_shared, mlkem_shared)
        try {
            return gcm(false, key, nonce, aad, ct)
        } catch (t: GuardSealException) {
            throw t
        } catch (t: Throwable) {
            throw GuardSealException("open failed")
        } finally {
            zeroize(key)
        }
    }

    private fun agree(sk: X25519PrivateKeyParameters, peer_pk: ByteArray): ByteArray {
        if (peer_pk.size != X25519_LEN) throw GuardSealException("bad key")
        val shared = ByteArray(X25519_LEN)
        val agreement = X25519Agreement()
        agreement.init(sk)
        try {
            agreement.calculateAgreement(X25519PublicKeyParameters(peer_pk, 0), shared, 0)
        } catch (t: IllegalStateException) {
            throw GuardSealException("bad key")
        }
        if (shared.all { it == 0.toByte() }) throw GuardSealException("bad key")
        return shared
    }

    private fun derive_key(
        x_shared: ByteArray,
        mlkem_shared: ByteArray,
        eph_pk: ByteArray,
        mlkem_ct: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val ikm = x_shared + mlkem_shared
        val info = eph_pk + mlkem_ct + aad
        val gen = HKDFBytesGenerator(SHA256Digest())
        gen.init(HKDFParameters(ikm, context_label, info))
        val key = ByteArray(32)
        gen.generateBytes(key, 0, key.size)
        zeroize(ikm)
        return key
    }

    private fun gcm(encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray {
        val cipher = GCMBlockCipher.newInstance(AESEngine.newInstance())
        cipher.init(encrypt, AEADParameters(KeyParameter(key), TAG_BITS, nonce, aad))
        val out = ByteArray(cipher.getOutputSize(input.size))
        val n = cipher.processBytes(input, 0, input.size, out, 0)
        val total = try {
            n + cipher.doFinal(out, n)
        } catch (t: Throwable) {
            out.fill(0)
            throw GuardSealException("open failed")
        }
        return if (total == out.size) out else out.copyOf(total)
    }
}
